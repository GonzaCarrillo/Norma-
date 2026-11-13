package com.norma.taller;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class IntentParser {

    public record Intent(String kind, Integer number, String title, Integer price, String image) {}

    // Normalización base de acentos y caracteres para reconocimiento de comandos
    public static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[¡!¿?.,]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static final Map<String, Integer> UNITS = new HashMap<>();
    private static final Map<String, Integer> TENS = new HashMap<>();
    private static final Map<String, Integer> HUNDREDS = new HashMap<>();

    static {
        String[] u = {"cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
                "diez", "once", "doce", "trece", "catorce", "quince", "dieciseis", "diecisiete", "dieciocho", "diecinueve",
                "veinte", "veintiuno", "veintidos", "veintitres", "veinticuatro", "veinticinco", "veintiseis", "veintisiete", "veintiocho", "veintinueve"};
        for (int i = 0; i < u.length; i++) UNITS.put(u[i], i);
        UNITS.put("un", 1);
        UNITS.put("una", 1);
        UNITS.put("veintiun", 21);

        String[] t = {"treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
        for (int i = 0; i < t.length; i++) TENS.put(t[i], 30 + 10 * i);

        String[] h = {"cien", "doscientos", "trescientos", "cuatrocientos", "quinientos", "seiscientos", "setecientos", "ochocientos", "novecientos"};
        for (int i = 0; i < h.length; i++) HUNDREDS.put(h[i], 100 * (i + 1));
        HUNDREDS.put("ciento", 100);
    }

    // Adaptador legacy price
    public Integer price(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        // Si viene con prefijos de corrección legacy como "no, dije ..." o "dije ..."
        // Capturar monto sobre s ORIGINAL sin normalizar para preservar puntos y comas
        Pattern corrPatt = Pattern.compile("^(?:no[¡!¿?,.]*\\s*dije|dije|el precio es)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
        Matcher mCorr = corrPatt.matcher(s);
        if (mCorr.find()) {
            s = mCorr.group(1).trim();
        }
        return parsePriceExpression(s);
    }

    // Adaptador legacy parse
    public Intent parse(String raw, String image, List<Catalog.Product> products) {
        if (raw == null) raw = "";
        String s = norm(raw);
        if (s.contains("equivoque") || s.contains("volve atras") || s.equals("deshacer")) {
            return new Intent("UNDO", null, null, null, null);
        }

        BotModel.ParseResult result = interpret(raw, image, products, null);
        if (result instanceof BotModel.Complete c) {
            return BotModel.toLegacyIntent(c.action());
        } else if (result instanceof BotModel.Invalid inv) {
            throw new IllegalArgumentException(inv.userMessage());
        } else if (result instanceof BotModel.Ambiguous amb) {
            throw new IllegalArgumentException("Hay varias piezas que coinciden. Decime el numerito.");
        } else if (result instanceof BotModel.NeedsDetails nd) {
            throw new IllegalArgumentException("Faltan datos para completar la acción.");
        } else {
            throw new IllegalArgumentException("No entendí qué querés hacer.");
        }
    }

    // ========================================================
    // DETECCIÓN DE CONTROL (Confirm, Cancel, Undo, Correction)
    // ========================================================

    public BotModel.Control extractControl(String raw, String image) {
        if (raw == null && image == null) return new BotModel.None();
        String rawStr = raw == null ? "" : raw.trim();
        String s = norm(rawStr);

        // Control exacto de Confirm / Cancel / Undo: NO coincidir si vienen fotos adjuntas (§4)
        // Ni frases compuestas como "no se", "si pero...", "si, no"
        if (image == null) {
            // Undo exacto
            if (s.equals("deshacer") || s.equals("me equivoque") || s.equals("uy me equivoque")
                    || s.equals("volve atras") || s.equals("uy no me equivoque volve atras")) {
                return new BotModel.Undo();
            }

            // Cancel exacto (sin texto adicional)
            if (s.equals("no") || s.equals("para") || s.equals("cancelar") || s.equals("cancela")) {
                return new BotModel.Cancel();
            }

            // Confirm exacto (sin texto adicional)
            if (s.equals("si") || s.equals("dale") || s.equals("subilo") || s.equals("esta bien")
                    || s.equals("confirmo") || s.equals("confirmar")) {
                return new BotModel.Confirm();
            }
        }

        // Corrección explícita de precio: SOBRE rawStr intacto para no alterar puntos de miles o comas decimales
        Pattern corrPrice = Pattern.compile("^(?:no[¡!¿?,.]*\\s*dije|dije|el precio es)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
        Matcher mPrice = corrPrice.matcher(rawStr);
        if (mPrice.find()) {
            Integer p = parsePriceExpression(mPrice.group(1).trim());
            return new BotModel.Correction(BotModel.CorrectionField.PRICE, p); // p puede ser null si es invalido
        }

        // Corrección explícita de destino: "no, la pieza [ref]", "la pieza [ref]", "el numero es [ref]"
        Pattern corrTarget = Pattern.compile("^(?:no[¡!¿?,.]*\\s*la pieza|la pieza|el numero es|la numero)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
        Matcher mTarget = corrTarget.matcher(rawStr);
        if (mTarget.find()) {
            Integer t = parseTargetNumber(norm(mTarget.group(1)));
            return new BotModel.Correction(BotModel.CorrectionField.TARGET, t);
        }

        // Corrección explícita de título: "el nombre es [titulo]"
        Pattern corrTitle = Pattern.compile("^(?:el nombre es|el titulo es)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
        Matcher mTit = corrTitle.matcher(rawStr);
        if (mTit.find()) {
            String tit = mTit.group(1).trim();
            return new BotModel.Correction(BotModel.CorrectionField.TITLE, tit);
        }

        // Corrección de foto: "esta es la foto correcta"
        if (s.equals("esta es la foto correcta") && image != null) {
            return new BotModel.Correction(BotModel.CorrectionField.IMAGE, image);
        }

        return new BotModel.None();
    }

    // ========================================================
    // PARSER DETERMINISTA E INTERPRETACIÓN PRINCIPAL
    // ========================================================

    public BotModel.ParseResult interpret(String rawText, String image, List<Catalog.Product> products, BotModel.Draft currentDraft) {
        if (products == null) products = List.of();
        String raw = rawText != null ? rawText.trim() : "";
        String s = norm(raw);

        // 1. Mensaje vacío sin foto
        if (raw.isEmpty() && image == null) {
            return new BotModel.Unknown("Mandame una foto o contame qué pieza querés cambiar.");
        }

        // 2. Extraer referencias a piezas del mensaje (#N, pieza N, numero N, la numero N)
        ExtractedReferences refs = extractPieceReferences(raw);
        if (refs.conflict) {
            return new BotModel.Invalid(BotModel.InvalidReason.MULTIPLE_ACTIONS,
                    "Encontré dos números de pieza distintos. Decime uno solo para saber cuál cambiar.");
        }

        // 3. Determinar si estamos en contexto de completar Draft existente en DETAILS
        if (currentDraft != null && currentDraft.operation() != null) {
            return handleDetailsCompletion(raw, s, image, products, currentDraft, refs);
        }

        // Si currentDraft tiene imageUrl pero operation es null (foto previa sola)
        String inheritedImage = (image != null) ? image : (currentDraft != null ? currentDraft.imageUrl() : null);

        // 4. Reconocimiento de acciones por gramática y límites de segmento
        // Si hay una coordinación explícita " y " con verbos en ambos lados: ej "Sacá la pieza 2 y agregá Plato a 5000"
        if (s.contains(" y ")) {
            String[] segments = s.split("\\s+y\\s+");
            int segActions = 0;
            for (String seg : segments) {
                if (hasActionCommand(seg)) {
                    segActions++;
                }
            }
            if (segActions > 1) {
                return new BotModel.Invalid(BotModel.InvalidReason.MULTIPLE_ACTIONS,
                        "Me pediste varias acciones en el mismo mensaje. Por favor decime una sola por vez.");
            }
        }

        // Reconocimiento ordenado de comandos
        // A. Intento de CREATE: "Agregá/Publicá/Subí [nombre] a [precio]", "Es/Esta es [nombre] a [precio]", o "[nombre] a [precio]"
        Pattern createPattern = Pattern.compile("^(?:agrega|agregá|publica|publicá|subi|subí|es|esta es)?\\s*(.+?)\\s+(?:a|por|precio)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
        Matcher cm = createPattern.matcher(raw);
        boolean isCreateCandidate = cm.find();

        // Si es CREATE, verificar si NO empieza con comando explícito de otra acción (ej "cambia el precio de...", "sacá...")
        boolean startsWithOtherAction = s.matches("^(?:saca|sacala|sacale|vendi|vendida|quita|quitala|retira|retirala|cambia|cambiale|bajala)\\b.*");

        if (isCreateCandidate && !startsWithOtherAction && refs.number == null) {
            String titlePart = cm.group(1).trim();
            titlePart = titlePart.replaceFirst("(?i)^(?:agrega|agregá|publica|publicá|subi|subí|es|esta es)\\s+", "").trim();
            String pricePart = cm.group(2).trim();

            Integer price = parsePriceExpression(pricePart);
            if (price == null) {
                return new BotModel.Invalid(BotModel.InvalidReason.DECIMAL_OR_CENTAVOS,
                        "Decime un precio mayor a cero y en pesos enteros, por ejemplo quince mil pesos.");
            }
            if (titlePart.length() > 100) {
                return new BotModel.Invalid(BotModel.InvalidReason.TITLE_TOO_LONG,
                        "El nombre es demasiado largo. Decime un nombre de hasta cien letras.");
            }
            if (titlePart.isEmpty()) {
                return new BotModel.Invalid(BotModel.InvalidReason.EMPTY_TITLE,
                        "Decime el nombre de la pieza.");
            }

            if (inheritedImage == null) {
                return new BotModel.NeedsDetails(
                        new BotModel.Draft(BotModel.Operation.CREATE, null, titlePart, price, null, null),
                        BotModel.MissingField.IMAGE
                );
            }
            return new BotModel.Complete(new BotModel.Create(titlePart, price, inheritedImage));
        }

        // B. IMAGE: cambio de foto
        boolean hasImage = s.matches(".*(cambia.*foto|foto.*cambia|cambiale la foto).*");
        if (hasImage) {
            return handleImage(raw, s, inheritedImage, products, refs);
        }

        // C. SOLD: venta o retiro
        boolean hasSold = s.matches(".*\\b(vendi|vendida|saca|sacala|sacale|quita|quitala|retira|retirala)\\b.*");
        if (hasSold) {
            return handleSold(raw, s, products, refs);
        }

        // D. PRICE: cambio de precio
        boolean hasPriceVerb = s.matches(".*\\b(subila|bajala|cambia el precio|ponele|precio|vale|cobr|cambiale el precio)\\b.*")
                || (refs.number != null && s.matches(".*\\b(a|por)\\b.*"));
        if (hasPriceVerb) {
            return handlePrice(raw, s, products, refs);
        }

        // E. Foto sola
        if (raw.isEmpty() && inheritedImage != null) {
            return new BotModel.NeedsDetails(
                    new BotModel.Draft(null, null, null, null, inheritedImage, null),
                    BotModel.MissingField.OPERATION
            );
        }

        // F. Foto con texto que es nombre solo (CREATE esperando precio)
        if (inheritedImage != null && !raw.isEmpty()) {
            if (raw.length() <= 100) {
                return new BotModel.NeedsDetails(
                        new BotModel.Draft(BotModel.Operation.CREATE, null, raw, null, inheritedImage, null),
                        BotModel.MissingField.PRICE
                );
            }
        }

        return new BotModel.Unknown("Decime qué pieza querés cambiar, o mandame una foto con nombre y precio.");
    }

    private boolean hasActionCommand(String segment) {
        String seg = segment.trim();
        return seg.matches("^(?:agrega|agregá|publica|publicá|subi|subí|es|esta es)\\b.*")
                || seg.matches(".*\\b(vendi|vendida|saca|sacala|sacale|quita|quitala|retira|retirala)\\b.*")
                || seg.matches(".*(cambia.*foto|foto.*cambia).*")
                || seg.matches(".*\\b(subila|bajala|cambia el precio|cambiale el precio)\\b.*");
    }

    private BotModel.ParseResult handleSold(String raw, String s, List<Catalog.Product> products, ExtractedReferences refs) {
        ResolvedTarget target = resolveTarget(raw, s, products, refs, "saca|sacala|sacale|vendi|vendida|quita|quitala|retira|retirala");
        if (target.ambiguousCandidates != null) {
            return new BotModel.Ambiguous(
                    new BotModel.Draft(BotModel.Operation.SOLD, null, null, null, null, target.ambiguousCandidates),
                    target.ambiguousCandidates
            );
        }
        if (target.notFound) {
            return new BotModel.Invalid(BotModel.InvalidReason.UNKNOWN_TARGET,
                    "No encuentro esa pieza disponible. Decime otro numerito.");
        }
        if (target.alreadySold) {
            return new BotModel.Invalid(BotModel.InvalidReason.TARGET_ALREADY_SOLD,
                    "Esa pieza ya está marcada como vendida.");
        }
        if (target.pieceNumber == null) {
            return new BotModel.NeedsDetails(
                    new BotModel.Draft(BotModel.Operation.SOLD, null, null, null, null, null),
                    BotModel.MissingField.TARGET
            );
        }
        return new BotModel.Complete(new BotModel.Sell(target.pieceNumber));
    }

    private BotModel.ParseResult handleImage(String raw, String s, String image, List<Catalog.Product> products, ExtractedReferences refs) {
        ResolvedTarget target = resolveTarget(raw, s, products, refs, "cambia.*foto|foto.*cambia|cambiale la foto");
        if (target.ambiguousCandidates != null) {
            return new BotModel.Ambiguous(
                    new BotModel.Draft(BotModel.Operation.IMAGE, null, null, null, image, target.ambiguousCandidates),
                    target.ambiguousCandidates
            );
        }
        if (target.notFound) {
            return new BotModel.Invalid(BotModel.InvalidReason.UNKNOWN_TARGET,
                    "No encuentro esa pieza disponible. Decime otro numerito.");
        }
        if (target.pieceNumber == null) {
            return new BotModel.NeedsDetails(
                    new BotModel.Draft(BotModel.Operation.IMAGE, null, null, null, image, null),
                    BotModel.MissingField.TARGET
            );
        }
        if (image == null) {
            return new BotModel.NeedsDetails(
                    new BotModel.Draft(BotModel.Operation.IMAGE, target.pieceNumber, null, null, null, null),
                    BotModel.MissingField.IMAGE
            );
        }
        return new BotModel.Complete(new BotModel.ChangeImage(target.pieceNumber, image));
    }

    private BotModel.ParseResult handlePrice(String raw, String s, List<Catalog.Product> products, ExtractedReferences refs) {
        ResolvedTarget target = resolveTarget(raw, s, products, refs, "subila|bajala|cambia el precio|ponele|precio|vale|cobr|cambiale el precio");
        if (target.ambiguousCandidates != null) {
            return new BotModel.Ambiguous(
                    new BotModel.Draft(BotModel.Operation.PRICE, null, null, null, null, target.ambiguousCandidates),
                    target.ambiguousCandidates
            );
        }
        if (target.notFound) {
            return new BotModel.Invalid(BotModel.InvalidReason.UNKNOWN_TARGET,
                    "No encuentro esa pieza disponible. Decime otro numerito.");
        }

        // Aislar expresión de precio sobre texto original intacto
        String priceText = extractPricePortion(raw, refs);
        Integer price = parsePriceExpression(priceText);

        if (target.pieceNumber == null && price == null) {
            return new BotModel.NeedsDetails(
                    new BotModel.Draft(BotModel.Operation.PRICE, null, null, null, null, null),
                    BotModel.MissingField.TARGET
            );
        }
        if (target.pieceNumber == null) {
            return new BotModel.NeedsDetails(
                    new BotModel.Draft(BotModel.Operation.PRICE, null, null, price, null, null),
                    BotModel.MissingField.TARGET
            );
        }
        if (price == null) {
            return new BotModel.NeedsDetails(
                    new BotModel.Draft(BotModel.Operation.PRICE, target.pieceNumber, null, null, null, null),
                    BotModel.MissingField.PRICE
            );
        }
        return new BotModel.Complete(new BotModel.ChangePrice(target.pieceNumber, price));
    }

    private BotModel.ParseResult handleDetailsCompletion(String raw, String s, String image, List<Catalog.Product> products,
                                                         BotModel.Draft draft, ExtractedReferences refs) {
        BotModel.Operation op = draft.operation();
        Integer target = draft.target();
        String title = draft.title();
        Integer price = draft.price();
        String imageUrl = draft.imageUrl() != null ? draft.imageUrl() : image;

        // Si faltaba foto y llega la foto
        if (imageUrl == null && image != null) {
            imageUrl = image;
        }

        // Si faltaba target y llega un número o candidato
        if (target == null && (op == BotModel.Operation.PRICE || op == BotModel.Operation.IMAGE || op == BotModel.Operation.SOLD)) {
            ResolvedTarget rt = resolveTarget(raw, s, products, refs, "");
            if (rt.pieceNumber != null) {
                target = rt.pieceNumber;
            } else if (rt.ambiguousCandidates != null) {
                return new BotModel.Ambiguous(
                        new BotModel.Draft(op, null, title, price, imageUrl, rt.ambiguousCandidates),
                        rt.ambiguousCandidates
                );
            } else if (rt.notFound) {
                return new BotModel.Invalid(BotModel.InvalidReason.UNKNOWN_TARGET, "No encuentro esa pieza disponible. Decime otro numerito.");
            }
        }

        // Si faltaba precio y llega monto
        if (price == null && (op == BotModel.Operation.CREATE || op == BotModel.Operation.PRICE)) {
            Integer p = parsePriceExpression(raw);
            if (p != null) {
                price = p;
            }
        }

        // Si faltaba título y llega texto (para CREATE)
        if (title == null && op == BotModel.Operation.CREATE && !raw.isEmpty()) {
            if (raw.length() <= 100) {
                title = raw.trim();
            }
        }

        BotModel.Draft updated = new BotModel.Draft(op, target, title, price, imageUrl, null);

        // Verificar si está completo
        return switch (op) {
            case CREATE -> (title != null && price != null && imageUrl != null)
                    ? new BotModel.Complete(new BotModel.Create(title, price, imageUrl))
                    : new BotModel.NeedsDetails(updated, title == null ? BotModel.MissingField.TITLE : (price == null ? BotModel.MissingField.PRICE : BotModel.MissingField.IMAGE));
            case PRICE -> (target != null && price != null)
                    ? new BotModel.Complete(new BotModel.ChangePrice(target, price))
                    : new BotModel.NeedsDetails(updated, target == null ? BotModel.MissingField.TARGET : BotModel.MissingField.PRICE);
            case IMAGE -> (target != null && imageUrl != null)
                    ? new BotModel.Complete(new BotModel.ChangeImage(target, imageUrl))
                    : new BotModel.NeedsDetails(updated, target == null ? BotModel.MissingField.TARGET : BotModel.MissingField.IMAGE);
            case SOLD -> (target != null)
                    ? new BotModel.Complete(new BotModel.Sell(target))
                    : new BotModel.NeedsDetails(updated, BotModel.MissingField.TARGET);
        };
    }

    // ========================================================
    // RESOLUCIÓN DE DESTINO (NUMERITO / NOMBRE)
    // ========================================================

    private static class ResolvedTarget {
        Integer pieceNumber;
        List<Integer> ambiguousCandidates;
        boolean notFound;
        boolean alreadySold;
    }

    private ResolvedTarget resolveTarget(String raw, String s, List<Catalog.Product> products, ExtractedReferences refs, String verbRegex) {
        ResolvedTarget res = new ResolvedTarget();

        // 1. Número explícito en refs
        if (refs.number != null) {
            int num = refs.number;
            Optional<Catalog.Product> pOpt = products.stream().filter(p -> p.pieceNumber() == num).findFirst();
            if (pOpt.isEmpty()) {
                res.notFound = true;
                return res;
            }
            if (pOpt.get().sold()) {
                res.alreadySold = true;
                return res;
            }
            res.pieceNumber = num;
            return res;
        }

        // 2. Coincidencia por nombre en piezas disponibles
        // Limpiar verbos y palabras de control de s
        String clean = s.replaceAll("^(?:" + verbRegex + ")\\s*", "").trim();
        clean = clean.replaceFirst("^(?:la|el|una|un)\\s+", "").trim();
        // Quitar también porción de precio si existiera (ej: "a 25000")
        clean = clean.replaceFirst("\\s+(?:a|por|precio)\\s+.*$", "").trim();
        if (clean.isEmpty()) {
            return res;
        }

        List<Catalog.Product> available = products.stream().filter(p -> !p.sold()).toList();
        List<Catalog.Product> exactMatches = new ArrayList<>();
        List<Catalog.Product> wordMatches = new ArrayList<>();

        for (Catalog.Product p : available) {
            String pNorm = norm(p.title());
            if (pNorm.equals(clean)) {
                exactMatches.add(p);
            } else if (matchesFullWordSequence(clean, pNorm)) {
                wordMatches.add(p);
            }
        }

        if (exactMatches.size() == 1) {
            res.pieceNumber = exactMatches.get(0).pieceNumber();
            return res;
        }
        if (exactMatches.size() > 1) {
            res.ambiguousCandidates = exactMatches.stream().map(Catalog.Product::pieceNumber).toList();
            return res;
        }

        if (wordMatches.size() == 1) {
            res.pieceNumber = wordMatches.get(0).pieceNumber();
            return res;
        }
        if (wordMatches.size() > 1) {
            res.ambiguousCandidates = wordMatches.stream().map(Catalog.Product::pieceNumber).toList();
            return res;
        }

        return res;
    }

    private static boolean matchesFullWordSequence(String query, String target) {
        String[] qWords = query.split("\\s+");
        String[] tWords = target.split("\\s+");
        if (qWords.length == 0 || qWords.length > tWords.length) return false;
        for (int i = 0; i <= tWords.length - qWords.length; i++) {
            boolean match = true;
            for (int j = 0; j < qWords.length; j++) {
                if (!qWords[j].equals(tWords[i + j])) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }
        return false;
    }

    // ========================================================
    // PARSEO DE REFERENCIAS (#N, pieza N, numero N)
    // ========================================================

    private static class ExtractedReferences {
        Integer number;
        boolean conflict;
        List<String> matchedSpans = new ArrayList<>();
    }

    private ExtractedReferences extractPieceReferences(String raw) {
        ExtractedReferences res = new ExtractedReferences();
        String s = norm(raw);

        // Pattern para: #N, pieza N, numero N, la numero N
        Pattern p = Pattern.compile("(?:#\\s*(\\d+|[a-z]+)|(?:pieza|numero|la\\s+numero)\\s+(\\d+|[a-z]+))");
        Matcher m = p.matcher(s);

        Set<Integer> found = new HashSet<>();
        while (m.find()) {
            String token = m.group(1) != null ? m.group(1) : m.group(2);
            Integer val = parseTargetNumber(token);
            if (val != null) {
                found.add(val);
                res.matchedSpans.add(m.group(0));
            }
        }

        if (found.size() > 1) {
            res.conflict = true;
        } else if (found.size() == 1) {
            res.number = found.iterator().next();
        }
        return res;
    }

    private Integer parseTargetNumber(String token) {
        if (token == null) return null;
        token = token.trim();
        if (token.matches("\\d+")) {
            try {
                long l = Long.parseLong(token);
                if (l >= 1 && l <= 2147483646) return (int) l;
            } catch (Exception ignored) {}
            return null;
        }
        Integer wordVal = UNITS.get(token);
        if (wordVal != null && wordVal >= 1) return wordVal;
        return null;
    }

    private String extractPricePortion(String raw, ExtractedReferences refs) {
        String s = raw;
        // Eliminar referencias coincidentes para no mezclar #2 con precio
        for (String span : refs.matchedSpans) {
            s = s.replaceAll("(?i)" + Pattern.quote(span), " ");
        }

        // Buscar el último delimitador entre "\s+a\s+", "\s+por\s+", o "\bprecio\s+"
        Pattern delimPattern = Pattern.compile("(?:\\s+a\\s+|\\s+por\\s+|\\bprecio\\s+)", Pattern.CASE_INSENSITIVE);
        Matcher m = delimPattern.matcher(s);
        int lastMatchEnd = -1;
        while (m.find()) {
            lastMatchEnd = m.end();
        }
        if (lastMatchEnd != -1) {
            return s.substring(lastMatchEnd).trim();
        }
        return s.trim();
    }

    // ========================================================
    // PARSEO ESTRICTO DE EXPRESIONES DE PRECIO (§4)
    // ========================================================

    public Integer parsePriceExpression(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.isEmpty()) return null;

        // Rechazo de signos negativos, +, exponentes, comas (decimales/centavos)
        // No chequear contains("e") sobre el texto completo porque palabras como "dieciocho", "quince" tienen 'e'!
        // Exponentes solo en contexto de números: ej 1e5
        if (s.contains(",") || s.contains("+") || s.matches(".*\\bmenos\\b.*") || s.matches(".*-\\s*\\d.*") || s.matches(".*\\d+[eE]\\d+.*")) {
            return null;
        }

        // Quitar signo peso inicial si existe
        if (s.startsWith("$")) {
            s = s.substring(1).trim();
        }

        // Quitar sufijo "peso" o "pesos" final opcional
        s = s.replaceFirst("(?i)\\s+pesos?$", "").trim();

        // 1. Entero decimal directo (sin puntos o con puntos de miles estrictos)
        // Grupo de puntos: solo grupos de 3 digitos tras primer grupo de 1..3 digitos: ej 18.500, 2.147.483.647
        if (s.matches("^\\d{1,3}(?:\\.\\d{3})+$")) {
            String digits = s.replace(".", "");
            return checkIntPrice(digits);
        } else if (s.matches("^\\d+$")) {
            // Entero sin agrupar: sin ceros iniciales salvo 0 (que es rechazado por rango)
            if (s.length() > 1 && s.startsWith("0")) {
                return null; // ceros iniciales no permitidos
            }
            return checkIntPrice(s);
        }

        // Si contiene puntos pero no cumplió el regex estricto de agrupación: ej 15.00, 15.000.00
        if (s.contains(".")) {
            return null; // agrupación inválida
        }

        // Normalizamos texto para palabras habladas
        String normS = norm(s);

        // 2. Multiplicadores aislados: `15 mil`, `15 lucas`, `quince mil`, `quince lucas`
        // Ojo: solo si NO contiene "millon" / "millones", y el coeficiente es simple (sin sub-miles)
        if (!normS.contains("millon") && !normS.contains("millones")) {
            Pattern multPattern = Pattern.compile("^(.*?)\\s+(mil|lucas)$");
            Matcher multMatcher = multPattern.matcher(normS);
            if (multMatcher.matches()) {
                String coefStr = multMatcher.group(1).trim();
                // Coeficiente entero 1..999 en dígitos o palabras
                Long coef = parseGroup1to999(coefStr);
                if (coef != null && coef >= 1 && coef <= 999) {
                    long total = coef * 1000L;
                    return checkIntPrice(total);
                }
                return null;
            }
        }

        // 3. Hablado canónico y coloquial argentino
        return parseSpokenPrice(normS);
    }

    private Integer checkIntPrice(String digits) {
        try {
            long val = Long.parseLong(digits);
            return checkIntPrice(val);
        } catch (NumberFormatException e) {
            return null; // overflow de Long
        }
    }

    private Integer checkIntPrice(long val) {
        if (val >= 1 && val <= 2147483647L) {
            return (int) val;
        }
        return null; // cero, negativo o desborda intmax
    }

    private Long parseGroup1to999(String text) {
        String s = norm(text);
        if (s.matches("^\\d+$")) {
            try {
                long val = Long.parseLong(s);
                return (val >= 1 && val <= 999) ? val : null;
            } catch (Exception e) {
                return null;
            }
        }
        return parseSpokenGroup(s);
    }

    /**
     * Parsea un grupo hablado de 1..999 canónico: ej "quinientos", "doscientos cincuenta", "quince"
     */
    private Long parseSpokenGroup(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] words = s.split("\\s+");
        long total = 0;
        int i = 0;

        // Centena
        if (i < words.length && HUNDREDS.containsKey(words[i])) {
            total += HUNDREDS.get(words[i]);
            i++;
        }

        // Decenas / Unidades
        if (i < words.length) {
            if (UNITS.containsKey(words[i])) {
                total += UNITS.get(words[i]);
                i++;
            } else if (TENS.containsKey(words[i])) {
                total += TENS.get(words[i]);
                i++;
                if (i < words.length && words[i].equals("y")) {
                    i++;
                    if (i < words.length && UNITS.containsKey(words[i]) && UNITS.get(words[i]) < 10) {
                        total += UNITS.get(words[i]);
                        i++;
                    } else {
                        return null; // inválido tras "y"
                    }
                }
            } else {
                return null;
            }
        }

        if (i != words.length) return null; // residuos no consumidos
        return (total >= 1 && total <= 999) ? total : null;
    }

    /**
     * Parser hablado completo con millones, miles, coloquial argentino y centenas.
     */
    private Integer parseSpokenPrice(String s) {
        if (s.isEmpty()) return null;

        // Rechazo de patrones incoherentes como "mil mil", "quince veinte", "o"
        if (s.contains("mil mil") || s.matches(".*\\b(o|menos)\\b.*")) {
            return null;
        }

        // Caso "mil" a secas = 1000
        if (s.equals("mil")) {
            return 1000;
        }
        // Caso "un millon" a secas = 1000000
        if (s.equals("un millon") || s.equals("un millon de pesos")) {
            return 1000000;
        }

        // Coloquial requerido: grupo 1..99 seguido directamente por centena 100..900 (con resto 0..99)
        // ej: "dieciocho quinientos" -> 18500, "quince doscientos cincuenta" -> 15250
        // Excepción de diseño: NO aplicar a "ciento cincuenta" -> 150
        Integer colloquial = parseColloquial(s);
        if (colloquial != null) {
            return checkIntPrice(colloquial);
        }

        // Canónico estándar: millones -> miles -> centenas/resto
        return parseCanonicalSpoken(s);
    }

    private Integer parseColloquial(String s) {
        String[] words = s.split("\\s+");
        if (words.length < 2) return null;

        // Primer grupo: 1..99 (dos palabras si es "treinta y cinco")
        int idx = 0;
        long thousands = 0;
        if (UNITS.containsKey(words[idx])) {
            thousands = UNITS.get(words[idx]);
            idx++;
        } else if (TENS.containsKey(words[idx])) {
            thousands = TENS.get(words[idx]);
            idx++;
            if (idx < words.length && words[idx].equals("y")) {
                idx++;
                if (idx < words.length && UNITS.containsKey(words[idx])) {
                    thousands += UNITS.get(words[idx]);
                    idx++;
                } else {
                    return null;
                }
            }
        } else {
            return null; // no empieza con 1..99
        }

        if (thousands < 1 || thousands > 99) return null;

        // La siguiente palabra DEBE ser una centena 100..900 (no "cien" ni "ciento")
        if (idx >= words.length) return null;
        String hWord = words[idx];
        if (!HUNDREDS.containsKey(hWord) || hWord.equals("cien") || hWord.equals("ciento")) {
            return null;
        }

        // Consumir el resto como grupo 100..999
        StringBuilder restSb = new StringBuilder();
        for (int j = idx; j < words.length; j++) {
            if (!restSb.isEmpty()) restSb.append(" ");
            restSb.append(words[j]);
        }

        Long restGroup = parseSpokenGroup(restSb.toString());
        if (restGroup != null && restGroup >= 100 && restGroup <= 999) {
            long total = thousands * 1000L + restGroup;
            return checkIntPrice(total);
        }

        return null;
    }

    private Integer parseCanonicalSpoken(String s) {
        String[] words = s.split("\\s+");
        long total = 0;
        int i = 0;

        // 1. Millones
        int millonIdx = -1;
        for (int k = 0; k < words.length; k++) {
            if (words[k].equals("millon") || words[k].equals("millones")) {
                millonIdx = k;
                break;
            }
        }

        if (millonIdx != -1) {
            String mPart = String.join(" ", Arrays.copyOfRange(words, 0, millonIdx)).trim();
            Long mCoef = mPart.isEmpty() ? 1L : parseGroup1to999(mPart);
            if (mCoef == null || mCoef < 1 || mCoef > 999) return null;
            total += mCoef * 1000000L;
            i = millonIdx + 1;
            if (i < words.length && words[i].equals("de")) {
                i++; // "de pesos" opcional
            }
        }

        // 2. Miles
        int milIdx = -1;
        for (int k = i; k < words.length; k++) {
            if (words[k].equals("mil")) {
                milIdx = k;
                break;
            }
        }

        if (milIdx != -1) {
            String kPart = String.join(" ", Arrays.copyOfRange(words, i, milIdx)).trim();
            Long kCoef = kPart.isEmpty() ? 1L : parseGroup1to999(kPart);
            if (kCoef == null || kCoef < 1 || kCoef > 999) return null;
            total += kCoef * 1000L;
            i = milIdx + 1;
        }

        // 3. Resto de unidades/centenas (0..999)
        if (i < words.length) {
            String restPart = String.join(" ", Arrays.copyOfRange(words, i, words.length)).trim();
            Long rest = parseSpokenGroup(restPart);
            if (rest == null || rest < 1 || rest > 999) return null;
            total += rest;
        }

        return checkIntPrice(total);
    }
}
