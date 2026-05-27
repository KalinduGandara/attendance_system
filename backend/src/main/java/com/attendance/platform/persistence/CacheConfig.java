package com.attendance.platform.persistence;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class CacheConfig {

    public static final String TIME_CODES = "timeCodes";
    public static final String SHIFTS = "shifts";
    public static final String SCHEDULE_TEMPLATES = "scheduleTemplates";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager();
        mgr.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(10_000));
        mgr.setCacheNames(List.of(TIME_CODES, SHIFTS, SCHEDULE_TEMPLATES));
        return mgr;
    }
}
