package com.norma.taller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "groq.api.key=",
    "norma.uploads=target/test-uploads-audio",
    "spring.datasource.url=jdbc:sqlite::memory:"
})
@AutoConfigureMockMvc
class ApiAudioTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Catalog catalog;

    @MockitoSpyBean
    private MediaService mediaService;

    private static final String SESS = "sessAudioTest01";

    private static byte[] jpegBytes() throws IOException {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 200, 200);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "JPEG", baos);
        return baos.toByteArray();
    }

    @BeforeEach
    void setUp() {
        catalog.reset(SESS);
        Mockito.reset(mediaService);
    }

    @Test
    void audioSinClaveDevuelve400TranscripcionYSesionPreservada() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "nota.webm", "audio/webm", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/bot/message")
                        .file(audio)
                        .param("sessionId", SESS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("transcripción")));

        // Sesión preservada: el fallo de STT no toca el motor ni el estado.
        assertEquals("IDLE", catalog.session(SESS).state());
        assertEquals(8, catalog.all(SESS).size());
    }

    @Test
    void soloTextoSinClaveDevuelve200() throws Exception {
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", SESS)
                        .param("text", "Publicá Plato a 15000"))
                .andExpect(status().isOk());
    }

    @Test
    void textoYAudioSimultaneosDevuelve400UnoPorVez() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "nota.webm", "audio/webm", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/bot/message")
                        .file(audio)
                        .param("sessionId", SESS)
                        .param("text", "Hola"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("uno por vez")));
    }

    @Test
    void fotoYTextoSinClaveDevuelve200() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "plato.jpg", "image/jpeg", jpegBytes());

        mockMvc.perform(multipart("/api/bot/message")
                        .file(image)
                        .param("sessionId", SESS)
                        .param("text", "Publicá Plato a 15000"))
                .andExpect(status().isOk());
    }

    @Test
    void audioValidoConSttSimuladoPasaAlMotorYDevuelveReply() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "nota.webm", "audio/webm", new byte[]{1, 2, 3});
        doReturn("Publicá Plato a 15000").when(mediaService).transcribe(any());
        doReturn("/media/audio-reply-test.mp3").when(mediaService).speak(any());

        mockMvc.perform(multipart("/api/bot/message")
                        .file(audio)
                        .param("sessionId", SESS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyAudioUrl").value("/media/audio-reply-test.mp3"))
                .andExpect(jsonPath("$.state").value("AWAITING_DETAILS"));

        assertEquals("AWAITING_DETAILS", catalog.session(SESS).state());
    }

    @Test
    void audioIlegibleGroq400Devuelve400NoPudeEscucharYSesionPreservada() throws Exception {
        MockMultipartFile corrupto = new MockMultipartFile("audio", "corrupt.webm", "audio/webm", new byte[]{0x12, 0x34, 0x56, 0x78});
        doThrow(new IllegalArgumentException("No pude escuchar el audio. Probá otra vez o escribime."))
                .when(mediaService).transcribe(any());

        mockMvc.perform(multipart("/api/bot/message")
                        .file(corrupto)
                        .param("sessionId", SESS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("No pude escuchar el audio")));

        assertEquals("IDLE", catalog.session(SESS).state());
        assertEquals(8, catalog.all(SESS).size());
    }

    @Test
    void audioFalloTranscriptorGroq401o429Devuelve400NoPudeTranscribirYSesionPreservada() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "nota.webm", "audio/webm", new byte[]{1, 2, 3});
        doThrow(new IllegalArgumentException("No pude transcribir ahora. Probá otra vez o escribime."))
                .when(mediaService).transcribe(any());

        mockMvc.perform(multipart("/api/bot/message")
                        .file(audio)
                        .param("sessionId", SESS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("No pude transcribir ahora")));

        assertEquals("IDLE", catalog.session(SESS).state());
        assertEquals(8, catalog.all(SESS).size());
    }
}
