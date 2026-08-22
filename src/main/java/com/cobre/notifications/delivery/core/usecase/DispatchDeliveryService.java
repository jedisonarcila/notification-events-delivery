package com.cobre.notifications.delivery.core.usecase;

import com.cobre.notifications.delivery.core.domain.*;
import com.cobre.notifications.delivery.core.dto.DeliveryCommand;
import com.cobre.notifications.delivery.core.mapper.DomainMapper;
import com.cobre.notifications.delivery.core.ports.in.DispatchDeliveryUseCase;
import com.cobre.notifications.delivery.core.ports.out.*;
import com.cobre.notifications.delivery.core.utils.BackoffCalculator;
import com.cobre.notifications.delivery.core.utils.RetryDecision;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Dispatcher: entrega una notificación, clasifica el resultado y decide
 * reintento o desenlace terminal (CU-2/CU-3 de spec-funcional.md).
 */
public class DispatchDeliveryService implements DispatchDeliveryUseCase {

    private static final int MAX_ATTEMPTS = 6;
    private static final long OUTCOME_RETENTION_DAYS = 90;

    private final WebhookClient webhookClient;
    private final SignatureResolver signatureResolver;
    private final DeliveryOutcomeRepository deliveryOutcomeRepository;
    private final DeliveryRetryScheduler deliveryRetryScheduler;
    private final DeliveryMetrics deliveryMetrics;

    public DispatchDeliveryService(
            WebhookClient webhookClient,
            SignatureResolver signatureResolver,
            DeliveryOutcomeRepository deliveryOutcomeRepository,
            DeliveryRetryScheduler deliveryRetryScheduler,
            DeliveryMetrics deliveryMetrics
    ) {
        this.webhookClient = webhookClient;
        this.signatureResolver = signatureResolver;
        this.deliveryOutcomeRepository = deliveryOutcomeRepository;
        this.deliveryRetryScheduler = deliveryRetryScheduler;
        this.deliveryMetrics = deliveryMetrics;
    }

    @Override
    public Optional<DeliveryOutcome> handle(DeliveryCommand command) {
        DeliveryTask task = DomainMapper.toDomain(command);

        // Un desenlace ya existente (completado o fallido) es terminal: no se
        // reintenta ni se vuelve a escribir (constitución, Principio IV).
        Optional<DeliveryOutcome> existing = deliveryOutcomeRepository.findById(task.notificationEventId());
        if (existing.isPresent()) {
            return existing;
        }

        String signature = signatureResolver.sign(task.clientId(), task.content());
        HttpResult result = webhookClient.post(task.webhookUrl(), task.content(), signature);

        return switch (RetryDecision.classify(result)) {
            case SUCCESS -> Optional.of(persistTerminal(task, DeliveryStatus.COMPLETED, null));
            case PERMANENT -> Optional.of(persistTerminal(task, DeliveryStatus.FAILED, describe(result)));
            case TRANSIENT -> handleTransient(task, result);
        };
    }

    private Optional<DeliveryOutcome> handleTransient(DeliveryTask task, HttpResult result) {
        if (task.attemptCount() >= MAX_ATTEMPTS) {
            return Optional.of(persistTerminal(task, DeliveryStatus.FAILED, describe(result)));
        }

        long delaySeconds = BackoffCalculator.delaySeconds(task.attemptCount());
        DeliveryTask rescheduled = task.withAttemptCount(task.attemptCount() + 1);
        deliveryRetryScheduler.reschedule(rescheduled, delaySeconds);
        return Optional.empty();
    }

    private DeliveryOutcome persistTerminal(DeliveryTask task, DeliveryStatus status, String lastError) {
        Instant now = Instant.now();
        DeliveryOutcome outcome = new DeliveryOutcome(
                task.notificationEventId(),
                task.clientId(),
                task.eventType(),
                status,
                now,
                task.attemptCount(),
                lastError,
                task.webhookUrl(),
                now.plus(OUTCOME_RETENTION_DAYS, ChronoUnit.DAYS).getEpochSecond()
        );
        deliveryOutcomeRepository.save(outcome);
        deliveryMetrics.result(task.clientId(), task.eventType(), status);
        return outcome;
    }

    private String describe(HttpResult result) {
        if (result.statusCode() == null) {
            return "sin respuesta (timeout o error de red)";
        }
        return "HTTP " + result.statusCode() + (result.body() != null ? ": " + result.body() : "");
    }
}
