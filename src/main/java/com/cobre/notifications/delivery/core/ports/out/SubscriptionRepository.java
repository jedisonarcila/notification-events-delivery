package com.cobre.notifications.delivery.core.ports.out;

import com.cobre.notifications.delivery.core.domain.Subscription;

import java.util.List;

/**
 * Resuelve las suscripciones activas de un cliente para un tipo de evento.
 * Toda implementación MUST acotar la consulta por {@code clientId}
 * (aislamiento A01, constitución Principio VI) y devolver únicamente
 * suscripciones con {@code active = true}.
 */
public interface SubscriptionRepository {

    List<Subscription> findActive(String clientId, String eventType);
}
