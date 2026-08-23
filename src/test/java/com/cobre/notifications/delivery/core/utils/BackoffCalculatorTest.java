package com.cobre.notifications.delivery.core.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackoffCalculatorTest {

    // base = min(30 * 2^intento, 3600); espera = base/2 + random(0, base/2)
    @ParameterizedTest
    @CsvSource({
            "0,30",
            "1,60",
            "2,120",
            "3,240",
            "4,480",
            "5,960",
    })
    void staysWithinEqualJitterBoundsForEachAttempt(int attempt, long expectedBase) {
        for (int i = 0; i < 200; i++) {
            long delay = BackoffCalculator.delaySeconds(attempt);
            assertThat(delay).isBetween(expectedBase / 2, expectedBase);
        }
    }

    @Test
    void capsBaseAt3600SecondsForLargeAttempts() {
        long delay = BackoffCalculator.delaySeconds(10);

        assertThat(delay).isBetween(1800L, 3600L);
    }

    @Test
    void isDeterministicWithAnInjectedRandom() {
        long delay = BackoffCalculator.delaySeconds(0, new Random(42));

        assertThat(delay).isBetween(15L, 30L);
    }

    @Test
    void rejectsNegativeAttempt() {
        assertThatThrownBy(() -> BackoffCalculator.delaySeconds(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
