---
name: architect-spec
description: Diseñar o revisar una especificación de issue de El Taller de Norma como Codex, con contratos compatibles con el monolito Java 25, Spring MVC y SQLite; entregar diseño para aprobación de Orca sin implementar funcionalidades.
---

# Especificar una issue de Norma

1. Leer `AGENTS.md`, las dos especificaciones Markdown de la raíz y `docs/decisiones.md`. Confirmar ruta/rama con Git y las variables Orca conforme a la regla de worktree. Leer la issue con `gh issue view NUMERO --json title,body,state` y sus dependencias en `docs/backlog.json`. Un backlog existente no autoriza construir.
2. Inspeccionar el código afectado y `git status --short`, incluidos cambios sin seguimiento. Distinguir código presente, contrato aprobado y comportamiento todavía sin auditar. Consultar diseños/aprobaciones/auditorías pertinentes en `docs/orchestration/`; sus informes históricos pueden corresponder a otro hash.
3. Trazar el flujo real en el paquete plano `src/main/java/com/norma/taller/`:
   - `Api`: REST multipart y SSE `catalog-update`; también contiene `MediaConfig` y `ApiErrors`.
   - `BotEngine`: FSM y unidad transaccional; `BotModel`: records/tipos sellados; `IntentParser`: interpretación determinista; `BotSessionCodec`: JSON estricto y compatibilidad legacy.
   - `Catalog`: `JdbcClient`, `Product` y `Session`; `CatalogDatabaseInitializer`: bootstrap/migración/validación SQLite v2.
   - `MediaService`: imágenes con Thumbnailator/ImageIO WebP, STT Groq por RestClient y TTS por proceso edge-tts.
   - `src/main/resources/static/`: index.html, style.css, app.js y assets locales. No hay build Node, JPA ni broker.
4. Para contratos HTTP, leer `Api.java`: `/api/products`, `/api/catalog/stream`, `/api/bot/message`, `/api/reset`, `/api/ping` y `/media/**`. Documentar los parámetros, respuestas y errores de la tarea. Preservar `Reply(replyText,replyAudioUrl,state)` y multipart `sessionId/text/image/audio` salvo cambio aprobado. Texto y audio no vacíos juntos se rechazan; las imágenes pueden acompañar cualquiera.
5. Para persistencia, leer `schema.sql`, `db/seed.sql`, `db/migrate-v0-v2.sql` y el inicializador, no solo los diagramas originales. Mantener DEFAULT inmutable, sesiones vacías sin reseed, numeración monotónica tras reset/undo y una instancia por archivo con una conexión Hikari. Un cambio de DDL requiere diseño de migración y prueba de datos previos; `CREATE IF NOT EXISTS` no sustituye migraciones.
6. Para conversación, describir transiciones de IDLE, AWAITING_DETAILS y AWAITING_CONFIRMATION; distinguir borrador de publicación. Las cuatro operaciones requieren confirmación; undo consume un snapshot de un paso. Resolver nombres ambiguos mediante aclaración. Proveedores externos se ejecutan fuera de la transacción del motor. No incorporar Groq al parser como una autoridad para confirmar.
7. Redactar `docs/orchestration/issue-N-design.md` con versión y hash base, hechos observados, decisiones propuestas, contratos/datos/flujos, lista cerrada de archivos, compatibilidad, tareas secuenciales y criterios con evidencia verificable. Ajustar profundidad al riesgo: no agregar esquemas, colas, autenticación o módulos por preferencia personal. La vidriera es de lectura, sin carrito/pagos; interfaz en español argentino, precios enteros y fotos humanas.
8. Entregar a Orca con el formato de delegación de `AGENTS.md`. Esperar aprobación versionada antes de construcción. En revisión, emitir PASS/FAIL contra esa versión con reproducción y corrección mínima; no implementar el arreglo ni cerrar la issue.

El diseño puede definir records, interfaces y pseudocódigo; el código de producción corresponde a Antigravity. Una incompatibilidad arquitectónica vuelve a Orca, que solicita ajuste a Codex.
