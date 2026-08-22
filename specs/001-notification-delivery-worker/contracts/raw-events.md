# Contract: `raw-events` (mensaje de entrada)

**Feature**: [../spec.md](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

Cola SQS que el worker consume. Publicada por el productor (simulator /
Cobre), fuera del alcance de este sistema.

## Esquema

```json
{
  "event_id": "string",
  "client_id": "string",
  "event_type": "string",
  "content": "object",
  "delivery_date": "string (ISO-8601 instant)"
}
```

| Campo | Obligatorio | Descripción |
|---|---|---|
| `event_id` | sí | Identificador del evento, provisto por el productor. Junto con cada `subscription_id` resuelto, determina el `notification_event_id`. |
| `client_id` | sí | Cliente dueño del evento. Usado como PK para resolver suscripciones (aislamiento A01). |
| `event_type` | sí | Tipo de evento; se compara contra el `event_type` de las suscripciones activas del cliente. |
| `content` | sí | Payload de negocio; se reenvía tal cual (firmado) al webhook del cliente. |
| `delivery_date` | sí | Fecha del evento de origen. |

## Garantías del productor / consumidor

- **At-least-once**: el mismo mensaje puede llegar más de una vez. El
  worker **MUST** tolerarlo (idempotencia vía `notification_event_id`
  determinista — ver [data-model.md](../data-model.md)).
- **Confirmación (ack)**: el worker **MUST** eliminar el mensaje de
  `raw-events` únicamente después de encolar exitosamente todas las tareas
  de entrega derivadas (o de confirmar que no hay suscriptores). Un fallo a
  mitad de camino deja el mensaje visible de nuevo para SQS, y el
  reprocesamiento resultante debe converger al mismo resultado
  (idempotencia).
- **Sin suscriptores**: si no hay ninguna suscripción activa que coincida,
  el mensaje se confirma sin generar ninguna tarea, y se incrementa la
  métrica `eventos_sin_suscriptores` con tags `client_id`, `event_type`.
