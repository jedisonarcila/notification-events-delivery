package com.cobre.notifications.delivery.infrastructure.adapter.in.sqs.message;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Forma del mensaje de la cola {@code deliveries} (contracts/deliveries.md).
 * La publican los adaptadores de salida (fan-out y reintento) y la consume
 * el listener de entrada; vive en el paquete {@code adapter.in.sqs.message}
 * porque ese es su formato de deserialización final, aunque también se use
 * para serializar al publicar.
 */
public record DeliveryMessage(
        @JsonProperty("notification_event_id") String notificationEventId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("subscription_id") String subscriptionId,
        @JsonProperty("webhook_url") String webhookUrl,
        @JsonProperty("content") String content,
        @JsonProperty("delivery_date") Instant deliveryDate,
        @JsonProperty("attempt_count") int attemptCount
) {
}
