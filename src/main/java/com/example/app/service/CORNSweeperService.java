package com.example.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CORNSweeperService {

    private final RedisTemplate<String, String> redisTemplate;
    private final NotificationService notificationService;

    // Runs every 5 minutes 
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void sweepPendingNotifications(){
        Set<String> userIds = redisTemplate.opsForSet().members(notificationService.pendingUsersKey());
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        for (String userId : userIds) {
            sweepUserNotifications(userId);
        }
    }

    private void sweepUserNotifications(String userId) {
        Long parsedUserId = Long.valueOf(userId);
        String pendingKey = notificationService.pendingNotificationsKey(parsedUserId);
        List<String> messages = redisTemplate.opsForList().range(pendingKey, 0, -1);

        if (messages == null || messages.isEmpty()) {
            redisTemplate.opsForSet().remove(notificationService.pendingUsersKey(), userId);
            return;
        }

        String firstBotName = extractBotName(messages.get(0));
        int otherInteractions = messages.size() - 1;

        System.out.println(
                "Summarized Push Notification: "
                        + firstBotName
                        + " and "
                        + otherInteractions
                        + " others interacted with your posts."
        );

        // Clear processed work for this user after the summary has been logged.
        redisTemplate.delete(pendingKey);
        redisTemplate.opsForSet().remove(notificationService.pendingUsersKey(), userId);
    }

    private String extractBotName(String message) {
        int replyTextStart = message.indexOf(" replied to your post");
        if (replyTextStart == -1) {
            return message;
        }
        return message.substring(0, replyTextStart);
    }
}
