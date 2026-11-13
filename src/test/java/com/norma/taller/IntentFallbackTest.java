package com.norma.taller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("IntentFallbackTest — Pruebas deterministas de normalización de intenciones H1")
class IntentFallbackTest {

    private IntentParser parser;
    private ObjectMapper json;
    private List<Catalog.Product> products;

    @BeforeEach
    void setUp() {
        parser = new IntentParser();
        json = new ObjectMapper();
        products = List.of(
                new Catalog.Product(1L, "sess-test", 1, "Cuenco de barro", 5000, "https://test.com/1.jpg", false),
                new Catalog.Product(2L, "sess-test", 2, "Mate de algarrobo", 15000, "https://test.com/2.jpg", false),
                new Catalog.Product(3L, "sess-test", 3, "Jarrón de greda", 22000, "https://test.com/3.jpg", true), // Vendida
                new Catalog.Product(4L, "sess-test", 4, "Tabla de picar", 8500, "https://test.com/4.jpg", false)
        );
    }

    private RestClient mockClientWithContent(String modelJsonContent) {
        RestClient client = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(client.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        try {
            String envelope = """
                    {
                      "id": "chatcmpl-test",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": %s
                          }
                        }
                      ]
                    }
                    """.formatted(json.writeValueAsString(modelJsonContent));
            when(responseSpec.body(String.class)).thenReturn(envelope);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return client;
    }

    private RestClient mockClientThrowing(Exception ex) {
        RestClient client = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(client.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(ex);

        return client;
    }

    // =========================================================================
    // 1. CASOS EXITOSOS DE NORMALIZACIÓN
    // =========================================================================

    @Test
    @DisplayName("Normalización válida de CREATE con evidencia literal e imagen")
    void normalizeValidoCreate() {
        String original = "Hola, me gustaría publicar este Mate de quebracho a quince mil pesos";
        String image = "https://test.com/mate.jpg";
        String modelJson = """
                {
                  "operation": "CREATE",
                  "targetEvidence": "",
                  "priceEvidence": "quince mil pesos",
                  "titleEvidence": "Mate de quebracho"
                }
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, image, products);

        assertTrue(result.isPresent());
        assertEquals("Publicá Mate de quebracho a 15000 pesos", result.get());

        // Verificar que parser.interpret sobre el resultado produce Complete Create
        BotModel.ParseResult parseRes = parser.interpret(result.get(), image, products, null);
        assertInstanceOf(BotModel.Complete.class, parseRes);
        BotModel.Complete complete = (BotModel.Complete) parseRes;
        assertInstanceOf(BotModel.Create.class, complete.action());
        BotModel.Create create = (BotModel.Create) complete.action();
        assertEquals("Mate de quebracho", create.title());
        assertEquals(15000, create.price());
        assertEquals(image, create.imageUrl());
    }

    @Test
    @DisplayName("Normalización válida de PRICE con targetEvidence, priceEvidence y catálogo activo")
    void normalizeValidoPrice() {
        String original = "Por favor a la pieza 2 subila a veinte mil pesos";
        String modelJson = """
                {
                  "operation": "PRICE",
                  "targetEvidence": "pieza 2",
                  "priceEvidence": "veinte mil pesos",
                  "titleEvidence": ""
                }
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, null, products);

        assertTrue(result.isPresent());
        assertEquals("A la número 2 subila a 20000 pesos", result.get());

        BotModel.ParseResult parseRes = parser.interpret(result.get(), null, products, null);
        assertInstanceOf(BotModel.Complete.class, parseRes);
        BotModel.Complete complete = (BotModel.Complete) parseRes;
        assertInstanceOf(BotModel.ChangePrice.class, complete.action());
        BotModel.ChangePrice cp = (BotModel.ChangePrice) complete.action();
        assertEquals(2, cp.pieceNumber());
        assertEquals(20000, cp.newPrice());
    }

    @Test
    @DisplayName("Normalización válida de IMAGE con foto de entrada y targetEvidence")
    void normalizeValidoImage() {
        String original = "Quiero renovar la foto de la pieza 4";
        String image = "https://test.com/nueva-tabla.jpg";
        String modelJson = """
                {
                  "operation": "IMAGE",
                  "targetEvidence": "pieza 4",
                  "priceEvidence": "",
                  "titleEvidence": ""
                }
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, image, products);

        assertTrue(result.isPresent());
        assertEquals("Cambiá la foto de la pieza 4", result.get());

        BotModel.ParseResult parseRes = parser.interpret(result.get(), image, products, null);
        assertInstanceOf(BotModel.Complete.class, parseRes);
        BotModel.Complete complete = (BotModel.Complete) parseRes;
        assertInstanceOf(BotModel.ChangeImage.class, complete.action());
        BotModel.ChangeImage ci = (BotModel.ChangeImage) complete.action();
        assertEquals(4, ci.pieceNumber());
        assertEquals(image, ci.newImageUrl());
    }

    @Test
    @DisplayName("Normalización válida de SOLD con targetEvidence y pieza activa")
    void normalizeValidoSold() {
        String original = "Avisame que ya se vendió la pieza 1";
        String modelJson = """
                {
                  "operation": "SOLD",
                  "targetEvidence": "pieza 1",
                  "priceEvidence": "",
                  "titleEvidence": ""
                }
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, null, products);

        assertTrue(result.isPresent());
        assertEquals("Ya vendí la pieza 1", result.get());

        BotModel.ParseResult parseRes = parser.interpret(result.get(), null, products, null);
        assertInstanceOf(BotModel.Complete.class, parseRes);
        BotModel.Complete complete = (BotModel.Complete) parseRes;
        assertInstanceOf(BotModel.Sell.class, complete.action());
        BotModel.Sell sell = (BotModel.Sell) complete.action();
        assertEquals(1, sell.pieceNumber());
    }

    // =========================================================================
    // 2. DEFENSAS ANTI-ALUCINACIÓN (EVIDENCIA NO PRESENTE EN ORIGINAL)
    // =========================================================================

    @Test
    @DisplayName("Rechaza si el modelo inventa un título que no está en el original")
    void rechazaTituloAlucinado() {
        String original = "Quiero publicar algo a 10000 pesos";
        String image = "https://test.com/foto.jpg";
        String modelJson = """
                {
                  "operation": "CREATE",
                  "targetEvidence": "",
                  "priceEvidence": "10000 pesos",
                  "titleEvidence": "Jarrón azul hermoso"
                }
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, image, products);
        assertTrue(result.isEmpty(), "Debe descartar cuando el título no es subcadena literal del mensaje");
    }

    @Test
    @DisplayName("Rechaza si el modelo inventa un precio que no está en el original")
    void rechazaPrecioAlucinado() {
        String original = "Publicá este Cuenco rústico";
        String image = "https://test.com/cuenco.jpg";
        String modelJson = """
                {
                  "operation": "CREATE",
                  "targetEvidence": "",
                  "priceEvidence": "12000 pesos",
                  "titleEvidence": "Cuenco rústico"
                }
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, image, products);
        assertTrue(result.isEmpty(), "Debe descartar cuando el precio no figura en el texto original");
    }

    @Test
    @DisplayName("Rechaza si el modelo inventa una pieza que no está en el original")
    void rechazaPiezaAlucinada() {
        String original = "Quiero cambiar el precio a 25000";
        String modelJson = """
                {
                  "operation": "PRICE",
                  "targetEvidence": "pieza 2",
                  "priceEvidence": "25000",
                  "titleEvidence": ""
                }
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, null, products);
        assertTrue(result.isEmpty(), "Debe descartar si la pieza no es una subcadena literal del mensaje");
    }

    @Test
    @DisplayName("Rechaza CREATE si falta la imagen (la imagen proviene del parámetro de entrada, no del LLM)")
    void rechazaCreateSinImagen() {
        String original = "Publicá Cuenco de madera a 9000 pesos";
        String modelJson = """
                {
                  "operation": "CREATE",
                  "targetEvidence": "",
                  "priceEvidence": "9000 pesos",
                  "titleEvidence": "Cuenco de madera"
                }
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, null, products);
        assertTrue(result.isEmpty(), "CREATE sin imagen debe retornar empty()");
    }

    // =========================================================================
    // 3. ESTRUCTURA JSON ESTRICTA Y SINTAXIS
    // =========================================================================

    @Test
    @DisplayName("Rechaza JSON con propiedades adicionales desconocidas")
    void rechazaPropiedadesDesconocidas() {
        String original = "Vendí la pieza 2";
        String modelJson = """
                {
                  "operation": "SOLD",
                  "targetEvidence": "pieza 2",
                  "priceEvidence": "",
                  "titleEvidence": "",
                  "confidence": 0.99
                }
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, null, products);
        assertTrue(result.isEmpty(), "Debe descartar JSON con campos extra no permitidos");
    }

    @Test
    @DisplayName("Rechaza JSON con claves duplicadas")
    void rechazaClavesDuplicadas() {
        String original = "Vendí la pieza 2";
        String modelJson = """
                {
                  "operation": "SOLD",
                  "operation": "PRICE",
                  "targetEvidence": "pieza 2",
                  "priceEvidence": "",
                  "titleEvidence": ""
                }
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, null, products);
        assertTrue(result.isEmpty(), "Debe descartar JSON con claves duplicadas (strict duplicate detection)");
    }

    @Test
    @DisplayName("Rechaza coerción de tipos (ej: número entero en priceEvidence en vez de string)")
    void rechazaCoercionDeTipos() {
        String original = "A la pieza 2 subila a 18000";
        String modelJson = """
                {
                  "operation": "PRICE",
                  "targetEvidence": "pieza 2",
                  "priceEvidence": 18000,
                  "titleEvidence": ""
                }
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, null, products);
        assertTrue(result.isEmpty(), "Todos los campos deben ser cadenas textuales estrictas sin coerción");
    }

    @Test
    @DisplayName("Rechaza JSON con tokens o caracteres adicionales al final (trailing garbage)")
    void rechazaContenidoResidualTrailing() {
        String original = "Vendí la pieza 2";
        String modelJson = """
                {
                  "operation": "SOLD",
                  "targetEvidence": "pieza 2",
                  "priceEvidence": "",
                  "titleEvidence": ""
                } {"extra": true}
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, null, products);
        assertTrue(result.isEmpty(), "Debe descartar cuando hay tokens adicionales tras el cierre del JSON");
    }

    // =========================================================================
    // 4. CONSISTENCIA CON CATÁLOGO Y AMBIGÜEDAD
    // =========================================================================

    @Test
    @DisplayName("Rechaza si la pieza indicada no existe en el catálogo")
    void rechazaPiezaInexistenteEnCatalogo() {
        String original = "Anotá que se vendió la pieza 99";
        String modelJson = """
                {
                  "operation": "SOLD",
                  "targetEvidence": "pieza 99",
                  "priceEvidence": "",
                  "titleEvidence": ""
                }
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, null, products);
        assertTrue(result.isEmpty(), "Pieza #99 inexistente en catálogo debe ser descartada");
    }

    @Test
    @DisplayName("Rechaza si la pieza indicada ya está vendida en el catálogo")
    void rechazaPiezaYaVendida() {
        // Pieza 3 está sold = true en los fixtures
        String original = "Cambiá el precio de la pieza 3 a 30000";
        String modelJson = """
                {
                  "operation": "PRICE",
                  "targetEvidence": "pieza 3",
                  "priceEvidence": "30000",
                  "titleEvidence": ""
                }
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, null, products);
        assertTrue(result.isEmpty(), "Pieza #3 ya vendida debe ser descartada");
    }

    @Test
    @DisplayName("Rechaza si el mensaje original contiene múltiples piezas ambiguas")
    void rechazaMultiplesPiezasAmbiguas() {
        String original = "Fijate si la pieza 1 o la pieza 2 se vendió";
        String modelJson = """
                {
                  "operation": "SOLD",
                  "targetEvidence": "pieza 1",
                  "priceEvidence": "",
                  "titleEvidence": ""
                }
                """;

        RestClient client = mockClientWithContent(modelJson);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> result = fallback.normalize(original, null, products);
        assertTrue(result.isEmpty(), "Si el mensaje original menciona múltiples piezas distintas, no se debe adivinar");
    }

    // =========================================================================
    // 5. INMUNIDAD DE CONTROLES CONVERSACIONALES (CONFIRM / CANCEL / UNDO)
    // =========================================================================

    @Test
    @DisplayName("Rechaza controles conversacionales y nunca consulta al LLM")
    void rechazaControlesConversacionales() {
        RestClient client = mock(RestClient.class);
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        List<String> controls = List.of("si", "sí", "confirmo", "dale", "no", "cancela", "deshacer", "me equivoque");

        for (String ctrl : controls) {
            Optional<String> res = fallback.normalize(ctrl, null, products);
            assertTrue(res.isEmpty(), "Control conversacional '" + ctrl + "' debe ser rechazado sin llamar al LLM");
        }

        verifyNoInteractions(client);
    }

    // =========================================================================
    // 6. RESILIENCIA ANTE FALLOS DE INFRAESTRUCTURA Y RED
    // =========================================================================

    @Test
    @DisplayName("Retorna empty si groq.api.key es vacía o en blanco sin realizar llamadas de red")
    void claveAusenteRetornaEmptySinLlamarRed() {
        RestClient client = mock(RestClient.class);
        IntentFallback fallback = new IntentFallback("   ", parser, json, client);

        Optional<String> res = fallback.normalize("Publicá algo", "https://img.jpg", products);
        assertTrue(res.isEmpty());
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("Retorna empty ante HTTP 401 Unauthorized")
    void degredaAnteError401() {
        RestClient client = mockClientThrowing(
                new RestClientResponseException("Unauthorized", HttpStatusCode.valueOf(401), "Unauthorized", HttpHeaders.EMPTY, null, null)
        );
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> res = fallback.normalize("Publicá plato a 1000", "https://img.jpg", products);
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("Retorna empty ante HTTP 429 Too Many Requests")
    void degradaAnteError429() {
        RestClient client = mockClientThrowing(
                new RestClientResponseException("Rate Limit", HttpStatusCode.valueOf(429), "Too Many Requests", HttpHeaders.EMPTY, null, null)
        );
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> res = fallback.normalize("Publicá plato a 1000", "https://img.jpg", products);
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("Retorna empty ante HTTP 500 Server Error")
    void degradaAnteError500() {
        RestClient client = mockClientThrowing(
                new RestClientResponseException("Server Error", HttpStatusCode.valueOf(500), "Internal Error", HttpHeaders.EMPTY, null, null)
        );
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> res = fallback.normalize("Publicá plato a 1000", "https://img.jpg", products);
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("Retorna empty ante timeout o excepción de I/O de red")
    void degradaAnteTimeoutRed() {
        RestClient client = mockClientThrowing(new ResourceAccessException("Read timed out"));
        IntentFallback fallback = new IntentFallback("valid-key", parser, json, client);

        Optional<String> res = fallback.normalize("Publicá plato a 1000", "https://img.jpg", products);
        assertTrue(res.isEmpty());
    }
}
