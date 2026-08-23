package com.cobre.notifications.delivery.core.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Deriva un identificador de notificación determinista a partir del evento y
 * la suscripción (constitución, Principio III: idempotencia).
 */
public final class NotificationIdFactory {

    private NotificationIdFactory() {
    }

    public static String create(String eventId, String subscriptionId) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(subscriptionId, "subscriptionId");

        String raw = eventId + "|" + subscriptionId;
        byte[] hash = sha256(raw.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
