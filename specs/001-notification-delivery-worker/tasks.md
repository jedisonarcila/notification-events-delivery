---

description: "Task list template for feature implementation"
---

# Tasks: Notification Delivery Worker

**Input**: Design documents from `/specs/001-notification-delivery-worker/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md, `.specify/memory/constitution.md`

**Tests**: Incluidos y OBLIGATORIOS — la constitución del proyecto (Principio II, TDD) exige test primero, implementación después, para todo el núcleo de dominio. Los tests marcados abajo deben escribirse y **fallar** antes de la tarea de implementación correspondiente.

**Package root**: `com.cobre.notifications.delivery` (constitution.md, Convenciones de Código — ver nota de corrección en plan.md).

**Progreso (2026-08-22)**: primera pasada de `/speckit-implement`, acotada explícitamente por el usuario al núcleo — modelos de dominio, puertos in/out y los 5 servicios/utilidades (Matcher, Dispatcher, RetryDecision, BackoffCalculator, NotificationIdFactory) con sus tests JUnit. Completadas: T001, T005-T010, T013-T016, T026-T031, T034-T037 (21/43). Pendientes explícitamente diferidas a una siguiente pasada de adaptadores: T002-T004, T011-T012, T017-T025, T032-T033, T038-T043. `mvn test` (offline tras resolver dependencias una vez): 41/41 tests en verde.

**Progreso (2026-08-22, 2ª pasada)**: adaptadores de salida, acotados explícitamente por el usuario a DynamoDbTemplate, SQS publisher/retry, RestClient webhook, Secrets, Micrometer (sin listeners de entrada). Completadas: T002-T004, T019-T024, T032 (+7 respecto a la pasada anterior). **T011 y T012 quedan parciales** (no se marcan `[X]`):
- T011: solo se creó `DeliveryMessage` (necesario para publisher/retry); `RawEventMessage` queda para cuando se implemente `RawEventsSqsListener`.
- T012: se crearon `DeliveryProperties`, `AwsConfig` (resolver de nombres de tabla DynamoDB + `SecretsManagerClient`) y `RestClientConfig`; `Resilience4jConfig` (circuit breaker) se difiere a Polish/T041 (no se pidió y no bloquea nada); `CacheConfig` se descartó a propósito — el cache TTL del secreto HMAC se resolvió con un `ConcurrentHashMap` simple dentro de `SecretsManagerSignatureResolver` en vez de una clase de configuración de Caffeine separada (una sola clave por cliente no justifica la dependencia extra).

Dos decisiones de diseño no triviales tomadas al implementar (documentadas también en el código):
1. **`SqsRetryScheduler` no usa `ChangeMessageVisibility` real** (requiere el receipt handle, que solo existe en el listener aún no construido); en su lugar republica un mensaje con `delaySeconds` capado a 900s (límite nativo de `SendMessage` en SQS). Revisar cuando se construya `DeliveriesSqsListener`.
2. **GSI añadido** a `notification_events` (`notification_event_id-index`, PK=notification_event_id) — el puerto `findById` solo recibe el id determinista, y la PK/SK de la tabla base (client_id / delivery_date#id) no alcanzaban para resolverlo sin ese índice. Documentado en `data-model.md`.

`mvn test` (offline): 42/42 en verde (41 anteriores + smoke test de arranque de contexto Spring `NotifierApplicationContextSmokeTest`, agregado para verificar que los 6 adaptadores nuevos resuelven sus dependencias). Se encontró y corrigió una dependencia real faltante: `spring-boot-starter-actuator` (requerida por la autoconfiguración de métricas CloudWatch de Spring Cloud AWS). **T025/T033 (integration tests con Testcontainers+LocalStack) no se ejecutaron: Docker no está disponible en este entorno.**

**Progreso (2026-08-22, 3ª pasada)**: adaptadores de entrada (`@SqsListener`) y wiring de configuración, como pidió el usuario. Completadas: T011 (ya completa: se agregó `RawEventMessage`, el que faltaba junto a `DeliveryMessage`), T017, T018.

- `RawEventsSqsListener` invoca `MatchEventUseCase`; el ack (borrado del mensaje) es automático al retornar sin excepción — como `MatchEventService` publica cada tarea de forma síncrona y propaga cualquier fallo, eso ya satisface FR-004 sin código adicional.
- `DeliveriesSqsListener` invoca `DispatchDeliveryUseCase`; el mismo ack automático cubre los 3 desenlaces (éxito, permanente, transitorio) porque en el caso transitorio `DispatchDeliveryService` ya publicó un mensaje nuevo vía `SqsRetryScheduler` antes de retornar.
- **"Wiring de configuración"** se resolvió con una clase nueva no listada originalmente en T012: `UseCaseConfig` (`application/config`). Es necesaria porque `core/usecase` (`MatchEventService`, `DispatchDeliveryService`) es Java puro sin anotaciones Spring (constitución, Principio I) — algo de infraestructura tiene que construirlos como beans inyectándoles los adaptadores, y exponerlos como los puertos de entrada (`MatchEventUseCase`, `DispatchDeliveryUseCase`) que los listeners consumen. `Resilience4jConfig`/`CacheConfig` (el resto de T012) siguen diferidos, sin cambios respecto a la pasada anterior.

**Hallazgo durante la verificación**: el smoke test de arranque (`NotifierApplicationContextSmokeTest`) empezó a fallar al agregar los listeners — el contenedor `@SqsListener` intenta resolver la cola contra AWS real al arrancar (`GetQueueUrl` vía credenciales IMDS), y en este entorno sin red a AWS eso agota el timeout y tumba el contexto. Es el comportamiento correcto para producción (ahí sí hay que resolver la cola), así que el fix fue solo de la prueba: un `@TestConfiguration` anidado en el smoke test reemplaza el container factory con `autoStartup(false)`, para verificar el wiring de beans sin ejercitar el polling real. `mvn test` (offline): 42/42 en verde (ninguno nuevo — el conteo no cambia porque no se agregaron tests, solo adaptadores de producción).

**Sin cambios pendientes de esta pasada**: T025/T033 (Testcontainers+LocalStack) siguen bloqueadas por falta de Docker en este entorno.

**Organization**: Tareas agrupadas por historia de usuario (spec.md) para permitir implementación y prueba independiente de cada una.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivo distinto, sin dependencias pendientes)
- **[Story]**: Historia de usuario a la que pertenece (US1..US4)
- Rutas de archivo exactas en cada descripción

## Path Conventions

- **Single project**: `src/main/java/com/cobre/notifications/delivery/**`, `src/test/java/com/cobre/notifications/delivery/**`, `src/main/resources/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Inicialización del proyecto Maven y esqueleto de paquetes

- [X] T001 Crear `pom.xml` (Java 21, Spring Boot 3.x, Spring Cloud AWS BOM [SQS, Secrets Manager, DynamoDB Enhanced Client], Resilience4j-spring-boot3, Micrometer registry-cloudwatch2, spring-boot-starter-web para `RestClient`, spring-boot-starter-test/JUnit 5, Testcontainers junit-jupiter + localstack) en `pom.xml`
- [X] T002 [P] Crear el esqueleto de paquetes bajo `src/main/java/com/cobre/notifications/delivery/{application/config,core/domain,core/dto,core/mapper,core/ports/in,core/ports/out,core/usecase,core/utils,infrastructure/adapter/in/sqs/message,infrastructure/adapter/out/dynamo/entity,infrastructure/adapter/out/sqs,infrastructure/adapter/out/http,infrastructure/adapter/out/secrets,infrastructure/adapter/out/metrics}` y su espejo en `src/test/java/com/cobre/notifications/delivery/{core/usecase,core/utils,infrastructure/adapter}`
- [X] T003 [P] Crear `src/main/resources/application.yml` con placeholders para nombres/URLs de colas (`raw-events`, `deliveries`), tablas (`subscriptions`, `notification_events`), timeouts HTTP (conexión/lectura), `maxRetries=6`, TTL del cache de secretos, namespace de métricas, y `spring.threads.virtual.enabled=true`
- [X] T004 Crear `NotifierApplication.java` (`@SpringBootApplication`) en `src/main/java/com/cobre/notifications/delivery/NotifierApplication.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Modelos de dominio, puertos y config compartidos por todas las historias

**⚠️ CRITICAL**: Ninguna historia de usuario puede empezar hasta que esta fase esté completa

- [X] T005 [P] Modelos de dominio `RawEvent`, `Subscription`, `DeliveryTask`, `DeliveryOutcome`, `DeliveryStatus` (enum `COMPLETED`/`FAILED`), `HttpResult` per [data-model.md](./data-model.md) en `src/main/java/com/cobre/notifications/delivery/core/domain/*.java`
- [X] T006 [P] Puertos de entrada `MatchEventUseCase`, `DispatchDeliveryUseCase` en `src/main/java/com/cobre/notifications/delivery/core/ports/in/*.java`
- [X] T007 [P] Puertos de salida `SubscriptionRepository`, `DeliveryTaskPublisher`, `WebhookClient`, `SignatureResolver`, `DeliveryOutcomeRepository`, `DeliveryRetryScheduler`, `DeliveryMetrics` en `src/main/java/com/cobre/notifications/delivery/core/ports/out/*.java`
- [X] T008 [P] DTOs del núcleo `RawEventCommand`, `DeliveryCommand` y `DomainMapper` en `src/main/java/com/cobre/notifications/delivery/core/dto/*.java` y `core/mapper/DomainMapper.java`
- [X] T009 [P] Test `NotificationIdFactoryTest` (mismo `event_id`+`subscription_id` → mismo id; inputs distintos → ids distintos) en `src/test/java/com/cobre/notifications/delivery/core/utils/NotificationIdFactoryTest.java` — escribir y verificar que falla antes de T010
- [X] T010 `NotificationIdFactory` (`hash(event_id + subscription_id)` determinista) en `src/main/java/com/cobre/notifications/delivery/core/utils/NotificationIdFactory.java` (hace pasar T009)
- [ ] T011 [P] DTOs de mensaje SQS `RawEventMessage`, `DeliveryMessage` (esquema per [contracts/raw-events.md](./contracts/raw-events.md) y [contracts/deliveries.md](./contracts/deliveries.md)) en `src/main/java/com/cobre/notifications/delivery/infrastructure/adapter/in/sqs/message/*.java`
- [ ] T012 [P] Clases de configuración `AwsConfig`, `RestClientConfig`, `Resilience4jConfig`, `CacheConfig`, `DeliveryProperties` (`@ConfigurationProperties` sobre T003) en `src/main/java/com/cobre/notifications/delivery/application/config/*.java`

**Checkpoint**: Fundación lista — las historias de usuario pueden empezar

---

## Phase 3: User Story 1 - Entrega exitosa de una notificación a un webhook (Priority: P1) 🎯 MVP

**Goal**: Un evento con al menos una suscripción activa se entrega firmado al webhook del cliente y queda un único desenlace `completed` por suscripción.

**Independent Test**: Publicar un evento con una suscripción activa cuyo webhook responde 2xx de inmediato → el webhook recibe exactamente una solicitud firmada y queda un único desenlace `completed`. Con dos suscripciones activas → dos entregas independientes con id determinista propio.

### Tests for User Story 1

> **Escribir estos tests PRIMERO y verificar que fallan antes de implementar**

- [X] T013 [P] [US1] `MatchEventServiceTest`: fan-out genera una `DeliveryTask` por cada suscripción activa (con `SubscriptionRepository`/`DeliveryTaskPublisher` mockeados), cada una con `notification_event_id` determinista (T010); la consulta de suscripciones se acota al `client_id` del evento (A01) en `src/test/java/com/cobre/notifications/delivery/core/usecase/MatchEventServiceTest.java`
- [X] T014 [P] [US1] `DispatchDeliveryServiceTest` (camino de éxito): `HttpResult` 2xx → persiste exactamente un `DeliveryOutcome COMPLETED` vía `DeliveryOutcomeRepository`; firma el payload vía `SignatureResolver` antes de `WebhookClient.post`; no invoca `DeliveryRetryScheduler` en `src/test/java/com/cobre/notifications/delivery/core/usecase/DispatchDeliveryServiceTest.java`

### Implementation for User Story 1

- [X] T015 [US1] `MatchEventService` implementando `MatchEventUseCase` — camino feliz: query de suscripciones activas por `client_id`+`event_type`, una `DeliveryTask` por suscripción vía `NotificationIdFactory`, publicación vía `DeliveryTaskPublisher` (rama "sin suscriptores" se añade en US3) en `src/main/java/com/cobre/notifications/delivery/core/usecase/MatchEventService.java` (depende de T013, T005-T008, T010)
- [X] T016 [US1] `DispatchDeliveryService` implementando `DispatchDeliveryUseCase` — camino de éxito: resolver firma, `WebhookClient.post`, en 2xx persistir `DeliveryOutcome COMPLETED` y emitir métrica `entrega_resultado` (outcome=completed) vía `DeliveryMetrics.result(...)`; guarda de idempotencia: no reentregar si ya existe `completed` para ese `notification_event_id` (ramas transitorio/permanente se añaden en US2/US4, reutilizando el mismo helper interno de "persistir + emitir métrica") en `src/main/java/com/cobre/notifications/delivery/core/usecase/DispatchDeliveryService.java` (depende de T014, T005-T008)
- [X] T017 [P] [US1] `RawEventsSqsListener` (`@SqsListener` sobre `raw-events`) invoca `MatchEventUseCase`; confirma (borra) el mensaje solo después de que todas las tareas quedaron encoladas (FR-004) en `src/main/java/com/cobre/notifications/delivery/infrastructure/adapter/in/sqs/RawEventsSqsListener.java`
- [X] T018 [P] [US1] `DeliveriesSqsListener` (`@SqsListener` sobre `deliveries`) invoca `DispatchDeliveryUseCase` en `src/main/java/com/cobre/notifications/delivery/infrastructure/adapter/in/sqs/DeliveriesSqsListener.java`
- [X] T019 [P] [US1] `DynamoSubscriptionRepository` implementando `SubscriptionRepository` (`Query` PK=`client_id`, SK `begins_with(event_type#)`, filtro `active=true`) + `SubscriptionEntity` en `src/main/java/com/cobre/notifications/delivery/infrastructure/adapter/out/dynamo/DynamoSubscriptionRepository.java` y `entity/SubscriptionEntity.java`
- [X] T020 [P] [US1] `DynamoOutcomeRepository` implementando `DeliveryOutcomeRepository` (una sola escritura en estado terminal, `ttl`=90d) + `NotificationEventEntity` en `src/main/java/com/cobre/notifications/delivery/infrastructure/adapter/out/dynamo/DynamoOutcomeRepository.java` y `entity/NotificationEventEntity.java`
- [X] T021 [P] [US1] `SqsDeliveryPublisher` implementando `DeliveryTaskPublisher` (publica `DeliveryTask` en `deliveries`) en `src/main/java/com/cobre/notifications/delivery/infrastructure/adapter/out/sqs/SqsDeliveryPublisher.java`
- [X] T022 [P] [US1] `RestClientWebhookClient` implementando `WebhookClient` (POST con header `X-Cobre-Signature`, timeouts de conexión/lectura per [contracts/webhook-delivery.md](./contracts/webhook-delivery.md)) en `src/main/java/com/cobre/notifications/delivery/infrastructure/adapter/out/http/RestClientWebhookClient.java`
- [X] T023 [P] [US1] `SecretsManagerSignatureResolver` implementando `SignatureResolver` (resuelve `cobre/webhook-hmac/{client_id}`, cache en memoria con TTL, firma HMAC-SHA256) en `src/main/java/com/cobre/notifications/delivery/infrastructure/adapter/out/secrets/SecretsManagerSignatureResolver.java`
- [X] T024 [P] [US1] `MicrometerDeliveryMetrics` implementando `DeliveryMetrics` (`result(clientId, eventType, outcome)` con `Counter` tags `client_id`/`event_type`; `noSubscribers(...)` se completa en US3) en `src/main/java/com/cobre/notifications/delivery/infrastructure/adapter/out/metrics/MicrometerDeliveryMetrics.java`
- [ ] T025 [US1] Integration tests `DynamoSubscriptionRepositoryIT` (Query begins_with contra LocalStack) y `RestClientWebhookClientIT` (POST firmado + timeouts) en `src/test/java/com/cobre/notifications/delivery/infrastructure/adapter/DynamoSubscriptionRepositoryIT.java` y `RestClientWebhookClientIT.java` (depende de T019, T022)

**Checkpoint**: User Story 1 funcional y probable de forma independiente (primer escenario de [quickstart.md](./quickstart.md))

---

## Phase 4: User Story 2 - Reintento automático ante fallas transitorias del webhook (Priority: P2)

**Goal**: Un fallo transitorio (timeout/5xx/429) reencola la entrega con backoff exponencial + jitter vía SQS, hasta 6 intentos; al agotarse, se persiste `failed`.

**Independent Test**: Webhook que responde 503 en los primeros intentos y 200 en el tercero → reintentos con esperas crecientes, desenlace final `completed` solo tras el éxito, sin duplicar el registro.

### Tests for User Story 2

- [X] T026 [P] [US2] `BackoffCalculatorTest`: `base = min(30 * 2^intento, 3600)`, espera final en `[base/2, base]` (equal jitter) en `src/test/java/com/cobre/notifications/delivery/core/utils/BackoffCalculatorTest.java`
- [X] T027 [P] [US2] `RetryDecisionTest`: `{timeout, 5xx, 429}` → transitorio; resto de 4xx → permanente en `src/test/java/com/cobre/notifications/delivery/core/utils/RetryDecisionTest.java`
- [X] T028 [US2] Ampliar `DispatchDeliveryServiceTest`: `HttpResult` transitorio → reencola vía `DeliveryRetryScheduler` con `attempt_count+1` y no persiste desenlace; `attempt_count==6` agotado → persiste `FAILED` en `src/test/java/com/cobre/notifications/delivery/core/usecase/DispatchDeliveryServiceTest.java` (mismo archivo que T014)

### Implementation for User Story 2

- [X] T029 [US2] `BackoffCalculator` (fórmula exponencial + equal jitter) en `src/main/java/com/cobre/notifications/delivery/core/utils/BackoffCalculator.java` (depende de T026)
- [X] T030 [US2] `RetryDecision` (clasifica `HttpResult` → transitorio/permanente) en `src/main/java/com/cobre/notifications/delivery/core/utils/RetryDecision.java` (depende de T027)
- [X] T031 [US2] Ampliar `DispatchDeliveryService`: rama transitoria reencola vía `DeliveryRetryScheduler` con el delay de `BackoffCalculator`; rama de intentos agotados (`attempt_count>=6`) persiste `FAILED` en `src/main/java/com/cobre/notifications/delivery/core/usecase/DispatchDeliveryService.java` (depende de T028-T030, extiende T016)
- [X] T032 [P] [US2] `SqsRetryScheduler` implementando `DeliveryRetryScheduler` (`ChangeMessageVisibility` con el delay calculado) en `src/main/java/com/cobre/notifications/delivery/infrastructure/adapter/out/sqs/SqsRetryScheduler.java`
- [ ] T033 [US2] Integration test `SqsDeliveryPublisherIT` (publicación en `deliveries` + `ChangeMessageVisibility` de reintento contra LocalStack) en `src/test/java/com/cobre/notifications/delivery/infrastructure/adapter/SqsDeliveryPublisherIT.java` (depende de T021, T032)

**Checkpoint**: User Story 1 y 2 funcionan de forma independiente (escenario "fallo transitorio" de [quickstart.md](./quickstart.md))

---

## Phase 5: User Story 3 - Descarte de eventos sin suscriptores (Priority: P2)

**Goal**: Un evento sin ninguna suscripción activa coincidente se descarta sin generar entregas, incrementando una métrica de visibilidad.

**Independent Test**: Publicar un evento de un cliente/tipo sin suscripciones activas → no se encola ninguna tarea y se incrementa `eventos_sin_suscriptores`.

### Tests for User Story 3

- [X] T034 [US3] Ampliar `MatchEventServiceTest`: sin suscripciones activas → no se publica ninguna `DeliveryTask` y se invoca `DeliveryMetrics.noSubscribers(clientId, eventType)` en `src/test/java/com/cobre/notifications/delivery/core/usecase/MatchEventServiceTest.java` (mismo archivo que T013)

### Implementation for User Story 3

- [X] T035 [US3] Ampliar `MatchEventService`: rama de cero suscripciones activas → omitir publicación y llamar `DeliveryMetrics.noSubscribers(...)` en `src/main/java/com/cobre/notifications/delivery/core/usecase/MatchEventService.java` (depende de T034, extiende T015; reutiliza `MicrometerDeliveryMetrics` de T024)

**Checkpoint**: User Story 1, 2 y 3 funcionan de forma independiente (escenario "sin suscriptores" de [quickstart.md](./quickstart.md))

---

## Phase 6: User Story 4 - Fallo permanente sin reintento (Priority: P3)

**Goal**: Un rechazo permanente del webhook (4xx ≠ 429) persiste `failed` de inmediato, sin reintentos.

**Independent Test**: Webhook que responde 400 → desenlace `failed` en el primer intento, sin reencolado.

### Tests for User Story 4

- [X] T036 [US4] Ampliar `DispatchDeliveryServiceTest`: `HttpResult` permanente (4xx≠429) → persiste `FAILED` de inmediato, sin invocar `DeliveryRetryScheduler` en `src/test/java/com/cobre/notifications/delivery/core/usecase/DispatchDeliveryServiceTest.java` (mismo archivo que T014/T028)

### Implementation for User Story 4

- [X] T037 [US4] Ampliar `DispatchDeliveryService`: rama permanente (vía `RetryDecision` de T030) persiste `FAILED` inmediato y emite métrica `entrega_resultado` (outcome=failed) reutilizando el helper de T016 en `src/main/java/com/cobre/notifications/delivery/core/usecase/DispatchDeliveryService.java` (depende de T036, extiende T031)

**Checkpoint**: Las 4 historias de usuario funcionan de forma independiente (escenario "fallo permanente" de [quickstart.md](./quickstart.md))

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Endurecimiento de seguridad y validación final transversal a todas las historias

- [ ] T038 [P] Test `WebhookUrlValidatorTest` (predicado puro: bloquea rangos privados/reservados RFC1918, loopback, link-local y `169.254.169.254` dada una IP resuelta) en `src/test/java/com/cobre/notifications/delivery/core/utils/WebhookUrlValidatorTest.java`
- [ ] T039 `WebhookUrlValidator` (predicado anti-SSRF, sin dependencias de red) en `src/main/java/com/cobre/notifications/delivery/core/utils/WebhookUrlValidator.java` (depende de T038; cierra la brecha señalada en [plan.md](./plan.md) Constitution Check)
- [ ] T040 Integrar `WebhookUrlValidator` + *pinning* anti DNS-rebinding en `RestClientWebhookClient` (resolver IP una vez, validar, conectar a la IP fijada; URL inválida → tratar como fallo permanente) en `src/main/java/com/cobre/notifications/delivery/infrastructure/adapter/out/http/RestClientWebhookClient.java` (depende de T039, extiende T022)
- [ ] T041 [P] Circuit breaker Resilience4j opcional por host de webhook, wireado en `RestClientWebhookClient` vía `Resilience4jConfig` en `src/main/java/com/cobre/notifications/delivery/infrastructure/adapter/out/http/RestClientWebhookClient.java` y `application/config/Resilience4jConfig.java`
- [ ] T042 [P] Contract test de reproducibilidad de firma HMAC (mismo secreto+payload → misma firma) ampliando `SecretsManagerSignatureResolverIT` en `src/test/java/com/cobre/notifications/delivery/infrastructure/adapter/SecretsManagerSignatureResolverIT.java`
- [ ] T043 Ejecutar la validación end-to-end de [quickstart.md](./quickstart.md) (los 3 escenarios: éxito, fallo transitorio, fallo permanente, más el reproceso idempotente) contra LocalStack

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias — puede empezar de inmediato
- **Foundational (Phase 2)**: depende de Setup — BLOQUEA todas las historias
- **User Stories (Phase 3-6)**: todas dependen de Foundational
  - US1 no depende de otras historias
  - US2 extiende archivos de US1 (`DispatchDeliveryService`, su test) pero es probable de forma independiente una vez añadida
  - US3 extiende archivos de US1 (`MatchEventService`, su test) — independiente una vez añadida
  - US4 extiende archivos de US2 (`DispatchDeliveryService`, su test) — independiente una vez añadida
- **Polish (Phase 7)**: depende de que las historias que se quieran completar ya estén hechas (en particular T040 extiende `RestClientWebhookClient` de US1/T022)

### User Story Dependencies

- **US1 (P1)**: solo depende de Foundational
- **US2 (P2)**: depende de Foundational; comparte archivo (`DispatchDeliveryService`) con US1 pero añade una rama nueva sin romper la de éxito
- **US3 (P2)**: depende de Foundational; comparte archivo (`MatchEventService`) con US1 pero añade una rama nueva sin romper el fan-out
- **US4 (P3)**: depende de Foundational; comparte archivo (`DispatchDeliveryService`) con US1/US2 (reutiliza `RetryDecision` de US2)

### Within Each User Story

- Tests primero (deben fallar) → luego implementación
- Modelos/puertos (Foundational) antes que cualquier servicio de caso de uso
- Adaptadores de entrada/salida después del servicio de caso de uso que implementan
- Historia completa y probada antes de pasar a la siguiente prioridad

### Parallel Opportunities

- Setup: T002, T003 en paralelo tras T001
- Foundational: T005-T008, T011, T012 en paralelo; T009 en paralelo con ellos (T010 espera a T009)
- US1: T013, T014 en paralelo; luego T017-T024 en paralelo entre sí (todos dependen de T015/T016 pero son archivos distintos)
- US2: T026, T027 en paralelo; T032 en paralelo con T029/T030
- Distintas historias de usuario pueden trabajarse en paralelo por distintas personas una vez completada la fase Foundational, teniendo en cuenta que US2/US3 tocan los mismos archivos que US1 y US4 los mismos que US2 (coordinar esos merges)

---

## Parallel Example: User Story 1

```bash
# Tests de US1 en paralelo:
Task: "MatchEventServiceTest en src/test/java/com/cobre/notifications/delivery/core/usecase/MatchEventServiceTest.java"
Task: "DispatchDeliveryServiceTest en src/test/java/com/cobre/notifications/delivery/core/usecase/DispatchDeliveryServiceTest.java"

# Adaptadores de US1 en paralelo (tras T015/T016):
Task: "RawEventsSqsListener en .../infrastructure/adapter/in/sqs/RawEventsSqsListener.java"
Task: "DynamoSubscriptionRepository en .../infrastructure/adapter/out/dynamo/DynamoSubscriptionRepository.java"
Task: "RestClientWebhookClient en .../infrastructure/adapter/out/http/RestClientWebhookClient.java"
Task: "SecretsManagerSignatureResolver en .../infrastructure/adapter/out/secrets/SecretsManagerSignatureResolver.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1: Setup
2. Completar Phase 2: Foundational (bloquea todas las historias)
3. Completar Phase 3: User Story 1
4. **DETENER y VALIDAR**: probar User Story 1 de forma independiente (quickstart.md, escenario de éxito)
5. Desplegar/demostrar si está listo

### Incremental Delivery

1. Setup + Foundational → fundación lista
2. + US1 → probar independientemente → MVP
3. + US2 → probar independientemente (reintentos)
4. + US3 → probar independientemente (sin suscriptores)
5. + US4 → probar independientemente (fallo permanente)
6. + Polish (anti-SSRF, circuit breaker, validación end-to-end completa)

---

## Notes

- [P] = archivos distintos, sin dependencias pendientes
- [Story] mapea la tarea a su historia de usuario para trazabilidad
- Verificar que cada test falla antes de implementar (constitución, Principio II)
- US2/US3/US4 amplían archivos creados en US1 (mismo caso de uso, rama nueva) — es una dependencia secuencial esperada, no rompe la prueba independiente de cada historia una vez añadida su tarea
- Confirmar tras cada checkpoint que las historias previas siguen pasando antes de continuar con la siguiente
