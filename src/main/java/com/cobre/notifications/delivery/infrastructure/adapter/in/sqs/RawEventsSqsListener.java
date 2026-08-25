package com.cobre.notifications.delivery.infrastructure.adapter.in.sqs;

import com.cobre.notifications.delivery.core.dto.RawEventCommand;
import com.cobre.notifications.delivery.core.ports.in.MatchEventUseCase;
import com.cobre.notifications.delivery.infrastructure.adapter.in.sqs.message.RawEventMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Entrada del fan-out (CU-1).
 *
 * <p>Devuelve {@code CompletableFuture} por la misma razón que
 * {@code DeliveriesSqsListener}: registrarse como listener asíncrono para
 * ejecutar sobre virtual threads en vez de sobre los hilos de plataforma del
 * contenedor (ver {@code SqsContainerConfig}).</p>
 *
 * <p>El mensaje se confirma (borra) únicamente si el futuro completa sin
 * excepción. Como {@code MatchEventService} encola cada tarea de forma
 * síncrona y propaga cualquier fallo de publicación, esto sigue satisfaciendo
 * FR-004 (confirmar solo tras encolar todas las tareas) sin lógica adicional
 * aquí.</p>
 */
@Component
public class RawEventsSqsListener {

    private final MatchEventUseCase matchEventUseCase;
    private final ExecutorService sqsVirtualThreadExecutor;

    public RawEventsSqsListener(
            MatchEventUseCase matchEventUseCase,
            ExecutorService sqsVirtualThreadExecutor
    ) {
        this.matchEventUseCase = matchEventUseCase;
        this.sqsVirtualThreadExecutor = sqsVirtualThreadExecutor;
    }

    @SqsListener("${notifier.raw-events-queue}")
    public CompletableFuture<Void> listen(RawEventMessage message) {
        RawEventCommand command = toCommand(message);
        return CompletableFuture.runAsync(
                () -> matchEventUseCase.handle(command),
                sqsVirtualThreadExecutor
        );
    }

    private RawEventCommand toCommand(RawEventMessage message) {
        return new RawEventCommand(
                message.eventId(),
                message.clientId(),
                message.eventType(),
                message.content(),
                message.deliveryDate()
        );
    }
}
