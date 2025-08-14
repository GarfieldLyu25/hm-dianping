package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1735689600L;
    private static final int COUNT_BITS = 32;
    @Resource
    StringRedisTemplate stringRedisTemplate;
    public long nextId(String perfix) {
        LocalDateTime now = LocalDateTime.now();
        long nowsecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowsecond - BEGIN_TIMESTAMP;
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + perfix + ":" + date);
        return timestamp << COUNT_BITS | count;
    }

    public static void main(String[] args) {
        //LocalDateTime now = LocalDateTime.of(2025, 1, 1, 0, 0, 0);
        LocalDateTime now = LocalDateTime.now();
        long nowsecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowsecond - BEGIN_TIMESTAMP;
        System.out.println(timestamp);
    }
}
