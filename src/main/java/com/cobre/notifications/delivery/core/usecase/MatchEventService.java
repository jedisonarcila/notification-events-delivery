package com.cobre.notifications.delivery.core.usecase;

import com.cobre.notifications.delivery.core.domain.DeliveryTask;
import com.cobre.notifications.delivery.core.domain.RawEvent;
import com.cobre.notifications.delivery.core.domain.Subscription;
import com.cobre.notifications.delivery.core.dto.RawEventCommand;
import com.cobre.notifications.delivery.core.mapper.DomainMapper;
import com.cobre.notifications.delivery.core.ports.in.MatchEventUseCase;
import com.cobre.notifications.delivery.core.ports.out.DeliveryMetrics;
import com.cobre.notifications.delivery.core.ports.out.DeliveryTaskPublisher;
import com.cobre.notifications.delivery.core.ports.out.SubscriptionRepository;
import com.cobre.notifications.delivery.core.utils.NotificationIdFactory;

import java.util.List;

/**
 * Matcher: resuelve las suscripciones activas de un evento y genera una
 * tarea de entrega por cada una (CU-1 de spec-funcional.md).
 */
public class MatchEventService implements MatchEventUseCase {

    private final SubscriptionRepository subscriptionRepository;
    private final DeliveryTaskPublisher deliveryTaskPublisher;
    private final DeliveryMetrics deliveryMetrics;

    public MatchEventService(
            SubscriptionRepository subscriptionRepository,
            DeliveryTaskPublisher deliveryTaskPublisher,
            DeliveryMetrics deliveryMetrics
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryTaskPublisher = deliveryTaskPublisher;
        this.deliveryMetrics = deliveryMetrics;
    }

    @Override
    public List<DeliveryTask> handle(RawEventCommand command) {
        RawEvent event = DomainMapper.toDomain(command);

        // findActive() ya garantiza el aislamiento A01 (PK=clientId) y el
        // filtro active=true; el matcher no re-filtra, confía en el contrato del puerto.
        List<Subscription> activeSubscriptions = subscriptionRepository.findActive(event.clientId(), event.eventType());

        if (activeSubscriptions.isEmpty()) {
            deliveryMetrics.noSubscribers(event.clientId(), event.eventType());
            return List.of();
        }

        List<DeliveryTask> tasks = activeSubscriptions.stream()
                .map(subscription -> toDeliveryTask(event, subscription))
                .toList();

        tasks.forEach(deliveryTaskPublisher::publish);
        return tasks;
    }

    private DeliveryTask toDeliveryTask(RawEvent event, Subscription subscription) {
        String notificationEventId = NotificationIdFactory.create(event.eventId(), subscription.subscriptionId());
        return new DeliveryTask(
                notificationEventId,
                event.clientId(),
                event.eventType(),
                subscription.subscriptionId(),
                subscription.webhookUrl(),
                event.content(),
                event.deliveryDate(),
                0
        );
    }
}
