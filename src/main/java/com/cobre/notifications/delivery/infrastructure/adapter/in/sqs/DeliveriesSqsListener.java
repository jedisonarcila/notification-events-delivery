package com.cobre.notifications.delivery.infrastructure.adapter.in.sqs;

import com.cobre.notifications.delivery.core.dto.DeliveryCommand;
import com.cobre.notifications.delivery.core.ports.in.DispatchDeliveryUseCase;
import com.cobre.notifications.delivery.infrastructure.adapter.in.sqs.message.DeliveryMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Component;

/**
 * Entrada del dispatch (CU-2/CU-3). El mensaje original se confirma al
 * retornar sin excepción en todos los casos: éxito, fallo permanente (ambos
 * ya con desenlace terminal persistido) y fallo transitorio (donde
 * {@code DispatchDeliveryService} ya publicó, a través del puerto de
 * reintento, un mensaje nuevo con el intento incrementado — ver la nota de
 * diseño en {@code SqsRetryScheduler} sobre la aproximación usada mientras
 * no se dispone del receipt handle en el dominio).
 */
@Component
public class DeliveriesSqsListener {

    private final DispatchDeliveryUseCase dispatchDeliveryUseCase;

    public DeliveriesSqsListener(DispatchDeliveryUseCase dispatchDeliveryUseCase) {
        this.dispatchDeliveryUseCase = dispatchDeliveryUseCase;
    }

    @SqsListener("${notifier.deliveries-queue}")
    public void listen(DeliveryMessage message) {
        dispatchDeliveryUseCase.handle(toCommand(message));
    }

    private DeliveryCommand toCommand(DeliveryMessage message) {
        return new DeliveryCommand(
                message.notificationEventId(),
                message.clientId(),
                message.eventType(),
                message.subscriptionId(),
                message.webhookUrl(),
                message.content(),
                message.deliveryDate(),
                message.attemptCount()
        );
    }
}
