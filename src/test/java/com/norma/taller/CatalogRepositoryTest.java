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
import java.util.stream.Collectors;
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
            catalog.apply(sessA, new IntentParser.Intent("IMAGE", 1, null, null, "/assets/cuenco_custom.webp"));
            catalog.apply(sessA, new IntentParser.Intent("SOLD", 2, null, 0, null));
            catalog.apply(sessA, new IntentParser.Intent("CREATE", null, "Pieza Nueva A9", 45000, "/assets/a9.webp"));

            assertEquals(99999, catalog.all(sessA).get(0).price());
            assertEquals("/assets/cuenco_custom.webp", catalog.all(sessA).get(0).imageUrl());
            assertTrue(catalog.all(sessA).get(1).sold());
            assertEquals(9, catalog.all(sessA).size());

            // B debe permanecer completamente intacto con sus 8 piezas originales
            List<Catalog.Product> prodsBAfter = catalog.all(sessB);
            assertEquals(8, prodsBAfter.size());
            for (int i = 0; i < 8; i++) {
                assertEquals(defaultProducts.get(i).pieceNumber(), prodsBAfter.get(i).pieceNumber());
                assertEquals(defaultProducts.get(i).title(), prodsBAfter.get(i).title());
                assertEquals(defaultProducts.get(i).price(), prodsBAfter.get(i).price());
                assertEquals(defaultProducts.get(i).imageUrl(), prodsBAfter.get(i).imageUrl());
                assertEquals(defaultProducts.get(i).sold(), prodsBAfter.get(i).sold());
            }

            // DEFAULT debe permanecer 100% idéntico al original en sus 8 piezas
            List<Catalog.Product> defaultAfter = db.sql("SELECT id,session_id,piece_number,title,price,image_url,sold FROM products WHERE session_id='DEFAULT' ORDER BY piece_number").query(Catalog.Product.class).list();
            assertEquals(8, defaultAfter.size());
            for (int i = 0; i < 8; i++) {
                assertEquals(defaultProducts.get(i).pieceNumber(), defaultAfter.get(i).pieceNumber());
                assertEquals(defaultProducts.get(i).title(), defaultAfter.get(i).title());
                assertEquals(defaultProducts.get(i).price(), defaultAfter.get(i).price());
                assertEquals(defaultProducts.get(i).imageUrl(), defaultAfter.get(i).imageUrl());
                assertEquals(defaultProducts.get(i).sold(), defaultAfter.get(i).sold());
            }

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

            // Marcar todas como vendidas en sesion independiente: catalogo disponible queda vacio sin reseed
            String sessSoldAll = "session_test_r2_sold";
            catalog.ensure(sessSoldAll);
            for (int i = 1; i <= 8; i++) {
                catalog.apply(sessSoldAll, new IntentParser.Intent("SOLD", i, null, 0, null));
            }
            List<Catalog.Product> allItems = catalog.all(sessSoldAll);
            assertEquals(8, allItems.size());
            assertTrue(allItems.stream().allMatch(Catalog.Product::sold), "Todas las piezas deben estar sold=true");
            // ensure repetido sobre sesion donde todo esta vendido no altera nada ni re-siembra
            catalog.ensure(sessSoldAll);
            assertEquals(8, catalog.all(sessSoldAll).size());
            assertTrue(catalog.all(sessSoldAll).stream().allMatch(Catalog.Product::sold));

            // reset explicito si repone 8 piezas disponibles
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
            JdbcClient db = context.getBean(JdbcClient.class);

            // Capturar estado original exacto de DEFAULT
            List<Catalog.Product> defaultInitial = db.sql("SELECT id,session_id,piece_number,title,price,image_url,sold FROM products WHERE session_id='DEFAULT' ORDER BY piece_number").query(Catalog.Product.class).list();
            assertEquals(8, defaultInitial.size());

            List<String> invalidIds = new ArrayList<>(List.of(
                    "DEFAULT",
                    "short",
                    "this_is_an_extremely_long_session_identifier_that_exceeds_the_eighty_characters_limit_by_far_1234567890",
                    "invalid*char",
                    "with space",
                    "bad#session"
            ));

            for (String bad : invalidIds) {
                assertThrows(IllegalArgumentException.class, () -> catalog.ensure(bad), "ensure debe rechazar " + bad);
                assertThrows(IllegalArgumentException.class, () -> catalog.all(bad), "all debe rechazar " + bad);
                assertThrows(IllegalArgumentException.class, () -> catalog.session(bad), "session debe rechazar " + bad);
                assertThrows(IllegalArgumentException.class, () -> catalog.conversation(bad, "IDLE", null, null), "conversation debe rechazar " + bad);
                assertThrows(IllegalArgumentException.class, () -> catalog.snapshot(bad, "{}"), "snapshot debe rechazar " + bad);
                assertThrows(IllegalArgumentException.class, () -> catalog.reset(bad), "reset debe rechazar " + bad);
                assertThrows(IllegalArgumentException.class, () -> catalog.insert(bad, 99, "P99", 1000, "/99.webp", false), "insert debe rechazar " + bad);
                assertThrows(IllegalArgumentException.class, () -> catalog.apply(bad, new IntentParser.Intent("CREATE", null, "P99", 1000, "/99.webp")), "apply debe rechazar " + bad);
                assertThrows(IllegalArgumentException.class, () -> catalog.restore(bad, List.of()), "restore debe rechazar " + bad);
            }

            // Validacion explicita de null
            assertThrows(IllegalArgumentException.class, () -> catalog.ensure(null));
            assertThrows(IllegalArgumentException.class, () -> catalog.all(null));
            assertThrows(IllegalArgumentException.class, () -> catalog.session(null));
            assertThrows(IllegalArgumentException.class, () -> catalog.conversation(null, "IDLE", null, null));
            assertThrows(IllegalArgumentException.class, () -> catalog.snapshot(null, "{}"));
            assertThrows(IllegalArgumentException.class, () -> catalog.reset(null));
            assertThrows(IllegalArgumentException.class, () -> catalog.insert(null, 99, "P99", 1000, "/99.webp", false));
            assertThrows(IllegalArgumentException.class, () -> catalog.apply(null, new IntentParser.Intent("CREATE", null, "P99", 1000, "/99.webp")));
            assertThrows(IllegalArgumentException.class, () -> catalog.restore(null, List.of()));

            // Intento de mover session_id hacia DEFAULT en products via SQL
            String normalSess = "session_test_r5_norm";
            catalog.ensure(normalSess);
            try (Connection conn = ds.getConnection();
                 Statement st = conn.createStatement()) {

                // Intentos SQL directos contra DEFAULT disparan triggers protect_default_*
                assertThrows(SQLException.class, () -> st.execute("INSERT INTO sessions(id, state, next_number) VALUES('DEFAULT', 'IDLE', 9)"));
                assertThrows(SQLException.class, () -> st.execute("UPDATE sessions SET state='AWAITING_DETAILS' WHERE id='DEFAULT'"));
                assertThrows(SQLException.class, () -> st.execute("DELETE FROM sessions WHERE id='DEFAULT'"));

                assertThrows(SQLException.class, () -> st.execute("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES('DEFAULT', 9, 'P9', 10000, '/9.webp', 0)"));
                assertThrows(SQLException.class, () -> st.execute("UPDATE products SET price=9999 WHERE session_id='DEFAULT' AND piece_number=1"));
                assertThrows(SQLException.class, () -> st.execute("DELETE FROM products WHERE session_id='DEFAULT' AND piece_number=1"));

                // Intentar actualizar session_id de un producto existente hacia DEFAULT debe fallar por trigger y no por UNIQUE
                // Usamos piece_number=999 (numero libre en DEFAULT) para aislar la guarda protect_default_products_update
                st.execute("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES('" + normalSess + "', 999, 'P999', 10000, '/999.webp', 0)");
                SQLException updateEx = assertThrows(SQLException.class, () ->
                        st.execute("UPDATE products SET session_id='DEFAULT' WHERE session_id='" + normalSess + "' AND piece_number=999"));
                assertTrue(updateEx.getMessage().contains("DEFAULT products are immutable"),
                        "Debe fallar por la guarda protect_default_products_update: " + updateEx.getMessage());
            }

            // Comparacion integral final de DEFAULT: debe permanecer 100% identico al original
            List<Catalog.Product> defaultFinal = db.sql("SELECT id,session_id,piece_number,title,price,image_url,sold FROM products WHERE session_id='DEFAULT' ORDER BY piece_number").query(Catalog.Product.class).list();
            assertEquals(8, defaultFinal.size());
            for (int i = 0; i < 8; i++) {
                assertEquals(defaultInitial.get(i).id(), defaultFinal.get(i).id());
                assertEquals(defaultInitial.get(i).sessionId(), defaultFinal.get(i).sessionId());
                assertEquals(defaultInitial.get(i).pieceNumber(), defaultFinal.get(i).pieceNumber());
                assertEquals(defaultInitial.get(i).title(), defaultFinal.get(i).title());
                assertEquals(defaultInitial.get(i).price(), defaultFinal.get(i).price());
                assertEquals(defaultInitial.get(i).imageUrl(), defaultFinal.get(i).imageUrl());
                assertEquals(defaultInitial.get(i).sold(), defaultFinal.get(i).sold());
            }
        });
    }

    /**
     * R7 Repository:
     * Dos tareas sincronizadas con barrera invocan ensure del mismo id: una sesión, ocho productos.
     * Varias altas concurrentes en una sesión obtienen números únicos, contador final coherente; otra sesión no cambia.
     * Executor cerrado en bloque finally.
     */
    @Test
    void testR7_concurrentEnsureAndIsolation() throws Exception {
        contextRunner.run(context -> {
            Catalog catalog = context.getBean(Catalog.class);
            String sharedSess = "session_concurrent_sync";

            // Fase 1: ensure concurrente con barrera
            int threads = 4;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
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

                assertEquals(8, catalog.all(sharedSess).size());
                assertEquals(9, catalog.session(sharedSess).nextNumber());
            } finally {
                executor.shutdown();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }

            // Fase 2: Altas concurrentes (CREATE) en sharedSess verificando unicidad y sesion B aislada
            String sessB = "session_concurrent_sess_b";
            catalog.ensure(sessB);
            List<Catalog.Product> prodsBBefore = catalog.all(sessB);
            assertEquals(8, prodsBBefore.size());

            int createThreads = 5;
            ExecutorService createExecutor = Executors.newFixedThreadPool(createThreads);
            try {
                CyclicBarrier createBarrier = new CyclicBarrier(createThreads);
                List<Future<?>> createFutures = new ArrayList<>();

                for (int i = 0; i < createThreads; i++) {
                    final int idx = i;
                    createFutures.add(createExecutor.submit(() -> {
                        try {
                            createBarrier.await(5, TimeUnit.SECONDS);
                            catalog.apply(sharedSess, new IntentParser.Intent("CREATE", null, "Pieza Concurrente " + idx, 20000 + idx * 1000, "/assets/c" + idx + ".webp"));
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    }));
                }

                for (Future<?> f : createFutures) {
                    assertDoesNotThrow(() -> f.get(10, TimeUnit.SECONDS));
                }

                // Total de piezas en sharedSess: 8 iniciales + 5 altas = 13 piezas
                List<Catalog.Product> sharedProds = catalog.all(sharedSess);
                assertEquals(13, sharedProds.size());

                // Numeros de pieza asignados deben ser unicos y cubrir 9..13
                Set<Integer> pieceNumbers = sharedProds.stream().map(Catalog.Product::pieceNumber).collect(Collectors.toSet());
                assertEquals(13, pieceNumbers.size(), "Cada pieza debe tener un piece_number unico");
                for (int n = 1; n <= 13; n++) {
                    assertTrue(pieceNumbers.contains(n), "Debe contener piece_number=" + n);
                }

                // El contador next_number debe ser exactamente 14
                assertEquals(14, catalog.session(sharedSess).nextNumber());

                // Sesion B debe permanecer completamente intacta
                List<Catalog.Product> prodsBAfter = catalog.all(sessB);
                assertEquals(8, prodsBAfter.size());
                assertEquals(9, catalog.session(sessB).nextNumber());
                for (int i = 0; i < 8; i++) {
                    assertEquals(prodsBBefore.get(i).pieceNumber(), prodsBAfter.get(i).pieceNumber());
                    assertEquals(prodsBBefore.get(i).title(), prodsBAfter.get(i).title());
                    assertEquals(prodsBBefore.get(i).price(), prodsBAfter.get(i).price());
                    assertEquals(prodsBBefore.get(i).sold(), prodsBAfter.get(i).sold());
                }
            } finally {
                createExecutor.shutdown();
                assertTrue(createExecutor.awaitTermination(5, TimeUnit.SECONDS));
            }
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

            // Verificacion de targeting exacto: PRICE solo cambia price, preservando title, imageUrl, sold, pieceNumber
            Catalog.Product p1Before = catalog.all(sess).get(0);
            catalog.apply(sess, new IntentParser.Intent("PRICE", 1, null, 17500, null));
            Catalog.Product p1AfterPrice = catalog.all(sess).get(0);
            assertEquals(17500, p1AfterPrice.price());
            assertEquals(p1Before.title(), p1AfterPrice.title());
            assertEquals(p1Before.imageUrl(), p1AfterPrice.imageUrl());
            assertEquals(p1Before.sold(), p1AfterPrice.sold());
            assertEquals(p1Before.pieceNumber(), p1AfterPrice.pieceNumber());

            // IMAGE solo cambia imageUrl, preservando los demas campos
            catalog.apply(sess, new IntentParser.Intent("IMAGE", 1, null, null, "/assets/new_p1.webp"));
            Catalog.Product p1AfterImg = catalog.all(sess).get(0);
            assertEquals("/assets/new_p1.webp", p1AfterImg.imageUrl());
            assertEquals(17500, p1AfterImg.price());
            assertEquals(p1Before.title(), p1AfterImg.title());
            assertEquals(p1Before.sold(), p1AfterImg.sold());

            // SOLD solo cambia sold a true, preservando los demas campos
            catalog.apply(sess, new IntentParser.Intent("SOLD", 1, null, null, null));
            Catalog.Product p1AfterSold = catalog.all(sess).get(0);
            assertTrue(p1AfterSold.sold());
            assertEquals(17500, p1AfterSold.price());
            assertEquals("/assets/new_p1.webp", p1AfterSold.imageUrl());
            assertEquals(p1Before.title(), p1AfterSold.title());

            // Destino inexistente
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("PRICE", 999, null, 10000, null)));
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("IMAGE", 999, null, null, "/img.webp")));
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("SOLD", 999, null, null, null)));
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("UNKNOWN", 1, null, null, null)));

            // Capturar estado completo antes de pruebas de fallo
            List<Catalog.Product> prodsBeforeErrors = catalog.all(sess);
            Catalog.Session sessBeforeErrors = catalog.session(sess);

            // Intentos fallidos de apply por dominio no deben alterar el catalogo ni la sesion
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("PRICE", 1, null, 0, null)));
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("PRICE", 1, null, -500, null)));
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("IMAGE", 1, null, null, "   ")));

            // Verificar igualdad completa tras fallos de apply (Session y List<Product> exactos)
            List<Catalog.Product> prodsAfterApplyErrors = catalog.all(sess);
            assertEquals(prodsBeforeErrors.size(), prodsAfterApplyErrors.size());
            for (int i = 0; i < prodsBeforeErrors.size(); i++) {
                assertEquals(prodsBeforeErrors.get(i).id(), prodsAfterApplyErrors.get(i).id());
                assertEquals(prodsBeforeErrors.get(i).sessionId(), prodsAfterApplyErrors.get(i).sessionId());
                assertEquals(prodsBeforeErrors.get(i).pieceNumber(), prodsAfterApplyErrors.get(i).pieceNumber());
                assertEquals(prodsBeforeErrors.get(i).title(), prodsAfterApplyErrors.get(i).title());
                assertEquals(prodsBeforeErrors.get(i).price(), prodsAfterApplyErrors.get(i).price());
                assertEquals(prodsBeforeErrors.get(i).imageUrl(), prodsAfterApplyErrors.get(i).imageUrl());
                assertEquals(prodsBeforeErrors.get(i).sold(), prodsAfterApplyErrors.get(i).sold());
            }
            Catalog.Session sessAfterApplyErrors = catalog.session(sess);
            assertEquals(sessBeforeErrors.id(), sessAfterApplyErrors.id());
            assertEquals(sessBeforeErrors.state(), sessAfterApplyErrors.state());
            assertEquals(sessBeforeErrors.pending(), sessAfterApplyErrors.pending());
            assertEquals(sessBeforeErrors.tempImage(), sessAfterApplyErrors.tempImage());
            assertEquals(sessBeforeErrors.snapshot(), sessAfterApplyErrors.snapshot());
            assertEquals(sessBeforeErrors.nextNumber(), sessAfterApplyErrors.nextNumber());

            // SQL directo contra CHECK constraints, UNIQUE duplicado y tipos no numericos
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
            assertThrows(Exception.class, () ->
                    db.sql("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES(?, 99, 'P99', 'no_numeric', '/img.webp', 0)").param(sess).update());
            assertThrows(Exception.class, () ->
                    db.sql("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES(?, 1, 'P1_DUP', 10000, '/img.webp', 0)").param(sess).update(),
                    "Numero de pieza duplicado en la misma sesion debe fallar por UNIQUE");
            assertThrows(Exception.class, () ->
                    db.sql("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES(?, -1, 'P_NEG', 10000, '/img.webp', 0)").param(sess).update(),
                    "Numero de pieza negativo debe fallar por CHECK");

            // Limite maximo de contador (2147483647) falla sin overflow y conserva estado
            db.sql("UPDATE sessions SET next_number=2147483647 WHERE id=?").param(sess).update();
            assertThrows(IllegalArgumentException.class, () ->
                    catalog.apply(sess, new IntentParser.Intent("CREATE", null, "Overflow", 1000, "/img.webp")));
            assertEquals(2147483647, catalog.session(sess).nextNumber(), "next_number no debe cambiar tras fallo de agotamiento");
            assertEquals(prodsBeforeErrors.size(), catalog.all(sess).size(), "Total de piezas debe conservarse intacto tras fallo");
        });
    }


    /**
     * R6 Repository:
     * restore mixto con Product de B o números duplicados falla antes de borrar A. Lista vacía válida.
     * Inyectar fallo SQL después del primer insert (trigger temporal solo en fixture) durante ensure, restore y reset;
     * ni fila nueva parcial ni borrado parcial ni limpieza de borrador/cambio de contador.
     * Comprobación desde nueva transacción/conexión independiente.
     */
    @Test
    void testR6_restoreAtomicValidationAndFailureRollback() {
        contextRunner.run(context -> {
            Catalog catalog = context.getBean(Catalog.class);
            DataSource ds = context.getBean(DataSource.class);
            JdbcClient db = context.getBean(JdbcClient.class);

            String sessA = "session_test_r6_a";
            String sessB = "session_test_r6_b";
            catalog.ensure(sessA);
            catalog.ensure(sessB);

            // Preparar estado de sesion A con borrador y snapshot
            catalog.conversation(sessA, "AWAITING_DETAILS", "{\"draft\":true}", "/tmp/draft.png");
            catalog.snapshot(sessA, "[{\"piece\":1}]");

            // Capturar estado previo exacto de A
            Catalog.Session sABefore = catalog.session(sessA);
            List<Catalog.Product> prodsABefore = catalog.all(sessA);
            assertEquals(8, prodsABefore.size());

            List<Catalog.Product> prodsB = catalog.all(sessB);
            Catalog.Product itemB = prodsB.get(0);

            // Subcaso 1: Intentar restore en A con un producto de B debe fallar antes de borrar A
            List<Catalog.Product> mixedList = List.of(
                    new Catalog.Product(1, sessA, 1, "Pieza A", 1000, "/a.webp", false),
                    itemB // sesion B
            );
            assertThrows(IllegalArgumentException.class, () -> catalog.restore(sessA, mixedList));
            assertEquals(8, catalog.all(sessA).size());

            // Subcaso 2: Lista con numeros duplicados debe fallar antes de borrar A
            List<Catalog.Product> dupList = List.of(
                    new Catalog.Product(1, sessA, 1, "Pieza 1", 1000, "/a.webp", false),
                    new Catalog.Product(2, sessA, 1, "Pieza 1bis", 2000, "/b.webp", false)
            );
            assertThrows(IllegalArgumentException.class, () -> catalog.restore(sessA, dupList));
            assertEquals(8, catalog.all(sessA).size());

            // Subcaso 3: Fallo SQL inyectado durante restore tras el primer insert
            // Trigger temporal que falla al insertar piece_number=2
            try (Connection conn = ds.getConnection();
                 Statement st = conn.createStatement()) {
                st.execute("CREATE TRIGGER test_fail_trigger_restore BEFORE INSERT ON products WHEN NEW.piece_number=2 BEGIN SELECT RAISE(ABORT, 'Simulated DB failure in trigger'); END;");
            }

            List<Catalog.Product> testList = List.of(
                    new Catalog.Product(0, sessA, 1, "P1", 1000, "/1.webp", false),
                    new Catalog.Product(0, sessA, 2, "P2", 2000, "/2.webp", false)
            );
            assertThrows(Exception.class, () -> catalog.restore(sessA, testList));

            // Limpiar trigger de restore
            try (Connection conn = ds.getConnection();
                 Statement st = conn.createStatement()) {
                st.execute("DROP TRIGGER test_fail_trigger_restore");
            }

            // Comprobacion integral desde nueva transaccion: no hay borrado parcial, ni fila nueva parcial, ni alteracion de estado
            List<Catalog.Product> prodsAAfterRestoreFail = db.sql("SELECT id,session_id,piece_number,title,price,image_url,sold FROM products WHERE session_id=? ORDER BY piece_number").param(sessA).query(Catalog.Product.class).list();
            assertEquals(8, prodsAAfterRestoreFail.size(), "Las 8 piezas originales deben permanecer intactas tras rollback de restore");
            for (int i = 0; i < 8; i++) {
                assertEquals(prodsABefore.get(i).id(), prodsAAfterRestoreFail.get(i).id());
                assertEquals(prodsABefore.get(i).sessionId(), prodsAAfterRestoreFail.get(i).sessionId());
                assertEquals(prodsABefore.get(i).pieceNumber(), prodsAAfterRestoreFail.get(i).pieceNumber());
                assertEquals(prodsABefore.get(i).title(), prodsAAfterRestoreFail.get(i).title());
                assertEquals(prodsABefore.get(i).price(), prodsAAfterRestoreFail.get(i).price());
                assertEquals(prodsABefore.get(i).imageUrl(), prodsAAfterRestoreFail.get(i).imageUrl());
                assertEquals(prodsABefore.get(i).sold(), prodsAAfterRestoreFail.get(i).sold());
            }
            Catalog.Session sAAfterRestoreFail = catalog.session(sessA);
            assertEquals(sABefore.nextNumber(), sAAfterRestoreFail.nextNumber(), "next_number no debe cambiar tras restore fallido");
            assertEquals(sABefore.state(), sAAfterRestoreFail.state(), "state no debe cambiar tras restore fallido");
            assertEquals(sABefore.pending(), sAAfterRestoreFail.pending(), "pending draft no debe cambiar tras restore fallido");
            assertEquals(sABefore.tempImage(), sAAfterRestoreFail.tempImage(), "tempImage no debe cambiar tras restore fallido");
            assertEquals(sABefore.snapshot(), sAAfterRestoreFail.snapshot(), "snapshot no debe cambiar tras restore fallido");

            // Subcaso 4: Fallo SQL inyectado durante ensure (clonacion de DEFAULT)
            // Trigger temporal que falla al insertar la pieza #3 clonada
            try (Connection conn = ds.getConnection();
                 Statement st = conn.createStatement()) {
                st.execute("CREATE TRIGGER test_fail_trigger_ensure BEFORE INSERT ON products WHEN NEW.piece_number=3 AND NEW.session_id='session_fail_ensure' BEGIN SELECT RAISE(ABORT, 'Simulated failure during ensure clone'); END;");
            }

            String sessFailEnsure = "session_fail_ensure";
            assertThrows(Exception.class, () -> catalog.ensure(sessFailEnsure));

            // Limpiar trigger
            try (Connection conn = ds.getConnection();
                 Statement st = conn.createStatement()) {
                st.execute("DROP TRIGGER test_fail_trigger_ensure");
            }

            // Verificar que no se creo fila parcial de sesion ni piezas huerfanas de session_fail_ensure
            Integer sessCount = db.sql("SELECT count(*) FROM sessions WHERE id=?").param(sessFailEnsure).query(Integer.class).single();
            assertEquals(0, sessCount, "La fila de sesion no debe existir tras rollback de ensure fallido");
            Integer prodCount = db.sql("SELECT count(*) FROM products WHERE session_id=?").param(sessFailEnsure).query(Integer.class).single();
            assertEquals(0, prodCount, "No deben existir productos parciales tras rollback de ensure fallido");

            // Subcaso 5: Fallo SQL inyectado durante reset
            // Trigger temporal que falla al clonar pieza #4 durante reset
            try (Connection conn = ds.getConnection();
                 Statement st = conn.createStatement()) {
                st.execute("CREATE TRIGGER test_fail_trigger_reset BEFORE INSERT ON products WHEN NEW.piece_number=4 AND NEW.session_id='" + sessA + "' BEGIN SELECT RAISE(ABORT, 'Simulated failure during reset'); END;");
            }

            assertThrows(Exception.class, () -> catalog.reset(sessA));

            // Limpiar trigger
            try (Connection conn = ds.getConnection();
                 Statement st = conn.createStatement()) {
                st.execute("DROP TRIGGER test_fail_trigger_reset");
            }

            // Comprobar que reset fallido no borro las piezas de A ni altero contador ni borrador
            List<Catalog.Product> prodsAAfterResetFail = db.sql("SELECT id,session_id,piece_number,title,price,image_url,sold FROM products WHERE session_id=? ORDER BY piece_number").param(sessA).query(Catalog.Product.class).list();
            assertEquals(8, prodsAAfterResetFail.size(), "Las piezas originales de A no deben borrarse parcialmente tras reset fallido");
            for (int i = 0; i < 8; i++) {
                assertEquals(prodsABefore.get(i).pieceNumber(), prodsAAfterResetFail.get(i).pieceNumber());
                assertEquals(prodsABefore.get(i).title(), prodsAAfterResetFail.get(i).title());
                assertEquals(prodsABefore.get(i).price(), prodsAAfterResetFail.get(i).price());
                assertEquals(prodsABefore.get(i).imageUrl(), prodsAAfterResetFail.get(i).imageUrl());
                assertEquals(prodsABefore.get(i).sold(), prodsAAfterResetFail.get(i).sold());
            }
            Catalog.Session sAAfterResetFail = catalog.session(sessA);
            assertEquals(sABefore.nextNumber(), sAAfterResetFail.nextNumber(), "next_number no debe cambiar tras reset fallido");
            assertEquals(sABefore.state(), sAAfterResetFail.state(), "state no debe cambiar tras reset fallido");
            assertEquals(sABefore.pending(), sAAfterResetFail.pending(), "pending draft no debe limpiarse tras reset fallido");
            assertEquals(sABefore.tempImage(), sAAfterResetFail.tempImage(), "tempImage no debe limpiarse tras reset fallido");
            assertEquals(sABefore.snapshot(), sAAfterResetFail.snapshot(), "snapshot no debe limpiarse tras reset fallido");

            // Subcaso 6: reset exitoso limpia por completo state, pending, temp_image, snapshot y restaura piezas exactas de seed
            catalog.reset(sessA);
            Catalog.Session sAAfterResetSuccess = catalog.session(sessA);
            assertEquals("IDLE", sAAfterResetSuccess.state(), "state debe ser IDLE tras reset exitoso");
            assertNull(sAAfterResetSuccess.pending(), "pending debe ser null tras reset exitoso");
            assertNull(sAAfterResetSuccess.tempImage(), "tempImage debe ser null tras reset exitoso");
            assertNull(sAAfterResetSuccess.snapshot(), "snapshot debe ser null tras reset exitoso");
            assertEquals(sABefore.nextNumber(), sAAfterResetSuccess.nextNumber(), "next_number monótono debe mantenerse");
            List<Catalog.Product> prodsAAfterResetSuccess = catalog.all(sessA);
            assertEquals(8, prodsAAfterResetSuccess.size());
            for (int i = 0; i < 8; i++) {
                assertEquals(prodsABefore.get(i).pieceNumber(), prodsAAfterResetSuccess.get(i).pieceNumber());
                assertEquals(prodsABefore.get(i).title(), prodsAAfterResetSuccess.get(i).title());
                assertEquals(prodsABefore.get(i).price(), prodsAAfterResetSuccess.get(i).price());
                assertEquals(prodsABefore.get(i).imageUrl(), prodsAAfterResetSuccess.get(i).imageUrl());
                assertFalse(prodsAAfterResetSuccess.get(i).sold(), "sold debe ser false tras reset");
            }
        });
    }

}
