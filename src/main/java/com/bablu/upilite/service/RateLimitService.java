package com.bablu.upilite.service;

import com.bablu.upilite.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, Deque<Long>> requestBuckets = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitingEnabled;

    public void assertAllowed(String scope, String key, int maxRequests, Duration window) {
        if (!rateLimitingEnabled || maxRequests <= 0 || window == null || window.isZero() || window.isNegative()) {
            return;
        }

        String normalizedScope = StringUtils.hasText(scope) ? scope.trim().toUpperCase() : "GLOBAL";
        String normalizedKey = StringUtils.hasText(key) ? key.trim().toLowerCase() : "anonymous";
        String bucketKey = normalizedScope + "|" + normalizedKey;

        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();

        Deque<Long> bucket = requestBuckets.computeIfAbsent(bucketKey, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst() < windowStart) {
                bucket.pollFirst();
            }

            if (bucket.size() >= maxRequests) {
                long oldestTimestamp = bucket.peekFirst() == null ? now : bucket.peekFirst();
                long retryAfterMillis = Math.max(1, (oldestTimestamp + window.toMillis()) - now);
                long retryAfterSeconds = Math.max(1, (retryAfterMillis + 999) / 1000);
                throw new RateLimitExceededException(
                        "Too many requests. Please retry after " + retryAfterSeconds + " seconds.",
                        retryAfterSeconds
                );
            }

            bucket.addLast(now);
        }
    }
}
