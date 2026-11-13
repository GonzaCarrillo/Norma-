package com.norma.taller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias deterministas para el aislamiento de fallos en Api.publish (Codex v2.1).
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
    @DisplayName("Control positivo: demostración del fallo del baseline si complete() no estuviera protegido")
    void testPositiveControlBaselineBehavior() {
        // Demuestra que si el bloque catch invoca e.complete() sin try-catch,
        // la excepción de Tomcat interrumpe la ejecución del hilo llamador
        SseEmitter unshieldedEmitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException("A non-container thread attempted to use AsyncContext"))
                .when(unshieldedEmitter).complete();

        assertThrows(IllegalStateException.class, () -> {
            try {
                throw new IOException("Broken pipe");
            } catch (Exception ex) {
                // Comportamiento anterior del baseline en Api.java:42
                unshieldedEmitter.complete();
            }
        }, "El comportamiento anterior sin try-catch arrojaba la excepción asíncrona hacia el llamador");
    }
}
