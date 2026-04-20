package com.example.app.service;
import com.example.app.exception.GuardrailException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GuardrailService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    //horizontal cap
    public void horizontalCap(Long postId){
        String key = "post:" + postId + ":bot_count";
        Long count = redisTemplate.opsForValue().increment(key);
        if(count> 100){
            redisTemplate.opsForValue().decrement(key);
            throw new GuardrailException("Too many bot replies on this post");
        }
    }

    //vertical cap
    public void verticalCap (int levels){
        if(levels>20){
            throw new GuardrailException("Maximum thread depth reached");
        }
    }
    //cooldown cap
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


    public void revertHorizontalCap(Long postId) {
        String key = "post:" + postId + ":bot_count";
        redisTemplate.opsForValue().decrement(key);
    }

    public void revertCooldownCap(String key) {
        if (key != null) {
            redisTemplate.delete(key);
        }
    }
}
