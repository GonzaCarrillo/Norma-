package com.norma.taller;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class SimulatorContractTest {

    private static Path tempDir;
    private static Path dbPath;
    private static Path uploadsDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws IOException {
        Path targetDir = Path.of("target").toAbsolutePath();
        Files.createDirectories(targetDir);
        tempDir = Files.createTempDirectory(targetDir, "sim-contract-test-");
        dbPath = tempDir.resolve("sim_contract.sqlite");
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
            Path testMediaRoot = Files.createTempDirectory(targetDir, "sim-media-root-");
            MediaService ms = mock(MediaService.class);
            Field rootField = MediaService.class.getDeclaredField("root");
            rootField.setAccessible(true);
            rootField.set(ms, testMediaRoot);
            return ms;
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private Catalog catalog;
    @Autowired private MediaService mediaService;

    private static final String SESS_A = "sess_sim_test_alpha";
    private static final String SESS_B = "sess_sim_test_beta";

    @BeforeEach
    void setUp() throws Exception {
        reset(mediaService);
        when(mediaService.image(any())).thenReturn("/assets/pieza-1.webp");
        when(mediaService.speak(any())).thenReturn(null); // Verificación explícita de degradación de audio
        catalog.reset(SESS_A);
        catalog.reset(SESS_B);
    }

    @Test
    @DisplayName("C1: Recursos estáticos del simulador existen y no exponen credenciales")
    void testSimulatorStaticAssetsAndElements() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"sim\"")))
                .andExpect(content().string(containsString("id=\"open\"")))
                .andExpect(content().string(containsString("id=\"close\"")))
                .andExpect(content().string(containsString("data-demo=\"create\"")))
                .andExpect(content().string(containsString("data-demo=\"price\"")))
                .andExpect(content().string(containsString("data-demo=\"image\"")))
                .andExpect(content().string(containsString("data-demo=\"sold\"")))
                .andExpect(content().string(containsString("id=\"yes\"")))
                .andExpect(content().string(containsString("id=\"no\"")))
                .andExpect(content().string(containsString("id=\"undo\"")))
                .andExpect(content().string(containsString("id=\"reset\"")))
                .andExpect(content().string(not(containsString("AI_KEY"))))
                .andExpect(content().string(not(containsString("WHATSAPP_TOKEN"))));

        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/bot/message")))
                .andExpect(content().string(containsString("/api/reset")))
                .andExpect(content().string(containsString("/api/products")));

        mockMvc.perform(get("/style.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("#sim")))
                .andExpect(content().string(containsString(".bubble")));
    }

    @Test
    @DisplayName("C3.1: Demo Create - Propuesta sin mutación, cancelar preserva, confirmar agrega y undo revierte")
    void testDemoCreateLifecycle() throws Exception {
        assertEquals(8, catalog.all(SESS_A).size());

        MockMultipartFile image = new MockMultipartFile("image", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3});

        // 1. Enviar propuesta de creación
        mockMvc.perform(multipart("/api/bot/message")
                        .file(image)
                        .param("sessionId", SESS_A)
                        .param("text", "Cuenco del patio a quince mil pesos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.replyText", containsString("15.000")));

        // Catálogo sigue en 8 (sin mutación en propuesta)
        assertEquals(8, catalog.all(SESS_A).size());

        // 2. Cancelar
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "No"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"))
                .andExpect(jsonPath("$.replyText", containsString("Cancelé el cambio")));

        assertEquals(8, catalog.all(SESS_A).size());

        // 3. Proponer nuevamente y confirmar
        mockMvc.perform(multipart("/api/bot/message")
                        .file(image)
                        .param("sessionId", SESS_A)
                        .param("text", "Cuenco del patio a quince mil pesos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"));

        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Sí"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"))
                .andExpect(jsonPath("$.replyText", containsString("actualizado en la página")));

        // Ahora hay 9 piezas
        List<Catalog.Product> prodsAfterAdd = catalog.all(SESS_A);
        assertEquals(9, prodsAfterAdd.size());

        // 4. Deshacer
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Me equivoqué"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyText", containsString("ya dejé todo como estaba antes")));

        // Vuelve a 8 piezas
        assertEquals(8, catalog.all(SESS_A).size());
    }

    @Test
    @DisplayName("C3.2: Demo Price - Propuesta sin mutación, confirmar actualiza precio y undo restaura original")
    void testDemoPriceLifecycle() throws Exception {
        int originalPrice = catalog.all(SESS_A).stream().filter(p -> p.pieceNumber() == 2).findFirst().orElseThrow().price();
        assertEquals(18500, originalPrice);

        // 1. Proponer precio
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "A la número 2 subila a veinte mil pesos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.replyText", containsString("20.000")));

        // Precio no muta aún
        assertEquals(18500, catalog.all(SESS_A).stream().filter(p -> p.pieceNumber() == 2).findFirst().orElseThrow().price());

        // 2. Confirmar
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Sí"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"))
                .andExpect(jsonPath("$.replyText", containsString("actualizado en la página")));

        assertEquals(20000, catalog.all(SESS_A).stream().filter(p -> p.pieceNumber() == 2).findFirst().orElseThrow().price());

        // 3. Undo
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Me equivoqué"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyText", containsString("ya dejé todo como estaba antes")));

        assertEquals(18500, catalog.all(SESS_A).stream().filter(p -> p.pieceNumber() == 2).findFirst().orElseThrow().price());
    }

    @Test
    @DisplayName("C3.3: Demo Image - Propuesta con foto multipart, confirmación actualiza imagen y undo revierte")
    void testDemoImageLifecycle() throws Exception {
        String originalImg = catalog.all(SESS_A).stream().filter(p -> p.pieceNumber() == 2).findFirst().orElseThrow().imageUrl();
        when(mediaService.image(any())).thenReturn("/media/nueva-foto-2.webp");

        MockMultipartFile image = new MockMultipartFile("image", "nueva.jpg", "image/jpeg", new byte[]{4, 5, 6});

        // 1. Proponer cambio de foto
        mockMvc.perform(multipart("/api/bot/message")
                        .file(image)
                        .param("sessionId", SESS_A)
                        .param("text", "Esta foto es para cambiar la de la pieza 2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"));

        // Foto no cambia antes de confirmar
        assertEquals(originalImg, catalog.all(SESS_A).stream().filter(p -> p.pieceNumber() == 2).findFirst().orElseThrow().imageUrl());

        // 2. Confirmar
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Sí"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"));

        assertEquals("/media/nueva-foto-2.webp", catalog.all(SESS_A).stream().filter(p -> p.pieceNumber() == 2).findFirst().orElseThrow().imageUrl());

        // 3. Undo
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Me equivoqué"))
                .andExpect(status().isOk());

        assertEquals(originalImg, catalog.all(SESS_A).stream().filter(p -> p.pieceNumber() == 2).findFirst().orElseThrow().imageUrl());
    }

    @Test
    @DisplayName("C3.4: Demo Sold - Propuesta marca vendida, confirmación oculta de activos y undo recupera pieza")
    void testDemoSoldLifecycle() throws Exception {
        // En activos hay 8
        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));

        // 1. Proponer venta de pieza 2
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Ya vendí la pieza 2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"));

        // Sigue apareciendo en activos
        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));

        // 2. Confirmar venta
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Sí"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"));

        // Activos ahora son 7
        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[*].pieceNumber", not(hasItem(2))));

        // 3. Undo
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Me equivoqué"))
                .andExpect(status().isOk());

        // Activos vuelven a 8
        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[*].pieceNumber", hasItem(2)));
    }

    @Test
    @DisplayName("C6: Reset restaura 8 piezas originales en Sesión A sin afectar en absoluto a Sesión B")
    void testResetIsolationBetweenSessions() throws Exception {
        // Vender pieza 2 en Sesión A
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Ya vendí la pieza 2"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Sí"))
                .andExpect(status().isOk());

        // Sesión A tiene 7 activos, Sesión B tiene 8
        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)));
        mockMvc.perform(get("/api/products").param("sessionId", SESS_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));

        // Reset solo en Sesión A
        mockMvc.perform(post("/api/reset").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("restaurado")));

        // Sesión A vuelve a 8
        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));

        // Sesión B permanece intacta con 8
        mockMvc.perform(get("/api/products").param("sessionId", SESS_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));
    }

    @Test
    @DisplayName("C7.1: Error 400 en foto inválida no muta el catálogo")
    void testInvalidPhotoDoesNotMutateCatalog() throws Exception {
        when(mediaService.image(any())).thenThrow(new IllegalArgumentException("El formato de la imagen no es válido."));

        MockMultipartFile badImage = new MockMultipartFile("image", "corrupt.txt", "text/plain", "no-image".getBytes());

        mockMvc.perform(multipart("/api/bot/message")
                        .file(badImage)
                        .param("sessionId", SESS_A)
                        .param("text", "Cuenco del patio a quince mil pesos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("no es válido")));

        // Catálogo permanece intacto
        assertEquals(8, catalog.all(SESS_A).size());
    }

    @Test
    @DisplayName("C7.2: SessionId ilegal rechaza con 400 sin crear estado")
    void testIllegalSessionId() throws Exception {
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", "DEFAULT")
                        .param("text", "Hola"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", "short")
                        .param("text", "Hola"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("C7.3: Degradación de voz mantiene respuesta textual intacta")
    void testVoiceAbsenceKeepsText() throws Exception {
        when(mediaService.speak(any())).thenReturn(null);

        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "A la número 2 subila a veinte mil pesos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyAudioUrl").doesNotExist())
                .andExpect(jsonPath("$.replyText", not(emptyOrNullString())));
    }
}
