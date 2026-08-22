package com.cobre.notifications.delivery.core.domain;

/**
 * Resultado crudo de un intento de entrega HTTP.
 *
 * @param statusCode código de respuesta, o {@code null} si hubo timeout o
 *                   error de red (sin respuesta del webhook)
 * @param body       cuerpo de la respuesta, si la hubo
 */
public record HttpResult(Integer statusCode, String body) {
}
