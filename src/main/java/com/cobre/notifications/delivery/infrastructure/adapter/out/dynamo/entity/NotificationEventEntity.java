package com.cobre.notifications.delivery.infrastructure.adapter.out.dynamo.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

/**
 * Fila de la tabla {@code notification_events}: PK=client_id,
 * SK=delivery_date#notification_event_id, TTL 90d (data-model.md).
 *
 * <p>Se agrega un GSI {@value #ID_INDEX} con PK=notification_event_id: el
 * puerto {@code DeliveryOutcomeRepository.findById} solo recibe el id
 * determinista (sin client_id ni delivery_date), y la clave primaria de la
 * tabla base no alcanza para resolverlo directamente. Como el id ya es
 * único a nivel global (hash determinista), el GSI no necesita sort key.
 * Requiere aprovisionar el índice con {@code ProjectionType.ALL} en la
 * infraestructura (fuera del alcance de este código Java).</p>
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

    @DynamoDbSecondaryPartitionKey(indexNames = ID_INDEX)
    public String getNotificationEventId() {
        return notificationEventId;
    }

    public void setNotificationEventId(String notificationEventId) {
        this.notificationEventId = notificationEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public String getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(String deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }
}
