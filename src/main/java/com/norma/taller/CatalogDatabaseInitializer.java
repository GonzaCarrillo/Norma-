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

    private static final Map<String, String> REQUIRED_TRIGGERS = Map.of(
            "protect_default_sessions_insert",
            "CREATE TRIGGER protect_default_sessions_insert BEFORE INSERT ON sessions WHEN NEW.id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT session is immutable and cannot be inserted'); END",
            "protect_default_sessions_update",
            "CREATE TRIGGER protect_default_sessions_update BEFORE UPDATE ON sessions WHEN OLD.id = 'DEFAULT' OR NEW.id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT session is immutable and cannot be modified'); END",
            "protect_default_sessions_delete",
            "CREATE TRIGGER protect_default_sessions_delete BEFORE DELETE ON sessions WHEN OLD.id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT session is immutable and cannot be deleted'); END",
            "protect_default_products_insert",
            "CREATE TRIGGER protect_default_products_insert BEFORE INSERT ON products WHEN NEW.session_id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT products are immutable and cannot be inserted'); END",
            "protect_default_products_update",
            "CREATE TRIGGER protect_default_products_update BEFORE UPDATE ON products WHEN OLD.session_id = 'DEFAULT' OR NEW.session_id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT products are immutable and cannot be modified'); END",
            "protect_default_products_delete",
            "CREATE TRIGGER protect_default_products_delete BEFORE DELETE ON products WHEN OLD.session_id = 'DEFAULT' BEGIN SELECT RAISE(ABORT, 'DEFAULT products are immutable and cannot be deleted'); END"
    );

    private static final String LEGACY_SESSIONS_SQL_NORM =
            "create table sessions(id text primary key,state text not null default 'idle',pending text,temp_image text, snapshot text,next_number integer not null default 9)";
    private static final String LEGACY_PRODUCTS_SQL_NORM =
            "create table products(id integer primary key autoincrement,session_id text not null,piece_number integer not null,title text not null,price integer not null check(price>0),image_url text not null,sold integer not null default 0,unique(session_id,piece_number))";

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
                            if (!rsWal.next() || !"wal".equalsIgnoreCase(rsWal.getString(1))) {
                                throw new IllegalStateException("No se pudo configurar journal_mode=WAL");
                            }
                            log.info("PRAGMA journal_mode configurado a WAL");
                        }
                    }
                }
            }
            st.execute("PRAGMA foreign_keys=ON");
            try (ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                if (!rs.next() || rs.getInt(1) != 1) {
                    throw new IllegalStateException("No se pudo habilitar PRAGMA foreign_keys=ON");
                }
            }

            st.execute("PRAGMA busy_timeout=5000");
            try (ResultSet rs = st.executeQuery("PRAGMA busy_timeout")) {
                if (!rs.next() || rs.getInt(1) != 5000) {
                    throw new IllegalStateException("No se pudo configurar PRAGMA busy_timeout=5000");
                }
            }

            st.execute("PRAGMA synchronous=NORMAL");
            try (ResultSet rs = st.executeQuery("PRAGMA synchronous")) {
                if (!rs.next() || rs.getInt(1) != 1) { // 1 = NORMAL
                    throw new IllegalStateException("No se pudo configurar PRAGMA synchronous=NORMAL");
                }
            }
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
        // En legacy estricto, sólo pueden existir las tablas 'sessions' y 'products',
        // sin vistas ni triggers extra. (sqlite_sequence y autoindexes son internos esperados).
        try (Statement st = conn.createStatement()) {
            // Verificar que no existan vistas
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM sqlite_schema WHERE type='view'")) {
                if (rs.next() && rs.getInt(1) > 0) return false;
            }
            // Verificar que no existan triggers extra
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM sqlite_schema WHERE type='trigger'")) {
                if (rs.next() && rs.getInt(1) > 0) return false;
            }
            // Verificar que no existan indices de usuario que no sean autoindex
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM sqlite_schema WHERE type='index' AND name NOT LIKE 'sqlite_%'")) {
                if (rs.next() && rs.getInt(1) > 0) return false;
            }
            // Verificar definiciones exactas de DDL legacy de sessions y products
            try (ResultSet rs = st.executeQuery("SELECT name, sql FROM sqlite_schema WHERE type='table' AND name IN ('sessions', 'products')")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    String name = rs.getString("name");
                    String sql = normalizeSql(rs.getString("sql"));
                    if ("sessions".equals(name)) {
                        if (!sql.equals(LEGACY_SESSIONS_SQL_NORM)) return false;
                    } else if ("products".equals(name)) {
                        if (!sql.equals(LEGACY_PRODUCTS_SQL_NORM)) return false;
                    }
                }
                if (count != 2) return false;
            }
        }
        return tables.equals(Set.of("sessions", "products"));
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
            validateV2Schema(conn);

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

        // F3: Capturar seq histórico previo de products en sqlite_sequence
        Long oldSeq = null;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT seq FROM sqlite_sequence WHERE name='products'")) {
            if (rs.next()) {
                oldSeq = rs.getLong(1);
            }
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

            // F3: Reconstituir sqlite_sequence conservando el máximo histórico previo
            if (oldSeq != null) {
                try (Statement st = conn.createStatement()) {
                    int updated = st.executeUpdate("UPDATE sqlite_sequence SET seq = MAX(seq, " + oldSeq + ") WHERE name = 'products'");
                    if (updated == 0) {
                        st.execute("INSERT INTO sqlite_sequence(name, seq) VALUES('products', " + oldSeq + ")");
                    }
                }
            }

            installTriggers(conn);
            validateDefaultSeed(conn);
            validateV2Schema(conn);

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
                try (ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                    if (!rs.next() || rs.getInt(1) != 1) {
                        throw new IllegalStateException("Fallo al restaurar PRAGMA foreign_keys=ON tras migracion");
                    }
                }
            }
        }
    }

    private void installTriggers(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            for (Map.Entry<String, String> entry : REQUIRED_TRIGGERS.entrySet()) {
                st.execute(entry.getValue());
            }
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
            // F4: Verificar sesiones con typeof estricto y sin truncamiento de getInt
            try (ResultSet rs = st.executeQuery("SELECT id, state, next_number, typeof(next_number) AS nn_type FROM sessions")) {
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

                    String nnType = rs.getString("nn_type");
                    if (!"integer".equalsIgnoreCase(nnType)) {
                        throw new IllegalStateException("next_number no entero detectado en legacy (" + nnType + ") para sesion: " + id);
                    }
                    long nextNumber = rs.getLong("next_number");
                    if (nextNumber < 1 || nextNumber > 2147483647L) {
                        throw new IllegalStateException("Sesion con next_number fuera de rango en legacy: " + nextNumber);
                    }
                }
            }

            // Verificar productos huerfanos
            try (ResultSet rs = st.executeQuery("SELECT p.id, p.session_id FROM products p LEFT JOIN sessions s ON p.session_id = s.id WHERE s.id IS NULL")) {
                if (rs.next()) {
                    throw new IllegalStateException("Productos huerfanos detectados en legacy: session_id=" + rs.getString("session_id"));
                }
            }

            // F4: Verificar productos con typeof estricto y rangos de 64 bits
            try (ResultSet rs = st.executeQuery("SELECT piece_number, title, price, image_url, sold, typeof(price) as p_type, typeof(piece_number) as num_type, typeof(sold) as sold_type FROM products")) {
                while (rs.next()) {
                    String pType = rs.getString("p_type");
                    String numType = rs.getString("num_type");
                    String soldType = rs.getString("sold_type");
                    if (!"integer".equalsIgnoreCase(pType) || !"integer".equalsIgnoreCase(numType) || !"integer".equalsIgnoreCase(soldType)) {
                        throw new IllegalStateException("Tipos no enteros en products de legacy");
                    }
                    long price = rs.getLong("price");
                    if (price <= 0 || price > 2147483647L) {
                        throw new IllegalStateException("Precio fuera de rango en products de legacy: " + price);
                    }
                    long pieceNumber = rs.getLong("piece_number");
                    if (pieceNumber <= 0 || pieceNumber > 2147483646L) {
                        throw new IllegalStateException("piece_number fuera de rango en products de legacy: " + pieceNumber);
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

        // F1: Validar columnas, tipos, notnull, pk y defaults via PRAGMA table_info
        validateSessionsTableInfo(conn);
        validateProductsTableInfo(conn);

        // F1: Validar foreign keys de products
        validateProductsForeignKeys(conn);

        // F1: Validar unique index de products (session_id, piece_number)
        validateProductsUniqueIndex(conn);

        // F1: Validar CHECK constraints funcionales en sessions y products
        validateCheckConstraintsFunctional(conn);

        // F1: Validar definiciones efectivas de triggers
        validateTriggersStrict(conn);

        // Validar semilla canónica
        validateDefaultSeedStrict(conn);
    }

    private void validateSessionsTableInfo(Connection conn) throws SQLException {
        // sessions: id (TEXT NOT NULL PK), state (TEXT NOT NULL DEF 'IDLE'), pending, temp_image, snapshot, next_number (INTEGER NOT NULL DEF 9), updated_at (TEXT NOT NULL DEF CURRENT_TIMESTAMP)
        Map<String, ColInfo> cols = getTableColsInfo(conn, "sessions");
        Set<String> expectedCols = Set.of("id", "state", "pending", "temp_image", "snapshot", "next_number", "updated_at");
        if (!cols.keySet().equals(expectedCols)) {
            throw new IllegalStateException("sessions columnas no coinciden con v2: " + cols.keySet());
        }
        ColInfo id = cols.get("id");
        if (!"TEXT".equalsIgnoreCase(id.type()) || !id.notNull() || id.pk() != 1) {
            throw new IllegalStateException("sessions.id definicion incorrecta");
        }
        ColInfo state = cols.get("state");
        if (!"TEXT".equalsIgnoreCase(state.type()) || !state.notNull() || state.pk() != 0) {
            throw new IllegalStateException("sessions.state definicion incorrecta");
        }
        ColInfo nn = cols.get("next_number");
        if (!"INTEGER".equalsIgnoreCase(nn.type()) || !nn.notNull() || nn.pk() != 0) {
            throw new IllegalStateException("sessions.next_number definicion incorrecta");
        }
        ColInfo upd = cols.get("updated_at");
        if (!"TEXT".equalsIgnoreCase(upd.type()) || !upd.notNull() || upd.pk() != 0) {
            throw new IllegalStateException("sessions.updated_at definicion incorrecta");
        }
    }

    private void validateProductsTableInfo(Connection conn) throws SQLException {
        Map<String, ColInfo> cols = getTableColsInfo(conn, "products");
        Set<String> expectedCols = Set.of("id", "session_id", "piece_number", "title", "price", "image_url", "sold", "updated_at");
        if (!cols.keySet().equals(expectedCols)) {
            throw new IllegalStateException("products columnas no coinciden con v2: " + cols.keySet());
        }
        ColInfo id = cols.get("id");
        if (!"INTEGER".equalsIgnoreCase(id.type()) || id.pk() != 1) {
            throw new IllegalStateException("products.id definicion incorrecta");
        }
        ColInfo sessId = cols.get("session_id");
        if (!"TEXT".equalsIgnoreCase(sessId.type()) || !sessId.notNull() || sessId.pk() != 0) {
            throw new IllegalStateException("products.session_id definicion incorrecta");
        }
        ColInfo pieceNum = cols.get("piece_number");
        if (!"INTEGER".equalsIgnoreCase(pieceNum.type()) || !pieceNum.notNull() || pieceNum.pk() != 0) {
            throw new IllegalStateException("products.piece_number definicion incorrecta");
        }
        ColInfo price = cols.get("price");
        if (!"INTEGER".equalsIgnoreCase(price.type()) || !price.notNull() || price.pk() != 0) {
            throw new IllegalStateException("products.price definicion incorrecta");
        }
        ColInfo sold = cols.get("sold");
        if (!"INTEGER".equalsIgnoreCase(sold.type()) || !sold.notNull() || sold.pk() != 0) {
            throw new IllegalStateException("products.sold definicion incorrecta");
        }
    }

    private void validateProductsForeignKeys(Connection conn) throws SQLException {
        boolean fkFound = false;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA foreign_key_list(products)")) {
            while (rs.next()) {
                String targetTable = rs.getString("table");
                String fromCol = rs.getString("from");
                String toCol = rs.getString("to");
                String onUpdate = rs.getString("on_update");
                String onDelete = rs.getString("on_delete");
                if ("sessions".equalsIgnoreCase(targetTable) && "session_id".equalsIgnoreCase(fromCol) && "id".equalsIgnoreCase(toCol)) {
                    if ("RESTRICT".equalsIgnoreCase(onUpdate) && "RESTRICT".equalsIgnoreCase(onDelete)) {
                        fkFound = true;
                    }
                }
            }
        }
        if (!fkFound) {
            throw new IllegalStateException("products foreign key hacia sessions(id) RESTRICT ausente o alterada");
        }
    }

    private void validateProductsUniqueIndex(Connection conn) throws SQLException {
        boolean uniqueFound = false;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA index_list(products)")) {
            while (rs.next()) {
                if (rs.getInt("unique") == 1) {
                    String indexName = rs.getString("name");
                    List<String> indexCols = new ArrayList<>();
                    try (Statement st2 = conn.createStatement();
                         ResultSet rs2 = st2.executeQuery("PRAGMA index_info(" + indexName + ")")) {
                        while (rs2.next()) {
                            indexCols.add(rs2.getString("name"));
                        }
                    }
                    if (indexCols.equals(List.of("session_id", "piece_number"))) {
                        uniqueFound = true;
                        break;
                    }
                }
            }
        }
        if (!uniqueFound) {
            throw new IllegalStateException("UNIQUE(session_id, piece_number) ausente en products");
        }
    }

    private void validateCheckConstraintsFunctional(Connection conn) throws SQLException {
        // Para verificar que la tabla sessions y products tienen realmente sus CHECK compilados en SQLite
        // sin romper ninguna transaccion exterior: se utiliza un SAVEPOINT dedicado.
        try (Statement st = conn.createStatement()) {
            st.execute("SAVEPOINT v2_check_validation_sp");
            try {
                // 1. sessions: CHECK(state IN ('IDLE','AWAITING_DETAILS','AWAITING_CONFIRMATION'))
                boolean checkStateFailed = false;
                try {
                    st.execute("INSERT INTO sessions(id, state) VALUES('valid_sess_1', 'INVALID_STATE')");
                } catch (SQLException e) {
                    checkStateFailed = true;
                }
                if (!checkStateFailed) throw new IllegalStateException("CHECK(state) ausente o inoperante en sessions");

                // 2. products: CHECK(typeof(price)='integer' AND price BETWEEN 1 AND 2147483647)
                boolean checkPriceFailed = false;
                try {
                    st.execute("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES('valid_sess_1', 100, 'T', -1, '/img', 0)");
                } catch (SQLException e) {
                    checkPriceFailed = true;
                }
                if (!checkPriceFailed) throw new IllegalStateException("CHECK(price) ausente o inoperante en products");

                // 3. products: CHECK(typeof(sold)='integer' AND sold IN (0,1))
                boolean checkSoldFailed = false;
                try {
                    st.execute("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES('valid_sess_1', 100, 'T', 100, '/img', 2)");
                } catch (SQLException e) {
                    checkSoldFailed = true;
                }
                if (!checkSoldFailed) throw new IllegalStateException("CHECK(sold) ausente o inoperante en products");

                // 4. products: CHECK(typeof(piece_number)='integer' AND piece_number BETWEEN 1 AND 2147483646)
                boolean checkPieceNumFailed = false;
                try {
                    st.execute("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES('valid_sess_1', 0, 'T', 100, '/img', 0)");
                } catch (SQLException e) {
                    checkPieceNumFailed = true;
                }
                if (!checkPieceNumFailed) throw new IllegalStateException("CHECK(piece_number) ausente o inoperante en products");

                // 5. products: CHECK(length(trim(title))>0)
                boolean checkTitleFailed = false;
                try {
                    st.execute("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES('valid_sess_1', 101, '   ', 100, '/img', 0)");
                } catch (SQLException e) {
                    checkTitleFailed = true;
                }
                if (!checkTitleFailed) throw new IllegalStateException("CHECK(title) ausente o inoperante en products");

                // 6. products: CHECK(length(trim(image_url))>0)
                boolean checkImgFailed = false;
                try {
                    st.execute("INSERT INTO products(session_id, piece_number, title, price, image_url, sold) VALUES('valid_sess_1', 102, 'T', 100, '', 0)");
                } catch (SQLException e) {
                    checkImgFailed = true;
                }
                if (!checkImgFailed) throw new IllegalStateException("CHECK(image_url) ausente o inoperante en products");
            } finally {
                st.execute("ROLLBACK TO v2_check_validation_sp");
                st.execute("RELEASE v2_check_validation_sp");
            }
        }
    }


    private record ColInfo(String name, String type, boolean notNull, String defVal, int pk) {}

    private Map<String, ColInfo> getTableColsInfo(Connection conn, String table) throws SQLException {
        Map<String, ColInfo> map = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                String name = rs.getString("name");
                String type = rs.getString("type");
                boolean notNull = rs.getInt("notnull") == 1;
                String defVal = rs.getString("dflt_value");
                int pk = rs.getInt("pk");
                map.put(name, new ColInfo(name, type, notNull, defVal, pk));
            }
        }
        return map;
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

    private void validateTriggersStrict(Connection conn) throws SQLException {
        Map<String, String> actualTriggers = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, sql FROM sqlite_schema WHERE type='trigger'")) {
            while (rs.next()) {
                actualTriggers.put(rs.getString("name"), rs.getString("sql"));
            }
        }

        for (Map.Entry<String, String> required : REQUIRED_TRIGGERS.entrySet()) {
            String name = required.getKey();
            String rawActualSql = actualTriggers.get(name);
            if (rawActualSql == null) {
                throw new IllegalStateException("Trigger requerido ausente: " + name);
            }
            String normActual = normalizeSql(rawActualSql);
            String normExpected = normalizeSql(required.getValue());
            if (!normActual.equals(normExpected)) {
                throw new IllegalStateException("Definicion de trigger inoperante o alterada para: " + name + ". Esperada: [" + normExpected + "], Encontrada: [" + normActual + "]");
            }
        }
    }

    private static String normalizeSql(String sql) {
        if (sql == null) return "";
        return sql.replaceAll("(?m)^\\s*--.*$", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
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
