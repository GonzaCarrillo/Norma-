package com.norma.taller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

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

    private void assertProcessingDirectoryEmpty() throws IOException {
        Path processing = root.resolve(".processing");
        if (Files.exists(processing)) {
            try (var stream = Files.list(processing)) {
                assertEquals(0, stream.count(), "El directorio .processing debe quedar limpio sin temporales residuales");
            }
        }
    }

    // ==========================================
    // STT Tests (A1 - A3, H2 y Casos Borde)
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
        assertProcessingDirectoryEmpty();
    }

    @Test
    void transcribeConAudioVacioLanzaIllegalArgumentExceptionMencionandoNoPudeEscuchar() throws Exception {
        MediaService conClave = new MediaService(root.toString(), "sk-dummy-key");
        MockMultipartFile vacio = new MockMultipartFile("audio", "vacio.webm", "audio/webm", new byte[0]);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> conClave.transcribe(vacio));
        assertTrue(e.getMessage().contains("No pude escuchar el audio"), e.getMessage());
        assertProcessingDirectoryEmpty();
    }

    @Test
    void transcribeConAudioCorruptoGroq400LanzaNoPudeEscucharYBorraTemporal() throws Exception {
        RestClient mockGroq = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockGroq.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request: corrupted audio header"));

        MediaService svc = new MediaService(root.toString(), "sk-valid-key", mockGroq, null);
        MockMultipartFile corrupto = new MockMultipartFile("audio", "corrupt.webm", "audio/webm", new byte[]{0x12, 0x34, 0x56, 0x78});

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> svc.transcribe(corrupto));
        assertEquals("No pude escuchar el audio. Probá otra vez o escribime.", e.getMessage());
        assertProcessingDirectoryEmpty();
    }

    @Test
    void transcribeConAudioIlegibleGroq422LanzaNoPudeEscucharYBorraTemporal() throws Exception {
        RestClient mockGroq = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockGroq.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity: unreadable audio payload"));

        MediaService svc = new MediaService(root.toString(), "sk-valid-key", mockGroq, null);
        MockMultipartFile corrupto = new MockMultipartFile("audio", "corrupt.webm", "audio/webm", new byte[]{(byte) 0xFF, (byte) 0xEE, (byte) 0xDD});

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> svc.transcribe(corrupto));
        assertEquals("No pude escuchar el audio. Probá otra vez o escribime.", e.getMessage());
        assertProcessingDirectoryEmpty();
    }

    @Test
    void transcribeConGroq401NoAutorizadoLanzaNoPudeTranscribirYBorraTemporal() throws Exception {
        RestClient mockGroq = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockGroq.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

        MediaService svc = new MediaService(root.toString(), "sk-invalid-key", mockGroq, null);
        MockMultipartFile audio = new MockMultipartFile("audio", "nota.webm", "audio/webm", new byte[]{1, 2, 3, 4});

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> svc.transcribe(audio));
        assertEquals("No pude transcribir ahora. Probá otra vez o escribime.", e.getMessage());
        assertFalse(e.getMessage().toLowerCase().contains("groq"), "No debe exponer al proveedor externo");
        assertFalse(e.getMessage().toLowerCase().contains("whisper"), "No debe exponer al proveedor externo");
        assertProcessingDirectoryEmpty();
    }

    @Test
    void transcribeConGroq429CuotaLanzaNoPudeTranscribirYBorraTemporal() throws Exception {
        RestClient mockGroq = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockGroq.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded"));

        MediaService svc = new MediaService(root.toString(), "sk-valid-key", mockGroq, null);
        MockMultipartFile audio = new MockMultipartFile("audio", "nota.webm", "audio/webm", new byte[]{1, 2, 3, 4});

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> svc.transcribe(audio));
        assertEquals("No pude transcribir ahora. Probá otra vez o escribime.", e.getMessage());
        assertProcessingDirectoryEmpty();
    }

    @Test
    void transcribeConGroq500ErrorServidorLanzaNoPudeTranscribirYBorraTemporal() throws Exception {
        RestClient mockGroq = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockGroq.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error"));

        MediaService svc = new MediaService(root.toString(), "sk-valid-key", mockGroq, null);
        MockMultipartFile audio = new MockMultipartFile("audio", "nota.webm", "audio/webm", new byte[]{1, 2, 3, 4});

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> svc.transcribe(audio));
        assertEquals("No pude transcribir ahora. Probá otra vez o escribime.", e.getMessage());
        assertProcessingDirectoryEmpty();
    }

    @Test
    void transcribeConTimeoutRedLanzaNoPudeTranscribirYBorraTemporal() throws Exception {
        RestClient mockGroq = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockGroq.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenThrow(new ResourceAccessException("Connection timed out after 5000ms"));

        MediaService svc = new MediaService(root.toString(), "sk-valid-key", mockGroq, null);
        MockMultipartFile audio = new MockMultipartFile("audio", "nota.webm", "audio/webm", new byte[]{1, 2, 3, 4});

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> svc.transcribe(audio));
        assertEquals("No pude transcribir ahora. Probá otra vez o escribime.", e.getMessage());
        assertProcessingDirectoryEmpty();
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
        assertEquals("No pude escuchar el audio. Probá otra vez o escribime.", e.getMessage());
        assertProcessingDirectoryEmpty();
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
        assertProcessingDirectoryEmpty();
    }

    // ==========================================
    // TTS Tests (A4, A5, H3 y Determinismo)
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
    void speakExitosoConRunnerSimuladoConservaArchivo() throws Exception {
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

    @Test
    void speakExitosoConProveedorRealGeneraMp3ReproducibleSiDisponible() throws Exception {
        Path localTool = Paths.get("target/tts-env/bin/edge-tts").toAbsolutePath();
        boolean toolAvailable = Files.isExecutable(localTool);
        if (!toolAvailable) {
            try {
                Process check = new ProcessBuilder("which", "edge-tts").start();
                toolAvailable = check.waitFor() == 0;
            } catch (Exception ignored) {
            }
        }
        if (!toolAvailable) {
            return; // Entorno sin binario edge-tts; cubierto por tests deterministas
        }

        MediaService svc = new MediaService(root.toString(), "");
        String url = svc.speak("Hola Norma, esto es una prueba de síntesis real.");
        assertNotNull(url, "speak con proveedor real debe devolver URL");
        assertTrue(url.startsWith("/media/"));
        assertTrue(url.endsWith(".mp3"));

        Path mp3 = root.resolve(url.substring("/media/".length()));
        assertTrue(Files.exists(mp3), "El archivo MP3 debe existir en root");
        long size = Files.size(mp3);
        assertTrue(size > 500, "El MP3 debe contener datos de audio reales (tamaño: " + size + ")");

        byte[] header = new byte[3];
        try (var is = Files.newInputStream(mp3)) {
            int read = is.read(header);
            assertEquals(3, read);
            boolean isId3 = header[0] == 'I' && header[1] == 'D' && header[2] == '3';
            boolean isMpegSync = (header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0;
            assertTrue(isId3 || isMpegSync, "El archivo debe tener cabecera válida MP3");
        }
    }
}
