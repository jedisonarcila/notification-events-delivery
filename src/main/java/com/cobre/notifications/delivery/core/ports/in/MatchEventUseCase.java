package com.cobre.notifications.delivery.core.ports.in;

import com.cobre.notifications.delivery.core.domain.DeliveryTask;
import com.cobre.notifications.delivery.core.dto.RawEventCommand;

import java.util.List;

/**
 * Resuelve las suscripciones activas de un evento y produce una tarea de
 * entrega por cada una (matching + fan-out, CU-1).
 */
public interface MatchEventUseCase {

    /**
     * @return las tareas de entrega generadas (vacío si no hay suscriptores activos)
     */
    List<DeliveryTask> handle(RawEventCommand command);
}
