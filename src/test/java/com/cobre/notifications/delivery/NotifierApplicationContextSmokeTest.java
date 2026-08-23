package com.cobre.notifications.delivery;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Verificación de arranque: confirma que todos los adaptadores y su
 * configuración (DynamoDbTemplate, SqsTemplate, RestClient, SecretsManagerClient,
 * MeterRegistry, los listeners de entrada y el wiring de los casos de uso)
 * resuelven sus dependencias y el contexto de Spring levanta, sin necesidad
 * de credenciales AWS reales (la resolución es diferida).
 *
 * <p>{@link TestSqsContainerConfig} desactiva el auto-arranque de los
 * contenedores {@code @SqsListener}: en producción SÍ deben arrancar solos y
 * resolver la cola contra SQS real, pero eso requeriría red/credenciales
 * reales que no existen en este entorno de pruebas — aquí solo interesa
 * verificar que los beans se conectan entre sí, no ejercitar el polling.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotifierApplicationContextSmokeTest {

    @Test
    void contextLoads() {
    }

    @TestConfiguration
    static class TestSqsContainerConfig {

        @Bean
        @Primary
        public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(
                SqsAsyncClient sqsAsyncClient
        ) {
            return SqsMessageListenerContainerFactory.<Object>builder()
                    .sqsAsyncClient(sqsAsyncClient)
                    .configure(options -> options.autoStartup(false))
                    .build();
        }
    }
}
