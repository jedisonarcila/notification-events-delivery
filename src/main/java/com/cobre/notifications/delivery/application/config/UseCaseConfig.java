package com.cobre.notifications.delivery.application.config;

import com.cobre.notifications.delivery.core.ports.in.DispatchDeliveryUseCase;
import com.cobre.notifications.delivery.core.ports.in.MatchEventUseCase;
import com.cobre.notifications.delivery.core.ports.out.*;
import com.cobre.notifications.delivery.core.usecase.DispatchDeliveryService;
import com.cobre.notifications.delivery.core.usecase.MatchEventService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring de los casos de uso del núcleo como beans de Spring.
 *
 * <p>{@code core/usecase} es Java puro (constitución, Principio I: el
 * dominio NO importa Spring), así que {@code MatchEventService} y
 * {@code DispatchDeliveryService} no llevan {@code @Component}/{@code @Service}.
 * Esta clase — sí de infraestructura — es la única que conoce ambos lados:
 * construye los servicios de dominio inyectándoles los adaptadores
 * (puertos de salida) que Spring ya wireó, y expone el resultado como los
 * puertos de entrada que consumen los listeners.</p>
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public MatchEventUseCase matchEventUseCase(
            SubscriptionRepository subscriptionRepository,
            DeliveryTaskPublisher deliveryTaskPublisher,
            DeliveryMetrics deliveryMetrics
    ) {
        return new MatchEventService(subscriptionRepository, deliveryTaskPublisher, deliveryMetrics);
    }

    @Bean
    public DispatchDeliveryUseCase dispatchDeliveryUseCase(
            WebhookClient webhookClient,
            SignatureResolver signatureResolver,
            DeliveryOutcomeRepository deliveryOutcomeRepository,
            DeliveryRetryScheduler deliveryRetryScheduler,
            DeliveryMetrics deliveryMetrics
    ) {
        return new DispatchDeliveryService(
                webhookClient, signatureResolver, deliveryOutcomeRepository, deliveryRetryScheduler, deliveryMetrics
        );
    }
}
