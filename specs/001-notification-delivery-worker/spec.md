# Feature Specification: Notification Delivery Worker

**Feature Branch**: `001-notification-delivery-worker`

**Created**: 2026-08-22

**Status**: Draft

**Input**: User description: "Genera la especificación a partir de spec-funcional.md" — worker headless que consume eventos crudos, resuelve suscripciones activas por cliente (matching + fan-out), entrega notificaciones vía webhook HTTPS firmado, reintenta fallos transitorios con backoff y persiste un único desenlace terminal por notificación.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Entrega exitosa de una notificación a un webhook (Priority: P1)

Cuando un evento de negocio ocurre para un cliente y ese cliente tiene una
suscripción activa para ese tipo de evento, el cliente debe recibir la
notificación en su webhook y el sistema debe quedar con un registro
permanente de que la entrega fue exitosa.

**Why this priority**: Es la razón de ser del sistema — sin esta entrega
básica end-to-end (evento → suscripción → webhook → desenlace) no existe
producto. Es el flujo mínimo que ya entrega valor completo a un cliente.

**Independent Test**: Publicar un evento para un cliente con una suscripción
activa cuyo webhook responde 2xx de inmediato; verificar que el webhook
recibe exactamente una solicitud firmada y que queda un único registro de
desenlace "completado" para esa combinación evento-suscripción.

**Acceptance Scenarios**:

1. **Given** un evento con `client_id=C1`, `event_type=payment.created` y una
   suscripción activa de C1 para ese tipo de evento, **When** el worker
   procesa el evento, **Then** el webhook de la suscripción recibe una
   solicitud HTTPS firmada con el contenido del evento.
2. **Given** que el webhook respondió con éxito (2xx), **When** el worker
   evalúa la respuesta, **Then** se persiste un único desenlace "completado"
   con la fecha, el número de intentos y los datos de la notificación.
3. **Given** un evento con dos suscripciones activas de C1 para el mismo
   `event_type`, **When** el worker procesa el evento, **Then** se generan y
   entregan dos notificaciones independientes, una por suscripción, cada una
   con su propio identificador determinista.

---

### User Story 2 - Reintento automático ante fallas transitorias del webhook (Priority: P2)

Cuando el webhook de un cliente falla de forma transitoria (no disponible
momentáneamente, sobrecargado, timeout), el sistema debe reintentar la
entrega automáticamente en vez de darla por perdida de inmediato, aumentando
la espera entre intentos hasta un límite razonable.

**Why this priority**: Sin reintentos, cualquier caída momentánea de un
webhook de cliente (muy común en la práctica) se traduciría en pérdida
permanente de notificaciones. Depende de que la entrega básica (US1) ya
funcione.

**Independent Test**: Configurar un webhook que responde 503 en los primeros
intentos y luego 200; verificar que el sistema reintenta con esperas
crecientes y que el desenlace final queda como "completado" solo tras el
intento exitoso, sin duplicar el registro.

**Acceptance Scenarios**:

1. **Given** una tarea de entrega y un webhook que responde 503, **When** el
   worker intenta entregar, **Then** la tarea se reencola con el contador de
   intentos incrementado y no se persiste ningún desenlace todavía.
2. **Given** una notificación que ya alcanzó 6 intentos fallidos por errores
   transitorios, **When** se agota el límite de reintentos, **Then** se
   persiste un desenlace "fallido" y no se generan más intentos.
3. **Given** un webhook que responde 429 (límite de tasa), **When** el worker
   evalúa la respuesta, **Then** se trata igual que un fallo transitorio
   (reintento con backoff).

---

### User Story 3 - Descarte de eventos sin suscriptores (Priority: P2)

Cuando llega un evento para un cliente que no tiene ninguna suscripción
activa para ese tipo de evento, el sistema debe descartarlo sin generar
ninguna entrega ni error, y dejar visibilidad de que esto ocurrió.

**Why this priority**: Evita procesamiento y alertas innecesarias, y da a
operación visibilidad temprana de configuraciones de cliente incompletas
(cliente sin suscripción para un evento que sí le llega). No bloquea el
flujo principal de entrega, por lo que puede construirse después de US1.

**Independent Test**: Publicar un evento para un cliente/tipo de evento sin
ninguna suscripción activa; verificar que no se genera ninguna tarea de
entrega y que se incrementa el indicador de "eventos sin suscriptores".

**Acceptance Scenarios**:

1. **Given** un evento de `client_id=C1` con `event_type=unknown` y ninguna
   suscripción activa de C1 para ese tipo, **When** el worker procesa el
   evento, **Then** no se encola ninguna tarea de entrega.
2. **Given** el mismo escenario, **When** el evento se descarta, **Then** se
   incrementa una métrica de "eventos sin suscriptores" identificable por
   cliente y tipo de evento.

---

### User Story 4 - Fallo permanente sin reintento (Priority: P3)

Cuando el webhook de un cliente rechaza la notificación de forma
inequívocamente permanente (por ejemplo, la solicitud está mal formada o no
autorizada), el sistema no debe insistir con reintentos que no cambiarán el
resultado, y debe dejarlo registrado como fallido de inmediato.

**Why this priority**: Ahorra recursos y evita ruido operativo por
reintentos inútiles; es un refinamiento sobre el comportamiento base de
clasificación de resultados ya cubierto por US1/US2.

**Independent Test**: Configurar un webhook que responde 400; verificar que
el sistema persiste el desenlace "fallido" en el primer intento, sin
reencolar la tarea.

**Acceptance Scenarios**:

1. **Given** una tarea de entrega y un webhook que responde 400, **When** el
   worker intenta entregar, **Then** se persiste de inmediato un desenlace
   "fallido" y no se reintenta.

---

### Edge Cases

- ¿Qué ocurre si el secreto usado para firmar las notificaciones de un
  cliente no puede resolverse (no existe o el servicio que lo provee no
  responde)? El sistema no debe entregar una notificación sin firmar.
- ¿Qué ocurre si el mismo evento crudo se reprocesa (redelivery esperada de
  la cola) después de que ya se generaron y completaron sus entregas? No debe
  producirse una segunda ronda de notificaciones ni un segundo desenlace.
- ¿Qué ocurre si dos instancias del worker procesan por error el mismo
  evento o la misma tarea de entrega al mismo tiempo? El resultado final debe
  seguir siendo un único desenlace por notificación.
- ¿Qué ocurre si el webhook de un cliente nunca responde (cuelga la
  conexión) en vez de devolver un código de error? Debe tratarse como fallo
  transitorio sujeto a reintento, no quedar esperando indefinidamente.
- ¿Qué ocurre si un cliente tiene suscripciones activas para varios tipos de
  evento pero el evento entrante no coincide con ninguno? Debe comportarse
  igual que "sin suscriptores" para ese evento específico.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST consumir eventos de negocio entrantes,
  cada uno con al menos cliente, tipo de evento, contenido e identificador
  de evento.
- **FR-002**: El sistema MUST resolver, para cada evento entrante, el
  conjunto de suscripciones activas que pertenecen exclusivamente al cliente
  del evento y coinciden con su tipo de evento.
- **FR-003**: El sistema MUST generar una tarea de entrega independiente
  por cada suscripción activa encontrada, identificada de forma determinista
  a partir del evento y la suscripción (de modo que reprocesar el mismo
  evento produzca los mismos identificadores).
- **FR-004**: El sistema MUST confirmar el evento de origen como procesado
  solo después de que todas sus tareas de entrega correspondientes hayan sido
  encoladas exitosamente.
- **FR-005**: Cuando un evento no tiene ninguna suscripción activa
  coincidente, el sistema MUST descartarlo sin generar ninguna tarea de
  entrega ni error visible, y MUST incrementar un indicador de "eventos sin
  suscriptores" identificable por cliente y tipo de evento.
- **FR-006**: El sistema MUST firmar cada notificación saliente con un
  secreto propio del cliente antes de enviarla al webhook.
- **FR-007**: El sistema MUST entregar cada notificación como una solicitud
  HTTPS al webhook configurado en la suscripción correspondiente.
- **FR-008**: El sistema MUST clasificar el resultado de cada intento de
  entrega en exactamente una de tres categorías: éxito, fallo transitorio o
  fallo permanente.
- **FR-009**: Ante un fallo transitorio (tiempo de espera agotado, error de
  servidor, o límite de tasa alcanzado), el sistema MUST reintentar la
  entrega automáticamente, incrementando el contador de intentos de esa
  notificación.
- **FR-010**: El sistema MUST espaciar los reintentos con una espera
  creciente entre cada intento fallido, y esta espera MUST ser gestionada
  externamente al proceso de entrega (no mediante una espera activa que
  bloquee el procesamiento).
- **FR-011**: El sistema MUST limitar cada notificación a un máximo de 6
  intentos de entrega; al agotar ese límite MUST registrar el desenlace como
  fallido.
- **FR-012**: Ante un fallo permanente (rechazo del webhook que no es un
  límite de tasa), el sistema MUST registrar de inmediato el desenlace como
  fallido, sin generar más intentos.
- **FR-013**: Ante una entrega exitosa, el sistema MUST registrar el
  desenlace como completado.
- **FR-014**: El sistema MUST garantizar que cada notificación tenga
  exactamente un registro de desenlace final (completado o fallido); el
  sistema MUST NOT registrar estados intermedios o de "en curso".
- **FR-015**: El sistema MUST evitar reentregar una notificación cuyo
  desenlace ya fue registrado como completado, incluso si el evento o la
  tarea de entrega que la originó se reprocesa.
- **FR-016**: El registro de desenlace MUST incluir, como mínimo: el
  identificador de la notificación, el cliente, el tipo de evento, el
  estado final, la fecha del resultado, el número de intentos realizados, el
  último error (cuando exista) y el webhook de destino.
- **FR-017**: El sistema MUST retener cada registro de desenlace durante 90
  días desde su creación, tras lo cual puede expirar automáticamente.
- **FR-018**: El sistema MUST reportar el resultado de cada entrega
  (completado/fallido) como una métrica identificable por cliente y tipo de
  evento, sin añadir otras dimensiones de segmentación.
- **FR-019**: Toda consulta de datos asociados a un cliente (suscripciones,
  secretos, desenlaces) MUST restringirse exclusivamente a los datos del
  cliente del evento en curso; el sistema MUST NOT exponer ni mezclar datos
  entre distintos clientes.

### Key Entities *(include if feature involves data)*

- **Evento de negocio**: hecho entrante que origina el proceso de
  notificación; incluye el cliente al que pertenece, su tipo, su contenido y
  un identificador propio.
- **Suscripción**: preferencia de un cliente por recibir un tipo de evento
  determinado en un webhook específico; tiene un estado activo/inactivo. Es
  gestionada fuera de este sistema.
- **Tarea de entrega**: la intención de notificar un evento concreto a una
  suscripción concreta; lleva un identificador determinista y un contador de
  intentos realizados.
- **Desenlace de notificación**: el registro final único de cómo terminó una
  tarea de entrega (completado o fallido), con su historial de intentos y
  motivo de fallo si aplica.
- **Secreto del cliente**: credencial propia de cada cliente usada para
  firmar las notificaciones que se le entregan; es única por cliente, no por
  suscripción.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los eventos con al menos una suscripción activa
  generan exactamente una entrega por cada suscripción activa, verificable
  contando notificaciones recibidas por los webhooks de prueba.
- **SC-002**: Un evento sin suscriptores queda reflejado en las métricas de
  operación en menos de 1 minuto desde su llegada, sin generar ninguna
  entrega.
- **SC-003**: Una entrega cuyo webhook responde exitosamente en el primer
  intento queda registrada como completada sin demoras perceptibles para
  operación (segundos, no minutos).
- **SC-004**: Ante fallas transitorias sostenidas de un webhook, el sistema
  reintenta hasta 6 veces de forma completamente automática antes de declarar
  la notificación como fallida, sin intervención manual.
- **SC-005**: Ningún evento o tarea reprocesada produce una segunda entrega
  HTTP ni un segundo registro de desenlace para la misma combinación
  evento-suscripción, verificable en el 100% de los casos de reproceso
  simulado.
- **SC-006**: El 100% de los registros de desenlace y suscripciones
  consultados provienen exclusivamente del mismo cliente que originó el
  evento correspondiente; ninguna prueba de aislamiento entre clientes falla.
- **SC-007**: Operación puede determinar la tasa de éxito/fallo de entregas
  por cliente y tipo de evento consultando únicamente las métricas
  publicadas, sin necesidad de inspeccionar registros crudos.

## Assumptions

- Las suscripciones (estado activo/inactivo, tipo de evento de interés,
  webhook de destino) ya existen y son administradas por un sistema externo;
  este worker solo las consulta, no las crea ni modifica.
- El secreto de firma de cada cliente ya está disponible en el gestor de
  secretos antes de que llegue su primer evento; no se cubre el
  aprovisionamiento de ese secreto.
- La fuente de eventos entrega cada evento al menos una vez y puede
  reentregar el mismo evento más de una vez (semántica at-least-once); el
  sistema debe tolerar esos duplicados en vez de asumir entrega única.
- Un fallo transitorio se limita a: tiempo de espera agotado, errores de
  servidor (5xx) y límite de tasa alcanzado (429); cualquier otro rechazo del
  webhook (4xx) se considera permanente y no se reintenta.
- El período de retención de 90 días para los registros de desenlace es
  suficiente para necesidades de auditoría y soporte, a falta de un
  requerimiento distinto.
- El calendario de espera entre reintentos (progresivamente mayor en cada
  intento, hasta 6 intentos en total) es responsabilidad de la capa de
  entrega y no altera el comportamiento observable descrito en esta
  especificación.
