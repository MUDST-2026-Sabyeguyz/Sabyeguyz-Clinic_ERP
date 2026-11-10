package com.sabyeguyz.clinicv2.idcard;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReadRateLimiterTest {
    @Test
    void allowsTheLimitPerMinutePerUserThenRecovers() {
        var clock = new ReadRequestStoreTest.MutableClock();
        var limiter = new ReadRateLimiter(clock, 3);
        assertTrue(limiter.tryAcquire(1L));
        assertTrue(limiter.tryAcquire(1L));
        assertTrue(limiter.tryAcquire(1L));
        assertFalse(limiter.tryAcquire(1L));
        assertTrue(limiter.tryAcquire(2L), "another user has their own allowance");
        clock.advance(Duration.ofSeconds(61));
        assertTrue(limiter.tryAcquire(1L));
    }
}
