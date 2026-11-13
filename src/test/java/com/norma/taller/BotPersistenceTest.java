package com.norma.taller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BotPersistenceTest {

    private static final String SESS = "session_persist_test";

    @Test
    @DisplayName("D1: Persistencia real sobre archivo SQLite: FSM, reinicios de contexto/pool y undo entre reinicios")
    void testPersistenceAcrossRestarts(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("norma_persist_test.db");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        ObjectMapper mapper = new ObjectMapper();

        // ==========================================
        // 1. PRIMER ARRANQUE: Inicializar y crear borrador en AWAITING_DETAILS
        // ==========================================
        {
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl(jdbcUrl);
            CatalogDatabaseInitializer init = new CatalogDatabaseInitializer(ds);
            init.initialize();

            JdbcClient db = JdbcClient.create(ds);
            Catalog catalog = new Catalog(db);
            catalog.ensure(SESS);
            IntentParser parser = new IntentParser();
            BotEngine bot = new BotEngine(catalog, parser, mapper);

            // Enviar foto sola -> pasa a AWAITING_DETAILS
            BotEngine.Reply r1 = bot.process(SESS, null, "/media/plato.jpg");
            assertEquals("AWAITING_DETAILS", r1.state());
            assertEquals("AWAITING_DETAILS", catalog.session(SESS).state());
            assertEquals("/media/plato.jpg", catalog.session(SESS).tempImage());
        }

        // ==========================================
        // 2. SEGUNDO ARRANQUE (simula reinicio): Reabrir archivo y completar datos hacia AWAITING_CONFIRMATION
        // ==========================================
        {
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl(jdbcUrl);
            // Inicializador sobre BD existente en v2 no recrea tablas
            CatalogDatabaseInitializer init = new CatalogDatabaseInitializer(ds);
            init.initialize();

            JdbcClient db = JdbcClient.create(ds);
            Catalog catalog = new Catalog(db);
            IntentParser parser = new IntentParser();
            BotEngine bot = new BotEngine(catalog, parser, mapper);

            // Sesión recuperada intacta en AWAITING_DETAILS
            Catalog.Session s = catalog.session(SESS);
            assertEquals("AWAITING_DETAILS", s.state());
            assertEquals("/media/plato.jpg", s.tempImage());

            // Completar detalles: "Plato hondo a 25000" -> pasa a AWAITING_CONFIRMATION
            BotEngine.Reply r2 = bot.process(SESS, "Plato hondo a 25000", null);
            assertEquals("AWAITING_CONFIRMATION", r2.state());
            assertEquals("AWAITING_CONFIRMATION", catalog.session(SESS).state());
            assertNotNull(catalog.session(SESS).pending());
        }

        // ==========================================
        // 3. TERCER ARRANQUE: Reabrir y confirmar cambio
        // ==========================================
        {
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl(jdbcUrl);
            CatalogDatabaseInitializer init = new CatalogDatabaseInitializer(ds);
            init.initialize();

            JdbcClient db = JdbcClient.create(ds);
            Catalog catalog = new Catalog(db);
            IntentParser parser = new IntentParser();
            BotEngine bot = new BotEngine(catalog, parser, mapper);

            // Confirmar
            BotEngine.Reply r3 = bot.process(SESS, "sí", null);
            assertEquals("IDLE", r3.state());
            assertEquals(9, catalog.all(SESS).size());
            assertEquals(10, catalog.session(SESS).nextNumber()); // 9 consumido, siguiente 10
            assertNotNull(catalog.session(SESS).snapshot());
        }

        // ==========================================
        // 4. CUARTO ARRANQUE: Reabrir tras confirmación y ejecutar undo
        // ==========================================
        {
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl(jdbcUrl);
            CatalogDatabaseInitializer init = new CatalogDatabaseInitializer(ds);
            init.initialize();

            JdbcClient db = JdbcClient.create(ds);
            Catalog catalog = new Catalog(db);
            IntentParser parser = new IntentParser();
            BotEngine bot = new BotEngine(catalog, parser, mapper);

            // Verificar estado previo a undo
            assertEquals(9, catalog.all(SESS).size());
            assertNotNull(catalog.session(SESS).snapshot());

            // Ejecutar deshacer
            BotEngine.Reply rUndo = bot.process(SESS, "deshacer", null);
            assertEquals("IDLE", rUndo.state());
            assertTrue(rUndo.replyText().contains("ya dejé todo como estaba antes"));

            // Catálogo restaurado a 8 productos originales
            List<Catalog.Product> prods = catalog.all(SESS);
            assertEquals(8, prods.size());
            // Slot consumido
            assertNull(catalog.session(SESS).snapshot());
            // High-water mark preservado en 10
            assertEquals(10, catalog.session(SESS).nextNumber());
        }

        // ==========================================
        // 5. QUINTO ARRANQUE: Reabrir tras undo y verificar consumo de slot y nueva alta
        // ==========================================
        {
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl(jdbcUrl);
            CatalogDatabaseInitializer init = new CatalogDatabaseInitializer(ds);
            init.initialize();

            JdbcClient db = JdbcClient.create(ds);
            Catalog catalog = new Catalog(db);
            IntentParser parser = new IntentParser();
            BotEngine bot = new BotEngine(catalog, parser, mapper);

            // Intentar segundo undo -> slot consumido
            BotEngine.Reply rUndo2 = bot.process(SESS, "deshacer", null);
            assertTrue(rUndo2.replyText().contains("Todavía no hay un cambio para deshacer"));
            assertEquals(8, catalog.all(SESS).size());
            assertEquals(10, catalog.session(SESS).nextNumber());

            // Nueva alta no recicla el #9 (recibe #10 y next_number pasa a 11)
            bot.process(SESS, "Publicá Taza nueva a 18000", "/media/taza.jpg");
            bot.process(SESS, "sí", null);

            List<Catalog.Product> finalProds = catalog.all(SESS);
            assertEquals(9, finalProds.size());
            assertEquals(10, finalProds.get(8).pieceNumber());
            assertEquals(11, catalog.session(SESS).nextNumber());
        }
    }
}
