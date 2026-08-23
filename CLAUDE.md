# CLAUDE.md — notification-events-delivery

## Qué es este proyecto
Worker headless (sin HTTP) que entrega notificaciones a webhooks de clientes:
consume SQS, hace matching + fan-out, entrega HTTPS firmada, reintenta
transitorios y persiste el desenlace. Parte del reto Cobre Notifications.

## Principios (constitución)
1. **Arquitectura hexagonal estricta**: el `domain` NO importa Spring, AWS SDK,
   ni ninguna librería de infraestructura. Solo Java puro. Toda dependencia
   externa entra por un puerto (interface) implementado en `adapter`.
2. **TDD**: primero el test (JUnit) del núcleo, luego la implementación.
3. **Idempotencia**: SQS es at-least-once; todo debe tolerar reprocesamiento.
   `notification_event_id = hash(event_id + subscription_id)`.
4. **Una sola escritura de desenlace** por notificación (terminal:
   completed/failed). Nada de estados intermedios.
5. **La espera de reintento la hace SQS** (ChangeMessageVisibility), nunca
   Thread.sleep. `attempt_count` viaja en el mensaje.
6. **Aislamiento A01**: toda query a datos usa `client_id` como PK.

## Stack (decisiones cerradas — no cambiar sin justificar)
- Java 21 + virtual threads (`spring.threads.virtual.enabled=true`)
- Spring Boot 3.x, Maven
- Spring Cloud AWS: SQS, Secrets Manager, DynamoDB (`DynamoDbTemplate`), métricas
- HTTP saliente: `RestClient` (NO WebClient, NO OpenFeign — URL dinámica)
- Resiliencia: Resilience4j (backoff/jitter calc + circuit breaker)
- Métricas: Micrometer → CloudWatch, tags `client_id` + `event_type`
- Tests: JUnit 5 (núcleo), Testcontainers + LocalStack (adaptadores)

## Convenciones
- Paquete raíz: `com.cobre.notifications.delivery`
- Puertos de entrada: sufijo `UseCase`. Puertos de salida: nombre del rol
  (`SubscriptionRepository`, `WebhookClient`...).
- Un adaptador por puerto; el nombre del adaptador indica la tecnología
  (`DynamoSubscriptionRepository`, `RestClientWebhookClient`).
- Sin lógica de negocio en los adaptadores; solo traducción.

## Qué NO hacer
- No poner anotaciones de Spring en `domain`.
- No usar Thread.sleep para reintentos.
- No escribir estados intermedios en DynamoDB.
- No exponer endpoints HTTP (esto es un worker).
- No promover más dimensiones de métricas que `client_id` y `event_type`.
