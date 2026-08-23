package com.cobre.notifications.delivery.core.ports.out;

import com.cobre.notifications.delivery.core.domain.DeliveryTask;

/**
 * Reencola una tarea de entrega para reintento, delegando la espera en la
 * infraestructura de mensajería (constitución, Principio V — nunca
 * {@code Thread.sleep}).
 */
public interface DeliveryRetryScheduler {

    void reschedule(DeliveryTask task, long delaySeconds);
}
