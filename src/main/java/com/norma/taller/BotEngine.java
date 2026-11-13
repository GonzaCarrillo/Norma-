package com.norma.taller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Motor conversacional FSM del Bot (Issue #3).
 * Implementa la máquina de estados finitos exhaustiva (§5) y transacciones atómicas (§7).
 */
@Service
public class BotEngine {

    public record Reply(String replyText, String replyAudioUrl, String state) {}

    private final Catalog catalog;
    private final IntentParser parser;
    private final BotSessionCodec codec;

    @org.springframework.beans.factory.annotation.Autowired
    public BotEngine(Catalog catalog, IntentParser parser, ObjectMapper json) {
        this.catalog = catalog;
        this.parser = parser;
        this.codec = new BotSessionCodec(json);
    }

    public BotEngine(Catalog catalog, IntentParser parser, BotSessionCodec codec) {
        this.catalog = catalog;
        this.parser = parser;
        this.codec = codec;
    }

    @Transactional
    public Reply process(String id, String text, String image) {
        Catalog.valid(id);
        catalog.ensure(id);
        Catalog.Session session = catalog.session(id);

        String rawText = text != null ? text.trim() : "";
        String s = IntentParser.norm(rawText);

        // Control exacto conversacional (§4 y §5)
        BotModel.Control control = parser.extractControl(rawText, image);

        // ========================================================
        // 1. CONTROL: UNDO EXACTO
        // ========================================================
        if (control instanceof BotModel.Undo) {
            return handleUndo(id, session);
        }

        // ========================================================
        // 2. RECUPERACIÓN DE CORRUPCIÓN EN PENDING (§6)
        // ========================================================
        Optional<BotSessionCodec.DecodedPending> decodedPendingOpt = Optional.empty();
        if (session.pending() != null) {
            decodedPendingOpt = codec.decodePending(session.pending(), id, session.tempImage());
            if (decodedPendingOpt.isEmpty()) {
                // Pending corrupto/incompatible o sesión cruzada: limpiar solo pending/temp y volver IDLE, conservar snapshot
                catalog.conversation(id, BotModel.State.IDLE.name(), null, null);
                return reply(id, "No pude recuperar el cambio pendiente. Mandámelo de nuevo.");
            }
        }

        // Comprobar inconsistencia de estado en sesión
        BotModel.State currentState;
        try {
            currentState = BotModel.State.valueOf(session.state());
        } catch (Exception e) {
            catalog.conversation(id, BotModel.State.IDLE.name(), null, null);
            return reply(id, "No pude recuperar el cambio pendiente. Mandámelo de nuevo.");
        }

        // Si el estado es AWAITING_CONFIRMATION pero no hay decoded pending con Action válida
        if (currentState == BotModel.State.AWAITING_CONFIRMATION && (decodedPendingOpt.isEmpty() || decodedPendingOpt.get().action() == null)) {
            catalog.conversation(id, BotModel.State.IDLE.name(), null, null);
            return reply(id, "No pude recuperar el cambio pendiente. Mandámelo de nuevo.");
        }

        // ========================================================
        // 3. FSM EN AWAITING_CONFIRMATION
        // ========================================================
        if (currentState == BotModel.State.AWAITING_CONFIRMATION) {
            return handleConfirmationState(id, session, rawText, s, image, control, decodedPendingOpt.get());
        }

        // ========================================================
        // 4. FSM EN AWAITING_DETAILS
        // ========================================================
        if (currentState == BotModel.State.AWAITING_DETAILS) {
            return handleDetailsState(id, session, rawText, s, image, control, decodedPendingOpt.orElse(null));
        }

        // ========================================================
        // 5. FSM EN IDLE
        // ========================================================
        return handleIdleState(id, session, rawText, s, image, control);
    }

    // ========================================================
    // MANEJO DE UNDO
    // ========================================================

    private Reply handleUndo(String id, Catalog.Session session) {
        if (session.snapshot() == null) {
            // Si estaba en borrador DETAILS o CONFIRMATION sin snapshot, conserva borrador
            if (!session.state().equals(BotModel.State.IDLE.name())) {
                return reply(id, "Todavía no hay un cambio para deshacer.");
            }
            return reply(id, "Todavía no hay un cambio para deshacer.");
        }

        // Validar snapshot estrictamente según §6
        Optional<List<Catalog.Product>> snapOpt = codec.decodeSnapshot(session.snapshot(), id);
        if (snapOpt.isEmpty()) {
            // Snapshot corrupto/incompatible: no restaurar ni consumir ni limpiar borrador
            return reply(id, "No pude recuperar el cambio anterior. El catálogo sigue igual; podés cancelar el cambio pendiente o restaurar el catálogo original.");
        }

        // Restaurar atómicamente: restore -> snapshot null -> conversation IDLE
        catalog.restore(id, snapOpt.get());
        catalog.snapshot(id, null);
        catalog.conversation(id, BotModel.State.IDLE.name(), null, null);
        return reply(id, "Tranquila Norma, ya dejé todo como estaba antes.");
    }

    // ========================================================
    // MANEJO EN AWAITING_CONFIRMATION
    // ========================================================

    private Reply handleConfirmationState(String id, Catalog.Session session, String rawText, String s,
                                          String image, BotModel.Control control, BotSessionCodec.DecodedPending pending) {
        BotModel.Action action = pending.action();

        // Si era pending legacy, no aplicar a ciegas, reescribir envelope y repreguntar (§6)
        if (pending.isLegacy()) {
            catalog.conversation(id, BotModel.State.AWAITING_CONFIRMATION.name(), codec.encodePendingAction(id, action), session.tempImage());
            return prepareReply(id, action);
        }

        // Si vino una foto adjunta a un intento de confirmación/cancelación
        if (image != null && (control instanceof BotModel.Confirm || control instanceof BotModel.Cancel)) {
            return reply(id, "Tengo un cambio pendiente. Mandame sí para confirmar o no para cancelar, sin foto.");
        }

        // Confirmación exacta
        if (control instanceof BotModel.Confirm) {
            return executeConfirmation(id, action);
        }

        // Cancelación exacta
        if (control instanceof BotModel.Cancel) {
            catalog.conversation(id, BotModel.State.IDLE.name(), null, null);
            return reply(id, "Cancelé el cambio, mandame de nuevo tranqui.");
        }

        // Corrección explícita (§4, §5)
        if (control instanceof BotModel.Correction corr) {
            return handleCorrectionInConfirmation(id, session, image, corr, action);
        }

        // Entrada vacía
        if (rawText.isEmpty() && image == null) {
            return prepareReply(id, action);
        }

        // Si el usuario manda una foto sola
        if (rawText.isEmpty() && image != null) {
            return reply(id, "Tengo un cambio pendiente. Decime sí para confirmar, no para cancelar, o 'esta es la foto correcta' para cambiar la foto.");
        }

        // Cualquier otro mensaje: conservar borrador y explicar opciones
        return reply(id, "Hay un cambio pendiente. Decime sí para confirmar, no para cancelar, o qué querés corregir.");
    }

    private Reply executeConfirmation(String id, BotModel.Action action) {
        // Revalidación local preconfirmación contra catálogo actual (§5)
        List<Catalog.Product> currentProducts = catalog.all(id);

        if (action instanceof BotModel.ChangePrice cp) {
            Optional<Catalog.Product> targetOpt = currentProducts.stream().filter(p -> p.pieceNumber() == cp.pieceNumber()).findFirst();
            if (targetOpt.isEmpty() || targetOpt.get().sold()) {
                catalog.conversation(id, BotModel.State.AWAITING_DETAILS.name(),
                        codec.encodePendingDraft(id, new BotModel.Draft(BotModel.Operation.PRICE, null, null, cp.newPrice(), null, null)), null);
                return reply(id, "No encuentro la pieza #" + cp.pieceNumber() + " disponible. Decime otro numerito.");
            }
            // Detección de no-op
            if (targetOpt.get().price() == cp.newPrice()) {
                catalog.conversation(id, BotModel.State.IDLE.name(), null, null);
                return reply(id, "Ya estaba así, no hice cambios.");
            }
        } else if (action instanceof BotModel.ChangeImage ci) {
            Optional<Catalog.Product> targetOpt = currentProducts.stream().filter(p -> p.pieceNumber() == ci.pieceNumber()).findFirst();
            if (targetOpt.isEmpty() || targetOpt.get().sold()) {
                catalog.conversation(id, BotModel.State.AWAITING_DETAILS.name(),
                        codec.encodePendingDraft(id, new BotModel.Draft(BotModel.Operation.IMAGE, null, null, null, ci.newImageUrl(), null)), null);
                return reply(id, "No encuentro la pieza #" + ci.pieceNumber() + " disponible. Decime otro numerito.");
            }
            if (targetOpt.get().imageUrl().equals(ci.newImageUrl())) {
                catalog.conversation(id, BotModel.State.IDLE.name(), null, null);
                return reply(id, "Ya estaba así, no hice cambios.");
            }
        } else if (action instanceof BotModel.Sell sAction) {
            Optional<Catalog.Product> targetOpt = currentProducts.stream().filter(p -> p.pieceNumber() == sAction.pieceNumber()).findFirst();
            if (targetOpt.isEmpty() || targetOpt.get().sold()) {
                catalog.conversation(id, BotModel.State.AWAITING_DETAILS.name(),
                        codec.encodePendingDraft(id, new BotModel.Draft(BotModel.Operation.SOLD, null, null, null, null, null)), null);
                return reply(id, "No encuentro la pieza #" + sAction.pieceNumber() + " disponible. Decime otro numerito.");
            }
        } else if (action instanceof BotModel.Create c) {
            Catalog.Session sess = catalog.session(id);
            if (sess.nextNumber() >= 2147483647) {
                // Agotamiento de numeración
                return reply(id, "No se pueden asignar más numeritos a la sesión. Podés cancelar o restaurar el catálogo.");
            }
        }

        // Secuencia atómica de confirmación: snapshot actual completo -> apply -> IDLE
        catalog.snapshot(id, codec.encodeSnapshot(id, currentProducts));
        catalog.apply(id, BotModel.toLegacyIntent(action));
        catalog.conversation(id, BotModel.State.IDLE.name(), null, null);

        return reply(id, "¡Listo Norma! Ya quedó actualizado en la página.");
    }

    private Reply handleCorrectionInConfirmation(String id, Catalog.Session session, String image,
                                                 BotModel.Correction corr, BotModel.Action action) {
        BotModel.Action updatedAction = null;

        if (corr.field() == BotModel.CorrectionField.PRICE && corr.value() instanceof Integer newPrice) {
            if (action instanceof BotModel.Create c) {
                updatedAction = new BotModel.Create(c.title(), newPrice, c.imageUrl());
            } else if (action instanceof BotModel.ChangePrice cp) {
                updatedAction = new BotModel.ChangePrice(cp.pieceNumber(), newPrice);
            }
        } else if (corr.field() == BotModel.CorrectionField.TARGET && corr.value() instanceof Integer newTarget) {
            List<Catalog.Product> prods = catalog.all(id);
            Optional<Catalog.Product> pOpt = prods.stream().filter(p -> p.pieceNumber() == (int) newTarget && !p.sold()).findFirst();
            if (pOpt.isPresent()) {
                if (action instanceof BotModel.ChangePrice cp) {
                    updatedAction = new BotModel.ChangePrice(newTarget, cp.newPrice());
                } else if (action instanceof BotModel.ChangeImage ci) {
                    updatedAction = new BotModel.ChangeImage(newTarget, ci.newImageUrl());
                } else if (action instanceof BotModel.Sell) {
                    updatedAction = new BotModel.Sell(newTarget);
                }
            } else {
                return reply(id, "No encuentro esa pieza disponible. Decime otro numerito.");
            }
        } else if (corr.field() == BotModel.CorrectionField.TITLE && corr.value() instanceof String newTitle) {
            if (action instanceof BotModel.Create c && !newTitle.isBlank() && newTitle.length() <= 100) {
                updatedAction = new BotModel.Create(newTitle, c.price(), c.imageUrl());
            }
        } else if (corr.field() == BotModel.CorrectionField.IMAGE && image != null) {
            if (action instanceof BotModel.Create c) {
                updatedAction = new BotModel.Create(c.title(), c.price(), image);
            } else if (action instanceof BotModel.ChangeImage ci) {
                updatedAction = new BotModel.ChangeImage(ci.pieceNumber(), image);
            }
        }

        if (updatedAction != null) {
            catalog.conversation(id, BotModel.State.AWAITING_CONFIRMATION.name(),
                    codec.encodePendingAction(id, updatedAction),
                    updatedAction instanceof BotModel.Create c ? c.imageUrl() : (updatedAction instanceof BotModel.ChangeImage ci ? ci.newImageUrl() : null));
            return prepareReply(id, updatedAction);
        }

        // Si la corrección no fue válida o no aplica, conservar borrador y explicar
        return reply(id, "No pude aplicar esa corrección. Decime qué querés corregir (precio, numerito o foto) o sí para confirmar.");
    }

    // ========================================================
    // MANEJO EN AWAITING_DETAILS
    // ========================================================

    private Reply handleDetailsState(String id, Catalog.Session session, String rawText, String s,
                                     String image, BotModel.Control control, BotSessionCodec.DecodedPending pending) {
        // Cancelación exacta
        if (control instanceof BotModel.Cancel) {
            catalog.conversation(id, BotModel.State.IDLE.name(), null, null);
            return reply(id, "Cancelé el cambio, mandame de nuevo tranqui.");
        }

        // Si mandan "sí" en details
        if (control instanceof BotModel.Confirm) {
            return reply(id, "Todavía faltan datos antes de confirmar. Decime los detalles.");
        }

        BotModel.Draft draft = pending != null ? pending.draft() : new BotModel.Draft(null, null, null, null, session.tempImage(), null);

        // Si mandan foto sola
        if (rawText.isEmpty() && image != null) {
            BotModel.Draft updated = new BotModel.Draft(draft.operation(), draft.target(), draft.title(), draft.price(), image, draft.candidates());
            if (updated.title() != null && updated.price() != null && (updated.operation() == null || updated.operation() == BotModel.Operation.CREATE)) {
                BotModel.Create createAction = new BotModel.Create(updated.title(), updated.price(), image);
                catalog.conversation(id, BotModel.State.AWAITING_CONFIRMATION.name(), codec.encodePendingAction(id, createAction), image);
                return prepareReply(id, createAction);
            }
            catalog.conversation(id, BotModel.State.AWAITING_DETAILS.name(), codec.encodePendingDraft(id, updated), image);
            return reply(id, "Qué linda foto, Norma. Decime el nombre y el precio, o de qué pieza es la foto nueva.");
        }

        // Entrada vacía sin foto
        if (rawText.isEmpty() && image == null) {
            return explainMissingField(id, draft);
        }

        // Interpretar mensaje en contexto del draft actual
        BotModel.ParseResult parseResult = parser.interpret(rawText, image, catalog.all(id), draft);
        return applyParseResult(id, parseResult, session.tempImage());
    }

    // ========================================================
    // MANEJO EN IDLE
    // ========================================================

    private Reply handleIdleState(String id, Catalog.Session session, String rawText, String s,
                                  String image, BotModel.Control control) {
        if (control instanceof BotModel.Confirm) {
            return reply(id, "No hay un cambio pendiente para confirmar.");
        }
        if (control instanceof BotModel.Cancel) {
            return reply(id, "No hay un cambio pendiente para cancelar.");
        }

        // Interpretar mensaje
        BotModel.ParseResult parseResult = parser.interpret(rawText, image, catalog.all(id), null);
        return applyParseResult(id, parseResult, image);
    }

    // ========================================================
    // APLICACIÓN DEL PARSE RESULT A LA SESIÓN
    // ========================================================

    private Reply applyParseResult(String id, BotModel.ParseResult result, String currentImage) {
        if (result instanceof BotModel.Complete c) {
            String img = null;
            if (c.action() instanceof BotModel.Create cr) img = cr.imageUrl();
            else if (c.action() instanceof BotModel.ChangeImage ci) img = ci.newImageUrl();

            catalog.conversation(id, BotModel.State.AWAITING_CONFIRMATION.name(), codec.encodePendingAction(id, c.action()), img);
            return prepareReply(id, c.action());
        }

        if (result instanceof BotModel.NeedsDetails nd) {
            String img = nd.draft().imageUrl() != null ? nd.draft().imageUrl() : currentImage;
            catalog.conversation(id, BotModel.State.AWAITING_DETAILS.name(), codec.encodePendingDraft(id, nd.draft()), img);
            return explainMissingField(id, nd.draft());
        }

        if (result instanceof BotModel.Ambiguous amb) {
            catalog.conversation(id, BotModel.State.AWAITING_DETAILS.name(), codec.encodePendingDraft(id, amb.draft()), currentImage);
            return reply(id, "Hay varias piezas que coinciden (" + amb.candidatePieceNumbers() + "). Decime el numerito.");
        }

        if (result instanceof BotModel.Invalid inv) {
            return reply(id, inv.userMessage());
        }

        if (result instanceof BotModel.Unknown unk) {
            return reply(id, unk.userMessage());
        }

        return reply(id, "No entendí qué querés hacer.");
    }

    private Reply prepareReply(String id, BotModel.Action action) {
        String description;
        if (action instanceof BotModel.Create c) {
            description = "publicar " + c.title() + " a " + formatPrice(c.price()) + " pesos";
        } else if (action instanceof BotModel.ChangePrice cp) {
            description = "cambiar el precio de la pieza " + cp.pieceNumber() + " a " + formatPrice(cp.newPrice()) + " pesos";
        } else if (action instanceof BotModel.ChangeImage ci) {
            description = "cambiar la foto de la pieza " + ci.pieceNumber();
        } else if (action instanceof BotModel.Sell s) {
            description = "retirar la pieza " + s.pieceNumber() + " porque está vendida";
        } else {
            description = "el cambio";
        }
        return reply(id, "¿Confirmás " + description + "? Decime sí o no.");
    }

    private String formatPrice(int price) {
        return String.format(Locale.GERMAN, "%,d", price); // puntos como separador de miles argentino: 18.500
    }

    private Reply explainMissingField(String id, BotModel.Draft draft) {
        if (draft.imageUrl() == null && (draft.operation() == null || draft.operation() == BotModel.Operation.CREATE || draft.operation() == BotModel.Operation.IMAGE)) {
            return reply(id, "Me falta la foto. Mandámela y repetí el nombre y precio, o el numerito de la pieza.");
        }
        if (draft.price() == null && (draft.operation() == null || draft.operation() == BotModel.Operation.CREATE || draft.operation() == BotModel.Operation.PRICE)) {
            return reply(id, "Decime el precio de la pieza.");
        }
        if (draft.target() == null && (draft.operation() == BotModel.Operation.PRICE || draft.operation() == BotModel.Operation.IMAGE || draft.operation() == BotModel.Operation.SOLD)) {
            return reply(id, "Decime el numerito de la pieza.");
        }
        if (draft.title() == null && draft.operation() == BotModel.Operation.CREATE) {
            return reply(id, "Decime el nombre de la pieza.");
        }
        return reply(id, "Mandame los datos que faltan para completar el cambio.");
    }

    private Reply reply(String id, String text) {
        return new Reply(text, null, catalog.session(id).state());
    }
}
