# Quickstart: Notification Delivery Worker

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Guía de validación end-to-end. No contiene código de implementación — el
scaffold real (`pom.xml`, clases Java) se crea en `/speckit-tasks` +
`/speckit-implement`; este documento describe cómo se probará una vez exista.

## Prerrequisitos

- JDK 21
- Maven
- Docker (para Testcontainers + LocalStack — SQS, DynamoDB, Secrets Manager
  simulados)

## Validar el núcleo de dominio (sin Docker)

El núcleo (`core/**`) es Java puro y sus tests son JUnit 5 sin contenedor:

```bash
mvn test
```

Cubre, como mínimo (ver `spec-tecnico.md` §9):
- `MatchEventServiceTest`: fan-out a N suscripciones activas, aislamiento
  A01, descarte silencioso cuando no hay suscriptores.
- `DispatchDeliveryServiceTest`: clasificación transitorio/permanente/éxito,
  no reentrega si ya existe `completed`.
- `NotificationIdFactoryTest`: mismo `event_id` + `subscription_id` →
  siempre el mismo `notification_event_id`.
- `BackoffCalculatorTest`: fórmula `base = min(30*2^intento, 3600)` y tope
  de 6 intentos.
- `RetryDecisionTest`: mapeo código HTTP → transitorio/permanente.

## Validar los adaptadores (con Docker)

```bash
mvn verify
```

Levanta LocalStack vía Testcontainers y ejerce:
- `DynamoSubscriptionRepositoryIT`: `Query` con `PK=client_id`,
  `SK begins_with(event_type#)`.
- `SqsDeliveryPublisherIT`: publicación en `deliveries` y
  `ChangeMessageVisibility` para reintentos.
- `RestClientWebhookClientIT`: POST firmado, timeouts, mapeo de respuesta.

## Escenario de validación manual end-to-end

Reproduce el escenario Gherkin "Evento con dos suscripciones activas" de
`spec-funcional.md` §7, contra el worker corriendo localmente apuntando a
LocalStack:

1. Sembrar en la tabla `subscriptions` (LocalStack) dos suscripciones
   activas de `client_id=C1` para `event_type=payment.created`, cada una con
   su propio `webhook_url` (por ejemplo, dos receptores HTTP de prueba que
   respondan `200`).
2. Sembrar el secreto HMAC de `C1` en Secrets Manager (LocalStack) bajo
   `cobre/webhook-hmac/C1`.
3. Publicar en `raw-events` un mensaje:
   ```json
   {
     "event_id": "evt-123",
     "client_id": "C1",
     "event_type": "payment.created",
     "content": { "amount": 1000 },
     "delivery_date": "2026-08-22T10:00:00Z"
   }
   ```
4. **Resultado esperado**:
   - Cada uno de los dos receptores HTTP de prueba recibe exactamente una
     solicitud POST, con header `X-Cobre-Signature` válido para el secreto
     de `C1`.
   - La tabla `notification_events` tiene exactamente 2 items para
     `client_id=C1`, cada uno `delivery_status=completed`, con
     `notification_event_id` distinto y determinista.
   - La métrica `entrega_resultado{client_id=C1,event_type=payment.created,outcome=completed}`
     se incrementó en 2.
5. **Reproceso (idempotencia)**: volver a publicar el mismo mensaje del paso
   3 (simulando redelivery de SQS). Verificar que **no** llegan nuevas
   solicitudes a los receptores HTTP de prueba y que la tabla
   `notification_events` sigue teniendo exactamente 2 items (no se
   duplican).

## Escenario de fallo transitorio

1. Configurar un receptor HTTP de prueba que responda `503` en sus primeras
   2 invocaciones y `200` en la tercera.
2. Publicar un evento con una suscripción activa apuntando a ese receptor.
3. **Resultado esperado**: el mensaje en `deliveries` se reencola 2 veces
   (con `attempt_count` incrementando y una espera de visibilidad
   creciente), y el desenlace final en `notification_events` queda
   `completed` con `attempt_count=2` (o el valor correspondiente al intento
   exitoso), sin ningún registro intermedio previo.

## Escenario de fallo permanente

1. Configurar un receptor HTTP de prueba que responda `400`.
2. Publicar un evento con una suscripción activa apuntando a ese receptor.
3. **Resultado esperado**: el desenlace en `notification_events` queda
   `failed` tras el primer intento, sin reencolado en `deliveries`.
