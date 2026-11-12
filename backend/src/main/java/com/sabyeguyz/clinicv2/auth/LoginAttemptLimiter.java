package com.sabyeguyz.clinicv2.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Slows password guessing: after {@value #MAX_FAILURES} failed logins for the same account name within
 * the window, further attempts are refused until the lock expires. In memory per instance, so a restart
 * clears it; put a shared limiter (nginx or an ingress) in front when running several replicas.
 */
@Component
public class LoginAttemptLimiter {
    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED = 10_000;

    private record Entry(int failures, Instant first) {}

    private final ConcurrentHashMap<String, Entry> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String account) {
        Entry entry = attempts.get(key(account));

        if (entry == null) return false;

        if (entry.first().plus(WINDOW).isBefore(Instant.now())) {
            attempts.remove(key(account), entry);

            return false;
        }

        return entry.failures() >= MAX_FAILURES;
    }

    public void recordFailure(String account) {
        if (attempts.size() > MAX_TRACKED) attempts.clear();

        Instant now = Instant.now();
        attempts.merge(
                key(account),
                new Entry(1, now),
                (old, ignored) -> old.first().plus(WINDOW).isBefore(now)
                        ? new Entry(1, now)
                        : new Entry(old.failures() + 1, old.first()));
    }

    public void recordSuccess(String account) {
        attempts.remove(key(account));
    }

    private static String key(String account) {
        return account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
    }
}
