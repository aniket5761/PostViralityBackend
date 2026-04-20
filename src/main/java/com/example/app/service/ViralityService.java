package com.example.app.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ViralityService {

    
    private final RedisTemplate<String, String> redisTemplate;

    public Long increaseVirality(Long postId, String type){
        String key = "post:" + postId + ":viral_score";
        Long points = pointsToAdd(type);
        return redisTemplate.opsForValue().increment(key, points);
    }

    private Long pointsToAdd(String type) {
        return switch(type){
            case "BOT_REPLY" -> 1L;
            case "HUMAN_LIKE" -> 20L;
            case "HUMAN_COMMENT" -> 50L;
            default -> 0L;
        };
    }

    public Long getViraityScore(Long postId) {
        String key = "post:" + postId + ":viral_score";
        String score = redisTemplate.opsForValue().get(key);
        if (score == null) {
            return 0L;
        }
        return Long.parseLong(score);
    
    }
    

}
