package com.norma.taller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class MediaServiceTest {

    private Path root;
    private MediaService media;

    @BeforeEach
    void setUp() throws Exception {
        root = Files.createTempDirectory(Paths.get("target").toAbsolutePath(), "media-service-test-");
        media = new MediaService(root.toString(), "");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (root != null) {
            Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }

    @Test
    void transcribeConClaveVaciaLanzaIllegalArgumentExceptionMencionandoTranscripcion() {
        MockMultipartFile audio = new MockMultipartFile("audio", "nota.webm", "audio/webm", new byte[]{1, 2, 3});
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> media.transcribe(audio));
        assertTrue(e.getMessage().contains("transcripción"), e.getMessage());
    }

    @Test
    void transcribeConAudioBasuraLanzaIllegalArgumentException() throws Exception {
        MediaService conClave = new MediaService(root.toString(), "sk-test-clave-no-blanca");
        MockMultipartFile basura = new MockMultipartFile("audio", "basura.webm", "audio/webm", new byte[]{0, 1, 2, 3, 4, 5});
        assertThrows(IllegalArgumentException.class, () -> conClave.transcribe(basura));
    }

    @Test
    void speakNuloDevuelveNull() {
        assertNull(media.speak(null));
    }

    @Test
    void speakVacioDevuelveNull() {
        assertNull(media.speak(""));
    }

    @Test
    void speakMasDeMilCaracteresDevuelveNull() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1001; i++) {
            sb.append('a');
        }
        assertNull(media.speak(sb.toString()));
    }

    @Test
    void speakTextoValidoConEdgeTtsAusenteDevuelveNullSinBloqueo() {
        long inicio = System.currentTimeMillis();
        String resultado = media.speak("Hola, este es un texto de prueba válido para sintetizar.");
        long transcurrido = System.currentTimeMillis() - inicio;
        assertNull(resultado);
        assertTrue(transcurrido < 30_000, "speak no debe bloquear más de 30s: " + transcurrido + "ms");
    }
}
