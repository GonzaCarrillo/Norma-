package com.norma.taller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class CatalogRepositoryTest {

    @TempDir
    Path tempDir;

    private Path dbPath;
    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("catalog_repo_test.db");
        contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        JdbcTemplateAutoConfiguration.class,
                        JdbcClientAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class,
                        TransactionAutoConfiguration.class
                ))
                .withPropertyValues(
                        "spring.datasource.url=jdbc:sqlite:" + dbPath.toAbsolutePath(),
                        "spring.datasource.driver-class-name=org.sqlite.JDBC",
                        "spring.datasource.hikari.maximum-pool-size=1",
                        "spring.datasource.hikari.data-source-properties.foreign_keys=true",
                        "spring.datasource.hikari.data-source-properties.busy_timeout=5000",
                        "spring.datasource.hikari.data-source-properties.synchronous=NORMAL",
                        "spring.sql.init.mode=never"
                )
                .withUserConfiguration(CatalogDatabaseInitializer.class, Catalog.class);
    }

    /**
     * R1 Repository:
     * Base nueva tiene DEFAULT exacto de ocho filas. ensure(A), ensure(B): números/títulos/precios/rutas/sold iguales,
     * ids independientes, next=9; cambiar precio/foto/sold/crear en A no cambia B ni DEFAULT.
     */
    @Test
    void testR1_defaultSeedAndSessionCloningIsolation() {
        contextRunner.run(context -> {
            Catalog catalog = context.getBean(Catalog.class);
            JdbcClient db = context.getBean(JdbcClient.class);

            // DEFAULT exacto de 8 filas verificado directamente en DB
            List<Catalog.Product> defaultProducts = db.sql("SELECT id,session_id,piece_number,title,price,image_url,sold FROM products WHERE session_id='DEFAULT' ORDER BY piece_number").query(Catalog.Product.class).list();
            assertEquals(8, defaultProducts.size());
            assertEquals("Cuenco rústico", defaultProducts.get(0).title());
            assertEquals(15000, defaultProducts.get(0).price());
            assertEquals(0, defaultProducts.get(0).sold() ? 1 : 0);

            String sessA = "session_alpha_01";
            String sessB = "session_beta_002";

            catalog.ensure(sessA);
            catalog.ensure(sessB);

            List<Catalog.Product> prodsA = catalog.all(sessA);
            List<Catalog.Product> prodsB = catalog.all(sessB);

            assertEquals(8, prodsA.size());
            assertEquals(8, prodsB.size());

            for (int i = 0; i < 8; i++) {
                Catalog.Product pA = prodsA.get(i);
                Catalog.Product pB = prodsB.get(i);
                Catalog.Product pDef = defaultProducts.get(i);

                assertEquals(pDef.pieceNumber(), pA.pieceNumber());
                assertEquals(pDef.pieceNumber(), pB.pieceNumber());
                assertEquals(pDef.title(), pA.title());
                assertEquals(pDef.title(), pB.title());
                assertEquals(pDef.price(), pA.price());
                assertEquals(pDef.price(), pB.price());
                assertEquals(pDef.imageUrl(), pA.imageUrl());
                assertEquals(pDef.imageUrl(), pB.imageUrl());
                assertEquals(pDef.sold(), pA.sold());
                assertEquals(pDef.sold(), pB.sold());

                assertNotEquals(pA.id(), pB.id(), "Los ids surrogados deben ser independientes");
                assertNotEquals(pA.id(), pDef.id(), "Los ids no deben solapar con DEFAULT");
            }

            assertEquals(9, catalog.session(sessA).nextNumber());
            assertEquals(9, catalog.session(sessB).nextNumber());

            // Mutaciones en A no deben afectar a B ni a DEFAULT
            catalog.apply(sessA, new IntentParser.Intent("PRICE", 1, "Cuenco rústico", 99999, null));
            catalog.apply(sessA, new IntentParser.Intent("SOLD", 2, null, 0, null));

            assertEquals(99999, catalog.all(sessA).get(0).price());
            assertTrue(catalog.all(sessA).get(1).sold());

            assertEquals(15000, catalog.all(sessB).get(0).price());
            assertFalse(catalog.all(sessB).get(1).sold());

            Catalog.Product def0 = db.sql("SELECT id,session_id,piece_number,title,price,image_url,sold FROM products WHERE session_id='DEFAULT' AND piece_number=1").query(Catalog.Product.class).single();
            assertEquals(15000, def0.price());
        });
    }

    /**
     * R2 Repository:
     * ensure repetido no duplica; restore(A,[]) conserva fila sessions y contador; ensure/all devuelven vacío.
     * Todos sold también producen catálogo público vacío sin reseed. Reset explícito sí repone ocho.
     */
    @Test
    void testR2_ensureIdempotentAndEmptyCatalogNeverReseeds() {
        contextRunner.run(context -> {
            Catalog catalog = context.getBean(Catalog.class);
            String sess = "session_test_r2_x";

            catalog.ensure(sess);
            assertEquals(8, catalog.all(sess).size());

            // ensure repetido es no-op
            catalog.ensure(sess);
            assertEquals(8, catalog.all(sess).size());

            // restore vacio
            catalog.restore(sess, List.of());
            assertEquals(0, catalog.all(sess).size());
            assertEquals(9, catalog.session(sess).nextNumber());

            // ensure sobre catalogo vacio no debe repoblar
            catalog.ensure(sess);
            assertEquals(0, catalog.all(sess).size());

            // reset explicito si repone 8 piezas
            catalog.reset(sess);
            assertEquals(8, catalog.all(sess).size());
        });
    }

    /**
     * R5 Repository:
     * Cada método público rechaza sesión inválida/DEFAULT, incluyendo all/session/conversation/snapshot/insert/apply/restore/reset.
     * SQL directo INSERT/UPDATE/DELETE de DEFAULT y cambio de session_id hacia DEFAULT fallan por trigger; DEFAULT sigue idéntico.
     */
    @Test
    void testR5_validationAndDefaultProtection() {
        contextRunner.run(context -> {
            Catalog catalog = context.getBean(Catalog.class);
            DataSource ds = context.getBean(DataSource.class);

            List<String> invalidIds = List.of(
                    "DEFAULT",
                    "short",
                    "this_is_an_extremely_long_session_identifier_that_exceeds_the_eighty_characters_limit_by_far_1234567890",
                    "invalid*char",
                    "with space",
                    "bad#session"
            );

            for (String bad : invalidIds) {
                assertThrows(IllegalArgumentException.class, () -> catalog.ensure(bad), "ensure debe rechazar " + bad);
                assertThrows(IllegalArgumentException.class, () -> catalog.all(bad), "all debe rechazar " + bad);
                assertThrows(IllegalArgumentException.class, () -> catalog.session(bad), "session debe rechazar " + bad);
                assertThrows(IllegalArgumentException.class, () -> catalog.conversation(bad, "IDLE", null, null), "conversation debe rechazar " + bad);
                assertThrows(IllegalArgumentException.class, () -> catalog.snapshot(bad, "{}"), "snapshot debe rechazar " + bad);
                assertThrows(IllegalArgumentException.class, () -> catalog.reset(bad), "reset debe rechazar " + bad);
            }

            // SQL directo contra DEFAULT debe disparar triggers RAISE(ABORT,...)
            try (Connection conn = ds.getConnection();
                 Statement st = conn.createStatement()) {

                assertThrows(SQLException.class, () -> st.execute("INSERT INTO sessions(id, state, next_number) VALUES('DEFAULT', 'IDLE', 9)"));
                assertThrows(SQLException.class, () -> st.execute("UPDATE sessions SET state='AWAITING_DETAILS' WHERE id='DEFAULT'"));
                assertThrows(SQLException.class, () -> st.execute("DELETE FROM sessions WHERE id='DEFAULT'"));

                assertThrows(SQLException.class, () -> st.execute("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES('DEFAULT', 9, 'P9', 10000, '/9.webp', 0)"));
                assertThrows(SQLException.class, () -> st.execute("UPDATE products SET price=9999 WHERE session_id='DEFAULT' AND piece_number=1"));
                assertThrows(SQLException.class, () -> st.execute("DELETE FROM products WHERE session_id='DEFAULT' AND piece_number=1"));
            }
        });
    }

    /**
     * R7 Repository:
     * Dos tareas sincronizadas con barrera invocan ensure del mismo id: una sesión, ocho productos.
     * Varias altas concurrentes en una sesión obtienen números únicos, contador final coherente; otra sesión no cambia.
     */
    @Test
    void testR7_concurrentEnsureAndIsolation() throws Exception {
        contextRunner.run(context -> {
            Catalog catalog = context.getBean(Catalog.class);
            String sharedSess = "session_concurrent_sync";

            int threads = 4;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CyclicBarrier barrier = new CyclicBarrier(threads);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await(5, TimeUnit.SECONDS);
                        catalog.ensure(sharedSess);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }));
            }

            for (Future<?> f : futures) {
                assertDoesNotThrow(() -> f.get(10, TimeUnit.SECONDS));
            }
            executor.shutdown();

            assertEquals(8, catalog.all(sharedSess).size());
            assertEquals(9, catalog.session(sharedSess).nextNumber());
        });
    }

    /**
     * R3 Repository:
     * Alta #9 y #10, vender/restaurar/quitar #9 no renumera #10; restore de snapshot anterior y reset no disminuyen next;
     * alta posterior #11. Sesión nueva sigue empezando #9.
     */
    @Test
    void testR3_monotonicCounterAcrossMutationsResetAndRestore() {
        contextRunner.run(context -> {
            Catalog catalog = context.getBean(Catalog.class);
            String sess = "session_test_r3_seq";
            catalog.ensure(sess);

            assertEquals(9, catalog.session(sess).nextNumber());

            // Alta #9
            catalog.apply(sess, new IntentParser.Intent("CREATE", null, "Pieza Nueve", 30000, "/assets/9.webp"));
            assertEquals(10, catalog.session(sess).nextNumber());
            Catalog.Product p9 = catalog.all(sess).stream().filter(p -> p.pieceNumber() == 9).findFirst().orElseThrow();
            assertEquals("Pieza Nueve", p9.title());

            // Snapshot antes de la #10
            List<Catalog.Product> snapBefore10 = catalog.all(sess);

            // Alta #10
            catalog.apply(sess, new IntentParser.Intent("CREATE", null, "Pieza Diez", 35000, "/assets/10.webp"));
            assertEquals(11, catalog.session(sess).nextNumber());
            Catalog.Product p10 = catalog.all(sess).stream().filter(p -> p.pieceNumber() == 10).findFirst().orElseThrow();
            assertEquals("Pieza Diez", p10.title());

            // Vender #9 no afecta el numero de #10
            catalog.apply(sess, new IntentParser.Intent("SOLD", 9, null, 0, null));
            assertTrue(catalog.all(sess).stream().filter(p -> p.pieceNumber() == 9).findFirst().orElseThrow().sold());
            assertEquals(10, catalog.all(sess).stream().filter(p -> p.pieceNumber() == 10).findFirst().orElseThrow().pieceNumber());
            assertEquals(11, catalog.session(sess).nextNumber());

            // Restaurar snapshot previo (que solo tenia hasta la 9) no disminuye el contador
            catalog.restore(sess, snapBefore10);
            assertEquals(11, catalog.session(sess).nextNumber());

            // Reset no disminuye el contador
            catalog.reset(sess);
            assertEquals(8, catalog.all(sess).size());
            assertEquals(11, catalog.session(sess).nextNumber());

            // Proxima alta usa #11
            catalog.apply(sess, new IntentParser.Intent("CREATE", null, "Pieza Once", 40000, "/assets/11.webp"));
            assertEquals(12, catalog.session(sess).nextNumber());
            assertTrue(catalog.all(sess).stream().anyMatch(p -> p.pieceNumber() == 11));

            // Sesion nueva completamente independiente empieza en 9
            String sessNew = "session_test_r3_new";
            catalog.ensure(sessNew);
            assertEquals(9, catalog.session(sessNew).nextNumber());
        });
    }

    /**
     * R4 Repository:
     * PRICE/IMAGE/SOLD solo cambian campo objetivo; destino inexistente y kind desconocido fallan sin alterar estado.
     * Precio 0/negativo, título/ruta vacíos, número duplicado, sold=2 y número inválido fallan;
     * SQL directo REAL/no-numérico no satisface CHECK. Límite máximo del contador falla sin overflow.
     */
    @Test
    void testR4_fieldTargetingAndDomainValidation() {
        contextRunner.run(context -> {
            Catalog catalog = context.getBean(Catalog.class);
            JdbcClient db = context.getBean(JdbcClient.class);
            String sess = "session_test_r4_val";
            catalog.ensure(sess);

            // Destino inexistente
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("PRICE", 999, null, 10000, null)));
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("IMAGE", 999, null, null, "/img.webp")));
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("SOLD", 999, null, null, null)));
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("UNKNOWN", 1, null, null, null)));

            // Validaciones de dominio en Java y SQL constraints
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("PRICE", 1, null, 0, null)));
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("PRICE", 1, null, -500, null)));
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("IMAGE", 1, null, null, "   ")));

            // SQL directo contra CHECK constraints
            assertThrows(Exception.class, () ->
                    db.sql("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES(?, 99, 'P99', 0, '/img.webp', 0)").param(sess).update());
            assertThrows(Exception.class, () ->
                    db.sql("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES(?, 99, '', 10000, '/img.webp', 0)").param(sess).update());
            assertThrows(Exception.class, () ->
                    db.sql("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES(?, 99, 'P99', 10000, '', 0)").param(sess).update());
            assertThrows(Exception.class, () ->
                    db.sql("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES(?, 99, 'P99', 10000, '/img.webp', 2)").param(sess).update());
            assertThrows(Exception.class, () ->
                    db.sql("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES(?, 99, 'P99', 15.5, '/img.webp', 0)").param(sess).update());

            // Limite maximo de contador (2147483647) falla sin overflow
            db.sql("UPDATE sessions SET next_number=2147483647 WHERE id=?").param(sess).update();
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("CREATE", null, "Overflow", 1000, "/img.webp")));
        });
    }

    /**
     * R6 Repository:
     * restore mixto con Product de B o números duplicados falla antes de borrar A. Lista vacía válida.
     * Inyectar fallo SQL después del primer insert (trigger temporal solo en fixture) durante ensure, restore y reset;
     * ni fila nueva parcial ni borrado parcial ni limpieza de borrador/cambio de contador.
     */
    @Test
    void testR6_restoreAtomicValidationAndFailureRollback() {
        contextRunner.run(context -> {
            Catalog catalog = context.getBean(Catalog.class);
            String sessA = "session_test_r6_a";
            String sessB = "session_test_r6_b";
            catalog.ensure(sessA);
            catalog.ensure(sessB);

            List<Catalog.Product> prodsB = catalog.all(sessB);
            Catalog.Product itemB = prodsB.get(0);

            // Intentar restore en A con un producto de B debe fallar antes de borrar A
            List<Catalog.Product> mixedList = List.of(
                    new Catalog.Product(1, sessA, 1, "Pieza A", 1000, "/a.webp", false),
                    itemB // sesion B
            );
            assertThrows(IllegalArgumentException.class, () -> catalog.restore(sessA, mixedList));
            assertEquals(8, catalog.all(sessA).size());

            // Lista con numeros duplicados debe fallar antes de borrar A
            List<Catalog.Product> dupList = List.of(
                    new Catalog.Product(1, sessA, 1, "Pieza 1", 1000, "/a.webp", false),
                    new Catalog.Product(2, sessA, 1, "Pieza 1bis", 2000, "/b.webp", false)
            );
            assertThrows(IllegalArgumentException.class, () -> catalog.restore(sessA, dupList));
            assertEquals(8, catalog.all(sessA).size());

            // Inyeccion de fallo SQL en transaccion de Catalog (trigger temporal que falla al insertar pieza #2)
            DataSource ds = context.getBean(DataSource.class);
            try (Connection conn = ds.getConnection();
                 Statement st = conn.createStatement()) {
                st.execute("CREATE TRIGGER test_fail_trigger BEFORE INSERT ON products WHEN NEW.piece_number=2 BEGIN SELECT RAISE(ABORT, 'Simulated DB failure in trigger'); END;");
            }

            // Restore de lista con pieza #1 y #2 debe revertir por completo (rollback)
            List<Catalog.Product> testList = List.of(
                    new Catalog.Product(0, sessA, 1, "P1", 1000, "/1.webp", false),
                    new Catalog.Product(0, sessA, 2, "P2", 2000, "/2.webp", false)
            );
            assertThrows(Exception.class, () -> catalog.restore(sessA, testList));
            assertEquals(8, catalog.all(sessA).size(), "Las 8 piezas originales deben permanecer intactas tras rollback");

            // Limpiar trigger temporal
            try (Connection conn = ds.getConnection();
                 Statement st = conn.createStatement()) {
                st.execute("DROP TRIGGER test_fail_trigger");
            }
        });
    }

}
