package com.norma.taller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IntentParserTest {

    private final IntentParser parser = new IntentParser();

    private final List<Catalog.Product> sampleProducts = List.of(
            new Catalog.Product(1L, "sess_1", 1, "Cuenco rústico", 12000, "cuenco.jpg", false),
            new Catalog.Product(2L, "sess_1", 2, "Maceta azul", 15000, "maceta1.jpg", false),
            new Catalog.Product(3L, "sess_1", 3, "Maceta roja", 18000, "maceta2.jpg", false),
            new Catalog.Product(4L, "sess_1", 4, "Plato hondo", 20000, "plato.jpg", true) // sold
    );

    // ==========================================
    // P1: PARSER PARAMETRIZADO DE PRECIOS (§4)
    // ==========================================

    @ParameterizedTest(name = "Precio válido: \"{0}\" -> {1}")
    @CsvSource({
            "15000, 15000",
            "18.500, 18500",
            "2.147.483.647, 2147483647",
            "$ 15000 pesos, 15000",
            "15 mil, 15000",
            "15 lucas, 15000",
            "quince mil, 15000",
            "quince lucas, 15000",
            "mil, 1000",
            "un millón, 1000000",
            "dieciocho quinientos, 18500",
            "quince doscientos cincuenta, 15250",
            "ciento cincuenta, 150",
            "veinticinco mil cuatrocientos, 25400",
            "un millon doscientos mil, 1200000",
            "'no, dije 18.500', 18500",
            "'dije 15 mil', 15000",
            "'el precio es 20.000', 20000"
    })
    void testValidPrices(String raw, int expected) {
        Integer p = parser.price(raw);
        assertNotNull(p, "Debería parsear: " + raw);
        assertEquals(expected, p.intValue());
    }

    @ParameterizedTest(name = "Precio inválido rechazado: \"{0}\"")
    @ValueSource(strings = {
            "0",
            "-15000",
            "+15000",
            "15,50",
            "15.000,00",
            "15.00",
            "015000",
            "2147483648",
            "4294967297",
            "quince veinte",
            "mil mil",
            "15 o 20 mil",
            "15000 20000",
            "lucas",
            "menos veinte mil",
            "1e5",
            "el precio es 15,50",
            "dije 15.000,00",
            "no, dije 15.00"
    })
    void testInvalidPrices(String raw) {
        Integer p = parser.price(raw);
        assertNull(p, "No debería parsear precio inválido: " + raw);
    }

    // ==========================================
    // P2: DESTINO, NÚMEROS Y NOMBRES
    // ==========================================

    @Test
    @DisplayName("Número explícito y nombre único resuelven destino correcto")
    void testTargetResolution() {
        // Por número explícito
        BotModel.ParseResult rNum = parser.interpret("Sacá la pieza 2", null, sampleProducts, null);
        assertInstanceOf(BotModel.Complete.class, rNum);
        BotModel.Complete cNum = (BotModel.Complete) rNum;
        assertInstanceOf(BotModel.Sell.class, cNum.action());
        assertEquals(2, ((BotModel.Sell) cNum.action()).pieceNumber());

        // Por nombre único
        BotModel.ParseResult rName = parser.interpret("Sacá el cuenco rústico", null, sampleProducts, null);
        assertInstanceOf(BotModel.Complete.class, rName);
        BotModel.Complete cName = (BotModel.Complete) rName;
        assertInstanceOf(BotModel.Sell.class, cName.action());
        assertEquals(1, ((BotModel.Sell) cName.action()).pieceNumber());
    }

    @Test
    @DisplayName("Palabras de acción dentro del título no eligen acción errónea ni causan MULTIPLE_ACTIONS")
    void testActionWordsInsideTitle() {
        // "Publicá Vendida a 15000" contiene "Vendida" en el título
        BotModel.ParseResult r1 = parser.interpret("Publicá Vendida a 15000", "foto.jpg", sampleProducts, null);
        assertInstanceOf(BotModel.Complete.class, r1);
        BotModel.Create c1 = (BotModel.Create) ((BotModel.Complete) r1).action();
        assertEquals("Vendida", c1.title());
        assertEquals(15000, c1.price());

        // "Publicá Saca a 15000" contiene "Saca" en el título
        BotModel.ParseResult r2 = parser.interpret("Publicá Saca a 15000", "foto.jpg", sampleProducts, null);
        assertInstanceOf(BotModel.Complete.class, r2);
        BotModel.Create c2 = (BotModel.Create) ((BotModel.Complete) r2).action();
        assertEquals("Saca", c2.title());
        assertEquals(15000, c2.price());
    }

    @Test
    @DisplayName("Nombre ambiguo (maceta) detecta candidatos y pide aclaración")
    void testAmbiguousName() {
        // Hay maceta azul (#2) y maceta roja (#3)
        BotModel.ParseResult r = parser.interpret("Sacá la maceta", null, sampleProducts, null);
        assertInstanceOf(BotModel.Ambiguous.class, r);
        BotModel.Ambiguous amb = (BotModel.Ambiguous) r;
        assertEquals(List.of(2, 3), amb.candidatePieceNumbers());
    }

    @Test
    @DisplayName("Pieza vendida o inexistente se rechaza con mensaje apropiado")
    void testSoldOrUnknownTarget() {
        // Pieza 4 está vendida
        BotModel.ParseResult rSold = parser.interpret("Sacá la pieza 4", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, rSold);
        assertEquals(BotModel.InvalidReason.TARGET_ALREADY_SOLD, ((BotModel.Invalid) rSold).reason());

        // Pieza 99 no existe
        BotModel.ParseResult rNotFound = parser.interpret("Sacá la pieza 99", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, rNotFound);
        assertEquals(BotModel.InvalidReason.UNKNOWN_TARGET, ((BotModel.Invalid) rNotFound).reason());
    }

    @Test
    @DisplayName("Referencias contradictorias en el mismo mensaje se rechazan")
    void testConflictingReferences() {
        BotModel.ParseResult r = parser.interpret("Cambiá el precio de la pieza 1 y pieza 2 a 15000", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r);
        assertEquals(BotModel.InvalidReason.MULTIPLE_ACTIONS, ((BotModel.Invalid) r).reason());
    }

    @Test
    @DisplayName("Dígitos en título no se confunden con el precio (#N separado de monto)")
    void testTitleDigitsNotConfusedWithPrice() {
        BotModel.ParseResult r = parser.interpret("Publicá Cuenco 2 a 15000", "foto.jpg", sampleProducts, null);
        assertInstanceOf(BotModel.Complete.class, r);
        BotModel.Create c = (BotModel.Create) ((BotModel.Complete) r).action();
        assertEquals("Cuenco 2", c.title());
        assertEquals(15000, c.price());
    }

    @Test
    @DisplayName("Múltiples acciones en el mismo mensaje son rechazadas")
    void testMultipleActionsRejected() {
        BotModel.ParseResult r = parser.interpret("Sacá la pieza 2 y agregá Plato a 5000", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r);
        assertEquals(BotModel.InvalidReason.MULTIPLE_ACTIONS, ((BotModel.Invalid) r).reason());
    }

    // ==========================================
    // CONTROLES Y ADAPTADORES LEGACY
    // ==========================================

    @Test
    @DisplayName("Detección de controles conversacionales exactos y rechazo de ambiguos o con foto")
    void testControlExtraction() {
        // Exactos sin foto
        assertInstanceOf(BotModel.Confirm.class, parser.extractControl("sí", null));
        assertInstanceOf(BotModel.Confirm.class, parser.extractControl("dale", null));
        assertInstanceOf(BotModel.Cancel.class, parser.extractControl("no", null));
        assertInstanceOf(BotModel.Cancel.class, parser.extractControl("pará", null));
        assertInstanceOf(BotModel.Undo.class, parser.extractControl("deshacer", null));
        assertInstanceOf(BotModel.Undo.class, parser.extractControl("me equivoqué", null));
        assertInstanceOf(BotModel.Undo.class, parser.extractControl("uy no me equivoqué volvé atrás", null));

        // Frases compuestas que NO deben ser confirm/cancel exactos (§4)
        assertInstanceOf(BotModel.None.class, parser.extractControl("no sé", null));
        assertInstanceOf(BotModel.None.class, parser.extractControl("sí pero no ahora", null));
        assertInstanceOf(BotModel.None.class, parser.extractControl("sí, no", null));

        // Con foto adjunta NO confirman ni cancelan automáticamente (§4)
        assertInstanceOf(BotModel.None.class, parser.extractControl("sí", "foto.jpg"));
        assertInstanceOf(BotModel.None.class, parser.extractControl("no", "foto.jpg"));

        // Corrección de precio con puntuación original intacta
        BotModel.Control corrP1 = parser.extractControl("no, dije 18.500", null);
        assertInstanceOf(BotModel.Correction.class, corrP1);
        assertEquals(18500, ((BotModel.Correction) corrP1).value());

        // Corrección de precio inválido (con comas o centavos)
        BotModel.Control corrP2 = parser.extractControl("el precio es 15,50", null);
        assertInstanceOf(BotModel.Correction.class, corrP2);
        assertNull(((BotModel.Correction) corrP2).value());

        // Corrección de target
        BotModel.Control corrT = parser.extractControl("no, la pieza 3", null);
        assertInstanceOf(BotModel.Correction.class, corrT);
        assertEquals(3, ((BotModel.Correction) corrT).value());
    }

    @Test
    @DisplayName("Adaptador legacy parse() mantiene compatibilidad para acción completa y lanza para incompleta")
    void testLegacyParseAdapter() {
        IntentParser.Intent intent = parser.parse("Cambiá el precio de #2 a 25000", null, sampleProducts);
        assertEquals("PRICE", intent.kind());
        assertEquals(2, intent.number());
        assertEquals(25000, intent.price());

        // Incompleto lanza IllegalArgumentException clara
        assertThrows(IllegalArgumentException.class, () -> parser.parse("Cambiá el precio", null, sampleProducts));
    }

    // ==========================================
    // REGRESIONES EXACTAS F1 Y F2 AUDITORÍA v1.0
    // ==========================================

    @Test
    @DisplayName("F1.1: Contradicción número y nombre explícito requiere aclaración y no elige arbitrariamente")
    void testContradictoryNumberAndNameRequiresClarification() {
        // En sampleProducts, #2 es 'Maceta azul', #3 es 'Maceta roja', #1 es 'Cuenco rústico'
        // 'Sacá la pieza 2 cuenco rústico' tiene contradicción entre #2 y #1
        BotModel.ParseResult r = parser.interpret("Sacá la pieza 2 cuenco rústico", null, sampleProducts, null);
        assertInstanceOf(BotModel.Ambiguous.class, r);
        BotModel.Ambiguous amb = (BotModel.Ambiguous) r;
        assertEquals(List.of(1, 2), amb.candidatePieceNumbers());
    }

    @Test
    @DisplayName("F1.2: Referencia decimal 2.5 se rechaza explícitamente y no se trunca a 2")
    void testDecimalReferenceRejected() {
        BotModel.ParseResult r = parser.interpret("Sacá la pieza 2.5", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r);
        assertEquals(BotModel.InvalidReason.DECIMAL_OR_CENTAVOS, ((BotModel.Invalid) r).reason());
    }

    @Test
    @DisplayName("F1.3: Varios montos en cambio de precio requieren aclaración y no eligen el último")
    void testMultipleAmountsRejected() {
        BotModel.ParseResult r = parser.interpret("Cambiá el precio de #2 a 15000 por 20000", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r);
        assertEquals(BotModel.InvalidReason.MULTIPLE_ACTIONS, ((BotModel.Invalid) r).reason());
    }

    @Test
    @DisplayName("F1.4: Acciones separadas por ';' no se ejecutan parcialmente ni eligen la última")
    void testSemicolonSeparatedActionsRejected() {
        BotModel.ParseResult r = parser.interpret("Cambiá el precio de #2 a 20000; sacá la pieza 2", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r);
        assertEquals(BotModel.InvalidReason.MULTIPLE_ACTIONS, ((BotModel.Invalid) r).reason());
    }

    @Test
    @DisplayName("F2.1: Prefijos completos 'Esta es' y palabras con prefijo como 'Estrella' preservan título y espacios")
    void testTitlePrefixesAndSpacings() {
        BotModel.ParseResult r1 = parser.interpret("Esta es Jarra a 15000", "foto.jpg", sampleProducts, null);
        assertInstanceOf(BotModel.Complete.class, r1);
        BotModel.Create c1 = (BotModel.Create) ((BotModel.Complete) r1).action();
        assertEquals("Jarra", c1.title());
        assertEquals(15000, c1.price());

        BotModel.ParseResult r2 = parser.interpret("Estrella a 15000", "foto.jpg", sampleProducts, null);
        assertInstanceOf(BotModel.Complete.class, r2);
        BotModel.Create c2 = (BotModel.Create) ((BotModel.Complete) r2).action();
        assertEquals("Estrella", c2.title());
        assertEquals(15000, c2.price());
    }

    @Test
    @DisplayName("F2.2: Gramática canónica y coloquial estricta de números y centenas")
    void testCanonicalAndColloquialGrammarRules() {
        // dieciocho cien -> 18100
        assertEquals(18100, parser.price("dieciocho cien"));

        // 'cien cincuenta' es inválido (debe ser 'ciento cincuenta')
        assertNull(parser.price("cien cincuenta"));
        assertEquals(150, parser.price("ciento cincuenta"));

        // 'un' o 'veintiún' solos no son números canónicos válidos aislados
        assertNull(parser.price("un"));
        assertNull(parser.price("veintiún"));
        assertNull(parser.price("veintiun"));

        // pero ante mil o millón sí son válidos
        assertEquals(1000000, parser.price("un millón"));
        assertEquals(21000, parser.price("veintiún mil"));

        // 'treinta y veinte quinientos' es inválido (tras 'y' sólo unidades 1..9)
        assertNull(parser.price("treinta y veinte quinientos"));
    }

    @Test
    @DisplayName("F2.3: Números hablados como referencias a piezas")
    void testSpokenNumberReferences() {
        BotModel.ParseResult r = parser.interpret("Sacá la pieza dos", null, sampleProducts, null);
        assertInstanceOf(BotModel.Complete.class, r);
        BotModel.Sell s = (BotModel.Sell) ((BotModel.Complete) r).action();
        assertEquals(2, s.pieceNumber());
    }

    // ========================================================
    // REGRESIONES EXACTAS AUDITORÍA v1.1 (F1 - F2)
    // ========================================================

    @Test
    @DisplayName("F1.v1.1: Decimales con acento o coma en pieza/número no se truncan a entero")
    void testDecimalReferencesWithAccentOrCommaRejected() {
        BotModel.ParseResult r1 = parser.interpret("Sacá la número 2.5", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r1);
        assertEquals(BotModel.InvalidReason.DECIMAL_OR_CENTAVOS, ((BotModel.Invalid) r1).reason());

        BotModel.ParseResult r2 = parser.interpret("Sacá la pieza 2,5", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r2);
        assertEquals(BotModel.InvalidReason.DECIMAL_OR_CENTAVOS, ((BotModel.Invalid) r2).reason());

        BotModel.ParseResult r3 = parser.interpret("pieza 2,5", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r3);
        assertEquals(BotModel.InvalidReason.DECIMAL_OR_CENTAVOS, ((BotModel.Invalid) r3).reason());

        BotModel.ParseResult r4 = parser.interpret("#2.5", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r4);
        assertEquals(BotModel.InvalidReason.DECIMAL_OR_CENTAVOS, ((BotModel.Invalid) r4).reason());

        BotModel.ParseResult r5 = parser.interpret("la numero 2,5", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r5);
        assertEquals(BotModel.InvalidReason.DECIMAL_OR_CENTAVOS, ((BotModel.Invalid) r5).reason());
    }

    @Test
    @DisplayName("F1.v1.1: Montos compuestos con primer monto inválido, negativo u overflow no se descartan")
    void testCompoundAmountsWithInvalidFirstAmountRejected() {
        // Primer monto con coma decimal seguido de segundo monto
        BotModel.ParseResult r1 = parser.interpret("Cambiá el precio de #2 a 15,50 por 20000", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r1);
        assertEquals(BotModel.InvalidReason.MULTIPLE_ACTIONS, ((BotModel.Invalid) r1).reason());

        // Primer monto negativo seguido de segundo monto
        BotModel.ParseResult r2 = parser.interpret("Cambiá el precio de #2 a -15000 por 20000", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r2);
        assertEquals(BotModel.InvalidReason.MULTIPLE_ACTIONS, ((BotModel.Invalid) r2).reason());

        // Primer monto en overflow intmax seguido de segundo monto
        BotModel.ParseResult r3 = parser.interpret("Cambiá el precio de #2 a 2147483648 por 20000", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r3);
        assertEquals(BotModel.InvalidReason.MULTIPLE_ACTIONS, ((BotModel.Invalid) r3).reason());
    }

    @Test
    @DisplayName("F1.v1.1: Múltiples acciones coordinadas con 'Y' mayúscula no eligen la última")
    void testMultipleActionsWithUppercaseYRejected() {
        BotModel.ParseResult r = parser.interpret("Cambiá el precio de #2 a 20000 Y sacá la pieza 2", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r);
        assertEquals(BotModel.InvalidReason.MULTIPLE_ACTIONS, ((BotModel.Invalid) r).reason());
    }

    @Test
    @DisplayName("F1.v1.1: Número hablado 'ciento treinta y dos' consume 132 completo y no prefijo 130")
    void testSpokenNumberFull132Consumed() {
        List<Catalog.Product> prods = List.of(
                new Catalog.Product(130L, "S1", 130, "Pieza ciento treinta", 1000, null, false),
                new Catalog.Product(132L, "S1", 132, "Pieza ciento treinta y dos", 2000, null, false)
        );
        BotModel.ParseResult r = parser.interpret("Sacá la pieza ciento treinta y dos", null, prods, null);
        assertInstanceOf(BotModel.Complete.class, r);
        BotModel.Sell s = (BotModel.Sell) ((BotModel.Complete) r).action();
        assertEquals(132, s.pieceNumber());
    }

    @Test
    @DisplayName("F1.v1.1: 'Cambiá el precio de #2' conserva destino 2 y no genera falsa ambigüedad por 'de'")
    void testChangePriceOfTargetWithoutPriceDoesNotCreateFalseContradiction() {
        // En sampleProducts, #2 es 'Maceta azul', #4 es 'Maceta de barro'
        BotModel.ParseResult r = parser.interpret("Cambiá el precio de #2", null, sampleProducts, null);
        assertInstanceOf(BotModel.NeedsDetails.class, r);
        BotModel.NeedsDetails nd = (BotModel.NeedsDetails) r;
        assertEquals(BotModel.Operation.PRICE, nd.draft().operation());
        assertEquals(2, nd.draft().target());
        assertNull(nd.draft().price());
        assertEquals(BotModel.MissingField.PRICE, nd.missingField());
    }

    @Test
    @DisplayName("F2.v1.1: 'Publicá Es amor a 15000' conserva título 'Es amor' extrayendo prefijo una sola vez")
    void testPublishTitleStartingWithEsPreserved() {
        BotModel.ParseResult r = parser.interpret("Publicá Es amor a 15000", "foto.webp", sampleProducts, null);
        assertInstanceOf(BotModel.Complete.class, r);
        BotModel.Create c = (BotModel.Create) ((BotModel.Complete) r).action();
        assertEquals("Es amor", c.title());
        assertEquals(15000, c.price());
        assertEquals("foto.webp", c.imageUrl());
    }

    @Test
    @DisplayName("F2.v1.1: Precios coloquiales contractuales: '15 lucas' y 'quince lucas'")
    void testLucasColloquialPrices() {
        assertEquals(15000, parser.price("15 lucas"));
        assertEquals(15000, parser.price("quince lucas"));
        assertEquals(1000, parser.price("1 luca"));
        assertEquals(1000, parser.price("una luca"));
    }

    // ========================================================
    // REGRESIONES EXACTAS AUDITORÍA v1.2 (R2)
    // ========================================================

    @Test
    @DisplayName("R2.v1.2: Extracción de precio rechaza tramos y residuos no autorizados antes o después del monto")
    void testPriceExpressionRejectsResidualChunksBeforeAndAfterValidAmount() {
        // Residuo no autorizado después del monto válido
        BotModel.ParseResult r1 = parser.interpret("Cambiá el precio de #2 a 15000 por nada", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r1);
        assertEquals(BotModel.InvalidReason.MULTIPLE_ACTIONS, ((BotModel.Invalid) r1).reason());

        // Residuo no autorizado antes del monto válido
        BotModel.ParseResult r2 = parser.interpret("Cambiá el precio de #2 a nada por 15000", null, sampleProducts, null);
        assertInstanceOf(BotModel.Invalid.class, r2);
        assertEquals(BotModel.InvalidReason.MULTIPLE_ACTIONS, ((BotModel.Invalid) r2).reason());

        // Control positivo: monto único válido sin residuos produce Complete
        BotModel.ParseResult r3 = parser.interpret("Cambiá el precio de #2 a 15000", null, sampleProducts, null);
        assertInstanceOf(BotModel.Complete.class, r3);
        BotModel.ChangePrice cp3 = (BotModel.ChangePrice) ((BotModel.Complete) r3).action();
        assertEquals(2, cp3.pieceNumber());
        assertEquals(15000, cp3.newPrice());

        // Control positivo: delimitador 'por' con precio único válido
        BotModel.ParseResult r4 = parser.interpret("Cambiá el precio de #2 por 15000", null, sampleProducts, null);
        assertInstanceOf(BotModel.Complete.class, r4);
        BotModel.ChangePrice cp4 = (BotModel.ChangePrice) ((BotModel.Complete) r4).action();
        assertEquals(2, cp4.pieceNumber());
        assertEquals(15000, cp4.newPrice());

        // Control positivo: coloquial '15 lucas' tras 'por'
        BotModel.ParseResult r5 = parser.interpret("Cambiá el precio de #2 por 15 lucas", null, sampleProducts, null);
        assertInstanceOf(BotModel.Complete.class, r5);
        BotModel.ChangePrice cp5 = (BotModel.ChangePrice) ((BotModel.Complete) r5).action();
        assertEquals(2, cp5.pieceNumber());
        assertEquals(15000, cp5.newPrice());
    }
}
