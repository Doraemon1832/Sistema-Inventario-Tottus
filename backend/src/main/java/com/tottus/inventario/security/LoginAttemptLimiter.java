package com.tottus.inventario.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-process throttle for repeated authentication attempts from one socket peer. */
@Service
public class LoginAttemptLimiter {
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptLimiter(
            @Value("${security.login.max-failures:5}") int maxAttempts,
            @Value("${security.login.window-seconds:900}") long windowSeconds) {
        this(maxAttempts, windowSeconds, Clock.systemUTC());
    }

    LoginAttemptLimiter(int maxAttempts, long windowSeconds, Clock clock) {
        if (maxAttempts < 1 || windowSeconds < 1) throw new IllegalArgumentException("Login throttle settings must be positive");
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
        this.clock = clock;
    }

    /** Atomically reserves an attempt so simultaneous requests cannot all pass the threshold check. */
    public void checkAndRecordAttempt(String remoteAddress) {
        String key = normalize(remoteAddress);
        Instant now = clock.instant();
        AtomicLong retryAfter = new AtomicLong();
        attempts.compute(key, (ignored, current) -> {
            if (current == null || Duration.between(current.startedAt(), now).compareTo(window) >= 0) {
                return new AttemptWindow(1, now);
            }
            if (current.attempts() >= maxAttempts) {
                long elapsed = Duration.between(current.startedAt(), now).getSeconds();
                retryAfter.set(Math.max(1, window.getSeconds() - elapsed));
                return current;
            }
            return new AttemptWindow(current.attempts() + 1, current.startedAt());
        });
        if (retryAfter.get() > 0) throw new LoginRateLimitException(retryAfter.get());
        cleanExpiredAndBoundSize(now);
    }

    public void recordSuccess(String remoteAddress) {
        attempts.remove(normalize(remoteAddress));
    }

    int trackedClientCountForTest() {
        return attempts.size();
    }

    private void cleanExpiredAndBoundSize(Instant now) {
        if (attempts.size() <= MAX_TRACKED_CLIENTS) return;
        Instant cutoff = now.minus(window);
        attempts.entrySet().removeIf(entry -> entry.getValue().startedAt().isBefore(cutoff));
        if (attempts.size() > MAX_TRACKED_CLIENTS) {
            attempts.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().startedAt()))
                    .ifPresent(oldest -> attempts.remove(oldest.getKey(), oldest.getValue()));
        }
    }

    private String normalize(String remoteAddress) {
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.trim();
    }

    private record AttemptWindow(int attempts, Instant startedAt) { }
}
