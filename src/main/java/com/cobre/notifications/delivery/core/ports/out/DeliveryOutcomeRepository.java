package com.cobre.notifications.delivery.core.ports.out;

import com.cobre.notifications.delivery.core.domain.DeliveryOutcome;

import java.util.Optional;

/**
 * Persiste el desenlace terminal único de una notificación (constitución,
 * Principio IV). Toda implementación MUST escribir exactamente una vez por
 * {@code notificationEventId}.
 */
public interface DeliveryOutcomeRepository {

    void save(DeliveryOutcome outcome);

    Optional<DeliveryOutcome> findById(String notificationEventId);
}
