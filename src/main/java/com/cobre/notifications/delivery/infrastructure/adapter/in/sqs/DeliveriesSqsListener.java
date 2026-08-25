package com.cobre.notifications.delivery.infrastructure.adapter.in.sqs;

import com.cobre.notifications.delivery.core.dto.DeliveryCommand;
import com.cobre.notifications.delivery.core.ports.in.DispatchDeliveryUseCase;
import com.cobre.notifications.delivery.infrastructure.adapter.in.sqs.message.DeliveryMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Entrada del dispatch (CU-2/CU-3).
 *
 * <p><b>Por qué devuelve {@code CompletableFuture}</b>: es lo que hace que
 * Spring Cloud AWS registre este listener como asíncrono
 * ({@code AbstractEndpoint#setupContainer} comprueba si el tipo de retorno es
 * {@code CompletionStage}) y no lo envuelva en el adaptador bloqueante, que
 * obliga a ejecutar sobre {@code MessageExecutionThread} — hilos de
 * plataforma. Así la entrega corre sobre un virtual thread
 * (ver {@code SqsContainerConfig}) y el hilo del contenedor queda libre en
 * cuanto encola la tarea.</p>
 *
 * <p><b>Acknowledge</b>: el mensaje se confirma cuando el futuro completa
 * <i>normalmente</i>. Si {@code handle} lanza, el futuro completa con
 * excepción, el contenedor no borra el mensaje y este vuelve a la cola al
 * expirar la visibilidad — la misma semántica que cuando el método era
 * bloqueante, solo que desplazada del {@code return} a la finalización del
 * futuro.</p>
 */
@Component
public class DeliveriesSqsListener {

    private final DispatchDeliveryUseCase dispatchDeliveryUseCase;
    private final ExecutorService sqsVirtualThreadExecutor;

    public DeliveriesSqsListener(
            DispatchDeliveryUseCase dispatchDeliveryUseCase,
            ExecutorService sqsVirtualThreadExecutor
    ) {
        this.dispatchDeliveryUseCase = dispatchDeliveryUseCase;
        this.sqsVirtualThreadExecutor = sqsVirtualThreadExecutor;
    }

    @SqsListener("${notifier.deliveries-queue}")
    public CompletableFuture<Void> listen(DeliveryMessage message) {
        DeliveryCommand command = toCommand(message);
        return CompletableFuture.runAsync(
                () -> dispatchDeliveryUseCase.handle(command),
                sqsVirtualThreadExecutor
        );
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
