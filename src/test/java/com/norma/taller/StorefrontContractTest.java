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
public class StorefrontContractTest {

    private static Path tempDir;
    private static Path dbPath;
    private static Path uploadsDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws IOException {
        Path targetDir = Path.of("target").toAbsolutePath();
        Files.createDirectories(targetDir);
        tempDir = Files.createTempDirectory(targetDir, "storefront-contract-test-");
        dbPath = tempDir.resolve("contract.sqlite");
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
            Path testMediaRoot = Files.createTempDirectory(targetDir, "contract-media-root-");
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

    private static final String SESS_A = "sess_contract_test_a";
    private static final String SESS_B = "sess_contract_test_b";

    @BeforeEach
    void setUp() throws Exception {
        reset(mediaService);
        when(mediaService.image(any())).thenReturn("/assets/pieza-1.webp");
        when(mediaService.speak(any())).thenReturn(null); // Explicit null audio check per contract §8
        catalog.reset(SESS_A);
        catalog.reset(SESS_B);
    }

    @Test
    @DisplayName("C1: Recursos estáticos devuelven 200 y controles de accesibilidad existen")
    void testStaticAssetsAndAccessibilityControls() throws Exception {
        // GET / forwards to index.html with 200
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        // GET /index.html serves the full accessible storefront markup
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lang=\"es-AR\"")))
                .andExpect(content().string(containsString("id=\"piezas\"")))
                .andExpect(content().string(containsString("id=\"font\"")))
                .andExpect(content().string(containsString("id=\"listen\"")))
                .andExpect(content().string(containsString("id=\"silence\"")))
                .andExpect(content().string(containsString("id=\"status\"")))
                .andExpect(content().string(containsString("id=\"retry\"")))
                .andExpect(content().string(containsString("id=\"zoom\"")))
                .andExpect(content().string(containsString("id=\"close-zoom\"")))
                .andExpect(content().string(containsString("id=\"zoom-silence\"")))
                // Preserve simulator IDs (§7)
                .andExpect(content().string(containsString("id=\"sim\"")))
                .andExpect(content().string(containsString("id=\"open\"")))
                .andExpect(content().string(containsString("id=\"close\"")))
                .andExpect(content().string(containsString("id=\"chat\"")))
                .andExpect(content().string(containsString("id=\"message\"")))
                .andExpect(content().string(containsString("id=\"text\"")))
                .andExpect(content().string(containsString("id=\"photo\"")))
                .andExpect(content().string(containsString("id=\"record\"")))
                .andExpect(content().string(containsString("id=\"yes\"")))
                .andExpect(content().string(containsString("id=\"no\"")))
                .andExpect(content().string(containsString("id=\"undo\"")))
                .andExpect(content().string(containsString("id=\"reset\"")))
                .andExpect(content().string(containsString("id=\"bot-status\"")))
                .andExpect(content().string(containsString("id=\"reply-audio\"")));

        // GET /style.css
        mockMvc.perform(get("/style.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(":root")))
                .andExpect(content().string(containsString("22px")));

        // GET /app.js
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("speechSynthesis")));
    }

    @Test
    @DisplayName("C2: Contrato GET /api/products inicial devuelve 8 productos no vendidos con estructura requerida")
    void testInitialProductsContract() throws Exception {
        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].pieceNumber").value(1))
                .andExpect(jsonPath("$[0].title").value("Cuenco rústico"))
                .andExpect(jsonPath("$[0].price").value(15000))
                .andExpect(jsonPath("$[0].imageUrl").value("/assets/pieza-1.webp"))
                .andExpect(jsonPath("$[0].sold").value(false))
                .andExpect(jsonPath("$[0].sessionId").value(SESS_A))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[7].pieceNumber").value(8))
                .andExpect(jsonPath("$[7].sold").value(false));

        // Verificar que todos los 8 productos no están vendidos y sus pieceNumbers son ordenados
        List<Catalog.Product> products = catalog.all(SESS_A);
        assertEquals(8, products.size());
        for (int i = 0; i < 8; i++) {
            assertEquals(i + 1, products.get(i).pieceNumber());
            assertFalse(products.get(i).sold());
            assertTrue(products.get(i).price() > 0);
            assertNotNull(products.get(i).imageUrl());
        }
    }

    @Test
    @DisplayName("C3: Validación de tokens de sesión rechaza DEFAULT y tokens inválidos con HTTP 400")
    void testSessionValidationContract() throws Exception {
        // DEFAULT session token
        mockMvc.perform(get("/api/products").param("sessionId", "DEFAULT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La sesión no es válida."));

        // Demasiado corto (< 8 chars)
        mockMvc.perform(get("/api/products").param("sessionId", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La sesión no es válida."));

        // Caracteres inválidos
        mockMvc.perform(get("/api/products").param("sessionId", "invalid@token!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La sesión no es válida."));

        // SSE con DEFAULT
        mockMvc.perform(get("/api/catalog/stream").param("sessionId", "DEFAULT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La sesión no es válida."));

        // POST /api/bot/message con DEFAULT
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", "DEFAULT")
                        .param("text", "Hola"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La sesión no es válida."));

        // POST /api/reset con DEFAULT
        mockMvc.perform(post("/api/reset").param("sessionId", "DEFAULT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La sesión no es válida."));
    }

    @Test
    @DisplayName("C4: Catálogo vacío real mediante restore permanece vacío tras GET")
    void testEmptyCatalogRemainsEmpty() throws Exception {
        String emptySession = "sess_empty_catalog_test";
        catalog.ensure(emptySession);
        catalog.restore(emptySession, List.of());

        // GET devuelve []
        mockMvc.perform(get("/api/products").param("sessionId", emptySession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Segundo GET posterior sigue devolviendo [] y no dispara reset accidental
        mockMvc.perform(get("/api/products").param("sessionId", emptySession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("C5: Propuesta de precio no muta datos antes de confirmación; cancelación preserva precio original")
    void testPriceProposalAndCancellationLifecycle() throws Exception {
        // 1. Enviar propuesta de precio para pieza #1
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "A la número 1 subila a veinte mil pesos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.replyText", containsString("¿Confirmás cambiar el precio de la pieza #1 (Cuenco rústico) a $ 20.000")))
                .andExpect(jsonPath("$.replyAudioUrl").doesNotExist()); // Null speak handled gracefully

        // 2. GET /api/products comprueba que la pieza #1 NO fue modificada aún (autoridad GET)
        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pieceNumber").value(1))
                .andExpect(jsonPath("$[0].price").value(15000));

        // 3. Cancelar propuesta con "No"
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "No"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"));

        // 4. GET /api/products comprueba que el precio sigue siendo 15000
        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pieceNumber").value(1))
                .andExpect(jsonPath("$[0].price").value(15000));

        // 5. Repetir propuesta y confirmar con "Sí"
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "A la número 1 subila a veinte mil pesos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"));

        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Sí"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"));

        // 6. Ahora GET /api/products refleja el nuevo precio 20000
        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pieceNumber").value(1))
                .andExpect(jsonPath("$[0].price").value(20000));

        // 7. Sesión B no fue afectada
        mockMvc.perform(get("/api/products").param("sessionId", SESS_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pieceNumber").value(1))
                .andExpect(jsonPath("$[0].price").value(15000));
    }

    @Test
    @DisplayName("C6: Flujo de venta omite pieza, undo la repone, reset restaura catálogo canónico y aísla sesión B")
    void testSoldUndoResetAndSessionBIsolation() throws Exception {
        // 1. Proponer venta de pieza #2
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Ya vendí la pieza 2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"));

        // Antes de confirmar: pieza #2 sigue en GET
        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[1].pieceNumber").value(2));

        // 2. Confirmar venta con "Sí"
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Sí"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"));

        // Después de confirmar: GET omite la pieza #2 (quedan 7)
        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[?(@.pieceNumber == 2)]").doesNotExist());

        // Sesión B conserva sus 8 piezas intactas
        mockMvc.perform(get("/api/products").param("sessionId", SESS_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[1].pieceNumber").value(2));

        // 3. Deshacer venta ("Me equivoqué")
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Me equivoqué"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"));

        // GET posterior repone pieza #2
        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[1].pieceNumber").value(2));

        // 4. Modificar y luego reset
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "A la número 3 subila a cincuenta mil pesos"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Sí"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].price").value(50000));

        // Reset
        mockMvc.perform(post("/api/reset").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("El catálogo original ya está restaurado."));

        // Catálogo de sesión A vuelve a 8 piezas canónicas (precio pieza 3 vuelve a 12000)
        mockMvc.perform(get("/api/products").param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[2].price").value(12000));

        // Sesión B sigue intacta con sus 8 piezas
        mockMvc.perform(get("/api/products").param("sessionId", SESS_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));
    }
}
