package com.cobre.notifications.delivery.infrastructure.adapter.out.sqs;

import com.cobre.notifications.delivery.application.config.DeliveryProperties;
import com.cobre.notifications.delivery.core.domain.DeliveryTask;
import com.cobre.notifications.delivery.core.ports.out.DeliveryTaskPublisher;
import com.cobre.notifications.delivery.infrastructure.adapter.in.sqs.message.DeliveryMessage;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.stereotype.Component;

/** Publica una tarea de entrega recién generada por el fan-out en {@code deliveries}. */
@Component
public class SqsDeliveryPublisher implements DeliveryTaskPublisher {

    private final SqsTemplate sqsTemplate;
    private final DeliveryProperties properties;

    public SqsDeliveryPublisher(SqsTemplate sqsTemplate, DeliveryProperties properties) {
        this.sqsTemplate = sqsTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(DeliveryTask task) {
        sqsTemplate.send(options -> options
                .queue(properties.getDeliveriesQueue())
                .payload(toMessage(task)));
    }

    static DeliveryMessage toMessage(DeliveryTask task) {
        return new DeliveryMessage(
                task.notificationEventId(),
                task.clientId(),
                task.eventType(),
                task.subscriptionId(),
                task.webhookUrl(),
                task.content(),
                task.deliveryDate(),
                task.attemptCount()
        );
    }
}
