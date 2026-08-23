package com.cobre.notifications.delivery.core.ports.out;

import com.cobre.notifications.delivery.core.domain.HttpResult;

/** Envía el POST HTTPS firmado al webhook del cliente. */
public interface WebhookClient {

    /**
     * @return el resultado crudo de la solicitud; un timeout o error de red
     *         MUST representarse con {@link HttpResult#statusCode()} nulo,
     *         nunca lanzando una excepción de infraestructura al dominio.
     */
    HttpResult post(String webhookUrl, String payload, String signature);
}
