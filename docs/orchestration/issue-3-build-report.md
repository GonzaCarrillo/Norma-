# Reporte de Construcción y Verificación — Issue #3: Conversación confirmada y deshacer (v1.2)

**Fecha:** 2026-11-13
**Implementador:** Antigravity (Software Builder)
**Diseño de referencia:** Codex v1.0 (`docs/orchestration/issue-3-design.md`)
**Aprobación de diseño:** Orca (`docs/orchestration/issue-3-approval.md`)
**Auditorías de referencia:** Codex v1.0 (`docs/orchestration/issue-3-audit.md`) y Addendum v1.1 (`docs/orchestration/issue-3-audit.md#addendum-v11`)
**Entorno de ejecución:** OpenJDK 25.0.4.1 (Microsoft Build 25.0.4.1+1-LTS) en `/home/gonzalocar/.jdks/ms-25.0.4.1`, Apache Maven 3.8.7 en `/usr/share/maven`, Linux x86_64

---

## 1. Resumen de Correcciones tras Reauditoría v1.1 (F1–F4 y F7/G1)

En cumplimiento de las instrucciones de Orca y los hallazgos de la reauditoría Codex v1.1 sobre `f387257`, se completaron las correcciones pendientes en tres bloques secuenciales con compuertas formales de validación:

### Bloque 1 — Parser y Gramática Argentina (F1 y F2) — Aprobado por Orca (`msg_5c5b9dca688c`)
- **F1 (Manejo estricto de referencias decimales y tokens compuestos)**:
  - Referencias decimales con acento o coma (`la número 2.5`, `pieza 2,5`, `#2.5`, `# 2,5`, `la numero 2,5`): detección explícita sobre texto crudo antes de normalización, retornando `Invalid(DECIMAL_OR_CENTAVOS)` sin truncar silenciosamente al identificador entero.
  - Frases numéricas habladas como referencias a piezas: ampliación de la ventana de análisis hasta 8 palabras e inspección de palabras residuales (`y`, decenas, unidades, dígitos) para evitar truncar expresiones inválidas o parciales. En particular, `ciento treinta y dos` consume los cuatro tokens y resuelve a la pieza 132 completa (verificado con fixture que contiene `#130` y `#132`).
  - Detección exhaustiva de montos compuestos (`isPriceCandidate`) en `extractPricePortion`: mensajes con múltiples intentos de precio, incluyendo combinaciones donde el primer monto es decimal (`#2 a 15,50 por 20000`), negativo (`-15000`) o supera el límite intmax (`2147483648`), son rechazados como `Invalid(MULTIPLE_ACTIONS)` sin descartar el primer fragmento ni elegir silenciosamente el último.
  - Detección de acciones múltiples coordinadas por delimitadores o coordinación explícita (`y`/`Y` o punto y coma, ej. `Cambiá el precio de #2 a 20000 Y sacá la pieza 2`) rechazada como `Invalid(MULTIPLE_ACTIONS)`.
  - En `resolveTarget`, limpieza exhaustiva de preposiciones y palabras residuales (`de`, `la`, `el`, etc.) tras extraer el número de pieza. Se elimina la falsa contradicción con `Maceta de barro` ante `Cambiá el precio de #2`, preservando unívocamente el destino 2 y solicitando el precio faltante.
- **F2 (Prefijos de comandos y títulos intactos)**:
  - En `createPattern`, extracción del prefijo de comando (`Publicá`, `Esta es`, etc.) exactamente una vez al inicio del mensaje, preservando íntegramente títulos capturados como `Es amor` ante `Publicá Es amor a 15000`.
  - Se ratifica el soporte contractual de expresiones coloquiales argentinas como `15 lucas`, `quince lucas`, `1 luca` y `una luca` (que resuelven a 15000 y 1000 respectivamente), aclarando que `15k` no forma parte de la gramática contractual aprobada.
- **Suite:** `IntentParserTest.java` (60 pruebas pasando, 100% PASS).

### Bloque 2 — Motor Conversacional, Codec e Invariantes (F3 y F4) — Aprobado por Orca (`msg_a0e51ce7dda8`)
- **F3 (Correcciones explícitas y protección de borradores parciales)**:
  - Corrección de destino `#N` (`no, la pieza #3`, `la pieza #3`, `el número es 3` o nombre único) en `AWAITING_CONFIRMATION` y `AWAITING_DETAILS`: procesada conforme a la sintaxis contractual aprobada, actualizando el target, preservando el precio establecido y repreguntando la confirmación correspondiente.
  - Protección de borrador ante `CREATE` parcial incompatible sin foto: un mensaje como `Publicá Jarra a 20000` sin foto recibido mientras existe un borrador `Publicá Plato a 15000` es detectado por `isDraftIncompatible` (comparando títulos y precios conocidos), protegiendo el borrador previo íntegro y advirtiendo el conflicto sin mezclar datos.
  - Secuencia `Cambiá el precio de #2` seguido de `no, dije 25000`: al conservar el destino 2 en el paso inicial, la corrección de precio completa exitosamente la acción `ChangePrice(2, 25000)` quedando lista para confirmar.
- **F4 (Invariantes de operación y roundtrip estricto de codec)**:
  - Protección estricta contra fotos inesperadas en operaciones que no admiten imagen (`PRICE` y `SOLD`): si el usuario envía una foto en `AWAITING_DETAILS`, el motor nunca almacena `imageUrl` en el borrador ni en `temp_image`. Se preserva el borrador válido intacto y se reiteran los datos faltantes, evitando que un mensaje vacío posterior sufra un wipe espurio a `IDLE` por rechazo del codec.
  - Generación de payloads de borrador (`Draft`) con invariantes estructurales por operación (`PRICE` sin título/imagen, `SOLD` sin título/precio/imagen, `IMAGE` sin título/precio, `CREATE` sin target), garantizando roundtrip de codec estricto en cada mutación.
- **Suites:** `BotEngineTest.java` (27 pruebas pasando) y `BotSessionCodecTest.java` (15 pruebas pasando).

### Bloque 3 — Integración, Cierre de Evidencia y Exportación Limpia (F7 y G1)
- **F7 (Corrección factual del informe y métodos reales)**:
  - Se corrigieron los nombres de los métodos de prueba en la matriz y el reporte para coincidir exactamente con los identificadores reales del código fuente.
  - Se aclara que el total local previo de 88 pruebas sí fue observado empíricamente en la v1.0, debiéndose la discrepancia de la tabla 99 a un error aritmético de redacción previa.
  - Se ratifica la plena vigencia de `BotPersistenceTest` (D1) mediante 5 reinicios reales de Spring Boot / Hikari con proxies sobre base de datos física temporal SQLite.
- **G1 (Evidencia limpia atribuible y empaquetado)**:
  - Se realizó el commit de código `4f2eb56` (`4f2eb568f9ef298e3b1c6d3bc8527a050f757e23`).
  - Se exportó dicho commit vía `git archive` a un directorio nuevo e independiente: `/tmp/norma-issue3-export-v11`.
  - Se compararon todos los 56 archivos del árbol versionado contra el commit de git (0 discrepancias). Se constató la exclusión de archivos no versionados (`IntentFallback.java`, `ApiTest.java`).
  - Se ejecutó secuencialmente `mvn -B clean verify` en el directorio de exportación con captura de log completo, exit code `0`, SHA del JAR generado y verificación byte a byte de clases empaquetadas.

---

## 2. Cobertura de Criterios de Aceptación (§10)

| ID | Criterio | Estado | Evidencia de Validación |
|---|---|---|---|
| **P1** | Parser: límites de precios, rechazo de overflow, decimales y montos múltiples | Cumplido | `IntentParserTest`: 60 pruebas parametrizadas verificando rango hasta `Integer.MAX_VALUE`, rechazo de decimales con punto/coma, y montos compuestos (`testCompoundAmountsWithInvalidFirstAmountRejected`) |
| **P2** | Destino único vs ambiguo / vendido / inexistente | Cumplido | `IntentParserTest.testContradictoryNumberAndNameRequiresClarification`, `testDecimalReferencesWithAccentOrCommaRejected`, `testSpokenNumberFull132Consumed`, `testChangePriceOfTargetWithoutPriceDoesNotCreateFalseContradiction` |
| **F1** | CREATE, PRICE, IMAGE, SOLD aplican exclusivamente tras confirmación | Cumplido | `BotEngineTest.testOperationsOnlyOnConfirm` |
| **F2** | FSM en los 3 estados, corrección de campos y protección de borradores | Cumplido | `BotEngineTest.testFsmTransitionsAndCorrections`, `testCorrectionTargetHash3InConfirmation`, `testPartialCreateConflictWithoutPhotoProtectsDraft`, `testChangePriceTargetThenCorrectionPriceCompletes` |
| **F3** | Doble confirmación y no-op no duplican ni corrompen historial | Cumplido | `BotEngineTest.testDuplicateConfirmAndNoOp` |
| **U1** | Deshacer de todas las operaciones (CREATE, PRICE, IMAGE, SOLD), consumo de slot único, no reciclado de numeración | Cumplido | `BotEngineTest.testUndoAllOperations`, `testUndoImageChange`, `testTwoCommitsAndUndoSecondOnly`, `testSnapshotPreservedAcrossNoOpCancelPrepare`, `testUndoDuringDraftWithAndWithoutSnapshot`, `testEmptyCatalogUndo` |
| **J1** | Envelope v1, rechazo estricto de borrador vacío, campos incompatibles y roundtrip estricto | Cumplido | `BotSessionCodecTest` (15 pruebas), `BotEngineTest.testCorruptStateRecovery`, `testPriceDraftUnexpectedPhotoDoesNotPersistIncompatibleImageUrl` |
| **T1** | Rollback atómico verificado con trigger SQL real sin transacción envolvente exterior | Cumplido | `BotEngineTest.testAtomicTransactionRollback` (verificación de excepción SQL e igualdad íntegra de `Session` y `Product` en conexión nueva) |
| **T2** | Concurrencia real (doble sí, doble undo, confirm vs reset) sin estados parciales | Cumplido | `BotEngineTest.testConcurrentRaces` (ejecución multihilo con `CyclicBarrier`, verificación de high-water mark y catálogo íntegro frente a `DEFAULT`) |
| **D1** | Persistencia real en archivo SQLite, reinicios de pool y undo entre sesiones | Cumplido | `BotPersistenceTest.testPersistenceAcrossSpringContextRestarts` (5 ciclos independientes de Spring Boot / Hikari) |
| **A1** | Contratos de API: rechazo 400 de texto+audio, foto+audio, foto+texto multipart, STT fallido, TTS null | Cumplido | `BotApiTest` (6 pruebas MockMvc pasando: `testSimultaneousTextAndAudioReturns400`, `testMultipartPhotoAndAudioWithStub`, `testSttFailurePreservesState`, `testTtsNullStillDeliversReplyText`, `testMultipartPhotoAndTextTogether`, `testPhotoFollowedByDetailsAndConfirmWithTtsNull`) |
| **G1** | Preservación íntegra de suites previas y clean verify completo en exportación limpia | Cumplido | 126 pruebas ejecutadas y aprobadas en exportación limpia (`128` localmente incluyendo `ApiTest` no versionado) |

---

## 3. Matriz de Ejecución de Pruebas y Evidencia de Artefactos

### Ejecución en Directorio de Exportación Limpia (`/tmp/norma-issue3-export-v11`)

- **Comando:** `cd /tmp/norma-issue3-export-v11 && JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1 PATH=/home/gonzalocar/.jdks/ms-25.0.4.1/bin:$PATH mvn -B clean verify`
- **Código de salida:** `EXIT_CODE=0`
- **Log de ejecución completo:** `/tmp/norma-issue3-export-v11/clean-verify.log` (SHA-256: `967da6e2665967fa6d273ae00a7b8c84f49df8e7b8a6f5be99035893163a2c6f`)
- **JAR generado:** `/tmp/norma-issue3-export-v11/target/taller-1.0.0.jar` (SHA-256: `db5367895ccc85ffbe6e3420756b5782ef72df23cbdabf1ee8cc7bbce3dd3040`)
- **Verificación de empaquetado:** 66 entradas de clase en `BOOT-INF/classes` verificadas byte a byte contra `target/classes` (0 discrepancias).

| Suite de Prueba | Pruebas Ejecutadas | Fallos | Errores | Ignoradas | Tiempo | SHA-256 Reporte XML (basedir `/tmp/norma-issue3-export-v11`) |
|---|---|---|---|---|---|---|
| `CatalogMigrationTest` | 3 | 0 | 0 | 0 | 1.99 s | `c9c45c1e1c802136e5a0e0400590d805093c3376f479d298aa223db7aacb7b8e` |
| `CatalogPersistenceTest` | 3 | 0 | 0 | 0 | 1.09 s | `d714ed634d1717c247dc370d31f9ba859deac3f5ec2b2ad8333e7fcbe98c77a7` |
| `CatalogRepositoryTest` | 7 | 0 | 0 | 0 | 4.48 s | `fe5f5850230a830eddd720cfdd4848fedff12f56b0ee605714dd9a3cc61ac4df` |
| `FlowTest` | 4 | 0 | 0 | 0 | 1.47 s | `dcac07b7f0100676471621ebd55bd0eb82d3c74b05c67f35fab83ce6ee8f1ae1` |
| `BotPersistenceTest` | 1 | 0 | 0 | 0 | 4.81 s | `0aab6b19fa87fd5ffa6d9118125c8f1f7aed854de5dab2dd4ee23979ff190989` |
| `BotApiTest` | 6 | 0 | 0 | 0 | 4.36 s | `1238be3c090f8127ac3d6d11d2d1cb2b66bd2cb33a98b76726a4c8b96c6ac6e7` |
| `IntentParserTest` | 60 | 0 | 0 | 0 | 0.55 s | `ee311cc1b4593500374b1d0990aa177a8fe1419168203446588293abd95c860d` |
| `BotSessionCodecTest` | 15 | 0 | 0 | 0 | 0.09 s | `239b99eda79f7d2adf8dbe84916af796074f10b45ebf3aabb7641e5cb9e2d3c4` |
| `BotEngineTest` | 27 | 0 | 0 | 0 | 1.26 s | `fe6ac382ee393e9e695631a691188f24a8e14468da94832cdb638c8f27fd1375` |
| **TOTAL (Exportación versionada)** | **126** | **0** | **0** | **0** | **~40 s (BUILD SUCCESS)** | — |
| *(ApiTest - local sin seguimiento)* | 2 | 0 | 0 | 0 | 1.70 s | *(solo presente en workspace local)* |
| **TOTAL (Espacio local)** | **128** | **0** | **0** | **0** | **~29 s** | — |

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
  src/main/resources/db/schema.sql
  src/main/resources/db/seed.sql
  src/main/java/com/norma/taller/Catalog.java
  src/main/java/com/norma/taller/CatalogDatabaseInitializer.java
  src/main/java/com/norma/taller/MediaService.java
  src/main/java/com/norma/taller/IntentFallback.java
  src/test/java/com/norma/taller/ApiTest.java
  src/test/java/com/norma/taller/FlowTest.java
  src/test/java/com/norma/taller/Catalog*Test.java
```

---

## 5. Trazabilidad de Commits e Higiene

1. **Commit de implementación probado:** `4f2eb56` (`4f2eb568f9ef298e3b1c6d3bc8527a050f757e23`), que incorpora los cambios de lógica en `IntentParser`, `BotEngine` y las nuevas pruebas de regresión.
2. **Diferencia documental posterior:** La actualización del presente documento (`docs/orchestration/issue-3-build-report.md`) constituye el único cambio subsiguiente, registrando las mediciones y hashes verificados de la exportación limpia.
3. **Higiene de formato:** `git diff --check` ejecutado sin errores de formato ni espacios en blanco finales.
4. **No se realizaron operaciones de push remoto ni cierre de issues**, quedando el repositorio disponible para la reauditoría técnica final de Codex.
