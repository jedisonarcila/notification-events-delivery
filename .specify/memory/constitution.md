<!--
Sync Impact Report
==================
Version change: (template, unratified) → 1.0.0
Rationale for MAJOR: initial ratification of the project constitution — no prior
enforced version existed (the file only contained unfilled template placeholders).

Modified principles: n/a (first fill-in)
Added sections:
  - Core Principles I–VI (Arquitectura Hexagonal Estricta, TDD, Idempotencia,
    Escritura Única de Desenlace, Reintentos Delegados a SQS, Aislamiento A01)
  - Stack Tecnológico (decisiones cerradas)
  - Convenciones de Código
  - Governance
Removed sections: none (template placeholders replaced, no prior content existed)
Deferred / TODO placeholders: none — RATIFICATION_DATE set to the date this
constitution was first ratified (today), since no earlier ratification date exists.

Templates requiring alignment check:
  - .specify/templates/plan-template.md ⚠ pending manual review (not modified by this command)
  - .specify/templates/spec-template.md ⚠ pending manual review (not modified by this command)
  - .specify/templates/tasks-template.md ⚠ pending manual review (not modified by this command)
  (This command's scope is limited to the constitution file itself; dependent
  templates/commands read it at runtime and are not edited here.)
-->

# Notification Events Delivery Constitution

## Core Principles

### I. Arquitectura Hexagonal Estricta
El `domain` NO importa Spring, AWS SDK, ni ninguna librería de infraestructura:
solo Java puro. Toda dependencia externa (SQS, DynamoDB, Secrets Manager, cliente
HTTP saliente, métricas) entra por un puerto (interface) implementado en un
`adapter`. Este proyecto es un worker headless: NO se exponen endpoints HTTP.
Rationale: mantiene el núcleo de negocio (matching, fan-out, cálculo de
reintentos) testable de forma aislada y evita que decisiones de infraestructura
se filtren al dominio o que el proyecto derive en un servicio HTTP cuando su
propósito es consumir y procesar eventos de una cola.

### II. TDD (Test-Driven Development)
Primero se escribe el test (JUnit 5) del núcleo de dominio, luego la
implementación. Ciclo Red-Green-Refactor obligatorio antes de dar una pieza de
lógica de negocio por terminada.
Rationale: el dominio no tiene contenedor Spring que lo ejecute end-to-end
fácilmente; el test JUnit puro es la única red de seguridad rápida para validar
matching, cálculo de idempotencia y política de reintentos.

### III. Idempotencia
SQS es at-least-once: todo el flujo debe tolerar reprocesamiento del mismo
mensaje sin duplicar efectos. El identificador de negocio se deriva de forma
determinística: `notification_event_id = hash(event_id + subscription_id)`.
Rationale: sin una clave determinística, una redelivery de SQS (esperada, no un
caso extremo) produciría notificaciones duplicadas hacia el webhook del
cliente.

### IV. Escritura Única de Desenlace
Cada notificación tiene una única escritura de desenlace terminal
(`completed` o `failed`) en el almacenamiento de persistencia. No se escriben
estados intermedios en DynamoDB.
Rationale: elimina inconsistencias por escrituras parciales cuando el mismo
mensaje se reprocesa concurrentemente y simplifica el modelo de datos a un
solo hecho de negocio por notificación.

### V. Reintentos Delegados a SQS
La espera entre reintentos la controla SQS mediante `ChangeMessageVisibility`;
está prohibido usar `Thread.sleep` para esperar antes de reintentar. El
`attempt_count` viaja en el propio mensaje.
Rationale: `Thread.sleep` bloquea el hilo (incluso siendo virtual) y no
sobrevive a un reinicio o caída del worker; delegar la espera en SQS hace el
reintento resiliente, observable desde la cola y libre de recursos retenidos
en el proceso.

### VI. Aislamiento A01 (Multi-tenant)
Toda consulta a datos de cliente usa `client_id` como partition key (PK). No
se construyen queries que crucen datos de distintos `client_id` sin ese
filtro explícito.
Rationale: previene fugas de datos entre clientes (OWASP A01 — Broken Access
Control) al nivel del acceso a datos, que es donde ese riesgo es más barato de
cerrar de forma sistemática.

## Stack Tecnológico (decisiones cerradas)

Estas decisiones están cerradas; un cambio requiere justificación explícita y,
si aplica, una enmienda a esta constitución:

- Java 21 con virtual threads (`spring.threads.virtual.enabled=true`).
- Spring Boot 3.x, Maven.
- Spring Cloud AWS para SQS, Secrets Manager y DynamoDB (`DynamoDbTemplate`),
  y métricas.
- HTTP saliente con `RestClient` — NO WebClient, NO OpenFeign, porque la URL
  del webhook es dinámica por cliente.
- Resiliencia con Resilience4j (cálculo de backoff/jitter y circuit breaker).
- Métricas vía Micrometer hacia CloudWatch, con tags limitados a `client_id` y
  `event_type`: no se promueven más dimensiones de métricas que esas dos.
- Tests: JUnit 5 para el núcleo de dominio; Testcontainers + LocalStack para
  adaptadores de infraestructura.

## Convenciones de Código

- Paquete raíz: `com.cobre.notifications.delivery`.
- Puertos de entrada llevan el sufijo `UseCase`. Puertos de salida se nombran
  por su rol (`SubscriptionRepository`, `WebhookClient`, etc.), sin sufijo de
  tecnología.
- Un adaptador por puerto; el nombre del adaptador indica la tecnología usada
  (`DynamoSubscriptionRepository`, `RestClientWebhookClient`).
- Los adaptadores no contienen lógica de negocio, solo traducción entre el
  puerto y la tecnología externa.

## Governance

Esta constitución prevalece sobre cualquier otra práctica, convención o
preferencia individual dentro del proyecto. Ante un conflicto entre código
existente y un principio aquí definido, el principio gana y el código debe
corregirse.

- **Enmiendas**: cualquier cambio a esta constitución debe registrarse en el
  mismo commit que modifica este archivo, incluyendo el motivo del cambio y,
  si corresponde, el impacto sobre plantillas dependientes
  (`plan-template.md`, `spec-template.md`, `tasks-template.md`).
- **Versionado semántico** de esta constitución:
  - MAJOR: eliminación o redefinición incompatible de un principio existente.
  - MINOR: adición de un principio o sección, o expansión material de una guía
    existente.
  - PATCH: aclaraciones de redacción, correcciones tipográficas o
    refinamientos no semánticos.
- **Cumplimiento**: toda revisión de código (PR o su equivalente) debe
  verificar que el cambio respeta los principios I–VI. Cualquier excepción
  debe justificarse explícitamente en la descripción del cambio; la
  complejidad añadida sin esa justificación se rechaza.
- Use `CLAUDE.md` como guía operativa de desarrollo del día a día; esta
  constitución es la fuente de verdad cuando ambos documentos difieran.

**Version**: 1.0.0 | **Ratified**: 2026-08-22 | **Last Amended**: 2026-08-22
