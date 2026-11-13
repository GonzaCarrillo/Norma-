package com.norma.taller;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class PhotoProcessorTest {

    @TempDir
    Path tempDir;

    private Path uploadsRoot;
    private PhotoProcessor processor;

    @BeforeEach
    void setUp() {
        uploadsRoot = tempDir.resolve("uploads");
        processor = new PhotoProcessor(uploadsRoot);
    }

    // ==========================================
    // V1: Formatos válidos e indiferencia de MIME/extensión
    // ==========================================

    @Test
    void testV1_ValidJpeg_AcceptedRegardlessOfMimeOrFilename() throws Exception {
        byte[] jpegBytes = createTestImageBytes("JPEG", 400, 300, Color.BLUE);
        MockMultipartFile file = new MockMultipartFile("image", "foto.webp", "application/octet-stream", jpegBytes);

        String url = processor.process(file);
        assertNotNull(url);
        assertTrue(url.startsWith("/media/"));
        assertTrue(url.endsWith(".webp"));

        Path saved = uploadsRoot.resolve(url.substring("/media/".length()));
        assertTrue(Files.exists(saved));
        assertTrue(Files.size(saved) > 0 && Files.size(saved) < 80000);

        BufferedImage decoded = ImageIO.read(saved.toFile());
        assertNotNull(decoded);
        assertEquals(800, decoded.getWidth());
        assertEquals(600, decoded.getHeight());
    }

    @Test
    void testV1_ValidWebP_AcceptedWithJpgFilename() throws Exception {
        byte[] webpBytes = createTestImageBytes("webp", 400, 300, Color.MAGENTA);
        MockMultipartFile file = new MockMultipartFile("image", "foto.jpg", "image/jpeg", webpBytes);

        String url = processor.process(file);
        assertNotNull(url);
        assertTrue(url.startsWith("/media/"));

        Path saved = uploadsRoot.resolve(url.substring("/media/".length()));
        assertTrue(Files.exists(saved));
        assertEquals(800, ImageIO.read(saved.toFile()).getWidth());
    }

    @Test
    void testV1_ValidPng8_Rgb_Grayscale_Indexed_Alpha() throws Exception {
        // RGB
        byte[] pngRgb = createTestImageBytes("PNG", 400, 300, Color.RED);
        assertNotNull(processor.process(new MockMultipartFile("image", "rgb.png", "image/png", pngRgb)));

        // Grayscale
        BufferedImage grayImg = new BufferedImage(400, 300, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = grayImg.createGraphics();
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, 400, 300);
        g.dispose();
        ByteArrayOutputStream baosGray = new ByteArrayOutputStream();
        ImageIO.write(grayImg, "PNG", baosGray);
        assertNotNull(processor.process(new MockMultipartFile("image", "gray.png", "image/png", baosGray.toByteArray())));

        // Alpha (ARGB)
        BufferedImage alphaImg = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ga = alphaImg.createGraphics();
        ga.setColor(new Color(255, 0, 0, 128));
        ga.fillRect(0, 0, 400, 300);
        ga.dispose();
        ByteArrayOutputStream baosAlpha = new ByteArrayOutputStream();
        ImageIO.write(alphaImg, "PNG", baosAlpha);
        assertNotNull(processor.process(new MockMultipartFile("image", "alpha.png", "image/png", baosAlpha.toByteArray())));

        // Indexed
        byte[] r = new byte[]{(byte) 0, (byte) 255};
        byte[] gCol = new byte[]{(byte) 0, (byte) 255};
        byte[] b = new byte[]{(byte) 0, (byte) 255};
        IndexColorModel icm = new IndexColorModel(1, 2, r, gCol, b);
        BufferedImage indexedImg = new BufferedImage(400, 300, BufferedImage.TYPE_BYTE_BINARY, icm);
        ByteArrayOutputStream baosIdx = new ByteArrayOutputStream();
        ImageIO.write(indexedImg, "PNG", baosIdx);
        assertNotNull(processor.process(new MockMultipartFile("image", "indexed.png", "image/png", baosIdx.toByteArray())));
    }

    // ==========================================
    // V2: Rechazos 400 y limpieza estricta
    // ==========================================

    @Test
    void testV2_EmptyAndGarbageDisguised_Rejected() {
        Path staging = uploadsRoot.resolve(".processing");

        // Empty file
        MockMultipartFile emptyFile = new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]);
        IllegalArgumentException exEmpty = assertThrows(IllegalArgumentException.class, () -> processor.process(emptyFile));
        assertEquals("La foto está vacía o pesa más de 15 MB.", exEmpty.getMessage());

        // Garbage text
        MockMultipartFile garbage = new MockMultipartFile("image", "bad.jpg", "image/jpeg", "ESTO NO ES UNA FOTO VALIDA NI TIENE CABECERA".getBytes());
        IllegalArgumentException exGarbage = assertThrows(IllegalArgumentException.class, () -> processor.process(garbage));
        assertEquals("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.", exGarbage.getMessage());

        // Verificar que no queden temporales en staging
        assertEquals(0, countFiles(staging));
    }

    @Test
    void testV2_ExcludedFormats_HEIC_GIF_BMP_Rejected() {
        Path staging = uploadsRoot.resolve(".processing");

        // GIF signature
        byte[] gif = new byte[]{'G', 'I', 'F', '8', '9', 'a', 0x10, 0x00, 0x10, 0x00, (byte) 0x80, 0, 0};
        IllegalArgumentException exGif = assertThrows(IllegalArgumentException.class,
                () -> processor.process(new MockMultipartFile("image", "anim.gif", "image/gif", gif)));
        assertEquals("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.", exGif.getMessage());

        // BMP signature
        byte[] bmp = new byte[]{'B', 'M', 0x36, 0x00, 0x00, 0x00, 0, 0, 0, 0, 0x36, 0, 0, 0};
        IllegalArgumentException exBmp = assertThrows(IllegalArgumentException.class,
                () -> processor.process(new MockMultipartFile("image", "test.bmp", "image/bmp", bmp)));
        assertEquals("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.", exBmp.getMessage());

        assertEquals(0, countFiles(staging));
    }

    @Test
    void testV2_AnimatedPng_Rejected() throws Exception {
        Path staging = uploadsRoot.resolve(".processing");

        // Construir PNG mínimo con chunk acTL
        byte[] pngValid = createTestImageBytes("PNG", 100, 100, Color.ORANGE);
        byte[] pngAnim = injectPngChunk(pngValid, "acTL", new byte[]{0, 0, 0, 1, 0, 0, 0, 0});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.process(new MockMultipartFile("image", "animated.png", "image/png", pngAnim)));
        assertEquals("Mandame una foto estática JPEG, PNG o WebP válida.", ex.getMessage());

        assertEquals(0, countFiles(staging));
    }

    @Test
    void testV2_AnimatedWebP_Rejected() {
        Path staging = uploadsRoot.resolve(".processing");

        // Construir WebP con FourCC ANIM
        ByteBuffer bb = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(new byte[]{'R', 'I', 'F', 'F'});
        bb.putInt(24);
        bb.put(new byte[]{'W', 'E', 'B', 'P'});
        bb.put(new byte[]{'A', 'N', 'I', 'M'});
        bb.putInt(4);
        bb.putInt(0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.process(new MockMultipartFile("image", "anim.webp", "image/webp", bb.array())));
        assertEquals("Mandame una foto estática JPEG, PNG o WebP válida.", ex.getMessage());

        assertEquals(0, countFiles(staging));
    }

    @Test
    void testV2_ExcessiveOrCorruptedExif_Rejected() throws Exception {
        Path staging = uploadsRoot.resolve(".processing");

        // EXIF de más de 256 KiB
        byte[] hugeExif = new byte[262145];
        byte[] pngValid = createTestImageBytes("PNG", 100, 100, Color.GREEN);
        byte[] pngHugeExif = injectPngChunk(pngValid, "eXIf", hugeExif);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.process(new MockMultipartFile("image", "huge_exif.png", "image/png", pngHugeExif)));
        assertEquals("La foto contiene metadatos dañados o no soportados.", ex.getMessage());

        // EXIF corrupto (datos inválidos en eXIf)
        byte[] corruptExif = new byte[]{'I', 'I', 42, 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        byte[] pngCorruptExif = injectPngChunk(pngValid, "eXIf", corruptExif);
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> processor.process(new MockMultipartFile("image", "corrupt_exif.png", "image/png", pngCorruptExif)));
        assertEquals("La foto contiene metadatos dañados o no soportados.", ex2.getMessage());

        assertEquals(0, countFiles(staging));
    }

    // ==========================================
    // L1: Límites de tamaño, ejes, píxeles y memoria 24MP
    // ==========================================

    @Test
    void testL1_SizeLimit_15MiB_ExactVsExceeded() {
        // MockMultipartFile que dice medir 15 MiB + 1
        MockMultipartFile exceededMeta = new MockMultipartFile("image", "huge.jpg", "image/jpeg", new byte[10]) {
            @Override
            public long getSize() {
                return 15 * 1024 * 1024 + 1;
            }
        };
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> processor.process(exceededMeta));
        assertEquals("La foto está vacía o pesa más de 15 MB.", ex.getMessage());

        // Stream que supera 15 MiB aunque getSize mienta
        MockMultipartFile lyingFile = new MockMultipartFile("image", "lie.jpg", "image/jpeg", new byte[10]) {
            @Override
            public long getSize() {
                return 100;
            }

            @Override
            public InputStream getInputStream() {
                return new InputStream() {
                    long count = 0;
                    @Override
                    public int read() {
                        if (count++ > 15 * 1024 * 1024 + 1) return -1;
                        return 0xFF;
                    }
                };
            }
        };
        IllegalArgumentException exStream = assertThrows(IllegalArgumentException.class, () -> processor.process(lyingFile));
        assertEquals("La foto está vacía o pesa más de 15 MB.", exStream.getMessage());
    }

    @Test
    void testL1_AxisAndPixelLimits_LongProductCalculation() throws Exception {
        // Cabecera PNG con eje 16385 (debe rechazarse antes de decodificar raster)
        byte[] pngValid = createTestImageBytes("PNG", 100, 100, Color.BLACK);
        // Modificar ancho en IHDR (bytes 16..19)
        byte[] pngOverAxis = pngValid.clone();
        pngOverAxis[16] = 0;
        pngOverAxis[17] = 0;
        pngOverAxis[18] = 0x40; // 16384 + 1 = 16385 -> 0x4001
        pngOverAxis[19] = 0x01;

        IllegalArgumentException exAxis = assertThrows(IllegalArgumentException.class,
                () -> processor.process(new MockMultipartFile("image", "axis.png", "image/png", pngOverAxis)));
        assertEquals("La foto es demasiado grande. Usá hasta 24 megapíxeles y 16384 píxeles por lado.", exAxis.getMessage());

        // Modificar para producto que exceda 24MP en long (e.g. 10000 x 3000 = 30MP <= eje 16384 pero > 24MP)
        byte[] pngOverPixels = pngValid.clone();
        // 10000 = 0x2710
        pngOverPixels[16] = 0; pngOverPixels[17] = 0; pngOverPixels[18] = 0x27; pngOverPixels[19] = 0x10;
        // 3000 = 0x0BB8
        pngOverPixels[20] = 0; pngOverPixels[21] = 0; pngOverPixels[22] = 0x0B; pngOverPixels[23] = (byte) 0xB8;
        IllegalArgumentException exPixels = assertThrows(IllegalArgumentException.class,
                () -> processor.process(new MockMultipartFile("image", "pixels.png", "image/png", pngOverPixels)));
        assertEquals("La foto es demasiado grande. Usá hasta 24 megapíxeles y 16384 píxeles por lado.", exPixels.getMessage());
    }

    @Test
    void testL1_Real24MP_DecodedAndMemoryMeasured() throws Exception {
        // 6000 x 4000 = exactos 24.000.000 píxeles
        int w = 6000;
        int h = 4000;
        BufferedImage bigImage = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = bigImage.createGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        g.fillRect(2000, 1000, 2000, 2000);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bigImage, "JPEG", baos);
        byte[] bigJpeg = baos.toByteArray();
        assertTrue(bigJpeg.length > 0 && bigJpeg.length <= 15 * 1024 * 1024);

        System.gc();
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        String url = processor.process(new MockMultipartFile("image", "24mp.jpg", "image/jpeg", bigJpeg));
        assertNotNull(url);

        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memDiffMb = (memAfter - memBefore) / (1024 * 1024);
        System.out.printf("[L1 MEMORY] Caso 24MP procesado exitosamente. Delta memoria estimada: %d MB. Salida: %s\n",
                memDiffMb, url);

        Path saved = uploadsRoot.resolve(url.substring("/media/".length()));
        assertTrue(Files.exists(saved));
        BufferedImage out = ImageIO.read(saved.toFile());
        assertEquals(800, out.getWidth());
        assertEquals(600, out.getHeight());
    }

    // ==========================================
    // O1: Orientaciones EXIF 1..8
    // ==========================================

    @Test
    void testO1_ExifOrientations1To8_DeterministicTransforms() throws Exception {
        // Creamos una imagen base 80x60 dividida en 4 cuadrantes con colores distintos:
        // Top-Left: ROJO, Top-Right: VERDE, Bottom-Left: AZUL, Bottom-Right: AMARILLO
        int w = 80;
        int h = 60;
        BufferedImage base = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x < w / 2 && y < h / 2) base.setRGB(x, y, 0xFF0000); // Rojo
                else if (x >= w / 2 && y < h / 2) base.setRGB(x, y, 0x00FF00); // Verde
                else if (x < w / 2 && y >= h / 2) base.setRGB(x, y, 0x0000FF); // Azul
                else base.setRGB(x, y, 0xFFFF00); // Amarillo
            }
        }

        // Probar las 8 orientaciones tanto con Little-Endian (II) como Big-Endian (MM)
        for (int ori = 1; ori <= 8; ori++) {
            for (boolean le : new boolean[]{true, false}) {
                byte[] tiff = makeTiff(ori, le);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(base, "PNG", baos);
                byte[] pngWithExif = injectPngChunk(baos.toByteArray(), "eXIf", tiff);

                PhotoProcessor.ContainerAnalysis analysis = processor.analyzeContainer(
                        writeTemp(pngWithExif), pngWithExif.length);
                assertEquals(ori, analysis.orientation(), "Orientación detectada no coincide para ori=" + ori + " le=" + le);

                BufferedImage revealed = processor.transformAndReveal(base, ori);
                assertEquals(800, revealed.getWidth());
                assertEquals(600, revealed.getHeight());
            }
        }
    }

    // ==========================================
    // G1: Geometría, proporciones, k=0
    // ==========================================

    @Test
    void testG1_CropAspectRatios_AndSmallRejection() throws Exception {
        // 1. Horizontal (1200 x 800) -> 800x600
        BufferedImage hImg = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
        BufferedImage resH = processor.transformAndReveal(hImg, 1);
        assertEquals(800, resH.getWidth());
        assertEquals(600, resH.getHeight());

        // 2. Vertical (800 x 1200) -> 800x600
        BufferedImage vImg = new BufferedImage(800, 1200, BufferedImage.TYPE_INT_RGB);
        BufferedImage resV = processor.transformAndReveal(vImg, 1);
        assertEquals(800, resV.getWidth());
        assertEquals(600, resV.getHeight());

        // 3. Cuadrada (1000 x 1000) -> 800x600
        BufferedImage sqImg = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
        BufferedImage resSq = processor.transformAndReveal(sqImg, 1);
        assertEquals(800, resSq.getWidth());
        assertEquals(600, resSq.getHeight());

        // 4. Dimensiones impares (1001 x 701) -> 800x600
        BufferedImage oddImg = new BufferedImage(1001, 701, BufferedImage.TYPE_INT_RGB);
        BufferedImage resOdd = processor.transformAndReveal(oddImg, 1);
        assertEquals(800, resOdd.getWidth());
        assertEquals(600, resOdd.getHeight());

        // 5. Pequeña con k=0 (e.g. 3 x 2) -> rechazada con 400
        BufferedImage tinyImg = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        IllegalArgumentException exTiny = assertThrows(IllegalArgumentException.class,
                () -> processor.transformAndReveal(tinyImg, 1));
        assertEquals("La foto es demasiado chica. Mandame una más grande.", exTiny.getMessage());
    }

    // ==========================================
    // C1: Fórmula de revelado y composición sobre blanco
    // ==========================================

    @Test
    void testC1_SubtleColorCorrection_AndAlphaOnWhite() throws Exception {
        // Imagen 4x3 con 4 píxeles de prueba:
        // (0,0): Negro puro (0, 0, 0)
        // (1,0): Blanco puro (255, 255, 255)
        // (2,0): Gris medio (128, 128, 128)
        // (0,1): Transparente puro (A=0, RGB=0)
        // (1,1): Rojo semitransparente (A=128, R=255, G=0, B=0)
        BufferedImage img = new BufferedImage(8, 6, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0, 0, 0, 255));
        g.fillRect(0, 0, 8, 6);
        img.setRGB(0, 0, new Color(0, 0, 0, 255).getRGB());
        img.setRGB(1, 0, new Color(255, 255, 255, 255).getRGB());
        img.setRGB(2, 0, new Color(128, 128, 128, 255).getRGB());
        img.setRGB(3, 0, new Color(0, 0, 0, 0).getRGB());
        g.dispose();

        // Aplicamos revelado directamente sobre una imagen de 400x300 con regiones uniformes
        BufferedImage testImg = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = testImg.createGraphics();
        // Cuadrante 1: Negro
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, 200, 150);
        // Cuadrante 2: Blanco
        g2.setColor(Color.WHITE);
        g2.fillRect(200, 0, 200, 150);
        // Cuadrante 3: Gris medio (128, 128, 128)
        g2.setColor(new Color(128, 128, 128));
        g2.fillRect(0, 150, 200, 150);
        // Cuadrante 4: Transparente (alfa = 0)
        g2.setColor(new Color(0, 0, 0, 0));
        g2.fillRect(200, 150, 200, 150);
        g2.dispose();

        BufferedImage revealed1 = processor.transformAndReveal(testImg, 1);
        BufferedImage revealed2 = processor.transformAndReveal(testImg, 1);

        // 1. Negro sigue negro: (0, 0, 0) (Cuadrante 1: X=0..400, Y=0..300)
        int rgbBlack = revealed1.getRGB(100, 100);
        assertEquals(0, (rgbBlack >> 16) & 0xFF, "R negro");
        assertEquals(0, (rgbBlack >> 8) & 0xFF, "G negro");
        assertEquals(0, rgbBlack & 0xFF, "B negro");

        // 2. Blanco satura a blanco: (255, 255, 255) (Cuadrante 2: X=400..800, Y=0..300)
        int rgbWhite = revealed1.getRGB(600, 100);
        assertEquals(255, (rgbWhite >> 16) & 0xFF, "R blanco");
        assertEquals(255, (rgbWhite >> 8) & 0xFF, "G blanco");
        assertEquals(255, rgbWhite & 0xFF, "B blanco");

        // 3. Gris medio: (Cuadrante 3: X=0..400, Y=300..600)
        // R' = clamp(round(128 * 1.03 * 1.01)) = 133
        // G' = clamp(round(128 * 1.03)) = 132
        // B' = clamp(round(128 * 1.03 * 0.99)) = 131
        int rgbGray = revealed1.getRGB(100, 450);
        assertEquals(133, (rgbGray >> 16) & 0xFF, "R gris medio");
        assertEquals(132, (rgbGray >> 8) & 0xFF, "G gris medio");
        assertEquals(131, rgbGray & 0xFF, "B gris medio");

        // 4. Alfa sobre blanco satura a blanco: (255, 255, 255) (Cuadrante 4: X=400..800, Y=300..600)
        int rgbAlpha = revealed1.getRGB(600, 450);
        assertEquals(255, (rgbAlpha >> 16) & 0xFF, "R alfa blanco");
        assertEquals(255, (rgbAlpha >> 8) & 0xFF, "G alfa blanco");
        assertEquals(255, rgbAlpha & 0xFF, "B alfa blanco");

        // 5. Determinismo estricto píxel a píxel entre dos ejecuciones
        for (int y = 0; y < 600; y += 50) {
            for (int x = 0; x < 800; x += 50) {
                assertEquals(revealed1.getRGB(x, y), revealed2.getRGB(x, y),
                        "Discrepancia no determinista en (" + x + "," + y + ")");
            }
        }
    }

    // ==========================================
    // Helpers para construcción de fixtures
    // ==========================================

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

    private byte[] makeTiff(int ori, boolean le) {
        ByteBuffer bb = ByteBuffer.allocate(32).order(le ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        bb.put((byte) (le ? 'I' : 'M')).put((byte) (le ? 'I' : 'M'));
        bb.putShort((short) 42);
        bb.putInt(8);
        bb.putShort((short) 1);
        bb.putShort((short) 0x0112); // Orientation tag
        bb.putShort((short) 3); // SHORT
        bb.putInt(1);
        bb.putShort((short) ori);
        bb.putShort((short) 0);
        bb.putInt(0);
        return bb.array();
    }

    private byte[] injectPngChunk(byte[] pngBytes, String chunkType, byte[] payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(pngBytes, 0, 8); // PNG signature

        int offset = 8;
        while (offset + 8 <= pngBytes.length) {
            int chunkLen = ((pngBytes[offset] & 0xFF) << 24) | ((pngBytes[offset + 1] & 0xFF) << 16) |
                           ((pngBytes[offset + 2] & 0xFF) << 8) | (pngBytes[offset + 3] & 0xFF);
            String type = new String(pngBytes, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);

            out.write(pngBytes, offset, 8 + chunkLen + 4);
            offset += 8 + chunkLen + 4;

            if ("IHDR".equals(type)) {
                // Inyectar inmediatamente después de IHDR
                ByteBuffer lenBuf = ByteBuffer.allocate(4).putInt(payload.length);
                out.write(lenBuf.array());
                out.write(chunkType.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                out.write(payload);
                // CRC dummy
                out.write(new byte[]{0, 0, 0, 0});
            }
        }
        return out.toByteArray();
    }

    private Path writeTemp(byte[] bytes) throws IOException {
        Path p = Files.createTempFile(tempDir, "sample-", ".tmp");
        Files.write(p, bytes);
        return p;
    }

    private long countFiles(Path dir) {
        if (!Files.exists(dir)) return 0;
        try (var stream = Files.list(dir)) {
            return stream.count();
        } catch (IOException e) {
            return 0;
        }
    }
}
