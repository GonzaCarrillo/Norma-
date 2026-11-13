package com.norma.taller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite::memory:"})
@AutoConfigureMockMvc
public class BotApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private Catalog catalog;

    @MockBean private MediaService mediaService;

    private static final String SESS = "session_bot_api_test";

    @BeforeEach
    void setUp() throws Exception {
        catalog.reset(SESS);
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
                .andExpect(jsonPath("$.replyText", containsString("¿Confirmás publicar Taza de barro a 22.000 pesos?")))
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
                .andExpect(jsonPath("$.replyText", containsString("¿Confirmás cambiar el precio de la pieza 1 a 19.000 pesos?")))
                .andExpect(jsonPath("$.replyAudioUrl").doesNotExist());
    }
}
