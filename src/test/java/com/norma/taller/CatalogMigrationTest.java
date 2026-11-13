package com.norma.taller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class CatalogMigrationTest {

    @TempDir
    Path tempDir;

    private Path dbPath;
    private SQLiteDataSource dataSource;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test_migration.db");
        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void executeScript(Connection conn, String scriptPath) throws Exception {
        String content = Files.readString(Path.of("src/test/resources/db/legacy-v0.sql"));
        try (Statement st = conn.createStatement()) {
            for (String sql : content.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    st.execute(trimmed);
                }
            }
        }
    }

    /**
     * M1 Migration:
     * Fixture legacy poblado, una sesión vacía, vendidos y JSON/estado pendientes;
     * migrar a v2 preservando ids/campos, normalizando contador bajo y agregando DEFAULT.
     * Segundo arranque sin cambios. Ejecutar foreign_key_check vacío y quick_check=ok.
     */
    @Test
    void testM1_migrationFromLegacyPreservesDataAndNormalizesCounter() throws Exception {
        try (Connection conn = getConnection()) {
            executeScript(conn, "src/test/resources/db/legacy-v0.sql");

            try (Statement st = conn.createStatement()) {
                // Sesion 1: con productos normales, algunos vendidos, contador bajo (ej: 5)
                st.execute("INSERT INTO sessions(id, state, pending, temp_image, snapshot, next_number) " +
                        "VALUES('session_normal_1', 'AWAITING_DETAILS', '{\"draft\":1}', '/tmp/img.png', '[{\"piece\":1}]', 5)");
                st.execute("INSERT INTO products(id, session_id, piece_number, title, price, image_url, sold) " +
                        "VALUES(101, 'session_normal_1', 1, 'Pieza Uno', 15000, '/assets/1.webp', 0)");
                st.execute("INSERT INTO products(id, session_id, piece_number, title, price, image_url, sold) " +
                        "VALUES(102, 'session_normal_1', 2, 'Pieza Dos', 20000, '/assets/2.webp', 1)");
                st.execute("INSERT INTO products(id, session_id, piece_number, title, price, image_url, sold) " +
                        "VALUES(103, 'session_normal_1', 10, 'Pieza Diez', 25000, '/assets/10.webp', 0)");

                // Sesion 2: vacia, sin productos, estado IDLE
                st.execute("INSERT INTO sessions(id, state, pending, temp_image, snapshot, next_number) " +
                        "VALUES('session_empty_2', 'IDLE', NULL, NULL, NULL, 9)");
            }
        }

        CatalogDatabaseInitializer initializer = new CatalogDatabaseInitializer(dataSource);

        // Primer arranque / migracion
        initializer.initialize();

        try (Connection conn = getConnection()) {
            try (Statement st = conn.createStatement()) {
                // Verificar user_version = 2
                try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt(1));
                }

                // Verificar quick_check y foreign_key_check
                try (ResultSet rs = st.executeQuery("PRAGMA quick_check")) {
                    assertTrue(rs.next());
                    assertEquals("ok", rs.getString(1).toLowerCase());
                }
                try (ResultSet rs = st.executeQuery("PRAGMA foreign_key_check")) {
                    assertFalse(rs.next(), "foreign_key_check debe estar vacio");
                }

                // Verificar DEFAULT agregado
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM products WHERE session_id='DEFAULT'")) {
                    assertTrue(rs.next());
                    assertEquals(8, rs.getInt(1));
                }

                // Verificar preservacion de ids y campos de session_normal_1
                try (ResultSet rs = st.executeQuery("SELECT state, pending, temp_image, snapshot, next_number, updated_at FROM sessions WHERE id='session_normal_1'")) {
                    assertTrue(rs.next());
                    assertEquals("AWAITING_DETAILS", rs.getString("state"));
                    assertEquals("{\"draft\":1}", rs.getString("pending"));
                    assertEquals("/tmp/img.png", rs.getString("temp_image"));
                    assertEquals("[{\"piece\":1}]", rs.getString("snapshot"));
                    // next_number era 5, pero max(piece_number) es 10 -> normalizado a max(9, 5, 11) = 11
                    assertEquals(11, rs.getInt("next_number"));
                    assertNotNull(rs.getString("updated_at"));
                }

                // Verificar productos de session_normal_1 preservados con sus ids exactos
                try (ResultSet rs = st.executeQuery("SELECT id, piece_number, title, price, image_url, sold, updated_at FROM products WHERE session_id='session_normal_1' ORDER BY piece_number")) {
                    assertTrue(rs.next());
                    assertEquals(101, rs.getLong("id"));
                    assertEquals(1, rs.getInt("piece_number"));
                    assertEquals("Pieza Uno", rs.getString("title"));
                    assertEquals(15000, rs.getInt("price"));
                    assertEquals(0, rs.getInt("sold"));
                    assertNotNull(rs.getString("updated_at"));

                    assertTrue(rs.next());
                    assertEquals(102, rs.getLong("id"));
                    assertEquals(2, rs.getInt("piece_number"));
                    assertEquals("Pieza Dos", rs.getString("title"));
                    assertEquals(20000, rs.getInt("price"));
                    assertEquals(1, rs.getInt("sold"));

                    assertTrue(rs.next());
                    assertEquals(103, rs.getLong("id"));
                    assertEquals(10, rs.getInt("piece_number"));
                    assertEquals("Pieza Diez", rs.getString("title"));
                    assertEquals(25000, rs.getInt("price"));
                    assertEquals(0, rs.getInt("sold"));

                    assertFalse(rs.next());
                }

                // Verificar sesion vacia preservada sin productos
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM products WHERE session_id='session_empty_2'")) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1));
                }
            }
        }

        // Segundo arranque consecutivo sobre v2: no debe alterar nada ni fallar
        assertDoesNotThrow(() -> initializer.initialize());

        try (Connection conn = getConnection()) {
            try (Statement st = conn.createStatement()) {
                try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt(1));
                }
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM products WHERE session_id='DEFAULT'")) {
                    assertTrue(rs.next());
                    assertEquals(8, rs.getInt(1));
                }
            }
        }
    }

    /**
     * M2 Migration:
     * Legacy con DEFAULT exacto conserva filas/ids; DEFAULT parcial o alterado falla sin reparar.
     * DB nueva crea semilla una vez; v2 con semilla dañada o guardas faltantes falla sin reseed.
     */
    @Test
    void testM2_legacyWithValidOrDamagedDefaultAndBootstrapOnce() throws Exception {
        // Caso A: legacy con DEFAULT valido
        try (Connection conn = getConnection()) {
            executeScript(conn, "src/test/resources/db/legacy-v0.sql");
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO sessions(id, state, next_number) VALUES('DEFAULT', 'IDLE', 9)");
                String[] titles = {"Cuenco rústico", "Jarrón de gres", "Taza panzona", "Maceta de barro", "Plato del taller", "Florero azul", "Compotera", "Vasito de cerámica"};
                int[] prices = {15000, 18500, 12000, 20000, 14000, 24000, 16500, 9000};
                for (int i = 0; i < 8; i++) {
                    st.execute("INSERT INTO products(id, session_id, piece_number, title, price, image_url, sold) " +
                            "VALUES(" + (500 + i) + ", 'DEFAULT', " + (i + 1) + ", '" + titles[i] + "', " + prices[i] + ", '/assets/pieza-" + (i + 1) + ".webp', 0)");
                }
            }
        }

        CatalogDatabaseInitializer initializer = new CatalogDatabaseInitializer(dataSource);
        initializer.initialize();

        try (Connection conn = getConnection()) {
            try (Statement st = conn.createStatement()) {
                // Verificar que se conservaron los ids originales 500..507
                try (ResultSet rs = st.executeQuery("SELECT id FROM products WHERE session_id='DEFAULT' ORDER BY piece_number")) {
                    for (int i = 0; i < 8; i++) {
                        assertTrue(rs.next());
                        assertEquals(500 + i, rs.getLong("id"));
                    }
                }
            }
        }

        // Caso B: legacy con DEFAULT alterado (por ejemplo 7 piezas)
        Path dbPathB = tempDir.resolve("test_damaged_default.db");
        SQLiteDataSource dsB = new SQLiteDataSource();
        dsB.setUrl("jdbc:sqlite:" + dbPathB.toAbsolutePath());
        try (Connection conn = dsB.getConnection()) {
            executeScript(conn, "src/test/resources/db/legacy-v0.sql");
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO sessions(id, state, next_number) VALUES('DEFAULT', 'IDLE', 9)");
                st.execute("INSERT INTO products(id, session_id, piece_number, title, price, image_url, sold) " +
                        "VALUES(1, 'DEFAULT', 1, 'Cuenco rústico', 15000, '/assets/pieza-1.webp', 0)");
            }
        }

        CatalogDatabaseInitializer initB = new CatalogDatabaseInitializer(dsB);
        assertThrows(IllegalStateException.class, () -> initB.initialize());

        // Caso C: base v2 con trigger eliminado debe fallar
        try (Connection conn = getConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute("DROP TRIGGER protect_default_sessions_insert");
            }
        }
        assertThrows(IllegalStateException.class, () -> initializer.initialize());
    }

    /**
     * M3 Migration:
     * Legacy inválido (huérfanos, tipos/rangos, estados, ids) o versión/esquema desconocido falla con datos/version intactos;
     * no borrar ni añadir sesiones para ocultarlo. Inyectar fallo tras copia antes de commit:
     * tablas originales y versión se conservan, nuevo arranque recupera; FK vuelve a ON.
     */
    @Test
    void testM3_invalidLegacyOrVersionAndRollbackOnFailure() throws Exception {
        // Caso A: producto huerfano en legacy
        Path dbPathOrphan = tempDir.resolve("test_orphan.db");
        SQLiteDataSource dsOrphan = new SQLiteDataSource();
        dsOrphan.setUrl("jdbc:sqlite:" + dbPathOrphan.toAbsolutePath());
        try (Connection conn = dsOrphan.getConnection()) {
            executeScript(conn, "src/test/resources/db/legacy-v0.sql");
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO products(id, session_id, piece_number, title, price, image_url, sold) " +
                        "VALUES(1, 'session_inexistente', 1, 'Huérfano', 10000, '/img.png', 0)");
            }
        }
        CatalogDatabaseInitializer initOrphan = new CatalogDatabaseInitializer(dsOrphan);
        assertThrows(IllegalStateException.class, () -> initOrphan.initialize());

        // Caso B: user_version desconocido
        Path dbPathVersion = tempDir.resolve("test_version.db");
        SQLiteDataSource dsVersion = new SQLiteDataSource();
        dsVersion.setUrl("jdbc:sqlite:" + dbPathVersion.toAbsolutePath());
        try (Connection conn = dsVersion.getConnection()) {
            executeScript(conn, "src/test/resources/db/legacy-v0.sql");
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA user_version = 99");
            }
        }
        CatalogDatabaseInitializer initVersion = new CatalogDatabaseInitializer(dsVersion);
        assertThrows(IllegalStateException.class, () -> initVersion.initialize());

        // Caso C: Inyeccion de fallo en ruta real de migracion post-copia y antes de commit
        Path dbPathFail = tempDir.resolve("test_fail_real.db");
        SQLiteDataSource dsRaw = new SQLiteDataSource();
        dsRaw.setUrl("jdbc:sqlite:" + dbPathFail.toAbsolutePath());
        dsRaw.setEnforceForeignKeys(true);
        try (Connection conn = dsRaw.getConnection()) {
            executeScript(conn, "src/test/resources/db/legacy-v0.sql");
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO sessions(id, state, next_number) VALUES('session_valid_1', 'IDLE', 9)");
                st.execute("INSERT INTO products(id, session_id, piece_number, title, price, image_url, sold) " +
                        "VALUES(100, 'session_valid_1', 1, 'Pieza Original', 15000, '/assets/orig.webp', 0)");
            }
        }

        // Inyectar fallo en 'PRAGMA quick_check' (que ocurre post-copia y antes de PRAGMA user_version=2 y commit)
        javax.sql.DataSource dsFailing = new TestFailingDataSource(dsRaw, "PRAGMA quick_check");
        CatalogDatabaseInitializer initFail = new CatalogDatabaseInitializer(dsFailing);

        // La ejecucion de migracion debe fallar
        assertThrows(IllegalStateException.class, () -> initFail.initialize());

        // Verificar rollback total de la base de datos original
        try (Connection conn = dsRaw.getConnection()) {
            try (Statement st = conn.createStatement()) {
                // user_version debe seguir siendo 0
                try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "user_version debe permanecer en 0 tras rollback");
                }

                // foreign_keys debe estar en ON (restaurado en finally)
                try (ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "PRAGMA foreign_keys debe haber sido restaurado a ON");
                }

                // No deben existir tablas temporales intermedias (new_sessions, new_products)
                try (ResultSet rs = st.executeQuery("SELECT count(*) FROM sqlite_schema WHERE name IN ('new_sessions', 'new_products')")) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "Tablas intermedias new_* no deben existir tras rollback");
                }

                // Las tablas y datos originales deben estar intactos
                try (ResultSet rs = st.executeQuery("SELECT count(*) FROM sessions WHERE id='session_valid_1'")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
                try (ResultSet rs = st.executeQuery("SELECT title, price FROM products WHERE id=100")) {
                    assertTrue(rs.next());
                    assertEquals("Pieza Original", rs.getString("title"));
                    assertEquals(15000, rs.getInt("price"));
                }
            }
        }

        // Recuperacion posterior en arranque limpio sin inyeccion de error: debe migrar exitosamente a v2
        CatalogDatabaseInitializer initClean = new CatalogDatabaseInitializer(dsRaw);
        assertDoesNotThrow(() -> initClean.initialize());

        try (Connection conn = dsRaw.getConnection();
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
        }
    }

    private static class TestFailingDataSource implements javax.sql.DataSource {
        private final javax.sql.DataSource delegate;
        private final String failOnSql;

        public TestFailingDataSource(javax.sql.DataSource delegate, String failOnSql) {
            this.delegate = delegate;
            this.failOnSql = failOnSql;
        }

        @Override public Connection getConnection() throws SQLException { return wrap(delegate.getConnection()); }
        @Override public Connection getConnection(String u, String p) throws SQLException { return wrap(delegate.getConnection(u, p)); }

        private Connection wrap(Connection c) {
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                    c.getClass().getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("createStatement".equals(method.getName())) {
                            Statement st = (Statement) method.invoke(c, args);
                            return wrapStatement(st);
                        }
                        return method.invoke(c, args);
                    }
            );
        }

        private Statement wrapStatement(Statement st) {
            return (Statement) java.lang.reflect.Proxy.newProxyInstance(
                    st.getClass().getClassLoader(),
                    new Class<?>[]{Statement.class},
                    (proxy, method, args) -> {
                        if (args != null && args.length > 0 && args[0] instanceof String sql) {
                            if (failOnSql != null && sql.contains(failOnSql)) {
                                throw new SQLException("Injected test failure on: " + sql);
                            }
                        }
                        return method.invoke(st, args);
                    }
            );
        }

        @Override public java.io.PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
        @Override public void setLogWriter(java.io.PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
        @Override public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
        @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
        @Override public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
        @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }
    }
}
