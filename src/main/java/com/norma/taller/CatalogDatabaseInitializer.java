package com.norma.taller;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Component("catalogDatabaseInitializer")
public class CatalogDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(CatalogDatabaseInitializer.class);

    private static final record ExpectedPiece(int number, String title, int price, String imageUrl) {}

    private static final List<ExpectedPiece> EXPECTED_PIECES = List.of(
            new ExpectedPiece(1, "Cuenco rústico", 15000, "/assets/pieza-1.webp"),
            new ExpectedPiece(2, "Jarrón de gres", 18500, "/assets/pieza-2.webp"),
            new ExpectedPiece(3, "Taza panzona", 12000, "/assets/pieza-3.webp"),
            new ExpectedPiece(4, "Maceta de barro", 20000, "/assets/pieza-4.webp"),
            new ExpectedPiece(5, "Plato del taller", 14000, "/assets/pieza-5.webp"),
            new ExpectedPiece(6, "Florero azul", 24000, "/assets/pieza-6.webp"),
            new ExpectedPiece(7, "Compotera", 16500, "/assets/pieza-7.webp"),
            new ExpectedPiece(8, "Vasito de cerámica", 9000, "/assets/pieza-8.webp")
    );

    private final DataSource dataSource;

    public CatalogDatabaseInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initialize() {
        try (Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(true);
                configurePragmas(conn);
                performMigrationOrBootstrap(conn);
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (Exception ignored) {
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Fallo critico en inicializacion/migracion de Catalog: " + e.getMessage(), e);
        }
    }

    private void configurePragmas(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
                if (rs.next()) {
                    String mode = rs.getString(1);
                    if (!"memory".equalsIgnoreCase(mode) && !"wal".equalsIgnoreCase(mode)) {
                        try (ResultSet rsWal = st.executeQuery("PRAGMA journal_mode=WAL")) {
                            if (rsWal.next()) {
                                log.info("PRAGMA journal_mode configurado a {}", rsWal.getString(1));
                            }
                        }
                    }
                }
            }
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("PRAGMA synchronous=NORMAL");
        }
    }

    public void performMigrationOrBootstrap(Connection conn) throws SQLException {
        int userVersion = getUserVersion(conn);
        Set<String> tables = getTableNames(conn);

        if (userVersion == 2) {
            validateV2Schema(conn);
            return;
        }

        if (userVersion != 0) {
            throw new IllegalStateException("Version de esquema desconocida o futura: user_version=" + userVersion);
        }

        // userVersion == 0
        if (tables.isEmpty()) {
            bootstrapNewDatabase(conn);
        } else if (isLegacySchema(tables, conn)) {
            migrateLegacyDatabase(conn);
        } else {
            throw new IllegalStateException("Esquema SQLite incompatible o desconocido para user_version=0: " + tables);
        }
    }

    private int getUserVersion(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    private Set<String> getTableNames(Connection conn) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_schema WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
            while (rs.next()) {
                tables.add(rs.getString("name"));
            }
        }
        return tables;
    }

    private boolean isLegacySchema(Set<String> tables, Connection conn) throws SQLException {
        if (!tables.equals(Set.of("sessions", "products"))) {
            return false;
        }
        Set<String> sessionCols = getColumnNames(conn, "sessions");
        Set<String> productCols = getColumnNames(conn, "products");
        Set<String> expectedSessions = Set.of("id", "state", "pending", "temp_image", "snapshot", "next_number");
        Set<String> expectedProducts = Set.of("id", "session_id", "piece_number", "title", "price", "image_url", "sold");
        return sessionCols.equals(expectedSessions) && productCols.equals(expectedProducts);
    }

    private Set<String> getColumnNames(Connection conn, String table) throws SQLException {
        Set<String> cols = new HashSet<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                cols.add(rs.getString("name"));
            }
        }
        return cols;
    }

    private void bootstrapNewDatabase(Connection conn) throws SQLException {
        log.info("Inicializando base de datos nueva con esquema v2...");
        conn.setAutoCommit(false);
        try {
            executeSqlScript(conn, "schema.sql");
            executeSqlScript(conn, "db/seed.sql");
            installTriggers(conn);
            validateDefaultSeed(conn);
            validateTriggers(conn);

            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA user_version = 2");
            }
            conn.commit();
            log.info("Base de datos inicializada exitosamente en v2.");
        } catch (Exception e) {
            conn.rollback();
            throw new IllegalStateException("Error al inicializar base de datos nueva: " + e.getMessage(), e);
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private void migrateLegacyDatabase(Connection conn) throws SQLException {
        log.info("Detectado esquema legacy v0. Prevalidando datos antes de migrar...");
        prevalidateLegacyData(conn);

        boolean hasDefault = checkHasDefault(conn);
        if (hasDefault) {
            validateDefaultSeedStrict(conn);
        }

        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");
        }

        conn.setAutoCommit(false);
        try {
            executeSqlScript(conn, "db/migrate-v0-v2.sql");

            if (!hasDefault) {
                executeSqlScript(conn, "db/seed.sql");
            }

            installTriggers(conn);
            validateDefaultSeed(conn);
            validateTriggers(conn);

            // Verificaciones de integridad SQLite
            try (Statement st = conn.createStatement()) {
                try (ResultSet rs = st.executeQuery("PRAGMA foreign_key_check")) {
                    if (rs.next()) {
                        throw new IllegalStateException("foreign_key_check fallo despues de migracion");
                    }
                }
                try (ResultSet rs = st.executeQuery("PRAGMA quick_check")) {
                    if (rs.next() && !"ok".equalsIgnoreCase(rs.getString(1))) {
                        throw new IllegalStateException("quick_check fallo: " + rs.getString(1));
                    }
                }
                st.execute("PRAGMA user_version = 2");
            }

            conn.commit();
            log.info("Migracion v0 -> v2 completada con exito.");
        } catch (Exception e) {
            conn.rollback();
            throw new IllegalStateException("Error durante migracion v0 -> v2: " + e.getMessage(), e);
        } finally {
            conn.setAutoCommit(true);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
        }
    }

    private void installTriggers(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TRIGGER IF NOT EXISTS protect_default_sessions_insert " +
                    "BEFORE INSERT ON sessions WHEN NEW.id = 'DEFAULT' BEGIN " +
                    "SELECT RAISE(ABORT, 'DEFAULT session is immutable and cannot be inserted'); END;");
            st.execute("CREATE TRIGGER IF NOT EXISTS protect_default_sessions_update " +
                    "BEFORE UPDATE ON sessions WHEN OLD.id = 'DEFAULT' OR NEW.id = 'DEFAULT' BEGIN " +
                    "SELECT RAISE(ABORT, 'DEFAULT session is immutable and cannot be modified'); END;");
            st.execute("CREATE TRIGGER IF NOT EXISTS protect_default_sessions_delete " +
                    "BEFORE DELETE ON sessions WHEN OLD.id = 'DEFAULT' BEGIN " +
                    "SELECT RAISE(ABORT, 'DEFAULT session is immutable and cannot be deleted'); END;");
            st.execute("CREATE TRIGGER IF NOT EXISTS protect_default_products_insert " +
                    "BEFORE INSERT ON products WHEN NEW.session_id = 'DEFAULT' BEGIN " +
                    "SELECT RAISE(ABORT, 'DEFAULT products are immutable and cannot be inserted'); END;");
            st.execute("CREATE TRIGGER IF NOT EXISTS protect_default_products_update " +
                    "BEFORE UPDATE ON products WHEN OLD.session_id = 'DEFAULT' OR NEW.session_id = 'DEFAULT' BEGIN " +
                    "SELECT RAISE(ABORT, 'DEFAULT products are immutable and cannot be modified'); END;");
            st.execute("CREATE TRIGGER IF NOT EXISTS protect_default_products_delete " +
                    "BEFORE DELETE ON products WHEN OLD.session_id = 'DEFAULT' BEGIN " +
                    "SELECT RAISE(ABORT, 'DEFAULT products are immutable and cannot be deleted'); END;");
        }
    }

    private boolean checkHasDefault(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sessions WHERE id='DEFAULT'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private void prevalidateLegacyData(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            // Verificar sesiones
            try (ResultSet rs = st.executeQuery("SELECT id, state, next_number FROM sessions")) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    if (id == null) {
                        throw new IllegalStateException("Sesion con id NULL en legacy");
                    }
                    if (!id.equals("DEFAULT") && (!id.matches("[a-zA-Z0-9_-]{8,80}"))) {
                        throw new IllegalStateException("Sesion con id invalido en legacy: " + id);
                    }
                    String state = rs.getString("state");
                    if (state == null || (!state.equals("IDLE") && !state.equals("AWAITING_DETAILS") && !state.equals("AWAITING_CONFIRMATION"))) {
                        throw new IllegalStateException("Sesion con estado invalido en legacy: " + state);
                    }
                    int nextNumber = rs.getInt("next_number");
                    if (rs.wasNull() || nextNumber < 1) {
                        throw new IllegalStateException("Sesion con next_number invalido en legacy: " + nextNumber);
                    }
                }
            }

            // Verificar productos
            try (ResultSet rs = st.executeQuery("SELECT p.id, p.session_id, p.piece_number, p.title, p.price, p.image_url, p.sold, typeof(p.price) as p_type, typeof(p.piece_number) as num_type FROM products p LEFT JOIN sessions s ON p.session_id = s.id WHERE s.id IS NULL")) {
                if (rs.next()) {
                    throw new IllegalStateException("Productos huerfanos detectados en legacy: session_id=" + rs.getString("session_id"));
                }
            }

            try (ResultSet rs = st.executeQuery("SELECT piece_number, title, price, image_url, sold, typeof(price) as p_type, typeof(piece_number) as num_type, typeof(sold) as sold_type FROM products")) {
                while (rs.next()) {
                    String pType = rs.getString("p_type");
                    String numType = rs.getString("num_type");
                    String soldType = rs.getString("sold_type");
                    if (!"integer".equalsIgnoreCase(pType) || !"integer".equalsIgnoreCase(numType) || !"integer".equalsIgnoreCase(soldType)) {
                        throw new IllegalStateException("Tipos no enteros en products de legacy");
                    }
                    int price = rs.getInt("price");
                    if (price <= 0) {
                        throw new IllegalStateException("Precio <= 0 en products de legacy: " + price);
                    }
                    int pieceNumber = rs.getInt("piece_number");
                    if (pieceNumber <= 0) {
                        throw new IllegalStateException("piece_number <= 0 en products de legacy: " + pieceNumber);
                    }
                    int sold = rs.getInt("sold");
                    if (sold != 0 && sold != 1) {
                        throw new IllegalStateException("sold fuera de (0, 1) en products de legacy: " + sold);
                    }
                    String title = rs.getString("title");
                    if (title == null || title.trim().isEmpty()) {
                        throw new IllegalStateException("title vacio en products de legacy");
                    }
                    String imageUrl = rs.getString("image_url");
                    if (imageUrl == null || imageUrl.trim().isEmpty()) {
                        throw new IllegalStateException("image_url vacio en products de legacy");
                    }
                }
            }
        }
    }

    private void validateV2Schema(Connection conn) throws SQLException {
        Set<String> tables = getTableNames(conn);
        if (!tables.contains("sessions") || !tables.contains("products")) {
            throw new IllegalStateException("Esquema v2 incompleto: faltan tablas requeridas");
        }
        Set<String> sessionCols = getColumnNames(conn, "sessions");
        Set<String> productCols = getColumnNames(conn, "products");
        if (!sessionCols.contains("updated_at") || !productCols.contains("updated_at")) {
            throw new IllegalStateException("Esquema v2 invalido: falta columna updated_at");
        }
        validateDefaultSeedStrict(conn);
        validateTriggers(conn);
    }

    private void validateDefaultSeed(Connection conn) throws SQLException {
        validateDefaultSeedStrict(conn);
    }

    private void validateDefaultSeedStrict(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT state, pending, temp_image, snapshot, next_number FROM sessions WHERE id='DEFAULT'")) {
                if (!rs.next()) {
                    throw new IllegalStateException("DEFAULT session no existe");
                }
                if (!"IDLE".equals(rs.getString("state")) || rs.getString("pending") != null || rs.getString("temp_image") != null || rs.getString("snapshot") != null || rs.getInt("next_number") != 9) {
                    throw new IllegalStateException("Metadata de DEFAULT session alterada o corrupta");
                }
            }

            Map<Integer, ExpectedPiece> actualPieces = new HashMap<>();
            try (ResultSet rs = st.executeQuery("SELECT piece_number, title, price, image_url, sold FROM products WHERE session_id='DEFAULT' ORDER BY piece_number")) {
                while (rs.next()) {
                    int pieceNumber = rs.getInt("piece_number");
                    String title = rs.getString("title");
                    int price = rs.getInt("price");
                    String imageUrl = rs.getString("image_url");
                    int sold = rs.getInt("sold");
                    if (sold != 0) {
                        throw new IllegalStateException("DEFAULT product tiene sold != 0");
                    }
                    actualPieces.put(pieceNumber, new ExpectedPiece(pieceNumber, title, price, imageUrl));
                }
            }

            if (actualPieces.size() != EXPECTED_PIECES.size()) {
                throw new IllegalStateException("DEFAULT no tiene exactamente 8 productos (tiene " + actualPieces.size() + ")");
            }

            for (ExpectedPiece expected : EXPECTED_PIECES) {
                ExpectedPiece actual = actualPieces.get(expected.number());
                if (actual == null || !actual.title().equals(expected.title()) || actual.price() != expected.price() || !actual.imageUrl().equals(expected.imageUrl())) {
                    throw new IllegalStateException("DEFAULT product #" + expected.number() + " no coincide con la semilla canonica");
                }
            }
        }
    }

    private void validateTriggers(Connection conn) throws SQLException {
        Set<String> triggers = new HashSet<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_schema WHERE type='trigger'")) {
            while (rs.next()) {
                triggers.add(rs.getString("name"));
            }
        }
        Set<String> requiredTriggers = Set.of(
                "protect_default_sessions_insert",
                "protect_default_sessions_update",
                "protect_default_sessions_delete",
                "protect_default_products_insert",
                "protect_default_products_update",
                "protect_default_products_delete"
        );
        if (!triggers.containsAll(requiredTriggers)) {
            throw new IllegalStateException("Faltan triggers de proteccion de DEFAULT. Encontrados: " + triggers);
        }
    }

    private void executeSqlScript(Connection conn, String resourcePath) throws SQLException {
        String content;
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar el script SQL " + resourcePath, e);
        }

        String cleaned = content.replaceAll("(?m)^\\s*--.*$", "");
        String[] statements = cleaned.split(";;");
        try (Statement st = conn.createStatement()) {
            for (String raw : statements) {
                String trimmed = raw.trim();
                if (!trimmed.isEmpty()) {
                    st.execute(trimmed);
                }
            }
        }
    }

}
