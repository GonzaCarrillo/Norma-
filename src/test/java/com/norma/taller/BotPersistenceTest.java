package com.norma.taller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D1: Pruebas de persistencia real sobre archivo SQLite.
 * Utiliza contextos Spring ApplicationContext completos con pool Hikari y proxies transaccionales,
 * cerrando y reabriendo el contexto entre etapas sobre el mismo archivo de base de datos.
 */
public class BotPersistenceTest {

    private static final String SESS = "session_persist_test";
    private static final String OTHER_SESS = "session_other_persist";

    private ConfigurableApplicationContext createContext(String jdbcUrl, Path tempDir) {
        Path uploads = tempDir.resolve("uploads");
        String dbFilePath = jdbcUrl.replace("jdbc:sqlite:", "");
        return new SpringApplicationBuilder(TallerApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=" + jdbcUrl,
                        "--NORMA_DB=" + dbFilePath,
                        "--norma.uploads=" + uploads.toAbsolutePath(),
                        "--groq.api.key=test-key"
                );
    }

    @Test
    @DisplayName("D1: Persistencia real sobre archivo SQLite con contextos Spring y pool Hikari cerrados entre etapas")
    void testPersistenceAcrossSpringContextRestarts(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("norma_persist_spring.db");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        // ==========================================
        // ETAPA 1: Inicialización, ensure de sesiones y creación de borrador en AWAITING_DETAILS
        // ==========================================
        try (ConfigurableApplicationContext ctx1 = createContext(jdbcUrl, tempDir)) {
            Catalog catalog = ctx1.getBean(Catalog.class);
            BotEngine bot = ctx1.getBean(BotEngine.class);

            catalog.ensure(SESS);
            catalog.reset(SESS);
            catalog.ensure(OTHER_SESS);
            catalog.reset(OTHER_SESS);

            // Enviar foto sola -> pasa a AWAITING_DETAILS con tempImage persistida
            BotEngine.Reply r1 = bot.process(SESS, null, "/media/plato.jpg");
            assertEquals("AWAITING_DETAILS", r1.state());

            Catalog.Session s = catalog.session(SESS);
            assertEquals("AWAITING_DETAILS", s.state());
            assertEquals("/media/plato.jpg", s.tempImage());
            assertNotNull(s.pending());
            assertTrue(s.pending().contains("/media/plato.jpg"));
            assertNull(s.snapshot());
            assertEquals(9, s.nextNumber());

            // Verificar aislamiento: OTHER_SESS sigue IDLE
            assertEquals("IDLE", catalog.session(OTHER_SESS).state());
        }

        // ==========================================
        // ETAPA 2: Reapertura con nuevo contexto y pool; verificar DETAILS y avanzar a AWAITING_CONFIRMATION
        // ==========================================
        try (ConfigurableApplicationContext ctx2 = createContext(jdbcUrl, tempDir)) {
            Catalog catalog = ctx2.getBean(Catalog.class);
            BotEngine bot = ctx2.getBean(BotEngine.class);

            // Verificar recuperación exacta de AWAITING_DETAILS tras reinicio de contexto
            Catalog.Session s = catalog.session(SESS);
            assertEquals("AWAITING_DETAILS", s.state());
            assertEquals("/media/plato.jpg", s.tempImage());

            // Completar detalles: "Plato hondo a 25000" -> pasa a AWAITING_CONFIRMATION
            BotEngine.Reply r2 = bot.process(SESS, "Plato hondo a 25000", null);
            assertEquals("AWAITING_CONFIRMATION", r2.state());

            Catalog.Session sAfter = catalog.session(SESS);
            assertEquals("AWAITING_CONFIRMATION", sAfter.state());
            assertNotNull(sAfter.pending());
            assertEquals("/media/plato.jpg", sAfter.tempImage());

            // En OTHER_SESS iniciamos un borrador distinto para comparar aislamiento
            bot.process(OTHER_SESS, "Cambiá el precio de #1 a 30000", null);
            assertEquals("AWAITING_CONFIRMATION", catalog.session(OTHER_SESS).state());
        }

        // ==========================================
        // ETAPA 3: Reapertura con nuevo contexto y pool; confirmar cambio mediante proxy Spring
        // ==========================================
        try (ConfigurableApplicationContext ctx3 = createContext(jdbcUrl, tempDir)) {
            Catalog catalog = ctx3.getBean(Catalog.class);
            BotEngine bot = ctx3.getBean(BotEngine.class);

            // Confirmar en SESS
            BotEngine.Reply r3 = bot.process(SESS, "sí", null);
            assertEquals("IDLE", r3.state());

            Catalog.Session s = catalog.session(SESS);
            assertEquals("IDLE", s.state());
            assertNull(s.pending());
            assertNull(s.tempImage());
            assertNotNull(s.snapshot()); // Snapshot guardado
            assertEquals(10, s.nextNumber()); // Contador avanzó a 10
            assertEquals(9, catalog.all(SESS).size()); // 9 productos

            // OTHER_SESS sigue esperando confirmación sin ser alterada
            assertEquals("AWAITING_CONFIRMATION", catalog.session(OTHER_SESS).state());
            assertEquals(8, catalog.all(OTHER_SESS).size());
        }

        // ==========================================
        // ETAPA 4: Reapertura con nuevo contexto y pool; ejecutar undo mediante proxy Spring
        // ==========================================
        try (ConfigurableApplicationContext ctx4 = createContext(jdbcUrl, tempDir)) {
            Catalog catalog = ctx4.getBean(Catalog.class);
            BotEngine bot = ctx4.getBean(BotEngine.class);

            // Verificar estado antes de undo
            assertEquals(9, catalog.all(SESS).size());
            assertNotNull(catalog.session(SESS).snapshot());

            // Deshacer cambio
            BotEngine.Reply rUndo = bot.process(SESS, "deshacer", null);
            assertEquals("IDLE", rUndo.state());
            assertTrue(rUndo.replyText().contains("ya dejé todo como estaba antes"));

            Catalog.Session s = catalog.session(SESS);
            assertEquals("IDLE", s.state());
            assertNull(s.snapshot()); // Slot de snapshot consumido
            assertEquals(10, s.nextNumber()); // High-water mark conservado en 10
            assertEquals(8, catalog.all(SESS).size()); // Restaurado a 8 productos

            // OTHER_SESS cancela su cambio
            bot.process(OTHER_SESS, "no", null);
            assertEquals("IDLE", catalog.session(OTHER_SESS).state());
        }

        // ==========================================
        // ETAPA 5: Reapertura con nuevo contexto y pool; verificar consumo de slot y no reciclado
        // ==========================================
        try (ConfigurableApplicationContext ctx5 = createContext(jdbcUrl, tempDir)) {
            Catalog catalog = ctx5.getBean(Catalog.class);
            BotEngine bot = ctx5.getBean(BotEngine.class);

            // Segundo undo: slot consumido, no redo
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

            // DEFAULT y OTHER_SESS permanecen completamente aislados
            assertEquals(8, catalog.all(OTHER_SESS).size());
            org.springframework.jdbc.core.simple.JdbcClient db = ctx5.getBean(org.springframework.jdbc.core.simple.JdbcClient.class);
            assertEquals(8, db.sql("SELECT count(*) FROM products WHERE session_id='DEFAULT'").query(Integer.class).single());
        }
    }
}
