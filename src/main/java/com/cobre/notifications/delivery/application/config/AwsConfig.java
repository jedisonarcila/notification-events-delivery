package com.cobre.notifications.delivery.application.config;

import com.cobre.notifications.delivery.infrastructure.adapter.out.dynamo.entity.NotificationEventEntity;
import com.cobre.notifications.delivery.infrastructure.adapter.out.dynamo.entity.SubscriptionEntity;
import io.awspring.cloud.autoconfigure.core.AwsClientBuilderConfigurer;
import io.awspring.cloud.dynamodb.DynamoDbTableNameResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.util.Map;

/**
 * Beans de AWS que Spring Cloud AWS no autoconfigura por sí solo:
 * <ul>
 *   <li>Los nombres de tabla DynamoDB reales ({@code subscriptions},
 *   {@code notification_events}) no coinciden con el nombre de las clases
 *   de entidad, así que se sobreescribe el resolver por defecto (que usa
 *   {@code Class.getSimpleName()}).</li>
 *   <li>{@code SecretsManagerClient}: Spring Cloud AWS solo autoconfigura
 *   Secrets Manager como fuente de configuración estática
 *   ({@code spring.config.import}), no como cliente de uso general para
 *   resolver secretos dinámicamente por cliente en tiempo de ejecución.</li>
 * </ul>
 */
@Configuration
public class AwsConfig {

    @Bean
    public DynamoDbTableNameResolver dynamoDbTableNameResolver(DeliveryProperties properties) {
        Map<Class<?>, String> tableNamesByEntity = Map.of(
                SubscriptionEntity.class, properties.getSubscriptionsTable(),
                NotificationEventEntity.class, properties.getNotificationEventsTable()
        );
        return new FixedDynamoDbTableNameResolver(tableNamesByEntity);
    }

    @Bean
    public SecretsManagerClient secretsManagerClient(AwsClientBuilderConfigurer configurer) {
        return configurer.configure(SecretsManagerClient.builder()).build();
    }

    private record FixedDynamoDbTableNameResolver(
            Map<Class<?>, String> tableNamesByEntity
    ) implements DynamoDbTableNameResolver {

        @Override
        public <T> String resolve(Class<T> clazz) {
            String tableName = tableNamesByEntity.get(clazz);
            if (tableName == null) {
                throw new IllegalArgumentException("Sin tabla configurada para " + clazz.getSimpleName());
            }
            return tableName;
        }
    }
}
