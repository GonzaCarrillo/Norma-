package com.norma.taller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class CatalogPersistenceTest {

    @TempDir
    Path tempDir;

    private Path dbPath;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("catalog_persistence.db");
    }

    private ApplicationContextRunner createContextRunner() {
        return new ApplicationContextRunner()
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
     * P3 Persistence:
     * PRAGMA journal_mode=wal en archivo, foreign_keys=1, busy_timeout=5000, synchronous=NORMAL en conexión efectiva.
     * Cerrar/renovar conexión física y repetir; huérfano SQL rechazado. No aceptar memoria como evidencia WAL.
     */
    @Test
    void testP3_physicalFilePragmasAndForeignKeyEnforcement() throws Exception {
        createContextRunner().run(context -> {
            DataSource ds = context.getBean(DataSource.class);
            com.zaxxer.hikari.HikariDataSource hikariDs = (com.zaxxer.hikari.HikariDataSource) ds;

            // Conexion 1
            Connection conn1 = hikariDs.getConnection();
            Connection raw1 = conn1.unwrap(org.sqlite.SQLiteConnection.class);
            try (Statement st = conn1.createStatement()) {
                // journal_mode en archivo
                try (ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
                    assertTrue(rs.next());
                    assertEquals("wal", rs.getString(1).toLowerCase());
                }
                // foreign_keys activo
                try (ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
                // busy_timeout
                try (ResultSet rs = st.executeQuery("PRAGMA busy_timeout")) {
                    assertTrue(rs.next());
                    assertEquals(5000, rs.getInt(1));
                }
                // synchronous = NORMAL (1)
                try (ResultSet rs = st.executeQuery("PRAGMA synchronous")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
                // Rechazo de producto huerfano por foreign key
                assertThrows(Exception.class, () ->
                        st.execute("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) " +
                                "VALUES('non_existent_sess', 1, 'Huerfano', 1000, '/img.png', 0)"));
            }

            // F6: Expulsar y renovar fisicamente la conexion del pool
            hikariDs.evictConnection(conn1);
            conn1.close();

            // Conexion 2 (conexion fisica nueva)
            Connection conn2 = hikariDs.getConnection();
            Connection raw2 = conn2.unwrap(org.sqlite.SQLiteConnection.class);
            assertNotSame(raw1, raw2, "La conexion fisica debe haber sido renovada en el pool");

            try (Statement st = conn2.createStatement()) {
                try (ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
                    assertTrue(rs.next());
                    assertEquals("wal", rs.getString(1).toLowerCase());
                }
                try (ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
                try (ResultSet rs = st.executeQuery("PRAGMA busy_timeout")) {
                    assertTrue(rs.next());
                    assertEquals(5000, rs.getInt(1));
                }
                try (ResultSet rs = st.executeQuery("PRAGMA synchronous")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
                assertThrows(Exception.class, () ->
                        st.execute("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) " +
                                "VALUES('non_existent_sess', 1, 'Huerfano', 1000, '/img.png', 0)"));
            } finally {
                conn2.close();
            }
        });
    }


    /**
     * P1 Persistence:
     * Archivo temporal: abrir contexto A, ensure sesión, cambiar precio/foto/sold, alta #9 y otra sesión vacía;
     * guardar estado/pending/temp_image/snapshot por primitivas. Cerrar contexto y pool por completo, abrir B en mismo archivo:
     * igualdad de datos, contador y temporales, vacío permanece vacío tras ensure.
     */
    @Test
    void testP1_restartContextPreservesDataAndEmptyRemainsEmpty() {
        String sessActive = "session_active_p1";
        String sessEmpty = "session_empty_p1";

        // Contexto A
        createContextRunner().run(contextA -> {
            Catalog catalog = contextA.getBean(Catalog.class);
            catalog.ensure(sessActive);
            catalog.ensure(sessEmpty);

            // Vaciar sessEmpty
            catalog.restore(sessEmpty, List.of());

            // Mutaciones en sessActive
            catalog.apply(sessActive, new IntentParser.Intent("PRICE", 1, null, 25000, null));
            catalog.apply(sessActive, new IntentParser.Intent("IMAGE", 2, null, null, "/assets/new_jarron.webp"));
            catalog.apply(sessActive, new IntentParser.Intent("SOLD", 3, null, null, null));
            catalog.apply(sessActive, new IntentParser.Intent("CREATE", null, "Pieza Nueva Nueve", 40000, "/assets/9.webp"));

            // Temporales de conversacion y snapshot
            catalog.conversation(sessActive, "AWAITING_DETAILS", "{\"draft\":9}", "/uploads/draft.png");
            catalog.snapshot(sessActive, "[{\"piece\":1}]");

            assertEquals(10, catalog.session(sessActive).nextNumber());
            assertEquals(9, catalog.session(sessEmpty).nextNumber());
            assertEquals(0, catalog.all(sessEmpty).size());
            assertEquals(9, catalog.all(sessActive).size());
        });

        // Contexto B (nuevo proceso/pool sobre el mismo archivo en disco)
        createContextRunner().run(contextB -> {
            Catalog catalog = contextB.getBean(Catalog.class);

            // ensure sobre sessEmpty no debe resembrar
            catalog.ensure(sessEmpty);
            assertEquals(0, catalog.all(sessEmpty).size());
            assertEquals(9, catalog.session(sessEmpty).nextNumber());

            // ensure sobre sessActive
            catalog.ensure(sessActive);
            Catalog.Session s = catalog.session(sessActive);
            assertEquals("AWAITING_DETAILS", s.state());
            assertEquals("{\"draft\":9}", s.pending());
            assertEquals("/uploads/draft.png", s.tempImage());
            assertEquals("[{\"piece\":1}]", s.snapshot());
            assertEquals(10, s.nextNumber());

            List<Catalog.Product> prods = catalog.all(sessActive);
            assertEquals(9, prods.size());

            Catalog.Product p1 = prods.stream().filter(p -> p.pieceNumber() == 1).findFirst().orElseThrow();
            assertEquals(25000, p1.price());

            Catalog.Product p2 = prods.stream().filter(p -> p.pieceNumber() == 2).findFirst().orElseThrow();
            assertEquals("/assets/new_jarron.webp", p2.imageUrl());

            Catalog.Product p3 = prods.stream().filter(p -> p.pieceNumber() == 3).findFirst().orElseThrow();
            assertTrue(p3.sold());

            Catalog.Product p9 = prods.stream().filter(p -> p.pieceNumber() == 9).findFirst().orElseThrow();
            assertEquals("Pieza Nueva Nueve", p9.title());
            assertEquals(40000, p9.price());
        });
    }

    /**
     * P2 Persistence (Prueba a nivel Spring Boot ApplicationContext):
     * Verifica la preservación del estado entre dos ciclos sucesivos de ApplicationContextRunner
     * sobre el mismo archivo físico de base de datos SQLite (cerrando completamente el DataSource y pool entre ciclos).
     * El reinicio real de procesos de sistema operativo independientes (PIDs distintos sobre JAR)
     * se ejecuta y documenta en la fase de verificación y smoke packaging.
     */
    @Test
    void testP2_springBootContextRestartPreservesCatalog() throws Exception {
        createContextRunner().run(c1 -> {
            Catalog catalog = c1.getBean(Catalog.class);
            String testSess = "session_test_p2_real";
            catalog.ensure(testSess);
            catalog.apply(testSess, new IntentParser.Intent("CREATE", null, "Pieza P2", 12345, "/p2.webp"));
            assertEquals(10, catalog.session(testSess).nextNumber());
        });

        // Segundo ciclo de contexto sobre el mismo archivo en disco
        createContextRunner().run(c2 -> {
            Catalog catalog = c2.getBean(Catalog.class);
            String testSess = "session_test_p2_real";
            // ensure debe ser idempotente y no resembrar
            catalog.ensure(testSess);
            assertEquals(10, catalog.session(testSess).nextNumber());
            assertEquals(9, catalog.all(testSess).size());
            assertTrue(catalog.all(testSess).stream().anyMatch(p -> p.pieceNumber() == 9 && p.title().equals("Pieza P2")));

            // DEFAULT no es accesible como sesion ordinaria
            assertThrows(IllegalArgumentException.class, () -> catalog.ensure("DEFAULT"));
            assertThrows(IllegalArgumentException.class, () -> catalog.all("DEFAULT"));
        });
    }
}
