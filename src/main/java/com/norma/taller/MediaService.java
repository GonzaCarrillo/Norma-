package com.norma.taller;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.LinkedMultiValueMap;

@Service
public class MediaService {

    @FunctionalInterface
    interface VoiceProcessRunner {
        Process start(String text, Path outputFile) throws Exception;
    }

    final Path root;
    private final String key;
    private final RestClient groq;
    final PhotoProcessor photoProcessor;
    private final VoiceProcessRunner ttsRunner;

    @Autowired
    public MediaService(@Value("${norma.uploads}") String dir, @Value("${groq.api.key}") String key) throws IOException {
        this(dir, key, null, null);
    }

    MediaService(String dir, String key, RestClient groqClient, VoiceProcessRunner ttsRunner) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(this.root.resolve(".processing"));
        Files.createDirectories(this.root);
        this.key = key != null ? key : "";
        this.photoProcessor = new PhotoProcessor(this.root);
        if (groqClient != null) {
            this.groq = groqClient;
        } else {
            var http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(http);
            factory.setReadTimeout(Duration.ofSeconds(20));
            this.groq = RestClient.builder().baseUrl("https://api.groq.com/openai/v1").requestFactory(factory).build();
        }
        this.ttsRunner = ttsRunner != null ? ttsRunner : (text, output) -> {
            String cmd = "edge-tts";
            Path localTool = Path.of("target/tts-env/bin/edge-tts").toAbsolutePath();
            if (Files.isExecutable(localTool)) {
                cmd = localTool.toString();
            }
            return new ProcessBuilder(
                    cmd,
                    "--voice", "es-AR-TomasNeural",
                    "--text", text,
                    "--write-media", output.toString()
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        };
    }

    public String image(MultipartFile file) throws IOException {
        return photoProcessor.process(file);
    }

    public String transcribe(MultipartFile audio) throws IOException {
        if (key.isBlank()) {
            throw new IllegalArgumentException("La transcripción no está configurada. Podés escribir el mensaje o usar un ejemplo.");
        }
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("No pude escuchar el audio. Probá otra vez o escribime.");
        }
        Path tempDir = Files.exists(root.resolve(".processing")) ? root.resolve(".processing") : root;
        Path temp = Files.createTempFile(tempDir, "norma-audio-", ".webm");
        try {
            audio.transferTo(temp);
            var body = new LinkedMultiValueMap<String, Object>();
            body.add("file", new FileSystemResource(temp));
            body.add("model", "whisper-large-v3-turbo");
            body.add("language", "es");
            var result = groq.post()
                    .uri("/audio/transcriptions")
                    .header("Authorization", "Bearer " + key)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(com.fasterxml.jackson.databind.JsonNode.class);
            if (result == null || result.path("text").asText().isBlank()) {
                throw new IllegalArgumentException("No pude escuchar el audio. Probá otra vez o escribime.");
            }
            return result.path("text").asText();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 400 || status == 422) {
                throw new IllegalArgumentException("No pude escuchar el audio. Probá otra vez o escribime.");
            }
            throw new IllegalArgumentException("No pude transcribir ahora. Probá otra vez o escribime.");
        } catch (RestClientException e) {
            throw new IllegalArgumentException("No pude transcribir ahora. Probá otra vez o escribime.");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public String speak(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.length() > 1000) {
            return null;
        }
        String name = UUID.randomUUID() + ".mp3";
        Path file = root.resolve(name);
        Process p = null;
        try {
            p = ttsRunner.start(text, file);
            if (p.waitFor(12, TimeUnit.SECONDS) && p.exitValue() == 0 && Files.exists(file) && Files.size(file) > 0) {
                return "/media/" + name;
            }
        } catch (Exception ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {}
        return null;
    }
}
