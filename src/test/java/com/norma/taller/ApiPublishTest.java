package com.norma.taller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias deterministas para el aislamiento de fallos y concurrencia SSE (Codex v2.2).
 */
public class ApiPublishTest {

    private Api api;
    private Catalog catalog;
    private BotEngine bot;
    private MediaService media;

    @BeforeEach
    void setUp() {
        catalog = mock(Catalog.class);
        bot = mock(BotEngine.class);
        media = mock(MediaService.class);
        api = new Api(catalog, bot, media);
    }

    @Test
    @DisplayName("Un emisor roto con send() y complete() fallidos es retirado sin propagar excepción")
    void testBrokenEmitterDoesNotThrowAndIsRemoved() throws IOException {
        String sessionId = "sess_broken_1";
        SseEmitter brokenEmitter = mock(SseEmitter.class);

        // Simular fallo en send y fallo asíncrono en complete de Tomcat
        doThrow(new IOException("Broken pipe")).when(brokenEmitter).send(any(SseEmitter.SseEventBuilder.class));
        doThrow(new IllegalStateException("A non-container thread attempted to use AsyncContext after error"))
                .when(brokenEmitter).complete();

        CopyOnWriteArrayList<SseEmitter> list = new CopyOnWriteArrayList<>();
        list.add(brokenEmitter);
        api.streams.put(sessionId, list);

        // Ejecutar publish: no debe propagar excepción
        assertDoesNotThrow(() -> api.publish(sessionId), "publish no debe propagar fallo de emisor roto");

        // El emisor debe haber sido retirado y el mapa limpiado al quedar vacía la lista
        assertFalse(list.contains(brokenEmitter), "Emisor roto debe ser retirado de la lista");
        assertNull(api.streams.get(sessionId), "La clave de sesión debe ser removida al quedar vacía");
        verify(brokenEmitter, times(1)).complete();
    }

    @Test
    @DisplayName("Cuando un emisor falla, los demás emisores sanos de la misma sesión reciben la notificación")
    void testHealthyEmitterStillReceivesWhenAnotherFails() throws IOException {
        String sessionId = "sess_mixed_1";
        SseEmitter brokenEmitter = mock(SseEmitter.class);
        SseEmitter healthyEmitter = mock(SseEmitter.class);

        doThrow(new IOException("Connection reset by peer")).when(brokenEmitter).send(any(SseEmitter.SseEventBuilder.class));
        doThrow(new RuntimeException("AsyncRequestNotUsableException: Response not usable after response errors"))
                .when(brokenEmitter).complete();

        CopyOnWriteArrayList<SseEmitter> list = new CopyOnWriteArrayList<>();
        list.add(brokenEmitter);
        list.add(healthyEmitter);
        api.streams.put(sessionId, list);

        assertDoesNotThrow(() -> api.publish(sessionId));

        // El roto se retira, el sano permanece y fue notificado
        assertFalse(api.streams.get(sessionId).contains(brokenEmitter));
        assertTrue(api.streams.get(sessionId).contains(healthyEmitter));
        assertEquals(1, api.streams.get(sessionId).size());

        verify(brokenEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(healthyEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("Aislamiento entre sesiones: fallos en sesión A no afectan las notificaciones de sesión B")
    void testSessionIsolation() throws IOException {
        String sessA = "sess_A";
        String sessB = "sess_B";

        SseEmitter brokenA = mock(SseEmitter.class);
        doThrow(new IOException("Socket closed")).when(brokenA).send(any(SseEmitter.SseEventBuilder.class));
        doThrow(new IllegalStateException("AsyncContext already errored")).when(brokenA).complete();

        SseEmitter healthyB = mock(SseEmitter.class);

        CopyOnWriteArrayList<SseEmitter> listA = new CopyOnWriteArrayList<>();
        listA.add(brokenA);
        api.streams.put(sessA, listA);

        CopyOnWriteArrayList<SseEmitter> listB = new CopyOnWriteArrayList<>();
        listB.add(healthyB);
        api.streams.put(sessB, listB);

        // Publicar mutación en sesión A: no afecta B
        assertDoesNotThrow(() -> api.publish(sessA));
        assertNull(api.streams.get(sessA));
        assertNotNull(api.streams.get(sessB));
        verify(healthyB, never()).send(any(SseEmitter.SseEventBuilder.class));

        // Publicar en sesión B: healthyB recibe normalmente
        assertDoesNotThrow(() -> api.publish(sessB));
        verify(healthyB, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertTrue(api.streams.get(sessB).contains(healthyB));
    }

    @Test
    @DisplayName("Publicar en una sesión sin emisores activos es no-op y no arroja error")
    void testPublishOnNonExistentSession() {
        assertDoesNotThrow(() -> api.publish("sess_empty"));
        assertNull(api.streams.get("sess_empty"));
    }

    @Test
    @DisplayName("P2 — Regresión concurrente determinista: cleanup del último emisor vs registro nuevo nunca orfana la nueva suscripción")
    void testConcurrentCleanupVsNewRegistrationDoesNotOrphanSubscription() throws Exception {
        int rounds = 50;
        ExecutorService executor = Executors.newFixedThreadPool(4);

        try {
            for (int i = 0; i < rounds; i++) {
                String sessionId = "sess_race_p2_" + i;

                // Emisor previo ya registrado
                SseEmitter oldEmitter = api.stream(sessionId);
                assertNotNull(api.streams.get(sessionId));
                assertTrue(api.streams.get(sessionId).contains(oldEmitter));

                CountDownLatch startLatch = new CountDownLatch(1);
                CountDownLatch doneLatch = new CountDownLatch(2);
                Future<?>[] futures = new Future<?>[2];
                ConcurrentLinkedQueue<SseEmitter> newEmitterHolder = new ConcurrentLinkedQueue<>();
                ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

                // Hilo 1: cleanup del emisor viejo (ejecuta removeEmitter como en onCompletion/onError)
                futures[0] = executor.submit(() -> {
                    try {
                        startLatch.await();
                        api.removeEmitter(sessionId, oldEmitter);
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });

                // Hilo 2: registro concurrente de nuevo emisor
                futures[1] = executor.submit(() -> {
                    try {
                        startLatch.await();
                        SseEmitter newEmitter = api.stream(sessionId);
                        newEmitterHolder.add(newEmitter);
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });

                // Disparo sincronizado
                startLatch.countDown();
                assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Los hilos deben terminar en menos de 5s en ronda " + i);

                for (Future<?> f : futures) {
                    f.get(1, TimeUnit.SECONDS);
                }

                assertTrue(errors.isEmpty(), "No deben ocurrir errores en ronda " + i + ": " + errors);
                assertEquals(1, newEmitterHolder.size(), "Debe haberse registrado exactamente 1 nuevo emisor");
                SseEmitter newEmitter = newEmitterHolder.peek();

                // Verificación fundamental v2.2:
                // La nueva suscripción DEBE seguir alcanzable en streams
                var currentList = api.streams.get(sessionId);
                assertNotNull(currentList, "La lista en streams no debe haber sido eliminada para la ronda " + i);
                assertTrue(currentList.contains(newEmitter), "El nuevo emisor debe estar presente en streams para la ronda " + i);
                assertFalse(currentList.contains(oldEmitter), "El viejo emisor debe haber sido retirado");

                // Y publish debe ejecutarse sin errores alcanzando la nueva suscripción
                assertDoesNotThrow(() -> api.publish(sessionId), "Publish no debe fallar tras cleanup/registro concurrente");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Límite de 5 bajo registros concurrentes: exactamente 5 son aceptados y el resto rechazados con límite respetado")
    void testConcurrentRegistrationLimitFive() throws Exception {
        String sessionId = "sess_limit_concurrent";
        int totalAttempts = 10;
        ExecutorService executor = Executors.newFixedThreadPool(totalAttempts);

        try {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalAttempts);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger limitExceededCount = new AtomicInteger(0);
            ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

            for (int i = 0; i < totalAttempts; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        api.stream(sessionId);
                        successCount.incrementAndGet();
                    } catch (IllegalArgumentException e) {
                        if ("Ya hay varias ventanas abiertas para esta sesión.".equals(e.getMessage())) {
                            limitExceededCount.incrementAndGet();
                        } else {
                            unexpectedErrors.add(e);
                        }
                    } catch (Throwable t) {
                        unexpectedErrors.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Iniciar todos los hilos simultáneamente
            startLatch.countDown();
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Los 10 intentos deben terminar en menos de 5 segundos");

            assertTrue(unexpectedErrors.isEmpty(), "No debe haber errores inesperados: " + unexpectedErrors);
            assertEquals(5, successCount.get(), "Exactamente 5 registros deben tener éxito");
            assertEquals(5, limitExceededCount.get(), "Exactamente 5 intentos deben ser rechazados por exceso de ventanas");

            var list = api.streams.get(sessionId);
            assertNotNull(list);
            assertEquals(5, list.size(), "La lista persistida en streams debe tener exactamente 5 emisores");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Retiro idempotente de emitter: múltiples invocaciones sobre el mismo emisor no alteran la lista")
    void testRemoveEmitterIdempotence() {
        String sessionId = "sess_idem";
        SseEmitter e1 = api.stream(sessionId);
        SseEmitter e2 = api.stream(sessionId);

        assertEquals(2, api.streams.get(sessionId).size());

        // Primer retiro
        api.removeEmitter(sessionId, e1);
        assertEquals(1, api.streams.get(sessionId).size());
        assertTrue(api.streams.get(sessionId).contains(e2));

        // Segundo retiro del mismo emisor: debe ser no-op idempotente
        assertDoesNotThrow(() -> api.removeEmitter(sessionId, e1));
        assertEquals(1, api.streams.get(sessionId).size());
        assertTrue(api.streams.get(sessionId).contains(e2));

        // Retiro del segundo emisor: la clave se elimina
        api.removeEmitter(sessionId, e2);
        assertNull(api.streams.get(sessionId));

        // Retiro posterior sobre clave inexistente: no-op seguro
        assertDoesNotThrow(() -> api.removeEmitter(sessionId, e2));
        assertNull(api.streams.get(sessionId));
    }
}
