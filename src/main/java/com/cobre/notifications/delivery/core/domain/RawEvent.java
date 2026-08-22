package com.cobre.notifications.delivery.core.domain;

import java.time.Instant;

/**
 * Evento de negocio entrante, tal como llega desde la cola de eventos crudos.
 *
 * @param eventId      identificador propio del evento, provisto por el productor
 * @param clientId     cliente dueño del evento; PK de aislamiento A01
 * @param eventType    tipo de evento (e.g. "payment.created")
 * @param content      payload de negocio a entregar (opaco para el dominio)
 * @param deliveryDate fecha del evento de origen
 */
public record RawEvent(
        String eventId,
        String clientId,
        String eventType,
        String content,
        Instant deliveryDate
) {
}
