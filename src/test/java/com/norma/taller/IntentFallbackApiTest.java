package com.norma.taller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "groq.api.key=",
    "norma.uploads=target/test-uploads-fallback-api",
    "spring.datasource.url=jdbc:sqlite::memory:"
})
@AutoConfigureMockMvc
@DisplayName("IntentFallbackApiTest — Pruebas de integración HTTP MockMvc para H1 en Api")
class IntentFallbackApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Catalog catalog;

    @MockitoSpyBean
    private IntentFallback fallback;

    private static final String SESS_A = "sessFallbackApiA";
    private static final String SESS_B = "sessFallbackApiB";

    private static byte[] jpegBytes() throws IOException {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 100, 100);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    @BeforeEach
    void resetSessions() {
        catalog.ensure(SESS_A);
        catalog.reset(SESS_A);
        catalog.ensure(SESS_B);
        catalog.reset(SESS_B);
        reset(fallback);
    }

    @Test
    @DisplayName("Texto no reconocido con candidato válido activa fallback y pasa a AWAITING_CONFIRMATION sin mutar catálogo hasta 'si'")
    void flujoCompletoFallbackConConfirmacion() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "mate.jpg", "image/jpeg", jpegBytes());
        String originalColoquial = "Hola Norma, acá te mando la foto de la nueva artesanía de madera de caldén que estuvimos tallando en el taller, fijate que el valor sería dieciocho mil pesos";

        AtomicBoolean wasTransactionActive = new AtomicBoolean(true);

        doAnswer(invocation -> {
            // Verificar explícitamente que la invocación ocurre fuera de cualquier transacción de base de datos
            wasTransactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of("Publicá Artesanía de caldén a 18000 pesos");
        }).when(fallback).normalize(any(), any(), any());

        // 1. Mensaje inicial con foto y texto coloquial desconocido para el parser determinista
        mockMvc.perform(multipart("/api/bot/message")
                        .file(image)
                        .param("sessionId", SESS_A)
                        .param("text", originalColoquial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", equalTo("AWAITING_CONFIRMATION")))
                .andExpect(jsonPath("$.replyText", containsString("Artesanía de caldén")))
                .andExpect(jsonPath("$.replyText", containsString("18.000")));

        // Verificar que el proveedor fue invocado fuera de transacción activa
        assertFalse(wasTransactionActive.get(), "La invocación a IntentFallback debe ejecutarse fuera de @Transactional");
        verify(fallback, times(1)).normalize(eq(originalColoquial), any(), any());

        // Verificar que el catálogo NO se modificó aún (permanecen los 8 productos de la semilla)
        assertEquals(8, catalog.all(SESS_A).size(), "El catálogo no debe mutar antes de la confirmación explícita");

        // 2. Enviar confirmación "si"
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "si"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", equalTo("IDLE")))
                .andExpect(jsonPath("$.replyText", containsString("Ya quedó actualizado")));

        // Verificar que 'si' NO vuelve a invocar a IntentFallback
        verify(fallback, times(1)).normalize(any(), any(), any());

        // Verificar que tras 'si', el producto fue efectivamente agregado (9 productos)
        assertEquals(9, catalog.all(SESS_A).size(), "El catálogo debe mutar solo tras confirmación explícita");
    }

    @Test
    @DisplayName("Comandos deterministas nunca consultan a IntentFallback")
    void comandosDeterministasNoInvocanFallback() throws Exception {
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Ya vendí la pieza 1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", equalTo("AWAITING_CONFIRMATION")));

        verify(fallback, never()).normalize(any(), any(), any());
    }

    @Test
    @DisplayName("Señales de control (si, no, deshacer) nunca consultan a IntentFallback")
    void controlesNoInvocanFallback() throws Exception {
        for (String ctrl : new String[]{"no", "deshacer", "si"}) {
            mockMvc.perform(multipart("/api/bot/message")
                            .param("sessionId", SESS_A)
                            .param("text", ctrl))
                    .andExpect(status().isOk());
        }

        verify(fallback, never()).normalize(any(), any(), any());
    }

    @Test
    @DisplayName("Cuando la sesión no está en IDLE (ej: AWAITING_DETAILS), no se consulta al LLM")
    void estadoNoIdleNoInvocaFallback() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "cuenco.jpg", "image/jpeg", jpegBytes());

        // Enviar foto sola: pasa a AWAITING_DETAILS
        mockMvc.perform(multipart("/api/bot/message")
                        .file(image)
                        .param("sessionId", SESS_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", equalTo("AWAITING_DETAILS")));

        verify(fallback, never()).normalize(any(), any(), any());

        // Enviar texto para completar
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "Cuenco de barro a 5000 pesos"))
                .andExpect(status().isOk());

        // Sigue sin invocar fallback porque el estado previo era AWAITING_DETAILS (pending != null)
        verify(fallback, never()).normalize(any(), any(), any());
    }

    @Test
    @DisplayName("Si fallback retorna empty (fallo red, error o sin intención), conserva flujo nativo sin error HTTP 500")
    void degradacionSeguraCuandoFallbackRetornaEmpty() throws Exception {
        doReturn(Optional.empty()).when(fallback).normalize(any(), any(), any());

        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", "frase totalmente ininteligible xyz 123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", equalTo("IDLE")))
                .andExpect(jsonPath("$.replyText", containsString("Decime qué pieza querés cambiar")));

        verify(fallback, times(1)).normalize(any(), any(), any());
    }

    @Test
    @DisplayName("Aislamiento entre sesiones concurrentes A y B")
    void aislamientoEntreSesiones() throws Exception {
        String msgA = "ya no tengo la pieza 2 disponible";

        doReturn(Optional.of("Ya vendí la pieza 2"))
                .when(fallback).normalize(eq(msgA), isNull(), any());

        // Sesión A ejecuta fallback y queda en AWAITING_CONFIRMATION
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS_A)
                        .param("text", msgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", equalTo("AWAITING_CONFIRMATION")))
                .andExpect(jsonPath("$.replyText", containsString("está vendida")));

        // Sesión B permanece en IDLE y con sus 8 productos intactos
        var sessB = catalog.session(SESS_B);
        assertEquals("IDLE", sessB.state());
        assertNull(sessB.pending());
        assertEquals(8, catalog.all(SESS_B).size());
    }
}
