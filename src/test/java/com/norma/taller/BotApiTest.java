package com.norma.taller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite::memory:"})
@AutoConfigureMockMvc
public class BotApiTest {

    @TestConfiguration
    static class StubMediaConfig {
        @Bean
        @Primary
        public MediaService mediaService() throws Exception {
            Path tempDir = Files.createTempDirectory("norma-test-media-root");
            MediaService ms = mock(MediaService.class);
            Field rootField = MediaService.class.getDeclaredField("root");
            rootField.setAccessible(true);
            rootField.set(ms, tempDir);
            return ms;
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private Catalog catalog;
    @Autowired private MediaService mediaService;

    private static final String SESS = "session_bot_api_test";

    @BeforeEach
    void setUp() throws Exception {
        catalog.reset(SESS);
        reset(mediaService);
        when(mediaService.image(any())).thenReturn("/media/mock-image.webp");
        when(mediaService.speak(any())).thenReturn("/media/mock-audio.mp3");
    }

    @Test
    @DisplayName("A1.1: Texto y Audio no vacíos simultáneos devuelven HTTP 400 sin alterar estado")
    void testSimultaneousTextAndAudioReturns400() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "nota.webm", "audio/webm", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/bot/message")
                        .file(audio)
                        .param("sessionId", SESS)
                        .param("text", "Publicá Plato a 15000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Mandame el texto o el audio con los detalles, uno por vez.")));

        // Estado sigue IDLE y no se invocó ni mediaService ni botEngine
        assertEquals("IDLE", catalog.session(SESS).state());
        verify(mediaService, never()).transcribe(any());
    }

    @Test
    @DisplayName("A1.2: Multipart con foto y audio usa transcripción de MediaService y responde confirmación")
    void testMultipartPhotoAndAudioWithStub() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "plato.jpg", "image/jpeg", new byte[]{10, 20});
        MockMultipartFile audio = new MockMultipartFile("audio", "audio.webm", "audio/webm", new byte[]{30, 40});

        when(mediaService.transcribe(any())).thenReturn("Publicá Taza de barro a 22000");

        mockMvc.perform(multipart("/api/bot/message")
                        .file(image)
                        .file(audio)
                        .param("sessionId", SESS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.replyText", containsString("¿Confirmás publicar Taza de barro a $ 22.000")))
                .andExpect(jsonPath("$.replyAudioUrl").value("/media/mock-audio.mp3"));

        assertEquals("AWAITING_CONFIRMATION", catalog.session(SESS).state());
        verify(mediaService, times(1)).transcribe(any());
        verify(mediaService, times(1)).image(any());
    }

    @Test
    @DisplayName("A1.3: Fallo en STT transcribe() no invoca al bot ni destruye borrador")
    void testSttFailurePreservesState() throws Exception {
        // Ponemos la sesión en DETAILS enviando primero una foto
        MockMultipartFile image = new MockMultipartFile("image", "plato.jpg", "image/jpeg", new byte[]{10, 20});
        mockMvc.perform(multipart("/api/bot/message")
                        .file(image)
                        .param("sessionId", SESS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_DETAILS"));

        assertEquals("AWAITING_DETAILS", catalog.session(SESS).state());

        // Ahora enviamos audio que falla en STT
        MockMultipartFile audio = new MockMultipartFile("audio", "audio.webm", "audio/webm", new byte[]{30, 40});
        when(mediaService.transcribe(any())).thenThrow(new IllegalArgumentException("No se pudo transcribir el audio"));

        mockMvc.perform(multipart("/api/bot/message")
                        .file(audio)
                        .param("sessionId", SESS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("No se pudo transcribir el audio")));

        // El borrador DETAILS permanece
        assertEquals("AWAITING_DETAILS", catalog.session(SESS).state());
    }

    @Test
    @DisplayName("A1.4: TTS speak() devolviendo null conserva respuesta textual y estado")
    void testTtsNullStillDeliversReplyText() throws Exception {
        when(mediaService.speak(any())).thenReturn(null);

        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS)
                        .param("text", "Cambiá el precio de #1 a 19000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.replyText", containsString("¿Confirmás cambiar el precio de la pieza #1 (Cuenco rústico) a $ 19.000")))
                .andExpect(jsonPath("$.replyAudioUrl").doesNotExist());
    }

    @Test
    @DisplayName("A1.5: Foto y Texto multipart simultáneos pasan directamente a AWAITING_CONFIRMATION")
    void testMultipartPhotoAndTextTogether() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "plato.jpg", "image/jpeg", new byte[]{10, 20});

        mockMvc.perform(multipart("/api/bot/message")
                        .file(image)
                        .param("sessionId", SESS)
                        .param("text", "Publicá Plato cerámico a 18000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.replyText", containsString("¿Confirmás publicar Plato cerámico a $ 18.000")));

        assertEquals("AWAITING_CONFIRMATION", catalog.session(SESS).state());
        verify(mediaService, times(1)).image(any());
        verify(mediaService, never()).transcribe(any());
    }

    @Test
    @DisplayName("A1.6: Foto sola seguida de texto con detalles y confirmación con TTS null no repite acción")
    void testPhotoFollowedByDetailsAndConfirmWithTtsNull() throws Exception {
        // 1. Enviar solo foto -> AWAITING_DETAILS
        MockMultipartFile image = new MockMultipartFile("image", "foto.jpg", "image/jpeg", new byte[]{1, 2});
        mockMvc.perform(multipart("/api/bot/message")
                        .file(image)
                        .param("sessionId", SESS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_DETAILS"));
        assertEquals("AWAITING_DETAILS", catalog.session(SESS).state());

        // 2. Enviar texto con detalles -> AWAITING_CONFIRMATION
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS)
                        .param("text", "Plato hondo a 20000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.replyText", containsString("¿Confirmás publicar Plato hondo a $ 20.000")));

        // 3. Confirmar con TTS devolviendo null
        when(mediaService.speak(any())).thenReturn(null);
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS)
                        .param("text", "sí"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"))
                .andExpect(jsonPath("$.replyAudioUrl").doesNotExist());

        assertEquals(9, catalog.all(SESS).size());
        assertEquals("IDLE", catalog.session(SESS).state());

        // 4. Segundo sí no repite la acción
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS)
                        .param("text", "sí"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyText", containsString("No hay un cambio pendiente")));

        assertEquals(9, catalog.all(SESS).size());
    }
}
