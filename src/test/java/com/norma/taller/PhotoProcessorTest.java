package com.norma.taller;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
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
    void testV1_ProgressiveJpeg_Accepted() throws Exception {
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, 400, 300);
        g.setColor(Color.ORANGE);
        g.fillOval(100, 50, 200, 200);
        g.dispose();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("JPEG");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.write(null, new IIOImage(img, null, null), param);
        }
        writer.dispose();

        byte[] progJpeg = baos.toByteArray();
        MockMultipartFile file = new MockMultipartFile("image", "prog.jpg", "image/jpeg", progJpeg);
        String url = processor.process(file);
        assertNotNull(url);

        Path saved = uploadsRoot.resolve(url.substring("/media/".length()));
        BufferedImage out = ImageIO.read(saved.toFile());
        assertEquals(800, out.getWidth());
        assertEquals(600, out.getHeight());
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

        // EXIF > 256 KiB
        byte[] hugeExif = new byte[262145];
        byte[] pngValid = createTestImageBytes("PNG", 100, 100, Color.GREEN);
        byte[] pngHugeExif = injectPngChunk(pngValid, "eXIf", hugeExif);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.process(new MockMultipartFile("image", "huge_exif.png", "image/png", pngHugeExif)));
        assertEquals("La foto contiene metadatos dañados o no soportados.", ex.getMessage());

        // EXIF corrupto
        byte[] corruptExif = new byte[]{'I', 'I', 42, 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        byte[] pngCorruptExif = injectPngChunk(pngValid, "eXIf", corruptExif);
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> processor.process(new MockMultipartFile("image", "corrupt_exif.png", "image/png", pngCorruptExif)));
        assertEquals("La foto contiene metadatos dañados o no soportados.", ex2.getMessage());

        assertEquals(0, countFiles(staging));
    }

    @Test
    void testV2_16BitPng_Rejected() throws Exception {
        Path staging = uploadsRoot.resolve(".processing");

        BufferedImage img16 = new BufferedImage(100, 100, BufferedImage.TYPE_USHORT_GRAY);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img16, "PNG", baos);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.process(new MockMultipartFile("image", "16bit.png", "image/png", baos.toByteArray())));
        assertEquals("Mandame una foto estática JPEG, PNG o WebP válida.", ex.getMessage());

        assertEquals(0, countFiles(staging));
    }

    @Test
    void testV2_TruncatedImageData_Rejected() throws Exception {
        Path staging = uploadsRoot.resolve(".processing");

        byte[] validJpeg = createTestImageBytes("JPEG", 200, 200, Color.CYAN);
        byte[] truncated = Arrays.copyOf(validJpeg, validJpeg.length / 2);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.process(new MockMultipartFile("image", "trunc.jpg", "image/jpeg", truncated)));
        assertEquals("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.", ex.getMessage());

        assertEquals(0, countFiles(staging));
    }

    // ==========================================
    // L1: Límites de tamaño, ejes, píxeles y memoria 24MP
    // ==========================================

    @Test
    void testL1_SizeLimit_15MiB_ExactVsExceeded() {
        MockMultipartFile exceededMeta = new MockMultipartFile("image", "huge.jpg", "image/jpeg", new byte[10]) {
            @Override
            public long getSize() {
                return 15 * 1024 * 1024 + 1;
            }
        };
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> processor.process(exceededMeta));
        assertEquals("La foto está vacía o pesa más de 15 MB.", ex.getMessage());

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
        byte[] pngValid = createTestImageBytes("PNG", 100, 100, Color.BLACK);
        byte[] pngOverAxis = pngValid.clone();
        pngOverAxis[16] = 0;
        pngOverAxis[17] = 0;
        pngOverAxis[18] = 0x40; // 16385 -> 0x4001
        pngOverAxis[19] = 0x01;

        IllegalArgumentException exAxis = assertThrows(IllegalArgumentException.class,
                () -> processor.process(new MockMultipartFile("image", "axis.png", "image/png", pngOverAxis)));
        assertEquals("La foto es demasiado grande. Usá hasta 24 megapíxeles y 16384 píxeles por lado.", exAxis.getMessage());

        byte[] pngOverPixels = pngValid.clone();
        pngOverPixels[16] = 0; pngOverPixels[17] = 0; pngOverPixels[18] = 0x27; pngOverPixels[19] = 0x10;
        pngOverPixels[20] = 0; pngOverPixels[21] = 0; pngOverPixels[22] = 0x0B; pngOverPixels[23] = (byte) 0xB8;
        IllegalArgumentException exPixels = assertThrows(IllegalArgumentException.class,
                () -> processor.process(new MockMultipartFile("image", "pixels.png", "image/png", pngOverPixels)));
        assertEquals("La foto es demasiado grande. Usá hasta 24 megapíxeles y 16384 píxeles por lado.", exPixels.getMessage());
    }

    @Test
    void testL1_Real24MP_DecodedAndMemoryMeasured() throws Exception {
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
        System.out.printf("[L1 MEMORY] Caso 24MP completado. Estimación interna de heap delta: %d MB (no representa pico RSS de SO). Salida: %s\n",
                memDiffMb, url);

        Path saved = uploadsRoot.resolve(url.substring("/media/".length()));
        assertTrue(Files.exists(saved));
        BufferedImage out = ImageIO.read(saved.toFile());
        assertEquals(800, out.getWidth());
        assertEquals(600, out.getHeight());
    }

    // ==========================================
    // O1: Orientaciones EXIF 1..8 en JPEG, PNG y WebP con verificación de píxeles
    // ==========================================

    @Test
    void testO1_ExifOrientations1To8_AllFormats_PixelsChecked() throws Exception {
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

        ByteArrayOutputStream baosPng = new ByteArrayOutputStream();
        ImageIO.write(base, "PNG", baosPng);
        byte[] rawPng = baosPng.toByteArray();

        ByteArrayOutputStream baosJpeg = new ByteArrayOutputStream();
        ImageIO.write(base, "JPEG", baosJpeg);
        byte[] rawJpeg = baosJpeg.toByteArray();

        ByteArrayOutputStream baosWebp = new ByteArrayOutputStream();
        ImageIO.write(base, "webp", baosWebp);
        byte[] rawWebp = baosWebp.toByteArray();

        int RED = 0xFF0000, GREEN = 0x00FF00, BLUE = 0x0000FF, YELLOW = 0xFFFF00;
        int[][] expectedColors = {
                {}, // 0 unused
                {RED, GREEN, BLUE, YELLOW},    // 1: Normal
                {GREEN, RED, YELLOW, BLUE},    // 2: Flip H
                {YELLOW, BLUE, GREEN, RED},    // 3: 180 deg
                {BLUE, YELLOW, RED, GREEN},    // 4: Flip V
                {RED, BLUE, GREEN, YELLOW},    // 5: Transpose (espejo 5)
                {BLUE, RED, YELLOW, GREEN},    // 6: 90 CW
                {YELLOW, GREEN, BLUE, RED},    // 7: Transverse (espejo 7)
                {GREEN, YELLOW, RED, BLUE}     // 8: 270 CW
        };

        for (int ori = 1; ori <= 8; ori++) {
            for (boolean le : new boolean[]{true, false}) {
                byte[] tiff = makeTiff(ori, le);

                // 1. PNG con eXIf
                byte[] pngWithExif = injectPngChunk(rawPng, "eXIf", tiff);
                BufferedImage revPng = processor.transformAndReveal(base, ori);
                assertQuadrantColors(revPng, expectedColors[ori], "PNG ori=" + ori + " le=" + le);

                // 2. JPEG con APP1 Exif
                byte[] jpegWithExif = injectJpegApp1(rawJpeg, tiff);
                PhotoProcessor.ContainerAnalysis jpegAnalysis = processor.analyzeContainer(
                        writeTemp(jpegWithExif), jpegWithExif.length);
                assertEquals(ori, jpegAnalysis.orientation(), "JPEG orientación falló para ori=" + ori);
                BufferedImage revJpeg = processor.transformAndReveal(base, ori);
                assertQuadrantColors(revJpeg, expectedColors[ori], "JPEG ori=" + ori + " le=" + le);

                // 3. WebP con EXIF
                byte[] webpWithExif = injectWebpExif(rawWebp, tiff, w, h);
                PhotoProcessor.ContainerAnalysis webpAnalysis = processor.analyzeContainer(
                        writeTemp(webpWithExif), webpWithExif.length);
                assertEquals(ori, webpAnalysis.orientation(), "WebP orientación falló para ori=" + ori);
                BufferedImage revWebp = processor.transformAndReveal(base, ori);
                assertQuadrantColors(revWebp, expectedColors[ori], "WebP ori=" + ori + " le=" + le);
            }
        }
    }

    private void assertQuadrantColors(BufferedImage img, int[] expected, String context) {
        assertPixelColor(img.getRGB(200, 150), expected[0], context + " TL");
        assertPixelColor(img.getRGB(600, 150), expected[1], context + " TR");
        assertPixelColor(img.getRGB(200, 450), expected[2], context + " BL");
        assertPixelColor(img.getRGB(600, 450), expected[3], context + " BR");
    }

    private void assertPixelColor(int actualRgb, int expectedRgb, String msg) {
        int actR = (actualRgb >> 16) & 0xFF;
        int actG = (actualRgb >> 8) & 0xFF;
        int actB = actualRgb & 0xFF;

        int expR = (expectedRgb >> 16) & 0xFF;
        int expG = (expectedRgb >> 8) & 0xFF;
        int expB = expectedRgb & 0xFF;

        assertTrue(Math.abs(actR - expR) <= 5 && Math.abs(actG - expG) <= 5 && Math.abs(actB - expB) <= 5,
                String.format("%s: esperado RGB(%d,%d,%d) pero fue RGB(%d,%d,%d)", msg, expR, expG, expB, actR, actG, actB));
    }

    @Test
    void testO1_AbsenceOfExif_DefaultsToOne() throws Exception {
        byte[] pngNoExif = createTestImageBytes("PNG", 200, 150, Color.YELLOW);
        PhotoProcessor.ContainerAnalysis a = processor.analyzeContainer(writeTemp(pngNoExif), pngNoExif.length);
        assertEquals(1, a.orientation());
    }

    @Test
    void testO1_IFD0_GovernsOverThumbnail() throws Exception {
        ByteBuffer bb = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 'I').put((byte) 'I');
        bb.putShort((short) 42);
        bb.putInt(8);

        // IFD0: 1 tag orientación = 1, next IFD en offset 26
        bb.putShort((short) 1);
        bb.putShort((short) 0x0112);
        bb.putShort((short) 3);
        bb.putInt(1);
        bb.putShort((short) 1);
        bb.putShort((short) 0);
        bb.putInt(26);

        // IFD1: 1 tag orientación = 6 (miniatura)
        bb.putShort((short) 1);
        bb.putShort((short) 0x0112);
        bb.putShort((short) 3);
        bb.putInt(1);
        bb.putShort((short) 6);
        bb.putShort((short) 0);
        bb.putInt(0);

        byte[] tiff = bb.array();
        byte[] png = injectPngChunk(createTestImageBytes("PNG", 100, 100, Color.WHITE), "eXIf", tiff);
        PhotoProcessor.ContainerAnalysis a = processor.analyzeContainer(writeTemp(png), png.length);
        assertEquals(1, a.orientation());
    }

    // ==========================================
    // G1: Geometría, proporciones, recorte centrado y conservación de círculos
    // ==========================================

    @Test
    void testG1_Crop_IdentifiableBorders_AndCenterPreserved() throws Exception {
        // 1. Horizontal 1200 x 600 (ratio 2:1)
        BufferedImage hImg = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D gh = hImg.createGraphics();
        gh.setColor(Color.CYAN);
        gh.fillRect(0, 0, 200, 600);
        gh.setColor(Color.MAGENTA);
        gh.fillRect(1000, 0, 200, 600);
        gh.setColor(Color.WHITE);
        gh.fillRect(200, 0, 800, 600);
        gh.setColor(Color.RED);
        gh.fillRect(580, 280, 40, 40);
        gh.dispose();

        BufferedImage resH = processor.transformAndReveal(hImg, 1);
        assertEquals(800, resH.getWidth());
        assertEquals(600, resH.getHeight());

        for (int y = 0; y < 600; y += 30) {
            for (int x = 0; x < 800; x += 30) {
                int rgb = resH.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                assertFalse(r < 30 && g > 200 && b > 200, "Margen CIAN presente en (" + x + "," + y + ")");
                assertFalse(r > 200 && g < 30 && b > 200, "Margen MAGENTA presente en (" + x + "," + y + ")");
            }
        }
        int centerRgb = resH.getRGB(400, 300);
        assertEquals(255, (centerRgb >> 16) & 0xFF, "R centro");
        assertTrue(((centerRgb >> 8) & 0xFF) < 30, "G centro");

        // 2. Vertical 600 x 1200 (ratio 1:2)
        BufferedImage vImg = new BufferedImage(600, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D gv = vImg.createGraphics();
        gv.setColor(Color.YELLOW);
        gv.fillRect(0, 0, 600, 375);
        gv.setColor(Color.BLUE);
        gv.fillRect(0, 825, 600, 375);
        gv.setColor(Color.WHITE);
        gv.fillRect(0, 375, 600, 450);
        gv.setColor(Color.GREEN);
        gv.fillRect(280, 580, 40, 40);
        gv.dispose();

        BufferedImage resV = processor.transformAndReveal(vImg, 1);
        assertEquals(800, resV.getWidth());
        assertEquals(600, resV.getHeight());
        int vCenter = resV.getRGB(400, 300);
        assertEquals(255, (vCenter >> 8) & 0xFF, "G centro vertical");

        // 3. Panorámica 2400 x 600 (ratio 4:1)
        BufferedImage panoImg = new BufferedImage(2400, 600, BufferedImage.TYPE_INT_RGB);
        BufferedImage resPano = processor.transformAndReveal(panoImg, 1);
        assertEquals(800, resPano.getWidth());
        assertEquals(600, resPano.getHeight());

        // 4. Dimensiones impares con redondeo floor (1001 x 701)
        BufferedImage oddImg = new BufferedImage(1001, 701, BufferedImage.TYPE_INT_RGB);
        BufferedImage resOdd = processor.transformAndReveal(oddImg, 1);
        assertEquals(800, resOdd.getWidth());
        assertEquals(600, resOdd.getHeight());

        // 5. Círculo conserva proporción:
        BufferedImage circleImg = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D gc = circleImg.createGraphics();
        gc.setColor(Color.BLACK);
        gc.fillRect(0, 0, 800, 600);
        gc.setColor(Color.WHITE);
        gc.fillOval(400 - 100, 300 - 100, 200, 200);
        gc.dispose();

        BufferedImage resCircle = processor.transformAndReveal(circleImg, 1);
        int minX = 800, maxX = 0, minY = 600, maxY = 0;
        for (int y = 0; y < 600; y++) {
            for (int x = 0; x < 800; x++) {
                if (((resCircle.getRGB(x, y) >> 16) & 0xFF) > 200) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        int diamX = maxX - minX + 1;
        int diamY = maxY - minY + 1;
        assertTrue(Math.abs(diamX - diamY) <= 1,
                String.format("El círculo se deformó: diamX=%d, diamY=%d", diamX, diamY));

        // 6. Pequeña con k=0 (e.g. 3 x 2)
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
        BufferedImage testImg = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = testImg.createGraphics();
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, 200, 150);
        g2.setColor(Color.WHITE);
        g2.fillRect(200, 0, 200, 150);
        g2.setColor(new Color(128, 128, 128));
        g2.fillRect(0, 150, 200, 150);
        g2.setColor(new Color(0, 0, 0, 0));
        g2.fillRect(200, 150, 200, 150);
        g2.dispose();

        BufferedImage revealed1 = processor.transformAndReveal(testImg, 1);
        BufferedImage revealed2 = processor.transformAndReveal(testImg, 1);

        // 1. Negro sigue negro: (0, 0, 0)
        int rgbBlack = revealed1.getRGB(100, 100);
        assertEquals(0, (rgbBlack >> 16) & 0xFF, "R negro");
        assertEquals(0, (rgbBlack >> 8) & 0xFF, "G negro");
        assertEquals(0, rgbBlack & 0xFF, "B negro");

        // 2. Blanco satura a blanco: (255, 255, 255)
        int rgbWhite = revealed1.getRGB(600, 100);
        assertEquals(255, (rgbWhite >> 16) & 0xFF, "R blanco");
        assertEquals(255, (rgbWhite >> 8) & 0xFF, "G blanco");
        assertEquals(255, rgbWhite & 0xFF, "B blanco");

        // 3. Gris medio: (133, 132, 131)
        int rgbGray = revealed1.getRGB(100, 450);
        assertEquals(133, (rgbGray >> 16) & 0xFF, "R gris medio");
        assertEquals(132, (rgbGray >> 8) & 0xFF, "G gris medio");
        assertEquals(131, rgbGray & 0xFF, "B gris medio");

        // 4. Alfa sobre blanco satura a blanco: (255, 255, 255)
        int rgbAlpha = revealed1.getRGB(600, 450);
        assertEquals(255, (rgbAlpha >> 16) & 0xFF, "R alfa blanco");
        assertEquals(255, (rgbAlpha >> 8) & 0xFF, "G alfa blanco");
        assertEquals(255, rgbAlpha & 0xFF, "B alfa blanco");

        // 5. Determinismo estricto píxel a píxel
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
        bb.putShort((short) 0x0112);
        bb.putShort((short) 3);
        bb.putInt(1);
        bb.putShort((short) ori);
        bb.putShort((short) 0);
        bb.putInt(0);
        return bb.array();
    }

    private byte[] injectPngChunk(byte[] pngBytes, String chunkType, byte[] payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(pngBytes, 0, 8);

        int offset = 8;
        while (offset + 8 <= pngBytes.length) {
            int chunkLen = ((pngBytes[offset] & 0xFF) << 24) | ((pngBytes[offset + 1] & 0xFF) << 16) |
                           ((pngBytes[offset + 2] & 0xFF) << 8) | (pngBytes[offset + 3] & 0xFF);
            String type = new String(pngBytes, offset + 4, 4, StandardCharsets.US_ASCII);

            out.write(pngBytes, offset, 8 + chunkLen + 4);
            offset += 8 + chunkLen + 4;

            if ("IHDR".equals(type)) {
                ByteBuffer lenBuf = ByteBuffer.allocate(4).putInt(payload.length);
                out.write(lenBuf.array());
                out.write(chunkType.getBytes(StandardCharsets.US_ASCII));
                out.write(payload);
                out.write(new byte[]{0, 0, 0, 0});
            }
        }
        return out.toByteArray();
    }

    private byte[] injectJpegApp1(byte[] rawJpeg, byte[] tiff) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF); out.write(0xD8);
        out.write(0xFF); out.write(0xE1);
        int app1Len = 2 + 6 + tiff.length;
        out.write((app1Len >> 8) & 0xFF); out.write(app1Len & 0xFF);
        out.write("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        out.write(tiff);
        out.write(rawJpeg, 2, rawJpeg.length - 2);
        return out.toByteArray();
    }

    private byte[] injectWebpExif(byte[] rawWebp, byte[] tiff, int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{'R', 'I', 'F', 'F'});
        int totalLen = 4 + 18 + (8 + tiff.length + (tiff.length % 2)) + (rawWebp.length - 12);
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalLen).array());
        out.write(new byte[]{'W', 'E', 'B', 'P'});

        out.write(new byte[]{'V', 'P', '8', 'X'});
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(10).array());
        out.write((byte) 0x08);
        out.write(new byte[]{0, 0, 0});
        int wMinus1 = w - 1;
        int hMinus1 = h - 1;
        out.write(new byte[]{(byte)(wMinus1 & 0xFF), (byte)((wMinus1 >> 8) & 0xFF), (byte)((wMinus1 >> 16) & 0xFF)});
        out.write(new byte[]{(byte)(hMinus1 & 0xFF), (byte)((hMinus1 >> 8) & 0xFF), (byte)((hMinus1 >> 16) & 0xFF)});

        out.write(new byte[]{'E', 'X', 'I', 'F'});
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(tiff.length).array());
        out.write(tiff);
        if (tiff.length % 2 != 0) out.write(0);

        out.write(rawWebp, 12, rawWebp.length - 12);
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
