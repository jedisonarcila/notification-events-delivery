package com.cobre.notifications.delivery.infrastructure.adapter.out.http;

import com.cobre.notifications.delivery.core.domain.HttpResult;
import com.cobre.notifications.delivery.core.ports.out.WebhookClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * POST HTTPS firmado al webhook del cliente. Un timeout o error de red se
 * traduce a {@link HttpResult} con {@code statusCode} nulo — el dominio
 * (RetryDecision) ya sabe tratar eso como fallo transitorio.
 */
@Component
public class RestClientWebhookClient implements WebhookClient {

    private static final String SIGNATURE_HEADER = "X-Cobre-Signature";

    private final RestClient restClient;

    public RestClientWebhookClient(RestClient webhookRestClient) {
        this.restClient = webhookRestClient;
    }

    @Override
    public HttpResult post(String webhookUrl, String payload, String signature) {
        try {
            return restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(SIGNATURE_HEADER, signature)
                    .body(payload)
                    .exchange((request, response) -> new HttpResult(
                            response.getStatusCode().value(),
                            response.bodyTo(String.class)
                    ));
        } catch (ResourceAccessException timeoutOrConnectionFailure) {
            return new HttpResult(null, null);
        }
    }
}
