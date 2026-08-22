package com.cobre.notifications.delivery.infrastructure.adapter.out.dynamo.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Fila de la tabla {@code subscriptions}: PK=client_id,
 * SK=event_type#subscription_id (data-model.md).
 */
@DynamoDbBean
public class SubscriptionEntity {

    private String clientId;
    private String sortKey;
    private String subscriptionId;
    private String eventType;
    private String webhookUrl;
    private boolean active;

    public static String sortKey(String eventType, String subscriptionId) {
        return eventType + "#" + subscriptionId;
    }

    public static String sortKeyPrefix(String eventType) {
        return eventType + "#";
    }

    @DynamoDbPartitionKey
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @DynamoDbSortKey
    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
