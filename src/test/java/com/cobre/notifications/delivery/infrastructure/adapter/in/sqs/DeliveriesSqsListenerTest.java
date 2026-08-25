package com.cobre.notifications.delivery.infrastructure.adapter.in.sqs;

import com.cobre.notifications.delivery.core.dto.DeliveryCommand;
import com.cobre.notifications.delivery.core.ports.in.DispatchDeliveryUseCase;
import com.cobre.notifications.delivery.infrastructure.adapter.in.sqs.message.DeliveryMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato del adaptador asíncrono: sobre qué tipo de hilo corre la entrega y,
 * sobre todo, qué le pasa al acknowledge cuando el caso de uso falla.
 *
 * <p>Esto último es lo que cambia al pasar de listener bloqueante a asíncrono:
 * antes la excepción salía por el {@code return} del método; ahora viaja dentro
 * del {@code CompletableFuture}. Si el futuro completara normalmente pese al
 * fallo, Spring Cloud AWS borraría el mensaje y perderíamos la entrega —
 * rompiendo la garantía at-least-once. De ahí el test.</p>
 */
class DeliveriesSqsListenerTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    private static DeliveryMessage message() {
        return new DeliveryMessage(
                "notif-1", "C1", "payment.created", "sub-1",
                "https://hook.example.com/receive", "{\"amount\":100}",
                Instant.parse("2026-08-24T10:15:30Z"), 0);
    }

    @Test
    void runsTheDeliveryOnAVirtualThread() {
        AtomicBoolean wasVirtual = new AtomicBoolean(false);
        DispatchDeliveryUseCase useCase = command -> {
            wasVirtual.set(Thread.currentThread().isVirtual());
            return Optional.empty();
        };

        new DeliveriesSqsListener(useCase, executor).listen(message()).join();

        assertThat(wasVirtual).isTrue();
    }

    @Test
    void doesNotRunOnTheCallingThread() {
        AtomicReference<Thread> executionThread = new AtomicReference<>();
        DispatchDeliveryUseCase useCase = command -> {
            executionThread.set(Thread.currentThread());
            return Optional.empty();
        };

        new DeliveriesSqsListener(useCase, executor).listen(message()).join();

        assertThat(executionThread.get()).isNotSameAs(Thread.currentThread());
    }

    @Test
    void mapsEveryMessageFieldIntoTheCommand() {
        AtomicReference<DeliveryCommand> captured = new AtomicReference<>();
        DispatchDeliveryUseCase useCase = command -> {
            captured.set(command);
            return Optional.empty();
        };

        new DeliveriesSqsListener(useCase, executor).listen(message()).join();

        DeliveryCommand command = captured.get();
        assertThat(command.notificationEventId()).isEqualTo("notif-1");
        assertThat(command.clientId()).isEqualTo("C1");
        assertThat(command.eventType()).isEqualTo("payment.created");
        assertThat(command.subscriptionId()).isEqualTo("sub-1");
        assertThat(command.webhookUrl()).isEqualTo("https://hook.example.com/receive");
        assertThat(command.content()).isEqualTo("{\"amount\":100}");
        assertThat(command.deliveryDate()).isEqualTo(Instant.parse("2026-08-24T10:15:30Z"));
        assertThat(command.attemptCount()).isZero();
    }

    @Test
    void completesTheFutureExceptionallySoTheMessageIsNotAcknowledged() {
        DispatchDeliveryUseCase failing = command -> {
            throw new IllegalStateException("DynamoDB no disponible");
        };

        CompletableFuture<Void> future = new DeliveriesSqsListener(failing, executor).listen(message());

        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("DynamoDB no disponible");
        assertThat(future.isCompletedExceptionally()).isTrue();
    }

    @Test
    void completesNormallyWhenTheUseCaseSucceeds() {
        DispatchDeliveryUseCase succeeding = command -> Optional.empty();

        CompletableFuture<Void> future = new DeliveriesSqsListener(succeeding, executor).listen(message());

        assertThat(future.join()).isNull();
        assertThat(future.isCompletedExceptionally()).isFalse();
    }
}
