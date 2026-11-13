# Issue #2 — Catálogo persistente y sesiones aisladas

Versión de diseño: **1.0**, 2026-11-12. Autor: Codex, Software Architect.
Estado: **PROPUESTO; pendiente de aprobación explícita de Orca**. Este documento no autoriza construcción.
Tarea: `task_4dab81c11c34`; dispatch: `ctx_d433b6dc3db0`.
Base observada: `538ff07`, con cambios locales enumerados abajo.

## 1. Fuentes, evidencia y alcance

Leídos: AGENTS.md, ambas especificaciones de raíz, docs/decisiones.md, docs/backlog.json, README.md, docs/operacion.md y aprobación de #1. Consultados con `gh issue view 2 --json number,title,body,comments`, `gh issue list --state all --limit 50 --json number,title,state,body` y `gh issue view 1 --json state,title`: #1 CLOSED, #2 OPEN sin comentarios, #3–#8 OPEN. El PASS de #1 pertenece a esa fase; no acepta el catálogo existente.

#2 exige ocho piezas, SQLite WAL, clonado, restauración, transacciones, semilla inmutable, números estables, precios enteros positivos, aislamiento, persistencia tras reinicio y catálogo vacío sin repoblación. #3 exige FSM, confirmación, cancelación/corrección y deshacer de un paso. Aquí se diseña la infraestructura de persistencia que #3 consumirá; no sus transiciones ni su política de confirmación.

Inspección realizada con `git status`, `git ls-files`, `git diff`, lectura de Catalog.java, schema.sql, application.properties, BotEngine.java, Api.java, FlowTest.java y pom.xml. **No se ejecutaron builds ni tests ni se abrió/modificó una base**. No existe `norma.db`, `norma.db-wal` o `norma.db-shm` en la raíz inspeccionada; no se afirma que no haya bases en otras rutas configuradas.

Estado inicial que debe preservarse: BotEngine.java modificado; AGENTS.md, docs/orchestration/, IntentFallback.java y ApiTest.java sin seguimiento. Catalog.java, schema.sql, FlowTest.java y los ocho assets sí están versionados, pero no auditados para #2. No agregar archivos ajenos al commit de este diseño.

## 2. Contradicciones observadas y resolución propuesta

| Evidencia exacta | Diferencia o riesgo | Decisión para #2 |
|---|---|---|
| Catalog.java:17–20 inserta ocho constantes Java por sesión; schema.sql:1–3 no contiene DEFAULT ni seed | No clona una semilla relacional, como pide la especificación técnica §2–3 | Crear DEFAULT una sola vez y clonar mediante INSERT SELECT; quitar duplicación Java |
| Especificación técnica §3, sandbox paso 2, dice clonar si no hay productos | Confunde sesión nueva con sesión vacía; contradice aceptación de #2 | La existencia de la fila sessions es el único marcador de inicialización |
| Catalog.java:41–42 elimina products y sessions y llama ensure | Borra next_number y permite reutilizar #9 después de reset | Conservar fila de sesión y contador histórico; reemplazar productos desde DEFAULT y limpiar conversación/snapshot atómicamente |
| Catalog.java:30 asigna next_number sin transacción propia; solo BotEngine.process tiene transacción externa | Llamar apply directamente permite inserción y contador parciales | Fronteras transaccionales en repositorio; unirse a la transacción externa cuando exista |
| Catalog.java:37–39 borra y reinserta sin transacción propia, ignora sessionId de cada Product y regenera id | Restauración parcial o copia cruzada accidental; id SQL no es el número público | Validar lote completo y pertenencia antes de borrar, operación atómica; identidad pública por sesión/número |
| Catalog.java:23–39 no valida sesión en todas las entradas | Una llamada interna puede apuntar a DEFAULT o sesión ajena | valid en toda entrada pública, triggers SQL para DEFAULT, predicado session_id en cada operación |
| Catalog.java:31–33 acepta UPDATE de cero filas | Cambio aparente de una pieza inexistente | Exigir exactamente una fila o lanzar error de dominio y revertir |
| schema.sql:2 no restringe state/next_number; TEXT PRIMARY KEY sin NOT NULL explícito | Estados/counters inválidos posibles | NOT NULL y CHECK detallados en §4; sin reglas nuevas de transición |
| schema.sql:3 CHECK(price>0) con afinidad SQLite INTEGER | No garantiza tipo entero; falta CHECK de sold y piece_number y FK | CHECK typeof/rangos, FK y unicidad; migración validada |
| Especificación usa bot_sessions/current_state/previous_snapshot_json; código usa sessions/state/snapshot y next_number | Renombrar rompería mapeo y conversación existente sin beneficio en #2 | Mantener nombres actuales y documentar equivalencia |
| Snapshot actual es JSON List<Product>, no incluye contador; restore deja next_number intacto | No especifica recuperación histórica del contador; rollback de FSM pendiente | Mantener JSON y contador monótono; restaurar contenido no rebobina numeración; #3 diseñará consumo del snapshot |
| Especificación incluye updated_at e índice session; implementación no | Metadatos ausentes; índice independiente puede duplicar UNIQUE existente | Agregar timestamps internos; UNIQUE(session_id,piece_number) cubre búsquedas por sesión |
| FlowTest usa SQLite en memoria y mezcla parser/FSM/CRUD | No demuestra WAL, reinicio ni migración; pasar no acepta #2 | Suites JDBC de archivo aislado, además de regresión existente |

**Interpretación a aprobar:** “números estables” significa no renumerar piezas y no reutilizar números nuevos emitidos en esa sesión, incluso tras reset/restauración. Reset devuelve #1–#8 originales; el próximo alta usa al menos el máximo histórico +1. Ejemplo: alta #9, reset, alta #10. Una sesión realmente nueva empieza en #9. Es una decisión propuesta explícita, no un hecho ya aprobado. Los huecos son válidos. No se garantiza reconstruir números históricos que una versión antigua ya perdió.

## 3. Contratos compatibles

Conservar firmas/records públicos de Catalog y JSON REST, incluyendo Product(id, sessionId, pieceNumber, title, price, imageUrl, sold) y Session(id, state, pending, tempImage, snapshot, nextNumber). Los timestamps no se exponen. `id` es surrogate opaco de almacenamiento, no referencia humana; restore/reset pueden regenerarlo. Las referencias estables son `(sessionId,pieceNumber)`.

- sessionId de evaluador: exactamente `[a-zA-Z0-9_-]{8,80}`, sensible a mayúsculas, sin trim ni normalización; DEFAULT solo interno. Toda entrada de Catalog valida antes de SQL. El token permite aislamiento lógico, no autenticación de producción.
- `ensure(id)`: crea fila+ocho clones solo si la fila de sesión no existía; repetible, incluso con cero productos o todos vendidos. Si falla cualquier clon no deja sesión parcialmente inicializada.
- `all(id)`: listado ordenado por pieceNumber, incluyendo vendidos para consumidores internos/snapshots. No siembra. `session(id)`: lectura existente sin sembrar. Sesión ausente: error de dominio claro; all puede devolver lista vacía, sin inferir existencia a partir de ella.
- `conversation` y `snapshot`: escriben solo en sesión existente y validada; exigen una fila afectada. Preservan formato/semántica actuales, admiten null para limpiar. No reinterpretan pending o snapshot.
- `insert`: primitiva compatible, solo sobre sesión existente, lote válido, número no duplicado; actualiza high-water a max(next_number, number+1) en la misma transacción. No asigna números fuera de apply CREATE; no es un nuevo endpoint. Usada para reponer números históricos en restore/reset, que ya preservan el contador. No hay doble incremento en apply.
- `apply(id,intent)`: validar kind y campos requeridos antes de escribir; CREATE asigna next_number y avanza exactamente una vez; PRICE/IMAGE/SOLD conservan número, campos restantes y contador. Destino inexistente provoca IllegalArgumentException con mensaje útil. No toca borrador/snapshot: #3 conserva su coordinación exterior actual.
- `restore(id,List<Product>)`: lista no nula, posiblemente vacía; cada sessionId coincide con destino, números únicos y campos válidos; ignora surrogate id. Reemplaza solo productos de destino y conserva max(contador anterior, max(número restaurado)+1,9). No cambia estado/borrador/snapshot: el llamador actual lo hace en la misma transacción. No permite recuperar productos de otra sesión.
- `reset(id)`: si es nueva, inicializa; si existe, reemplaza con los ocho clones, state=IDLE, pending/temp_image/snapshot=null y conserva contador. No elimina la fila de sesión ni medios. Repetido produce el mismo contenido, sin disminuir contador.

REST permanece: GET /api/products asegura sesión y filtra sold; POST /api/reset devuelve el mensaje actual; SSE existente se publica después del retorno de reset, por tanto después de commit. Sin cambios de payload, rutas, parser, redacción de bot o emisión SSE. API valida los errores de dominio como 400; errores SQL internos mantienen respuesta genérica existente. No prometer entrega durable de eventos (#6).

## 4. Esquema objetivo v2

Mantener nombres y columnas actuales. `PRAGMA user_version=2` marca migración completa; 0 es base actual sin versión. Otro valor o estructura desconocida detiene arranque. No usar CREATE IF NOT EXISTS como sustituto de migración.

| Tabla/columna | Contrato SQL propuesto |
|---|---|
| sessions.id | TEXT NOT NULL PRIMARY KEY COLLATE BINARY; CHECK(id='DEFAULT' OR (length(id) BETWEEN 8 AND 80 AND id NOT GLOB '*[^a-zA-Z0-9_-]*')) |
| sessions.state | TEXT NOT NULL DEFAULT 'IDLE'; CHECK(state IN ('IDLE','AWAITING_DETAILS','AWAITING_CONFIRMATION')) |
| sessions.pending, temp_image, snapshot | TEXT NULL, mismos nombres y contenido actuales; sin JSON CHECK o dependencia JSON1 |
| sessions.next_number | INTEGER NOT NULL DEFAULT 9; CHECK(typeof(next_number)='integer' AND next_number BETWEEN 9 AND 2147483647) |
| sessions.updated_at | TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP; actualizar en cada escritura |
| products.id | INTEGER PRIMARY KEY AUTOINCREMENT, conservar ids existentes al migrar |
| products.session_id | TEXT NOT NULL REFERENCES sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT |
| products.piece_number | INTEGER NOT NULL; CHECK(typeof(piece_number)='integer' AND piece_number BETWEEN 1 AND 2147483646) |
| products.title | TEXT NOT NULL CHECK(length(trim(title))>0) |
| products.price | INTEGER NOT NULL CHECK(typeof(price)='integer' AND price BETWEEN 1 AND 2147483647) |
| products.image_url | TEXT NOT NULL CHECK(length(trim(image_url))>0) |
| products.sold | INTEGER NOT NULL DEFAULT 0 CHECK(typeof(sold)='integer' AND sold IN (0,1)) |
| products.updated_at | TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP |
| products unique | UNIQUE(session_id,piece_number), índice que sirve filtro de sesión y orden por número |

No índice adicional duplicado por sesión, ni índice sold sin medición. No FK desde JSON. No CHECK de relaciones state/pending/temp_image: impondría decisiones FSM de #3 y podría invalidar estados actuales legítimos (por ejemplo AWAITING_DETAILS con pending y sin foto).

Límite de numeración: si next_number=2147483647, CREATE falla antes de insertar por agotamiento; no overflow de int ni reciclaje. Validar rango también en Java. SQLite permite valores convertibles por afinidad; el contrato exige valor almacenado entero, no distinguir si un SQL externo escribió el literal '15000'. La API Java sigue tipada como int.

### Semilla canónica y defensa SQL

DEFAULT es una fila sessions interna, state IDLE, demás temporales null, next_number=9, más exactamente estas filas, sold=0:

| # | Título | Precio | image_url |
|---|---|---:|---|
| 1 | Cuenco rústico | 15000 | /assets/pieza-1.webp |
| 2 | Jarrón de gres | 18500 | /assets/pieza-2.webp |
| 3 | Taza panzona | 12000 | /assets/pieza-3.webp |
| 4 | Maceta de barro | 20000 | /assets/pieza-4.webp |
| 5 | Plato del taller | 14000 | /assets/pieza-5.webp |
| 6 | Florero azul | 24000 | /assets/pieza-6.webp |
| 7 | Compotera | 16500 | /assets/pieza-7.webp |
| 8 | Vasito de cerámica | 9000 | /assets/pieza-8.webp |

Es el manifiesto observado en Catalog.java:19–20, trasladado sin cambiar títulos/precios/rutas. No se procesan ni sustituyen fotos en #2. `seed.sql` será la única fuente de esas ocho filas, ejecutada solo durante creación/migración inicial y antes de instalar guardas; nunca al ver un catálogo vacío.

Instalar triggers con nombres explícitos: `protect_default_sessions_insert/update/delete` y `protect_default_products_insert/update/delete`. Tras bootstrap, rechazar INSERT dirigido a DEFAULT, UPDATE si OLD o NEW apunta a DEFAULT y DELETE de DEFAULT, usando RAISE(ABORT,...). Así también se impide mover productos normales a DEFAULT. No dejar flags de mantenimiento desactivables por la aplicación. No se requiere defensa frente a un operador con acceso al archivo que elimine triggers.

En cada arranque v2 verificar las ocho filas (comparación completa, excluyendo ids/timestamps), metadata DEFAULT y presencia/definición de constraints/triggers esperados. Si falta o cambió la semilla: fallo explícito, no reparar ni resembrar silenciosamente. En migración, DEFAULT preexistente válido se conserva; cualquier DEFAULT parcial/alterado impide migrar. Un catálogo normal vacío sigue vacío.

## 5. Arranque, transacciones y concurrencia

Un único inicializador `CatalogDatabaseInitializer`, anterior a cualquier Catalog utilizable y a servir peticiones, es dueño del DDL/migración/seed. Reemplazar `spring.sql.init.mode=always` por `never` y cargar recursos explícitamente desde ese inicializador: evita competir con inicialización automática de Boot. Catalog declara dependencia del inicializador. No usar ApplicationRunner tardío. No agregar Flyway/JPA ni cambiar pom.xml. Los tres recursos SQL usan un separador explícito `;;` entre sentencias completas; los cuerpos de triggers mantienen sus `;` internos. El ejecutor JDBC debe respetar ese separador y participar en la conexión/transacción del inicializador, sin autocommit intermedio.

Configurar el mismo Hikari de Boot en application.properties mediante `spring.datasource.hikari.data-source-properties.foreign_keys=true`, `spring.datasource.hikari.data-source-properties.busy_timeout=5000` y `spring.datasource.hikari.data-source-properties.synchronous=NORMAL`, propiedades del driver SQLite para cada conexión física. Quitar el connection-init-sql anterior para tener una sola fuente de estos PRAGMA; no reemplazar bindings externos NORMA_DB/SPRING_DATASOURCE_URL. Mantener maximum-pool-size=1. El inicializador aplica/lee journal_mode=WAL fuera de transacción en bases de archivo y verifica foreign_keys=1, busy_timeout y synchronous en la conexión. No concatenar múltiples PRAGMA en un connection-init-sql suponiendo que todos se ejecutan. La configuración elegida debe verificarse con el driver 3.49.1.0 instalado, incluso tras reemplazo de conexión. In-memory se admite solo en regresión; no prueba WAL.

Métodos mutantes públicos de Catalog: @Transactional REQUIRED con rollback ante error (también excepciones comprobadas si se introducen), usando la conexión enlazada por Spring. Lectura-modificación-escritura completa en una transacción. La llamada interna de reset a ensure no depende de que Spring intercepte self-invocation: reset ya abre su transacción y ambos usan primitivas privadas comunes. Ningún BEGIN manual dentro de una transacción de Spring. No capturar errores de escritura para devolver éxito.

Operaciones, en orden:

1. **ensure:** INSERT INTO sessions(id) VALUES(?) ON CONFLICT(id) DO NOTHING; si afectó una fila, INSERT INTO products(session_id,piece_number,title,price,image_url,sold) SELECT ?,piece_number,title,price,image_url,sold FROM products WHERE session_id='DEFAULT'; exigir ocho clones. Un conflicto en otro constraint no se oculta con OR IGNORE.
2. **CREATE:** lectura de sesión existente/contador; validar capacidad; insertar n y actualizar contador a n+1 dentro del mismo commit. insert y apply comparten una primitiva para evitar incrementar dos veces.
3. **PRICE/IMAGE/SOLD:** UPDATE con session_id y piece_number, updated_at; exigir una fila. Nunca UPDATE por id global únicamente.
4. **restore:** validar lote completo; leer contador; DELETE WHERE session_id=?; insertar lote sin ids originales; ajustar contador sin disminuir; un fallo revierte delete, inserts y contador.
5. **reset:** ensure si ausente; leer contador; DELETE de productos destino; INSERT SELECT de DEFAULT (ocho filas); UPDATE sesión limpiando temporales/snapshot, contador conservado y timestamp. No hacer DELETE de sessions. Un fallo devuelve todo al estado anterior.
6. **conversation/snapshot:** validar y UPDATE acotado, una fila afectada. Cuando BotEngine abre la transacción exterior, snapshot+apply+limpieza siguen participando en ella sin modificar su código.

Una conexión serializa transacciones del proceso y evita carreras del contador; UNIQUE y FK son segunda defensa. Probar simultaneidad de peticiones del mismo proceso (no builds simultáneos). Mantener una sola instancia de aplicación por archivo. WAL no elimina SQLITE_BUSY; timeout acotado, sin reintentos parciales ni éxito aparente. Multiproceso activo, escalado horizontal, colas y optimización de transacciones de bot durante fallback externo quedan fuera de #2; registrar contención como riesgo, no expandir a #5.

## 6. Migración compatible, backup y recuperación

La migración real aún no se ejecutó. Antes de cualquier ensayo usar fixtures temporales o copia consistente, jamás norma.db de usuario. Antes de migrar una instalación existente: detener todas las instancias y hacer copia de seguridad consistente de la base y medios con proceso detenido y conexiones cerradas, conservando WAL/SHM si existen; no copiar solo el .db de una instancia viva. Registrar ruta/checksum y versión de aplicación. Si no se puede parar o asegurar backup, no migrar esa instalación y elevar a Orca.

Algoritmo del inicializador (misma ruta de archivo configurada):

1. Inspeccionar user_version, sqlite_schema y table_info antes de modificar. Admitir base sin tablas o **únicamente** esquema legacy observado sessions/products (columnas originales, sin versión; sqlite_sequence es objeto interno esperado de AUTOINCREMENT). Base con bot_sessions, solo una tabla, objetos desconocidos que dependan de estas tablas, versión futura o columnas incompatibles: detener con diagnóstico sin borrar/renombrar nada. No prometer compatibilidad con todo esquema ilustrativo.
2. Para legacy, prevalidar ids, tipos/rangos, state, títulos/rutas, sold, duplicados, huérfanos y DEFAULT. No reasignar sesiones ni piezas, no truncar precios, no limpiar borradores, no eliminar filas inválidas. Rechazar huérfanos: no se puede inferir si deben sembrarse. No registrar contenido sensible de borradores.
3. `next_number` positivo válido se normaliza a max(9, valor actual, max(piece_number)+1); tipos no enteros, null, negativos o exceso de rango requieren intervención explícita. Es el único saneamiento automático junto con timestamps ausentes. Preservar bytes de pending/temp_image/snapshot y state. No interpretar snapshots legacy ni afirmar que son válidos para #3.
4. Con conexión exclusiva de inicialización y fuera de transacción, desactivar foreign_keys temporalmente para reconstrucción; iniciar una sola transacción. Crear tablas nuevas con constraints objetivo; copiar ids y columnas, agregar timestamps; comprobar recuentos/valores y contador. Crear/incluir DEFAULT solo si no existía, sin alterar sesiones normales. Usar patrón create-new → copy → drop-old → rename-new, no renombrar primero las tablas viejas. Reconstituir UNIQUE/FK/triggers y preservar sqlite_sequence al menos al máximo anterior para no reciclar surrogate ids históricos.
5. Ejecutar foreign_key_check y quick_check, comparar todos los datos migrados (salvo contador normalizado y nuevos timestamps), verificar semilla exacta y ocho clones en fixture. Establecer user_version=2 **en la misma transacción al final**, commit; restaurar foreign_keys=ON fuera de transacción en finally y releerlo. Ante error hacer rollback total y abortar arranque; no continuar con guardas desactivadas.
6. En instalación nueva: esquema v2+DEFAULT+guardas+versión en una transacción, con verificaciones equivalentes. En v2 ya migrada: validar estructura/semilla y no ejecutar bootstrap otra vez. Dos arranques consecutivos deben preservar datos/contador e ids, incluidas sesiones vacías.

Cambios de journal_mode pueden persistir incluso si falla una migración; no afirmar identidad binaria del archivo tras rollback. La garantía es conservación lógica de tablas/datos/versionado, y backup previo. Recuperación operativa: parar proceso fallido, conservar base fallida para diagnóstico y restaurar backup consistente con versión anterior de aplicación; no ejecutar el binario anterior sobre v2 para “desmigrar”, ni borrar base para arrancar. No hay downgrade automático. Inyección de fallo tras copia y antes de commit debe demostrar rollback con fixture.

Fundamento: [SQLite, reconstrucción segura de tablas](https://www.sqlite.org/lang_altertable.html#otheralter), [PRAGMA y foreign_keys](https://www.sqlite.org/pragma.html#pragma_foreign_keys), [transacciones y escritor único](https://www.sqlite.org/lang_transaction.html) y [Spring Boot, mecanismo único de inicialización](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html). La decisión específica de migrador pequeño sin framework es propuesta para este monolito, no una exigencia de esas fuentes.

## 7. Archivos permitidos tras aprobación

Lista cerrada para construcción #2; cualquier necesidad adicional vuelve a Orca:

- src/main/java/com/norma/taller/Catalog.java
- src/main/java/com/norma/taller/CatalogDatabaseInitializer.java (nuevo)
- src/main/resources/schema.sql (DDL canónico v2, consumido por inicializador)
- src/main/resources/db/seed.sql (nuevo, manifiesto único)
- src/main/resources/db/migrate-v0-v2.sql (nuevo, reconstrucción legacy; coordinación/validación en inicializador)
- src/main/resources/application.properties (solo inicialización y PRAGMA/pool relacionados)
- src/test/java/com/norma/taller/CatalogRepositoryTest.java (nuevo)
- src/test/java/com/norma/taller/CatalogPersistenceTest.java (nuevo)
- src/test/java/com/norma/taller/CatalogMigrationTest.java (nuevo)
- src/test/resources/db/legacy-v0.sql (nuevo, copia exacta del DDL legacy observado)
- docs/decisiones.md (decisiones #2 solo cuando aprobadas)
- docs/operacion.md (backup, migración, contador y límites comprobados)

En esta tarea de diseño solo se crea/versiona docs/orchestration/issue-2-design.md. No editar BotEngine.java, IntentFallback.java, Api.java, IntentParser.java, ApiTest.java, FlowTest.java, AGENTS.md, otros docs/orchestration, fotos, frontend, pom.xml, Docker ni workflows. FlowTest se ejecuta intacto como regresión; si falla y arreglar exige conversación, informar a Orca para #3, sin maquillar el resultado. No añadir a Git los archivos locales ajenos para lograr que compile una exportación limpia.

## 8. Tareas de construcción secuenciales y puertas

Orca debe aprobar versión 1.0 y sus decisiones (especialmente contador tras reset, DEFAULT, nombres actuales y migración restringida) por escrito antes de despachar. Cada tarea usa el formato AGENTS y reporta archivos, comandos, resultado y límites; no comienza hasta aprobación de su predecesora.

| Orden | Tarea atómica para Antigravity | Entrega y puerta de aceptación |
|---|---|---|
| B1 | Esquema v2, seed, propiedades por conexión e inicializador con migración | Fixtures nuevo/legacy/v2; constraints, guardas, PRAGMA por conexión y fallos/rollback comprobados; revisión Orca |
| B2 | ensure, validación de entradas y clonación SQL en Catalog | Semilla exacta/inmutable; sesiones aisladas; ensure repetido, vacío y concurrente pasan; revisión Orca |
| B3 | Mutaciones, contador, restore y reset transaccionales | Asignación estable, no-op rechazado, validación cruzada y fallos a mitad de operación sin efectos parciales; regresión FlowTest intacta; revisión Orca |
| B4 | Persistencia real/reinicio y documentación de decisiones/operación | Suite de archivo/reinicio, verify y exportación limpia; evidencia con recuentos; revisión Orca y auditoría Codex |

No se autoriza #3 mientras #2 tenga criterios pendientes. El cierre y cualquier push corresponden a Orca con autorización y evidencia, no al builder.

## 9. Pruebas exactas exigidas

Todas las bases en directorios temporales independientes, sin fixtures binarios ni acceso a proveedores STT/TTS. Tests del repositorio llaman Catalog a través del bean proxied de Spring, no new Catalog y no una transacción del test que oculte el commit. Las pruebas de fallo deben comprobar desde otra transacción que nada cambió. Usar JDBC real SQLite y no mocks para atomicidad/constraints.

| ID / suite | Escenario y aserción necesaria |
|---|---|
| R1 Repository | Base nueva tiene DEFAULT exacto de ocho filas. ensure(A), ensure(B): números/títulos/precios/rutas/sold iguales, ids independientes, next=9; cambiar precio/foto/sold/crear en A no cambia B ni DEFAULT |
| R2 Repository | ensure repetido no duplica; restore(A,[]) conserva fila sessions y contador; ensure/all devuelven vacío. Todos sold también producen catálogo público vacío sin reseed. Reset explícito sí repone ocho |
| R3 Repository | Alta #9 y #10, vender/restaurar/quitar #9 no renumera #10; restore de snapshot anterior y reset no disminuyen next; alta posterior #11. Sesión nueva sigue empezando #9 |
| R4 Repository | PRICE/IMAGE/SOLD solo cambian campo objetivo; destino inexistente y kind desconocido fallan sin alterar estado. Precio 0/negativo, título/ruta vacíos, número duplicado, sold=2 y número inválido fallan; SQL directo REAL/no-numérico no satisface CHECK. Límite máximo del contador falla sin overflow |
| R5 Repository | Cada método público rechaza sesión inválida/DEFAULT, incluyendo all/session/conversation/snapshot/insert/apply/restore/reset. SQL directo INSERT/UPDATE/DELETE de DEFAULT y cambio de session_id hacia DEFAULT fallan por trigger; DEFAULT sigue idéntico |
| R6 Repository | restore mixto con Product de B o números duplicados falla antes de borrar A. Lista vacía válida. Inyectar fallo SQL después del primer insert (trigger temporal solo en fixture) durante ensure, restore y reset; ni fila nueva parcial ni borrado parcial ni limpieza de borrador/cambio de contador |
| R7 Repository | Dos tareas sincronizadas con barrera invocan ensure del mismo id: una sesión, ocho productos. Varias altas concurrentes en una sesión obtienen números únicos, contador final coherente; otra sesión no cambia. Executor con timeout y cierre, sin otro proceso Maven |
| P1 Persistence | Archivo temporal: abrir contexto A, ensure sesión, cambiar precio/foto/sold, alta #9 y otra sesión vacía; guardar estado/pending/temp_image/snapshot por primitivas. Cerrar contexto y pool por completo, abrir B en mismo archivo: igualdad de datos, contador y temporales, vacío permanece vacío tras ensure |
| P2 Persistence | Arrancar JAR en proceso separado con --spring.datasource.url=jdbc:sqlite:<archivo temporal>, --norma.uploads=<temporal> y puerto temporal; GET crea sesión, POST reset funciona y DEFAULT no es accesible. Parar de forma ordenada y relanzar sobre mismo archivo; GET preserva sesión y no reinicializa. Para mutación y vacío, fixture JDBC preparado con proceso parado; comparar DB antes/después del segundo proceso |
| P3 Persistence | PRAGMA journal_mode=wal en archivo, foreign_keys=1, busy_timeout=5000, synchronous=NORMAL en conexión efectiva. Cerrar/renovar conexión física y repetir; huérfano SQL rechazado. No aceptar memoria como evidencia WAL |
| M1 Migration | Fixture legacy poblado, una sesión vacía, vendidos y JSON/estado pendientes; migrar a v2 preservando ids/campos, normalizando contador bajo y agregando DEFAULT. Segundo arranque sin cambios. Ejecutar foreign_key_check vacío y quick_check=ok |
| M2 Migration | Legacy con DEFAULT exacto conserva filas/ids; DEFAULT parcial o alterado falla sin reparar. DB nueva crea semilla una vez; v2 con semilla dañada o guardas faltantes falla sin reseed |
| M3 Migration | Legacy inválido (huérfanos, tipos/rangos, estados, ids) o versión/esquema desconocido falla con datos/version intactos; no borrar ni añadir sesiones para ocultarlo. Inyectar fallo tras copia antes de commit: tablas originales y versión se conservan, nuevo arranque recupera; FK vuelve a ON |
| G1 Regresión | FlowTest intacto y suite versionada completa; ApiTest local se puede ejecutar e informar por separado, nunca contarlo como prueba de exportación versionada ni modificarlo |

Comandos previstos, **no ejecutados en esta entrega** (Orca/builder fijan JAVA_HOME a JDK25 real y confirman java/mvn -version):

1. `mvn -B -Dtest=CatalogMigrationTest test` después de B1.
2. `mvn -B -Dtest=CatalogRepositoryTest test` después de B2/B3.
3. `mvn -B -Dtest=FlowTest test` tras B3, sin omitir fallas.
4. `mvn -B -Dtest=CatalogPersistenceTest test` y `mvn -B verify` en secuencia tras B4.
5. Después de commits de construcción autorizados: exportación `git archive HEAD` a directorio temporal, ejecutar allí `mvn -B verify` y smoke P2 sobre JAR exportado. Nunca exportar sobre el workspace. Registrar commit/hash, JDK, comandos, recuentos y exit codes; contrastar árbol local contra contenido versionado. Si P2 requiere empaquetado, ejecutarlo tras package/verify y con fixture/harness aislado, no desde fase test que espera un JAR aún inexistente.

No ejecutar Maven simultáneamente, no `-DskipTests`, no excluir fallas ajenas para declarar verde. P2 es evidencia de reinicio de proceso; P1 demuestra además todas las mutaciones y persistencia de metadatos sin depender del diseño FSM pendiente. Tests unitarios que solo repiten SQL no sustituyen pruebas de archivo/rollback.

## 10. Riesgos, alternativas y límites

- Sin base real inspeccionada no se garantiza migración de instalaciones desconocidas. Estrategia: reconocer estrictamente legacy observado y abortar ante incompatibilidad; alternativa de borrar/recrear rechazada por pérdida de datos.
- Estado/snapshot se conserva como texto opaco. No se certifica deshacer ni confirmación correctos por su existencia; revisión FSM #3 deberá tratar snapshots inválidos, compatibilidad y consumo único sin disminuir numeración.
- Una conexión limita throughput y el BotEngine actual puede sostener transacción durante fallback externo. No se afirma latencia universal ni ausencia de contención; mover trabajo externo requiere diseño posterior.
- DDL pequeño coordinado por inicializador evita incorporar otro stack; más versiones futuras podrían justificar un migrador dedicado. No admitir scripts automáticos destructivos sin backup y validación.
- `synchronous=NORMAL` preserva integridad transaccional habitual en WAL, pero no se promete ausencia de pérdida de commits recientes ante corte de energía. Backup y volumen persistente siguen siendo necesarios; despliegue #8 pendiente.
- Reset recupera contenido original, conserva high-water y limpia historial; no restaura ids SQL ni borra archivos. Retención/limpieza de medios se diseña después para no romper snapshots.
- Timestamps internos y constraints pueden descubrir datos antes admitidos; fallar y elevar incompatibilidad es preferible a transformar silenciosamente datos del usuario. FK requiere prueba por conexión, no solo PRAGMA de inicio.
- No hay pruebas ejecutadas de este diseño ni implementación autorizada. Pendiente: validación de Orca, construcción secuencial y auditoría con evidencia antes de aceptar #2.
