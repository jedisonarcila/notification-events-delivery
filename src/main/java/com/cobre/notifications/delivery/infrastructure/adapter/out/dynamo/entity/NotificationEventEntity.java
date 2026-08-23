package com.cobre.notifications.delivery.infrastructure.adapter.out.dynamo.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

/**
 * Fila de la tabla {@code notification_events}: PK=client_id,
 * SK=delivery_date#notification_event_id, TTL 90d (data-model.md).
 *
 * Los @DynamoDbAttribute alinean los nombres con la tabla real (snake_case):
 * client_id, sk, notification_event_id, event_type, delivery_status,
 * delivery_date, attempt_count, last_error, webhook_url, ttl.
 *
 * GSI {@value #ID_INDEX} con PK=notification_event_id para findById
 * (idempotencia por id determinista). Debe existir en la infraestructura
 * (dynamodb.tf) con ese mismo nombre y hash key.
 */
@DynamoDbBean
public class NotificationEventEntity {

    public static final String ID_INDEX = "notification_event_id-index";

    private String clientId;
    private String sortKey;
    private String notificationEventId;
    private String eventType;
    private String deliveryStatus;
    private String deliveryDate;
    private int attemptCount;
    private String lastError;
    private String webhookUrl;
    private long ttl;

    public static String sortKey(Instant deliveryDate, String notificationEventId) {
        return deliveryDate.toString() + "#" + notificationEventId;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("client_id")
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("sk")
    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = ID_INDEX)
    @DynamoDbAttribute("notification_event_id")
    public String getNotificationEventId() {
        return notificationEventId;
    }

    public void setNotificationEventId(String notificationEventId) {
        this.notificationEventId = notificationEventId;
    }

    @DynamoDbAttribute("event_type")
    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    @DynamoDbAttribute("delivery_status")
    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    @DynamoDbAttribute("delivery_date")
    public String getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(String deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    @DynamoDbAttribute("attempt_count")
    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    @DynamoDbAttribute("last_error")
    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    @DynamoDbAttribute("webhook_url")
    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @DynamoDbAttribute("ttl")
    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }
}
