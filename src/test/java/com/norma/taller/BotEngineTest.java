package com.norma.taller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite::memory:"})
public class BotEngineTest {

    @Autowired private Catalog catalog;
    @Autowired private BotEngine bot;
    @Autowired private JdbcClient db;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ObjectMapper objectMapper;
    private BotSessionCodec codec;

    private static final String SESS = "session_bot_test";

    @BeforeEach
    void setUp() {
        codec = new BotSessionCodec(objectMapper);
        catalog.ensure(SESS);
        catalog.reset(SESS);
    }

    // ==========================================
    // F1: CUATRO OPERACIONES SOLO TRAS CONFIRMAR
    // ==========================================

    @Test
    @DisplayName("F1: CREATE, PRICE, IMAGE, SOLD no mutan catalogo antes del sí, solo tras sí")
    void testOperationsOnlyOnConfirm() {
        // 1. PRICE: pedir cambio de precio a 25000
        BotEngine.Reply r1 = bot.process(SESS, "Cambiá el precio de #2 a 25000", null);
        assertEquals("AWAITING_CONFIRMATION", r1.state());
        // Antes del sí: precio sigue siendo 18500
        assertEquals(18500, catalog.all(SESS).get(1).price());

        // Cancelar no muta y vuelve a IDLE
        bot.process(SESS, "no", null);
        assertEquals("IDLE", catalog.session(SESS).state());
        assertEquals(18500, catalog.all(SESS).get(1).price());

        // Pedir y confirmar
        bot.process(SESS, "Cambiá el precio de #2 a 25000", null);
        BotEngine.Reply rConfirm = bot.process(SESS, "sí", null);
        assertEquals("IDLE", rConfirm.state());
        assertEquals(25000, catalog.all(SESS).get(1).price());

        // 2. SOLD: vender pieza #2
        bot.process(SESS, "Ya vendí la pieza 2", null);
        assertFalse(catalog.all(SESS).get(1).sold()); // no vendido antes de sí
        bot.process(SESS, "dale", null);
        assertTrue(catalog.all(SESS).get(1).sold()); // vendido tras sí

        // 3. IMAGE: cambiar foto
        bot.process(SESS, "Esta foto es para cambiar la de la pieza 3", "/img/nueva3.jpg");
        assertEquals("/assets/pieza-3.webp", catalog.all(SESS).get(2).imageUrl());
        bot.process(SESS, "sí", null);
        assertEquals("/img/nueva3.jpg", catalog.all(SESS).get(2).imageUrl());

        // 4. CREATE: alta
        bot.process(SESS, "Publicá Jarra azul a 12000", "/img/jarra.jpg");
        assertEquals(8, catalog.all(SESS).size()); // todavía 8
        bot.process(SESS, "confirmo", null);
        assertEquals(9, catalog.all(SESS).size()); // 9 tras confirmar
        assertEquals("Jarra azul", catalog.all(SESS).get(8).title());
    }

    // ==========================================
    // F2: RECORRIDO DE ESTADOS FSM Y CORRECCIONES
    // ==========================================

    @Test
    @DisplayName("F2: Recorrido FSM en los 3 estados, correcciones explícitas y comandos nuevos no sustituyen borrador")
    void testFsmTransitionsAndCorrections() {
        // En IDLE, mensaje vacío conserva IDLE
        BotEngine.Reply rEmpty = bot.process(SESS, "", null);
        assertEquals("IDLE", rEmpty.state());

        // Foto sola pasa a AWAITING_DETAILS
        BotEngine.Reply rPhoto = bot.process(SESS, "", "/img/temp.jpg");
        assertEquals("AWAITING_DETAILS", rPhoto.state());
        assertEquals("/img/temp.jpg", catalog.session(SESS).tempImage());

        // En DETAILS, mandar precio y nombre completa a AWAITING_CONFIRMATION
        BotEngine.Reply rDetails = bot.process(SESS, "Maceta nueva a 30000", null);
        assertEquals("AWAITING_CONFIRMATION", rDetails.state());

        // En CONFIRMATION, un comando distinto nuevo NO sustituye el borrador silenciosamente (§5)
        BotEngine.Reply rOther = bot.process(SESS, "Sacá la pieza 1", null);
        assertEquals("AWAITING_CONFIRMATION", rOther.state());
        assertTrue(rOther.replyText().contains("Hay un cambio pendiente"));

        // Corrección explícita de precio: "no, dije 35000"
        BotEngine.Reply rCorrPrice = bot.process(SESS, "no, dije 35000", null);
        assertEquals("AWAITING_CONFIRMATION", rCorrPrice.state());
        assertTrue(rCorrPrice.replyText().contains("35.000"));

        // Corrección inválida conserva borrador
        BotEngine.Reply rCorrInv = bot.process(SESS, "no, dije quince veinte", null);
        assertEquals("AWAITING_CONFIRMATION", rCorrInv.state());
        assertTrue(rCorrInv.replyText().contains("No pude aplicar esa corrección"));

        // Confirmar aplica con el precio corregido (35000)
        bot.process(SESS, "sí", null);
        assertEquals(9, catalog.all(SESS).size());
        assertEquals(35000, catalog.all(SESS).get(8).price());
    }

    // ==========================================
    // F3: DUPLICADO SÍ, SÍ SIN PENDIENTE Y NO-OP
    // ==========================================

    @Test
    @DisplayName("F3: Sí sin pendiente, sí duplicado secuencial y no-op no crean cambios ni corrompen historial")
    void testDuplicateConfirmAndNoOp() {
        // Sí sin pendiente en IDLE
        BotEngine.Reply r1 = bot.process(SESS, "sí", null);
        assertEquals("IDLE", r1.state());
        assertTrue(r1.replyText().contains("No hay un cambio pendiente"));

        // Operación y confirmación
        bot.process(SESS, "Cambiá el precio de #1 a 15000", null);
        bot.process(SESS, "sí", null);
        assertEquals(15000, catalog.all(SESS).get(0).price());

        // Segundo sí consecutivo: ya está en IDLE, no muta de nuevo
        BotEngine.Reply rDup = bot.process(SESS, "sí", null);
        assertEquals("IDLE", rDup.state());
        assertTrue(rDup.replyText().contains("No hay un cambio pendiente"));

        // No-op: cambiar precio al mismo que ya tiene
        bot.process(SESS, "Cambiá el precio de #1 a 15000", null);
        BotEngine.Reply rNoOp = bot.process(SESS, "sí", null);
        assertEquals("IDLE", rNoOp.state());
        assertTrue(rNoOp.replyText().contains("Ya estaba así, no hice cambios"));
    }

    // ==========================================
    // U1: UNDO DE TODAS LAS OPERACIONES Y LÍMITES
    // ==========================================

    @Test
    @DisplayName("U1: Deshacer cada operación (CREATE, PRICE, IMAGE, SOLD), consumo único de slot y no redo")
    void testUndoAllOperations() {
        // 1. Deshacer PRICE
        int originalPrice = catalog.all(SESS).get(0).price();
        bot.process(SESS, "Cambiá el precio de #1 a 50000", null);
        bot.process(SESS, "sí", null);
        assertEquals(50000, catalog.all(SESS).get(0).price());

        BotEngine.Reply rUndo = bot.process(SESS, "me equivoqué", null);
        assertEquals("IDLE", rUndo.state());
        assertEquals(originalPrice, catalog.all(SESS).get(0).price());

        // Segundo undo no tiene efecto (consumo de un único slot, no redo)
        BotEngine.Reply rUndo2 = bot.process(SESS, "deshacer", null);
        assertTrue(rUndo2.replyText().contains("Todavía no hay un cambio para deshacer"));

        // 2. Deshacer SOLD
        bot.process(SESS, "Ya vendí la pieza 1", null);
        bot.process(SESS, "sí", null);
        assertTrue(catalog.all(SESS).get(0).sold());
        bot.process(SESS, "deshacer", null);
        assertFalse(catalog.all(SESS).get(0).sold());

        // 3. Deshacer CREATE no recicla número high-water mark
        int nextBefore = catalog.session(SESS).nextNumber();
        bot.process(SESS, "Publicá Plato nuevo a 10000", "/img/p.jpg");
        bot.process(SESS, "sí", null);
        assertEquals(nextBefore + 1, catalog.session(SESS).nextNumber());
        bot.process(SESS, "deshacer", null);
        assertEquals(8, catalog.all(SESS).size());
        assertEquals(nextBefore + 1, catalog.session(SESS).nextNumber()); // high-water se preserva

        // Nueva alta no recicla el ID destruido
        bot.process(SESS, "Publicá Jarra nueva a 12000", "/img/j.jpg");
        bot.process(SESS, "sí", null);
        assertEquals(nextBefore + 2, catalog.session(SESS).nextNumber());
        assertEquals(nextBefore + 1, catalog.all(SESS).get(8).pieceNumber());
    }

    // ==========================================
    // J1: COMPATIBILIDAD LEGACY Y RECUPERACIÓN DE CORRUPCIÓN
    // ==========================================

    @Test
    @DisplayName("J1: Pending y Snapshot corruptos se recuperan conversacionalmente conforme §6")
    void testCorruptStateRecovery() {
        // 1. Pending corrupto en base de datos
        catalog.conversation(SESS, "AWAITING_CONFIRMATION", "{\"corrupt\":true}", null);
        BotEngine.Reply r1 = bot.process(SESS, "sí", null);
        assertEquals("IDLE", r1.state());
        assertTrue(r1.replyText().contains("No pude recuperar el cambio pendiente"));
        assertNull(catalog.session(SESS).pending()); // pending limpiado

        // 2. Snapshot corrupto al pedir undo
        catalog.snapshot(SESS, "{\"corrupt\":true}");
        BotEngine.Reply r2 = bot.process(SESS, "deshacer", null);
        assertTrue(r2.replyText().contains("No pude recuperar el cambio anterior"));
        // No se destruye el catálogo ni el slot
        assertNotNull(catalog.session(SESS).snapshot());

        // 3. Pending legacy re-pregunta antes de aplicar (§6)
        String legacyPending = "{\"kind\":\"PRICE\",\"number\":1,\"title\":null,\"price\":45000,\"image\":null}";
        catalog.conversation(SESS, "AWAITING_CONFIRMATION", legacyPending, null);
        BotEngine.Reply rLegacy = bot.process(SESS, "sí", null);
        // Debe reescribir envelope y volver a preguntar sin aplicar directamente
        assertEquals("AWAITING_CONFIRMATION", rLegacy.state());
        assertTrue(rLegacy.replyText().contains("¿Confirmás"));
        // El catálogo sigue intacto
        assertNotEquals(45000, catalog.all(SESS).get(0).price());
        // El segundo sí aplica
        bot.process(SESS, "sí", null);
        assertEquals(45000, catalog.all(SESS).get(0).price());
    }

    // ==========================================
    // T1: ROLLBACK ATÓMICO ANTE FALLO SQL REAL
    // ==========================================

    @Test
    @DisplayName("T1: Fallo SQL real inyectado por trigger durante confirmación y undo revierte toda la transacción atómicamente")
    void testAtomicTransactionRollback() {
        // --- 1. Rollback durante Confirmación (falla al limpiar pendiente tras apply) ---
        bot.process(SESS, "Cambiá el precio de #1 a 77000", null);
        Catalog.Session sessionBeforeConfirm = catalog.session(SESS);
        List<Catalog.Product> productsBeforeConfirm = catalog.all(SESS);

        // Inyectamos trigger que falla exactamente cuando se intenta limpiar pending a NULL en sessions
        db.sql("CREATE TRIGGER trg_fail_confirm_pending BEFORE UPDATE OF pending ON sessions " +
                "WHEN NEW.pending IS NULL BEGIN SELECT RAISE(ABORT, 'FAIL_CONFIRM_CLEAN_PENDING'); END;").update();

        try {
            // bot.process se invoca SIN transacción exterior de prueba
            Exception ex = assertThrows(Exception.class, () -> bot.process(SESS, "sí", null));
            assertTrue(ex.getMessage().contains("FAIL_CONFIRM_CLEAN_PENDING"), "Causa esperada de trigger SQL en confirm");

            // Validar estado íntegro desde transacción nueva
            TransactionTemplate ttNew = new TransactionTemplate(txManager);
            ttNew.execute(status -> {
                Catalog.Session sessionAfter = catalog.session(SESS);
                List<Catalog.Product> productsAfter = catalog.all(SESS);

                // Comparación de Session completa
                assertEquals(sessionBeforeConfirm.id(), sessionAfter.id());
                assertEquals(sessionBeforeConfirm.state(), sessionAfter.state());
                assertEquals(sessionBeforeConfirm.pending(), sessionAfter.pending());
                assertEquals(sessionBeforeConfirm.tempImage(), sessionAfter.tempImage());
                assertEquals(sessionBeforeConfirm.snapshot(), sessionAfter.snapshot());
                assertEquals(sessionBeforeConfirm.nextNumber(), sessionAfter.nextNumber());

                // Comparación de productos íntegros (todos los campos lógicos y de almacenamiento)
                assertEquals(productsBeforeConfirm.size(), productsAfter.size());
                for (int i = 0; i < productsBeforeConfirm.size(); i++) {
                    Catalog.Product p1 = productsBeforeConfirm.get(i);
                    Catalog.Product p2 = productsAfter.get(i);
                    assertEquals(p1.id(), p2.id());
                    assertEquals(p1.sessionId(), p2.sessionId());
                    assertEquals(p1.pieceNumber(), p2.pieceNumber());
                    assertEquals(p1.title(), p2.title());
                    assertEquals(p1.price(), p2.price());
                    assertEquals(p1.imageUrl(), p2.imageUrl());
                    assertEquals(p1.sold(), p2.sold());
                    assertEquals(p1, p2);
                }
                return null;
            });
        } finally {
            db.sql("DROP TRIGGER IF EXISTS trg_fail_confirm_pending").update();
        }

        // --- 2. Rollback durante Undo (falla al consumir snapshot a NULL tras restore) ---
        // Aplicamos el cambio exitosamente ahora que no hay trigger
        bot.process(SESS, "sí", null);
        assertEquals(77000, catalog.all(SESS).get(0).price());
        Catalog.Session sessionBeforeUndo = catalog.session(SESS);
        List<Catalog.Product> productsBeforeUndo = catalog.all(SESS);
        assertNotNull(sessionBeforeUndo.snapshot());

        // Inyectamos trigger que falla al poner snapshot = NULL
        db.sql("CREATE TRIGGER trg_fail_undo_snapshot BEFORE UPDATE OF snapshot ON sessions " +
                "WHEN NEW.snapshot IS NULL BEGIN SELECT RAISE(ABORT, 'FAIL_UNDO_CONSUME_SNAPSHOT'); END;").update();

        try {
            // bot.process de undo sin transacción exterior
            Exception ex = assertThrows(Exception.class, () -> bot.process(SESS, "deshacer", null));
            assertTrue(ex.getMessage().contains("FAIL_UNDO_CONSUME_SNAPSHOT"), "Causa esperada de trigger SQL en undo");

            // Validar estado íntegro desde transacción nueva: no se consumió snapshot ni se modificó catálogo
            TransactionTemplate ttNew = new TransactionTemplate(txManager);
            ttNew.execute(status -> {
                Catalog.Session sessionAfter = catalog.session(SESS);
                List<Catalog.Product> productsAfter = catalog.all(SESS);

                assertEquals(sessionBeforeUndo.id(), sessionAfter.id());
                assertEquals(sessionBeforeUndo.state(), sessionAfter.state());
                assertEquals(sessionBeforeUndo.pending(), sessionAfter.pending());
                assertEquals(sessionBeforeUndo.tempImage(), sessionAfter.tempImage());
                assertEquals(sessionBeforeUndo.snapshot(), sessionAfter.snapshot());
                assertEquals(sessionBeforeUndo.nextNumber(), sessionAfter.nextNumber());

                assertEquals(productsBeforeUndo.size(), productsAfter.size());
                for (int i = 0; i < productsBeforeUndo.size(); i++) {
                    Catalog.Product p1 = productsBeforeUndo.get(i);
                    Catalog.Product p2 = productsAfter.get(i);
                    assertEquals(p1.id(), p2.id());
                    assertEquals(p1.sessionId(), p2.sessionId());
                    assertEquals(p1.pieceNumber(), p2.pieceNumber());
                    assertEquals(p1.title(), p2.title());
                    assertEquals(p1.price(), p2.price());
                    assertEquals(p1.imageUrl(), p2.imageUrl());
                    assertEquals(p1.sold(), p2.sold());
                    assertEquals(p1, p2);
                }
                return null;
            });
        } finally {
            db.sql("DROP TRIGGER IF EXISTS trg_fail_undo_snapshot").update();
        }
    }

    // ==========================================
    // T2: CARRERAS CONCURRENTES REALES
    // ==========================================

    @Test
    @DisplayName("T2: Confirmación concurrente, Undo concurrente y Confirm vs Reset serializan limpiamente sin estados parciales")
    void testConcurrentRaces() throws Exception {
        // --- 1. Dos sí concurrentes para el mismo borrador ---
        bot.process(SESS, "Publicá Pieza Concurrente a 10000", "/img/conc.jpg");
        assertEquals("AWAITING_CONFIRMATION", catalog.session(SESS).state());

        String otherSess = "other-sess-test";
        catalog.ensure(otherSess);
        Catalog.Session otherBefore = catalog.session(otherSess);
        List<Catalog.Product> otherProdsBefore = catalog.all(otherSess);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger confirmCount = new AtomicInteger(0);

        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> {
                        try {
                            barrier.await(5, TimeUnit.SECONDS);
                            BotEngine.Reply r = bot.process(SESS, "sí", null);
                            if (r.replyText().contains("¡Listo Norma!")) {
                                confirmCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }),
                    executor.submit(() -> {
                        try {
                            barrier.await(5, TimeUnit.SECONDS);
                            BotEngine.Reply r = bot.process(SESS, "sí", null);
                            if (r.replyText().contains("¡Listo Norma!")) {
                                confirmCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
            );

            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }

            assertEquals(1, confirmCount.get());
            assertEquals(9, catalog.all(SESS).size());
            assertEquals("IDLE", catalog.session(SESS).state());
            assertNull(catalog.session(SESS).pending());
            assertNotNull(catalog.session(SESS).snapshot());

            // --- 2. Undo concurrentes ---
            CyclicBarrier undoBarrier = new CyclicBarrier(threads);
            AtomicInteger undoSuccessCount = new AtomicInteger(0);
            AtomicInteger undoNoOpCount = new AtomicInteger(0);

            List<Future<?>> undoFutures = List.of(
                    executor.submit(() -> {
                        try {
                            undoBarrier.await(5, TimeUnit.SECONDS);
                            BotEngine.Reply r = bot.process(SESS, "deshacer", null);
                            if (r.replyText().contains("ya dejé todo como estaba antes")) {
                                undoSuccessCount.incrementAndGet();
                            } else if (r.replyText().contains("Todavía no hay un cambio para deshacer")) {
                                undoNoOpCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }),
                    executor.submit(() -> {
                        try {
                            undoBarrier.await(5, TimeUnit.SECONDS);
                            BotEngine.Reply r = bot.process(SESS, "deshacer", null);
                            if (r.replyText().contains("ya dejé todo como estaba antes")) {
                                undoSuccessCount.incrementAndGet();
                            } else if (r.replyText().contains("Todavía no hay un cambio para deshacer")) {
                                undoNoOpCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
            );

            for (Future<?> f : undoFutures) {
                f.get(10, TimeUnit.SECONDS);
            }

            // Exactamente un undo restaura y consume el snapshot; el otro reporta que no hay cambio
            assertEquals(1, undoSuccessCount.get());
            assertEquals(1, undoNoOpCount.get());
            assertEquals(8, catalog.all(SESS).size());
            assertNull(catalog.session(SESS).snapshot());
            assertEquals("IDLE", catalog.session(SESS).state());

            // --- 3. Confirm vs Reset concurrente ---
            bot.process(SESS, "Cambiá el precio de #1 a 99999", null);
            assertEquals("AWAITING_CONFIRMATION", catalog.session(SESS).state());
            int nextNumberBeforeCr = catalog.session(SESS).nextNumber();

            CyclicBarrier crBarrier = new CyclicBarrier(threads);
            AtomicBoolean confirmWon = new AtomicBoolean(false);
            AtomicBoolean resetWon = new AtomicBoolean(false);

            List<Future<?>> crFutures = List.of(
                    executor.submit(() -> {
                        try {
                            crBarrier.await(5, TimeUnit.SECONDS);
                            BotEngine.Reply r = bot.process(SESS, "sí", null);
                            if (r.replyText().contains("¡Listo Norma!")) {
                                confirmWon.set(true);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }),
                    executor.submit(() -> {
                        try {
                            crBarrier.await(5, TimeUnit.SECONDS);
                            catalog.reset(SESS);
                            resetWon.set(true);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
            );

            for (Future<?> f : crFutures) {
                f.get(10, TimeUnit.SECONDS);
            }

            // Ambos completaron
            assertTrue(resetWon.get());
            // Estado final íntegro en ambos órdenes seriales: seed intacto, borrador limpio, snapshot consumido
            Catalog.Session finalSession = catalog.session(SESS);
            List<Catalog.Product> finalProds = catalog.all(SESS);
            assertEquals("IDLE", finalSession.state());
            assertNull(finalSession.pending());
            assertNull(finalSession.tempImage());
            assertNull(finalSession.snapshot());
            assertEquals(nextNumberBeforeCr, finalSession.nextNumber()); // High-water mark conservado
            assertEquals(8, finalProds.size());
            assertEquals(15000, finalProds.get(0).price()); // Nunca 99999

            // DEFAULT intacto con los 8 productos completos
            List<Catalog.Product> defProds = db.sql("SELECT id,session_id,piece_number,title,price,image_url,sold FROM products WHERE session_id='DEFAULT' ORDER BY piece_number").query(Catalog.Product.class).list();
            assertEquals(8, defProds.size());
            for (int i = 0; i < 8; i++) {
                Catalog.Product defP = defProds.get(i);
                Catalog.Product sessP = finalProds.get(i);
                assertEquals(defP.pieceNumber(), sessP.pieceNumber());
                assertEquals(defP.title(), sessP.title());
                assertEquals(defP.price(), sessP.price());
                assertEquals(defP.imageUrl(), sessP.imageUrl());
                assertEquals(defP.sold(), sessP.sold());
            }

            // Verificar aislamiento: other-sess y DEFAULT siguen 100% intactos
            Catalog.Session otherAfter = catalog.session(otherSess);
            List<Catalog.Product> otherProdsAfter = catalog.all(otherSess);
            assertEquals(otherBefore.id(), otherAfter.id());
            assertEquals(otherBefore.state(), otherAfter.state());
            assertEquals(otherBefore.pending(), otherAfter.pending());
            assertEquals(otherBefore.tempImage(), otherAfter.tempImage());
            assertEquals(otherBefore.snapshot(), otherAfter.snapshot());
            assertEquals(otherBefore.nextNumber(), otherAfter.nextNumber());

            assertEquals(otherProdsBefore.size(), otherProdsAfter.size());
            for (int i = 0; i < otherProdsBefore.size(); i++) {
                Catalog.Product p1 = otherProdsBefore.get(i);
                Catalog.Product p2 = otherProdsAfter.get(i);
                assertEquals(p1.id(), p2.id());
                assertEquals(p1.sessionId(), p2.sessionId());
                assertEquals(p1.pieceNumber(), p2.pieceNumber());
                assertEquals(p1.title(), p2.title());
                assertEquals(p1.price(), p2.price());
                assertEquals(p1.imageUrl(), p2.imageUrl());
                assertEquals(p1.sold(), p2.sold());
                assertEquals(p1, p2);
            }

        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    // ==========================================
    // F3 / F4 / F5 REGRESIONES AUDITORÍA CODEX v1.0
    // ==========================================

    @Test
    @DisplayName("F3 Audit Probe 1: Corrección explícita de precio en DETAILS actualiza borrador y completa con foto")
    void testAuditF3DetailsCorrection() {
        // En DETAILS esperando foto: "Publicá Plato a 15000"
        bot.process(SESS, "Publicá Plato a 15000", null);
        assertEquals("AWAITING_DETAILS", catalog.session(SESS).state());

        // Corrección de precio en DETAILS: "no, dije 18000"
        BotEngine.Reply rCorr = bot.process(SESS, "no, dije 18000", null);
        assertEquals("AWAITING_DETAILS", rCorr.state());

        // Completar con foto pasa a AWAITING_CONFIRMATION con precio corregido (18000)
        BotEngine.Reply rPhoto = bot.process(SESS, "", "/img/plato.jpg");
        assertEquals("AWAITING_CONFIRMATION", rPhoto.state());
        assertTrue(rPhoto.replyText().contains("$ 18.000"));
        assertTrue(rPhoto.replyText().contains("Plato"));
    }

    @Test
    @DisplayName("F3 Audit Probe 2: Esta foto es para cambiar la de la pieza 2 seguido de foto sola completa IMAGE")
    void testAuditF3ImageCompletionWithPhotoAlone() {
        // "Esta foto es para cambiar la de la pieza 2" sin foto pasa a DETAILS esperando foto
        BotEngine.Reply r1 = bot.process(SESS, "Esta foto es para cambiar la de la pieza 2", null);
        assertEquals("AWAITING_DETAILS", r1.state());

        // Mensaje siguiente con solo foto completa ChangeImage(2, foto) a AWAITING_CONFIRMATION
        BotEngine.Reply r2 = bot.process(SESS, "", "/img/nueva_foto_2.jpg");
        assertEquals("AWAITING_CONFIRMATION", r2.state());
        assertTrue(r2.replyText().contains("#2"));
        assertTrue(r2.replyText().contains("cambiar la foto"));
    }

    @Test
    @DisplayName("F3 Audit Probe 3: Borrador en DETAILS protegido contra comando nuevo incompatible y foto de otra acción")
    void testAuditF3DetailsDraftProtectionAgainstIncompatibleAction() {
        // "Publicá Plato a 15000" sin foto
        bot.process(SESS, "Publicá Plato a 15000", null);
        assertEquals("AWAITING_DETAILS", catalog.session(SESS).state());

        // Siguiente mensaje: "Publicá Jarra a 20000" con foto de Jarra
        BotEngine.Reply r2 = bot.process(SESS, "Publicá Jarra a 20000", "/img/jarra.jpg");
        assertEquals("AWAITING_DETAILS", r2.state());
        assertTrue(r2.replyText().contains("Tengo un cambio pendiente"));
        // Borrador previo sigue siendo Plato a 15000 sin mezclar foto de Jarra
        Catalog.Session sess = catalog.session(SESS);
        BotSessionCodec.DecodedPending decoded = codec.decodePending(sess.pending(), SESS, sess.tempImage()).orElseThrow();
        assertEquals("Plato", decoded.draft().title());
        assertEquals(15000, decoded.draft().price());
    }

    @Test
    @DisplayName("F3 Audit Probe 4: Foto sola seguida de #2 determina IMAGE para #2, no CREATE titulado '#2'")
    void testAuditF3PhotoThenReferenceDeterminesImage() {
        // Foto sola pasa a DETAILS
        bot.process(SESS, "", "/img/jarra.jpg");
        assertEquals("AWAITING_DETAILS", catalog.session(SESS).state());

        // Siguiente mensaje: "#2"
        BotEngine.Reply r2 = bot.process(SESS, "#2", null);
        assertEquals("AWAITING_CONFIRMATION", r2.state());
        // Debe ser IMAGE para la pieza #2, no CREATE
        assertTrue(r2.replyText().contains("cambiar la foto"));
        assertTrue(r2.replyText().contains("#2"));
    }

    @Test
    @DisplayName("F3 Audit Probe 5: Aclaración con número desnudo en DETAILS con candidatos resuelve target")
    void testAuditF3AmbiguityBareNumberResolution() {
        // Agregamos otra maceta para crear ambigüedad con #4 (Maceta de barro)
        bot.process(SESS, "Publicá Maceta azul chica a 8000", "/img/m.jpg");
        bot.process(SESS, "sí", null);

        // "Sacá la maceta" es ambigua entre #4 y #9
        BotEngine.Reply rAmb = bot.process(SESS, "Sacá la maceta", null);
        assertEquals("AWAITING_DETAILS", rAmb.state());
        assertTrue(rAmb.replyText().contains("Hay varias piezas que coinciden"));

        // Responder con número desnudo "4" resuelve la pieza #4 para SOLD
        BotEngine.Reply rRes = bot.process(SESS, "4", null);
        assertEquals("AWAITING_CONFIRMATION", rRes.state());
        assertTrue(rRes.replyText().contains("#4"));
        assertTrue(rRes.replyText().contains("retirar la pieza"));
    }

    @Test
    @DisplayName("F3 Audit Probe 6: Sí o no con foto en DETAILS conserva borrador y pide respuesta sin foto")
    void testAuditF3YesNoWithPhotoInDetails() {
        // CREATE esperando precio
        bot.process(SESS, "Maceta nueva", "/img/maceta.jpg");
        assertEquals("AWAITING_DETAILS", catalog.session(SESS).state());

        // "no" con foto adjunta
        BotEngine.Reply rNo = bot.process(SESS, "no", "/img/otra.jpg");
        assertEquals("AWAITING_DETAILS", rNo.state());
        assertTrue(rNo.replyText().contains("Mandame sí para confirmar o no para cancelar, sin foto"));
    }

    @Test
    @DisplayName("F3 Audit: Prioridad de explainMissingField respeta §3 (operación, destino, título, precio, foto)")
    void testAuditF3MissingFieldPriority() {
        // Foto sola: explica ambas alternativas (nueva pieza o foto existente)
        BotEngine.Reply rPhoto = bot.process(SESS, "", "/img/temp.jpg");
        assertTrue(rPhoto.replyText().contains("nombre y el precio para una pieza nueva, o el numerito si es para cambiar la foto"));
    }

    @Test
    @DisplayName("F4 Audit: Invariantes state/metadata combinados limpian pending corrupto y responden sin procesar")
    void testAuditF4StateMetadataInvariants() {
        // 1. IDLE con pending válido SOLD: debe limpiar y responder
        String validSoldPending = codec.encodePendingAction(SESS, new BotModel.Sell(2));
        catalog.conversation(SESS, "IDLE", validSoldPending, null);
        BotEngine.Reply rIdle = bot.process(SESS, "", null);
        assertEquals("IDLE", rIdle.state());
        assertTrue(rIdle.replyText().contains("No pude recuperar el cambio pendiente"));
        assertNull(catalog.session(SESS).pending());

        // 2. AWAITING_DETAILS con acción completa: debe limpiar y responder
        catalog.conversation(SESS, "AWAITING_DETAILS", validSoldPending, null);
        BotEngine.Reply rDetails = bot.process(SESS, "", null);
        assertEquals("IDLE", rDetails.state());
        assertTrue(rDetails.replyText().contains("No pude recuperar el cambio pendiente"));
        assertNull(catalog.session(SESS).pending());
    }

    @Test
    @DisplayName("F5 Audit: Validación uniforme de longitud > 1000 rechaza antes de tocar DB")
    void testAuditF5MessageLengthValidation() {
        String longText = "x".repeat(1001);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> bot.process(SESS, longText, null));
        assertEquals("El mensaje es demasiado largo.", ex.getMessage());
    }

    @Test
    @DisplayName("F5 Audit: Formato argentino de precios $ 18.500 y títulos en confirmaciones y ambigüedad")
    void testAuditF5ArgentineFormatAndTitles() {
        // Confirmación de PRICE incluye $ y título
        BotEngine.Reply rPrice = bot.process(SESS, "Cambiá el precio de #2 a 25000", null);
        assertTrue(rPrice.replyText().contains("$ 25.000"));
        assertTrue(rPrice.replyText().contains("#2 (Jarrón de gres)")); // Pieza 2 es Jarrón de gres en seed
    }

    // ==========================================
    // U1 / F2 REGRESIONES DE RECORRIDOS OBLIGATORIOS (AUDITORÍA F7)
    // ==========================================

    @Test
    @DisplayName("U1 Audit: IMAGE undo restaura la foto original y consume el slot")
    void testUndoImageChange() {
        String originalImg = catalog.all(SESS).get(1).imageUrl();
        bot.process(SESS, "Esta foto es para cambiar la de la pieza 2", "/assets/nueva-foto-2.webp");
        bot.process(SESS, "sí", null);
        assertEquals("/assets/nueva-foto-2.webp", catalog.all(SESS).get(1).imageUrl());

        // Undo restaura foto original
        BotEngine.Reply rUndo = bot.process(SESS, "deshacer", null);
        assertEquals("IDLE", rUndo.state());
        assertEquals(originalImg, catalog.all(SESS).get(1).imageUrl());

        // Segundo undo es no-op
        BotEngine.Reply rUndo2 = bot.process(SESS, "deshacer", null);
        assertTrue(rUndo2.replyText().contains("Todavía no hay un cambio para deshacer"));
    }

    @Test
    @DisplayName("U1 Audit: Dos commits sucesivos (A + B) y undo solo revierte B")
    void testTwoCommitsAndUndoSecondOnly() {
        // Commit A: cambiar precio de #1 a 33000
        bot.process(SESS, "Cambiá el precio de #1 a 33000", null);
        bot.process(SESS, "sí", null);
        assertEquals(33000, catalog.all(SESS).get(0).price());

        // Commit B: cambiar precio de #2 a 44000
        int origPrice2 = catalog.all(SESS).get(1).price();
        bot.process(SESS, "Cambiá el precio de #2 a 44000", null);
        bot.process(SESS, "sí", null);
        assertEquals(44000, catalog.all(SESS).get(1).price());

        // Undo solo revierte B (precio #2 vuelve a origPrice2), mientras A (precio #1 en 33000) permanece
        BotEngine.Reply rUndo = bot.process(SESS, "deshacer", null);
        assertEquals("IDLE", rUndo.state());
        assertEquals(origPrice2, catalog.all(SESS).get(1).price());
        assertEquals(33000, catalog.all(SESS).get(0).price());

        // Segundo undo no existe (slot único)
        BotEngine.Reply rUndo2 = bot.process(SESS, "deshacer", null);
        assertTrue(rUndo2.replyText().contains("Todavía no hay un cambio para deshacer"));
    }

    @Test
    @DisplayName("U1 Audit: Snapshot previo no nulo se conserva en no-op, cancelar y preparar borrador")
    void testSnapshotPreservedAcrossNoOpCancelPrepare() {
        // Realizamos un cambio A efectivo y lo confirmamos
        bot.process(SESS, "Cambiá el precio de #1 a 50000", null);
        bot.process(SESS, "sí", null);
        assertEquals(50000, catalog.all(SESS).get(0).price());
        assertNotNull(catalog.session(SESS).snapshot());

        // 1. Ejecutar no-op: cambiar precio de #1 al mismo 50000
        bot.process(SESS, "Cambiá el precio de #1 a 50000", null);
        BotEngine.Reply rNoOp = bot.process(SESS, "sí", null);
        assertTrue(rNoOp.replyText().contains("Ya estaba así, no hice cambios"));
        // Snapshot previo sigue intacto
        assertNotNull(catalog.session(SESS).snapshot());

        // 2. Preparar un borrador y cancelarlo
        bot.process(SESS, "Cambiá el precio de #2 a 60000", null);
        assertEquals("AWAITING_CONFIRMATION", catalog.session(SESS).state());
        bot.process(SESS, "no", null);
        assertEquals("IDLE", catalog.session(SESS).state());
        // Snapshot previo sigue intacto
        assertNotNull(catalog.session(SESS).snapshot());

        // 3. Preparar un nuevo borrador incompleto en DETAILS
        bot.process(SESS, "Publicá Plato nuevo a 15000", null);
        assertEquals("AWAITING_DETAILS", catalog.session(SESS).state());
        // Snapshot previo sigue intacto
        assertNotNull(catalog.session(SESS).snapshot());

        // 4. Deshacer restaura el cambio A (precio #1 vuelve a 15000)
        BotEngine.Reply rUndo = bot.process(SESS, "deshacer", null);
        assertEquals("IDLE", rUndo.state());
        assertEquals(15000, catalog.all(SESS).get(0).price());
        assertNull(catalog.session(SESS).snapshot()); // consumido
    }

    @Test
    @DisplayName("U1 Audit: Undo durante borrador válido lo descarta si hay snapshot; sin snapshot conserva borrador")
    void testUndoDuringDraftWithAndWithoutSnapshot() {
        // Caso A: Sin snapshot previo en borrador DETAILS
        bot.process(SESS, "Publicá Plato a 15000", null);
        assertEquals("AWAITING_DETAILS", catalog.session(SESS).state());
        BotEngine.Reply rNoSnap = bot.process(SESS, "deshacer", null);
        // Sin snapshot, conserva borrador y explica
        assertEquals("AWAITING_DETAILS", catalog.session(SESS).state());
        assertTrue(rNoSnap.replyText().contains("Todavía no hay un cambio para deshacer"));
        bot.process(SESS, "no", null); // limpiamos

        // Caso B: Con snapshot previo
        bot.process(SESS, "Cambiá el precio de #1 a 40000", null);
        bot.process(SESS, "sí", null); // confirmado, snapshot guardado

        // Creamos nuevo borrador
        bot.process(SESS, "Publicá Jarra a 20000", null);
        assertEquals("AWAITING_DETAILS", catalog.session(SESS).state());

        // Deshacer con snapshot válido restaura el cambio anterior y descarta el borrador -> IDLE
        BotEngine.Reply rUndoWithSnap = bot.process(SESS, "deshacer", null);
        assertEquals("IDLE", rUndoWithSnap.state());
        assertEquals(15000, catalog.all(SESS).get(0).price());
        assertNull(catalog.session(SESS).pending());
        assertNull(catalog.session(SESS).snapshot());
    }

    @Test
    @DisplayName("U1 Audit: Deshacer con catálogo vacío restaura lista vacía")
    void testEmptyCatalogUndo() {
        // Simulamos un catálogo que tenía 0 productos guardando snapshot vacío []
        catalog.snapshot(SESS, codec.encodeSnapshot(SESS, List.of()));
        BotEngine.Reply rUndo = bot.process(SESS, "deshacer", null);
        assertEquals("IDLE", rUndo.state());
        assertEquals(0, catalog.all(SESS).size());
        assertNull(catalog.session(SESS).snapshot());
    }

    @Test
    @DisplayName("F2 Audit: Destino vendido durante confirmación no aplica, pasa a DETAILS y pide otro número")
    void testTargetDisappearedOrSoldDuringConfirmation() {
        bot.process(SESS, "Cambiá el precio de #2 a 25000", null);
        assertEquals("AWAITING_CONFIRMATION", catalog.session(SESS).state());

        // Entre tanto, la pieza 2 es vendida
        catalog.apply(SESS, BotModel.toLegacyIntent(new BotModel.Sell(2)));
        assertTrue(catalog.all(SESS).get(1).sold());

        // El usuario envía sí: detecta destino no disponible, pasa a DETAILS
        BotEngine.Reply rConfirm = bot.process(SESS, "sí", null);
        assertEquals("AWAITING_DETAILS", rConfirm.state());
        assertTrue(rConfirm.replyText().contains("No encuentro la pieza #2 disponible"));
    }
}
