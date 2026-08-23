package com.cobre.notifications.delivery.core.usecase;

import com.cobre.notifications.delivery.core.domain.DeliveryOutcome;
import com.cobre.notifications.delivery.core.domain.DeliveryStatus;
import com.cobre.notifications.delivery.core.domain.DeliveryTask;
import com.cobre.notifications.delivery.core.domain.HttpResult;
import com.cobre.notifications.delivery.core.dto.DeliveryCommand;
import com.cobre.notifications.delivery.core.ports.out.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchDeliveryServiceTest {

    private static final String NOTIFICATION_EVENT_ID = "notif-1";
    private static final String WEBHOOK_URL = "https://hook.example.com/receive";

    @Mock
    WebhookClient webhookClient;
    @Mock
    SignatureResolver signatureResolver;
    @Mock
    DeliveryOutcomeRepository deliveryOutcomeRepository;
    @Mock
    DeliveryRetryScheduler deliveryRetryScheduler;
    @Mock
    DeliveryMetrics deliveryMetrics;

    DispatchDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new DispatchDeliveryService(
                webhookClient, signatureResolver, deliveryOutcomeRepository, deliveryRetryScheduler, deliveryMetrics
        );
        lenient().when(deliveryOutcomeRepository.findById(NOTIFICATION_EVENT_ID)).thenReturn(Optional.empty());
        lenient().when(signatureResolver.sign(eq("C1"), anyString())).thenReturn("sig-123");
    }

    private DeliveryCommand command(int attemptCount) {
        return new DeliveryCommand(
                NOTIFICATION_EVENT_ID, "C1", "payment.created", "sub-1",
                WEBHOOK_URL, "{\"amount\":1000}", Instant.parse("2026-08-22T10:00:00Z"), attemptCount
        );
    }

    @Test
    void successfulResponsePersistsExactlyOneCompletedOutcome() {
        when(webhookClient.post(WEBHOOK_URL, "{\"amount\":1000}", "sig-123")).thenReturn(new HttpResult(200, "ok"));

        Optional<DeliveryOutcome> outcome = service.handle(command(0));

        assertThat(outcome).isPresent();
        assertThat(outcome.get().deliveryStatus()).isEqualTo(DeliveryStatus.COMPLETED);
        verify(deliveryOutcomeRepository, times(1)).save(any());
        verify(deliveryRetryScheduler, never()).reschedule(any(), anyLong());
        verify(deliveryMetrics).result("C1", "payment.created", DeliveryStatus.COMPLETED);
    }

    @Test
    void doesNotRedeliverWhenAnOutcomeAlreadyExists() {
        DeliveryOutcome existing = new DeliveryOutcome(
                NOTIFICATION_EVENT_ID, "C1", "payment.created", DeliveryStatus.COMPLETED,
                Instant.parse("2026-08-22T10:00:05Z"), 0, null, WEBHOOK_URL, 0L
        );
        when(deliveryOutcomeRepository.findById(NOTIFICATION_EVENT_ID)).thenReturn(Optional.of(existing));

        Optional<DeliveryOutcome> outcome = service.handle(command(0));

        assertThat(outcome).contains(existing);
        verifyNoInteractions(webhookClient, signatureResolver, deliveryRetryScheduler);
        verify(deliveryOutcomeRepository, never()).save(any());
    }

    @Test
    void transientFailureReschedulesWithIncrementedAttemptCountAndNoOutcomeYet() {
        when(webhookClient.post(anyString(), anyString(), anyString())).thenReturn(new HttpResult(503, null));

        Optional<DeliveryOutcome> outcome = service.handle(command(0));

        assertThat(outcome).isEmpty();
        verify(deliveryOutcomeRepository, never()).save(any());
        verifyNoInteractions(deliveryMetrics);

        ArgumentCaptor<DeliveryTask> taskCaptor = ArgumentCaptor.forClass(DeliveryTask.class);
        ArgumentCaptor<Long> delayCaptor = ArgumentCaptor.forClass(Long.class);
        verify(deliveryRetryScheduler).reschedule(taskCaptor.capture(), delayCaptor.capture());

        assertThat(taskCaptor.getValue().attemptCount()).isEqualTo(1);
        assertThat(delayCaptor.getValue()).isBetween(15L, 30L);
    }

    @Test
    void rateLimitResponseIsTreatedAsTransientAndRescheduled() {
        when(webhookClient.post(anyString(), anyString(), anyString())).thenReturn(new HttpResult(429, null));

        Optional<DeliveryOutcome> outcome = service.handle(command(0));

        assertThat(outcome).isEmpty();
        verify(deliveryRetryScheduler).reschedule(any(), anyLong());
        verify(deliveryOutcomeRepository, never()).save(any());
    }

    @Test
    void missingResponseIsTreatedAsTransientAndRescheduled() {
        when(webhookClient.post(anyString(), anyString(), anyString())).thenReturn(new HttpResult(null, null));

        Optional<DeliveryOutcome> outcome = service.handle(command(0));

        assertThat(outcome).isEmpty();
        verify(deliveryRetryScheduler).reschedule(any(), anyLong());
    }

    @Test
    void exhaustedRetriesPersistFailedOutcomeInsteadOfReschedulingAgain() {
        when(webhookClient.post(anyString(), anyString(), anyString())).thenReturn(new HttpResult(503, null));

        Optional<DeliveryOutcome> outcome = service.handle(command(6));

        assertThat(outcome).isPresent();
        assertThat(outcome.get().deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(outcome.get().attemptCount()).isEqualTo(6);
        verify(deliveryRetryScheduler, never()).reschedule(any(), anyLong());
        verify(deliveryOutcomeRepository).save(any());
        verify(deliveryMetrics).result("C1", "payment.created", DeliveryStatus.FAILED);
    }

    @Test
    void permanentFailurePersistsFailedOutcomeImmediatelyWithoutRetry() {
        when(webhookClient.post(anyString(), anyString(), anyString())).thenReturn(new HttpResult(400, "bad request"));

        Optional<DeliveryOutcome> outcome = service.handle(command(0));

        assertThat(outcome).isPresent();
        assertThat(outcome.get().deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(outcome.get().lastError()).contains("400");
        verify(deliveryRetryScheduler, never()).reschedule(any(), anyLong());
        verify(deliveryOutcomeRepository).save(any());
        verify(deliveryMetrics).result("C1", "payment.created", DeliveryStatus.FAILED);
    }
}
