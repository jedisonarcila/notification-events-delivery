package com.cobre.notifications.delivery.core.ports.out;

import com.cobre.notifications.delivery.core.domain.DeliveryTask;

/** Publica una tarea de entrega recién generada por el fan-out. */
public interface DeliveryTaskPublisher {

    void publish(DeliveryTask task);
}
