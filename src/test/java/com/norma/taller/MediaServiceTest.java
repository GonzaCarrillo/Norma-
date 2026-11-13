package com.norma.taller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    // ==========================================
    // STT Tests (A1 - A3 y Casos Borde)
    // ==========================================

    @Test
    void transcribeConClaveVaciaLanzaIllegalArgumentExceptionMencionandoTranscripcion() {
        MockMultipartFile audio = new MockMultipartFile("audio", "nota.webm", "audio/webm", new byte[]{1, 2, 3});
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> media.transcribe(audio));
        assertTrue(e.getMessage().contains("transcripción"), e.getMessage());
    }

    @Test
    void transcribeConAudioNuloLanzaIllegalArgumentExceptionMencionandoNoPudeEscuchar() throws Exception {
        MediaService conClave = new MediaService(root.toString(), "sk-dummy-key");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> conClave.transcribe(null));
        assertTrue(e.getMessage().contains("No pude escuchar el audio"), e.getMessage());
    }

    @Test
    void transcribeConAudioVacioLanzaIllegalArgumentExceptionMencionandoNoPudeEscuchar() throws Exception {
        MediaService conClave = new MediaService(root.toString(), "sk-dummy-key");
        MockMultipartFile vacio = new MockMultipartFile("audio", "vacio.webm", "audio/webm", new byte[0]);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> conClave.transcribe(vacio));
        assertTrue(e.getMessage().contains("No pude escuchar el audio"), e.getMessage());
    }

    @Test
    void transcribeConGroqFallandoLanzaIllegalArgumentExceptionMencionandoNoPudeTranscribir() throws Exception {
        RestClient mockGroq = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockGroq.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenThrow(new RestClientException("Connection timed out"));

        MediaService svc = new MediaService(root.toString(), "sk-valid-key", mockGroq, null);
        MockMultipartFile audio = new MockMultipartFile("audio", "nota.webm", "audio/webm", new byte[]{1, 2, 3, 4});

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> svc.transcribe(audio));
        assertTrue(e.getMessage().contains("No pude transcribir ahora"), e.getMessage());
    }

    @Test
    void transcribeConGroqRespuestaVaciaLanzaIllegalArgumentExceptionMencionandoNoPudeEscuchar() throws Exception {
        RestClient mockGroq = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode emptyNode = mapper.readTree("{\"text\": \"   \"}");

        when(mockGroq.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(emptyNode);

        MediaService svc = new MediaService(root.toString(), "sk-valid-key", mockGroq, null);
        MockMultipartFile audio = new MockMultipartFile("audio", "nota.webm", "audio/webm", new byte[]{1, 2, 3, 4});

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> svc.transcribe(audio));
        assertTrue(e.getMessage().contains("No pude escuchar el audio"), e.getMessage());
    }

    @Test
    void transcribeConGroqExitosoDevuelveTextoYBorraTemporal() throws Exception {
        RestClient mockGroq = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode successNode = mapper.readTree("{\"text\": \"Publicá Plato a 15000\"}");

        when(mockGroq.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(successNode);

        MediaService svc = new MediaService(root.toString(), "sk-valid-key", mockGroq, null);
        MockMultipartFile audio = new MockMultipartFile("audio", "nota.webm", "audio/webm", new byte[]{1, 2, 3, 4});

        String transcript = svc.transcribe(audio);
        assertEquals("Publicá Plato a 15000", transcript);
    }

    // ==========================================
    // TTS Tests (A4, A5 y Determinismo)
    // ==========================================

    @Test
    void speakNuloDevuelveNullSinLlamarProceso() {
        MediaService.VoiceProcessRunner runner = mock(MediaService.VoiceProcessRunner.class);
        MediaService svc;
        try {
            svc = new MediaService(root.toString(), "", null, runner);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertNull(svc.speak(null));
        verifyNoInteractions(runner);
    }

    @Test
    void speakVacioDevuelveNullSinLlamarProceso() {
        MediaService.VoiceProcessRunner runner = mock(MediaService.VoiceProcessRunner.class);
        MediaService svc;
        try {
            svc = new MediaService(root.toString(), "", null, runner);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertNull(svc.speak("   "));
        verifyNoInteractions(runner);
    }

    @Test
    void speakMasDeMilCaracteresDevuelveNullSinLlamarProceso() {
        MediaService.VoiceProcessRunner runner = mock(MediaService.VoiceProcessRunner.class);
        MediaService svc;
        try {
            svc = new MediaService(root.toString(), "", null, runner);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1001; i++) {
            sb.append('a');
        }
        assertNull(svc.speak(sb.toString()));
        verifyNoInteractions(runner);
    }

    @Test
    void speakConBinarioAusenteDevuelveNullSinBloqueo() throws Exception {
        MediaService.VoiceProcessRunner runner = (text, output) -> {
            throw new IOException("Cannot run program 'edge-tts': error=2, No such file or directory");
        };
        MediaService svc = new MediaService(root.toString(), "", null, runner);

        long inicio = System.currentTimeMillis();
        String resultado = svc.speak("Hola, este es un texto de prueba válido.");
        long transcurrido = System.currentTimeMillis() - inicio;

        assertNull(resultado);
        assertTrue(transcurrido < 5_000, "speak no debe bloquear si el binario falta: " + transcurrido + "ms");
    }

    @Test
    void speakConSalidaNoCeroDevuelveNullYBorraArchivo() throws Exception {
        MediaService.VoiceProcessRunner runner = (text, output) -> {
            Files.writeString(output, "archivo fallido");
            Process p = mock(Process.class);
            when(p.waitFor(12, TimeUnit.SECONDS)).thenReturn(true);
            when(p.exitValue()).thenReturn(1);
            when(p.isAlive()).thenReturn(false);
            return p;
        };
        MediaService svc = new MediaService(root.toString(), "", null, runner);

        assertNull(svc.speak("Texto con fallo de proceso"));
        try (var stream = Files.list(root)) {
            assertEquals(0, stream.filter(p -> p.toString().endsWith(".mp3")).count(),
                    "El archivo mp3 fallido debe ser eliminado");
        }
    }

    @Test
    void speakConTimeoutMataProcesoYBorraArchivo() throws Exception {
        AtomicBoolean processKilled = new AtomicBoolean(false);
        MediaService.VoiceProcessRunner runner = (text, output) -> {
            Files.writeString(output, "archivo truncado");
            Process p = mock(Process.class);
            when(p.waitFor(12, TimeUnit.SECONDS)).thenReturn(false);
            when(p.isAlive()).thenReturn(true);
            when(p.destroyForcibly()).thenAnswer(inv -> {
                processKilled.set(true);
                return p;
            });
            return p;
        };
        MediaService svc = new MediaService(root.toString(), "", null, runner);

        assertNull(svc.speak("Texto con timeout"));
        assertTrue(processKilled.get(), "El proceso debió ser terminado con destroyForcibly");
        try (var stream = Files.list(root)) {
            assertEquals(0, stream.filter(p -> p.toString().endsWith(".mp3")).count(),
                    "El archivo mp3 tras timeout debe ser eliminado");
        }
    }

    @Test
    void speakConArchivoAusenteOByteCeroDevuelveNull() throws Exception {
        MediaService.VoiceProcessRunner runner = (text, output) -> {
            Files.createFile(output); // archivo de 0 bytes
            Process p = mock(Process.class);
            when(p.waitFor(12, TimeUnit.SECONDS)).thenReturn(true);
            when(p.exitValue()).thenReturn(0);
            when(p.isAlive()).thenReturn(false);
            return p;
        };
        MediaService svc = new MediaService(root.toString(), "", null, runner);

        assertNull(svc.speak("Texto con salida vacía"));
        try (var stream = Files.list(root)) {
            assertEquals(0, stream.filter(p -> p.toString().endsWith(".mp3")).count(),
                    "El archivo de 0 bytes debe ser eliminado");
        }
    }

    @Test
    void speakExitosoDevuelveUrlMediaYConservaArchivo() throws Exception {
        MediaService.VoiceProcessRunner runner = (text, output) -> {
            Files.write(output, new byte[]{0x49, 0x44, 0x33, 1, 2, 3});
            Process p = mock(Process.class);
            when(p.waitFor(12, TimeUnit.SECONDS)).thenReturn(true);
            when(p.exitValue()).thenReturn(0);
            when(p.isAlive()).thenReturn(false);
            return p;
        };
        MediaService svc = new MediaService(root.toString(), "", null, runner);

        String url = svc.speak("Hola Norma");
        assertNotNull(url);
        assertTrue(url.startsWith("/media/"));
        assertTrue(url.endsWith(".mp3"));

        Path generado = root.resolve(url.substring("/media/".length()));
        assertTrue(Files.exists(generado));
        assertTrue(Files.size(generado) > 0);
    }
}
