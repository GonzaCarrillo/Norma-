package com.norma.taller;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class MediaHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private Catalog catalog;

    @Autowired
    private BotEngine botEngine;

    @TempDir
    static Path sharedTempDir;

    static Path testUploadsRoot;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        testUploadsRoot = sharedTempDir.resolve("media uploads root with spaces");
        registry.add("norma.uploads", () -> testUploadsRoot.toAbsolutePath().toString());
        registry.add("groq.api.key", () -> "");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite::memory:");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MediaService mediaService;

    // ==========================================
    // W1: WebP real lossy, tamaño estricto < 80000, dimensiones y sin metadata
    // ==========================================

    @Test
    void testW1_RealLossyWebP_StrictSizeAndDimensions() throws Exception {
        byte[] jpegBytes = createTestImageBytes("JPEG", 1200, 900, Color.BLUE);
        MockMultipartFile file = new MockMultipartFile("image", "sample.jpg", "image/jpeg", jpegBytes);

        String url = mediaService.image(file);
        assertNotNull(url);
        assertTrue(url.startsWith("/media/"));

        Path published = testUploadsRoot.resolve(url.substring("/media/".length()));
        assertTrue(Files.exists(published));
        long fileSize = Files.size(published);
        assertTrue(fileSize > 0 && fileSize < 80000, "El tamaño debe ser estrictamente < 80000 bytes: " + fileSize);

        BufferedImage img = ImageIO.read(published.toFile());
        assertNotNull(img);
        assertEquals(800, img.getWidth());
        assertEquals(600, img.getHeight());
    }

    // ==========================================
    // W2: Reintentos con calidades exactas [80..10], 79999 vs 80000, 8 fallos
    // ==========================================

    @Test
    void testW2_Retries_79999Accepted_80000Rejected_ExactQualities() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("w2-root-1");
        Files.createDirectories(isolatedRoot);

        List<Float> attemptedQualities = new ArrayList<>();
        byte[] dummyWebp = createTestImageBytes("webp", 800, 600, Color.BLACK);

        PhotoProcessor.WebPEncoder controlledEncoder = (img, q, dest) -> {
            attemptedQualities.add(q);
            try {
                if (attemptedQualities.size() == 1) {
                    // Intento 1 (q=0.8): exactamente 80000 bytes (debe rechazarse por no ser < 80000)
                    byte[] b = makePaddedWebp(dummyWebp, 80000);
                    Files.write(dest, b);
                } else if (attemptedQualities.size() == 2) {
                    // Intento 2 (q=0.7): exactamente 79998 bytes (máximo tamaño par canónico < 80000, debe aceptarse)
                    byte[] b = makePaddedWebp(dummyWebp, 79998);
                    Files.write(dest, b);
                }
            } catch (Exception e) {
                throw new IOException(e);
            }
        };

        PhotoProcessor p = new PhotoProcessor(isolatedRoot, controlledEncoder);
        byte[] inputJpeg = createTestImageBytes("JPEG", 400, 300, Color.RED);
        String url = p.process(new MockMultipartFile("image", "test.jpg", "image/jpeg", inputJpeg));

        assertNotNull(url);
        assertEquals(2, attemptedQualities.size());
        assertEquals(0.80f, attemptedQualities.get(0), 0.001f);
        assertEquals(0.70f, attemptedQualities.get(1), 0.001f);

        Path published = isolatedRoot.resolve(url.substring("/media/".length()));
        assertEquals(79998, Files.size(published));
    }

    @Test
    void testW2_EightAttemptsExhausted_RejectedWith400AndCleaned() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("w2-root-2");
        Files.createDirectories(isolatedRoot);

        List<Float> attemptedQualities = new ArrayList<>();
        byte[] dummyWebp = createTestImageBytes("webp", 800, 600, Color.GRAY);

        PhotoProcessor.WebPEncoder always80000 = (img, q, dest) -> {
            attemptedQualities.add(q);
            try {
                byte[] b = makePaddedWebp(dummyWebp, 80000);
                Files.write(dest, b);
            } catch (Exception e) {
                throw new IOException(e);
            }
        };

        PhotoProcessor p = new PhotoProcessor(isolatedRoot, always80000);
        byte[] inputJpeg = createTestImageBytes("JPEG", 400, 300, Color.GREEN);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> p.process(new MockMultipartFile("image", "hard.jpg", "image/jpeg", inputJpeg)));
        assertEquals("No pude reducir esta foto a menos de 80 KB. Probá con otra.", ex.getMessage());

        // Debe haber ejecutado exactamente 8 intentos con las calidades normativas
        assertEquals(8, attemptedQualities.size());
        float[] expected = {0.8f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f, 0.2f, 0.1f};
        for (int i = 0; i < 8; i++) {
            assertEquals(expected[i], attemptedQualities.get(i), 0.001f);
        }

        // Ningún archivo residual en staging ni en uploads
        assertEquals(0, countFiles(isolatedRoot.resolve(".processing")));
        assertEquals(0, countFilesExcludingProcessing(isolatedRoot));
    }

    // ==========================================
    // F1: Inyecciones de fallo, atomic move y reporte de limpieza
    // ==========================================

    @Test
    void testF1_EncoderFailure_Throws500_ResourcesFreed() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("f1-root-1");
        Files.createDirectories(isolatedRoot);

        PhotoProcessor.WebPEncoder failingEncoder = (img, q, dest) -> {
            throw new IOException("Fallo simulado en codificador nativo WebP");
        };

        PhotoProcessor p = new PhotoProcessor(isolatedRoot, failingEncoder);
        byte[] inputJpeg = createTestImageBytes("JPEG", 400, 300, Color.RED);

        IOException ex = assertThrows(IOException.class,
                () -> p.process(new MockMultipartFile("image", "fail.jpg", "image/jpeg", inputJpeg)));
        assertEquals("Fallo simulado en codificador nativo WebP", ex.getMessage());

        // Verificar que el semáforo quedó liberado y una siguiente solicitud puede procesarse
        assertEquals(0, countFiles(isolatedRoot.resolve(".processing")));
    }

    @Test
    void testF1_CandidateWithExifChunk_RejectedWith500_CleanedAndSemaphoreReleased() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("f1-exif-candidate");
        Files.createDirectories(isolatedRoot);

        byte[] baseWebp = createTestImageBytes("webp", 800, 600, Color.CYAN);
        byte[] exifCandidate = makeWebpWithMetadataChunk(baseWebp, "EXIF");

        PhotoProcessor.WebPEncoder exifEncoder = (img, q, dest) -> Files.write(dest, exifCandidate);
        PhotoProcessor p = new PhotoProcessor(isolatedRoot, exifEncoder);

        byte[] input = createTestImageBytes("JPEG", 400, 300, Color.BLACK);
        IOException ex = assertThrows(IOException.class,
                () -> p.process(new MockMultipartFile("image", "photo.jpg", "image/jpeg", input)));
        assertTrue(ex.getMessage().contains("metadatos prohibidos") || ex.getMessage().contains("EXIF"));

        // Verificar que no hay archivos en uploads ni en .processing
        assertEquals(0, countFilesExcludingProcessing(isolatedRoot));
        assertEquals(0, countFiles(isolatedRoot.resolve(".processing")));

        // Verificar que el semáforo quedó libre para una solicitud válida
        PhotoProcessor normalProcessor = new PhotoProcessor(isolatedRoot);
        String normalUrl = normalProcessor.process(new MockMultipartFile("image", "ok.jpg", "image/jpeg", input));
        assertNotNull(normalUrl);
        assertEquals(1, countFilesExcludingProcessing(isolatedRoot));
    }

    @Test
    void testF1_CandidateWithXmpChunk_RejectedWith500_CleanedAndSemaphoreReleased() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("f1-xmp-candidate");
        Files.createDirectories(isolatedRoot);

        byte[] baseWebp = createTestImageBytes("webp", 800, 600, Color.MAGENTA);
        byte[] xmpCandidate = makeWebpWithMetadataChunk(baseWebp, "XMP ");

        PhotoProcessor.WebPEncoder xmpEncoder = (img, q, dest) -> Files.write(dest, xmpCandidate);
        PhotoProcessor p = new PhotoProcessor(isolatedRoot, xmpEncoder);

        byte[] input = createTestImageBytes("JPEG", 400, 300, Color.BLACK);
        IOException ex = assertThrows(IOException.class,
                () -> p.process(new MockMultipartFile("image", "photo.jpg", "image/jpeg", input)));
        assertTrue(ex.getMessage().contains("metadatos prohibidos") || ex.getMessage().contains("XMP"));

        assertEquals(0, countFilesExcludingProcessing(isolatedRoot));
        assertEquals(0, countFiles(isolatedRoot.resolve(".processing")));

        PhotoProcessor normalProcessor = new PhotoProcessor(isolatedRoot);
        String normalUrl = normalProcessor.process(new MockMultipartFile("image", "ok.jpg", "image/jpeg", input));
        assertNotNull(normalUrl);
    }

    @Test
    void testF1_CandidateTruncatedPayload_RejectedWith500_CleanedAndSemaphoreReleased() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("f1-trunc-candidate");
        Files.createDirectories(isolatedRoot);

        byte[] baseWebp = createTestImageBytes("webp", 800, 600, Color.YELLOW);
        // Encabezado RIFF pero payload truncado prematuramente
        byte[] truncatedCandidate = Arrays.copyOf(baseWebp, 40);

        PhotoProcessor.WebPEncoder truncEncoder = (img, q, dest) -> Files.write(dest, truncatedCandidate);
        PhotoProcessor p = new PhotoProcessor(isolatedRoot, truncEncoder);

        byte[] input = createTestImageBytes("JPEG", 400, 300, Color.BLACK);
        IOException ex = assertThrows(IOException.class,
                () -> p.process(new MockMultipartFile("image", "photo.jpg", "image/jpeg", input)));

        assertEquals(0, countFilesExcludingProcessing(isolatedRoot));
        assertEquals(0, countFiles(isolatedRoot.resolve(".processing")));

        PhotoProcessor normalProcessor = new PhotoProcessor(isolatedRoot);
        String normalUrl = normalProcessor.process(new MockMultipartFile("image", "ok.jpg", "image/jpeg", input));
        assertNotNull(normalUrl);
    }

    @Test
    void testF1_CandidateDimensionMismatch_RejectedWith500_CleanedAndSemaphoreReleased() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("f1-dim-candidate");
        Files.createDirectories(isolatedRoot);

        // Encoder que produce WebP de dimensiones incorrectas (600x800)
        byte[] wrongDimWebp = createTestImageBytes("webp", 600, 800, Color.BLUE);

        PhotoProcessor.WebPEncoder wrongDimEncoder = (img, q, dest) -> Files.write(dest, wrongDimWebp);
        PhotoProcessor p = new PhotoProcessor(isolatedRoot, wrongDimEncoder);

        byte[] input = createTestImageBytes("JPEG", 400, 300, Color.BLACK);
        IOException ex = assertThrows(IOException.class,
                () -> p.process(new MockMultipartFile("image", "photo.jpg", "image/jpeg", input)));
        assertTrue(ex.getMessage().contains("dimensiones"));

        assertEquals(0, countFilesExcludingProcessing(isolatedRoot));
        assertEquals(0, countFiles(isolatedRoot.resolve(".processing")));

        PhotoProcessor normalProcessor = new PhotoProcessor(isolatedRoot);
        String normalUrl = normalProcessor.process(new MockMultipartFile("image", "ok.jpg", "image/jpeg", input));
        assertNotNull(normalUrl);
    }

    @Test
    void testF1_CorruptCandidateValidation_Throws500_Cleaned() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("f1-root-2");
        Files.createDirectories(isolatedRoot);

        // Encoder que escribe basura corrupta de 1000 bytes
        PhotoProcessor.WebPEncoder corruptEncoder = (img, q, dest) -> {
            Files.write(dest, new byte[1000]);
        };

        PhotoProcessor p = new PhotoProcessor(isolatedRoot, corruptEncoder);
        byte[] inputJpeg = createTestImageBytes("JPEG", 400, 300, Color.BLUE);

        IOException ex = assertThrows(IOException.class,
                () -> p.process(new MockMultipartFile("image", "corrupt.jpg", "image/jpeg", inputJpeg)));
        assertTrue(ex.getMessage().toLowerCase().contains("webp") || ex.getMessage().toLowerCase().contains("candidato"));

        assertEquals(0, countFiles(isolatedRoot.resolve(".processing")));
    }

    @Test
    void testF1_CandidateRiffSizeInconsistent_RejectedWith500_CleanedAndSemaphoreReleased() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("f1-riff-inconsistent");
        Files.createDirectories(isolatedRoot);

        byte[] baseWebp = createTestImageBytes("webp", 800, 600, Color.BLACK);
        byte[] corrupted = Arrays.copyOf(baseWebp, baseWebp.length);
        // Corromper tamaño RIFF declarado en bytes 4..7
        corrupted[4] = (byte) (corrupted[4] + 1);

        PhotoProcessor.WebPEncoder encoder = (img, q, dest) -> Files.write(dest, corrupted);
        PhotoProcessor p = new PhotoProcessor(isolatedRoot, encoder);

        byte[] input = createTestImageBytes("JPEG", 400, 300, Color.RED);
        IOException ex = assertThrows(IOException.class,
                () -> p.process(new MockMultipartFile("image", "photo.jpg", "image/jpeg", input)));
        assertTrue(ex.getMessage().contains("Contenedor RIFF inconsistente"));

        assertEquals(0, countFilesExcludingProcessing(isolatedRoot));
        assertEquals(0, countFiles(isolatedRoot.resolve(".processing")));

        PhotoProcessor normalProcessor = new PhotoProcessor(isolatedRoot);
        String normalUrl = normalProcessor.process(new MockMultipartFile("image", "ok.jpg", "image/jpeg", input));
        assertNotNull(normalUrl);
    }

    @Test
    void testF1_CandidateTrailingByte_RejectedWith500_CleanedAndSemaphoreReleased() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("f1-trailing-byte");
        Files.createDirectories(isolatedRoot);

        byte[] baseWebp = createTestImageBytes("webp", 800, 600, Color.BLACK);
        // Agregar un byte extra al final del archivo
        byte[] withTrailing = Arrays.copyOf(baseWebp, baseWebp.length + 1);
        withTrailing[baseWebp.length] = (byte) 0xFF;

        PhotoProcessor.WebPEncoder encoder = (img, q, dest) -> Files.write(dest, withTrailing);
        PhotoProcessor p = new PhotoProcessor(isolatedRoot, encoder);

        byte[] input = createTestImageBytes("JPEG", 400, 300, Color.RED);
        IOException ex = assertThrows(IOException.class,
                () -> p.process(new MockMultipartFile("image", "photo.jpg", "image/jpeg", input)));
        assertTrue(ex.getMessage().contains("RIFF") || ex.getMessage().contains("cursor final"));

        assertEquals(0, countFilesExcludingProcessing(isolatedRoot));
        assertEquals(0, countFiles(isolatedRoot.resolve(".processing")));

        PhotoProcessor normalProcessor = new PhotoProcessor(isolatedRoot);
        String normalUrl = normalProcessor.process(new MockMultipartFile("image", "ok.jpg", "image/jpeg", input));
        assertNotNull(normalUrl);
    }

    @Test
    void testF1_CandidatePartialChunkHeader_RejectedWith500_CleanedAndSemaphoreReleased() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("f1-partial-chunk-header");
        Files.createDirectories(isolatedRoot);

        byte[] baseWebp = createTestImageBytes("webp", 800, 600, Color.BLACK);
        // Construir un archivo cuyo RIFF declare 3 bytes adicionales, pero con cabecera de chunk incompleta
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int newSize = baseWebp.length + 3;
        out.write(baseWebp, 0, 4);
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(newSize - 8).array());
        out.write(baseWebp, 8, baseWebp.length - 8);
        out.write(new byte[]{'B', 'A', 'D'}); // Solo 3 bytes de FourCC, faltan bytes para los 8 de cabecera
        byte[] candidateBytes = out.toByteArray();

        PhotoProcessor.WebPEncoder encoder = (img, q, dest) -> Files.write(dest, candidateBytes);
        PhotoProcessor p = new PhotoProcessor(isolatedRoot, encoder);

        byte[] input = createTestImageBytes("JPEG", 400, 300, Color.RED);
        IOException ex = assertThrows(IOException.class,
                () -> p.process(new MockMultipartFile("image", "photo.jpg", "image/jpeg", input)));
        assertTrue(ex.getMessage().contains("Cabecera de chunk RIFF incompleta"));

        assertEquals(0, countFilesExcludingProcessing(isolatedRoot));
        assertEquals(0, countFiles(isolatedRoot.resolve(".processing")));

        PhotoProcessor normalProcessor = new PhotoProcessor(isolatedRoot);
        String normalUrl = normalProcessor.process(new MockMultipartFile("image", "ok.jpg", "image/jpeg", input));
        assertNotNull(normalUrl);
    }

    @Test
    void testF1_CandidateMissingPaddingByte_RejectedWith500_CleanedAndSemaphoreReleased() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("f1-missing-padding");
        Files.createDirectories(isolatedRoot);

        byte[] baseWebp = createTestImageBytes("webp", 800, 600, Color.BLACK);
        // Crear chunk PAD con longitud impar (5 bytes) omitiendo intencionalmente el byte de alineación RIFF
        byte[] oddPayload = new byte[]{1, 2, 3, 4, 5};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int newSize = baseWebp.length + 8 + oddPayload.length; // Sin el byte extra de alineación requerido
        out.write(baseWebp, 0, 4);
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(newSize - 8).array());
        out.write(baseWebp, 8, baseWebp.length - 8);
        out.write(new byte[]{'P', 'A', 'D', ' '});
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(oddPayload.length).array());
        out.write(oddPayload);
        byte[] candidateBytes = out.toByteArray();

        PhotoProcessor.WebPEncoder encoder = (img, q, dest) -> Files.write(dest, candidateBytes);
        PhotoProcessor p = new PhotoProcessor(isolatedRoot, encoder);

        byte[] input = createTestImageBytes("JPEG", 400, 300, Color.RED);
        IOException ex = assertThrows(IOException.class,
                () -> p.process(new MockMultipartFile("image", "photo.jpg", "image/jpeg", input)));
        assertTrue(ex.getMessage().contains("longitud o padding incompleto"));

        assertEquals(0, countFilesExcludingProcessing(isolatedRoot));
        assertEquals(0, countFiles(isolatedRoot.resolve(".processing")));

        PhotoProcessor normalProcessor = new PhotoProcessor(isolatedRoot);
        String normalUrl = normalProcessor.process(new MockMultipartFile("image", "ok.jpg", "image/jpeg", input));
        assertNotNull(normalUrl);
    }

    @Test
    void testF1_CandidateWithIccpChunk_RejectedWith500_CleanedAndSemaphoreReleased() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("f1-iccp-candidate");
        Files.createDirectories(isolatedRoot);

        byte[] baseWebp = createTestImageBytes("webp", 800, 600, Color.CYAN);
        byte[] iccpCandidate = makeWebpWithMetadataChunk(baseWebp, "ICCP");

        PhotoProcessor.WebPEncoder encoder = (img, q, dest) -> Files.write(dest, iccpCandidate);
        PhotoProcessor p = new PhotoProcessor(isolatedRoot, encoder);

        byte[] input = createTestImageBytes("JPEG", 400, 300, Color.BLACK);
        IOException ex = assertThrows(IOException.class,
                () -> p.process(new MockMultipartFile("image", "photo.jpg", "image/jpeg", input)));
        assertTrue(ex.getMessage().contains("ICCP"));

        assertEquals(0, countFilesExcludingProcessing(isolatedRoot));
        assertEquals(0, countFiles(isolatedRoot.resolve(".processing")));

        PhotoProcessor normalProcessor = new PhotoProcessor(isolatedRoot);
        String normalUrl = normalProcessor.process(new MockMultipartFile("image", "ok.jpg", "image/jpeg", input));
        assertNotNull(normalUrl);
    }

    @Test
    void testF1_CandidateWithVp8xProfileFlag_RejectedWith500_CleanedAndSemaphoreReleased() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("f1-vp8x-profile-flag");
        Files.createDirectories(isolatedRoot);

        byte[] baseWebp = createTestImageBytes("webp", 800, 600, Color.CYAN);
        // Bandera 0x20 = perfil ICCP en encabezado VP8X
        byte[] profileFlagCandidate = makeWebpWithVp8xFlags(baseWebp, (byte) 0x20);

        PhotoProcessor.WebPEncoder encoder = (img, q, dest) -> Files.write(dest, profileFlagCandidate);
        PhotoProcessor p = new PhotoProcessor(isolatedRoot, encoder);

        byte[] input = createTestImageBytes("JPEG", 400, 300, Color.BLACK);
        IOException ex = assertThrows(IOException.class,
                () -> p.process(new MockMultipartFile("image", "photo.jpg", "image/jpeg", input)));
        assertTrue(ex.getMessage().contains("VP8X") || ex.getMessage().contains("perfil"));

        assertEquals(0, countFilesExcludingProcessing(isolatedRoot));
        assertEquals(0, countFiles(isolatedRoot.resolve(".processing")));

        PhotoProcessor normalProcessor = new PhotoProcessor(isolatedRoot);
        String normalUrl = normalProcessor.process(new MockMultipartFile("image", "ok.jpg", "image/jpeg", input));
        assertNotNull(normalUrl);
    }

    // ==========================================
    // F2: Aislamiento, UUIDs, sanitización de nombre y un solo slot de concurrencia
    // ==========================================

    @Test
    void testF2_SanitizedClientFilenames_AndUniqueUuids() throws Exception {
        byte[] jpegBytes = createTestImageBytes("JPEG", 400, 300, Color.MAGENTA);

        // Nombres maliciosos o idénticos
        MockMultipartFile file1 = new MockMultipartFile("image", "../../etc/passwd.jpg", "image/jpeg", jpegBytes);
        MockMultipartFile file2 = new MockMultipartFile("image", "../../etc/passwd.jpg", "image/jpeg", jpegBytes);

        String url1 = mediaService.image(file1);
        String url2 = mediaService.image(file2);

        assertNotEquals(url1, url2);
        assertFalse(url1.contains("passwd"));
        assertFalse(url2.contains("passwd"));
        assertTrue(url1.matches("^/media/[a-f0-9\\-]+\\.webp$"));
        assertTrue(url2.matches("^/media/[a-f0-9\\-]+\\.webp$"));
    }

    @Test
    void testF2_Concurrency_SingleActiveSlot_SecondRequestRejectedWith500() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("f2-concurrency-root");
        Files.createDirectories(isolatedRoot);

        CountDownLatch inEncodeLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        byte[] validWebp = createTestImageBytes("webp", 800, 600, Color.BLACK);

        PhotoProcessor.WebPEncoder blockingEncoder = (img, q, dest) -> {
            inEncodeLatch.countDown();
            try {
                releaseLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Files.write(dest, validWebp);
        };

        PhotoProcessor p = new PhotoProcessor(isolatedRoot, blockingEncoder);
        byte[] input = createTestImageBytes("JPEG", 400, 300, Color.YELLOW);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> future1 = executor.submit(() -> p.process(new MockMultipartFile("image", "f1.jpg", "image/jpeg", input)));

            // Esperar que la primera solicitud esté dentro del slot de procesamiento
            assertTrue(inEncodeLatch.await(3, TimeUnit.SECONDS));

            // La segunda solicitud inmediata debe fallar con IOException (500)
            IOException ex = assertThrows(IOException.class,
                    () -> p.process(new MockMultipartFile("image", "f2.jpg", "image/jpeg", input)));
            assertEquals("Servidor ocupado procesando otra imagen.", ex.getMessage());

            // Liberar la primera solicitud
            releaseLatch.countDown();
            String res1 = future1.get(5, TimeUnit.SECONDS);
            assertNotNull(res1);

            // Una tercera solicitud posterior ahora debe funcionar normalmente
            PhotoProcessor normalP = new PhotoProcessor(isolatedRoot);
            String res3 = normalP.process(new MockMultipartFile("image", "f3.jpg", "image/jpeg", input));
            assertNotNull(res3);
        } finally {
            executor.shutdownNow();
        }
    }

    // ==========================================
    // H1: Serving HTTP con MockMvc: WebP, MP3, 404, bloqueo .processing y traversal
    // ==========================================

    @Test
    void testH1_ServingHttp_WebP_Mp3_Assets_AndBlockedPaths() throws Exception {
        // 1. Subir una imagen real a través de mediaService
        byte[] jpeg = createTestImageBytes("JPEG", 400, 300, Color.CYAN);
        String url = mediaService.image(new MockMultipartFile("image", "test.jpg", "image/jpeg", jpeg));

        // GET archivo subido: 200 OK y Content-Type image/webp
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("image/webp")));

        // 2. GET archivo faltante bajo /media/**: 404 con mensaje exacto
        mockMvc.perform(get("/media/no-existe-" + UUID.randomUUID() + ".webp"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No encontré ese archivo."));

        // 3. Bloqueo de acceso directo a .processing
        mockMvc.perform(get("/media/.processing/upload-dummy.tmp"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No encontré ese archivo."));

        // 4. Bloqueo de traversal simple y codificado
        mockMvc.perform(get("/media/../application.properties"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No encontré ese archivo."));

        mockMvc.perform(get("/media/%2e%2e/application.properties"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No encontré ese archivo."));

        // 5. Assets existentes: pieza-1.webp -> image/webp y demo-create.mp3 -> audio/mpeg
        mockMvc.perform(get("/assets/pieza-1.webp"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("image/webp")));

        mockMvc.perform(get("/assets/demo-create.mp3"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("audio/mpeg")));
    }

    // ==========================================
    // L2: Multipart límite con servidor real en RANDOM_PORT devuelve 413
    // ==========================================

    @Test
    void testL2_MultipartPartExceeds15MB_ReturnsReal413_PayloadTooLarge() throws Exception {
        // Generar un payload multipart que exceda los 15 MiB (15.5 MiB)
        int partSize = (int) (15.5 * 1024 * 1024);
        byte[] largeFileBytes = new byte[partSize];
        Arrays.fill(largeFileBytes, (byte) 0xAA);

        HttpResponse<String> response = sendMultipartOverHttp(
                "/api/bot/message",
                Map.of("sessionId", "session-l2"),
                "image", "large.jpg", "image/jpeg",
                largeFileBytes
        );

        assertEquals(413, response.statusCode());
        assertTrue(response.body().contains("El archivo es demasiado grande. Probá con uno más chico."));

        // Verificar que no se alteró la sesión ni se crearon archivos residuales
        assertEquals(0, countFiles(testUploadsRoot.resolve(".processing")));
        assertEquals(0, countFilesExcludingProcessing(testUploadsRoot));
    }

    // ==========================================
    // A1 / A2: Integración de imagen real con FSM BotEngine y aislamiento
    // ==========================================

    @Test
    void testA1_FullLifecycle_RealImage_BotEngine_Confirmation_Undo() throws Exception {
        String sessionId = "session-a1-integration";
        catalog.ensure(sessionId);
        assertEquals(8, catalog.all(sessionId).size());

        byte[] realJpeg = createTestImageBytes("JPEG", 800, 600, Color.MAGENTA);
        MockMultipartFile imageFile = new MockMultipartFile("image", "jarron.jpg", "image/jpeg", realJpeg);

        // 1. Envío de foto sola -> transición a AWAITING_DETAILS
        mockMvc.perform(multipart("/api/bot/message")
                        .file(imageFile)
                        .param("sessionId", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_DETAILS"));

        // Catálogo sigue con 8 piezas
        assertEquals(8, catalog.all(sessionId).size());

        // 2. Envío de texto con precio y título -> transición a AWAITING_CONFIRMATION
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", sessionId)
                        .param("text", "Jarrón nuevo a veinticinco mil pesos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"));

        assertEquals(8, catalog.all(sessionId).size());

        // 3. Confirmación "Sí" -> IDLE, pieza #9 creada con imagen WebP real
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", sessionId)
                        .param("text", "Sí"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"));

        var products = catalog.all(sessionId);
        assertEquals(9, products.size());
        var newPiece = products.get(8);
        assertEquals(25000, newPiece.price());
        assertTrue(newPiece.imageUrl().startsWith("/media/"));
        assertTrue(newPiece.imageUrl().endsWith(".webp"));

        // El archivo publicado existe físicamente en uploads y es un WebP válido
        Path published = testUploadsRoot.resolve(newPiece.imageUrl().substring("/media/".length()));
        assertTrue(Files.exists(published));
        assertTrue(Files.size(published) < 80000);

        // 4. Actualizar foto de pieza 2: "Esta foto es para cambiar la de la pieza 2" + nueva imagen real
        byte[] newJpeg = createTestImageBytes("JPEG", 400, 300, Color.CYAN);
        MockMultipartFile newImageFile = new MockMultipartFile("image", "plato.jpg", "image/jpeg", newJpeg);

        mockMvc.perform(multipart("/api/bot/message")
                        .file(newImageFile)
                        .param("sessionId", sessionId)
                        .param("text", "Esta foto es para cambiar la de la pieza 2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"));

        // 5. Confirmar actualización -> IDLE
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", sessionId)
                        .param("text", "Sí"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"));

        var piece2 = catalog.all(sessionId).get(1);
        String piece2NewUrl = piece2.imageUrl();
        assertTrue(piece2NewUrl.startsWith("/media/"));

        // 6. Undo: "Me equivoqué" revierte la imagen de la pieza 2 a la original
        mockMvc.perform(multipart("/api/bot/message")
                        .param("sessionId", sessionId)
                        .param("text", "Me equivoqué"))
                .andExpect(status().isOk());

        var piece2Reverted = catalog.all(sessionId).get(1);
        assertEquals("/assets/pieza-2.webp", piece2Reverted.imageUrl());

        // La URL anterior sigue accesible en /media/ (estabilidad referencial)
        mockMvc.perform(get(piece2NewUrl))
                .andExpect(status().isOk());
    }

    @Test
    void testA2_InvalidMediaAndSimultaneousTextAudio_PreservesSessionAndProduct() throws Exception {
        String sessionId = "session-a2-isolation";
        catalog.ensure(sessionId);
        var initialProducts = catalog.all(sessionId);

        // 1. Texto y audio simultáneos -> rechazado con 400 antes de procesar imagen
        MockMultipartFile dummyAudio = new MockMultipartFile("audio", "nota.mp3", "audio/mpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/bot/message")
                        .file(dummyAudio)
                        .param("sessionId", sessionId)
                        .param("text", "Texto descriptivo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mandame el texto o el audio con los detalles, uno por vez."));

        assertEquals(initialProducts, catalog.all(sessionId));

        // 2. Imagen inválida (corrupta) en DETAILS -> 400, estado y catálogo preservados
        MockMultipartFile corruptFile = new MockMultipartFile("image", "corrupt.jpg", "image/jpeg", new byte[]{0, 1, 2, 3});
        mockMvc.perform(multipart("/api/bot/message")
                        .file(corruptFile)
                        .param("sessionId", sessionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG."));

        assertEquals(initialProducts, catalog.all(sessionId));
        assertEquals(0, countFiles(testUploadsRoot.resolve(".processing")));
    }

    // ==========================================
    // F1: Cobertura explícita de sistema de archivos (Atomic Move no soportado y Symlinks)
    // ==========================================

    @Test
    void testF1_AtomicMoveUnsupported_ThrowsIOException_CleanedAndReleased() throws Exception {
        Path isolatedRoot = sharedTempDir.resolve("f1-atomic-unsupported");
        Files.createDirectories(isolatedRoot);

        byte[] validWebp = createTestImageBytes("webp", 800, 600, Color.BLACK);
        PhotoProcessor.WebPEncoder encoder = (img, q, dest) -> Files.write(dest, validWebp);

        PhotoProcessor.FileMover unsupportedMover = (src, dst) -> {
            throw new AtomicMoveNotSupportedException(src.toString(), dst.toString(), "Simulación de volumen sin soporte de ATOMIC_MOVE");
        };

        PhotoProcessor p = new PhotoProcessor(isolatedRoot, encoder, unsupportedMover);
        byte[] input = createTestImageBytes("JPEG", 400, 300, Color.BLUE);

        IOException ex = assertThrows(IOException.class,
                () -> p.process(new MockMultipartFile("image", "test.jpg", "image/jpeg", input)));
        assertTrue(ex.getMessage().contains("movimiento atómico"));

        // Verificar que no quedó ningún archivo en uploads ni en staging y que el semáforo quedó libre
        assertEquals(0, countFilesExcludingProcessing(isolatedRoot));
        assertEquals(0, countFiles(isolatedRoot.resolve(".processing")));

        PhotoProcessor normal = new PhotoProcessor(isolatedRoot);
        String url = normal.process(new MockMultipartFile("image", "ok.jpg", "image/jpeg", input));
        assertNotNull(url);
    }

    @Test
    void testF1_SymlinkTraversalBlocked_Returns404() throws Exception {
        // Crear un archivo objetivo fuera de uploads
        Path outsideFile = sharedTempDir.resolve("outside-secret.txt");
        Files.writeString(outsideFile, "SECRET_DATA");

        // Crear un symlink dentro de testUploadsRoot apuntando fuera
        Path symlink = testUploadsRoot.resolve("symlink-target.webp");
        try {
            Files.createSymbolicLink(symlink, outsideFile);
        } catch (UnsupportedOperationException | IOException e) {
            // Si el SO/entorno no permite symlinks, omitir gracefully
            return;
        }

        try {
            mockMvc.perform(get("/media/symlink-target.webp"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("No encontré ese archivo."));
        } finally {
            Files.deleteIfExists(symlink);
            Files.deleteIfExists(outsideFile);
        }
    }

    // ==========================================
    // Helpers
    // ==========================================

    private HttpResponse<String> sendMultipartOverHttp(
            String path,
            Map<String, String> params,
            String partName,
            String fileName,
            String contentType,
            byte[] fileBytes
    ) throws Exception {
        String boundary = "----NormaBoundary" + System.currentTimeMillis();
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"" + partName + "\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(fileBytes);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private byte[] makePaddedWebp(byte[] baseWebp, int targetSize) throws IOException {
        int currentLen = baseWebp.length;
        int padPayload = targetSize - currentLen - 8;
        if (padPayload < 0) {
            throw new IllegalArgumentException("targetSize smaller than baseWebp + header");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(baseWebp, 0, 4); // RIFF
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(targetSize - 8).array());
        out.write(baseWebp, 8, baseWebp.length - 8);
        out.write(new byte[]{'P', 'A', 'D', ' '});
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(padPayload).array());
        out.write(new byte[padPayload]);
        if (padPayload % 2 != 0) {
            out.write(0);
        }
        return out.toByteArray();
    }

    private byte[] makeWebpWithMetadataChunk(byte[] baseWebp, String chunkFourCC) throws IOException {
        int currentLen = baseWebp.length;
        byte[] payload = new byte[32];
        int newSize = currentLen + 8 + payload.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(baseWebp, 0, 4); // RIFF
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(newSize - 8).array());
        out.write(baseWebp, 8, baseWebp.length - 8);
        byte[] fourCCBytes = Arrays.copyOf(chunkFourCC.getBytes(StandardCharsets.US_ASCII), 4);
        out.write(fourCCBytes);
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.length).array());
        out.write(payload);
        return out.toByteArray();
    }

    private byte[] makeWebpWithVp8xFlags(byte[] rawWebp, byte flags) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{'R', 'I', 'F', 'F'});
        int targetSize = rawWebp.length + 18;
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(targetSize - 8).array());
        out.write(new byte[]{'W', 'E', 'B', 'P'});
        out.write(new byte[]{'V', 'P', '8', 'X'});
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(10).array());
        out.write(flags);
        out.write(new byte[]{0, 0, 0});
        int wM1 = 800 - 1;
        int hM1 = 600 - 1;
        out.write(new byte[]{(byte) (wM1 & 0xFF), (byte) ((wM1 >> 8) & 0xFF), (byte) ((wM1 >> 16) & 0xFF)});
        out.write(new byte[]{(byte) (hM1 & 0xFF), (byte) ((hM1 >> 8) & 0xFF), (byte) ((hM1 >> 16) & 0xFF)});
        out.write(rawWebp, 12, rawWebp.length - 12);
        return out.toByteArray();
    }

    private byte[] createTestImageBytes(String format, int w, int h, Color color) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String fmt = "webp".equalsIgnoreCase(format) ? "webp" : format;
        ImageIO.write(img, fmt, baos);
        return baos.toByteArray();
    }

    private long countFiles(Path dir) {
        if (!Files.exists(dir)) return 0;
        try (var stream = Files.list(dir)) {
            return stream.count();
        } catch (IOException e) {
            return 0;
        }
    }

    private long countFilesExcludingProcessing(Path dir) {
        if (!Files.exists(dir)) return 0;
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> !p.getFileName().toString().equals(".processing")).count();
        } catch (IOException e) {
            return 0;
        }
    }
}
