package com.cobre.notifications.delivery.core.utils;

import com.cobre.notifications.delivery.core.domain.HttpResult;

/**
 * Clasifica el resultado de un intento de entrega (RN-3 de spec-funcional.md):
 * transitorio = {timeout, 5xx, 429}; permanente = resto de 4xx; éxito = 2xx.
 */
public final class RetryDecision {

    private RetryDecision() {
    }

    public enum Classification {
        SUCCESS,
        TRANSIENT,
        PERMANENT
    }

    public static Classification classify(HttpResult result) {
        Integer statusCode = result.statusCode();

        if (statusCode == null) {
            return Classification.TRANSIENT;
        }
        if (statusCode >= 200 && statusCode < 300) {
            return Classification.SUCCESS;
        }
        if (statusCode == 429 || (statusCode >= 500 && statusCode < 600)) {
            return Classification.TRANSIENT;
        }
        return Classification.PERMANENT;
    }
}
