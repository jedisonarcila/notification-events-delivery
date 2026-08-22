package com.cobre.notifications.delivery.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuración externa del worker (colas, tablas, timeouts, secretos,
 * métricas). Ver spec-tecnico.md §8.
 */
@Component
@ConfigurationProperties(prefix = "notifier")
public class DeliveryProperties {

    private String rawEventsQueue = "raw-events";
    private String deliveriesQueue = "deliveries";
    private String subscriptionsTable = "subscriptions";
    private String notificationEventsTable = "notification_events";
    private Duration httpConnectTimeout = Duration.ofSeconds(5);
    private Duration httpReadTimeout = Duration.ofSeconds(10);
    private String secretPrefix = "cobre/webhook-hmac/";
    private Duration secretCacheTtl = Duration.ofMinutes(10);
    private String metricsNamespace = "notification-events-delivery";

    public String getRawEventsQueue() {
        return rawEventsQueue;
    }

    public void setRawEventsQueue(String rawEventsQueue) {
        this.rawEventsQueue = rawEventsQueue;
    }

    public String getDeliveriesQueue() {
        return deliveriesQueue;
    }

    public void setDeliveriesQueue(String deliveriesQueue) {
        this.deliveriesQueue = deliveriesQueue;
    }

    public String getSubscriptionsTable() {
        return subscriptionsTable;
    }

    public void setSubscriptionsTable(String subscriptionsTable) {
        this.subscriptionsTable = subscriptionsTable;
    }

    public String getNotificationEventsTable() {
        return notificationEventsTable;
    }

    public void setNotificationEventsTable(String notificationEventsTable) {
        this.notificationEventsTable = notificationEventsTable;
    }

    public Duration getHttpConnectTimeout() {
        return httpConnectTimeout;
    }

    public void setHttpConnectTimeout(Duration httpConnectTimeout) {
        this.httpConnectTimeout = httpConnectTimeout;
    }

    public Duration getHttpReadTimeout() {
        return httpReadTimeout;
    }

    public void setHttpReadTimeout(Duration httpReadTimeout) {
        this.httpReadTimeout = httpReadTimeout;
    }

    public String getSecretPrefix() {
        return secretPrefix;
    }

    public void setSecretPrefix(String secretPrefix) {
        this.secretPrefix = secretPrefix;
    }

    public Duration getSecretCacheTtl() {
        return secretCacheTtl;
    }

    public void setSecretCacheTtl(Duration secretCacheTtl) {
        this.secretCacheTtl = secretCacheTtl;
    }

    public String getMetricsNamespace() {
        return metricsNamespace;
    }

    public void setMetricsNamespace(String metricsNamespace) {
        this.metricsNamespace = metricsNamespace;
    }
}
