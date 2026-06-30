package org.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final int chatLimit;
    private final long chatWindowSeconds;

    public RateLimitService(@Value("${app.rate-limit.chat.limit:20}") int chatLimit,
                            @Value("${app.rate-limit.chat.window-seconds:60}") long chatWindowSeconds) {
        this.chatLimit = Math.max(1, chatLimit);
        this.chatWindowSeconds = Math.max(1, chatWindowSeconds);
    }

    public synchronized RateLimitResult consumeChat(String username) {
        long now = Instant.now().getEpochSecond();
        long windowId = now / chatWindowSeconds;
        String key = "chat:" + username;

        WindowCounter counter = counters.get(key);
        if (counter == null || counter.windowId != windowId) {
            counter = new WindowCounter(windowId, 0);
            counters.put(key, counter);
        }

        if (counter.count >= chatLimit) {
            long retryAfterSeconds = ((windowId + 1) * chatWindowSeconds) - now;
            return new RateLimitResult(false, Math.max(1, retryAfterSeconds));
        }

        counter.count++;
        return new RateLimitResult(true, 0);
    }

    private static class WindowCounter {
        private final long windowId;
        private int count;

        private WindowCounter(long windowId, int count) {
            this.windowId = windowId;
            this.count = count;
        }
    }

    public record RateLimitResult(
            boolean allowed,
            long retryAfterSeconds
    ) {
    }
}
