package com.norma.taller;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.web.multipart.MultipartFile;
import com.drew.lang.ByteArrayReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifReader;

class PhotoProcessor {
    static final long MAX_FILE_SIZE = 15 * 1024 * 1024; // 15 MiB
    static final int MAX_AXIS = 16384;
    static final long MAX_PIXELS = 24000000L;
    static final int MAX_EXIF_BYTES = 262144; // 256 KiB
    static final int TARGET_WIDTH = 800;
    static final int TARGET_HEIGHT = 600;
    static final int TARGET_MAX_BYTES = 80000;
    static final int[] QUALITIES = {80, 70, 60, 50, 40, 30, 20, 10};

    private final Path root;
    private final Path stagingDir;
    private final Semaphore semaphore = new Semaphore(1);
    private final WebPEncoder encoder;

    @FunctionalInterface
    interface WebPEncoder {
        void encode(BufferedImage img, float quality, Path dest) throws IOException;
    }

    PhotoProcessor(Path root) {
        this(root, new DefaultWebPEncoder());
    }

    PhotoProcessor(Path root, WebPEncoder encoder) {
        this.root = root.toAbsolutePath().normalize();
        this.stagingDir = this.root.resolve(".processing").toAbsolutePath().normalize();
        this.encoder = encoder != null ? encoder : new DefaultWebPEncoder();
        try {
            Files.createDirectories(this.stagingDir);
            Files.createDirectories(this.root);
            Path realRoot = this.root.toRealPath();
            Path realStaging = this.stagingDir.toRealPath();
            if (!realStaging.startsWith(realRoot) || Files.isSymbolicLink(this.stagingDir) || Files.isSymbolicLink(this.root)) {
                throw new IOException("Ruta de staging o root insegura o symlink no permitido");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo inicializar directorios de medios", e);
        }
    }

    String process(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("La foto está vacía o pesa más de 15 MB.");
        }

        if (!semaphore.tryAcquire()) {
            throw new IOException("Servidor ocupado procesando otra imagen.");
        }

        List<Path> temps = new ArrayList<>();
        Throwable mainError = null;
        try {
            Path tempInput = Files.createTempFile(stagingDir, "upload-", ".tmp");
            temps.add(tempInput);

            // Copiar controlando streaming hasta MAX_FILE_SIZE + 1
            long totalBytes = 0;
            byte[] buf = new byte[8192];
            try (InputStream in = file.getInputStream();
                 OutputStream out = Files.newOutputStream(tempInput)) {
                int read;
                while ((read = in.read(buf)) != -1) {
                    totalBytes += read;
                    if (totalBytes > MAX_FILE_SIZE) {
                        throw new IllegalArgumentException("La foto está vacía o pesa más de 15 MB.");
                    }
                    out.write(buf, 0, read);
                }
            }
            if (totalBytes == 0) {
                throw new IllegalArgumentException("La foto está vacía o pesa más de 15 MB.");
            }

            // Analizar contenedor: firma, animación y EXIF estricto
            ContainerAnalysis analysis = analyzeContainer(tempInput, totalBytes);

            // Validar cabecera, dimensiones y canales antes de decodificar raster
            BufferedImage sourceImage = readAndValidateSource(tempInput, analysis);

            // Transformar: recorte 4:3 determinista, escala y orientación EXIF 1..8
            BufferedImage revealedImage = transformAndReveal(sourceImage, analysis.orientation);

            // Codificar con WebP provider en hasta 8 intentos
            String finalName = encodeAndPublish(revealedImage, temps);
            return "/media/" + finalName;
        } catch (Throwable t) {
            mainError = t;
            throw t;
        } finally {
            semaphore.release();
            IOException cleanupError = null;
            for (Path p : temps) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    if (cleanupError == null) cleanupError = e;
                    else cleanupError.addSuppressed(e);
                }
            }
            if (cleanupError != null) {
                org.slf4j.LoggerFactory.getLogger(PhotoProcessor.class).error("Fallo al limpiar temporales de medios", cleanupError);
                if (mainError != null) {
                    mainError.addSuppressed(cleanupError);
                } else {
                    throw cleanupError;
                }
            }
        }
    }

    static record ContainerAnalysis(String format, int orientation) {}

    ContainerAnalysis analyzeContainer(Path path, long totalBytes) throws IOException {
        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IOException("Fallo de I/O al leer archivo temporal", e);
        }

        if (fileBytes.length < 12) {
            throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.");
        }

        // 1. JPEG
        if ((fileBytes[0] & 0xFF) == 0xFF && (fileBytes[1] & 0xFF) == 0xD8 && (fileBytes[2] & 0xFF) == 0xFF) {
            int orientation = parseJpeg(fileBytes);
            return new ContainerAnalysis("JPEG", orientation);
        }

        // 2. PNG
        if ((fileBytes[0] & 0xFF) == 0x89 && fileBytes[1] == 'P' && fileBytes[2] == 'N' && fileBytes[3] == 'G' &&
            fileBytes[4] == 0x0D && fileBytes[5] == 0x0A && fileBytes[6] == 0x1A && fileBytes[7] == 0x0A) {
            int orientation = parsePng(fileBytes);
            return new ContainerAnalysis("PNG", orientation);
        }

        // 3. WebP
        if (fileBytes[0] == 'R' && fileBytes[1] == 'I' && fileBytes[2] == 'F' && fileBytes[3] == 'F' &&
            fileBytes[8] == 'W' && fileBytes[9] == 'E' && fileBytes[10] == 'B' && fileBytes[11] == 'P') {
            int orientation = parseWebP(fileBytes);
            return new ContainerAnalysis("WebP", orientation);
        }

        throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.");
    }

    private int parseJpeg(byte[] b) {
        int offset = 2;
        int totalExifBytes = 0;
        Integer orientation = null;

        while (offset + 1 < b.length) {
            if ((b[offset] & 0xFF) != 0xFF) {
                throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.");
            }
            while (offset < b.length && (b[offset] & 0xFF) == 0xFF) {
                offset++;
            }
            if (offset >= b.length) {
                break;
            }
            int marker = b[offset++] & 0xFF;
            if (marker == 0xD9 || marker == 0xDA) { // EOI or SOS
                break;
            }
            if (marker >= 0xD0 && marker <= 0xD8) { // RST / SOI
                continue;
            }
            if (offset + 2 > b.length) {
                throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.");
            }
            int length = ((b[offset] & 0xFF) << 8) | (b[offset + 1] & 0xFF);
            if (length < 2 || offset + length > b.length) {
                throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.");
            }

            if (marker == 0xE1) { // APP1
                int payloadLen = length - 2;
                int payloadStart = offset + 2;
                if (payloadLen >= 6 && b[payloadStart] == 'E' && b[payloadStart + 1] == 'x' &&
                    b[payloadStart + 2] == 'i' && b[payloadStart + 3] == 'f' &&
                    b[payloadStart + 4] == 0 && b[payloadStart + 5] == 0) {
                    totalExifBytes += payloadLen;
                    if (totalExifBytes > MAX_EXIF_BYTES) {
                        throw new IllegalArgumentException("La foto contiene metadatos dañados o no soportados.");
                    }
                    byte[] exifData = Arrays.copyOfRange(b, payloadStart, payloadStart + payloadLen);
                    int ori = extractExifOrientation(exifData, 6);
                    if (ori != 1) {
                        if (orientation != null && orientation != ori) {
                            throw new IllegalArgumentException("La foto contiene metadatos dañados o no soportados.");
                        }
                        orientation = ori;
                    }
                }
            }
            offset += length;
        }

        return orientation != null ? orientation : 1;
    }

    private int parsePng(byte[] b) {
        int offset = 8;
        int totalExifBytes = 0;
        Integer orientation = null;

        while (offset + 8 <= b.length) {
            int chunkLen = ((b[offset] & 0xFF) << 24) | ((b[offset + 1] & 0xFF) << 16) |
                           ((b[offset + 2] & 0xFF) << 8) | (b[offset + 3] & 0xFF);
            offset += 4;
            if (chunkLen < 0 || offset + 4 + chunkLen + 4 > b.length) {
                throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.");
            }
            String chunkType = new String(b, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            offset += 4;

            if ("acTL".equals(chunkType)) {
                throw new IllegalArgumentException("Mandame una foto estática JPEG, PNG o WebP válida.");
            }
            if ("eXIf".equals(chunkType)) {
                totalExifBytes += chunkLen;
                if (totalExifBytes > MAX_EXIF_BYTES) {
                    throw new IllegalArgumentException("La foto contiene metadatos dañados o no soportados.");
                }
                byte[] exifData = Arrays.copyOfRange(b, offset, offset + chunkLen);
                int ori = extractExifOrientation(exifData, 0);
                if (ori != 1) {
                    if (orientation != null && orientation != ori) {
                        throw new IllegalArgumentException("La foto contiene metadatos dañados o no soportados.");
                    }
                    orientation = ori;
                }
            }
            if ("IEND".equals(chunkType)) {
                break;
            }
            offset += chunkLen + 4; // payload + CRC
        }

        return orientation != null ? orientation : 1;
    }

    private int parseWebP(byte[] b) {
        int offset = 12;
        int totalExifBytes = 0;
        Integer orientation = null;

        while (offset + 8 <= b.length) {
            String fourCC = new String(b, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int chunkLen = (b[offset + 4] & 0xFF) | ((b[offset + 5] & 0xFF) << 8) |
                           ((b[offset + 6] & 0xFF) << 16) | ((b[offset + 7] & 0xFF) << 24);
            offset += 8;
            if (chunkLen < 0 || offset + chunkLen > b.length) {
                throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.");
            }

            if ("VP8X".equals(fourCC)) {
                if (chunkLen < 10) {
                    throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.");
                }
                int flags = b[offset] & 0xFF;
                if ((flags & 0x02) != 0) { // Bit de animación
                    throw new IllegalArgumentException("Mandame una foto estática JPEG, PNG o WebP válida.");
                }
            } else if ("ANIM".equals(fourCC) || "ANMF".equals(fourCC)) {
                throw new IllegalArgumentException("Mandame una foto estática JPEG, PNG o WebP válida.");
            } else if ("EXIF".equals(fourCC)) {
                totalExifBytes += chunkLen;
                if (totalExifBytes > MAX_EXIF_BYTES) {
                    throw new IllegalArgumentException("La foto contiene metadatos dañados o no soportados.");
                }
                byte[] exifData = Arrays.copyOfRange(b, offset, offset + chunkLen);
                int tiffOffset = 0;
                if (exifData.length >= 6 && exifData[0] == 'E' && exifData[1] == 'x' &&
                    exifData[2] == 'i' && exifData[3] == 'f' && exifData[4] == 0 && exifData[5] == 0) {
                    tiffOffset = 6;
                }
                int ori = extractExifOrientation(exifData, tiffOffset);
                if (ori != 1) {
                    if (orientation != null && orientation != ori) {
                        throw new IllegalArgumentException("La foto contiene metadatos dañados o no soportados.");
                    }
                    orientation = ori;
                }
            }

            int padded = (chunkLen + 1) & ~1;
            offset += padded;
        }

        return orientation != null ? orientation : 1;
    }

    private int extractExifOrientation(byte[] exifData, int tiffOffset) {
        Metadata metadata = new Metadata();
        ExifReader reader = new ExifReader();
        try {
            reader.extract(new ByteArrayReader(exifData), metadata, tiffOffset);
        } catch (Exception e) {
            throw new IllegalArgumentException("La foto contiene metadatos dañados o no soportados.");
        }

        Integer detected = null;
        for (Directory dir : metadata.getDirectoriesOfType(ExifIFD0Directory.class)) {
            if (dir.hasErrors()) {
                throw new IllegalArgumentException("La foto contiene metadatos dañados o no soportados.");
            }
            if (dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                try {
                    int ori = dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                    if (ori < 1 || ori > 8) {
                        throw new IllegalArgumentException("La foto contiene metadatos dañados o no soportados.");
                    }
                    if (detected != null && detected != ori) {
                        throw new IllegalArgumentException("La foto contiene metadatos dañados o no soportados.");
                    }
                    detected = ori;
                } catch (Exception e) {
                    throw new IllegalArgumentException("La foto contiene metadatos dañados o no soportados.");
                }
            }
        }
        return detected != null ? detected : 1;
    }

    private BufferedImage readAndValidateSource(Path tempInput, ContainerAnalysis analysis) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(tempInput.toFile())) {
            if (iis == null) {
                throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                boolean[] decodeWarning = new boolean[1];
                reader.addIIOReadWarningListener((source, warning) -> decodeWarning[0] = true);
                String fmt = reader.getFormatName();
                if (!isFormatAllowed(fmt)) {
                    throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.");
                }

                int rawW;
                int rawH;
                try {
                    rawW = reader.getWidth(0);
                    rawH = reader.getHeight(0);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.");
                }

                if (rawW <= 0 || rawH <= 0 || rawW > MAX_AXIS || rawH > MAX_AXIS || (long) rawW * rawH > MAX_PIXELS) {
                    throw new IllegalArgumentException("La foto es demasiado grande. Usá hasta 24 megapíxeles y 16384 píxeles por lado.");
                }

                int ori = analysis.orientation;
                int wo = (ori >= 5) ? rawH : rawW;
                int ho = (ori >= 5) ? rawW : rawH;
                int k = Math.min(wo / 4, ho / 3);
                if (k == 0) {
                    throw new IllegalArgumentException("La foto es demasiado chica. Mandame una más grande.");
                }

                BufferedImage rawImage;
                try {
                    rawImage = reader.read(0);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.");
                }
                if (rawImage == null || decodeWarning[0]) {
                    throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.");
                }

                ColorModel cm = rawImage.getColorModel();
                if (cm != null) {
                    if (cm.getColorSpace().getType() == ColorSpace.TYPE_CMYK) {
                        throw new IllegalArgumentException("Mandame una foto estática JPEG, PNG o WebP válida.");
                    }
                    for (int size : cm.getComponentSize()) {
                        if (size > 8) {
                            throw new IllegalArgumentException("Mandame una foto estática JPEG, PNG o WebP válida.");
                        }
                    }
                }

                return rawImage;
            } finally {
                reader.dispose();
            }
        }
    }

    private boolean isFormatAllowed(String format) {
        if (format == null) return false;
        String f = format.trim().toUpperCase(Locale.ROOT);
        return f.equals("JPEG") || f.equals("JPG") || f.equals("PNG") || f.equals("WEBP");
    }

    BufferedImage transformAndReveal(BufferedImage rawImage, int ori) throws IOException {
        int rawW = rawImage.getWidth();
        int rawH = rawImage.getHeight();
        int wo = (ori >= 5) ? rawH : rawW;
        int ho = (ori >= 5) ? rawW : rawH;
        int k = Math.min(wo / 4, ho / 3);
        if (k == 0) {
            throw new IllegalArgumentException("La foto es demasiado chica. Mandame una más grande.");
        }

        int lx0 = (wo - 4 * k) / 2;
        int ly0 = (ho - 3 * k) / 2;
        int lx1 = lx0 + 4 * k - 1;
        int ly1 = ly0 + 3 * k - 1;

        int rx0, ry0, rw, rh;
        switch (ori) {
            case 1 -> { rx0 = lx0; ry0 = ly0; rw = 4 * k; rh = 3 * k; }
            case 2 -> { rx0 = rawW - 1 - lx1; ry0 = ly0; rw = 4 * k; rh = 3 * k; }
            case 3 -> { rx0 = rawW - 1 - lx1; ry0 = rawH - 1 - ly1; rw = 4 * k; rh = 3 * k; }
            case 4 -> { rx0 = lx0; ry0 = rawH - 1 - ly1; rw = 4 * k; rh = 3 * k; }
            case 5 -> { rx0 = ly0; ry0 = lx0; rw = 3 * k; rh = 4 * k; }
            case 6 -> { rx0 = ly0; ry0 = rawH - 1 - lx1; rw = 3 * k; rh = 4 * k; }
            case 7 -> { rx0 = rawW - 1 - ly1; ry0 = rawH - 1 - lx1; rw = 3 * k; rh = 4 * k; }
            case 8 -> { rx0 = rawW - 1 - ly1; ry0 = lx0; rw = 3 * k; rh = 4 * k; }
            default -> throw new IllegalArgumentException("Orientación no soportada: " + ori);
        }

        BufferedImage rawCrop = rawImage.getSubimage(rx0, ry0, rw, rh);
        int targetW = (ori >= 5) ? TARGET_HEIGHT : TARGET_WIDTH;
        int targetH = (ori >= 5) ? TARGET_WIDTH : TARGET_HEIGHT;

        BufferedImage resized = Thumbnails.of(rawCrop)
                .size(targetW, targetH)
                .asBufferedImage();

        BufferedImage oriented = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                int rgb = resized.getRGB(x, y);
                int dx, dy;
                switch (ori) {
                    case 1 -> { dx = x; dy = y; }
                    case 2 -> { dx = (TARGET_WIDTH - 1) - x; dy = y; }
                    case 3 -> { dx = (TARGET_WIDTH - 1) - x; dy = (TARGET_HEIGHT - 1) - y; }
                    case 4 -> { dx = x; dy = (TARGET_HEIGHT - 1) - y; }
                    case 5 -> { dx = y; dy = x; }
                    case 6 -> { dx = (TARGET_WIDTH - 1) - y; dy = x; }
                    case 7 -> { dx = (TARGET_WIDTH - 1) - y; dy = (TARGET_HEIGHT - 1) - x; }
                    case 8 -> { dx = y; dy = (TARGET_HEIGHT - 1) - x; }
                    default -> throw new IllegalStateException();
                }
                oriented.setRGB(dx, dy, rgb);
            }
        }

        // Componer alfa sobre #FFFFFF y aplicar corrección sutil exacta
        BufferedImage finalImage = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < TARGET_HEIGHT; y++) {
            for (int x = 0; x < TARGET_WIDTH; x++) {
                int argb = oriented.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                if (a < 255) {
                    double alpha = a / 255.0;
                    r = (int) Math.round(r * alpha + 255.0 * (1.0 - alpha));
                    g = (int) Math.round(g * alpha + 255.0 * (1.0 - alpha));
                    b = (int) Math.round(b * alpha + 255.0 * (1.0 - alpha));
                }

                int rPrime = Math.min(255, Math.max(0, (int) Math.round(r * 1.03 * 1.01)));
                int gPrime = Math.min(255, Math.max(0, (int) Math.round(g * 1.03)));
                int bPrime = Math.min(255, Math.max(0, (int) Math.round(b * 1.03 * 0.99)));

                finalImage.setRGB(x, y, (rPrime << 16) | (gPrime << 8) | bPrime);
            }
        }

        return finalImage;
    }

    private String encodeAndPublish(BufferedImage image, List<Path> temps) throws IOException {
        Path validCandidate = null;

        for (int q : QUALITIES) {
            Path candidate = Files.createTempFile(stagingDir, "cand-", ".webp");
            temps.add(candidate);
            float quality = q / 100f;

            encoder.encode(image, quality, candidate);

            long bytes = Files.size(candidate);
            if (bytes > 0 && bytes < TARGET_MAX_BYTES) {
                // Verificar que sea WebP válido 800x600 y cerrado
                verifyWebPCandidate(candidate);
                validCandidate = candidate;
                break;
            }
        }

        if (validCandidate == null) {
            throw new IllegalArgumentException("No pude reducir esta foto a menos de 80 KB. Probá con otra.");
        }

        // Publicación atómica a root/<uuid>.webp
        String uuidName;
        Path finalDest;
        while (true) {
            uuidName = UUID.randomUUID().toString() + ".webp";
            finalDest = root.resolve(uuidName);
            if (!Files.exists(finalDest, LinkOption.NOFOLLOW_LINKS)) {
                break;
            }
        }

        try {
            Files.move(validCandidate, finalDest, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            throw new IOException("El sistema de archivos no soporta movimiento atómico en uploads", e);
        }

        temps.remove(validCandidate);
        return uuidName;
    }

    private void verifyWebPCandidate(Path candidate) throws IOException {
        byte[] b = Files.readAllBytes(candidate);
        if (b.length < 12) {
            throw new IOException("Candidato WebP demasiado corto (" + b.length + " bytes)");
        }
        if (b[0] != 'R' || b[1] != 'I' || b[2] != 'F' || b[3] != 'F' ||
            b[8] != 'W' || b[9] != 'E' || b[10] != 'B' || b[11] != 'P') {
            throw new IOException("Candidato no tiene encabezado RIFF/WEBP válido");
        }
        long riffLen = (b[4] & 0xFFL) | ((b[5] & 0xFFL) << 8) | ((b[6] & 0xFFL) << 16) | ((b[7] & 0xFFL) << 24);
        if (riffLen + 8 != b.length && riffLen + 8 + 1 != b.length) {
            throw new IOException("Contenedor RIFF inconsistente: longitud declarada " + riffLen + ", bytes reales " + b.length);
        }

        // Inspeccionar chunks RIFF: prohibir EXIF, XMP, animaciones y flags prohibidos en VP8X
        int offset = 12;
        while (offset + 8 <= b.length) {
            String fourCC = new String(b, offset, 4, StandardCharsets.US_ASCII);
            long chunkLen = (b[offset + 4] & 0xFFL) | ((b[offset + 5] & 0xFFL) << 8) |
                            ((b[offset + 6] & 0xFFL) << 16) | ((b[offset + 7] & 0xFFL) << 24);
            if (chunkLen < 0 || offset + 8 + chunkLen > b.length) {
                throw new IOException("Chunk " + fourCC + " con longitud inválida o truncada: " + chunkLen);
            }

            if (fourCC.equalsIgnoreCase("EXIF") || fourCC.equalsIgnoreCase("XMP ") || fourCC.trim().equalsIgnoreCase("XMP")) {
                throw new IOException("Candidato WebP contiene metadatos prohibidos: " + fourCC);
            }
            if (fourCC.equals("ANIM") || fourCC.equals("ANMF")) {
                throw new IOException("Candidato WebP contiene chunks de animación prohibidos: " + fourCC);
            }
            if (fourCC.equals("VP8X")) {
                if (chunkLen >= 1) {
                    int flags = b[offset + 8] & 0xFF;
                    // Bit 1: Animation (0x02), Bit 2: XMP (0x04), Bit 3: Exif (0x08)
                    if ((flags & 0x0E) != 0) {
                        throw new IOException("Candidato WebP VP8X contiene flags prohibidos de animación/metadatos: " + flags);
                    }
                }
            }

            offset += 8 + chunkLen + (chunkLen % 2);
        }

        try (ImageInputStream iis = ImageIO.createImageInputStream(candidate.toFile())) {
            if (iis == null) {
                throw new IOException("No se pudo abrir candidato WebP para decodificación");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IOException("El candidato generado no es un WebP válido");
            }
            ImageReader r = readers.next();
            try {
                r.setInput(iis);
                if (!r.getFormatName().equalsIgnoreCase("WEBP")) {
                    throw new IOException("El formato del candidato no es WebP");
                }
                boolean[] warningOccurred = new boolean[1];
                r.addIIOReadWarningListener((source, warning) -> warningOccurred[0] = true);

                BufferedImage decoded = r.read(0);
                if (decoded == null) {
                    throw new IOException("No se pudo decodificar el contenido del WebP candidato");
                }
                if (warningOccurred[0]) {
                    throw new IOException("Candidato WebP produjo advertencias durante la decodificación");
                }
                if (decoded.getWidth() != TARGET_WIDTH || decoded.getHeight() != TARGET_HEIGHT) {
                    throw new IOException("Las dimensiones del WebP candidato son incorrectas: " + decoded.getWidth() + "x" + decoded.getHeight());
                }
            } finally {
                r.dispose();
            }
        }
    }

    static class DefaultWebPEncoder implements WebPEncoder {
        @Override
        public void encode(BufferedImage img, float quality, Path dest) throws IOException {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
            if (!writers.hasNext()) {
                throw new IOException("No hay ImageWriter para image/webp instalado");
            }
            ImageWriter writer = writers.next();
            try (ImageOutputStream out = ImageIO.createImageOutputStream(dest.toFile())) {
                writer.setOutput(out);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (!param.canWriteCompressed()) {
                    throw new IOException("El writer WebP no soporta compresión");
                }
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] types = param.getCompressionTypes();
                String lossyType = null;
                if (types != null) {
                    for (String t : types) {
                        if (t != null && t.equalsIgnoreCase("lossy")) {
                            lossyType = t;
                            break;
                        }
                    }
                }
                if (lossyType == null) {
                    throw new IOException("No se encontró modo de compresión con pérdida explícito en el writer WebP");
                }
                param.setCompressionType(lossyType);
                param.setCompressionQuality(quality);
                writer.write(null, new IIOImage(img, null, null), param);
            } finally {
                writer.dispose();
            }
        }
    }
}
