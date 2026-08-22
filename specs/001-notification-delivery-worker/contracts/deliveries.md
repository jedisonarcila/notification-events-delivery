# Contract: `deliveries` (tarea de entrega)

**Feature**: [../spec.md](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

Cola SQS interna: el worker se publica y se consume a sí mismo esta cola —
el fan-out (CU-1) produce estos mensajes y el dispatch (CU-2/CU-3) los
consume, reencolando cuando corresponde reintentar.

## Esquema

```json
{
  "notification_event_id": "string",
  "client_id": "string",
  "event_type": "string",
  "subscription_id": "string",
  "webhook_url": "string",
  "content": "object",
  "delivery_date": "string (ISO-8601 instant)",
  "attempt_count": "integer"
}
```

| Campo | Obligatorio | Descripción |
|---|---|---|
| `notification_event_id` | sí | `hash(event_id + subscription_id)`, determinista. Clave de idempotencia y de correlación con `notification_events` en DynamoDB. |
| `client_id` | sí | Heredado del evento/suscripción; PK de aislamiento A01 en toda consulta que dispare este mensaje. |
| `event_type` | sí | Heredado del evento; usado en métricas. |
| `subscription_id` | sí | Suscripción destino de esta tarea. |
| `webhook_url` | sí | Snapshot del destino al momento del fan-out. |
| `content` | sí | Payload de negocio a firmar y enviar. |
| `delivery_date` | sí | Heredado del evento de origen. |
| `attempt_count` | sí | Empieza en `0`; se incrementa en cada reencolado por fallo transitorio. Nunca supera 6 antes de que el worker declare el desenlace `failed`. |

## Reglas de reencolado (reintento)

- El worker **MUST** reencolar el mismo `notification_event_id` con
  `attempt_count + 1` cuando el resultado del intento se clasifique como
  transitorio (timeout, 5xx, 429) y `attempt_count < 6`.
- La espera antes de que el mensaje vuelva a ser visible **MUST** aplicarse
  vía `ChangeMessageVisibility` sobre el mensaje ya encolado — no se publica
  un mensaje nuevo con delay, y no se usa espera activa en el proceso.
- Duración de la espera: `base = min(30 * 2^attempt_count, 3600)` segundos;
  visibilidad real = `base/2 + random(0, base/2)` (equal jitter). Ver
  [../research.md](../research.md) §3.
- Al alcanzar `attempt_count == 6` con un fallo transitorio adicional, el
  worker **MUST** persistir el desenlace `failed` en vez de reencolar de
  nuevo.
- Un fallo permanente (4xx que no sea 429) o un éxito (2xx) **MUST NOT**
  reencolar el mensaje bajo ninguna circunstancia — el desenlace queda
  escrito de inmediato.

## Nota de infraestructura

El `maxReceiveCount` configurado en la Dead Letter Queue de `deliveries` es
una red de seguridad de infraestructura (ante fallos no clasificados por la
lógica de negocio), no el mecanismo que impone el límite de 6 intentos — ese
límite lo decide `RetryDecision` en el dominio a partir de `attempt_count`.
