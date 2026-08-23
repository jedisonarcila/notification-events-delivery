package com.cobre.notifications.delivery.infrastructure.adapter.out.metrics;

import com.cobre.notifications.delivery.core.domain.DeliveryStatus;
import com.cobre.notifications.delivery.core.ports.out.DeliveryMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Métricas hacia CloudWatch vía Micrometer. Únicas dimensiones permitidas:
 * {@code client_id} y {@code event_type} (constitución, stack tecnológico).
 */
@Component
public class MicrometerDeliveryMetrics implements DeliveryMetrics {

    static final String NO_SUBSCRIBERS_METRIC = "eventos_sin_suscriptores";
    static final String DELIVERY_RESULT_METRIC = "entrega_resultado";

    private final MeterRegistry meterRegistry;

    public MicrometerDeliveryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void noSubscribers(String clientId, String eventType) {
        meterRegistry.counter(NO_SUBSCRIBERS_METRIC, "client_id", clientId, "event_type", eventType)
                .increment();
    }

    @Override
    public void result(String clientId, String eventType, DeliveryStatus outcome) {
        meterRegistry.counter(
                DELIVERY_RESULT_METRIC,
                "client_id", clientId,
                "event_type", eventType,
                "outcome", outcome.name().toLowerCase()
        ).increment();
    }
}
