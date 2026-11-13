package com.norma.taller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
