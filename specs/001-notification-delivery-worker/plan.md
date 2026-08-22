# Implementation Plan: Notification Delivery Worker

**Branch**: `001-notification-delivery-worker` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-notification-delivery-worker/spec.md`,
technical decisions from `spec-tecnico.md` (repo root)

## Summary

Worker headless (sin HTTP) que consume eventos de negocio, resuelve las
suscripciones activas del cliente para ese tipo de evento (matching +
fan-out), entrega cada notificación como un POST HTTPS firmado al webhook del
cliente, clasifica el resultado (éxito / fallo transitorio / fallo
permanente), reintenta los fallos transitorios delegando la espera en SQS
(hasta 6 intentos con backoff exponencial + jitter), y persiste exactamente
un desenlace terminal (`completed`/`failed`) por notificación. El enfoque
técnico es arquitectura hexagonal estricta: un núcleo de dominio en Java puro
(sin Spring/AWS) rodeado de adaptadores Spring/AWS que implementan los
puertos de entrada y salida.

## Technical Context

**Language/Version**: Java 21 con virtual threads habilitados
(`spring.threads.virtual.enabled=true`)

**Primary Dependencies**: Spring Boot 3.x · Spring Cloud AWS (SQS, Secrets
Manager, DynamoDB `DynamoDbTemplate`, métricas) · `RestClient` (HTTP
saliente) · Resilience4j (backoff/jitter + circuit breaker) · Micrometer →
CloudWatch Metrics

**Storage**: DynamoDB — tablas `notification_events` y `subscriptions` (ver
[data-model.md](./data-model.md))

**Testing**: JUnit 5 puro para el núcleo de dominio; Testcontainers +
LocalStack para adaptadores de infraestructura (SQS, DynamoDB, Secrets
Manager)

**Target Platform**: worker headless stateless en contenedor (sin endpoints
HTTP expuestos); cualquier pod puede procesar cualquier mensaje

**Project Type**: single project — backend worker (sin frontend, sin API
pública)

**Performance Goals**: ~2.000 eventos/s sostenidos, picos de 5.000/s,
absorbidos por el buffer de SQS

**Constraints**: debe tolerar reprocesamiento at-least-once de SQS
(idempotente); máximo 6 intentos de entrega por notificación con backoff
exponencial + *equal jitter* (base 30s, tope 3.600s); registros de desenlace
con TTL de 90 días; la espera entre reintentos la ejecuta SQS
(`ChangeMessageVisibility`), nunca `Thread.sleep`

**Scale/Scope**: un solo módulo Maven, arquitectura hexagonal con núcleo de
dominio + adaptadores de entrada (2 listeners SQS) y adaptadores de salida
(DynamoDB ×2, SQS ×2, HTTP, Secrets Manager, métricas) — ver árbol completo
en [Project Structure](#project-structure)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluado contra `.specify/memory/constitution.md` v1.0.0:

| Principio | Veredicto | Cómo lo satisface el diseño |
|---|---|---|
| I. Arquitectura Hexagonal Estricta | PASS | `core/` (dominio) sin dependencias de Spring/AWS; toda dependencia externa entra por un puerto en `core/ports/{in,out}`, implementado en `infrastructure/adapter/**`. Sin endpoints HTTP expuestos (worker headless). |
| II. TDD | PASS (gate de proceso) | Cada servicio/utilidad del núcleo (`Matcher`, `Dispatcher`, `RetryDecision`, `NotificationIdFactory`, `BackoffCalculator`) se implementa con test JUnit 5 primero. El orden test-first exacto se secuencia en `tasks.md` (fase siguiente), no en este plan. |
| III. Idempotencia | PASS | `NotificationIdFactory` deriva `notification_event_id = hash(event_id + subscription_id)` de forma determinista; `DispatchDeliveryService` no reentrega si ya existe un desenlace `completed`. |
| IV. Escritura Única de Desenlace | PASS | `DeliveryOutcomeRepository.save(...)` se invoca una sola vez, solo en estado terminal (`COMPLETED`/`FAILED`). No existe ninguna escritura de estado intermedio/`pending`. |
| V. Reintentos Delegados a SQS | PASS | `DeliveryRetryScheduler` (puerto) / `SqsRetryScheduler` (adaptador) usa `ChangeMessageVisibility`; cero usos de `Thread.sleep` en el diseño. |
| VI. Aislamiento A01 | PASS | `SubscriptionRepository.findActive(clientId, eventType)` y las tablas DynamoDB usan `client_id` como partition key en toda consulta de datos de cliente. |

Sin violaciones → no se requiere tabla de Complexity Tracking.

**Brecha detectada (no bloqueante, a resolver en `/speckit-tasks`)**: la
sección de Seguridad de `spec-tecnico.md` exige validar el `webhook_url`
contra rangos privados / metadata (169.254.169.254) y aplicar *pinning*
anti DNS-rebinding antes de cada POST, pero el árbol de paquetes de
`spec-tecnico.md` §3 no nombra un archivo específico para esa validación.
Candidato de diseño: un predicado puro en `core/utils` (testable sin
infraestructura, dado un conjunto de IPs resueltas) invocado desde
`RestClientWebhookClient` antes de cada intento de entrega. Esta decisión de
ubicación exacta se cierra en la fase de tasks, no aquí.

## Project Structure

### Documentation (this feature)

```text
specs/001-notification-delivery-worker/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── raw-events.md
│   ├── deliveries.md
│   └── webhook-delivery.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Estructura ya cerrada en `spec-tecnico.md` §3 (single project, worker
backend headless) — se referencia aquí, no se reinventa:

```text
notification-events-delivery/
├── pom.xml
├── src/main/java/com/cobre/notifications/delivery
│   ├── NotifierApplication.java                 (@SpringBootApplication)
│   ├── application/config                       (AwsConfig, RestClientConfig,
│   │                                              Resilience4jConfig, CacheConfig,
│   │                                              DeliveryProperties)
│   ├── core                                     (NÚCLEO — sin Spring, sin AWS)
│   │   ├── domain        (RawEvent, Subscription, DeliveryTask, DeliveryOutcome,
│   │   │                  DeliveryStatus, HttpResult)
│   │   ├── dto            (RawEventCommand, DeliveryCommand)
│   │   ├── mapper         (DomainMapper)
│   │   ├── ports/in       (MatchEventUseCase, DispatchDeliveryUseCase)
│   │   ├── ports/out      (SubscriptionRepository, DeliveryTaskPublisher,
│   │   │                   WebhookClient, SignatureResolver,
│   │   │                   DeliveryOutcomeRepository, DeliveryRetryScheduler,
│   │   │                   DeliveryMetrics)
│   │   ├── usecase        (MatchEventService, DispatchDeliveryService)
│   │   └── utils          (NotificationIdFactory, BackoffCalculator,
│   │                       RetryDecision)
│   └── infrastructure/adapter
│       ├── in/sqs         (RawEventsSqsListener, DeliveriesSqsListener, message/*)
│       └── out
│           ├── dynamo     (DynamoSubscriptionRepository, DynamoOutcomeRepository, entity/*)
│           ├── sqs        (SqsDeliveryPublisher, SqsRetryScheduler)
│           ├── http       (RestClientWebhookClient)
│           ├── secrets    (SecretsManagerSignatureResolver)
│           └── metrics    (MicrometerDeliveryMetrics)
├── src/main/resources/application.yml
└── src/test/java/com/cobre/notifications/delivery
    ├── core/usecase       (MatchEventServiceTest, DispatchDeliveryServiceTest)
    ├── core/utils         (NotificationIdFactoryTest, BackoffCalculatorTest, RetryDecisionTest)
    └── infrastructure/adapter  (DynamoSubscriptionRepositoryIT, SqsDeliveryPublisherIT,
                                 RestClientWebhookClientIT)
```

**Structure Decision**: Single project (backend worker), sin frontend ni API
pública. El árbol completo, los contratos de mensaje y el modelo de datos se
detallan en `contracts/` y `data-model.md`; la creación real de estos
archivos (`pom.xml`, clases Java) queda para `/speckit-tasks` +
`/speckit-implement` — esta fase de plan solo fija el diseño y sus artefactos
de soporte.

**Corrección de paquete raíz**: `spec-tecnico.md` §3 usaba literalmente
`com.jarcila.notifier` en el árbol de paquetes, lo cual entra en conflicto
con `constitution.md` (Convenciones de Código: paquete raíz
`com.cobre.notifications.delivery`). Por la regla de Governance de la
constitución ("es la fuente de verdad cuando ambos documentos difieran"), el
árbol de arriba ya usa `com.cobre.notifications.delivery`; `spec-tecnico.md`
queda desactualizado en ese único punto y no se modifica (es un documento de
entrada, no un artefacto de spec-kit).

## Complexity Tracking

*No aplica: el Constitution Check no reportó violaciones.*
