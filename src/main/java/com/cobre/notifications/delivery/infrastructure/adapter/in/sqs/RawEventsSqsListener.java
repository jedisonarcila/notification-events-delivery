package com.cobre.notifications.delivery.infrastructure.adapter.in.sqs;

import com.cobre.notifications.delivery.core.dto.RawEventCommand;
import com.cobre.notifications.delivery.core.ports.in.MatchEventUseCase;
import com.cobre.notifications.delivery.infrastructure.adapter.in.sqs.message.RawEventMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Component;

/**
 * Entrada del fan-out (CU-1). Spring Cloud AWS confirma (borra) el mensaje
 * únicamente si {@link #listen} retorna sin excepción — como
 * {@code MatchEventService} encola cada tarea de forma síncrona y propaga
 * cualquier fallo de publicación, esto ya satisface FR-004 (confirmar solo
 * tras encolar todas las tareas) sin lógica adicional aquí.
 */
@Component
public class RawEventsSqsListener {

    private final MatchEventUseCase matchEventUseCase;

    public RawEventsSqsListener(MatchEventUseCase matchEventUseCase) {
        this.matchEventUseCase = matchEventUseCase;
    }

    @SqsListener("${notifier.raw-events-queue}")
    public void listen(RawEventMessage message) {
        matchEventUseCase.handle(toCommand(message));
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
