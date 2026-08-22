package com.cobre.notifications.delivery.core.domain;

import java.time.Instant;

/**
 * Intención de notificar un {@link RawEvent} concreto a una
 * {@link Subscription} concreta. Es lo que viaja por la cola de entregas.
 *
 * @param notificationEventId {@code hash(eventId + subscriptionId)} — determinista
 * @param clientId            heredado del evento/suscripción; PK de aislamiento A01
 * @param eventType           heredado del evento
 * @param subscriptionId      suscripción destino
 * @param webhookUrl          snapshot del destino al momento del fan-out
 * @param content             payload a firmar y enviar
 * @param deliveryDate        heredado del evento
 * @param attemptCount        número de intentos previos ya realizados (0 en la primera entrega)
 */
public record DeliveryTask(
        String notificationEventId,
        String clientId,
        String eventType,
        String subscriptionId,
        String webhookUrl,
        String content,
        Instant deliveryDate,
        int attemptCount
) {

    /** Copia esta tarea con un nuevo contador de intentos, para reencolarla. */
    public DeliveryTask withAttemptCount(int newAttemptCount) {
        return new DeliveryTask(
                notificationEventId, clientId, eventType, subscriptionId,
                webhookUrl, content, deliveryDate, newAttemptCount
        );
    }
}
