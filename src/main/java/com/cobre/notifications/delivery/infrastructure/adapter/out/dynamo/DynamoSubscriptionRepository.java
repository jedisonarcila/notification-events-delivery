package com.cobre.notifications.delivery.infrastructure.adapter.out.dynamo;

import com.cobre.notifications.delivery.core.domain.Subscription;
import com.cobre.notifications.delivery.core.ports.out.SubscriptionRepository;
import com.cobre.notifications.delivery.infrastructure.adapter.out.dynamo.entity.SubscriptionEntity;
import io.awspring.cloud.dynamodb.DynamoDbTemplate;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;

/**
 * {@code Query} PK=client_id, SK begins_with(event_type#) — el aislamiento
 * A01 es estructural: DynamoDB no permite un Query sin partition key.
 */
@Repository
public class DynamoSubscriptionRepository implements SubscriptionRepository {

    private final DynamoDbTemplate dynamoDbTemplate;

    public DynamoSubscriptionRepository(DynamoDbTemplate dynamoDbTemplate) {
        this.dynamoDbTemplate = dynamoDbTemplate;
    }

    @Override
    public List<Subscription> findActive(String clientId, String eventType) {
        QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue(clientId)
                        .sortValue(SubscriptionEntity.sortKeyPrefix(eventType))
                        .build()
        );
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .build();

        return dynamoDbTemplate.query(request, SubscriptionEntity.class)
                .items()
                .stream()
                .filter(SubscriptionEntity::isActive)
                .map(this::toDomain)
                .toList();
    }

    private Subscription toDomain(SubscriptionEntity entity) {
        return new Subscription(
                entity.getSubscriptionId(),
                entity.getClientId(),
                entity.getEventType(),
                entity.getWebhookUrl(),
                entity.isActive()
        );
    }
}
