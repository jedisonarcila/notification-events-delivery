package com.cobre.notifications.delivery.infrastructure.adapter.out.dynamo;

import com.cobre.notifications.delivery.core.domain.DeliveryOutcome;
import com.cobre.notifications.delivery.core.domain.DeliveryStatus;
import com.cobre.notifications.delivery.core.ports.out.DeliveryOutcomeRepository;
import com.cobre.notifications.delivery.infrastructure.adapter.out.dynamo.entity.NotificationEventEntity;
import io.awspring.cloud.dynamodb.DynamoDbTemplate;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.time.Instant;
import java.util.Optional;

/**
 * Una sola escritura por notificación (constitución, Principio IV):
 * {@link #save} solo se invoca con un {@link DeliveryOutcome} terminal.
 *
 * <p>{@link #findById} resuelve por el GSI {@link NotificationEventEntity#ID_INDEX}
 * porque el puerto recibe solo el {@code notificationEventId}, sin
 * {@code clientId} ni {@code deliveryDate} para completar la clave primaria
 * de la tabla base.</p>
 */
@Repository
public class DynamoOutcomeRepository implements DeliveryOutcomeRepository {

    private final DynamoDbTemplate dynamoDbTemplate;

    public DynamoOutcomeRepository(DynamoDbTemplate dynamoDbTemplate) {
        this.dynamoDbTemplate = dynamoDbTemplate;
    }

    @Override
    public void save(DeliveryOutcome outcome) {
        dynamoDbTemplate.save(toEntity(outcome));
    }

    @Override
    public Optional<DeliveryOutcome> findById(String notificationEventId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(notificationEventId).build()
        );
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .build();

        return dynamoDbTemplate.query(request, NotificationEventEntity.class, NotificationEventEntity.ID_INDEX)
                .items()
                .stream()
                .findFirst()
                .map(this::toDomain);
    }

    private NotificationEventEntity toEntity(DeliveryOutcome outcome) {
        NotificationEventEntity entity = new NotificationEventEntity();
        entity.setClientId(outcome.clientId());
        entity.setSortKey(NotificationEventEntity.sortKey(outcome.deliveryDate(), outcome.notificationEventId()));
        entity.setNotificationEventId(outcome.notificationEventId());
        entity.setEventType(outcome.eventType());
        entity.setDeliveryStatus(outcome.deliveryStatus().name());
        entity.setDeliveryDate(outcome.deliveryDate().toString());
        entity.setAttemptCount(outcome.attemptCount());
        entity.setLastError(outcome.lastError());
        entity.setWebhookUrl(outcome.webhookUrl());
        entity.setTtl(outcome.ttl());
        return entity;
    }

    private DeliveryOutcome toDomain(NotificationEventEntity entity) {
        return new DeliveryOutcome(
                entity.getNotificationEventId(),
                entity.getClientId(),
                entity.getEventType(),
                DeliveryStatus.valueOf(entity.getDeliveryStatus()),
                Instant.parse(entity.getDeliveryDate()),
                entity.getAttemptCount(),
                entity.getLastError(),
                entity.getWebhookUrl(),
                entity.getTtl()
        );
    }
}
