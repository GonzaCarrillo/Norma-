# Reporte de Construcción y Verificación — Issue #3: Conversación confirmada y deshacer (v1.3)

**Fecha:** 2026-11-13
**Implementador:** Antigravity (Software Builder)
**Diseño de referencia:** Codex v1.0 (`docs/orchestration/issue-3-design.md`)
**Aprobación de diseño:** Orca (`docs/orchestration/issue-3-approval.md`)
**Auditorías de referencia:** Codex v1.0, Addendum v1.1 y Addendum v1.2 (`docs/orchestration/issue-3-audit.md`)
**Entorno de ejecución:** OpenJDK 25.0.4.1 (Microsoft Build 25.0.4.1+1-LTS) en `/home/gonzalocar/.jdks/ms-25.0.4.1`, Apache Maven 3.8.7 en `/usr/share/maven`, Linux x86_64

---

## 1. Resumen de Correcciones tras Reauditoría v1.2 (R1, R2 y Erratas Documentales)

En cumplimiento de las instrucciones de Orca para la resolución atómica de los hallazgos de la reauditoría Codex v1.2 sobre `902bc75`, se ejecutaron las correcciones de código en `BotEngine.java` e `IntentParser.java`, junto con sus pruebas de regresión asociadas y la subsanación de erratas documentales:

### R1 — Ambigüedad por Nombre Duplicado en Corrección de Destino
- **Ubicaciones:** `BotEngine.java:293–305` y `BotEngine.java:571–585`.
- **Diagnóstico v1.2:** Los handlers de corrección `TARGET` por String filtraban títulos disponibles coincidentes y utilizaban `.findFirst()`, seleccionando arbitrariamente la primera coincidencia (ej. #3 ante duplicado con #9) en lugar de exigir cardinalidad 1 y aclarar si hay varias piezas con el mismo nombre.
- **Implementación conforme:**
  - En ambos handlers (`handleCorrectionInConfirmation` y `handleCorrectionInDetails`), se obtienen todos los productos disponibles que coinciden con el título normalizado (`norm(p.title()).equals(sNorm)`).
  - Si la lista tiene cardinalidad 1, se aplica unívocamente el `pieceNumber`.
  - Si la lista tiene cardinalidad > 1, se preserva íntegro el estado de la sesión, la acción o borrador pendiente y el snapshot previo en base de datos sin mutaciones parciales, respondiendo con la enumeración descriptiva de candidatos: `"Hay varias piezas que coinciden (" + String.join(", ", candidateDescs) + "). Decime el numerito."`.
  - Si no hay coincidencias, se responde `"No encuentro esa pieza disponible. Decime otro numerito."` preservando el estado intacto.
  - La corrección subsiguiente `no, la pieza #9` actualiza el destino a 9, conservando la operación y monto fijados y repreguntando la confirmación (`¿Confirmás cambiar el precio de la pieza #9 (Taza panzona) a $25.000? Decime sí o no.`).
- **Regresiones incorporadas en `BotEngineTest.java` (30 pruebas pasando, 100% PASS):**
  - `testDuplicateNameCorrectionInConfirmationRequiresPieceNumberClarification`: alta de #9 "Taza panzona", inicio de cambio de precio en #2, corrección `no, la pieza Taza panzona` aclara candidatos (#3 y #9) sin resolver arbitrariamente; aclaración `no, la pieza #9` confirma y muta únicamente #9 a $25.000, dejando #3 ($12.000) y #2 ($18.500) intactos.
  - `testDuplicateNameCorrectionInDetailsRequiresPieceNumberClarification`: en `AWAITING_DETAILS`, corrección con título duplicado en minúsculas repregunta candidatos y conserva borrador; aclaración `no, la pieza #9` solicita precio y completa el flujo hacia #9.
  - `testUniqueNameCorrectionResolvesDirectly`: control positivo con título único (`Jarrón de gres`) resuelve directamente a #2.

### R2 — Consumo Completo de Expresión de Precio y Rechazo de Residuos
- **Ubicación:** `IntentParser.java:735–775`.
- **Diagnóstico v1.2:** `extractPricePortion` filtraba fragmentos con `isPriceCandidate`, descartando silenciosamente tramos no numéricos o residuos ajenos a la gramática (`nada`), produciendo acciones `Complete` no autorizadas ante frases como `Cambiá el precio de #2 a 15000 por nada` o `... a nada por 15000`.
- **Implementación conforme:**
  - Se distingue el sustantivo `"el precio"` (cuando está precedido por `el`, `del`, `al`, `su`) para que no sea tratado como un delimitador espurio de importe.
  - Tras identificar delimitadores válidos (`a`, `por`, `precio`), se extraen todos los fragmentos no vacíos. Si existe más de un fragmento o si coexisten un monto y un residuo no reconocido, la expresión se marca como múltiple/inválida (`multipleAmounts = true`), resultando en `Invalid(MULTIPLE_ACTIONS)` sin producir acción `Complete` y preservando el borrador íntegro.
  - Se conservan todos los controles positivos contractuales: importes numéricos directos, agrupados con puntos (`18.500`), multiplicadores y coloquiales (`15 mil`, `15 lucas`, `1 luca`, `quince lucas`, `quince mil pesos`, `dieciocho quinientos`).
  - En `extractPieceReferences`, se condicionó la inspección del token `y` para que sólo considere continuación numérica si precede a unidades (1..9) tras una decena, evitando que `pieza 1 y pieza 2` descarte falsamente la primera referencia.
- **Regresiones incorporadas en `IntentParserTest.java` (61 pruebas pasando, 100% PASS):**
  - `testPriceExpressionRejectsResidualChunksBeforeAndAfterValidAmount`: verifica el rechazo formal de `Cambiá el precio de #2 a 15000 por nada` y `... a nada por 15000` como `Invalid(MULTIPLE_ACTIONS)`, validando además los controles positivos con `a 15000`, `por 15000` y `por 15 lucas`.

### Subsanación de Erratas Documentales de v1.2
1. **SHA de commit previo rectificado:** El SHA largo de la entrega v1.2 (`4f2eb56`) corresponde efectivamente a `4f2eb56951ba7793b9c758670d23829651ad50bc`.
2. **Descripción de empaquetado del JAR:** Se corrige la denominación a **47 archivos `.class` y 19 recursos** (totalizando 66 archivos bajo `BOOT-INF/classes`), verificados con 0 diferencias byte a byte frente a `target/classes`.

---

## 2. Cobertura de Criterios de Aceptación (§10)

| ID | Criterio | Estado | Evidencia de Validación |
|---|---|---|---|
| **P1** | Parser: límites de precios, rechazo de overflow, decimales, residuos y montos múltiples | Cumplido | `IntentParserTest`: 61 pruebas pasando, incluyendo rechazo de residuos no autorizados (`testPriceExpressionRejectsResidualChunksBeforeAndAfterValidAmount`), montos compuestos y límites de rango |
| **P2** | Destino único vs ambiguo / vendido / inexistente | Cumplido | `IntentParserTest`: detección de ambigüedad nominal exacta y referencias múltiples contradictorias (`testConflictingReferences`); `BotEngineTest`: aclaración de duplicados en confirmación y detalles |
| **F1** | CREATE, PRICE, IMAGE, SOLD aplican exclusivamente tras confirmación | Cumplido | `BotEngineTest.testOperationsOnlyOnConfirm` |
| **F2** | FSM en los 3 estados, corrección de campos y protección de borradores | Cumplido | `BotEngineTest`: 30 pruebas pasando, incluyendo `testDuplicateNameCorrectionInConfirmationRequiresPieceNumberClarification` y `testDuplicateNameCorrectionInDetailsRequiresPieceNumberClarification` |
| **F3** | Doble confirmación y no-op no duplican ni corrompen historial | Cumplido | `BotEngineTest.testDuplicateConfirmAndNoOp` |
| **U1** | Deshacer de todas las operaciones (CREATE, PRICE, IMAGE, SOLD), consumo de slot único, no reciclado de numeración | Cumplido | `BotEngineTest.testUndoAllOperations`, `testUndoImageChange`, `testTwoCommitsAndUndoSecondOnly`, `testSnapshotPreservedAcrossNoOpCancelPrepare`, `testUndoDuringDraftWithAndWithoutSnapshot`, `testEmptyCatalogUndo` |
| **J1** | Envelope v1, rechazo estricto de borrador vacío, campos incompatibles y roundtrip estricto | Cumplido | `BotSessionCodecTest` (15 pruebas), `BotEngineTest.testCorruptStateRecovery`, `testPriceDraftUnexpectedPhotoDoesNotPersistIncompatibleImageUrl` |
| **T1** | Rollback atómico verificado con trigger SQL real sin transacción envolvente exterior | Cumplido | `BotEngineTest.testAtomicTransactionRollback` (verificación de excepción SQL e igualdad íntegra de `Session` y `Product` en conexión nueva) |
| **T2** | Concurrencia real (doble sí, doble undo, confirm vs reset) sin estados parciales | Cumplido | `BotEngineTest.testConcurrentRaces` (ejecución multihilo con `CyclicBarrier`, verificación de high-water mark y catálogo íntegro frente a `DEFAULT`) |
| **D1** | Persistencia real en archivo SQLite, reinicios de pool y undo entre sesiones | Cumplido | `BotPersistenceTest.testPersistenceAcrossSpringContextRestarts` (5 ciclos independientes de Spring Boot / Hikari) |
| **A1** | Contratos de API: rechazo 400 de texto+audio, foto+audio, foto+texto multipart, STT fallido, TTS null | Cumplido | `BotApiTest` (6 pruebas MockMvc pasando) |
| **G1** | Preservación íntegra de suites previas y clean verify completo en exportación limpia | Cumplido | 130 pruebas ejecutadas y aprobadas en exportación limpia (`132` localmente incluyendo `ApiTest` no versionado) |

---

## 3. Matriz de Ejecución de Pruebas y Evidencia de Artefactos

### Ejecución en Directorio de Exportación Limpia (`/tmp/norma-issue3-export-v12`)

- **Commit exportado:** `cdabcae` (`cdabcaefddc7db2eb5c2482fe3f5dc1b2975e390`)
- **Comando:** `cd /tmp/norma-issue3-export-v12 && JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1 PATH=/home/gonzalocar/.jdks/ms-25.0.4.1/bin:$PATH mvn -B clean verify`
- **Código de salida:** `EXIT_CODE=0`
- **Log de ejecución completo:** `/tmp/norma-issue3-export-v12/clean-verify.log` (SHA-256: `f0d446b055a51db0f1863dcc731c643ab66436ff11c411d4bd2280ebfb33813c`)
- **JAR generado:** `/tmp/norma-issue3-export-v12/target/taller-1.0.0.jar` (SHA-256: `07718bb9d856645652cd14d4c2d1e275c4bc82731326e1ce9e663cdcb1d31e7d`)
- **Verificación de empaquetado:** 47 clases `.class` y 19 recursos (66 archivos totales bajo `BOOT-INF/classes`) verificados byte a byte contra `target/classes` (0 discrepancias).

| Suite de Prueba | Pruebas Ejecutadas | Fallos | Errores | Ignoradas | Tiempo | SHA-256 Reporte XML (basedir `/tmp/norma-issue3-export-v12`) |
|---|---|---|---|---|---|---|
| `CatalogMigrationTest` | 3 | 0 | 0 | 0 | 1.69 s | `3005c5f38b10b22474ec01cedd83335f7cbd4417399dd263967142a8034b5992` |
| `CatalogPersistenceTest` | 3 | 0 | 0 | 0 | 1.09 s | `40551afb86c5a2436bea052f3d00dee755b1e340c99ef9889769cfc78953b0f0` |
| `CatalogRepositoryTest` | 7 | 0 | 0 | 0 | 4.69 s | `29aa2a241ef5d15e023602bfe239c8a156367c144f3e43accfc4e04792d48b08` |
| `FlowTest` | 4 | 0 | 0 | 0 | 1.29 s | `1b16327f0e94a8b3c9eb5bee927fc753583295fbf924cbafd5bfc4cf01e96ae0` |
| `BotPersistenceTest` | 1 | 0 | 0 | 0 | 4.41 s | `5d281575b1d2280b2f52ebaa4adf3630deb6cc0b2d4d652f3d4d3e671cfb2067` |
| `BotApiTest` | 6 | 0 | 0 | 0 | 4.51 s | `de869d25595c6e3b3751bd3d6981241b53cd130306764bc69d6fa60026a53956` |
| `IntentParserTest` | 61 | 0 | 0 | 0 | 0.51 s | `fbef1b5f7a8c8ac82247b8549bd554d98187b02b93a150ec18046dafe9842fd0` |
| `BotSessionCodecTest` | 15 | 0 | 0 | 0 | 0.08 s | `d735fe68a99805428972f956b8349b4cc62a040d427ed2022b8178b819914710` |
| `BotEngineTest` | 30 | 0 | 0 | 0 | 1.34 s | `da3c58e836b5195b985edf0c598037ccb320fccef08cacfa3e56b76fd0351323` |
| **TOTAL (Exportación versionada)** | **130** | **0** | **0** | **0** | **~35.8 s (BUILD SUCCESS)** | — |
| *(ApiTest - local sin seguimiento)* | 2 | 0 | 0 | 0 | 1.70 s | *(solo presente en workspace local)* |
| **TOTAL (Espacio local)** | **132** | **0** | **0** | **0** | **~38.4 s** | — |

---

## 4. Archivos Modificados e Incorporados (Scope §9 Estricto)

```text
Modificados en esta iteración (v1.3):
  src/main/java/com/norma/taller/BotEngine.java
  src/main/java/com/norma/taller/IntentParser.java
  src/test/java/com/norma/taller/BotEngineTest.java
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

1. **Commit de implementación probado y verificado en exportación:** `cdabcae` (`cdabcaefddc7db2eb5c2482fe3f5dc1b2975e390`), que implementa las correcciones R1 y R2 y sus 4 nuevas pruebas de regresión.
2. **Diferencia documental posterior:** La actualización del presente informe (`docs/orchestration/issue-3-build-report.md`) constituye el único cambio subsiguiente, registrando las mediciones y hashes verificados de la exportación limpia en `/tmp/norma-issue3-export-v12`.
3. **Higiene de formato:** `git diff --check` ejecutado sin errores de formato ni espacios en blanco finales.
4. **No se realizaron operaciones de push remoto ni cierre de issues**, quedando el repositorio disponible para la reauditoría técnica final de Codex.
