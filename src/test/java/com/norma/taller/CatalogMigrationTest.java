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
import java.util.List;

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

        // Caso C: base v2 con trigger eliminado debe fallar sin reparar
        try (Connection conn = getConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute("DROP TRIGGER protect_default_sessions_insert");
            }
        }
        assertThrows(IllegalStateException.class, () -> initializer.initialize());

        // Caso D: base v2 con trigger reemplazado por otro del mismo nombre (homónimo falso)
        Path dbPathHom = tempDir.resolve("test_fake_trigger.db");
        SQLiteDataSource dsHom = new SQLiteDataSource();
        dsHom.setUrl("jdbc:sqlite:" + dbPathHom.toAbsolutePath());
        CatalogDatabaseInitializer initHom = new CatalogDatabaseInitializer(dsHom);
        initHom.initialize(); // bootstrap v2
        try (Connection conn = dsHom.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("DROP TRIGGER protect_default_products_update");
            // Trigger inoperante homonimo con WHEN 0
            st.execute("CREATE TRIGGER protect_default_products_update BEFORE UPDATE ON products WHEN 0 BEGIN SELECT RAISE(ABORT, 'fake'); END;");
        }
        assertThrows(IllegalStateException.class, () -> initHom.initialize(), "v2 con trigger homonimo falso inoperante debe fallar");

        // Caso E: base v2 con semilla alterada (precio de DEFAULT #1 modificado)
        Path dbPathAltSeed = tempDir.resolve("test_alt_seed.db");
        SQLiteDataSource dsAltSeed = new SQLiteDataSource();
        dsAltSeed.setUrl("jdbc:sqlite:" + dbPathAltSeed.toAbsolutePath());
        CatalogDatabaseInitializer initAlt = new CatalogDatabaseInitializer(dsAltSeed);
        initAlt.initialize();
        try (Connection conn = dsAltSeed.getConnection();
             Statement st = conn.createStatement()) {
            // Eliminar trigger de proteccion temporalmente solo para simular corrupcion de semilla
            st.execute("DROP TRIGGER protect_default_products_update");
            st.execute("UPDATE products SET price=99999 WHERE session_id='DEFAULT' AND piece_number=1");
            // Reinstalar exactamente el trigger canonico original (aislando la semilla alterada sin alterar el trigger)
            st.execute("CREATE TRIGGER protect_default_products_update BEFORE UPDATE ON products WHEN OLD.session_id = 'DEFAULT' OR NEW.session_id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT products are immutable and cannot be modified'); END");
        }
        assertThrows(IllegalStateException.class, () -> initAlt.initialize(), "v2 con semilla DEFAULT alterada debe fallar sin reparar silenciosamente");

        // Caso F: C1 - Trigger con condición 'default' en minúscula (homónimo inoperante para BINARY)
        Path dbPathLit = tempDir.resolve("test_trigger_literal.db");
        SQLiteDataSource dsLit = new SQLiteDataSource();
        dsLit.setUrl("jdbc:sqlite:" + dbPathLit.toAbsolutePath());
        CatalogDatabaseInitializer initLit = new CatalogDatabaseInitializer(dsLit);
        initLit.initialize();
        try (Connection conn = dsLit.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("DROP TRIGGER protect_default_products_update");
            // Instalar trigger homonimo pero con 'default' en minuscula
            st.execute("CREATE TRIGGER protect_default_products_update BEFORE UPDATE ON products WHEN OLD.session_id = 'default' OR NEW.session_id = 'default' BEGIN SELECT RAISE(ABORT, 'DEFAULT products are immutable and cannot be modified'); END");
        }
        assertThrows(IllegalStateException.class, () -> initLit.initialize(), "v2 con trigger con literal 'default' en minuscula debe fallar");

        // Caso G: C2 - Esquema sin CHECK constraints (aislado, manteniendo columnas, PK y FK)
        Path dbPathNoCheck = tempDir.resolve("test_no_check.db");
        SQLiteDataSource dsNoCheck = new SQLiteDataSource();
        dsNoCheck.setUrl("jdbc:sqlite:" + dbPathNoCheck.toAbsolutePath());
        try (Connection conn = dsNoCheck.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("PRAGMA user_version = 2");
            st.execute("CREATE TABLE sessions (\n" +
                    "    id TEXT NOT NULL PRIMARY KEY,\n" +
                    "    state TEXT NOT NULL DEFAULT 'IDLE',\n" +
                    "    pending TEXT,\n" +
                    "    temp_image TEXT,\n" +
                    "    snapshot TEXT,\n" +
                    "    next_number INTEGER NOT NULL DEFAULT 9,\n" +
                    "    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP\n" +
                    ")");
            st.execute("CREATE TABLE products (\n" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "    session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,\n" +
                    "    piece_number INTEGER NOT NULL,\n" +
                    "    title TEXT NOT NULL,\n" +
                    "    price INTEGER NOT NULL,\n" +
                    "    image_url TEXT NOT NULL,\n" +
                    "    sold INTEGER NOT NULL DEFAULT 0,\n" +
                    "    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                    "    UNIQUE(session_id, piece_number)\n" +
                    ")");
            // Insertar semilla canónica y los 6 triggers canónicos
            st.execute("INSERT INTO sessions(id, state, next_number) VALUES('DEFAULT', 'IDLE', 9)");
            for (int i = 1; i <= 8; i++) {
                st.execute("INSERT INTO products(id, session_id, piece_number, title, price, image_url, sold) VALUES(" + i + ", 'DEFAULT', " + i + ", 'P" + i + "', 10000, '/img.webp', 0)");
            }
            // Triggers
            st.execute("CREATE TRIGGER protect_default_sessions_insert BEFORE INSERT ON sessions WHEN NEW.id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT session is immutable and cannot be inserted'); END");
            st.execute("CREATE TRIGGER protect_default_sessions_update BEFORE UPDATE ON sessions WHEN OLD.id = 'DEFAULT' OR NEW.id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT session is immutable and cannot be modified'); END");
            st.execute("CREATE TRIGGER protect_default_sessions_delete BEFORE DELETE ON sessions WHEN OLD.id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT session is immutable and cannot be deleted'); END");
            st.execute("CREATE TRIGGER protect_default_products_insert BEFORE INSERT ON products WHEN NEW.session_id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT products are immutable and cannot be inserted'); END");
            st.execute("CREATE TRIGGER protect_default_products_update BEFORE UPDATE ON products WHEN OLD.session_id = 'DEFAULT' OR NEW.session_id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT products are immutable and cannot be modified'); END");
            st.execute("CREATE TRIGGER protect_default_products_delete BEFORE DELETE ON products WHEN OLD.session_id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT products are immutable and cannot be deleted'); END");
        }
        CatalogDatabaseInitializer initNoCheck = new CatalogDatabaseInitializer(dsNoCheck);
        assertThrows(IllegalStateException.class, () -> initNoCheck.initialize(), "v2 sin CHECK constraints contractuales debe ser rechazado en arranque");

        // Caso H: C2 - products con UNIQUE parcial (WHERE piece_number IS NOT NULL) debe fallar
        Path dbPathPartIdx = tempDir.resolve("test_partial_unique.db");
        SQLiteDataSource dsPartIdx = new SQLiteDataSource();
        dsPartIdx.setUrl("jdbc:sqlite:" + dbPathPartIdx.toAbsolutePath());
        CatalogDatabaseInitializer initPart = new CatalogDatabaseInitializer(dsPartIdx);
        initPart.initialize();
        try (Connection conn = dsPartIdx.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("DROP TABLE products");
            st.execute("CREATE TABLE products (\n" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "    session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,\n" +
                    "    piece_number INTEGER NOT NULL CHECK(typeof(piece_number)='integer' AND piece_number BETWEEN 1 AND 2147483646),\n" +
                    "    title TEXT NOT NULL CHECK(length(trim(title))>0),\n" +
                    "    price INTEGER NOT NULL CHECK(typeof(price)='integer' AND price BETWEEN 1 AND 2147483647),\n" +
                    "    image_url TEXT NOT NULL CHECK(length(trim(image_url))>0),\n" +
                    "    sold INTEGER NOT NULL DEFAULT 0 CHECK(typeof(sold)='integer' AND sold IN (0,1)),\n" +
                    "    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP\n" +
                    ")");
            st.execute("CREATE UNIQUE INDEX idx_products_partial ON products(session_id, piece_number) WHERE session_id IS NOT NULL");
            // Reinstalar triggers
            st.execute("CREATE TRIGGER protect_default_products_insert BEFORE INSERT ON products WHEN NEW.session_id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT products are immutable and cannot be inserted'); END");
            st.execute("CREATE TRIGGER protect_default_products_update BEFORE UPDATE ON products WHEN OLD.session_id = 'DEFAULT' OR NEW.session_id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT products are immutable and cannot be modified'); END");
            st.execute("CREATE TRIGGER protect_default_products_delete BEFORE DELETE ON products WHEN OLD.session_id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT products are immutable and cannot be deleted'); END");
        }
        assertThrows(IllegalStateException.class, () -> initPart.initialize(), "v2 con UNIQUE index parcial debe ser rechazado");

        // Caso F: base v2 sin foreign keys ni check constraints (estructura DDL alterada)
        Path dbPathNoFK = tempDir.resolve("test_no_fk.db");
        SQLiteDataSource dsNoFK = new SQLiteDataSource();
        dsNoFK.setUrl("jdbc:sqlite:" + dbPathNoFK.toAbsolutePath());
        try (Connection conn = dsNoFK.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("PRAGMA user_version = 2");
            st.execute("CREATE TABLE sessions (id TEXT, state TEXT, next_number INTEGER)");
            st.execute("CREATE TABLE products (id INTEGER, session_id TEXT, piece_number INTEGER, title TEXT, price INTEGER, image_url TEXT, sold INTEGER)");
        }
        CatalogDatabaseInitializer initNoFK = new CatalogDatabaseInitializer(dsNoFK);
        assertThrows(IllegalStateException.class, () -> initNoFK.initialize(), "v2 sin FK, PK ni CHECK constraints debe fallar");

        // Caso I: C4 Regression - Reinicializaciones repetidas con sesiones validas (valid_sess_check_1/2, valid_sess_prod_1/2) en legacy y v2
        Path dbPathC4 = tempDir.resolve("test_c4_restart_regression.db");
        SQLiteDataSource dsC4 = new SQLiteDataSource();
        dsC4.setUrl("jdbc:sqlite:" + dbPathC4.toAbsolutePath());
        CatalogDatabaseInitializer initC4 = new CatalogDatabaseInitializer(dsC4);
        assertDoesNotThrow(() -> initC4.initialize(), "Bootstrap v2 inicial debe ser exitoso");

        List<String> testSessions = List.of("valid_sess_check_1", "valid_sess_check_2", "valid_sess_prod_1", "valid_sess_prod_2");
        try (Connection conn = dsC4.getConnection();
             Statement st = conn.createStatement()) {
            for (String sId : testSessions) {
                st.execute("INSERT INTO sessions(id, state, next_number) VALUES('" + sId + "', 'IDLE', 9)");
                st.execute("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES('" + sId + "', 1, 'P1', 15000, '/assets/p1.webp', 0)");
            }
        }

        // Capturar snapshot logico antes de reinicializar
        String snapBeforeC4 = captureDatabaseSnapshot(dsC4);

        // Reinicializacion repetida debe pasar sin colision ni side-effect y preservar snapshot logico byte-exacto
        assertDoesNotThrow(() -> initC4.initialize(), "Reinicializacion con sesiones valid_sess_* existentes debe ser exitosa (sin colision C4)");
        String snapAfterC4 = captureDatabaseSnapshot(dsC4);
        assertEquals(snapBeforeC4, snapAfterC4, "Reinicializacion repetida sobre esquema v2 con datos validos debe preservar el estado logico exacto");

        // Regresion C4 sobre migracion legacy v0 con esas mismas 4 sesiones
        Path dbPathC4Legacy = tempDir.resolve("test_c4_legacy_regression.db");
        SQLiteDataSource dsC4Legacy = new SQLiteDataSource();
        dsC4Legacy.setUrl("jdbc:sqlite:" + dbPathC4Legacy.toAbsolutePath());
        try (Connection conn = dsC4Legacy.getConnection()) {
            executeScript(conn, "src/test/resources/db/legacy-v0.sql");
            try (Statement st = conn.createStatement()) {
                for (String sId : testSessions) {
                    st.execute("INSERT INTO sessions(id, state, next_number) VALUES('" + sId + "', 'IDLE', 9)");
                    st.execute("INSERT INTO products(id, session_id, piece_number, title, price, image_url, sold) VALUES(NULL, '" + sId + "', 1, 'P1', 15000, '/assets/p1.webp', 0)");
                }
            }
        }
        CatalogDatabaseInitializer initC4Legacy = new CatalogDatabaseInitializer(dsC4Legacy);
        assertDoesNotThrow(() -> initC4Legacy.initialize(), "Migracion v0->v2 con sesiones valid_sess_* debe ser exitosa");
        String snapLegacyAfterMigrate = captureDatabaseSnapshot(dsC4Legacy);
        // Segundo arranque sobre base migrada
        assertDoesNotThrow(() -> initC4Legacy.initialize(), "Segundo arranque tras migracion debe ser exitoso");
        String snapLegacyAfterSecondInit = captureDatabaseSnapshot(dsC4Legacy);
        assertEquals(snapLegacyAfterMigrate, snapLegacyAfterSecondInit, "Segundo arranque sobre base migrada debe ser idempotente y byte-equivalente en snapshot logico");
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
        TestFailingDataSource dsFailing = new TestFailingDataSource(dsRaw, "PRAGMA quick_check");
        CatalogDatabaseInitializer initFail = new CatalogDatabaseInitializer(dsFailing);

        // La ejecucion de migracion debe fallar por el fallo inyectado en quick_check
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> initFail.initialize());
        assertTrue(dsFailing.isFailReached(), "La inyeccion en PRAGMA quick_check debio ser alcanzada efectivamente");
        assertTrue(ex.getMessage().contains("quick_check") || (ex.getCause() != null && ex.getCause().getMessage().contains("quick_check")),
                "La excepcion debe deberse al fallo inyectado en quick_check: " + ex.getMessage());

        // Verificar que sobre LA MISMA conexion fisica cuyo migrador fallo, PRAGMA foreign_keys fue restaurado a ON en finally antes de cerrarla
        assertNotNull(dsFailing.getFkOnFailingConnection(), "Debe haberse capturado foreign_keys en la conexion fallida antes de cerrar");
        assertEquals(1, dsFailing.getFkOnFailingConnection(), "PRAGMA foreign_keys debio ser 1 (ON) en la conexion del migrador tras el rollback");

        // Verificar rollback total de la base de datos original desde nueva conexion
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
        // Caso D: Legacy con triggers/indices/vistas extra (F2) debe abortar sin modificar datos ni version
        Path dbPathExtra = tempDir.resolve("test_extra_objects.db");
        SQLiteDataSource dsExtra = new SQLiteDataSource();
        dsExtra.setUrl("jdbc:sqlite:" + dbPathExtra.toAbsolutePath());
        try (Connection conn = dsExtra.getConnection()) {
            executeScript(conn, "src/test/resources/db/legacy-v0.sql");
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO sessions(id, state, next_number) VALUES('session_extra_1', 'IDLE', 9)");
                st.execute("INSERT INTO products(id, session_id, piece_number, title, price, image_url, sold) " +
                        "VALUES(200, 'session_extra_1', 1, 'Pieza Extra', 10000, '/extra.webp', 0)");
                // Trigger dependiente no estandar
                st.execute("CREATE TRIGGER custom_safeguard BEFORE DELETE ON products BEGIN SELECT RAISE(ABORT, 'keep products'); END;");
            }
        }
        CatalogDatabaseInitializer initExtra = new CatalogDatabaseInitializer(dsExtra);
        assertThrows(IllegalStateException.class, () -> initExtra.initialize(), "Legacy con trigger extra debe ser rechazado sin tocar datos");
        try (Connection conn = dsExtra.getConnection();
             Statement st = conn.createStatement()) {
            // Verificar user_version = 0 y trigger intacto
            try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM sqlite_schema WHERE type='trigger' AND name='custom_safeguard'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Trigger custom_safeguard debe permanecer intacto");
            }
        }

        // Caso E: Legacy con next_number REAL (ej: 1.5) o negativo/texto debe fallar (F4)
        Path dbPathReal = tempDir.resolve("test_real_next_number.db");
        SQLiteDataSource dsReal = new SQLiteDataSource();
        dsReal.setUrl("jdbc:sqlite:" + dbPathReal.toAbsolutePath());
        try (Connection conn = dsReal.getConnection()) {
            executeScript(conn, "src/test/resources/db/legacy-v0.sql");
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO sessions(id, state, next_number) VALUES('session_real_1', 'IDLE', 1.5)");
                st.execute("INSERT INTO products(id, session_id, piece_number, title, price, image_url, sold) " +
                        "VALUES(300, 'session_real_1', 1, 'Pieza Real', 10000, '/real.webp', 0)");
            }
        }
        CatalogDatabaseInitializer initReal = new CatalogDatabaseInitializer(dsReal);
        assertThrows(IllegalStateException.class, () -> initReal.initialize(), "next_number=1.5 REAL debe abortar la migracion");
        try (Connection conn = dsReal.getConnection();
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }

        // Caso F: Legacy con sequence historica superior a ids vivos (F3), casos sin productos, con DEFAULT y segundo arranque
        Path dbPathSeq = tempDir.resolve("test_seq_high.db");
        SQLiteDataSource dsSeq = new SQLiteDataSource();
        dsSeq.setUrl("jdbc:sqlite:" + dbPathSeq.toAbsolutePath());
        try (Connection conn = dsSeq.getConnection()) {
            executeScript(conn, "src/test/resources/db/legacy-v0.sql");
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO sessions(id, state, next_number) VALUES('session_seq_1', 'IDLE', 9)");
                st.execute("INSERT INTO products(id, session_id, piece_number, title, price, image_url, sold) " +
                        "VALUES(1000, 'session_seq_1', 1, 'Pieza 1000', 10000, '/1000.webp', 0)");
                // Borrar el producto 1000 para que sqlite_sequence mantenga 1000 pero no haya id 1000 vivo
                st.execute("DELETE FROM products WHERE id=1000");
                st.execute("INSERT INTO products(id, session_id, piece_number, title, price, image_url, sold) " +
                        "VALUES(5, 'session_seq_1', 1, 'Pieza 5', 10000, '/5.webp', 0)");
            }
        }
        CatalogDatabaseInitializer initSeq = new CatalogDatabaseInitializer(dsSeq);
        assertDoesNotThrow(() -> initSeq.initialize());
        long seqAfterMigrate = 0;
        try (Connection conn = dsSeq.getConnection();
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT seq FROM sqlite_sequence WHERE name='products'")) {
                assertTrue(rs.next());
                seqAfterMigrate = rs.getLong(1);
                assertTrue(seqAfterMigrate >= 1000, "sqlite_sequence.products debe preservar el maximo historico (>=1000)");
            }
        }
        // Segundo arranque sobre v2 no debe alterar sqlite_sequence
        assertDoesNotThrow(() -> initSeq.initialize());
        try (Connection conn = dsSeq.getConnection();
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT seq FROM sqlite_sequence WHERE name='products'")) {
                assertTrue(rs.next());
                assertEquals(seqAfterMigrate, rs.getLong(1), "Segundo arranque no debe alterar el valor historico de sqlite_sequence");
            }
        }

        // Caso G: F2 - Fixtures con indice de usuario extra, vista dependiente, tipos/PK/defaults desconocidos
        Path dbPathExtraObj = tempDir.resolve("test_extra_view_index.db");
        SQLiteDataSource dsExtraObj = new SQLiteDataSource();
        dsExtraObj.setUrl("jdbc:sqlite:" + dbPathExtraObj.toAbsolutePath());
        try (Connection conn = dsExtraObj.getConnection()) {
            executeScript(conn, "src/test/resources/db/legacy-v0.sql");
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE INDEX idx_custom_prod ON products(title)");
            }
        }
        CatalogDatabaseInitializer initIdx = new CatalogDatabaseInitializer(dsExtraObj);
        assertThrows(IllegalStateException.class, () -> initIdx.initialize(), "Legacy con indice de usuario no estandar debe abortar");

        Path dbPathView = tempDir.resolve("test_extra_view.db");
        SQLiteDataSource dsView = new SQLiteDataSource();
        dsView.setUrl("jdbc:sqlite:" + dbPathView.toAbsolutePath());
        try (Connection conn = dsView.getConnection()) {
            executeScript(conn, "src/test/resources/db/legacy-v0.sql");
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE VIEW view_products AS SELECT id, title FROM products");
            }
        }
        CatalogDatabaseInitializer initView = new CatalogDatabaseInitializer(dsView);
        assertThrows(IllegalStateException.class, () -> initView.initialize(), "Legacy con vista dependiente debe abortar");

        // Caso H: F4 - Variantes de next_number invalido: texto, negativo, fuera de rango
        List<String> badCounters = List.of(
                "'NaN'",
                "-5",
                "2147483648" // out of integer range
        );
        for (String badVal : badCounters) {
            Path dbPathBadVal = tempDir.resolve("test_bad_val_" + badVal.replaceAll("[^a-zA-Z0-9]", "_") + ".db");
            SQLiteDataSource dsBadVal = new SQLiteDataSource();
            dsBadVal.setUrl("jdbc:sqlite:" + dbPathBadVal.toAbsolutePath());
            try (Connection conn = dsBadVal.getConnection()) {
                executeScript(conn, "src/test/resources/db/legacy-v0.sql");
                try (Statement st = conn.createStatement()) {
                    st.execute("INSERT INTO sessions(id, state, next_number) VALUES('sess_bad', 'IDLE', " + badVal + ")");
                }
            }
            CatalogDatabaseInitializer initBadVal = new CatalogDatabaseInitializer(dsBadVal);
            assertThrows(IllegalStateException.class, () -> initBadVal.initialize(), "next_number=" + badVal + " debe abortar la migracion");
            try (Connection conn = dsBadVal.getConnection();
                 Statement st = conn.createStatement()) {
                try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "user_version debe ser 0 tras abortar valor invalido: " + badVal);
                }
            }
        }
    }

    private static String captureDatabaseSnapshot(SQLiteDataSource ds) throws SQLException {
        StringBuilder b = new StringBuilder();
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement()) {
            for (String q : List.of(
                    "SELECT type,name,tbl_name,sql FROM sqlite_schema ORDER BY type,name",
                    "SELECT * FROM sessions ORDER BY id",
                    "SELECT * FROM products ORDER BY id",
                    "SELECT * FROM sqlite_sequence ORDER BY name",
                    "PRAGMA user_version")) {
                b.append(q).append("\n");
                try (ResultSet r = s.executeQuery(q)) {
                    int colCount = r.getMetaData().getColumnCount();
                    while (r.next()) {
                        for (int i = 1; i <= colCount; i++) {
                            b.append('|').append(r.getObject(i));
                        }
                        b.append('\n');
                    }
                }
            }
        }
        return b.toString();
    }

    private static class TestFailingDataSource implements javax.sql.DataSource {
        private final javax.sql.DataSource delegate;
        private final String failOnSql;
        private boolean failReached = false;
        private Integer fkOnFailingConnection = null;

        public TestFailingDataSource(javax.sql.DataSource delegate, String failOnSql) {
            this.delegate = delegate;
            this.failOnSql = failOnSql;
        }

        public boolean isFailReached() {
            return failReached;
        }

        public Integer getFkOnFailingConnection() {
            return fkOnFailingConnection;
        }

        @Override public Connection getConnection() throws SQLException {
            Connection realConn = delegate.getConnection();
            return wrap(realConn);
        }
        @Override public Connection getConnection(String u, String p) throws SQLException {
            Connection realConn = delegate.getConnection(u, p);
            return wrap(realConn);
        }

        private Connection wrap(Connection c) {
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                    c.getClass().getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        try {
                            if ("createStatement".equals(method.getName())) {
                                Statement st = (Statement) method.invoke(c, args);
                                return wrapStatement(st);
                            }
                            if ("close".equals(method.getName())) {
                                // Antes de cerrar la conexion que fallo, capturar PRAGMA foreign_keys efectivo
                                if (failReached && fkOnFailingConnection == null) {
                                    try (Statement st = c.createStatement();
                                         ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                                        if (rs.next()) {
                                            fkOnFailingConnection = rs.getInt(1);
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                            return method.invoke(c, args);
                        } catch (java.lang.reflect.InvocationTargetException ite) {
                            Throwable target = ite.getTargetException();
                            if (target instanceof SQLException se) throw se;
                            if (target instanceof RuntimeException re) throw re;
                            if (target instanceof Error err) throw err;
                            throw new RuntimeException(target);
                        }
                    }
            );
        }

        private Statement wrapStatement(Statement st) {
            return (Statement) java.lang.reflect.Proxy.newProxyInstance(
                    st.getClass().getClassLoader(),
                    new Class<?>[]{Statement.class},
                    (proxy, method, args) -> {
                        try {
                            if (args != null && args.length > 0 && args[0] instanceof String sql) {
                                if (failOnSql != null && sql.contains(failOnSql)) {
                                    failReached = true;
                                    throw new SQLException("Injected test failure on: " + sql);
                                }
                            }
                            return method.invoke(st, args);
                        } catch (java.lang.reflect.InvocationTargetException ite) {
                            Throwable target = ite.getTargetException();
                            if (target instanceof SQLException se) throw se;
                            if (target instanceof RuntimeException re) throw re;
                            if (target instanceof Error err) throw err;
                            throw new RuntimeException(target);
                        }
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
