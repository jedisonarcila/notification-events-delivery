package com.cobre.notifications.delivery.core.mapper;

import com.cobre.notifications.delivery.core.domain.DeliveryTask;
import com.cobre.notifications.delivery.core.domain.RawEvent;
import com.cobre.notifications.delivery.core.dto.DeliveryCommand;
import com.cobre.notifications.delivery.core.dto.RawEventCommand;

/** Traduce entre los DTOs de entrada del núcleo y sus modelos de dominio. */
public final class DomainMapper {

    private DomainMapper() {
    }

    public static RawEvent toDomain(RawEventCommand command) {
        return new RawEvent(
                command.eventId(),
                command.clientId(),
                command.eventType(),
                command.content(),
                command.deliveryDate()
        );
    }

    public static DeliveryTask toDomain(DeliveryCommand command) {
        return new DeliveryTask(
                command.notificationEventId(),
                command.clientId(),
                command.eventType(),
                command.subscriptionId(),
                command.webhookUrl(),
                command.content(),
                command.deliveryDate(),
                command.attemptCount()
        );
    }
}
