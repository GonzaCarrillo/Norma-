package com.norma.taller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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

        // Validación uniforme de longitud de mensaje antes de ensure/SQL (§4, F5)
        if (text != null && text.length() > 1000) {
            throw new IllegalArgumentException("El mensaje es demasiado largo.");
        }

        catalog.ensure(id);
        Catalog.Session session = catalog.session(id);

        String rawText = text != null ? text.trim() : "";
        String s = IntentParser.norm(rawText);

        // Control exacto conversacional (§4 y §5)
        BotModel.Control control = parser.extractControl(rawText, image);

        // ========================================================
        // 1. CONTROL: UNDO PRIORITARIO SI SNAPSHOT ES VÁLIDO (§6)
        // ========================================================
        if (control instanceof BotModel.Undo) {
            if (session.snapshot() != null) {
                Optional<List<Catalog.Product>> snapOpt = codec.decodeSnapshot(session.snapshot(), id);
                if (snapOpt.isPresent()) {
                    catalog.restore(id, snapOpt.get());
                    catalog.snapshot(id, null);
                    catalog.conversation(id, BotModel.State.IDLE.name(), null, null);
                    return reply(id, "Tranquila Norma, ya dejé todo como estaba antes.");
                }
            }
        }

        // ========================================================
        // 2. RECUPERACIÓN DE CORRUPCIÓN EN PENDING (§6, F4)
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

        // Comprobar estado de sesión
        BotModel.State currentState;
        try {
            currentState = BotModel.State.valueOf(session.state());
        } catch (Exception e) {
            catalog.conversation(id, BotModel.State.IDLE.name(), null, null);
            return reply(id, "No pude recuperar el cambio pendiente. Mandámelo de nuevo.");
        }

        // ========================================================
        // 3. VALIDACIÓN DE INVARIANTES ESTADO/METADATA JUNTOS (§5, §6, F4)
        // ========================================================
        // Invariante 1: IDLE no puede tener pending ni tempImage
        if (currentState == BotModel.State.IDLE && (session.pending() != null || session.tempImage() != null)) {
            catalog.conversation(id, BotModel.State.IDLE.name(), null, null);
            return reply(id, "No pude recuperar el cambio pendiente. Mandámelo de nuevo.");
        }

        // Invariante 2: AWAITING_DETAILS no puede tener acción ya completa lista para aplicar, ni estar vacío
        if (currentState == BotModel.State.AWAITING_DETAILS) {
            if (decodedPendingOpt.isEmpty() && session.tempImage() == null) {
                catalog.conversation(id, BotModel.State.IDLE.name(), null, null);
                return reply(id, "No pude recuperar el cambio pendiente. Mandámelo de nuevo.");
            }
            if (decodedPendingOpt.isPresent() && decodedPendingOpt.get().action() != null) {
                catalog.conversation(id, BotModel.State.IDLE.name(), null, null);
                return reply(id, "No pude recuperar el cambio pendiente. Mandámelo de nuevo.");
            }
        }

        // Invariante 3: AWAITING_CONFIRMATION exige DecodedPending con Action válida
        if (currentState == BotModel.State.AWAITING_CONFIRMATION) {
            if (decodedPendingOpt.isEmpty() || decodedPendingOpt.get().action() == null) {
                catalog.conversation(id, BotModel.State.IDLE.name(), null, null);
                return reply(id, "No pude recuperar el cambio pendiente. Mandámelo de nuevo.");
            }
        }

        // Si el control era Undo pero snapshot no estaba presente o era corrupto (§6)
        if (control instanceof BotModel.Undo) {
            return handleUndo(id, session);
        }

        // ========================================================
        // 4. CONTROL SÍ / NO CON FOTO ADJUNTA (§4, F3)
        // ========================================================
        if (image != null && (isConfirmPhrase(s) || isCancelPhrase(s))) {
            if (currentState == BotModel.State.AWAITING_CONFIRMATION || currentState == BotModel.State.AWAITING_DETAILS) {
                return reply(id, "Tengo un cambio pendiente. Mandame sí para confirmar o no para cancelar, sin foto.");
            }
        }

        // ========================================================
        // 5. DISPATCH POR ESTADO CANÓNICO
        // ========================================================
        if (currentState == BotModel.State.AWAITING_CONFIRMATION) {
            return handleConfirmationState(id, session, rawText, s, image, control, decodedPendingOpt.get());
        }

        if (currentState == BotModel.State.AWAITING_DETAILS) {
            return handleDetailsState(id, session, rawText, s, image, control, decodedPendingOpt.orElse(null));
        }

        return handleIdleState(id, session, rawText, s, image, control);
    }

    private static boolean isConfirmPhrase(String s) {
        return s.equals("si") || s.equals("dale") || s.equals("subilo") || s.equals("esta bien")
                || s.equals("confirmo") || s.equals("confirmar");
    }

    private static boolean isCancelPhrase(String s) {
        return s.equals("no") || s.equals("para") || s.equals("cancelar") || s.equals("cancela");
    }

    // ========================================================
    // MANEJO DE UNDO
    // ========================================================

    private Reply handleUndo(String id, Catalog.Session session) {
        if (session.snapshot() == null) {
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
        } else if (corr.field() == BotModel.CorrectionField.TARGET) {
            List<Catalog.Product> prods = catalog.all(id);
            Integer newTarget = null;
            if (corr.value() instanceof Integer it) {
                newTarget = it;
            } else if (corr.value() instanceof String st) {
                String sNorm = IntentParser.norm(st);
                Optional<Catalog.Product> match = prods.stream()
                        .filter(p -> !p.sold() && IntentParser.norm(p.title()).equals(sNorm))
                        .findFirst();
                if (match.isPresent()) {
                    newTarget = match.get().pieceNumber();
                }
            }
            if (newTarget != null) {
                int nt = newTarget;
                Optional<Catalog.Product> pOpt = prods.stream().filter(p -> p.pieceNumber() == nt && !p.sold()).findFirst();
                if (pOpt.isPresent()) {
                    if (action instanceof BotModel.ChangePrice cp) {
                        updatedAction = new BotModel.ChangePrice(nt, cp.newPrice());
                    } else if (action instanceof BotModel.ChangeImage ci) {
                        updatedAction = new BotModel.ChangeImage(nt, ci.newImageUrl());
                    } else if (action instanceof BotModel.Sell) {
                        updatedAction = new BotModel.Sell(nt);
                    }
                } else {
                    return reply(id, "No encuentro esa pieza disponible. Decime otro numerito.");
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

        // Corrección explícita en DETAILS (§4, §5, F3)
        if (control instanceof BotModel.Correction corr) {
            return handleCorrectionInDetails(id, session, image, corr, draft);
        }

        // Entrada vacía sin foto
        if (rawText.isEmpty() && image == null) {
            return explainMissingField(id, draft);
        }

        // Foto sola en DETAILS
        if (rawText.isEmpty() && image != null) {
            // Si la operación era PRICE o SOLD, una foto inesperada conserva el borrador válido y no persiste imageUrl incompatible
            if (draft.operation() == BotModel.Operation.PRICE || draft.operation() == BotModel.Operation.SOLD) {
                return explainMissingField(id, draft);
            }
            // Si la operación era IMAGE y faltaba la foto (Probe 2 F3)
            if (draft.operation() == BotModel.Operation.IMAGE && draft.target() != null) {
                BotModel.ChangeImage ci = new BotModel.ChangeImage(draft.target(), image);
                catalog.conversation(id, BotModel.State.AWAITING_CONFIRMATION.name(), codec.encodePendingAction(id, ci), image);
                return prepareReply(id, ci);
            }
            // Si la operación era CREATE y faltaba la foto
            if (draft.title() != null && draft.price() != null && (draft.operation() == null || draft.operation() == BotModel.Operation.CREATE)) {
                BotModel.Create cr = new BotModel.Create(draft.title(), draft.price(), image);
                catalog.conversation(id, BotModel.State.AWAITING_CONFIRMATION.name(), codec.encodePendingAction(id, cr), image);
                return prepareReply(id, cr);
            }
            // Reemplazar foto del borrador para CREATE/IMAGE y volver a pedir datos
            BotModel.Draft updated = new BotModel.Draft(draft.operation(), draft.target(), draft.title(), draft.price(), image, draft.candidates());
            catalog.conversation(id, BotModel.State.AWAITING_DETAILS.name(), codec.encodePendingDraft(id, updated), image);
            return explainMissingField(id, updated);
        }

        // Aclaración numérica desnuda o referencia de destino en DETAILS (F3)
        Integer bareTarget = parseBareTarget(rawText);
        if (bareTarget != null) {
            int bNum = bareTarget;
            // Caso A: si había candidatos y bNum es uno de ellos (Probe 5 F3)
            if (draft.candidates() != null && draft.candidates().contains(bNum)) {
                return resolveCandidateTarget(id, session, draft, bNum, image);
            }
            // Caso B: foto previa sola (draft.operation == null && draft.imageUrl != null) y llega #N (Probe 4 F3)
            if (draft.operation() == null && draft.imageUrl() != null) {
                Optional<Catalog.Product> pOpt = catalog.all(id).stream().filter(p -> p.pieceNumber() == bNum && !p.sold()).findFirst();
                if (pOpt.isPresent()) {
                    BotModel.ChangeImage ci = new BotModel.ChangeImage(bNum, draft.imageUrl());
                    catalog.conversation(id, BotModel.State.AWAITING_CONFIRMATION.name(), codec.encodePendingAction(id, ci), draft.imageUrl());
                    return prepareReply(id, ci);
                } else {
                    return reply(id, "No encuentro la pieza #" + bNum + " disponible; decime otro numerito.");
                }
            }
            // Caso C: operación PRICE/IMAGE/SOLD que esperaba target
            if (draft.target() == null && (draft.operation() == BotModel.Operation.PRICE || draft.operation() == BotModel.Operation.IMAGE || draft.operation() == BotModel.Operation.SOLD)) {
                Optional<Catalog.Product> pOpt = catalog.all(id).stream().filter(p -> p.pieceNumber() == bNum && !p.sold()).findFirst();
                if (pOpt.isEmpty()) {
                    return reply(id, "No encuentro la pieza #" + bNum + " disponible; decime otro numerito.");
                }
                if (pOpt.get().sold()) {
                    return reply(id, "Esa pieza ya está marcada como vendida.");
                }
                return resolveCandidateTarget(id, session, draft, bNum, image);
            }
        }

        // Protección de borrador frente a comandos incompatibles nuevos (§5, F3, Probe 3)
        BotModel.ParseResult standalone = parser.interpret(rawText, image, catalog.all(id), null);
        if (standalone instanceof BotModel.Invalid inv) {
            return reply(id, inv.userMessage());
        }
        if (standalone instanceof BotModel.Complete c) {
            if (isActionCompatibleWithDraft(c.action(), draft)) {
                // Reiteración compatible que completa lo faltante
                catalog.conversation(id, BotModel.State.AWAITING_CONFIRMATION.name(), codec.encodePendingAction(id, c.action()),
                        c.action() instanceof BotModel.Create cr ? cr.imageUrl() : (c.action() instanceof BotModel.ChangeImage ci ? ci.newImageUrl() : null));
                return prepareReply(id, c.action());
            } else {
                // Otra acción incompatible: proteger borrador íntegro y pedir cancelar o corregir
                return reply(id, "Tengo un cambio pendiente (" + describeDraft(id, draft) + "). Podés cancelarlo para iniciar otro o decirme qué querés corregir.");
            }
        }
        if (standalone instanceof BotModel.NeedsDetails nd) {
            if (isDraftIncompatible(draft, nd.draft())) {
                return reply(id, "Tengo un cambio pendiente (" + describeDraft(id, draft) + "). Podés cancelarlo para iniciar otro o decirme qué querés corregir.");
            }
        }

        // Interpretar mensaje en contexto del draft actual
        BotModel.ParseResult parseResult = parser.interpret(rawText, image, catalog.all(id), draft);
        return applyParseResult(id, parseResult, session.tempImage());
    }

    private Integer parseBareTarget(String text) {
        if (text == null) return null;
        String raw = text.trim();
        String s = IntentParser.norm(raw);
        s = s.replaceAll("[¡!¿?.,;:]+", "").trim();
        s = s.replaceFirst("^(?:#\\s*|(?:la|el|pieza|numero|la\\s+numero)\\s+)", "").trim();
        return parser.parseTargetNumber(s);
    }

    private Reply resolveCandidateTarget(String id, Catalog.Session session, BotModel.Draft draft, int targetNum, String image) {
        BotModel.Operation op = draft.operation();
        if (op == BotModel.Operation.SOLD) {
            BotModel.Sell s = new BotModel.Sell(targetNum);
            catalog.conversation(id, BotModel.State.AWAITING_CONFIRMATION.name(), codec.encodePendingAction(id, s), null);
            return prepareReply(id, s);
        }
        if (op == BotModel.Operation.PRICE) {
            if (draft.price() != null) {
                BotModel.ChangePrice cp = new BotModel.ChangePrice(targetNum, draft.price());
                catalog.conversation(id, BotModel.State.AWAITING_CONFIRMATION.name(), codec.encodePendingAction(id, cp), null);
                return prepareReply(id, cp);
            } else {
                BotModel.Draft updated = new BotModel.Draft(op, targetNum, null, null, null, null);
                catalog.conversation(id, BotModel.State.AWAITING_DETAILS.name(), codec.encodePendingDraft(id, updated), null);
                return explainMissingField(id, updated);
            }
        }
        if (op == BotModel.Operation.IMAGE) {
            String img = draft.imageUrl() != null ? draft.imageUrl() : image;
            if (img != null) {
                BotModel.ChangeImage ci = new BotModel.ChangeImage(targetNum, img);
                catalog.conversation(id, BotModel.State.AWAITING_CONFIRMATION.name(), codec.encodePendingAction(id, ci), img);
                return prepareReply(id, ci);
            } else {
                BotModel.Draft updated = new BotModel.Draft(op, targetNum, null, null, null, null);
                catalog.conversation(id, BotModel.State.AWAITING_DETAILS.name(), codec.encodePendingDraft(id, updated), null);
                return explainMissingField(id, updated);
            }
        }
        BotModel.Draft updated = switch (op) {
            case CREATE -> new BotModel.Draft(op, null, draft.title(), draft.price(), draft.imageUrl(), null);
            case PRICE -> new BotModel.Draft(op, targetNum, null, draft.price(), null, null);
            case IMAGE -> new BotModel.Draft(op, targetNum, null, null, draft.imageUrl(), null);
            case SOLD -> new BotModel.Draft(op, targetNum, null, null, null, null);
        };
        catalog.conversation(id, BotModel.State.AWAITING_DETAILS.name(), codec.encodePendingDraft(id, updated), updated.imageUrl());
        return explainMissingField(id, updated);
    }

    private boolean isDraftIncompatible(BotModel.Draft existing, BotModel.Draft incoming) {
        if (existing == null || incoming == null) return false;
        if (existing.operation() != null && incoming.operation() != null && existing.operation() != incoming.operation()) {
            return true;
        }
        if (existing.operation() == BotModel.Operation.CREATE && incoming.operation() == BotModel.Operation.CREATE) {
            if (existing.title() != null && incoming.title() != null && !IntentParser.norm(existing.title()).equals(IntentParser.norm(incoming.title()))) {
                return true;
            }
            if (existing.price() != null && incoming.price() != null && !existing.price().equals(incoming.price())) {
                return true;
            }
        }
        if (existing.target() != null && incoming.target() != null && !existing.target().equals(incoming.target())) {
            return true;
        }
        return false;
    }

    private boolean isActionCompatibleWithDraft(BotModel.Action action, BotModel.Draft draft) {
        if (draft.operation() == null) return true;
        if (action instanceof BotModel.Create c) {
            if (draft.operation() != BotModel.Operation.CREATE) return false;
            if (draft.title() != null && !draft.title().equalsIgnoreCase(c.title())) return false;
            if (draft.price() != null && draft.price() != c.price()) return false;
            return true;
        }
        if (action instanceof BotModel.ChangePrice cp) {
            if (draft.operation() != BotModel.Operation.PRICE) return false;
            if (draft.target() != null && draft.target() != cp.pieceNumber()) return false;
            if (draft.price() != null && draft.price() != cp.newPrice()) return false;
            return true;
        }
        if (action instanceof BotModel.ChangeImage ci) {
            if (draft.operation() != BotModel.Operation.IMAGE) return false;
            if (draft.target() != null && draft.target() != ci.pieceNumber()) return false;
            return true;
        }
        if (action instanceof BotModel.Sell s) {
            if (draft.operation() != BotModel.Operation.SOLD) return false;
            if (draft.target() != null && draft.target() != s.pieceNumber()) return false;
            return true;
        }
        return false;
    }

    private Reply handleCorrectionInDetails(String id, Catalog.Session session, String image,
                                            BotModel.Correction corr, BotModel.Draft draft) {
        BotModel.Draft updated = null;
        BotModel.Action completedAction = null;

        if (corr.field() == BotModel.CorrectionField.PRICE && corr.value() instanceof Integer newPrice) {
            if (draft.operation() == null || draft.operation() == BotModel.Operation.CREATE || draft.operation() == BotModel.Operation.PRICE) {
                updated = new BotModel.Draft(draft.operation(), draft.target(), draft.title(), newPrice, draft.imageUrl(), draft.candidates());
                if (updated.operation() == BotModel.Operation.CREATE && updated.title() != null && (updated.imageUrl() != null || image != null)) {
                    String img = updated.imageUrl() != null ? updated.imageUrl() : image;
                    completedAction = new BotModel.Create(updated.title(), newPrice, img);
                } else if (updated.operation() == BotModel.Operation.PRICE && updated.target() != null) {
                    completedAction = new BotModel.ChangePrice(updated.target(), newPrice);
                }
            }
        } else if (corr.field() == BotModel.CorrectionField.TARGET) {
            Integer newTarget = null;
            if (corr.value() instanceof Integer it) {
                newTarget = it;
            } else if (corr.value() instanceof String st) {
                String sNorm = IntentParser.norm(st);
                Optional<Catalog.Product> match = catalog.all(id).stream()
                        .filter(p -> !p.sold() && IntentParser.norm(p.title()).equals(sNorm))
                        .findFirst();
                if (match.isPresent()) {
                    newTarget = match.get().pieceNumber();
                }
            }
            if (newTarget != null) {
                int nt = newTarget;
                if (draft.operation() == null || draft.operation() == BotModel.Operation.PRICE || draft.operation() == BotModel.Operation.IMAGE || draft.operation() == BotModel.Operation.SOLD) {
                    Optional<Catalog.Product> pOpt = catalog.all(id).stream().filter(p -> p.pieceNumber() == nt && !p.sold()).findFirst();
                    if (pOpt.isPresent()) {
                        BotModel.Operation dop = draft.operation() != null ? draft.operation() : BotModel.Operation.PRICE;
                        updated = switch (dop) {
                            case PRICE -> new BotModel.Draft(dop, nt, null, draft.price(), null, null);
                            case IMAGE -> new BotModel.Draft(dop, nt, null, null, draft.imageUrl(), null);
                            case SOLD -> new BotModel.Draft(dop, nt, null, null, null, null);
                            default -> new BotModel.Draft(dop, nt, null, null, null, null);
                        };
                        if (updated.operation() == BotModel.Operation.PRICE && updated.price() != null) {
                            completedAction = new BotModel.ChangePrice(nt, updated.price());
                        } else if (updated.operation() == BotModel.Operation.IMAGE && (updated.imageUrl() != null || image != null)) {
                            String img = updated.imageUrl() != null ? updated.imageUrl() : image;
                            completedAction = new BotModel.ChangeImage(nt, img);
                        } else if (updated.operation() == BotModel.Operation.SOLD) {
                            completedAction = new BotModel.Sell(nt);
                        }
                    } else {
                        return reply(id, "No encuentro esa pieza disponible. Decime otro numerito.");
                    }
                }
            } else {
                return reply(id, "No encuentro esa pieza disponible. Decime otro numerito.");
            }
        } else if (corr.field() == BotModel.CorrectionField.TITLE && corr.value() instanceof String newTitle) {
            if (draft.operation() == null || draft.operation() == BotModel.Operation.CREATE) {
                String cleanTitle = String.join(" ", newTitle.trim().split("\\s+"));
                if (!cleanTitle.isEmpty() && cleanTitle.length() <= 100) {
                    updated = new BotModel.Draft(BotModel.Operation.CREATE, null, cleanTitle, draft.price(), draft.imageUrl(), null);
                    if (updated.price() != null && (updated.imageUrl() != null || image != null)) {
                        String img = updated.imageUrl() != null ? updated.imageUrl() : image;
                        completedAction = new BotModel.Create(cleanTitle, updated.price(), img);
                    }
                }
            }
        } else if (corr.field() == BotModel.CorrectionField.IMAGE && image != null) {
            if (draft.operation() == null || draft.operation() == BotModel.Operation.CREATE || draft.operation() == BotModel.Operation.IMAGE) {
                updated = new BotModel.Draft(draft.operation(), draft.target(), draft.title(), draft.price(), image, draft.candidates());
                if (updated.operation() == BotModel.Operation.CREATE && updated.title() != null && updated.price() != null) {
                    completedAction = new BotModel.Create(updated.title(), updated.price(), image);
                } else if (updated.operation() == BotModel.Operation.IMAGE && updated.target() != null) {
                    completedAction = new BotModel.ChangeImage(updated.target(), image);
                }
            }
        }

        if (completedAction != null) {
            String img = completedAction instanceof BotModel.Create cr ? cr.imageUrl() : (completedAction instanceof BotModel.ChangeImage ci ? ci.newImageUrl() : null);
            catalog.conversation(id, BotModel.State.AWAITING_CONFIRMATION.name(), codec.encodePendingAction(id, completedAction), img);
            return prepareReply(id, completedAction);
        }

        if (updated != null) {
            catalog.conversation(id, BotModel.State.AWAITING_DETAILS.name(), codec.encodePendingDraft(id, updated), updated.imageUrl());
            return explainMissingField(id, updated);
        }

        return reply(id, "No pude aplicar esa corrección. Decime qué querés corregir (precio, numerito o foto) o cancelá para empezar de nuevo.");
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
            List<Catalog.Product> allProds = catalog.all(id);
            List<String> candidateDescs = new ArrayList<>();
            for (int num : amb.candidatePieceNumbers()) {
                Optional<Catalog.Product> pOpt = allProds.stream().filter(p -> p.pieceNumber() == num).findFirst();
                if (pOpt.isPresent()) {
                    candidateDescs.add("#" + num + " (" + pOpt.get().title() + ")");
                } else {
                    candidateDescs.add("#" + num);
                }
            }
            return reply(id, "Hay varias piezas que coinciden (" + String.join(", ", candidateDescs) + "). Decime el numerito.");
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
            description = "publicar " + c.title() + " a " + formatPrice(c.price());
        } else if (action instanceof BotModel.ChangePrice cp) {
            description = "cambiar el precio de la pieza " + describePieceTarget(id, cp.pieceNumber()) + " a " + formatPrice(cp.newPrice());
        } else if (action instanceof BotModel.ChangeImage ci) {
            description = "cambiar la foto de la pieza " + describePieceTarget(id, ci.pieceNumber());
        } else if (action instanceof BotModel.Sell s) {
            description = "retirar la pieza " + describePieceTarget(id, s.pieceNumber()) + " porque está vendida";
        } else {
            description = "el cambio";
        }
        return reply(id, "¿Confirmás " + description + "? Decime sí o no.");
    }

    private String describeDraft(String id, BotModel.Draft draft) {
        if (draft.operation() == null) return "el cambio";
        return switch (draft.operation()) {
            case CREATE -> "publicar " + (draft.title() != null ? draft.title() : "una pieza") + (draft.price() != null ? " a " + formatPrice(draft.price()) : "");
            case PRICE -> "cambiar el precio de la pieza " + describePieceTarget(id, draft.target()) + (draft.price() != null ? " a " + formatPrice(draft.price()) : "");
            case IMAGE -> "cambiar la foto de la pieza " + describePieceTarget(id, draft.target());
            case SOLD -> "retirar la pieza " + describePieceTarget(id, draft.target());
        };
    }

    private String describePieceTarget(String id, Integer target) {
        if (target == null) return "";
        Optional<Catalog.Product> pOpt = catalog.all(id).stream().filter(p -> p.pieceNumber() == (int) target).findFirst();
        if (pOpt.isPresent()) {
            return "#" + target + " (" + pOpt.get().title() + ")";
        }
        return "#" + target;
    }

    private String formatPrice(int price) {
        return "$ " + String.format(Locale.GERMAN, "%,d", price); // Formato argentino: $ 18.500 (§8, F5)
    }

    private Reply explainMissingField(String id, BotModel.Draft draft) {
        // Prioridad §3: operación, destino, título, precio, imagen
        if (draft.operation() == null) {
            return reply(id, "Qué linda foto, Norma. Decime el nombre y el precio para una pieza nueva, o el numerito si es para cambiar la foto de una existente.");
        }
        if (draft.target() == null && (draft.operation() == BotModel.Operation.PRICE || draft.operation() == BotModel.Operation.IMAGE || draft.operation() == BotModel.Operation.SOLD)) {
            return reply(id, "Decime el numerito de la pieza.");
        }
        if (draft.title() == null && draft.operation() == BotModel.Operation.CREATE) {
            return reply(id, "Decime el nombre de la pieza.");
        }
        if (draft.price() == null && (draft.operation() == BotModel.Operation.CREATE || draft.operation() == BotModel.Operation.PRICE)) {
            return reply(id, "Decime el precio de la pieza.");
        }
        if (draft.imageUrl() == null && (draft.operation() == BotModel.Operation.CREATE || draft.operation() == BotModel.Operation.IMAGE)) {
            return reply(id, "Me falta la foto. Mandámela para completar el cambio.");
        }
        return reply(id, "Mandame los datos que faltan para completar el cambio.");
    }

    private Reply reply(String id, String text) {
        return new Reply(text, null, catalog.session(id).state());
    }
}
