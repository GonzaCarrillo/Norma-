package com.norma.taller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DemoMediaTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("groq.api.key", () -> "");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite::memory:");
    }

    private static final Map<String, ExpectedAsset> EXPECTED_WEBP = Map.of(
            "pieza-1.webp", new ExpectedAsset(52328, "365d0360f061b84a4e4fa6e7dd4ca85231666a8d67c796bdb0aa7b4f27fb37a7"),
            "pieza-2.webp", new ExpectedAsset(10988, "c15195fb60a6c4707710b4833d249c33cc99ed588fa8767e998ed4c23730972f"),
            "pieza-3.webp", new ExpectedAsset(15054, "ab8b44ac2dc020b58ee8405635ce7bdd921287fa1446da058f2d1d41795042a1"),
            "pieza-4.webp", new ExpectedAsset(29792, "fc0abb484a8622db9e4265f30b282ca0a3a27978305734f5782defc0257ddf6d"),
            "pieza-5.webp", new ExpectedAsset(38426, "e07db765feefe119ed51d5418faa70727c4498675a84007fe6a2b2b608b98f2f"),
            "pieza-6.webp", new ExpectedAsset(9036, "99d98672c9e6ab2749a92033bcf2f7271aa3841426bba841dfe928c626a10e91"),
            "pieza-7.webp", new ExpectedAsset(13604, "6bb6ec6d24d6f5af9f4a886698e2a82c1157e528607c0c45e31a6595efd3bd4e"),
            "pieza-8.webp", new ExpectedAsset(27104, "0dd65b5c297d46af98b6194b356350876a2bff83d0231c4bae4785237ba522bc")
    );

    private static final Map<String, ExpectedAsset> EXPECTED_MP3 = Map.of(
            "demo-create.mp3", new ExpectedAsset(18720, "25bfcb4eeb43a6dfe1f671bc368f8b64cf85852f32ba0d2efc90b913465b7c15"),
            "demo-image.mp3", new ExpectedAsset(21888, "aa435c531920ef9b9eb5dc229509910b85ad6d12b22d96ca56c5697162fcec46"),
            "demo-price.mp3", new ExpectedAsset(21168, "e83836d7077b6c69ec40a2884b66e0fa1ee203ff1df78cace88aa30dc8ba1a6a"),
            "demo-sold.mp3", new ExpectedAsset(14544, "0da71c5037e881f52cc87419e2bf82cc4d3b6684fdcb05429de5c5af7638dbcd")
    );

    private record ExpectedAsset(long size, String sha256) {}

    @Test
    void testD1_AllEightWebpAssets_InventoryAndSpecCompliance() throws Exception {
        for (int i = 1; i <= 8; i++) {
            String filename = "pieza-" + i + ".webp";
            ExpectedAsset expected = EXPECTED_WEBP.get(filename);
            assertNotNull(expected, "Asset no inventariado: " + filename);

            ClassPathResource resource = new ClassPathResource("static/assets/" + filename);
            assertTrue(resource.exists(), "Falta recurso en classpath: " + filename);

            byte[] b;
            try (InputStream in = resource.getInputStream()) {
                b = in.readAllBytes();
            }

            // 1. Verificación de tamaño y hash exactos
            assertEquals(expected.size(), b.length, "Tamaño no coincide para " + filename);
            assertTrue(b.length < 80000, "Tamaño excede los 80.000 bytes para " + filename);
            String actualSha256 = sha256Hex(b);
            assertEquals(expected.sha256(), actualSha256, "SHA-256 no coincide para " + filename);

            // 2. Verificación de contenedor RIFF/WEBP
            assertTrue(b.length >= 12);
            assertEquals('R', (char) b[0]);
            assertEquals('I', (char) b[1]);
            assertEquals('F', (char) b[2]);
            assertEquals('F', (char) b[3]);
            long riffLen = (b[4] & 0xFFL) | ((b[5] & 0xFFL) << 8) | ((b[6] & 0xFFL) << 16) | ((b[7] & 0xFFL) << 24);
            assertEquals(b.length, riffLen + 8, "Longitud declarada RIFF inconsistente en " + filename);
            assertEquals('W', (char) b[8]);
            assertEquals('E', (char) b[9]);
            assertEquals('B', (char) b[10]);
            assertEquals('P', (char) b[11]);

            // 3. Inspección estricta de chunks RIFF
            int offset = 12;
            boolean foundVp8 = false;
            while (offset < b.length) {
                assertTrue(offset + 8 <= b.length, "Truncamiento de cabecera de chunk en " + filename);
                String fourCC = new String(b, offset, 4, StandardCharsets.US_ASCII);
                long chunkLen = (b[offset + 4] & 0xFFL) | ((b[offset + 5] & 0xFFL) << 8) |
                                ((b[offset + 6] & 0xFFL) << 16) | ((b[offset + 7] & 0xFFL) << 24);
                long padByte = chunkLen % 2;
                assertTrue(offset + 8 + chunkLen + padByte <= b.length, "Chunk desborda archivo en " + filename);

                assertFalse("EXIF".equals(fourCC), "Metadatos EXIF no permitidos en " + filename);
                assertFalse("XMP ".equals(fourCC), "Metadatos XMP no permitidos en " + filename);
                assertFalse("ICCP".equals(fourCC), "Perfil ICC no permitido en " + filename);
                assertFalse("ANIM".equals(fourCC), "Animación no permitida en " + filename);
                assertFalse("ANMF".equals(fourCC), "Frame de animación no permitido en " + filename);

                if ("VP8 ".equals(fourCC)) {
                    foundVp8 = true;
                } else if ("VP8X".equals(fourCC)) {
                    assertTrue(chunkLen >= 10);
                    int flags = b[offset + 8] & 0xFF;
                    assertEquals(0, flags & 0x02, "Flag de animación activado en VP8X de " + filename);
                    assertEquals(0, flags & 0x08, "Flag de EXIF activado en VP8X de " + filename);
                    assertEquals(0, flags & 0x04, "Flag de XMP activado en VP8X de " + filename);
                    assertEquals(0, flags & 0x20, "Flag de ICC activado en VP8X de " + filename);
                }

                offset += (int) (8 + chunkLen + padByte);
            }
            assertEquals(b.length, offset, "Cursor final no coincide con fin de archivo en " + filename);
            assertTrue(foundVp8, "No se encontró chunk VP8 lossy en " + filename);

            // 4. Decodificación y dimensiones exactas 800x600
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(b));
            assertNotNull(img, "ImageIO no pudo decodificar " + filename);
            assertEquals(800, img.getWidth(), "Ancho debe ser 800 para " + filename);
            assertEquals(600, img.getHeight(), "Alto debe ser 600 para " + filename);
        }
    }

    @Test
    void testD1_AllFourMp3Assets_InventoryAndSpecCompliance() throws Exception {
        for (String filename : EXPECTED_MP3.keySet()) {
            ExpectedAsset expected = EXPECTED_MP3.get(filename);
            ClassPathResource resource = new ClassPathResource("static/assets/" + filename);
            assertTrue(resource.exists(), "Falta recurso en classpath: " + filename);

            byte[] b;
            try (InputStream in = resource.getInputStream()) {
                b = in.readAllBytes();
            }

            assertEquals(expected.size(), b.length, "Tamaño no coincide para " + filename);
            String actualSha256 = sha256Hex(b);
            assertEquals(expected.sha256(), actualSha256, "SHA-256 no coincide para " + filename);

            // Verificar cabecera MPEG/ID3 válida
            assertTrue(b.length > 10, "Archivo MP3 demasiado corto: " + filename);
            boolean isId3 = b[0] == 'I' && b[1] == 'D' && b[2] == '3';
            boolean isMpegFrameSync = (b[0] & 0xFF) == 0xFF && (b[1] & 0xE0) == 0xE0;
            assertTrue(isId3 || isMpegFrameSync, "Cabecera de audio MPEG/ID3 inválida en " + filename);
        }
    }

    @Test
    void testD1_HttpServing_AllWebpAndMp3Assets() throws Exception {
        for (int i = 1; i <= 8; i++) {
            mockMvc.perform(get("/assets/pieza-" + i + ".webp"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", startsWith("image/webp")));
        }

        for (String filename : EXPECTED_MP3.keySet()) {
            mockMvc.perform(get("/assets/" + filename))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", startsWith("audio/mpeg")));
        }
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data);
        return HexFormat.of().formatHex(digest);
    }
}
