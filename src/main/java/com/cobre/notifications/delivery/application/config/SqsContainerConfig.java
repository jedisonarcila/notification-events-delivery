package com.cobre.notifications.delivery.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ejecución de las entregas sobre virtual threads.
 *
 * <p><b>Por qué hace falta</b>: Spring Cloud AWS 3.1 no adopta los virtual
 * threads de {@code spring.threads.virtual.enabled}. El contenedor crea su
 * propio {@code ThreadPoolTaskExecutor} con {@code MessageExecutionThreadFactory}
 * y rechaza cualquier executor externo cuyos hilos no sean
 * {@code MessageExecutionThread} — que extiende {@code Thread}, luego nunca
 * puede ser virtual:
 *
 * <pre>
 * private void verifyThreadType() {
 *     if (!MessageExecutionThread.class.isAssignableFrom(Thread.currentThread().getClass())) {
 *         throw new UnsupportedThreadFactoryException(...);
 *     }
 * }
 * </pre>
 *
 * <p>La vía soportada es otra: cuando el método anotado con {@code @SqsListener}
 * devuelve un {@code CompletionStage}, {@code AbstractEndpoint#setupContainer}
 * lo registra como <i>async message listener</i> y no lo envuelve en el
 * adaptador bloqueante — el threading pasa a ser responsabilidad nuestra. Los
 * listeners devuelven {@code CompletableFuture} y despachan a este executor;
 * el hilo de plataforma del contenedor queda libre en cuanto encola la tarea.</p>
 *
 * <p><b>La concurrencia NO se configura aquí</b>: el contenedor autoconfigurado
 * ya la lee de {@code spring.cloud.aws.sqs.listener.max-concurrent-messages}.
 * Sustituir el bean {@code defaultSqsListenerContainerFactory} a mano costaría
 * perder lo que la autoconfiguración cablea por nosotros — el {@code ObjectMapper}
 * de Boot (necesario para los {@code Instant} de los mensajes), los error
 * handlers y los interceptores.</p>
 *
 * <p>El núcleo hexagonal no se entera: {@code CompletableFuture} vive
 * únicamente en el adaptador de entrada, nunca cruza un puerto.</p>
 */
@Configuration
public class SqsContainerConfig {

    /**
     * Un virtual thread por entrega. Sin pool: los virtual threads no se
     * reutilizan, se crean y descartan. La cota real de concurrencia no la
     * pone este executor sino el {@code SemaphoreBackPressureHandler} del
     * contenedor.
     *
     * <p>{@code destroyMethod = "close"} da drenaje ordenado en el apagado:
     * {@code ExecutorService#close()} (Java 19+) espera a que terminen las
     * entregas en vuelo. Spring detiene los contenedores SQS (SmartLifecycle)
     * antes de destruir este bean, así que no entran tareas nuevas mientras
     * drena.</p>
     */
    @Bean(destroyMethod = "close")
    public ExecutorService sqsVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
