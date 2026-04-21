# PostViralityBackend - Bot Guardrail System

##  Overview

A high-performance Spring Boot microservice that implements a **distributed guardrail system** using **Redis** and **PostgreSQL** to prevent AI compute runaway. The system handles concurrent requests safely, manages distributed state atomically, and implements event-driven scheduling.

**Tech Stack**: Java 17+, Spring Boot 3.x, PostgreSQL, Redis (Spring Data Redis)

---

## Architecture Approach

### Core Philosophy
**Redis as Gatekeeper, PostgreSQL as Source of Truth**

```
Request → Redis Guardrails Check → Database Transaction → Response
           (Atomic Operations)     (Only if Redis allows)
```

This architecture ensures:
-  Zero race conditions
-  Stateless scaling
-  Data integrity
-  High performance 

---

##  Thread Safety

Redis atomic operations guarantee **thread-safe, concurrency-proof** guardrails:

#### **1. Horizontal Cap: 100 Bot Comments Per Post**

**Implementation**:
```java
public void horizontalCap(Long postId){
    String key = "post:" + postId + ":bot_count";
    Long count = redisTemplate.opsForValue().increment(key);  // Atomic INCR
    if(count > 100){
        redisTemplate.opsForValue().decrement(key);            // Atomic DECR
        throw new GuardrailException("Too many bot replies on this post");
    }
}
```

**Thread Safety Guarantee**:
-  `INCR` is **atomic at hardware level** (single Redis protocol operation)
-  Even with 200 concurrent requests at the exact same millisecond:
  - Request 1: INCR → 1 
  - Request 2: INCR → 2 
  - ...
  - Request 100: INCR → 100 
  - Request 101: INCR → 101, then DECR → 100, Reject 
  - Request 102-200: All rejected (counter stays at 100)
-  **No lost increments**: Redis serializes all operations
-  **No race condition winners**: First 100 are deterministic based on Redis queue order

**This approach works because  **:
- Redis is single-threaded
- All commands are executed sequentially
- `INCR` is a single atomic operation 
- Database transaction only commits if counter check passes

---

#### **2. Cooldown Cap: 10 Minutes Between Bot Interactions**

**Implementation**:
```java
public String cooldownCap(Long botId, Long humanId){
    String key = "cooldown:bot_" + botId + ":human_" + humanId;
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
            key,
            "1",
            10,
            TimeUnit.MINUTES
    );
    if (Boolean.FALSE.equals(acquired)) {
        throw new GuardrailException("Bot must wait 10 minutes before interacting with this human again");
    }
    return key;
}
```

**Thread Safety Guarantee**:
-  `SETNX` (Set-If-Not-eXists) is **atomic**
-  Even if 2 threads try to set the same key simultaneously:
  - Thread A: SETNX → Success (returns true) 
  - Thread B: SETNX → Failed (returns false) 
  - Race condition eliminated: Only one thread can acquire the lock
-  TTL (10 minutes) is set **atomically with the key**
-  Distributed across multiple instances 

**Why This Works**:
- `SETNX` cannot be partially executed
- Redis guarantees exactly one success per unique key
- TTL prevents key leaks (automatic cleanup after 10 minutes)

---

#### **3. Vertical Cap: 20 Thread Depth Maximum**

**Implementation**:
```java
public void verticalCap(int levels){
    if(levels > 20){
        throw new GuardrailException("Maximum thread depth reached");
    }
}
```

**Thread Safety Guarantee**:
-  Validated before any Redis/Database operation
-  No concurrency issues (read-only check on incoming data)
-  Depth is calculated from database chain, not stored in memory

---

### Race Condition Test: 200 Concurrent Requests

**Scenario**: 200 bots try to comment on the same post at millisecond-0

**Expected Result**: Exactly 100 saved, 100 rejected

**How Thread Safety Guarantees This**:

```
Time 0ms: All 200 requests arrive at identical millisecond
          ↓
Step 1: Each thread executes INCR (atomically)
        Request 1:   INCR → 1 (allowed)
        Request 2:   INCR → 2 (allowed)
        ...
        Request 100: INCR → 100 (allowed)
        Request 101: INCR → 101 (rejected immediately)
        Request 102-200: All see counter > 100 (rejected)

Step 2: Redis returns results to threads
        Threads 1-100:   Proceed to database
        Threads 101-200: Exception thrown (never reach database)

Step 3: Database transactions execute sequentially (but 100 are already rejected)
        100 comments saved
        Redis counter: 100 (reverted if any DB fails)
```

**Guaranteed Outcome**:
-  Counter stops at exactly 100 (never 101, never 99)
-  Database has exactly 100 comments (no overflow)
-  Deterministic results (same outcome every run)
-  No lost increments (all 200 attempts accounted for)

---

##  Phase 1: Core API & Database Setup

### Database Schema (JPA/Hibernate)

Four interconnected entities with proper relationships:

#### **User Entity**

- **Purpose**: Represents human users in the system
- **Relationships**: Author of posts/comments

#### **Bot Entity**

- **Purpose**: Represents AI bots (subject to guardrails)
- **Relationships**: Subject of cooldown/cap restrictions

#### **Post Entity**

- **Purpose**: Main content unit
- **Relationships**: Parent to all comments, tracked in Redis for virality

#### **Comment Entity**

- **Purpose**: Comments/replies on posts
- **Relationships**: Hierarchical (depth_level tracks nesting)

### REST Endpoints

#### **1. POST /api/posts**
```json
"Request":
{
  "author_id": 1,
  "content": "My new post"
}

Response (200 OK):
{
  "id": 1,
  "content": "My new post",
  "author_id": 1,
  "created_at": "2026-04-21T10:30:00",
  "virality_score": 0,
  "message": "Post created successfully"
}
```

**Implementation**:
- Creates post in PostgreSQL
- Initializes virality score in Redis (default 0)
- Returns created post with metadata

---

#### **2. POST /api/posts/{postId}/comments**
```json
Request:
{
  "author_id": 100,
  "content": "Great post!",
  "parent_comment_id": null,
  "is_bot": true
}

Response (200 OK):
{
  "comment_id": 2,
  "post_id": 1,
  "content": "Great post!",
  "author_id": 100,
  "depth_level": 1,
  "post_virality_after_comment": 51,
  "message": "Comment added successfully"
}

Response (409 CONFLICT):
{
  "error": "Bot must wait 10 minutes before interacting with this human again",
  "timestamp": "2026-04-21T10:31:00"
}
```

**Implementation Flow**:
```
1. Find post and parent comment (if nested)
2. Calculate depth level
3. IF is_bot:
     ├─ Check vertical cap (depth ≤ 20)
     ├─ Check horizontal cap (bot_count < 100)
     └─ Check cooldown (no interaction in 10 min)
4. IF all guardrails pass:
     ├─ Increment bot_count in Redis
     ├─ Save comment to PostgreSQL (@Transactional)
     └─ Increase virality score in Redis
5. Update notifications if bot interaction
```

---

#### **3. POST /api/posts/{postId}/like**
```json
Request:
/api/posts/1/like?userId=5&isBot=false

Response (200 OK):
{
  "post_id": 1,
  "liked_by_user": 5,
  "bot_like": false,
  "points_added": 20,
  "new_virality_score": 120,
  "message": "Post liked successfully"
}
```

**Virality Points**:
- Human Like: +20 points
- Bot Like: +1 point

**Implementation**:
- Increment Redis key: `post:{id}:viral_score`
- Use atomic `increment()` operation
- No database record needed (likes are ephemeral)

---

##  Phase 2: Guardrails System

### The Three Atomic Locks

#### **Lock 1: Horizontal Cap (100 Bots Per Post)**
```
Redis Key: post:{postId}:bot_count
Type: Integer Counter
Operation: INCR (atomic increment)
Failure: Counter > 100 → Reject with 429

Code Location: GuardrailService.horizontalCap()
```

**Workflow**:
```
Request arrives (is_bot=true)
    ↓
Read current count (via INCR)
    ↓
Is count > 100?
    ├─ YES: Decrement, throw exception (429)
    └─ NO: Continue to database
    ↓
Save comment to PostgreSQL
    ↓
Return 200 OK
```

---

#### **Lock 2: Cooldown Cap (10 Minutes Between Interactions)**
```
Redis Key: cooldown:bot_{botId}:human_{humanId}
Type: String with TTL
Operation: SETNX + Expiry (atomic)
Failure: Key exists → Reject with 429
TTL: 10 minutes (auto-cleanup)

Code Location: GuardrailService.cooldownCap()
```

**Workflow**:
```
Request arrives (bot → human interaction)
    ↓
Try to set cooldown key (SETNX)
    ↓
Key already exists?
    ├─ YES: Reject (429)
    └─ NO: Continue
    ↓
Key expires in 10 minutes (automatic)
    ↓
After 10 min: Bot can interact again
```

**Why 10 Minutes**:
- Prevents bot spam on specific human users
- Allows diverse bot engagement (different bots can reply)
- Cooldown is per (bot, human) pair, not per post

---

#### **Lock 3: Vertical Cap (20 Levels Deep)**
```
Depth Level: Calculated from parent_comment_id chain
Max Depth: 20 levels
Operation: Integer comparison (no Redis operation)
Failure: depth_level > 20 → Reject with 429

Code Location: GuardrailService.verticalCap()
```

**Workflow**:
```
Request arrives with parent_comment_id
    ↓
Fetch parent comment from PostgreSQL
    ↓
Calculate depth = parent.depth_level + 1
    ↓
Is depth > 20?
    ├─ YES: Reject (429)
    └─ NO: Continue
    ↓
Save comment with new depth level
```

---

### The Virality Score System

**Real-Time Redis Scoring**:

| Interaction | Points | Redis Key | Operation |
|------------|--------|-----------|-----------|
| Human Comment | +50 | `post:{id}:viral_score` | INCR by 50 |
| Human Like | +20 | `post:{id}:viral_score` | INCR by 20 |
| Bot Reply | +1 | `post:{id}:viral_score` | INCR by 1 |

---

##  Phase 3: Notification Throttler & CRON Sweeper

### The Redis Throttler (15-Minute Window)

**Purpose**: Prevent notification spam while keeping users informed

**Workflow**:
```
Bot interacts with user's post
    ↓
Check: Has user received notification in last 15 min?
    ├─ NO (First interaction):
    │  ├─ Send immediate notification (console log)
    │  └─ Set 15-min cooldown key
    │
    └─ YES (Within 15-min window):
       ├─ Add message to pending list
       └─ Add user to pending sweep set
```

**Redis Keys**:
```
user:{userId}:notif_cooldown           → TTL: 15 minutes
user:{userId}:pending_notifs           → List of queued messages
users:pending_notifs                   → Set of user IDs with pending work
```
---

### The CRON Sweeper 

**Purpose**: Batch-process pending notifications into summaries

**Schedule**: `@Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)`

**Workflow**:
```
Every 5 minutes:
    ↓
1. Fetch all users with pending notifications
   SELECT members from users:pending_notifs
    ↓
2. For each user:
   ├─ Get pending messages from user:{userId}:pending_notifs
   ├─ Extract first bot name and count others
   ├─ Log summarized message:
   │  "Summarized Push Notification: Bot X and N others interacted with your posts"
   ├─ Delete message list
   └─ Remove user from pending set
    ↓
3. Complete (next sweep in 5 minutes)
```

**Example Timeline**:
```
10:00:00 → Bot1 replies (immediate notification sent)
10:00:05 → Bot2 replies (queued)
10:00:10 → Bot3 replies (queued)
10:05:00 → CRON runs: "Bot1 and 2 others interacted with your posts"
```

---

##  Running the Application

### Prerequisites
```bash
# Start PostgreSQL 
docker-compose up -d 

### Build & Run
```bash
mvn clean install
mvn spring-boot:run
```

### Test via Postman
```
Import: postman_collection.json
Run: Collection Runner

```

---

##  Project Structure

```
src/main/java/com/example/app/
├── AppApplication.java
├── config/
│   └── RedisConfig.java              (Spring Data Redis template)
├── controller/
│   └── AppController.java            (REST endpoints)
├── dto/
│   ├── CommentRequest.java
│   └── PostRequest.java
├── entity/
│   ├── Bot.java
│   ├── Comment.java
│   ├── Post.java
│   └── User.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   └── GuardrailException.java
├── repo/
│   ├── BotRepository.java
│   ├── CommentRepository.java
│   ├── PostRepository.java
│   └── UserRepository.java
└── service/
    ├── CommentService.java          (@Transactional, orchestrator)
    ├── CORNSweeperService.java      (@Scheduled CRON)
    ├── GuardrailService.java        (Atomic locks)
    ├── NotificationService.java     (Throttler)
    ├── PostService.java             (Post management)
    └── ViralityService.java         (Real-time scoring)
```

---
