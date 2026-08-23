package com.cobre.notifications.delivery.core.dto;

import java.time.Instant;

/**
 * Entrada al {@code MatchEventUseCase}. Desacopla el puerto de entrada del
 * modelo de dominio {@code RawEvent} para que este último pueda evolucionar
 * sin cambiar el contrato del caso de uso.
 */
public record RawEventCommand(
        String eventId,
        String clientId,
        String eventType,
        String content,
        Instant deliveryDate
) {
}
