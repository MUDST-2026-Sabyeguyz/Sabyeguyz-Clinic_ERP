package com.sabyeguyz.clinicv2.idcard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A user may start only a few card reads per minute. */
@Component
public class ReadRateLimiter {
    private final Clock clock;
    private final int maxPerMinute;
    private final Map<Long, ArrayDeque<Instant>> starts = new ConcurrentHashMap<>();

    @Autowired
    public ReadRateLimiter() {
        this(Clock.systemUTC(), 20);
    }

    public ReadRateLimiter(Clock clock, int maxPerMinute) {
        this.clock = clock;
        this.maxPerMinute = maxPerMinute;
    }

    public boolean tryAcquire(Long userId) {
        ArrayDeque<Instant> window = starts.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (window) {
            Instant now = clock.instant();
            while (!window.isEmpty() && window.peekFirst().isBefore(now.minus(Duration.ofMinutes(1)))) window.pollFirst();
            if (window.size() >= maxPerMinute) return false;
            window.addLast(now);
            return true;
        }
    }
}
