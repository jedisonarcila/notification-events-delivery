package com.cobre.notifications.delivery.infrastructure.adapter.out.secrets;

import com.cobre.notifications.delivery.application.config.DeliveryProperties;
import com.cobre.notifications.delivery.core.ports.out.SignatureResolver;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resuelve el secreto HMAC de {@code cobre/webhook-hmac/{client_id}} (uno
 * por cliente, RN-6) y firma el payload con HMAC-SHA256. El secreto se
 * cachea en memoria con TTL (sin librería de cache externa: es una única
 * clave por cliente con expiración simple).
 */
@Component
public class SecretsManagerSignatureResolver implements SignatureResolver {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretsManagerClient secretsManagerClient;
    private final DeliveryProperties properties;
    private final Map<String, CachedSecret> secretCache = new ConcurrentHashMap<>();

    public SecretsManagerSignatureResolver(SecretsManagerClient secretsManagerClient, DeliveryProperties properties) {
        this.secretsManagerClient = secretsManagerClient;
        this.properties = properties;
    }

    @Override
    public String sign(String clientId, String payload) {
        String secret = resolveSecret(clientId);
        return hmacSha256Hex(secret, payload);
    }

    private String resolveSecret(String clientId) {
        Instant now = Instant.now();
        CachedSecret cached = secretCache.get(clientId);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }

        String secretId = properties.getSecretPrefix() + clientId;
        String secretValue = secretsManagerClient.getSecretValue(
                GetSecretValueRequest.builder().secretId(secretId).build()
        ).secretString();

        secretCache.put(clientId, new CachedSecret(secretValue, now.plus(properties.getSecretCacheTtl())));
        return secretValue;
    }

    private String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("No se pudo calcular la firma HMAC-SHA256", e);
        }
    }

    private record CachedSecret(String value, Instant expiresAt) {
    }
}
