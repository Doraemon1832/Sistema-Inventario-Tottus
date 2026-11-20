package com.tottus.inventario.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptLimiterTest {
    private static final String IP = "192.0.2.10";

    @Test
    void limitaTrasCincoIntentosYExponeTiempoRestante() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(5, 900, Clock.systemUTC());
        for (int i = 0; i < 5; i++) limiter.checkAndRecordAttempt(IP);

        LoginRateLimitException error = assertThrows(LoginRateLimitException.class,
                () -> limiter.checkAndRecordAttempt(IP));

        assertEquals(900, error.getRetryAfterSeconds());
        assertDoesNotThrow(() -> limiter.checkAndRecordAttempt("192.0.2.11"));
    }

    @Test
    void loginCorrectoLimpiaElContadorDeLaIp() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(2, 900, Clock.systemUTC());
        limiter.checkAndRecordAttempt(IP);
        limiter.checkAndRecordAttempt(IP);

        limiter.recordSuccess(IP);

        assertDoesNotThrow(() -> limiter.checkAndRecordAttempt(IP));
    }

    @Test
    void permiteIntentosAlVencerLaVentana() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-14T12:00:00Z"));
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(1, 900, clock);
        limiter.checkAndRecordAttempt(IP);
        assertThrows(LoginRateLimitException.class, () -> limiter.checkAndRecordAttempt(IP));

        clock.advance(Duration.ofSeconds(900));

        assertDoesNotThrow(() -> limiter.checkAndRecordAttempt(IP));
    }

    @Test
    void concurrenciaNoPermiteSuperarElLimiteDeIntentos() throws Exception {
        int limit = 5;
        int contenders = 32;
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(limit, 900, Clock.systemUTC());
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger limited = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(contenders)) {
            for (int i = 0; i < contenders; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        limiter.checkAndRecordAttempt(IP);
                        allowed.incrementAndGet();
                    } catch (LoginRateLimitException ex) {
                        limited.incrementAndGet();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                });
            }
            ready.await();
            start.countDown();
        }

        assertEquals(limit, allowed.get());
        assertEquals(contenders - limit, limited.get());
    }

    @Test
    void mantieneAcotadoElNumeroDeIpsEnMemoria() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(5, 900, Clock.systemUTC());
        for (int i = 0; i < 10_050; i++) limiter.checkAndRecordAttempt("198.51.100." + i);

        assertEquals(10_000, limiter.trackedClientCountForTest());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
    }
}
