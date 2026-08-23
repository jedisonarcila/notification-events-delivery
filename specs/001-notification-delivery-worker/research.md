# Research: Notification Delivery Worker

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Todas las decisiones técnicas de esta feature ya estaban cerradas en
`spec-tecnico.md` (repo root) antes de esta fase, por lo que no quedó ningún
`NEEDS CLARIFICATION` en el Technical Context de `plan.md`. Este documento
existe para dejar registrado el racional de cada decisión no trivial, de modo
que futuras revisiones no necesiten releer `spec-tecnico.md` para entender el
"por qué".

## 1. Cliente HTTP saliente: `RestClient` en vez de WebClient/OpenFeign

- **Decision**: usar `RestClient` (Spring 6) para el POST al webhook del
  cliente.
- **Rationale**: la URL de destino es dinámica (una por suscripción/cliente,
  resuelta en tiempo de ejecución desde datos, no configuración estática).
  `RestClient` es síncrono y con virtual threads no hay penalidad de
  bloqueo de hilo real. OpenFeign está pensado para clientes declarativos con
  URL conocida en tiempo de compilación; WebClient añade el modelo reactivo
  sin necesidad real aquí (no hay backpressure entre el POST y su respuesta).
- **Alternatives considered**:
  - *WebClient*: descartado — complejidad reactiva innecesaria cuando virtual
    threads ya resuelven la concurrencia de forma síncrona y simple.
  - *OpenFeign*: descartado — su modelo de cliente declarativo por interfaz
    no encaja con una URL resuelta por fila de datos en tiempo de ejecución.

## 2. Persistencia: DynamoDB con `client_id` como partition key

- **Decision**: `DynamoDbTemplate` sobre dos tablas (`subscriptions`,
  `notification_events`), ambas con `client_id` como PK.
- **Rationale**: el aislamiento A01 (constitución, principio VI) exige que
  toda consulta de datos de cliente esté acotada por `client_id`; usarlo como
  partition key lo hace estructuralmente imposible de saltarse (no hay query
  sin PK en DynamoDB). Además encaja con el patrón de acceso dominante:
  siempre se consulta "las suscripciones/desenlaces de este cliente".
- **Alternatives considered**:
  - *Tabla única multi-entidad con GSI global*: descartado por mayor
    complejidad de modelado sin beneficio claro dado el volumen esperado
    (~2.000 ev/s) y el patrón de acceso siempre acotado por cliente.
  - *Base relacional (RDS/Aurora)*: descartado — el equipo ya opera DynamoDB
    para este dominio (Spring Cloud AWS) y el acceso es puramente por clave,
    sin necesidad de joins ni transacciones multi-fila.

## 3. Reintentos: backoff exponencial + *equal jitter* vía `ChangeMessageVisibility`

- **Decision**: `base = min(30 * 2^intento, 3600)`;
  `espera = base/2 + random(0, base/2)` segundos, aplicados con
  `ChangeMessageVisibility` sobre el mensaje en `deliveries`. Máximo 6
  intentos.
- **Rationale**: delegar la espera en SQS (constitución, principio V) evita
  bloquear hilos y sobrevive a caídas/reinicios del worker — un
  `Thread.sleep` perdería el reintento si el proceso muere a mitad de la
  espera. *Equal jitter* evita que reintentos sincronizados de muchos
  mensajes golpeen el mismo webhook al mismo tiempo (efecto manada).
- **Alternatives considered**:
  - *Cola de retraso dedicada (delay queue) por intento*: descartado —
    añade una cola más que gestionar sin ganar nada sobre
    `ChangeMessageVisibility`, que ya cumple la misma función sobre el
    mismo mensaje.
  - *Backoff fijo sin jitter*: descartado — bajo carga, sincroniza
    reintentos y puede saturar un webhook justo cuando se está recuperando.

## 4. Idempotencia: hash determinista en vez de deduplicación nativa de SQS

- **Decision**: `notification_event_id = hash(event_id + subscription_id)`,
  calculado en el dominio (`NotificationIdFactory`), y usado como clave para
  no reentregar si ya existe un desenlace `completed`.
- **Rationale**: SQS estándar (no FIFO) no ofrece deduplicación nativa, y
  aunque la ofreciera, la ventana de deduplicación de SQS FIFO es de minutos,
  no de la vida útil de una notificación (hasta 6 intentos con backoff que
  puede extenderse por más de una hora). Un identificador determinista
  calculado en el dominio funciona sin importar cuánto tiempo pase entre
  reintentos o reprocesos.
- **Alternatives considered**:
  - *SQS FIFO + `MessageDeduplicationId`*: descartado — resuelve
    duplicados solo dentro de una ventana corta y ata el diseño a colas FIFO
    (menor throughput que SQS estándar, innecesario aquí).
  - *UUID aleatorio + tabla de "ya visto"*: descartado — requeriría una
    consulta adicional de deduplicación separada del propio registro de
    desenlace; el hash determinista colapsa ambas cosas en una sola clave.

## 5. Resiliencia adicional: circuit breaker por host de webhook (opcional)

- **Decision**: Resilience4j circuit breaker, con alcance por host de
  webhook, como protección adicional — no reemplaza el mecanismo de
  reintento de 6 intentos.
- **Rationale**: si el webhook de un cliente está caído de forma prolongada,
  seguir intentando (aunque sea solo hasta el límite de 6) por cada mensaje
  nuevo que llegue desperdicia recursos y latencia. El circuit breaker corta
  rápido esos intentos sin cambiar la semántica de negocio (clasificación
  transitorio/permanente y el tope de 6 reintentos siguen siendo el
  mecanismo que decide el desenlace final).
- **Alternatives considered**:
  - *Sin circuit breaker*: descartado como configuración por defecto — bajo
    una caída prolongada de un webhook, cada mensaje entrante pagaría el
    timeout completo antes de fallar.
  - *Circuit breaker global (no por host)*: descartado — un webhook caído de
    un cliente no debe afectar la entrega a otros clientes.
