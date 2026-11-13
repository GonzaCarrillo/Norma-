package com.norma.taller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class BotSessionCodecTest {

    private final BotSessionCodec codec = new BotSessionCodec(new ObjectMapper());
    private static final String SESS = "session_test_1234";

    @Test
    @DisplayName("Envelope v1 encode y decode de Action Create completa")
    void testEncodeDecodeCreateAction() {
        BotModel.Create create = new BotModel.Create("Maceta verde", 15000, "img/foto1.jpg");
        String json = codec.encodePendingAction(SESS, create);

        assertTrue(json.contains("\"version\":1"));
        assertTrue(json.contains("\"sessionId\":\"" + SESS + "\""));
        assertTrue(json.contains("\"operation\":\"CREATE\""));
        assertTrue(json.contains("\"price\":15000"));

        Optional<BotSessionCodec.DecodedPending> opt = codec.decodePending(json, SESS, null);
        assertTrue(opt.isPresent());
        BotSessionCodec.DecodedPending decoded = opt.get();
        assertFalse(decoded.isLegacy());
        assertNotNull(decoded.action());
        assertInstanceOf(BotModel.Create.class, decoded.action());
        BotModel.Create c = (BotModel.Create) decoded.action();
        assertEquals("Maceta verde", c.title());
        assertEquals(15000, c.price());
        assertEquals("img/foto1.jpg", c.imageUrl());
    }

    @Test
    @DisplayName("Envelope v1 encode y decode con límites máximos válidos")
    void testEncodeDecodeMaxBounds() {
        // pieceNumber max: 2147483646, price max: 2147483647
        BotModel.ChangePrice cp = new BotModel.ChangePrice(2147483646, 2147483647);
        String json = codec.encodePendingAction(SESS, cp);

        Optional<BotSessionCodec.DecodedPending> opt = codec.decodePending(json, SESS, null);
        assertTrue(opt.isPresent());
        assertInstanceOf(BotModel.ChangePrice.class, opt.get().action());
        BotModel.ChangePrice res = (BotModel.ChangePrice) opt.get().action();
        assertEquals(2147483646, res.pieceNumber());
        assertEquals(2147483647, res.newPrice());
    }

    @Test
    @DisplayName("Rechazo estricto de enteros gigantes que envuelven a números válidos")
    void testBigIntegerWrappingRejections() {
        // version = 4294967297 (2^32 + 1, en int normal da 1)
        String wrapVersion = "{\"version\":4294967297,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"PRICE\"}}";
        assertTrue(codec.decodePending(wrapVersion, SESS, null).isEmpty());

        // target = 4294967298 (2^32 + 2, en int normal da 2)
        String wrapTarget = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"PRICE\",\"target\":4294967298,\"price\":1000}}";
        assertTrue(codec.decodePending(wrapTarget, SESS, null).isEmpty());

        // price = 4294967297 (en int normal da 1)
        String wrapPrice = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"PRICE\",\"target\":2,\"price\":4294967297}}";
        assertTrue(codec.decodePending(wrapPrice, SESS, null).isEmpty());

        // candidate gigante
        String wrapCand = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"PRICE\",\"candidates\":[4294967297]}}";
        assertTrue(codec.decodePending(wrapCand, SESS, null).isEmpty());

        // Snapshot Product id gigante = 18446744073709551617 (2^64 + 1, en long da 1)
        String wrapIdSnap = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"products\":[" +
                "{\"id\":18446744073709551617,\"sessionId\":\"" + SESS + "\",\"pieceNumber\":1,\"title\":\"A\",\"price\":100,\"imageUrl\":\"a.png\",\"sold\":false}" +
                "]}";
        assertTrue(codec.decodeSnapshot(wrapIdSnap, SESS).isEmpty());

        // Snapshot Product price gigante = 4294967297
        String wrapPriceSnap = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"products\":[" +
                "{\"id\":1,\"sessionId\":\"" + SESS + "\",\"pieceNumber\":1,\"title\":\"A\",\"price\":4294967297,\"imageUrl\":\"a.png\",\"sold\":false}" +
                "]}";
        assertTrue(codec.decodeSnapshot(wrapPriceSnap, SESS).isEmpty());
    }

    @Test
    @DisplayName("Rechazo de trailing tokens o trailing text")
    void testTrailingTokensRejection() {
        // Trailing text después de JSON válido
        String trailingText = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"PRICE\"}} trailing";
        assertTrue(codec.decodePending(trailingText, SESS, null).isEmpty());

        // Trailing JSON después de JSON válido
        String trailingJson = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"PRICE\"}} {\"extra\":1}";
        assertTrue(codec.decodePending(trailingJson, SESS, null).isEmpty());

        // Trailing en snapshot
        String trailingSnap = "[] extra";
        assertTrue(codec.decodeSnapshot(trailingSnap, SESS).isEmpty());
    }

    @Test
    @DisplayName("Envelope v1 encode y decode de Draft parcial con candidatos")
    void testEncodeDecodeDraftPartial() {
        BotModel.Draft draft = new BotModel.Draft(BotModel.Operation.PRICE, null, null, 25000, null, List.of(2, 5));
        String json = codec.encodePendingDraft(SESS, draft);

        Optional<BotSessionCodec.DecodedPending> opt = codec.decodePending(json, SESS, null);
        assertTrue(opt.isPresent());
        BotSessionCodec.DecodedPending decoded = opt.get();
        assertFalse(decoded.isLegacy());
        assertNull(decoded.action()); // no completa porque falta target
        assertEquals(BotModel.Operation.PRICE, decoded.draft().operation());
        assertNull(decoded.draft().target());
        assertEquals(25000, decoded.draft().price());
        assertEquals(List.of(2, 5), decoded.draft().candidates());
    }

    @Test
    @DisplayName("Decode Pending rechaza cross-session, version desconocida o campos extra")
    void testPendingRejections() {
        BotModel.Create create = new BotModel.Create("Plato", 5000, "p.jpg");
        String json = codec.encodePendingAction(SESS, create);

        // Sesión distinta rechazada
        assertTrue(codec.decodePending(json, "otra_sesion_distinta", null).isEmpty());

        // Versión desconocida
        String badVer = "{\"version\":2,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"PRICE\"}}";
        assertTrue(codec.decodePending(badVer, SESS, null).isEmpty());

        // Campo extra en root
        String extraRoot = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"extra\":true,\"draft\":{\"operation\":\"PRICE\"}}";
        assertTrue(codec.decodePending(extraRoot, SESS, null).isEmpty());

        // Campo extra en draft
        String extraDraft = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"PRICE\",\"hack\":123}}";
        assertTrue(codec.decodePending(extraDraft, SESS, null).isEmpty());

        // Claves duplicadas en JSON (STRICT_DUPLICATE_DETECTION)
        String dupKey = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"sessionId\":\"" + SESS + "\",\"draft\":{}}";
        assertTrue(codec.decodePending(dupKey, SESS, null).isEmpty());
    }

    @Test
    @DisplayName("Decode Pending rechaza coerción de strings a número/boolean o floats a int")
    void testPendingCoercionRejections() {
        // precio como string
        String strPrice = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"PRICE\",\"target\":2,\"price\":\"15000\"}}";
        assertTrue(codec.decodePending(strPrice, SESS, null).isEmpty());

        // precio como decimal/float
        String floatPrice = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"PRICE\",\"target\":2,\"price\":15000.5}}";
        assertTrue(codec.decodePending(floatPrice, SESS, null).isEmpty());

        // target negativo o fuera de rango
        String negTarget = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"PRICE\",\"target\":-1,\"price\":15000}}";
        assertTrue(codec.decodePending(negTarget, SESS, null).isEmpty());

        // target justo arriba de 2147483646
        String outOfBoundTarget = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"PRICE\",\"target\":2147483647,\"price\":15000}}";
        assertTrue(codec.decodePending(outOfBoundTarget, SESS, null).isEmpty());

        // discrepancia de tempImage y draft.imageUrl
        String imgMismatch = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"CREATE\",\"title\":\"A\",\"price\":100,\"imageUrl\":\"img1.png\"}}";
        assertTrue(codec.decodePending(imgMismatch, SESS, "img2.png").isEmpty());
    }

    @Test
    @DisplayName("Legacy pending Intent(kind,number,title,price,image) soportado lazy")
    void testLegacyPendingSupport() {
        // Legacy Intent para PRICE con campos sobrantes (title/image null o no aplicables)
        String legacyJson = "{\"kind\":\"PRICE\",\"number\":3,\"title\":null,\"price\":12000,\"image\":null}";
        Optional<BotSessionCodec.DecodedPending> opt = codec.decodePending(legacyJson, SESS, null);
        assertTrue(opt.isPresent());
        BotSessionCodec.DecodedPending decoded = opt.get();
        assertTrue(decoded.isLegacy());
        assertNotNull(decoded.action());
        assertInstanceOf(BotModel.ChangePrice.class, decoded.action());
        BotModel.ChangePrice cp = (BotModel.ChangePrice) decoded.action();
        assertEquals(3, cp.pieceNumber());
        assertEquals(12000, cp.newPrice());

        // Legacy con wrap overflow en precio
        String legacyWrap = "{\"kind\":\"PRICE\",\"number\":3,\"price\":4294967297}";
        assertTrue(codec.decodePending(legacyWrap, SESS, null).isEmpty());

        // Legacy desconocido o corrupto (ej: fixture opaco de #2)
        String opaque = "{\"draft\":9}";
        assertTrue(codec.decodePending(opaque, SESS, null).isEmpty());
    }

    @Test
    @DisplayName("Envelope v1 encode y decode de Snapshot completo y lista vacia")
    void testSnapshotEnvelope() {
        List<Catalog.Product> products = List.of(
                new Catalog.Product(101L, SESS, 1, "Plato", 5000, "plato.jpg", false),
                new Catalog.Product(102L, SESS, 2, "Taza", 3000, "taza.jpg", true)
        );
        String json = codec.encodeSnapshot(SESS, products);
        assertTrue(json.contains("\"version\":1"));
        assertTrue(json.contains("\"sessionId\":\"" + SESS + "\""));

        Optional<List<Catalog.Product>> decoded = codec.decodeSnapshot(json, SESS);
        assertTrue(decoded.isPresent());
        assertEquals(2, decoded.get().size());
        assertEquals(1, decoded.get().get(0).pieceNumber());
        assertEquals("Plato", decoded.get().get(0).title());
        assertFalse(decoded.get().get(0).sold());
        assertEquals(2, decoded.get().get(1).pieceNumber());
        assertTrue(decoded.get().get(1).sold());

        // Lista vacia es snapshot valido
        String emptyJson = codec.encodeSnapshot(SESS, List.of());
        Optional<List<Catalog.Product>> decodedEmpty = codec.decodeSnapshot(emptyJson, SESS);
        assertTrue(decodedEmpty.isPresent());
        assertTrue(decodedEmpty.get().isEmpty());
    }

    @Test
    @DisplayName("Legacy Snapshot List<Product> array directo es soportado lazy si valido")
    void testLegacySnapshotSupport() {
        String legacyJson = "[{\"id\":1,\"sessionId\":\"" + SESS + "\",\"pieceNumber\":1,\"title\":\"Pieza\",\"price\":1000,\"imageUrl\":\"p.jpg\",\"sold\":false}]";
        Optional<List<Catalog.Product>> decoded = codec.decodeSnapshot(legacyJson, SESS);
        assertTrue(decoded.isPresent());
        assertEquals(1, decoded.get().size());
        assertEquals(1, decoded.get().get(0).pieceNumber());

        // Fixture opaco de #2 no válido como snapshot de productos
        String opaque = "[{\"piece\":1}]";
        assertTrue(codec.decodeSnapshot(opaque, SESS).isEmpty());
    }

    @Test
    @DisplayName("Snapshot corrupto o inconsistente es rechazado estrictamente")
    void testSnapshotCorruptRejections() {
        // Sesión cruzada en un producto
        String crossSession = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"products\":[" +
                "{\"id\":1,\"sessionId\":\"" + SESS + "\",\"pieceNumber\":1,\"title\":\"A\",\"price\":100,\"imageUrl\":\"a.png\",\"sold\":false}," +
                "{\"id\":2,\"sessionId\":\"otra_sesion\",\"pieceNumber\":2,\"title\":\"B\",\"price\":200,\"imageUrl\":\"b.png\",\"sold\":false}" +
                "]}";
        assertTrue(codec.decodeSnapshot(crossSession, SESS).isEmpty());

        // pieceNumber duplicado en snapshot
        String dupPiece = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"products\":[" +
                "{\"id\":1,\"sessionId\":\"" + SESS + "\",\"pieceNumber\":1,\"title\":\"A\",\"price\":100,\"imageUrl\":\"a.png\",\"sold\":false}," +
                "{\"id\":2,\"sessionId\":\"" + SESS + "\",\"pieceNumber\":1,\"title\":\"B\",\"price\":200,\"imageUrl\":\"b.png\",\"sold\":false}" +
                "]}";
        assertTrue(codec.decodeSnapshot(dupPiece, SESS).isEmpty());

        // campo faltante o corrupto (ej: sold no es boolean)
        String nonBoolSold = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"products\":[" +
                "{\"id\":1,\"sessionId\":\"" + SESS + "\",\"pieceNumber\":1,\"title\":\"A\",\"price\":100,\"imageUrl\":\"a.png\",\"sold\":\"no\"}" +
                "]}";
        assertTrue(codec.decodeSnapshot(nonBoolSold, SESS).isEmpty());
    }

    // ==========================================
    // F4 REGRESIONES AUDITORÍA CODEX v1.0
    // ==========================================

    @Test
    @DisplayName("F4 Audit: Envelope con draft vacío {} es rechazado estrictamente como corrupto")
    void testAuditF4EmptyDraftEnvelopeRejection() {
        String emptyDraft = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{}}";
        assertTrue(codec.decodePending(emptyDraft, SESS, null).isEmpty());
    }

    @Test
    @DisplayName("F4 Audit: Envelope con campos de operación incompatibles es rechazado estrictamente")
    void testAuditF4IncompatibleOperationFieldsRejection() {
        // SOLD con price
        String soldWithPrice = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"SOLD\",\"target\":2,\"price\":50}}";
        assertTrue(codec.decodePending(soldWithPrice, SESS, null).isEmpty());

        // SOLD con title
        String soldWithTitle = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"SOLD\",\"target\":2,\"title\":\"Plato\"}}";
        assertTrue(codec.decodePending(soldWithTitle, SESS, null).isEmpty());

        // PRICE con title
        String priceWithTitle = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"PRICE\",\"target\":2,\"price\":5000,\"title\":\"Plato\"}}";
        assertTrue(codec.decodePending(priceWithTitle, SESS, null).isEmpty());

        // IMAGE con price
        String imageWithPrice = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"IMAGE\",\"target\":2,\"imageUrl\":\"/img/a.jpg\",\"price\":5000}}";
        assertTrue(codec.decodePending(imageWithPrice, SESS, null).isEmpty());

        // CREATE con target
        String createWithTarget = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"CREATE\",\"title\":\"Plato\",\"price\":5000,\"imageUrl\":\"/img/a.jpg\",\"target\":2}}";
        assertTrue(codec.decodePending(createWithTarget, SESS, null).isEmpty());
    }

    @Test
    @DisplayName("F4 Audit: Create normaliza espacios y rechaza título > 100 en modelo y codec")
    void testAuditF4CreateTitleConstraintsAndWhitespace() {
        // Modelo Create normaliza espacios internos
        BotModel.Create c = new BotModel.Create("  Plato   hondo   azul  ", 15000, "/img/p.jpg");
        assertEquals("Plato hondo azul", c.title());

        // Modelo Create rechaza > 100 caracteres
        String longTitle = "A".repeat(101);
        assertThrows(IllegalArgumentException.class, () -> new BotModel.Create(longTitle, 15000, "/img/p.jpg"));

        // Codec decodePending rechaza título > 100 en draft
        String jsonLongTitle = "{\"version\":1,\"sessionId\":\"" + SESS + "\",\"draft\":{\"operation\":\"CREATE\",\"title\":\"" + longTitle + "\",\"price\":15000,\"imageUrl\":\"/img/p.jpg\"}}";
        assertTrue(codec.decodePending(jsonLongTitle, SESS, null).isEmpty());
    }

    @Test
    @DisplayName("F4 Audit: decodeSnapshot preserva espacios exactos sin trim destructivo")
    void testAuditF4SnapshotPreservesExactWhitespaceRoundtrip() {
        String originalTitle = "  Título con espacios  ";
        String originalImage = "  /media/con espacios.webp  ";
        List<Catalog.Product> products = List.of(
                new Catalog.Product(999L, SESS, 1, originalTitle, 5000, originalImage, false)
        );

        String encoded = codec.encodeSnapshot(SESS, products);
        Optional<List<Catalog.Product>> decoded = codec.decodeSnapshot(encoded, SESS);
        assertTrue(decoded.isPresent());
        assertEquals(1, decoded.get().size());
        // Espacios intactos sin recorte
        assertEquals(originalTitle, decoded.get().get(0).title());
        assertEquals(originalImage, decoded.get().get(0).imageUrl());
    }
}
