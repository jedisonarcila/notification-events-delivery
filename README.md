# notification-events-delivery

Worker headless (sin HTTP) que entrega notificaciones a webhooks de clientes:
consume eventos de SQS, resuelve suscripciones activas (fan-out), entrega vía
HTTPS firmado (HMAC), reintenta fallos transitorios usando la visibilidad de
SQS (nunca `Thread.sleep`) y persiste un único desenlace terminal por
notificación en DynamoDB. Parte del reto **Cobre Notifications**.

## Arquitectura

Hexagonal estricta: `core/domain` y `core/usecase` son Java puro, sin Spring
ni AWS SDK. Toda dependencia externa entra por un puerto (`core/ports`)
implementado en `infrastructure/adapter`.

```
raw-events (SQS) ──▶ RawEventsSqsListener ──▶ MatchEventService
                                                 │ (fan-out por suscripción activa,
                                                 │  aislamiento A01: PK=client_id)
                                                 ▼
                                          deliveries (SQS) ──▶ DeliveriesSqsListener
                                                                 │
                                                                 ▼
                                                        DispatchDeliveryService
                                                          │  idempotente (notification_event_id
                                                          │  = hash(event_id + subscription_id))
                                                          │  firma HMAC + POST al webhook
                                                          │
                                              ┌───────────┼───────────┐
                                          COMPLETED    FAILED     TRANSIENT
                                        (terminal)  (terminal)  (reencola en
                                                                 deliveries con
                                                                 attempt_count+1
                                                                 y backoff)
```

Detalle funcional y técnico completo en [`spec-tecnico.md`](spec-tecnico.md)
y [`specs/001-notification-delivery-worker/`](specs/001-notification-delivery-worker)
(`spec.md`, `plan.md`, `data-model.md`, `quickstart.md`).

## Stack

- Java 21 + virtual threads
- Spring Boot 3.3.x, Maven
- Spring Cloud AWS: SQS, Secrets Manager, DynamoDB (`DynamoDbTemplate`)
- `RestClient` para el POST saliente al webhook
- Resilience4j (backoff/jitter, circuit breaker)
- Micrometer → CloudWatch (namespace configurable, tags `client_id` +
  `event_type`)
- JUnit 5 para el núcleo, Testcontainers + LocalStack para los adaptadores

## Prerrequisitos

- JDK 21
- Maven (o usa `./mvnw` si el wrapper está disponible)
- Docker, para correr LocalStack (local) y para los tests de adaptadores
  (Testcontainers)

## Configuración

Toda la configuración externa vive en `notifier.*`
(`src/main/resources/application.yml`), mapeada a `DeliveryProperties`:

| Propiedad | Variable de entorno | Default | Descripción |
|---|---|---|---|
| `notifier.raw-events-queue` | `QUEUE_RAW_URL` | `raw-events` | Cola de entrada de eventos |
| `notifier.deliveries-queue` | `QUEUE_DELIVERIES_URL` | `deliveries` | Cola de tareas de entrega/reintento |
| `notifier.subscriptions-table` | `TABLE_SUBS` | `subscriptions` | Tabla DynamoDB de suscripciones |
| `notifier.notification-events-table` | `TABLE_EVENTS` | `notification_events` | Tabla DynamoDB de desenlaces |
| `notifier.http-connect-timeout` | — | `5s` | Timeout de conexión al webhook |
| `notifier.http-read-timeout` | — | `10s` | Timeout de lectura del webhook |
| `notifier.secret-prefix` | — | `cobre/webhook-hmac/` | Prefijo en Secrets Manager (`{prefix}{client_id}`) |
| `notifier.secret-cache-ttl` | — | `10m` | TTL de caché del secreto HMAC |
| `notifier.metrics-namespace` | — | `notification-events-delivery` | Namespace CloudWatch |

Credenciales AWS: **no se fijan** en `application.yml` a propósito; se
resuelven vía `DefaultCredentialsProvider` (env vars, IRSA/EKS, rol de tarea
en ECS, instance profile en EC2, `~/.aws/credentials`), según el entorno
real de despliegue.

## Ejecutar localmente (perfil `local` + LocalStack)

1. Levanta LocalStack con SQS, DynamoDB y Secrets Manager simulados:

   ```bash
   docker run --rm -p 4566:4566 -e SERVICES=sqs,dynamodb,secretsmanager localstack/localstack
   ```

   Las colas `raw-events` y `deliveries` se auto-crean al arrancar el
   worker. Las tablas DynamoDB **hay que crearlas a mano** antes de probar:

   ```bash
   aws --endpoint-url=http://localhost:4566 dynamodb create-table \
     --table-name subscriptions \
     --attribute-definitions AttributeName=client_id,AttributeType=S AttributeName=sk,AttributeType=S \
     --key-schema AttributeName=client_id,KeyType=HASH AttributeName=sk,KeyType=RANGE \
     --billing-mode PAY_PER_REQUEST

   aws --endpoint-url=http://localhost:4566 dynamodb create-table \
     --table-name notification_events \
     --attribute-definitions \
       AttributeName=client_id,AttributeType=S \
       AttributeName=sk,AttributeType=S \
       AttributeName=notification_event_id,AttributeType=S \
     --key-schema AttributeName=client_id,KeyType=HASH AttributeName=sk,KeyType=RANGE \
     --global-secondary-indexes '[{
       "IndexName": "notification_event_id-index",
       "KeySchema": [{"AttributeName":"notification_event_id","KeyType":"HASH"}],
       "Projection": {"ProjectionType":"ALL"}
     }]' \
     --billing-mode PAY_PER_REQUEST
   ```

   El SK de `subscriptions` es `event_type#subscription_id`; el de
   `notification_events` es `delivery_date#notification_event_id`. Ver
   [`data-model.md`](specs/001-notification-delivery-worker/data-model.md)
   para el modelo completo (incluye el GSI por `delivery_status` usado por
   operación, opcional para correr el worker).

2. (Opcional) siembra un secreto HMAC de prueba para un cliente:

   ```bash
   aws --endpoint-url=http://localhost:4566 secretsmanager create-secret \
     --name cobre/webhook-hmac/C1 --secret-string '{"secret":"test-secret"}'
   ```

3. Arranca el worker con el perfil `local`:

   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

   El perfil `local` (`application-local.yml`) apunta SQS/DynamoDB/Secrets
   Manager a `http://localhost:4566` con credenciales dummy y desactiva la
   exportación de métricas a CloudWatch.

4. Publica un evento de prueba en `raw-events` y sigue el escenario
   end-to-end descrito en
   [`quickstart.md`](specs/001-notification-delivery-worker/quickstart.md).

## Tests

Núcleo de dominio (Java puro, JUnit 5, sin Docker):

```bash
mvn test
```

Adaptadores (levanta LocalStack vía Testcontainers, requiere Docker):

```bash
mvn verify
```

## Build

```bash
mvn clean package
java -jar target/notification-events-delivery-0.1.0-SNAPSHOT.jar
```

## Estructura del proyecto

```
src/main/java/com/cobre/notifications/delivery/
├── core/
│   ├── domain/       # Entidades puras (RawEvent, DeliveryTask, DeliveryOutcome...)
│   ├── dto/          # Comandos de entrada a los casos de uso
│   ├── mapper/        # DTO/mensaje → dominio
│   ├── ports/
│   │   ├── in/       # UseCases (MatchEventUseCase, DispatchDeliveryUseCase)
│   │   └── out/      # Puertos de salida (SubscriptionRepository, WebhookClient...)
│   ├── usecase/      # Implementación de los UseCases
│   └── utils/        # BackoffCalculator, NotificationIdFactory, RetryDecision
├── infrastructure/
│   └── adapter/
│       ├── in/sqs/   # Listeners SQS
│       └── out/      # dynamo, http, secrets, sqs, metrics
└── application/config/  # Beans y configuración de Spring
```

Convenciones y principios de diseño (idempotencia, aislamiento A01, una sola
escritura de desenlace, etc.) están documentados en
[`CLAUDE.md`](CLAUDE.md) y [`.specify/memory/constitution.md`](.specify/memory/constitution.md).
