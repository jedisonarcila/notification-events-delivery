package com.cobre.notifications.delivery.core.ports.out;

import com.cobre.notifications.delivery.core.domain.DeliveryStatus;

/**
 * Métricas de operación. Ninguna implementación MUST promover más
 * dimensiones que {@code client_id} y {@code event_type} (constitución,
 * stack tecnológico).
 */
public interface DeliveryMetrics {

    void noSubscribers(String clientId, String eventType);

    void result(String clientId, String eventType, DeliveryStatus outcome);
}
