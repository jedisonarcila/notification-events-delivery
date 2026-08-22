package com.cobre.notifications.delivery.core.domain;

/**
 * Preferencia de un cliente por recibir un tipo de evento en un webhook.
 * Gestionada fuera de este sistema; el worker solo la consulta.
 *
 * @param subscriptionId identificador de la suscripción
 * @param clientId       cliente dueño de la suscripción
 * @param eventType      tipo de evento al que está suscrita
 * @param webhookUrl     destino del POST
 * @param active         solo las suscripciones activas participan en el fan-out
 */
public record Subscription(
        String subscriptionId,
        String clientId,
        String eventType,
        String webhookUrl,
        boolean active
) {
}
