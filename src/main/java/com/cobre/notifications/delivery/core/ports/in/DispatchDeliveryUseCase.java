package com.cobre.notifications.delivery.core.ports.in;

import com.cobre.notifications.delivery.core.domain.DeliveryOutcome;
import com.cobre.notifications.delivery.core.dto.DeliveryCommand;

import java.util.Optional;

/**
 * Entrega una notificación y decide su desenlace (CU-2/CU-3).
 */
public interface DispatchDeliveryUseCase {

    /**
     * @return el desenlace terminal si la notificación quedó resuelta
     *         (completada o fallida), o vacío si se reencoló para reintento
     *         y todavía no hay desenlace
     */
    Optional<DeliveryOutcome> handle(DeliveryCommand command);
}
