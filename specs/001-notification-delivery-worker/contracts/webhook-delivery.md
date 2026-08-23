# Contract: entrega saliente al webhook del cliente

**Feature**: [../spec.md](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

Contrato del POST HTTPS que el worker envía al `webhook_url` de cada
suscripción, y cómo se interpreta la respuesta.

## Solicitud

- **Método**: `POST`
- **URL**: `webhook_url` de la suscripción (dinámica, resuelta por fila de
  datos — ver [../research.md](../research.md) §1).
- **Header `X-Cobre-Signature`**: HMAC-SHA256 del cuerpo de la solicitud,
  calculado con el secreto propio del cliente (uno por `client_id`, no por
  suscripción — RN-6 de `spec-funcional.md`). El secreto se resuelve en
  `cobre/webhook-hmac/{client_id}` (Secrets Manager) y se cachea en memoria
  con TTL.
- **Cuerpo**: el `content` del evento, sin transformar.
- **Timeouts**: de conexión y de lectura, configurables (ver
  `DeliveryProperties` en `spec-tecnico.md` §8); un timeout se trata igual
  que una respuesta sin código (ver clasificación abajo).

## Validación previa (anti-SSRF)

Antes de cada intento, el `webhook_url` **MUST** validarse para bloquear:
- Rangos de IP privados/reservados (RFC 1918, loopback, link-local).
- La IP de metadata de nube `169.254.169.254`.
- Cambios de IP entre la validación y la conexión real (DNS-rebinding) —
  requiere *pinning* de la IP resuelta para la propia solicitud.

Un `webhook_url` que no pase esta validación **MUST** tratarse como fallo
permanente (no se reintenta apuntando al mismo destino inseguro).

## Clasificación de la respuesta

| Código de respuesta | Clasificación | Efecto |
|---|---|---|
| `200`–`299` | Éxito | `DeliveryOutcome(COMPLETED)` — una sola escritura |
| `429` | Transitorio | Reencolar con `attempt_count + 1` (ver [deliveries.md](./deliveries.md)) |
| `500`–`599` | Transitorio | Reencolar con `attempt_count + 1` |
| timeout / sin respuesta | Transitorio | Reencolar con `attempt_count + 1` |
| `400`–`499` (excepto `429`) | Permanente | `DeliveryOutcome(FAILED)` inmediato, sin reintento |
| `webhook_url` no pasa validación anti-SSRF | Permanente | `DeliveryOutcome(FAILED)` inmediato, sin reintento |

## Idempotencia del lado del receptor

El cliente receptor del webhook puede recibir la misma notificación más de
una vez solo durante el período de reintentos activos (mientras no exista
aún un `DeliveryOutcome(COMPLETED)`); una vez completada, el worker **MUST
NOT** reenviarla. El `notification_event_id`, aunque no viaja hoy como
campo explícito documentado en el cuerpo o headers de la solicitud saliente,
está disponible para que una futura revisión de este contrato lo exponga
como header adicional si el cliente necesita deduplicar del lado receptor
(fuera de alcance de esta versión).
