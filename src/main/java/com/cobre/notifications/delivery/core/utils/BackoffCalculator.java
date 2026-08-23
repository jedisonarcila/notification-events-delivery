package com.cobre.notifications.delivery.core.utils;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Backoff exponencial con equal jitter para reintentos de entrega
 * (spec-funcional.md RN-4; research.md §3).
 *
 * <p>{@code base = min(30 * 2^intento, 3600)};
 * {@code espera = base/2 + random(0, base/2)} segundos.</p>
 */
public final class BackoffCalculator {

    private static final long BASE_SECONDS = 30;
    private static final long MAX_BASE_SECONDS = 3600;
    private static final int ATTEMPT_AT_WHICH_CAP_APPLIES = 7; // 30 * 2^7 = 3840 > 3600

    private BackoffCalculator() {
    }

    public static long delaySeconds(int attempt) {
        return delaySeconds(attempt, ThreadLocalRandom.current());
    }

    static long delaySeconds(int attempt, Random random) {
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must be >= 0");
        }

        long base = attempt >= ATTEMPT_AT_WHICH_CAP_APPLIES
                ? MAX_BASE_SECONDS
                : Math.min(BASE_SECONDS << attempt, MAX_BASE_SECONDS);
        long half = base / 2;
        return half + random.nextLong(half + 1);
    }
}
