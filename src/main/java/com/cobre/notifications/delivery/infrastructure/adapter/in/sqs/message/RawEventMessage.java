package com.cobre.notifications.delivery.infrastructure.adapter.in.sqs.message;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Forma del mensaje de la cola {@code raw-events} (contracts/raw-events.md). */
public record RawEventMessage(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("content") String content,
        @JsonProperty("delivery_date") Instant deliveryDate
) {
}
