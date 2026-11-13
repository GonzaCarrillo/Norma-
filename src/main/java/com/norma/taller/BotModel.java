package com.norma.taller;

import java.util.List;
import java.util.Objects;

/**
 * Contratos de dominio tipados y FSM para la conversación del bot (Issue #3).
 */
public final class BotModel {
    private BotModel() {}

    /**
     * Estados canónicos de la conversación.
     */
    public enum State {
        IDLE,
        AWAITING_DETAILS,
        AWAITING_CONFIRMATION
    }

    /**
     * Tipo de operación soportada por el bot.
     */
    public enum Operation {
        CREATE,
        PRICE,
        IMAGE,
        SOLD
    }

    /**
     * Acciones completas, válidas e inmutables listas para ejecutar al confirmar.
     */
    public sealed interface Action permits Create, ChangePrice, ChangeImage, Sell {}

    public record Create(String title, int price, String imageUrl) implements Action {
        public Create {
            Objects.requireNonNull(title, "title no puede ser null");
            if (title.trim().isEmpty()) {
                throw new IllegalArgumentException("title no puede ser vacio");
            }
            if (price <= 0) {
                throw new IllegalArgumentException("price debe ser positivo");
            }
            Objects.requireNonNull(imageUrl, "imageUrl no puede ser null");
            if (imageUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("imageUrl no puede ser vacia");
            }
            title = title.trim();
        }
    }

    public record ChangePrice(int pieceNumber, int newPrice) implements Action {
        public ChangePrice {
            if (pieceNumber < 1 || pieceNumber > 2147483646) {
                throw new IllegalArgumentException("pieceNumber fuera de rango: " + pieceNumber);
            }
            if (newPrice <= 0) {
                throw new IllegalArgumentException("newPrice debe ser positivo: " + newPrice);
            }
        }
    }

    public record ChangeImage(int pieceNumber, String newImageUrl) implements Action {
        public ChangeImage {
            if (pieceNumber < 1 || pieceNumber > 2147483646) {
                throw new IllegalArgumentException("pieceNumber fuera de rango: " + pieceNumber);
            }
            Objects.requireNonNull(newImageUrl, "newImageUrl no puede ser null");
            if (newImageUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("newImageUrl no puede ser vacia");
            }
        }
    }

    public record Sell(int pieceNumber) implements Action {
        public Sell {
            if (pieceNumber < 1 || pieceNumber > 2147483646) {
                throw new IllegalArgumentException("pieceNumber fuera de rango: " + pieceNumber);
            }
        }
    }

    /**
     * Borrador parcial de una acción en progreso.
     */
    public record Draft(
            Operation operation,
            Integer target,
            String title,
            Integer price,
            String imageUrl,
            List<Integer> candidates
    ) {
        public Draft {
            if (target != null && (target < 1 || target > 2147483646)) {
                throw new IllegalArgumentException("target fuera de rango: " + target);
            }
            if (price != null && price <= 0) {
                throw new IllegalArgumentException("price debe ser positivo: " + price);
            }
            if (title != null) {
                title = title.trim();
                if (title.isEmpty()) {
                    title = null;
                }
            }
            if (imageUrl != null && imageUrl.trim().isEmpty()) {
                imageUrl = null;
            }
            if (candidates != null) {
                candidates = List.copyOf(candidates);
            }
        }

        public boolean hasData() {
            return operation != null || target != null || title != null || price != null || imageUrl != null || (candidates != null && !candidates.isEmpty());
        }
    }

    public enum MissingField {
        OPERATION,
        TARGET,
        TITLE,
        PRICE,
        IMAGE
    }

    public enum InvalidReason {
        ZERO_OR_NEGATIVE_PRICE,
        OVERFLOW_PRICE,
        DECIMAL_OR_CENTAVOS,
        INVALID_GROUPING,
        MULTIPLE_PRICES,
        MULTIPLE_ACTIONS,
        UNKNOWN_TARGET,
        TARGET_ALREADY_SOLD,
        TITLE_TOO_LONG,
        EMPTY_TITLE,
        RESIDUAL_TEXT,
        OTHER
    }

    /**
     * Resultado del parser sobre el mensaje del usuario.
     */
    public sealed interface ParseResult permits Complete, NeedsDetails, Ambiguous, Invalid, Unknown {}

    public record Complete(Action action) implements ParseResult {
        public Complete {
            Objects.requireNonNull(action, "action no puede ser null");
        }
    }

    public record NeedsDetails(Draft draft, MissingField missingField) implements ParseResult {
        public NeedsDetails {
            Objects.requireNonNull(draft, "draft no puede ser null");
            Objects.requireNonNull(missingField, "missingField no puede ser null");
        }
    }

    public record Ambiguous(Draft draft, List<Integer> candidatePieceNumbers) implements ParseResult {
        public Ambiguous {
            Objects.requireNonNull(draft, "draft no puede ser null");
            Objects.requireNonNull(candidatePieceNumbers, "candidatePieceNumbers no puede ser null");
            candidatePieceNumbers = List.copyOf(candidatePieceNumbers);
        }
    }

    public record Invalid(InvalidReason reason, String userMessage) implements ParseResult {
        public Invalid {
            Objects.requireNonNull(reason, "reason no puede ser null");
        }
    }

    public record Unknown(String userMessage) implements ParseResult {}

    /**
     * Señal de control conversacional extraída prioritariamente.
     */
    public sealed interface Control permits Confirm, Cancel, Undo, Correction, None {}

    public record Confirm() implements Control {}
    public record Cancel() implements Control {}
    public record Undo() implements Control {}
    public record Correction(CorrectionField field, Object value) implements Control {
        public Correction {
            Objects.requireNonNull(field, "field no puede ser null");
        }
    }
    public record None() implements Control {}

    public enum CorrectionField {
        PRICE,
        TARGET,
        TITLE,
        IMAGE
    }

    /**
     * Adaptador seguro a IntentParser.Intent legacy requerido por Catalog.apply.
     */
    public static IntentParser.Intent toLegacyIntent(Action action) {
        Objects.requireNonNull(action, "action no puede ser null");
        if (action instanceof Create c) {
            return new IntentParser.Intent("CREATE", null, c.title(), c.price(), c.imageUrl());
        } else if (action instanceof ChangePrice cp) {
            return new IntentParser.Intent("PRICE", cp.pieceNumber(), null, cp.newPrice(), null);
        } else if (action instanceof ChangeImage ci) {
            return new IntentParser.Intent("IMAGE", ci.pieceNumber(), null, null, ci.newImageUrl());
        } else if (action instanceof Sell s) {
            return new IntentParser.Intent("SOLD", s.pieceNumber(), null, null, null);
        }
        throw new IllegalArgumentException("Tipo de accion no soportado: " + action.getClass());
    }
}
