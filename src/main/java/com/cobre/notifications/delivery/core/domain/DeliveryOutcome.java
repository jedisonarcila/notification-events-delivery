package com.cobre.notifications.delivery.core.domain;

import java.time.Instant;

/**
 * Registro terminal único por notificación (constitución, Principio IV: una
 * sola escritura, nunca un estado intermedio).
 *
 * @param notificationEventId igual al de {@link DeliveryTask} — clave de idempotencia
 * @param clientId            aislamiento A01
 * @param eventType           para métricas/consulta
 * @param deliveryStatus      {@code COMPLETED} o {@code FAILED} — sin estados intermedios
 * @param deliveryDate        momento del resultado final
 * @param attemptCount        intentos realizados hasta el desenlace
 * @param lastError           presente solo si {@code FAILED} (o hubo reintentos previos)
 * @param webhookUrl          destino al que se intentó entregar
 * @param ttl                 epoch seconds de expiración (deliveryDate + 90 días)
 */
public record DeliveryOutcome(
        String notificationEventId,
        String clientId,
        String eventType,
        DeliveryStatus deliveryStatus,
        Instant deliveryDate,
        int attemptCount,
        String lastError,
        String webhookUrl,
        long ttl
) {
}
