package com.example.app.service;

import com.example.app.entity.Bot;
import com.example.app.repo.BotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final RedisTemplate<String, String> redisTemplate;
    private final BotRepository botRepository;

    public void handleBotInteraction(Long userId, Long botId) {
        String botName = botRepository.findById(botId)
                .map(Bot::getName)
                .orElse("Bot " + botId);
        String message = botName + " replied to your post";

        sendOrQueueNotification(userId, message);
    }

    private void sendOrQueueNotification(Long userId, String message) {
        String cooldownKey = cooldownKey(userId);

        // Redis SETNX with expiry lets the first bot interaction open a 15-minute cooldown window.
        Boolean notificationAllowed = redisTemplate.opsForValue().setIfAbsent(
                cooldownKey,
                "1",
                15,
                TimeUnit.MINUTES
        );

        if (Boolean.TRUE.equals(notificationAllowed)) {
            sendNotification();
            return;
        }

        queueNotification(userId, message);
    }

    private void sendNotification() {
        System.out.println("Push Notification Sent to User");
    }

    private void queueNotification(Long userId, String message) {
        // A user already inside the cooldown window gets queued for the scheduled summary.
        redisTemplate.opsForList().rightPush(pendingNotificationsKey(userId), message);

        // Track users with pending work so the sweeper does not need to scan Redis keys.
        redisTemplate.opsForSet().add(pendingUsersKey(), String.valueOf(userId));
    }

    String pendingNotificationsKey(Long userId) {
        return "user:" + userId + ":pending_notifs";
    }

    String pendingUsersKey() {
        return "users:pending_notifs";
    }

    private String cooldownKey(Long userId) {
        return "user:" + userId + ":notif_cooldown";
    }
}
