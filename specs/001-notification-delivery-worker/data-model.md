# Data Model: Notification Delivery Worker

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Este documento describe las entidades del núcleo de dominio (Java puro, sin
anotaciones de persistencia) y el modelo de almacenamiento en DynamoDB que
las respalda. La correspondencia domain ↔ entidad DynamoDB la resuelve un
mapper en la capa de adaptadores (`DomainMapper` / entidades en
`infrastructure/adapter/out/dynamo/entity`), nunca el dominio mismo
(constitución, principio I).

## Entidades de dominio

### RawEvent
Evento de negocio entrante, tal como llega desde la cola `raw-events`.

| Campo | Tipo | Notas |
|---|---|---|
| `eventId` | String | Identificador propio del evento, provisto por el productor |
| `clientId` | String | Dueño del evento; PK de aislamiento A01 |
| `eventType` | String | Tipo de evento (e.g. `payment.created`) |
| `content` | String/JSON | Payload de negocio a entregar |
| `deliveryDate` | Instant | Fecha del evento (no de la entrega) |

### Subscription
Preferencia de un cliente por recibir un `eventType` en un `webhookUrl`.
Gestionada fuera de este sistema; el worker solo la consulta.

| Campo | Tipo | Notas |
|---|---|---|
| `subscriptionId` | String | Identificador de la suscripción |
| `clientId` | String | Dueño de la suscripción |
| `eventType` | String | Tipo de evento al que está suscrita |
| `webhookUrl` | String | Destino del POST |
| `active` | boolean | Solo las `true` participan en el fan-out |

### DeliveryTask
La intención de notificar un `RawEvent` concreto a una `Subscription`
concreta. Es lo que viaja por la cola `deliveries`.

| Campo | Tipo | Notas |
|---|---|---|
| `notificationEventId` | String | `hash(eventId + subscriptionId)` — determinista |
| `clientId` | String | Heredado del evento/suscripción |
| `eventType` | String | Heredado del evento |
| `subscriptionId` | String | Suscripción destino |
| `webhookUrl` | String | Snapshot del destino al momento del fan-out |
| `content` | String/JSON | Payload a firmar y enviar |
| `deliveryDate` | Instant | Heredado del evento |
| `attemptCount` | int | Empieza en 0; se incrementa en cada reintento |

### DeliveryOutcome
El registro terminal único por notificación (constitución, principio IV: una
sola escritura, nunca un estado intermedio).

| Campo | Tipo | Notas |
|---|---|---|
| `notificationEventId` | String | Igual al de `DeliveryTask` — clave de idempotencia |
| `clientId` | String | Aislamiento A01 |
| `eventType` | String | Para métricas/consulta |
| `deliveryStatus` | `DeliveryStatus` | `COMPLETED` \| `FAILED` — sin estados intermedios |
| `deliveryDate` | Instant | Momento del resultado final |
| `attemptCount` | int | Intentos realizados hasta el desenlace |
| `lastError` | String? | Presente solo si `FAILED` o si hubo reintentos previos |
| `webhookUrl` | String | Destino al que se intentó entregar |
| `ttl` | long (epoch seconds) | `deliveryDate` + 90 días |

### DeliveryStatus (enum)
`COMPLETED`, `FAILED`. No existe un tercer valor "pendiente"/"en curso": la
ausencia de registro *es* el estado "aún no resuelto".

### HttpResult
Resultado crudo de un intento de POST, interno al dominio para que
`RetryDecision` clasifique sin depender del tipo de cliente HTTP usado.

| Campo | Tipo | Notas |
|---|---|---|
| `statusCode` | Integer? | `null` si hubo timeout/error de red (sin respuesta) |
| `body` | String? | Cuerpo de la respuesta, si la hubo |

## Reglas de validación / transición

- Un `DeliveryTask` con `attemptCount >= 6` que vuelve a fallar
  transitoriamente **MUST** producir un `DeliveryOutcome` con
  `deliveryStatus = FAILED`, no un séptimo reintento (RN-4 de
  `spec-funcional.md`).
- Un `HttpResult` con `statusCode` en `{429}` o en el rango `[500,599]`, o
  `statusCode == null` (timeout/sin respuesta), se clasifica como
  **transitorio** → nuevo `DeliveryTask` reencolado con `attemptCount + 1`.
- Un `HttpResult` con `statusCode` en `[400,499] \ {429}` se clasifica como
  **permanente** → `DeliveryOutcome(FAILED)` inmediato, sin reintento.
- Un `HttpResult` con `statusCode` en `[200,299]` → `DeliveryOutcome(COMPLETED)`.
- Si ya existe un `DeliveryOutcome(COMPLETED)` para un `notificationEventId`,
  un `DeliveryTask` duplicado con el mismo id **MUST NOT** generar un nuevo
  intento de entrega (idempotencia, principio III).

## Modelo de almacenamiento (DynamoDB)

### Tabla `subscriptions`
- **PK**: `client_id`
- **SK**: `event_type#subscription_id`
- **Acceso dominante**: `Query` con `PK = client_id` y
  `SK begins_with(event_type#)`, filtrado luego por `active = true`
  (CU-1 de `spec-funcional.md`).

### Tabla `notification_events`
- **PK**: `client_id`
- **SK**: `delivery_date#notification_event_id`
- **GSI por status**: PK=`client_id#delivery_status`, SK=`delivery_date`
  (para que operación consulte fallidos/completados por cliente sin escanear
  toda la tabla).
- **GSI `notification_event_id-index`** *(añadido durante la implementación
  de `DynamoOutcomeRepository`)*: PK=`notification_event_id`, sin sort key.
  El puerto `DeliveryOutcomeRepository.findById(notificationEventId)` solo
  recibe el id determinista — ni `client_id` ni `delivery_date` — así que la
  PK/SK de la tabla base no alcanzan para resolverlo con un `load()` directo.
  Como el id ya es único a nivel global (hash determinista de
  `event_id + subscription_id`), este GSI no necesita sort key. **Requiere
  aprovisionarse con `ProjectionType.ALL`** para que la consulta por índice
  devuelva todos los atributos del desenlace, no solo las claves.
- **TTL**: 90 días desde `delivery_date`, vía el campo `ttl`.
- **Escritura**: `PutItem`/`UpdateItem` — una sola vez por
  `notification_event_id`, en estado terminal (principio IV).

## Contratos de mensaje relacionados

Ver [`contracts/raw-events.md`](./contracts/raw-events.md) y
[`contracts/deliveries.md`](./contracts/deliveries.md) para el formato JSON
exacto de los mensajes que transportan `RawEvent` y `DeliveryTask` por las
colas SQS.
