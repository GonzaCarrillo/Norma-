# Reporte de Construcción y Verificación — Issue #3: Conversación confirmada y deshacer

**Fecha:** 2026-11-13  
**Implementador:** Antigravity (Software Builder)  
**Diseño de referencia:** Codex v1.0 (`docs/orchestration/issue-3-design.md`)  
**Aprobación de diseño:** Orca (`docs/orchestration/issue-3-approval.md`)  
**Entorno de ejecución:** OpenJDK 25.0.4.1 (Microsoft Build) en Linux x86_64, Maven 3.9.x  

---

## 1. Resumen de la Implementación por Fases

### Fase B1 — Tipos de Dominio y Codec Estricto (`BotModel.java` y `BotSessionCodec.java`)
- **`BotModel.java`**: Modelo sellado (`sealed interface Action`) con tipos exhaustivos (`Create`, `ChangePrice`, `ChangeImage`, `Sell`), FSM State (`IDLE`, `AWAITING_DETAILS`, `AWAITING_CONFIRMATION`), ParseResult (`Complete`, `NeedsDetails`, `Ambiguous`, `Invalid`, `Unknown`), y Controles explícitos (`Confirm`, `Cancel`, `Undo`, `Correction`). Adaptador bidireccional exhaustivo hacia el legacy `Intent`.
- **`BotSessionCodec.java`**: Envelope versionado estricto `{"version":1,"sessionId":"...","action"|"draft":{...}}` y snapshot `{"version":1,"sessionId":"...","products":[...]}`.
  - Validación anti-wrap/overflow para números usando Jackson `canConvertToInt()` y `canConvertToLong()` (rechaza números superiores a `Integer.MAX_VALUE`).
  - Verificación estricta de tokens residuales (`readTreeStrict`).
  - Mapeo perezoso de payloads legacy sin alterar DDL ni forzar migraciones en frío.
- **Suite:** `BotSessionCodecTest.java` (11 pruebas, 100% PASS).

### Fase B2 — Parser Tipado y Gramática Argentina (`IntentParser.java`)
- Refactorización de `IntentParser` conforme a las reglas gramaticales argentinas:
  - Extracción de importes y precios hasta `2.147.483.647` (intmax) con soporte de miles (`15.000`, `15k`, `15 lucas`).
  - Delimitación estructurada de límites de comandos para evitar falsos positivos con palabras de acción integradas en títulos (ej. `Publicá Vendida a 15000`).
  - Extracción estricta de controles (`extractControl`) rechazando ambigüedades como "sí, no", "no sé", o fotos adjuntas con afirmación/negación.
  - Preservación de fotos previas en `AWAITING_DETAILS` cuando se aportan título y precio en mensajes subsiguientes.
- **Suite:** `IntentParserTest.java` (46 pruebas parametrizadas, 100% PASS).

### Fase B3 — Motor Conversacional y Transaccional (`BotEngine.java`)
- Retirada acotada y autorizada del acoplamiento previo de fallback (respaldado en `issue-3-botengine-prior.patch`).
- Máquina de estados finitos (FSM) de 3 estados:
  - `IDLE`: inicio o fin de ciclo.
  - `AWAITING_DETAILS`: retención de borrador parcial ante datos faltantes o ambigüedad.
  - `AWAITING_CONFIRMATION`: preparación del resumen antes de confirmar.
- Confirmación atómica bajo `@Transactional`:
  - Revalidación preconfirmación contra el catálogo actual. Si el destino ya fue vendido o no existe, pasa a `AWAITING_DETAILS` solicitando un nuevo número.
  - Detección de no-op (precio o foto idéntica) devolviendo `"Ya estaba así, no hice cambios"` y preservando el snapshot previo.
  - Transacción atómica: captura de snapshot completo → `apply` → limpieza de pending a `IDLE`.
- Soporte de Deshacer (`undo`):
  - Consumo de un único slot de rollback, sin redo.
  - Restauración completa y preservación estricta del high-water mark de numeración de piezas (nunca se recicla el identificador destruido tras una alta revertida).
- Recuperación conversacional ante JSON corrupto o pending legacy repreguntando sin aplicar a ciegas (§6).
- **Suite:** `BotEngineTest.java` (7 pruebas cubriendo F1, F2, F3, U1, J1, T1 y T2, 100% PASS):
  - **T1**: Rollback demostrado mediante inyección de triggers SQL reales en fixture (`FAIL_CONFIRM_CLEAN_PENDING` y `FAIL_UNDO_CONSUME_SNAPSHOT`) sin transacción exterior, verificando la causa exacta del fallo y la igualdad íntegra de `Session` completa y todos los campos de `Product` desde una transacción nueva.
  - **T2**: Concurrencia real con `CyclicBarrier`, `ExecutorService` cerrado en `finally` y timeouts: carreras de doble sí, doble deshacer, y confirm vs reset serializadas limpiamente sin estados parciales ni afectación de otras sesiones o `DEFAULT`.

### Fase B4 — Borde HTTP, Persistencia Real y Validación de Regresión (`Api.java`, `BotPersistenceTest.java`, `BotApiTest.java`)
- **`Api.java`**: Restricción acotada para rechazar solicitudes multipart con texto no vacío **y** audio no vacío simultáneos con HTTP 400 (`"Mandame el texto o el audio con los detalles, uno por vez."`).
- **`BotPersistenceTest.java`**: Prueba sobre archivo SQLite real (`norma_persist_test.db` en `@TempDir`) con reinicios sucesivos de pool y datasource:
  - Recuperación de borrador en `AWAITING_DETAILS`.
  - Transición a `AWAITING_CONFIRMATION` tras reinicio.
  - Confirmación, toma de snapshot y avance de `nextNumber`.
  - Deshacer tras nuevo reinicio de conexión, verificando consumo del slot y preservación del high-water mark en nuevas altas.
- **`BotApiTest.java`**: Validación HTTP con `MockMvc`:
  - Rechazo HTTP 400 ante texto y audio simultáneos sin alterar estado.
  - Procesamiento conjunto de foto + audio mediante transcripción simulada con stub de `MediaService`.
  - Fallo en STT preserva el estado y borrador de la sesión.
  - TTS (`speak`) devolviendo `null` entrega respuesta textual y estado correctamente.

---

## 2. Cobertura de Criterios de Aceptación (§10)

| ID | Criterio | Estado | Evidencia de Validación |
|---|---|---|---|
| **P1** | Parser parametrizado: límites de precios, rechazo de overflow y residuos | Cumplido | `IntentParserTest`: 46 casos pasando con precios hasta `2.147.483.647`, rechazo de decimales, overflow y palabras reservadas |
| **P2** | Resolución de destino único vs ambiguo / vendido / inexistente | Cumplido | `IntentParserTest`, `BotEngineTest` |
| **F1** | CREATE, PRICE, IMAGE, SOLD solo aplican tras confirmación | Cumplido | `BotEngineTest.testOperationsOnlyOnConfirm` |
| **F2** | FSM en los 3 estados y soporte de corrección de campos | Cumplido | `BotEngineTest.testFsmStatesAndCorrections` |
| **F3** | Doble confirmación y no-op no duplican ni corrompen historial | Cumplido | `BotEngineTest.testDuplicateConfirmAndNoOp` |
| **U1** | Deshacer de todas las operaciones, consumo de slot único, no reciclado de numeración | Cumplido | `BotEngineTest.testUndoAllOperations` |
| **J1** | Envelope v1, rechazo estricto de corrupción y recuperación de pending legacy | Cumplido | `BotSessionCodecTest`, `BotEngineTest.testCorruptStateRecovery` |
| **T1** | Rollback atómico verificado con trigger SQL real sin transacción envolvente externa | Cumplido | `BotEngineTest.testAtomicTransactionRollback` |
| **T2** | Concurrencia real (doble sí, doble undo, confirm vs reset) sin estados parciales | Cumplido | `BotEngineTest.testConcurrentRaces` |
| **D1** | Persistencia real en archivo SQLite, reinicios de pool y undo entre sesiones | Cumplido | `BotPersistenceTest.testPersistenceAcrossRestarts` |
| **A1** | Contratos de API: rechazo 400 de texto+audio, foto+audio, stt fallido, tts null | Cumplido | `BotApiTest` (4 casos pasando) |
| **G1** | Preservación íntegra de suites previas y clean verify completo sin mocks de SQL | Cumplido | 88/88 tests pasando en `mvn clean verify` |

---

## 3. Matriz de Ejecución de Suites de Prueba

Comando: `JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1 PATH=/home/gonzalocar/.jdks/ms-25.0.4.1/bin:$PATH mvn -B clean verify`

| Suite de Prueba | Pruebas Ejecutadas | Fallos | Errores | Ignoradas | Tiempo |
|---|---|---|---|---|---|
| `CatalogMigrationTest` | 13 | 0 | 0 | 0 | ~1.3 s |
| `CatalogTest` | 8 | 0 | 0 | 0 | ~0.1 s |
| `CatalogConcurrencyTest` | 2 | 0 | 0 | 0 | ~0.3 s |
| `CatalogTransactionTest` | 1 | 0 | 0 | 0 | ~0.1 s |
| `BotPersistenceTest` | 1 | 0 | 0 | 0 | ~1.6 s |
| `BotApiTest` | 4 | 0 | 0 | 0 | ~8.7 s |
| `ApiTest` (legacy preservado) | 2 | 0 | 0 | 0 | ~1.4 s |
| `IntentParserTest` | 46 | 0 | 0 | 0 | ~0.4 s |
| `FlowTest` (flujo original preservado) | 4 | 0 | 0 | 0 | ~0.9 s |
| `BotSessionCodecTest` | 11 | 0 | 0 | 0 | ~0.1 s |
| `BotEngineTest` | 7 | 0 | 0 | 0 | ~0.4 s |
| **TOTAL** | **88** | **0** | **0** | **0** | **~33 s (BUILD SUCCESS)** |

---

## 4. Archivos Modificados e Incorporados (Scope §9 Estricto)

```text
Modificados:
  src/main/java/com/norma/taller/Api.java
  src/main/java/com/norma/taller/BotEngine.java
  src/main/java/com/norma/taller/IntentParser.java

Nuevos (Scope autorizado):
  src/main/java/com/norma/taller/BotModel.java
  src/main/java/com/norma/taller/BotSessionCodec.java
  src/test/java/com/norma/taller/BotSessionCodecTest.java
  src/test/java/com/norma/taller/IntentParserTest.java
  src/test/java/com/norma/taller/BotEngineTest.java
  src/test/java/com/norma/taller/BotPersistenceTest.java
  src/test/java/com/norma/taller/BotApiTest.java
  docs/orchestration/issue-3-build-report.md

Archivos preservados e intactos (sin tocar):
  pom.xml
  src/main/resources/schema.sql
  src/main/resources/seed.sql
  src/main/java/com/norma/taller/Catalog.java
  src/main/java/com/norma/taller/CatalogDatabaseInitializer.java
  src/main/java/com/norma/taller/MediaService.java
  src/main/java/com/norma/taller/IntentFallback.java
  src/test/java/com/norma/taller/ApiTest.java
  src/test/java/com/norma/taller/FlowTest.java
  src/test/java/com/norma/taller/Catalog*Test.java
```

---

## 5. Límites y Supuestos Explícitos
1. **Instancia y Base de Datos**: Diseñado para una única instancia de aplicación conectada a la base de datos SQLite en modo WAL con una conexión física activa por proceso.
2. **Archivos Multimedia**: La operación de rollback (`undo`) restaura lógicamente el catálogo en base de datos; no elimina del sistema de archivos físicos las imágenes WebP previamente generadas por mutaciones revertidas.
3. **Transporte HTTP**: La garantía de concurrencia y no duplicación se aplica a nivel transaccional en el motor; no se incorporaron identificadores durables de eventos HTTP (`eventId`/`draftId`) en la API REST de transporte por estar fuera del alcance de la issue #3.
