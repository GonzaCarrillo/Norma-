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

    private static final String SESS = "session_bot_test";

    @BeforeEach
    void setUp() {
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

                // Comparación de productos íntegros (todos los campos lógicos)
                assertEquals(productsBeforeConfirm.size(), productsAfter.size());
                for (int i = 0; i < productsBeforeConfirm.size(); i++) {
                    Catalog.Product p1 = productsBeforeConfirm.get(i);
                    Catalog.Product p2 = productsAfter.get(i);
                    assertEquals(p1.pieceNumber(), p2.pieceNumber());
                    assertEquals(p1.title(), p2.title());
                    assertEquals(p1.price(), p2.price());
                    assertEquals(p1.imageUrl(), p2.imageUrl());
                    assertEquals(p1.sold(), p2.sold());
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
                    assertEquals(p1.pieceNumber(), p2.pieceNumber());
                    assertEquals(p1.title(), p2.title());
                    assertEquals(p1.price(), p2.price());
                    assertEquals(p1.imageUrl(), p2.imageUrl());
                    assertEquals(p1.sold(), p2.sold());
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
            // Estado final es consistente con orden serial: nunca parcial
            Catalog.Session finalSession = catalog.session(SESS);
            List<Catalog.Product> finalProds = catalog.all(SESS);
            assertEquals("IDLE", finalSession.state());
            assertNull(finalSession.pending());
            assertEquals(8, finalProds.size());

            if (confirmWon.get()) {
                // Caso A: Confirm ejecutó primero, y luego reset reseteó a DEFAULT (precio 15000 inicial)
                // O caso B: Reset ejecutó primero limpiando el pending, el confirm devolvió "No hay cambio pendiente"
                // En cualquier caso serial, no hay pending parcial ni inconsistencia
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
                assertEquals(p1.pieceNumber(), p2.pieceNumber());
                assertEquals(p1.title(), p2.title());
                assertEquals(p1.price(), p2.price());
                assertEquals(p1.imageUrl(), p2.imageUrl());
                assertEquals(p1.sold(), p2.sold());
            }

            // DEFAULT intacto
            List<Catalog.Product> defProds = db.sql("SELECT id,session_id,piece_number,title,price,image_url,sold FROM products WHERE session_id='DEFAULT' ORDER BY piece_number").query(Catalog.Product.class).list();
            assertEquals(8, defProds.size());
            assertEquals(1, defProds.get(0).pieceNumber());
            assertEquals("Cuenco rústico", defProds.get(0).title());
            assertEquals(15000, defProds.get(0).price());
            assertFalse(defProds.get(0).sold());

        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
