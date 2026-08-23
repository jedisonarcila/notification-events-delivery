package com.cobre.notifications.delivery.core.dto;

import java.time.Instant;

/**
 * Entrada al {@code DispatchDeliveryUseCase}. Desacopla el puerto de entrada
 * del modelo de dominio {@code DeliveryTask}.
 */
public record DeliveryCommand(
        String notificationEventId,
        String clientId,
        String eventType,
        String subscriptionId,
        String webhookUrl,
        String content,
        Instant deliveryDate,
        int attemptCount
) {
}
