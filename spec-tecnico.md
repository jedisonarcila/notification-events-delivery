# Spec Técnico — notification-events-delivery

## 1. Stack
- Java 21 (virtual threads habilitados), Spring Boot 3.x
- Arquitectura hexagonal (puertos y adaptadores)
- Maven
- Spring Cloud AWS (SQS, Secrets Manager, DynamoDB `DynamoDbTemplate`, métricas)
- `RestClient` (HTTP saliente), Resilience4j (backoff/jitter + circuit breaker)
- Micrometer → CloudWatch Metrics (tags `client_id`, `event_type`)
- JUnit 5 (núcleo), Testcontainers + LocalStack (adaptadores)

## 2. Arquitectura hexagonal

### Núcleo (dominio puro, SIN Spring)
- **Modelos**: `RawEvent`, `Subscription`, `DeliveryTask`, `DeliveryOutcome`,
  `RetryPolicy`.
- **Puertos de entrada** (lo que el mundo pide al dominio):
  - `MatchEventUseCase.handle(RawEvent)` → resuelve y produce `DeliveryTask[]`.
  - `DispatchDeliveryUseCase.handle(DeliveryTask)` → entrega y produce outcome.
- **Puertos de salida** (lo que el dominio necesita del mundo, como interfaces):
  - `SubscriptionRepository.findActive(clientId, eventType)`
  - `DeliveryTaskPublisher.publish(DeliveryTask)`
  - `WebhookClient.post(url, payload, signature)` → `HttpResult`
  - `SignatureResolver.sign(clientId, payload)`
  - `DeliveryOutcomeRepository.save(DeliveryOutcome)`
  - `DeliveryRetryScheduler.reschedule(DeliveryTask, delaySeconds)`
  - `DeliveryMetrics.noSubscribers(...)` / `.result(...)`
- **Servicios de dominio**: `Matcher`, `Dispatcher`, `RetryDecision`,
  `NotificationIdFactory` (hash determinista), `BackoffCalculator`.

### Adaptadores (con Spring)
- **Entrada**: `RawEventsSqsListener` (`@SqsListener` en `raw-events`),
  `DeliveriesSqsListener` (`@SqsListener` en `deliveries`).
- **Salida**:
  - `DynamoSubscriptionRepository` (`DynamoDbTemplate`, Query begins_with).
  - `DynamoOutcomeRepository` (`DynamoDbTemplate`, PutItem/UpdateItem).
  - `SqsDeliveryPublisher` / `SqsRetryScheduler` (`SqsTemplate`,
    `ChangeMessageVisibility`).
  - `RestClientWebhookClient` (`RestClient`, timeouts, Resilience4j).
  - `SecretsManagerSignatureResolver` (cache en memoria con TTL).
  - `MicrometerDeliveryMetrics` (`Counter` con tags).

## 3. Estructura de paquetes
```
notification-events-delivery/
├── pom.xml
├── src/main/java/com/jarcila/notifier
│   ├── NotifierApplication.java                 (@SpringBootApplication)
│   │
│   ├── application
│   │   └── config
│   │       ├── AwsConfig.java                   (clientes SQS/DynamoDB/Secrets)
│   │       ├── RestClientConfig.java            (RestClient + timeouts)
│   │       ├── Resilience4jConfig.java          (retry/backoff + circuit breaker)
│   │       ├── CacheConfig.java                 (cache de secretos HMAC)
│   │       └── DeliveryProperties.java          (@ConfigurationProperties)
│   │
│   ├── core                                     (NÚCLEO — sin Spring, sin AWS)
│   │   ├── domain
│   │   │   ├── RawEvent.java
│   │   │   ├── Subscription.java
│   │   │   ├── DeliveryTask.java
│   │   │   ├── DeliveryOutcome.java
│   │   │   ├── DeliveryStatus.java              (enum: COMPLETED, FAILED)
│   │   │   └── HttpResult.java                  (código + cuerpo de la respuesta)
│   │   ├── dto
│   │   │   ├── RawEventCommand.java             (entrada al MatchEventUseCase)
│   │   │   └── DeliveryCommand.java             (entrada al DispatchDeliveryUseCase)
│   │   ├── mapper
│   │   │   └── DomainMapper.java                (domain <-> dto del núcleo)
│   │   ├── ports
│   │   │   ├── in
│   │   │   │   ├── MatchEventUseCase.java
│   │   │   │   └── DispatchDeliveryUseCase.java
│   │   │   └── out
│   │   │       ├── SubscriptionRepository.java
│   │   │       ├── DeliveryTaskPublisher.java
│   │   │       ├── WebhookClient.java
│   │   │       ├── SignatureResolver.java
│   │   │       ├── DeliveryOutcomeRepository.java
│   │   │       ├── DeliveryRetryScheduler.java
│   │   │       └── DeliveryMetrics.java
│   │   ├── usecase
│   │   │   ├── MatchEventService.java           (implementa MatchEventUseCase)
│   │   │   └── DispatchDeliveryService.java     (implementa DispatchDeliveryUseCase)
│   │   └── utils
│   │       ├── NotificationIdFactory.java       (hash(event_id + subscription_id))
│   │       ├── BackoffCalculator.java           (exponencial + equal jitter)
│   │       └── RetryDecision.java               (transitorio vs permanente)
│   │
│   └── infrastructure
│       └── adapter
│           ├── in
│           │   └── sqs
│           │       ├── RawEventsSqsListener.java      (@SqsListener raw-events)
│           │       ├── DeliveriesSqsListener.java     (@SqsListener deliveries)
│           │       └── message                         (forma de los mensajes SQS)
│           │           ├── RawEventMessage.java
│           │           └── DeliveryMessage.java
│           └── out
│               ├── dynamo
│               │   ├── DynamoSubscriptionRepository.java
│               │   ├── DynamoOutcomeRepository.java
│               │   └── entity
│               │       ├── SubscriptionEntity.java
│               │       └── NotificationEventEntity.java
│               ├── sqs
│               │   ├── SqsDeliveryPublisher.java       (fan-out a deliveries)
│               │   └── SqsRetryScheduler.java          (ChangeMessageVisibility)
│               ├── http
│               │   └── RestClientWebhookClient.java    (POST firmado + timeouts)
│               ├── secrets
│               │   └── SecretsManagerSignatureResolver.java  (HMAC + cache)
│               └── metrics
│                   └── MicrometerDeliveryMetrics.java  (Counter + tags)
│
├── src/main/resources
│   └── application.yml                          (colas, tablas, timeouts, virtual threads)
│
└── src/test/java/com/jarcila/notifier
    ├── core                                     (JUnit PURO — sin Spring)
    │   ├── usecase
    │   │   ├── MatchEventServiceTest.java        (fan-out, A01, sin-suscriptores)
    │   │   └── DispatchDeliveryServiceTest.java  (transitorio/permanente/completed)
    │   └── utils
    │       ├── NotificationIdFactoryTest.java    (determinismo)
    │       ├── BackoffCalculatorTest.java        (fórmula + tope)
    │       └── RetryDecisionTest.java
    └── infrastructure                            (Testcontainers + LocalStack)
        └── adapter
            ├── DynamoSubscriptionRepositoryIT.java
            ├── SqsDeliveryPublisherIT.java
            └── RestClientWebhookClientIT.java
```

## 4. Contratos de mensajes
- **raw-events** (entrada): `{ event_id, client_id, event_type, content, delivery_date }`
- **deliveries** (tarea): `{ notification_event_id, client_id, event_type,
  subscription_id, webhook_url, content, delivery_date, attempt_count }`

## 5. Modelo de datos (DynamoDB)
- `notification_events`: PK=`client_id`, SK=`delivery_date#notification_event_id`,
  GSI status (PK=`client_id#delivery_status`, SK=`delivery_date`), TTL 90d.
- `subscriptions`: PK=`client_id`, SK=`event_type#subscription_id`.

## 6. Reintentos (detalle)
- `attempt_count` viaja en el mensaje de `deliveries`.
- Backoff: `base = min(30 * 2^intento, 3600)`; `espera = base/2 + random(0, base/2)`
  (equal jitter). El resultado (segundos) → `ChangeMessageVisibility`.
- Máx 6 intentos. `maxReceiveCount` de la DLQ = red de seguridad de INFRA
  (número mayor, no es el mecanismo de los 6).
- Circuit breaker (Resilience4j) opcional por host de webhook.

## 7. Seguridad
- Firma HMAC-SHA256 del payload → header `X-Cobre-Signature`.
- Secreto por cliente: `cobre/webhook-hmac/{client_id}` (derivable).
- Anti-SSRF: validar `webhook_url` antes del POST (bloquear rangos privados y
  metadata 169.254.169.254; pinning anti DNS-rebinding). Egreso por NAT/subred
  privada; IMDSv2 + hop-limit.

## 8. Config (`@ConfigurationProperties`)
- Nombres/URLs de colas y tablas, timeouts HTTP (conexión/lectura),
  `maxRetries=6`, TTL del cache de secretos, namespace de métricas.

## 9. Testing
- **Núcleo (JUnit puro)**: Matcher (fan-out, A01, sin-suscriptores),
  RetryDecision (transitorio vs permanente), BackoffCalculator (fórmula/tope),
  NotificationIdFactory (determinismo).
- **Adaptadores (Testcontainers + LocalStack)**: SQS consume/produce, DynamoDB
  query begins_with y escritura de desenlace, Secrets resolve+cache.
- **Contract**: firma HMAC reproducible; mapeo de respuesta a
  transitorio/permanente por código HTTP.

## 10. No-funcionales
- Throughput objetivo ~2.000 ev/s (picos 5.000/s), absorbido por SQS.
- Stateless: cualquier pod puede procesar cualquier mensaje.
- Idempotente ante at-least-once de SQS.
