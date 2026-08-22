package com.cobre.notifications.delivery.infrastructure.adapter.out.sqs;

import com.cobre.notifications.delivery.application.config.DeliveryProperties;
import com.cobre.notifications.delivery.core.domain.DeliveryTask;
import com.cobre.notifications.delivery.core.ports.out.DeliveryRetryScheduler;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.stereotype.Component;

/**
 * Reencola una tarea de entrega para reintento.
 *
 * <p><b>Nota de diseño / limitación conocida</b>: spec-tecnico.md §2 describe
 * este adaptador usando {@code ChangeMessageVisibility} sobre el mensaje
 * en curso. Esa operación requiere el receipt handle del mensaje original,
 * que solo existe en el listener que lo recibió ({@code DeliveriesSqsListener},
 * un adaptador de entrada aún no implementado) — el dominio, que es quien
 * invoca este puerto, nunca tiene ese handle (correctamente, para no
 * filtrar detalles de SQS al núcleo). Mientras el listener no esté
 * construido, este adaptador aproxima el mismo efecto (espera gestionada
 * por SQS, no por el proceso) reencolando un mensaje nuevo con
 * {@code delaySeconds}, capado al máximo nativo de SQS para
 * {@code SendMessage} (900s). Los backoffs de {@code BackoffCalculator}
 * (máximo intento usado antes de agotar reintentos: 960s) pueden así
 * quedar ligeramente recortados en el último reintento. Cuando se
 * implemente el listener con soporte de {@code Visibility}, esto debería
 * migrarse a extender la visibilidad del mensaje en curso en vez de
 * republicar uno nuevo.</p>
 */
@Component
public class SqsRetryScheduler implements DeliveryRetryScheduler {

    /** Límite nativo de AWS SQS para {@code SendMessage.DelaySeconds}. */
    static final int SQS_MAX_DELAY_SECONDS = 900;

    private final SqsTemplate sqsTemplate;
    private final DeliveryProperties properties;

    public SqsRetryScheduler(SqsTemplate sqsTemplate, DeliveryProperties properties) {
        this.sqsTemplate = sqsTemplate;
        this.properties = properties;
    }

    @Override
    public void reschedule(DeliveryTask task, long delaySeconds) {
        int cappedDelaySeconds = (int) Math.min(delaySeconds, SQS_MAX_DELAY_SECONDS);
        sqsTemplate.send(options -> options
                .queue(properties.getDeliveriesQueue())
                .payload(SqsDeliveryPublisher.toMessage(task))
                .delaySeconds(cappedDelaySeconds));
    }
}
