# Reporte de Construcción y Verificación — Issue #3: Conversación confirmada y deshacer (v1.1)

**Fecha:** 2026-11-13  
**Implementador:** Antigravity (Software Builder)  
**Diseño de referencia:** Codex v1.0 (`docs/orchestration/issue-3-design.md`)  
**Aprobación de diseño:** Orca (`docs/orchestration/issue-3-approval.md`)  
**Auditoría de referencia:** Codex v1.0 (`docs/orchestration/issue-3-audit.md`)
**Entorno de ejecución:** OpenJDK 25.0.4.1 (Microsoft Build 25.0.4.1+1-LTS) en Linux x86_64, Apache Maven 3.8.7

---

## 1. Resumen de Correcciones tras Auditoría (Hallazgos F1–F7)

En cumplimiento de las instrucciones de Orca y los hallazgos de auditoría de Codex sobre el commit `8f39458`, se implementaron las siguientes correcciones divididas en tres bloques con compuertas de validación:

### Bloque 1 — Parser y Gramática Argentina (F1 y F2) — Aprobado por Orca (`msg_8a901582d09e`)
- **F1 (Extracción de importes y comandos compuestos)**:
  - Manejo estricto de referencias decimales (ej. `2.5`): no se truncan silenciosamente como identificadores de pieza enteros (`#2`).
  - Detección de colisiones contradictorias entre número explícito y nombre (ej. `#2` vs `Jarrón 3`) reportando `Ambiguous` en lugar de asumir arbitrariamente el número.
  - Corrección de múltiples montos (ej. `#2 a 15000 por 20000`): se detecta ambigüedad en lugar de seleccionar silenciosamente el último importe.
  - Comandos múltiples concatenados por punto y coma (ej. `precio 20000; vendida`): se rechaza o marca ambiguo, sin ejecutar ciegamente la última acción.
- **F2 (Normalización léxica y números hablados)**:
  - Soporte completo de números hablados compuestos según reglas canónicas (`cien`/`ciento`, `dieciocho mil quinientos`, etc.).
  - Preservación íntegra de títulos con prefijos descriptivos argentinos (`Esta es Jarra`, `Estrella`), normalizando únicamente espacios repetidos internos sin mutilar el texto del usuario.
  - Retirada de afirmaciones fuera de diseño sobre sintaxis informal (como `15k`/`15 lucas` que no forman parte de la especificación contractual).
- **Suite:** `IntentParserTest.java` (53 pruebas pasando, 100% PASS).

### Bloque 2 — Motor Conversacional, Codec e Invariantes (F3, F4 y F5) — Aprobado por Orca (`msg_e4a64d21b8a6`)
- **F3 (Manejo de borrador en `AWAITING_DETAILS` y FSM)**:
  - En estado `AWAITING_DETAILS`, el motor ahora procesa correcciones explícitas de atributos (`PRICE`, `TITLE`, `TARGET`, `IMAGE`).
  - Una foto enviada a un borrador incompleto de `ChangeImage` avanza automáticamente a `AWAITING_CONFIRMATION`.
  - Protección del borrador ante comandos incompatibles o fotos huérfanas: se conserva el borrador preexistente sin sobreescribirlo ni descartarlo silenciosamente.
  - Flujo foto seguida de `#2`: determina unívocamente `ChangeImage` para la pieza `#2` en lugar de crear un borrador erróneo de `CREATE`.
  - Respuestas confirmatorias o negativas acompañadas de foto (`sí` o `no` con foto adjunta): se aclara la conservación del estado sin aplicar mutaciones espurias.
  - Prioridad determinista de repregunta `explainMissingField` conforme a §3: Operación → Destino → Título → Precio → Imagen.
- **F4 (Codec de Sesión, Invariantes y Snapshot Histórico)**:
  - Rechazo estricto de envelopes de borrador vacíos `{}` devolviendo `Optional.empty()`.
  - Validación estructural de campos incompatibles por operación en envelopes v1 (rechazo de `SOLD` con precio/título/imagen, `PRICE` con título/imagen, etc.).
  - Validación conjunta de invariantes de estado y pending: `IDLE` no puede contener draft/pending; `AWAITING_DETAILS` no puede contener una acción lista para aplicar; `AWAITING_CONFIRMATION` requiere obligatoriamente una acción completa válida.
  - `BotSessionCodec.decodeSnapshot` preserva de manera exacta y sin modificaciones los espacios en blanco de títulos y URLs de imagen históricos, eliminando el `trim()` destructivo.
- **F5 (Límites de Entrada, Formato y Claridad de Mensajes)**:
  - Validación temprana de longitud máxima de mensaje (> 1000 caracteres): se rechaza inmediatamente antes de invocar `ensureSession` o tocar la base de datos SQL (aplicable tanto a texto directo como a transcripciones de audio).
  - Los mensajes de confirmación formatean los precios bajo convención argentina con separador de miles y prefijo (`$ 18.500`) e incluyen número de pieza y título canónico (`#2 (Jarrón de gres)`).
  - La resolución de destinos ambiguos lista claramente los números y títulos de todas las piezas candidatas.
- **Suites:** `BotSessionCodecTest.java` (15 pruebas pasando) y `BotEngineTest.java` (23 pruebas pasando).

### Bloque 3 — Integración HTTP, Persistencia Real y Cierre de Scope (F6 y F7)
- **F6 (Eliminación de workaround en `Api.java` y pruebas HTTP)**:
  - Se retiró la comprobación condicional nula en `MediaConfig.addResourceHandlers`, restituyendo el registro de producción limpio.
  - En `BotApiTest.java`, se sustituyó el mock de Spring por un stub `@TestConfiguration` con un directorio temporal real para `media.root`, eliminando advertencias de JDK 25 y garantizando la inicialización idéntica a producción.
  - Incorporación de pruebas para envíos combinados de foto y texto multipart, así como foto seguida de detalles con transcripción simulada.
- **F7 (Persistencia real multiciclo en `BotPersistenceTest`)**:
  - Reescritura completa de `BotPersistenceTest.java` (D1): prueba de integración real utilizando `SpringApplicationBuilder` con proxies y pool Hikari completos sobre un archivo SQLite persistente (`norma_persist_test.db` en `@TempDir`).
  - Se valida el ciclo de vida a través de 5 reinicios independientes de aplicación (cerrando los contextos de Spring entre etapas):
    1. Creación de borrador en `AWAITING_DETAILS`.
    2. Completitud de datos y transición a `AWAITING_CONFIRMATION`.
    3. Confirmación atómica, avance de `nextNumber` y persistencia de snapshot.
    4. Rollback (`undo`) tras reinicio, verificando la restauración completa del catálogo, consumo del slot y preservación del high-water mark.
    5. Aislamiento estricto de sesiones concurrentes y preservación de la plantilla `DEFAULT`.
- **Suites:** `BotApiTest.java` (6 pruebas pasando) y `BotPersistenceTest.java` (1 prueba de integración pasando).

---

## 2. Cobertura de Criterios de Aceptación (§10)

| ID | Criterio | Estado | Evidencia de Validación |
|---|---|---|---|
| **P1** | Parser: límites de precios, rechazo de overflow, decimales y residuos | Cumplido | `IntentParserTest`: 53 pruebas parametrizadas verificando rango hasta `Integer.MAX_VALUE`, rechazo de decimales, coma flotante y palabras de comando integradas en títulos |
| **P2** | Destino único vs ambiguo / vendido / inexistente | Cumplido | `IntentParserTest`, `BotEngineTest.testResolveTargetSingleVsAmbiguous` |
| **F1** | CREATE, PRICE, IMAGE, SOLD aplican exclusivamente tras confirmación | Cumplido | `BotEngineTest.testOperationsOnlyOnConfirm` |
| **F2** | FSM en los 3 estados y soporte de corrección de campos en DETAILS | Cumplido | `BotEngineTest.testFsmStatesAndCorrections`, `testFsmCorrectionPriceTargetOnly` |
| **F3** | Doble confirmación y no-op no duplican ni corrompen historial | Cumplido | `BotEngineTest.testDuplicateConfirmAndNoOp` |
| **U1** | Deshacer de todas las operaciones (CREATE, PRICE, IMAGE, SOLD), consumo de slot único, no reciclado de numeración | Cumplido | `BotEngineTest.testUndoAllOperations`, `testUndoImageOperation`, `testUndoOnlyRevertsLastOperation`, `testUndoDuringDraftPreservesOrIgnores` |
| **J1** | Envelope v1, rechazo estricto de borrador vacío, campos incompatibles y recuperación de legacy | Cumplido | `BotSessionCodecTest` (15 pruebas), `BotEngineTest.testCorruptStateRecovery` |
| **T1** | Rollback atómico verificado con trigger SQL real sin transacción envolvente exterior | Cumplido | `BotEngineTest.testAtomicTransactionRollback` (demostración de fallo con trigger real e igualdad íntegra de `Session` y `Product` en conexión limpia) |
| **T2** | Concurrencia real (doble sí, doble undo, confirm vs reset) sin estados parciales | Cumplido | `BotEngineTest.testConcurrentRaces` (ejecución concurrente multihilo con `CyclicBarrier` y timeouts) |
| **D1** | Persistencia real en archivo SQLite, reinicios de pool y undo entre sesiones | Cumplido | `BotPersistenceTest.testPersistenceAcrossRestarts` (5 ciclos independientes de Spring Boot / Hikari) |
| **A1** | Contratos de API: rechazo 400 de texto+audio, foto+audio, foto+texto multipart, STT fallido, TTS null | Cumplido | `BotApiTest` (6 pruebas MockMvc pasando) |
| **G1** | Preservación íntegra de suites previas y clean verify completo sin mocks de SQL | Cumplido | 115 pruebas pasando en exportación limpia (`117` localmente incluyendo `ApiTest` no versionado) |

---

## 3. Matriz de Ejecución de Pruebas

Comando de verificación:
`JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1 PATH=/home/gonzalocar/.jdks/ms-25.0.4.1/bin:$PATH mvn -B clean verify`

### Conteo Real de Pruebas

- **Pruebas versionadas en el repositorio (115 pruebas en total, 0 fallos, 0 errores, 0 ignoradas):**
  - Suites heredadas de Issue #2 y arquitectura previa (17 pruebas):
    - `CatalogMigrationTest`: 3 pruebas
    - `CatalogPersistenceTest`: 3 pruebas
    - `CatalogRepositoryTest`: 7 pruebas
    - `FlowTest`: 4 pruebas
  - Nuevas suites de Issue #3 conforme a diseño Codex v1.0 (98 pruebas):
    - `IntentParserTest`: 53 pruebas
    - `BotSessionCodecTest`: 15 pruebas
    - `BotEngineTest`: 23 pruebas
    - `BotPersistenceTest`: 1 prueba (multietapa de integración)
    - `BotApiTest`: 6 pruebas
- **Pruebas locales en entorno de desarrollo (117 pruebas):**
  - Incluye `ApiTest.java` (2 pruebas), archivo no versionado preservado intacto fuera de git para evitar acoplamientos con otras issues.

| Suite de Prueba | Pruebas Ejecutadas | Fallos | Errores | Ignoradas | Tiempo |
|---|---|---|---|---|---|
| `CatalogMigrationTest` | 3 | 0 | 0 | 0 | ~2.6 s |
| `CatalogPersistenceTest` | 3 | 0 | 0 | 0 | ~1.4 s |
| `CatalogRepositoryTest` | 7 | 0 | 0 | 0 | ~6.1 s |
| `BotPersistenceTest` | 1 | 0 | 0 | 0 | ~5.8 s |
| `BotApiTest` | 6 | 0 | 0 | 0 | ~7.0 s |
| `IntentParserTest` | 53 | 0 | 0 | 0 | ~0.5 s |
| `FlowTest` | 4 | 0 | 0 | 0 | ~1.2 s |
| `BotSessionCodecTest` | 15 | 0 | 0 | 0 | ~0.1 s |
| `BotEngineTest` | 23 | 0 | 0 | 0 | ~1.2 s |
| *(ApiTest - local sin seguimiento)* | 2 | 0 | 0 | 0 | ~2.3 s |
| **TOTAL (Versionado en Export)** | **115** | **0** | **0** | **0** | **~26 s** |
| **TOTAL (Espacio local)** | **117** | **0** | **0** | **0** | **~36 s** |

*Nota aclaratoria sobre reportes previos:* El reporte inicial indicaba incorrectamente 88 pruebas locales y una tabla con 99 ítems debido a conteos preliminares antes de la formalización de la matriz completa. El presente conteo (115 versionados / 117 locales) refleja con exactitud la totalidad de casos ejecutados en el árbol limpio.

---

## 4. Archivos Modificados e Incorporados (Scope §9 Estricto)

```text
Modificados:
  src/main/java/com/norma/taller/Api.java
  src/main/java/com/norma/taller/BotEngine.java
  src/main/java/com/norma/taller/BotModel.java
  src/main/java/com/norma/taller/BotSessionCodec.java
  src/main/java/com/norma/taller/IntentParser.java
  src/test/java/com/norma/taller/BotApiTest.java
  src/test/java/com/norma/taller/BotEngineTest.java
  src/test/java/com/norma/taller/BotPersistenceTest.java
  src/test/java/com/norma/taller/BotSessionCodecTest.java
  src/test/java/com/norma/taller/IntentParserTest.java
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

## 5. Higiene de Código y Verificación de Exportación Limpia

1. **Higiene de espacios en blanco:** `git diff --check` ejecutado sin advertencias ni errores de formato.
2. **Exportación limpia (`git archive`):**
   - El árbol versionado se exportó limpiamente excluyendo archivos sin seguimiento (`IntentFallback.java`, `ApiTest.java`, documentos de coordinación locales).
   - Se ejecutó `JAVA_HOME=... mvn -B clean verify` en el directorio de exportación, confirmando la ejecución exitosa de las 115 pruebas con código de retorno `0`.
3. **No se realizaron operaciones de push remoto ni cierre de issues**, quedando el repositorio listo para la auditoría de conformidad técnica por parte de Codex.
