package com.norma.taller;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class StorefrontSseTest {

    @LocalServerPort
    private int port;

    private static Path tempDir;
    private static Path dbPath;
    private static Path uploadsDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws IOException {
        Path targetDir = Path.of("target").toAbsolutePath();
        Files.createDirectories(targetDir);
        tempDir = Files.createTempDirectory(targetDir, "storefront-sse-test-");
        dbPath = tempDir.resolve("sse.sqlite");
        uploadsDir = tempDir.resolve("uploads");
        Files.createDirectories(uploadsDir);

        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath.toString());
        registry.add("norma.uploads", () -> uploadsDir.toString());
        registry.add("groq.api.key", () -> "");
    }

    @AfterAll
    static void cleanup() {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {}
                        });
            } catch (Exception ignored) {}
        }
    }

    @TestConfiguration
    static class StubMediaConfig {
        @Bean
        @Primary
        public MediaService mediaService() throws Exception {
            Path targetDir = Path.of("target").toAbsolutePath();
            Files.createDirectories(targetDir);
            Path testMediaRoot = Files.createTempDirectory(targetDir, "sse-media-root-");
            MediaService ms = mock(MediaService.class);
            Field rootField = MediaService.class.getDeclaredField("root");
            rootField.setAccessible(true);
            rootField.set(ms, testMediaRoot);
            when(ms.image(any())).thenReturn("/assets/pieza-1.webp");
            when(ms.speak(any())).thenReturn(null);
            return ms;
        }
    }

    @Autowired private Catalog catalog;

    public record SseEvent(String event, String data) {}

    public static class SseReader implements AutoCloseable {
        private final HttpResponse<InputStream> response;
        private final InputStream inputStream;
        private final BufferedReader reader;
        private final BlockingQueue<SseEvent> queue = new LinkedBlockingQueue<>();
        private final Thread thread;
        private volatile boolean running = true;

        public SseReader(HttpResponse<InputStream> response) {
            this.response = response;
            this.inputStream = response.body();
            this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            this.thread = new Thread(this::run, "sse-reader-" + System.currentTimeMillis());
            this.thread.setDaemon(true);
            this.thread.start();
        }

        private void run() {
            try {
                String line;
                String currentEvent = null;
                StringBuilder currentData = new StringBuilder();
                while (running && (line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        currentEvent = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        if (!currentData.isEmpty()) {
                            currentData.append("\n");
                        }
                        currentData.append(line.substring(5).trim());
                    } else if (line.trim().isEmpty()) {
                        if (currentEvent != null || !currentData.isEmpty()) {
                            queue.offer(new SseEvent(currentEvent != null ? currentEvent : "message", currentData.toString()));
                            currentEvent = null;
                            currentData.setLength(0);
                        }
                    }
                }
            } catch (IOException ignored) {
                // Stream closed
            }
        }

        public SseEvent nextEvent(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }

        public boolean hasNoEvent(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit) == null;
        }

        @Override
        public void close() {
            running = false;
            try {
                inputStream.close();
            } catch (IOException ignored) {}
            try {
                reader.close();
            } catch (IOException ignored) {}
            thread.interrupt();
        }
    }

    @Test
    @DisplayName("S1: Secuencia completa de transporte SSE real con dos sesiones, barrera de pasos y reconexión snapshot (§8)")
    void testCompleteSseTransportLifecycle() throws Exception {
        String baseUrl = "http://localhost:" + port;
        String sessionA = "sess_sse_test_a";
        String sessionB = "sess_sse_test_b";

        catalog.reset(sessionA);
        catalog.reset(sessionB);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        SseReader readerA = null;
        SseReader readerB = null;
        SseReader readerA2 = null;

        try {
            // 1. Abrir stream A y B, verificar status 200, Content-Type text/event-stream y evento inicial catalog-update / conectado
            HttpRequest reqA = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/catalog/stream?sessionId=" + sessionA))
                    .header("Accept", "text/event-stream")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<InputStream> respA = httpClient.send(reqA, HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, respA.statusCode());
            assertTrue(respA.headers().firstValue("content-type").orElse("").contains("text/event-stream"));
            readerA = new SseReader(respA);

            HttpRequest reqB = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/catalog/stream?sessionId=" + sessionB))
                    .header("Accept", "text/event-stream")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<InputStream> respB = httpClient.send(reqB, HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, respB.statusCode());
            assertTrue(respB.headers().firstValue("content-type").orElse("").contains("text/event-stream"));
            readerB = new SseReader(respB);

            // Verificar evento inicial conectado en ambos
            SseEvent initA = readerA.nextEvent(5, TimeUnit.SECONDS);
            assertNotNull(initA, "Stream A debe recibir evento inicial conectado dentro de 5s");
            assertEquals("catalog-update", initA.event());
            assertEquals("conectado", initA.data());

            SseEvent initB = readerB.nextEvent(5, TimeUnit.SECONDS);
            assertNotNull(initB, "Stream B debe recibir evento inicial conectado dentro de 5s");
            assertEquals("catalog-update", initB.event());
            assertEquals("conectado", initB.data());

            // GET inicial A y B registrados
            List<Catalog.Product> productsA = restClient.get()
                    .uri("/api/products?sessionId={s}", sessionA)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            assertNotNull(productsA);
            assertEquals(8, productsA.size());
            assertEquals(15000, productsA.get(0).price());

            List<Catalog.Product> productsB = restClient.get()
                    .uri("/api/products?sessionId={s}", sessionB)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            assertNotNull(productsB);
            assertEquals(8, productsB.size());
            assertEquals(15000, productsB.get(0).price());

            // 2. POST propuesta de precio por multipart a A: consumir evento actualizado, GET mantiene precio previo y Reply pide confirmar
            MultiValueMap<String, Object> msg1 = new LinkedMultiValueMap<>();
            msg1.add("sessionId", sessionA);
            msg1.add("text", "A la número 1 subila a veinte mil pesos");

            BotEngine.Reply reply1 = restClient.post()
                    .uri("/api/bot/message")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(msg1)
                    .retrieve()
                    .body(BotEngine.Reply.class);
            assertNotNull(reply1);
            assertEquals("AWAITING_CONFIRMATION", reply1.state());

            SseEvent eventA1 = readerA.nextEvent(5, TimeUnit.SECONDS);
            assertNotNull(eventA1, "Stream A debe recibir evento actualizado tras mensaje");
            assertEquals("catalog-update", eventA1.event());
            assertEquals("actualizado", eventA1.data());

            // GET A mantiene precio previo 15000
            productsA = restClient.get()
                    .uri("/api/products?sessionId={s}", sessionA)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            assertEquals(15000, productsA.get(0).price());

            // Aislamiento: B no recibe eventos durante ventana de 750ms
            assertTrue(readerB.hasNoEvent(750, TimeUnit.MILLISECONDS), "Stream B no debe recibir eventos de mutación de A");

            // 3. POST No: nuevo evento y GET sin cambio
            MultiValueMap<String, Object> msgNo = new LinkedMultiValueMap<>();
            msgNo.add("sessionId", sessionA);
            msgNo.add("text", "No");

            BotEngine.Reply replyNo = restClient.post()
                    .uri("/api/bot/message")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(msgNo)
                    .retrieve()
                    .body(BotEngine.Reply.class);
            assertEquals("IDLE", replyNo.state());

            SseEvent eventANo = readerA.nextEvent(5, TimeUnit.SECONDS);
            assertNotNull(eventANo);
            assertEquals("actualizado", eventANo.data());

            productsA = restClient.get()
                    .uri("/api/products?sessionId={s}", sessionA)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            assertEquals(15000, productsA.get(0).price());
            assertTrue(readerB.hasNoEvent(750, TimeUnit.MILLISECONDS));

            // 4. Repetir propuesta, consumir evento, POST Sí: esperar evento posterior y GET A con nuevo precio; B sin cambio
            restClient.post()
                    .uri("/api/bot/message")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(msg1)
                    .retrieve()
                    .toBodilessEntity();
            SseEvent eventProp = readerA.nextEvent(5, TimeUnit.SECONDS);
            assertNotNull(eventProp);
            assertEquals("actualizado", eventProp.data());

            MultiValueMap<String, Object> msgYes = new LinkedMultiValueMap<>();
            msgYes.add("sessionId", sessionA);
            msgYes.add("text", "Sí");

            BotEngine.Reply replyYes = restClient.post()
                    .uri("/api/bot/message")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(msgYes)
                    .retrieve()
                    .body(BotEngine.Reply.class);
            assertEquals("IDLE", replyYes.state());

            SseEvent eventYes = readerA.nextEvent(5, TimeUnit.SECONDS);
            assertNotNull(eventYes);
            assertEquals("actualizado", eventYes.data());

            // GET A con nuevo precio 20000
            productsA = restClient.get()
                    .uri("/api/products?sessionId={s}", sessionA)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            assertEquals(20000, productsA.get(0).price());

            // B sin cambio en datos ni eventos
            productsB = restClient.get()
                    .uri("/api/products?sessionId={s}", sessionB)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            assertEquals(15000, productsB.get(0).price());
            assertTrue(readerB.hasNoEvent(750, TimeUnit.MILLISECONDS));

            // 5. Venta confirmada A: GET posterior al evento omite pieza
            MultiValueMap<String, Object> msgSold = new LinkedMultiValueMap<>();
            msgSold.add("sessionId", sessionA);
            msgSold.add("text", "Ya vendí la pieza 2");
            restClient.post()
                    .uri("/api/bot/message")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(msgSold)
                    .retrieve()
                    .toBodilessEntity();
            SseEvent eventSoldDraft = readerA.nextEvent(5, TimeUnit.SECONDS);
            assertNotNull(eventSoldDraft);

            restClient.post()
                    .uri("/api/bot/message")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(msgYes)
                    .retrieve()
                    .toBodilessEntity();
            SseEvent eventSoldConfirmed = readerA.nextEvent(5, TimeUnit.SECONDS);
            assertNotNull(eventSoldConfirmed);

            productsA = restClient.get()
                    .uri("/api/products?sessionId={s}", sessionA)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            assertEquals(7, productsA.size());
            assertTrue(productsA.stream().noneMatch(p -> p.pieceNumber() == 2));
            assertTrue(readerB.hasNoEvent(750, TimeUnit.MILLISECONDS));

            // 6. Undo: GET posterior la repone
            MultiValueMap<String, Object> msgUndo = new LinkedMultiValueMap<>();
            msgUndo.add("sessionId", sessionA);
            msgUndo.add("text", "Me equivoqué");
            restClient.post()
                    .uri("/api/bot/message")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(msgUndo)
                    .retrieve()
                    .toBodilessEntity();
            SseEvent eventUndo = readerA.nextEvent(5, TimeUnit.SECONDS);
            assertNotNull(eventUndo);

            productsA = restClient.get()
                    .uri("/api/products?sessionId={s}", sessionA)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            assertEquals(8, productsA.size());
            assertTrue(productsA.stream().anyMatch(p -> p.pieceNumber() == 2));
            assertTrue(readerB.hasNoEvent(750, TimeUnit.MILLISECONDS));

            // 7. Reset: GET posterior devuelve 8 canónicas; B continúa intacta
            restClient.post()
                    .uri("/api/reset?sessionId={s}", sessionA)
                    .retrieve()
                    .toBodilessEntity();
            SseEvent eventReset = readerA.nextEvent(5, TimeUnit.SECONDS);
            assertNotNull(eventReset);
            assertEquals("actualizado", eventReset.data());

            productsA = restClient.get()
                    .uri("/api/products?sessionId={s}", sessionA)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            assertEquals(8, productsA.size());
            assertEquals(15000, productsA.get(0).price()); // Canónico restablecido

            // Control positivo en B: mutar B y comprobar que B sí recibe su evento y A no
            restClient.post()
                    .uri("/api/reset?sessionId={s}", sessionB)
                    .retrieve()
                    .toBodilessEntity();
            SseEvent eventResetB = readerB.nextEvent(5, TimeUnit.SECONDS);
            assertNotNull(eventResetB, "Stream B debe recibir su propio evento de control positivo");
            assertEquals("actualizado", eventResetB.data());
            assertTrue(readerA.hasNoEvent(750, TimeUnit.MILLISECONDS), "Stream A no debe recibir eventos de B");

            // 8. Cerrar conexión A, mutar A mediante API mientras está desconectada, reabrir y recibir conectado; GET refleja cambio perdido
            readerA.close();
            readerA = null;

            // Mutar A desconectada: subir precio pieza 1 a 35000 y confirmar
            MultiValueMap<String, Object> msgOfflineProp = new LinkedMultiValueMap<>();
            msgOfflineProp.add("sessionId", sessionA);
            msgOfflineProp.add("text", "A la número 1 subila a treinta y cinco mil pesos");
            var respProp = restClient.post()
                    .uri("/api/bot/message")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(msgOfflineProp)
                    .retrieve()
                    .toBodilessEntity();
            assertEquals(HttpStatus.OK, respProp.getStatusCode());

            var respYes = restClient.post()
                    .uri("/api/bot/message")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(msgYes)
                    .retrieve()
                    .toBodilessEntity();
            assertEquals(HttpStatus.OK, respYes.getStatusCode());

            // Reabrir stream A (reconexión)
            HttpRequest reqA2 = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/catalog/stream?sessionId=" + sessionA))
                    .header("Accept", "text/event-stream")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<InputStream> respA2 = httpClient.send(reqA2, HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, respA2.statusCode());
            readerA2 = new SseReader(respA2);

            SseEvent reconnectEvent = readerA2.nextEvent(5, TimeUnit.SECONDS);
            assertNotNull(reconnectEvent, "Reconexión de stream A debe recibir evento inicial conectado");
            assertEquals("catalog-update", reconnectEvent.event());
            assertEquals("conectado", reconnectEvent.data());

            // GET posterior recupera el cambio perdido durante desconexión
            productsA = restClient.get()
                    .uri("/api/products?sessionId={s}", sessionA)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            assertEquals(35000, productsA.get(0).price(), "Snapshot GET tras reconexión debe reflejar mutación offline");

        } finally {
            if (readerA != null) readerA.close();
            if (readerB != null) readerB.close();
            if (readerA2 != null) readerA2.close();
        }
    }
}
