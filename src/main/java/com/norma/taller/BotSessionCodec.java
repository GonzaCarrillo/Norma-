package com.norma.taller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.*;

/**
 * Serializador y deserializador estricto para sesiones de Bot (Issue #3).
 * Soporta pending/snapshot en formato envelope v1 y formatos legacy.
 * Cumple estrictamente con §6 del diseño y previene desbordamiento/envoltorio de números.
 */
public class BotSessionCodec {

    private final ObjectMapper mapper;

    public BotSessionCodec(ObjectMapper mapper) {
        // Configuramos mapper para que sea estricto: sin duplicados de claves, sin coerción indeseada,
        // rechazo de trailing tokens / trailing text.
        this.mapper = mapper.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }

    public BotSessionCodec() {
        this(new ObjectMapper());
    }

    // ==========================================
    // PARSING STRICT CON TRAILING TOKENS CHECK
    // ==========================================

    private JsonNode readTreeStrict(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try (JsonParser parser = mapper.createParser(json)) {
            JsonNode node = mapper.readTree(parser);
            if (node == null) return null;
            // Verificar que no haya tokens o texto residual tras el valor raíz
            if (parser.nextToken() != null) {
                return null;
            }
            return node;
        } catch (Exception e) {
            return null;
        }
    }

    // ==========================================
    // HELPERS DE NUMEROS SEGUROS (NO OVERFLOW/WRAP)
    // ==========================================

    private static Integer parseStrictInt(JsonNode node, int min, int max) {
        if (node == null || !node.isIntegralNumber()) return null;
        if (!node.canConvertToInt()) return null;
        int val = node.asInt();
        if (val < min || val > max) return null;
        return val;
    }

    private static Long parseStrictLong(JsonNode node, long min, long max) {
        if (node == null || !node.isIntegralNumber()) return null;
        if (!node.canConvertToLong()) return null;
        long val = node.asLong();
        if (val < min || val > max) return null;
        return val;
    }

    // ==========================================
    // ENCODE
    // ==========================================

    public String encodePendingDraft(String sessionId, BotModel.Draft draft) {
        Objects.requireNonNull(sessionId, "sessionId no puede ser null");
        Objects.requireNonNull(draft, "draft no puede ser null");
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("version", 1);
            root.put("sessionId", sessionId);

            ObjectNode draftNode = mapper.createObjectNode();
            if (draft.operation() != null) {
                draftNode.put("operation", draft.operation().name());
            }
            if (draft.target() != null) {
                draftNode.put("target", draft.target());
            }
            if (draft.title() != null) {
                draftNode.put("title", draft.title());
            }
            if (draft.price() != null) {
                draftNode.put("price", draft.price());
            }
            if (draft.imageUrl() != null) {
                draftNode.put("imageUrl", draft.imageUrl());
            }
            if (draft.candidates() != null) {
                ArrayNode cArr = mapper.createArrayNode();
                for (int c : draft.candidates()) {
                    cArr.add(c);
                }
                draftNode.set("candidates", cArr);
            }

            root.set("draft", draftNode);
            return mapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new IllegalStateException("Error al codificar pending draft", e);
        }
    }

    public String encodePendingAction(String sessionId, BotModel.Action action) {
        Objects.requireNonNull(sessionId, "sessionId no puede ser null");
        Objects.requireNonNull(action, "action no puede ser null");
        BotModel.Draft draft;
        if (action instanceof BotModel.Create c) {
            draft = new BotModel.Draft(BotModel.Operation.CREATE, null, c.title(), c.price(), c.imageUrl(), null);
        } else if (action instanceof BotModel.ChangePrice cp) {
            draft = new BotModel.Draft(BotModel.Operation.PRICE, cp.pieceNumber(), null, cp.newPrice(), null, null);
        } else if (action instanceof BotModel.ChangeImage ci) {
            draft = new BotModel.Draft(BotModel.Operation.IMAGE, ci.pieceNumber(), null, null, ci.newImageUrl(), null);
        } else if (action instanceof BotModel.Sell s) {
            draft = new BotModel.Draft(BotModel.Operation.SOLD, s.pieceNumber(), null, null, null, null);
        } else {
            throw new IllegalArgumentException("Tipo de accion no soportado: " + action.getClass());
        }
        return encodePendingDraft(sessionId, draft);
    }

    public String encodeSnapshot(String sessionId, List<Catalog.Product> products) {
        Objects.requireNonNull(sessionId, "sessionId no puede ser null");
        Objects.requireNonNull(products, "products no puede ser null");
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("version", 1);
            root.put("sessionId", sessionId);

            ArrayNode arr = mapper.createArrayNode();
            for (Catalog.Product p : products) {
                if (p == null) throw new IllegalArgumentException("Product no puede ser null");
                ObjectNode pNode = mapper.createObjectNode();
                pNode.put("id", p.id());
                pNode.put("sessionId", p.sessionId());
                pNode.put("pieceNumber", p.pieceNumber());
                pNode.put("title", p.title());
                pNode.put("price", p.price());
                pNode.put("imageUrl", p.imageUrl());
                pNode.put("sold", p.sold());
                arr.add(pNode);
            }
            root.set("products", arr);
            return mapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new IllegalStateException("Error al codificar snapshot", e);
        }
    }

    // ==========================================
    // DECODE PENDING
    // ==========================================

    public record DecodedPending(
            boolean isLegacy,
            BotModel.Draft draft,
            BotModel.Action action // null si no es una accion completa o si faltan datos
    ) {}

    /**
     * Decodifica y valida estrictamente el JSON de sessions.pending para la sesión dada.
     * Retorna Optional.empty() si el JSON es corrupto o inválido para esta sesión.
     */
    public Optional<DecodedPending> decodePending(String json, String expectedSessionId, String tempImage) {
        JsonNode root = readTreeStrict(json);
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }

        try {
            // Caso 1: Envelope v1
            if (root.has("version")) {
                Integer version = parseStrictInt(root.get("version"), 1, 1);
                if (version == null) {
                    return Optional.empty(); // version desconocida, no convertible o fuera de 1
                }

                // Validar campos exactos permitidos en root
                Set<String> allowedRoot = Set.of("version", "sessionId", "draft");
                Iterator<String> fieldNames = root.fieldNames();
                while (fieldNames.hasNext()) {
                    if (!allowedRoot.contains(fieldNames.next())) {
                        return Optional.empty(); // campo inesperado
                    }
                }

                JsonNode sNode = root.get("sessionId");
                if (sNode == null || !sNode.isTextual() || !sNode.asText().equals(expectedSessionId)) {
                    return Optional.empty(); // sesion cruzada o invalida
                }

                JsonNode draftNode = root.get("draft");
                if (draftNode == null || !draftNode.isObject() || draftNode.isEmpty()) {
                    return Optional.empty(); // Envelope con draft vacio o corrupto rechazado (§6, F4)
                }

                Set<String> allowedDraft = Set.of("operation", "target", "title", "price", "imageUrl", "candidates");
                Iterator<String> dFields = draftNode.fieldNames();
                while (dFields.hasNext()) {
                    if (!allowedDraft.contains(dFields.next())) {
                        return Optional.empty(); // campo inesperado en draft
                    }
                }

                BotModel.Operation op = null;
                if (draftNode.has("operation")) {
                    JsonNode opNode = draftNode.get("operation");
                    if (!opNode.isTextual()) return Optional.empty();
                    try {
                        op = BotModel.Operation.valueOf(opNode.asText());
                    } catch (IllegalArgumentException e) {
                        return Optional.empty();
                    }
                }

                // Incompatibilidad de campos por operacion en envelope v1 (§6, F4)
                if (op == BotModel.Operation.SOLD) {
                    if (draftNode.has("title") || draftNode.has("price") || draftNode.has("imageUrl")) {
                        return Optional.empty();
                    }
                } else if (op == BotModel.Operation.PRICE) {
                    if (draftNode.has("title") || draftNode.has("imageUrl")) {
                        return Optional.empty();
                    }
                } else if (op == BotModel.Operation.IMAGE) {
                    if (draftNode.has("title") || draftNode.has("price")) {
                        return Optional.empty();
                    }
                } else if (op == BotModel.Operation.CREATE) {
                    if (draftNode.has("target") || draftNode.has("candidates")) {
                        return Optional.empty();
                    }
                }

                Integer target = null;
                if (draftNode.has("target")) {
                    target = parseStrictInt(draftNode.get("target"), 1, 2147483646);
                    if (target == null) return Optional.empty();
                }

                String title = null;
                if (draftNode.has("title")) {
                    JsonNode titNode = draftNode.get("title");
                    if (!titNode.isTextual()) return Optional.empty();
                    String rawTitle = titNode.asText();
                    title = String.join(" ", rawTitle.trim().split("\\s+"));
                    if (title.isEmpty() || title.length() > 100) return Optional.empty();
                }

                Integer price = null;
                if (draftNode.has("price")) {
                    price = parseStrictInt(draftNode.get("price"), 1, 2147483647);
                    if (price == null) return Optional.empty();
                }

                String imageUrl = null;
                if (draftNode.has("imageUrl")) {
                    JsonNode imgNode = draftNode.get("imageUrl");
                    if (!imgNode.isTextual()) return Optional.empty();
                    imageUrl = imgNode.asText().trim();
                    if (imageUrl.isEmpty()) imageUrl = null;
                }

                List<Integer> candidates = null;
                if (draftNode.has("candidates")) {
                    JsonNode cNode = draftNode.get("candidates");
                    if (!cNode.isArray()) return Optional.empty();
                    List<Integer> cList = new ArrayList<>();
                    for (JsonNode elem : cNode) {
                        Integer cVal = parseStrictInt(elem, 1, 2147483646);
                        if (cVal == null) return Optional.empty();
                        cList.add(cVal);
                    }
                    candidates = cList;
                }

                // Draft debe contener datos útiles y no estar completamente vacío
                if (op == null && target == null && title == null && price == null && imageUrl == null && (candidates == null || candidates.isEmpty())) {
                    return Optional.empty();
                }

                // Si viene tempImage en la sesion y imageUrl en draft, deben coincidir exactamente si ambos presentes
                if (tempImage != null && imageUrl != null && !tempImage.equals(imageUrl)) {
                    return Optional.empty();
                }
                if (imageUrl == null && tempImage != null) {
                    imageUrl = tempImage;
                }

                BotModel.Draft draft = new BotModel.Draft(op, target, title, price, imageUrl, candidates);
                BotModel.Action action = buildActionIfComplete(draft);
                return Optional.of(new DecodedPending(false, draft, action));
            }

            // Caso 2: Legacy Intent(kind,number,title,price,image)
            Set<String> allowedLegacy = Set.of("kind", "number", "title", "price", "image");
            Iterator<String> lFields = root.fieldNames();
            while (lFields.hasNext()) {
                if (!allowedLegacy.contains(lFields.next())) {
                    return Optional.empty(); // campo ajeno a Intent legacy
                }
            }

            if (!root.has("kind") || !root.get("kind").isTextual()) {
                return Optional.empty();
            }
            String kind = root.get("kind").asText();
            BotModel.Operation op = switch (kind) {
                case "CREATE" -> BotModel.Operation.CREATE;
                case "PRICE" -> BotModel.Operation.PRICE;
                case "IMAGE" -> BotModel.Operation.IMAGE;
                case "SOLD" -> BotModel.Operation.SOLD;
                default -> null;
            };
            if (op == null) return Optional.empty();

            Integer number = null;
            if (root.has("number") && !root.get("number").isNull()) {
                number = parseStrictInt(root.get("number"), 1, 2147483646);
                if (number == null) return Optional.empty();
            }

            String title = null;
            if (root.has("title") && !root.get("title").isNull()) {
                JsonNode titNode = root.get("title");
                if (!titNode.isTextual()) return Optional.empty();
                title = titNode.asText().trim();
                if (title.isEmpty()) title = null;
            }

            Integer price = null;
            if (root.has("price") && !root.get("price").isNull()) {
                price = parseStrictInt(root.get("price"), 1, 2147483647);
                if (price == null) return Optional.empty();
            }

            String image = null;
            if (root.has("image") && !root.get("image").isNull()) {
                JsonNode iNode = root.get("image");
                if (!iNode.isTextual()) return Optional.empty();
                image = iNode.asText().trim();
                if (image.isEmpty()) image = null;
            }

            // Ignorar campos no aplicables según la operacion
            Integer target = null;
            if (op == BotModel.Operation.CREATE) {
                target = null; // CREATE no usa target
            } else {
                target = number;
                title = null; // PRICE, IMAGE, SOLD no usan title
                if (op != BotModel.Operation.PRICE) price = null;
                if (op != BotModel.Operation.IMAGE) image = null;
            }

            if (tempImage != null && image != null && !tempImage.equals(image)) {
                return Optional.empty();
            }
            if (image == null && tempImage != null) {
                image = tempImage;
            }

            BotModel.Draft draft = new BotModel.Draft(op, target, title, price, image, null);
            BotModel.Action action = buildActionIfComplete(draft);
            return Optional.of(new DecodedPending(true, draft, action));

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private BotModel.Action buildActionIfComplete(BotModel.Draft draft) {
        if (draft.operation() == null) return null;
        try {
            return switch (draft.operation()) {
                case CREATE -> (draft.title() != null && draft.price() != null && draft.imageUrl() != null)
                        ? new BotModel.Create(draft.title(), draft.price(), draft.imageUrl()) : null;
                case PRICE -> (draft.target() != null && draft.price() != null)
                        ? new BotModel.ChangePrice(draft.target(), draft.price()) : null;
                case IMAGE -> (draft.target() != null && draft.imageUrl() != null)
                        ? new BotModel.ChangeImage(draft.target(), draft.imageUrl()) : null;
                case SOLD -> (draft.target() != null)
                        ? new BotModel.Sell(draft.target()) : null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    // ==========================================
    // DECODE SNAPSHOT
    // ==========================================

    /**
     * Decodifica y valida estrictamente el snapshot para la sesión dada.
     * Retorna Optional.empty() si el JSON no es válido, no pertenece a la sesión, o está corrupto.
     */
    public Optional<List<Catalog.Product>> decodeSnapshot(String json, String expectedSessionId) {
        JsonNode root = readTreeStrict(json);
        if (root == null) {
            return Optional.empty();
        }

        try {
            // Caso 1: Envelope v1 {"version":1,"sessionId":"...","products":[...]}
            if (root.isObject() && root.has("version")) {
                Integer version = parseStrictInt(root.get("version"), 1, 1);
                if (version == null) return Optional.empty();

                Set<String> allowedRoot = Set.of("version", "sessionId", "products");
                Iterator<String> rFields = root.fieldNames();
                while (rFields.hasNext()) {
                    if (!allowedRoot.contains(rFields.next())) return Optional.empty();
                }

                JsonNode sNode = root.get("sessionId");
                if (sNode == null || !sNode.isTextual() || !sNode.asText().equals(expectedSessionId)) {
                    return Optional.empty();
                }

                JsonNode pArr = root.get("products");
                if (pArr == null || !pArr.isArray()) return Optional.empty();

                return parseAndValidateProducts(pArr, expectedSessionId);
            }

            // Caso 2: Legacy snapshot List<Product> array directo [...]
            if (root.isArray()) {
                return parseAndValidateProducts(root, expectedSessionId);
            }

            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<List<Catalog.Product>> parseAndValidateProducts(JsonNode arr, String expectedSessionId) {
        List<Catalog.Product> result = new ArrayList<>();
        Set<Integer> pieceNumbers = new HashSet<>();
        Set<String> allowedFields = Set.of("id", "sessionId", "pieceNumber", "title", "price", "imageUrl", "sold");

        for (JsonNode item : arr) {
            if (!item.isObject()) return Optional.empty();

            Iterator<String> fNames = item.fieldNames();
            while (fNames.hasNext()) {
                if (!allowedFields.contains(fNames.next())) return Optional.empty();
            }

            // id: long positivo 1..Long.MAX_VALUE
            Long id = parseStrictLong(item.get("id"), 1L, Long.MAX_VALUE);
            if (id == null) return Optional.empty();

            // sessionId: coincidencia exacta con expectedSessionId
            JsonNode sNode = item.get("sessionId");
            if (sNode == null || !sNode.isTextual() || !sNode.asText().equals(expectedSessionId)) {
                return Optional.empty();
            }

            // pieceNumber: int en rango 1..2147483646 y unico en el lote
            Integer pieceNumber = parseStrictInt(item.get("pieceNumber"), 1, 2147483646);
            if (pieceNumber == null) return Optional.empty();
            if (!pieceNumbers.add(pieceNumber)) return Optional.empty(); // duplicado en lote

            // title: textual no vacio (no limit 100 for historical snapshot per design; no trimming to preserve exact snapshot roundtrip)
            JsonNode titNode = item.get("title");
            if (titNode == null || !titNode.isTextual() || titNode.asText().trim().isEmpty()) {
                return Optional.empty();
            }
            String title = titNode.asText();

            // price: int positivo 1..2147483647
            Integer price = parseStrictInt(item.get("price"), 1, 2147483647);
            if (price == null) return Optional.empty();

            // imageUrl: textual no vacio (no trimming to preserve exact snapshot roundtrip)
            JsonNode imgNode = item.get("imageUrl");
            if (imgNode == null || !imgNode.isTextual() || imgNode.asText().trim().isEmpty()) {
                return Optional.empty();
            }
            String imageUrl = imgNode.asText();

            // sold: boolean real
            JsonNode soldNode = item.get("sold");
            if (soldNode == null || !soldNode.isBoolean()) return Optional.empty();
            boolean sold = soldNode.asBoolean();

            result.add(new Catalog.Product(id, expectedSessionId, pieceNumber, title, price, imageUrl, sold));
        }

        return Optional.of(result);
    }
}
