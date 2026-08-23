package com.cobre.notifications.delivery.core.ports.out;

/**
 * Firma un payload con el secreto HMAC propio del cliente (uno por
 * {@code clientId}, no por suscripción — RN-6).
 */
public interface SignatureResolver {

    String sign(String clientId, String payload);
}
