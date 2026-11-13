package com.norma.taller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Componente de fallback de intenciones asistido por LLM (H1) conforme al diseño Codex v3.
 *
 * El modelo LLM solo propone normalización cuando la gramática no reconoce la intención;
 * la FSM y el validador determinista retienen toda la autoridad sobre la confirmación
 * y ejecución. Los comandos de control conversacional (CONFIRM, CANCEL, UNDO) jamás son admitidos.
 */
@Component
public class IntentFallback {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1";
    private static final String MODEL = "llama-3.3-70b-versatile";

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "operation",
            "targetEvidence",
            "priceEvidence",
            "titleEvidence"
    );

    private static final Set<String> ALLOWED_OPERATIONS = Set.of(
            "CREATE",
            "PRICE",
            "IMAGE",
            "SOLD"
    );

    private static final Pattern TARGET_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:la\\s+|el\\s+)?(?:pieza|n[uú]mero|numero)\\s*[:#]?\\s*|#\\s*)(\\d+|[a-záéíóúñ]+)"
    );

    private static final String SYSTEM_PROMPT =
            "Sos un extractor de intenciones de catálogo para un bot de taller de artesanías argentino.\n" +
            "Tu tarea es analizar el mensaje del usuario y extraer la intención estrictamente en JSON con 4 campos de texto:\n" +
            "1. operation: \"CREATE\", \"PRICE\", \"IMAGE\" o \"SOLD\" (o \"\" si no hay intención clara).\n" +
            "2. targetEvidence: subcadena literal del texto original que indica la pieza (ej: \"pieza 2\", \"la número 3\", \"#4\"), o \"\" para CREATE.\n" +
            "3. priceEvidence: subcadena literal del texto original que indica el precio (ej: \"quince mil pesos\", \"15000\"), o \"\" para IMAGE o SOLD.\n" +
            "4. titleEvidence: subcadena literal del texto original con el nombre del producto (máximo 100 caracteres), o \"\" para PRICE, IMAGE o SOLD.\n" +
            "Reglas obligatorias:\n" +
            "- No inventes datos que no estén explícitamente en el texto.\n" +
            "- Todas las evidencias no vacías deben ser fragmentos textuales idénticos a los del mensaje.\n" +
            "- No devuelvas ningún campo adicional ni texto fuera del JSON.\n" +
            "- Todos los valores deben ser cadenas de texto (strings).\n" +
            "- Nunca devuelvas URLs ni intentes procesar fotos.\n" +
            "- Si el mensaje es una confirmación, cancelación, saludo o no es claro, devolvé operation \"\".";

    private final String key;
    private final IntentParser parser;
    private final ObjectMapper json;
    private final RestClient client;

    @Autowired
    public IntentFallback(
            @Value("${groq.api.key:}") String key,
            IntentParser parser,
            ObjectMapper json
    ) {
        this(key, parser, json, createDefaultClient());
    }

    public IntentFallback(
            String key,
            IntentParser parser,
            ObjectMapper json,
            RestClient client
    ) {
        this.key = key == null ? "" : key.trim();
        this.parser = Objects.requireNonNull(parser, "parser no puede ser null");
        this.json = Objects.requireNonNull(json, "json no puede ser null");
        this.client = Objects.requireNonNull(client, "client no puede ser null");
    }

    private static RestClient createDefaultClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder()
                .baseUrl(GROQ_URL)
                .requestFactory(factory)
                .build();
    }

    /**
     * Intenta normalizar un mensaje no reconocido en un comando canónico admisible.
     * Retorna Optional.of(comandoCanonico) si el modelo propone una normalización
     * válida y verificable contra evidencia literal y el catálogo actual.
     * En cualquier otro caso retorna Optional.empty().
     */
    public Optional<String> normalize(String original, String image, List<Catalog.Product> products) {
        if (key.isBlank() || original == null || original.trim().isEmpty()) {
            return Optional.empty();
        }

        // Protección estricta: nunca admitir señales de control ni convertirlas en comandos
        if (!(parser.extractControl(original, image) instanceof BotModel.None)) {
            return Optional.empty();
        }

        List<Catalog.Product> prods = (products != null) ? products : List.of();

        // 1. Invocar a Groq LLM
        String content;
        try {
            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "temperature", 0,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", original.trim())
                    )
            );

            String responseBody = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return Optional.empty();
            }

            JsonNode groqResp = json.readTree(responseBody);
            JsonNode choices = groqResp.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return Optional.empty();
            }
            JsonNode message = choices.get(0).get("message");
            if (message == null) {
                return Optional.empty();
            }
            JsonNode contentNode = message.get("content");
            if (contentNode == null || !contentNode.isTextual()) {
                return Optional.empty();
            }
            content = contentNode.asText();
        } catch (Exception ignored) {
            // Falla de red, timeout, HTTP 4xx/5xx o JSON ilegible de Groq -> degradación segura
            return Optional.empty();
        }

        // 2. Parseo estricto del JSON de salida del modelo
        JsonNode root;
        try {
            ObjectMapper strictMapper = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            try (JsonParser jp = strictMapper.createParser(content)) {
                root = strictMapper.readTree(jp);
                if (root == null || !root.isObject() || jp.nextToken() != null) {
                    return Optional.empty();
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }

        // 3. Validación de estructura y ausencia de campos desconocidos
        if (root.size() != 4) {
            return Optional.empty();
        }
        Iterator<String> it = root.fieldNames();
        Set<String> fields = new HashSet<>();
        while (it.hasNext()) {
            String field = it.next();
            if (!ALLOWED_FIELDS.contains(field)) {
                return Optional.empty();
            }
            fields.add(field);
        }
        if (fields.size() != 4) {
            return Optional.empty();
        }

        // 4. Verificación de ausencia de coerción de tipos (todos deben ser cadenas)
        for (String field : ALLOWED_FIELDS) {
            JsonNode val = root.get(field);
            if (val == null || !val.isTextual()) {
                return Optional.empty();
            }
        }

        String operation = root.get("operation").asText().trim();
        String targetEvidence = root.get("targetEvidence").asText();
        String priceEvidence = root.get("priceEvidence").asText();
        String titleEvidence = root.get("titleEvidence").asText();

        if (!ALLOWED_OPERATIONS.contains(operation)) {
            return Optional.empty();
        }

        // 5. Verificación de subcadenas literales en el original (anti-alucinación)
        if (!targetEvidence.isEmpty() && !original.contains(targetEvidence)) {
            return Optional.empty();
        }
        if (!priceEvidence.isEmpty() && !original.contains(priceEvidence)) {
            return Optional.empty();
        }
        if (!titleEvidence.isEmpty() && !original.contains(titleEvidence)) {
            return Optional.empty();
        }

        // 6. Validación de consistencia según la operación y reconstrucción canónica
        return switch (operation) {
            case "CREATE" -> validateAndBuildCreate(original, image, titleEvidence, priceEvidence, targetEvidence, prods);
            case "PRICE" -> validateAndBuildPrice(original, targetEvidence, priceEvidence, titleEvidence, prods);
            case "IMAGE" -> validateAndBuildImage(original, image, targetEvidence, priceEvidence, titleEvidence, prods);
            case "SOLD" -> validateAndBuildSold(original, targetEvidence, priceEvidence, titleEvidence, prods);
            default -> Optional.empty();
        };
    }

    private Optional<String> validateAndBuildCreate(
            String original,
            String image,
            String titleEvidence,
            String priceEvidence,
            String targetEvidence,
            List<Catalog.Product> products
    ) {
        // En CREATE targetEvidence debe ser vacío
        if (!targetEvidence.trim().isEmpty()) {
            return Optional.empty();
        }
        if (titleEvidence.trim().isEmpty() || titleEvidence.trim().length() > 100) {
            return Optional.empty();
        }
        if (priceEvidence.trim().isEmpty()) {
            return Optional.empty();
        }
        if (image == null || image.trim().isEmpty()) {
            return Optional.empty();
        }

        Integer priceVal = parser.price(priceEvidence);
        if (priceVal == null || priceVal <= 0) {
            return Optional.empty();
        }

        String title = titleEvidence.trim();
        String canonical = "Publicá " + title + " a " + priceVal + " pesos";

        BotModel.ParseResult result = parser.interpret(canonical, image, products, null);
        if (result instanceof BotModel.Complete c && c.action() instanceof BotModel.Create act) {
            if (act.title().equalsIgnoreCase(title) && act.price() == priceVal && act.imageUrl().equals(image.trim())) {
                return Optional.of(canonical);
            }
        }
        return Optional.empty();
    }

    private Optional<String> validateAndBuildPrice(
            String original,
            String targetEvidence,
            String priceEvidence,
            String titleEvidence,
            List<Catalog.Product> products
    ) {
        // En PRICE titleEvidence debe ser vacío
        if (!titleEvidence.trim().isEmpty()) {
            return Optional.empty();
        }
        if (priceEvidence.trim().isEmpty()) {
            return Optional.empty();
        }

        Integer priceVal = parser.price(priceEvidence);
        if (priceVal == null || priceVal <= 0) {
            return Optional.empty();
        }

        Optional<Integer> targetNumOpt = extractAndValidateTarget(original, targetEvidence, products);
        if (targetNumOpt.isEmpty()) {
            return Optional.empty();
        }

        int targetNum = targetNumOpt.get();
        String canonical = "A la número " + targetNum + " subila a " + priceVal + " pesos";

        BotModel.ParseResult result = parser.interpret(canonical, null, products, null);
        if (result instanceof BotModel.Complete c && c.action() instanceof BotModel.ChangePrice act) {
            if (act.pieceNumber() == targetNum && act.newPrice() == priceVal) {
                return Optional.of(canonical);
            }
        }
        return Optional.empty();
    }

    private Optional<String> validateAndBuildImage(
            String original,
            String image,
            String targetEvidence,
            String priceEvidence,
            String titleEvidence,
            List<Catalog.Product> products
    ) {
        // En IMAGE titleEvidence y priceEvidence deben ser vacíos
        if (!titleEvidence.trim().isEmpty() || !priceEvidence.trim().isEmpty()) {
            return Optional.empty();
        }
        if (image == null || image.trim().isEmpty()) {
            return Optional.empty();
        }

        Optional<Integer> targetNumOpt = extractAndValidateTarget(original, targetEvidence, products);
        if (targetNumOpt.isEmpty()) {
            return Optional.empty();
        }

        int targetNum = targetNumOpt.get();
        String canonical = "Cambiá la foto de la pieza " + targetNum;

        BotModel.ParseResult result = parser.interpret(canonical, image, products, null);
        if (result instanceof BotModel.Complete c && c.action() instanceof BotModel.ChangeImage act) {
            if (act.pieceNumber() == targetNum && act.newImageUrl().equals(image.trim())) {
                return Optional.of(canonical);
            }
        }
        return Optional.empty();
    }

    private Optional<String> validateAndBuildSold(
            String original,
            String targetEvidence,
            String priceEvidence,
            String titleEvidence,
            List<Catalog.Product> products
    ) {
        // En SOLD titleEvidence y priceEvidence deben ser vacíos
        if (!titleEvidence.trim().isEmpty() || !priceEvidence.trim().isEmpty()) {
            return Optional.empty();
        }

        Optional<Integer> targetNumOpt = extractAndValidateTarget(original, targetEvidence, products);
        if (targetNumOpt.isEmpty()) {
            return Optional.empty();
        }

        int targetNum = targetNumOpt.get();
        String canonical = "Ya vendí la pieza " + targetNum;

        BotModel.ParseResult result = parser.interpret(canonical, null, products, null);
        if (result instanceof BotModel.Complete c && c.action() instanceof BotModel.Sell act) {
            if (act.pieceNumber() == targetNum) {
                return Optional.of(canonical);
            }
        }
        return Optional.empty();
    }

    private Optional<Integer> extractAndValidateTarget(
            String original,
            String targetEvidence,
            List<Catalog.Product> products
    ) {
        if (targetEvidence.trim().isEmpty() || targetEvidence.length() > 50) {
            return Optional.empty();
        }

        Matcher mEvidence = TARGET_PATTERN.matcher(targetEvidence);
        if (!mEvidence.find()) {
            return Optional.empty();
        }

        Integer targetNum = parser.parseTargetNumber(targetEvidence);
        if (targetNum == null || targetNum <= 0) {
            return Optional.empty();
        }

        // Detección de ambigüedad en el texto original: no debe haber más de un número de pieza distinto
        Matcher mOriginal = TARGET_PATTERN.matcher(original);
        Set<Integer> distinctPieces = new HashSet<>();
        while (mOriginal.find()) {
            Integer num = parser.parseTargetNumber(mOriginal.group());
            if (num != null) {
                distinctPieces.add(num);
            }
        }
        if (distinctPieces.size() > 1) {
            return Optional.empty();
        }

        // Validación contra el catálogo actual: debe existir y no estar vendida
        Optional<Catalog.Product> prodOpt = products.stream()
                .filter(p -> p.pieceNumber() == targetNum)
                .findFirst();

        if (prodOpt.isEmpty()) {
            return Optional.empty(); // Pieza inexistente
        }
        if (prodOpt.get().sold()) {
            return Optional.empty(); // Pieza ya vendida
        }

        return Optional.of(targetNum);
    }
}
