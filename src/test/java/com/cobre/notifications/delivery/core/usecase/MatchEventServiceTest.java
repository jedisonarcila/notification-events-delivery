package com.cobre.notifications.delivery.core.usecase;

import com.cobre.notifications.delivery.core.domain.DeliveryTask;
import com.cobre.notifications.delivery.core.domain.Subscription;
import com.cobre.notifications.delivery.core.dto.RawEventCommand;
import com.cobre.notifications.delivery.core.ports.out.DeliveryMetrics;
import com.cobre.notifications.delivery.core.ports.out.DeliveryTaskPublisher;
import com.cobre.notifications.delivery.core.ports.out.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchEventServiceTest {

    @Mock
    SubscriptionRepository subscriptionRepository;
    @Mock
    DeliveryTaskPublisher deliveryTaskPublisher;
    @Mock
    DeliveryMetrics deliveryMetrics;

    MatchEventService service;

    @BeforeEach
    void setUp() {
        service = new MatchEventService(subscriptionRepository, deliveryTaskPublisher, deliveryMetrics);
    }

    private RawEventCommand rawEvent(String eventId, String clientId, String eventType) {
        return new RawEventCommand(eventId, clientId, eventType, "{\"amount\":1000}", Instant.parse("2026-08-22T10:00:00Z"));
    }

    private Subscription activeSubscription(String subscriptionId, String clientId, String eventType) {
        return new Subscription(subscriptionId, clientId, eventType, "https://" + subscriptionId + ".example.com/hook", true);
    }

    @Test
    void fansOutOneDeliveryTaskPerActiveSubscription() {
        RawEventCommand event = rawEvent("evt-1", "C1", "payment.created");
        when(subscriptionRepository.findActive("C1", "payment.created")).thenReturn(List.of(
                activeSubscription("sub-1", "C1", "payment.created"),
                activeSubscription("sub-2", "C1", "payment.created")
        ));

        List<DeliveryTask> tasks = service.handle(event);

        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(DeliveryTask::subscriptionId)
                .containsExactlyInAnyOrder("sub-1", "sub-2");
        assertThat(tasks).allSatisfy(task -> assertThat(task.attemptCount()).isZero());
        verify(deliveryTaskPublisher, times(2)).publish(any());
        verifyNoInteractions(deliveryMetrics);
    }

    @Test
    void eachDeliveryTaskGetsItsOwnDeterministicNotificationEventId() {
        RawEventCommand event = rawEvent("evt-1", "C1", "payment.created");
        when(subscriptionRepository.findActive("C1", "payment.created")).thenReturn(List.of(
                activeSubscription("sub-1", "C1", "payment.created"),
                activeSubscription("sub-2", "C1", "payment.created")
        ));

        List<DeliveryTask> tasks = service.handle(event);

        assertThat(tasks.get(0).notificationEventId()).isNotEqualTo(tasks.get(1).notificationEventId());
    }

    @Test
    void reprocessingTheSameEventProducesTheSameNotificationEventIds() {
        RawEventCommand event = rawEvent("evt-1", "C1", "payment.created");
        when(subscriptionRepository.findActive("C1", "payment.created")).thenReturn(List.of(
                activeSubscription("sub-1", "C1", "payment.created")
        ));

        List<DeliveryTask> firstRun = service.handle(event);
        List<DeliveryTask> secondRun = service.handle(event);

        assertThat(firstRun.get(0).notificationEventId()).isEqualTo(secondRun.get(0).notificationEventId());
    }

    @Test
    void queriesSubscriptionsScopedToTheEventClientIdOnly() {
        RawEventCommand event = rawEvent("evt-1", "C1", "payment.created");
        when(subscriptionRepository.findActive(anyString(), anyString())).thenReturn(List.of());

        service.handle(event);

        verify(subscriptionRepository).findActive("C1", "payment.created");
        verify(subscriptionRepository, never()).findActive(argThat(clientId -> !"C1".equals(clientId)), anyString());
    }

    @Test
    void discardsSilentlyAndIncrementsMetricWhenNoActiveSubscriptions() {
        RawEventCommand event = rawEvent("evt-1", "C1", "unknown");
        when(subscriptionRepository.findActive("C1", "unknown")).thenReturn(List.of());

        List<DeliveryTask> tasks = service.handle(event);

        assertThat(tasks).isEmpty();
        verifyNoInteractions(deliveryTaskPublisher);
        verify(deliveryMetrics).noSubscribers("C1", "unknown");
    }
}
