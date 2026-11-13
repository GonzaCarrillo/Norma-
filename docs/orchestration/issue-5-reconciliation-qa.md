# Auditoría QA independiente — Issue #5 (Fase 3 final, integral)

**Dictamen: QA_APPROVED**
**Commit auditado:** `0ebcf1873a1df5a9aea321c9c9efa4cd7ac62ae8` (HEAD, rama `GonzaCarrillo/reconcile-5-voice`)
**Base unificada:** merge limpio de `origin/main` (`8cdacbe`, Issue #6 SSE cerrado y aprobado) en `55ba012`, sobre H1 (`c920ed3`) y H2/H3 (`06f86f7`)
**Diseño de referencia:** `docs/orchestration/issue-5-fallback-design-v3.md` v3.1 (Codex v3.1 aprobado para despacho — gate de integración en Api), `docs/orchestration/issue-5-design.md` v1.0, `docs/orchestration/issue-5-reconciliation-qa-plan.md` v2
**Informe de builder atendido:** `docs/orchestration/issue-5-reconciliation-build.md` (Antigravity)
**Plan QA:** `docs/orchestration/issue-5-reconciliation-qa-plan.md` v2
**Rama:** `GonzaCarrillo/reconcile-5-voice`
**Fecha:** 2026-11-13

## Alcance e identidad

Esta auditoría integral de Fase 3 evalúa el commit final `0ebcf18` de la issue #5, que integra H1 (fallback de intenciones) en `Api.java` bajo el contrato Codex v3.1, sobre la base ya verificada de H2/H3. Los hallazgos H2 y H3 fueron acreditados en auditorías previas sobre `b5fd45a` (commits `b5fd45a` y `06f86f7`) y se conservan con trazabilidad; no se vuelve a invocar al proveedor TTS ni a Groq sin necesidad.

Scope de escritura: únicamente `docs/orchestration/issue-5-reconciliation-qa.md` y la evidencia propia bajo `issue-5-reconciliation-evidence/qa/final/`. No se modifica producción, pruebas, reportes del builder ni documentos de diseño. Los informes previos de Copilot sobre `bc69036` y mi reauditoría sobre `b5fd45a` (directorio `issue-5-reconciliation-evidence/qa/b5fd45a/`) se conservan sin modificar; cada entrada distingue su SHA.

## Entorno y verificación independiente

- Linux x86_64, kernel `6.14.0-37-generic`.
- Microsoft OpenJDK `25.0.4.1` (build 25.0.4.1+1-LTS); Apache Maven `3.8.7`.
- `JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1`; bytecode versión 69 (Java 25 estándar, sin preview flags).
- Ejecución sobre checkout limpio de `0ebcf18` (`git status` vacío).
- Comando:

```bash
export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1
export PATH=$JAVA_HOME/bin:$PATH
mvn -B clean verify \
  -DargLine='-Djava.io.tmpdir=/tmp/opencode/qa-final-tmp'
```

**Resultado real:** exit code `0`, `BUILD SUCCESS`; 19 suites, 243 pruebas, 0 fallos, 0 errores y 0 omitidas. Surefire emitió el mensaje "Surefire is going to kill self fork JVM" tras el cierre de pruebas (comportamiento esperado del fork JVM, sin impacto); Maven terminó con éxito y exit code 0. No se ocultó ni se excluyó ninguna prueba. `java.io.tmpdir` aislado en `/tmp/opencode/qa-final-tmp` (fuera de `target/`); `java.version=25.0.4.1` corroborado en los XML de Surefire. `edge-tts` no está en PATH; no se llamó a Groq ni a proveedores externos; no se consumieron secretos.

| Artefacto | SHA-256 |
|---|---|
| Log QA (`issue-5-reconciliation-evidence/qa/final/verify.log`) | `fea0a9312a7ae2c7a6004a4122d0ac5b0a0d4de46660186305a3f3053d928258` |
| Resumen Surefire (`issue-5-reconciliation-evidence/qa/final/surefire-summary.txt`) | `449b5afc8265badf7a1e22741902f04941e13feb63006f41e76d7cf21271ee22` |
| JAR reconstruido (`target/taller-1.0.0.jar`) | `43c39f5a015782273f917eb3485f17c61e085b31c0ad99bdc94119d6c5beeabc` |
| Commit auditado | `0ebcf1873a1df5a9aea321c9c9efa4cd7ac62ae8` |

Nota sobre SHAs: el JAR recompilado por esta auditoría (`43c39f5a...`) no coincide byte a byte con el JAR del builder (`63064226...`, `issue-5-reconciliation-evidence/builder/jar-sha256.txt`). Se registra la diferencia sin atribuir equivalencia de empaquetado, criterio idéntico al usado en auditorías previas. La correspondencia de pruebas sí es exacta: builder declara 243 pruebas y esta auditoría verificó 243/243 verdes sobre el mismo commit `0ebcf18`.

## Matriz de aceptación y hallazgos

| Criterio | Resultado | Evidencia y límite |
|---|---|---|
| H-1 — Fallback de intenciones integrado en Api | PASS | `IntentFallback` cableado en `Api.message` bajo diseño v3.1; 21 unitarias (`IntentFallbackTest`) + 6 integración (`IntentFallbackApiTest`) verdes. |
| H-1 — JSON estricto / evidencia literal | PASS | `STRICT_DUPLICATE_DETECTION`, exactamente 4 campos, todos `isTextual`, rechazo de claves extra/duplicadas/trailing/coerción; `original.contains(...)` para cada evidencia. |
| H-1 — Gating: IDLE && pending==null && control None && interpret Unknown | PASS | `Api.java` líneas 40-44: cuádruple evaluada antes de `fallback.normalize`. |
| H-1 — Sin LLM ante Complete/NeedsDetails/Ambiguous/Invalid | PASS | `interpret(...) instanceof BotModel.Unknown` es condición de activación; matriz v3.1 §3 lo prohíbe para los otros cuatro. |
| H-1 — Sin LLM ante confirm/cancel/undo | PASS | `extractControl(...) instanceof BotModel.None`; `IntentFallbackTest.rechazaControlesConversacionales` verifica `verifyNoInteractions(client)` para `si/sí/confirmo/dale/no/cancela/deshacer/me equivoque`. |
| H-1 — Normalización solo propone; catálogo cambia tras `sí` explícito | PASS | `IntentFallbackApiTest.flujocompletoFallbackConConfirmacion`: catálogo se queda en 8 productos hasta `"si"` y pasa a 9 tras confirmación; `IntentFallback` no muta DB. |
| H-1 — Red fuera de transacción real | PASS | `IntentFallbackApiTest` verifica `TransactionSynchronizationManager.isActualTransactionActive() == false` dentro de `normalize`; `normalize` se invoca antes de `bot.process` y fuera de `@Transactional`. |
| H-1 — Sesiones A/B aisladas | PASS | `IntentFallbackApiTest.aislamientoEntreSesiones`: sesión B permanece IDLE con 8 productos. |
| H-1 — Fallback vacío / error / sin clave preserva respuesta original | PASS | `IntentFallbackApiTest.degradacionSeguraCuandoFallbackRetornaEmpty` (200, mensaje nativo del bot, sin HTTP 500); `IntentFallbackTest` clave ausente/401/429/500/timeout → `Optional.empty()` sin red. |
| H-1 — No URL de modelo ni prompt injection confirma por sí mismo | PASS | `IntentFallback` nunca devuelve URLs (prompt lo prohíbe); señales de control rechazadas sin consultar al LLM; `bot.process` es la única autoridad de mutación. |
| H-2 — Audio corrupto 400/422 distinto de 401/429/5xx | PASS | `MediaService.transcribe` captura `RestClientResponseException`; 400/422 → "No pude escuchar el audio"; 401/429/5xx/timeout → "No puede transcribir ahora" sin exponer proveedor. |
| H-2 — Limpieza STT bajo `.processing` | PASS | Temporales `.webm` bajo `root/.processing`; `finally { Files.deleteIfExists(temp) }`; `assertProcessingDirectoryEmpty()` en cada prueba STT. |
| H-3 — Síntesis TTS real acreditada (trazabilidad) | PASS | `edge-tts 7.3.1` voz `es-AR-TomasNeural`; `sample-voice.mp3` + `tts-probe.json` en `builder/`; ffprobe verificado previamente (mp3, 24 kHz, mono, 4.392 s, 26352 B, SHA `aa801018...`). No se repite proveedor. |
| H-3 — Degradación no bloqueante sin edge-tts | PASS | `speak` devuelve `null` si binario ausente/fallo; `Api.message` fallback a `speechSynthesis` del navegador. |
| R1 — Regresión completa (incl. SSE #6) | PASS | 243/243 verdes; `StorefrontSseTest` y `StorefrontContractTest` (SSE #6, commit `8cdacbe`) verdes; `computeIfPresent` preservado en `Api.removeEmitter`. |

## Verificación detallada de H1 (fallback de intenciones)

### Contrato de integración en `Api.java`
Verificado contra el diseño v3.1 §2:
- Constructor `@Autowired Api(Catalog, BotEngine, MediaService, IntentParser, IntentFallback)` y adaptador de 3 argumentos con `fallback = null` (sin red en pruebas aisladas). ✓
- Pipeline: `Catalog.valid(sessionId)` → rechazo texto+audio (400) → límite 1000 caracteres → `media.transcribe` → `media.image` → `catalog.ensure` → `sess`/`products` → cuádruple → `fallback.normalize` → sustitución de `text` solo si `Optional.present` → `bot.process` → `publish(sessionId)` → `Reply(replyText, media.speak(replyText), state)`. ✓
- `IntentFallback.normalize` invocado **antes** de `bot.process` y fuera de cualquier `@Transactional`; red Groq sin retención de locks SQLite. ✓

### JSON estricto y evidencia literal (`IntentFallback.java`)
- `ObjectMapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)`; `jp.nextToken() == null` descarta basura tras el cierre. ✓
- `root.size() == 4` y campos exactamente `{operation, targetEvidence, priceEvidence, titleEvidence}`. ✓
- Todos los valores `isTextual` (rechaza coerción entero→string). ✓
- `operation` en `{CREATE, PRICE, IMAGE, SOLD}`. ✓
- Cada evidencia no vacía verificada con `original.contains(...)` (anti-alucinación). ✓
- Reconstrucción canónica re-validada con `parser.interpret(...)` exigiendo `BotModel.Complete` con la acción esperada. ✓

### Inmunidad y degradación (`IntentFallbackTest`, 21 pruebas)
- Controles conversacionales (`si, sí, confirmo, dale, no, cancela, deshacer, me equivoque`) rechazados **sin llamar al LLM** (`verifyNoInteractions(client)`). ✓
- Clave en blanco → `Optional.empty()` sin interacciones de red. ✓
- HTTP 401/429/500 y `ResourceAccessException` (timeout) → `Optional.empty()`, degradación segura. ✓

### Integración HTTP (`IntentFallbackApiTest`, 6 pruebas)
- Flujo completo con confirmación: fallback activa, `AWAITING_CONFIRMATION` sin mutar catálogo (8 productos), `"si"` muta a 9 productos y vuelve a `IDLE` sin segunda llamada al LLM. ✓
- `TransactionSynchronizationManager.isActualTransactionActive() == false` dentro de `normalize`. ✓
- Comandos deterministas, controles y sesiones no-`IDLE` nunca invocan al fallback. ✓
- Fallback `Optional.empty()` → flujo nativo del bot, HTTP 200, sin HTTP 500. ✓
- Aislamiento sesiones A/B. ✓

## Verificación de trazabilidad H2/H3 (sin repetir proveedor)

H2 y H3 fueron acreditados en auditoría previa sobre `b5fd45a` (commits `b5fd45a`/`06f86f7`, evidencia en `issue-5-reconciliation-evidence/qa/b5fd45a/` y `builder/`). En `0ebcf18`, `MediaService.java` mantiene intacta la lógica H2/H3 (discriminación 400/422 vs 401/429/5xx, limpieza `.processing`, `edge-tts` con degradación). El MP3 real (`sample-voice.mp3`, SHA `aa801018...`, ffprobe: mp3/24 kHz/mono/4.392 s) y `tts-probe.json` se conservan en `builder/`; no se vuelve a invocar `edge-tts` ni Groq. `MediaServiceTest` (20) y `ApiAudioTest` (7) pasan verdes sobre `0ebcf18`.

## Correspondencia con el reporte del builder

El builder declara 243 pruebas verdes (`mvn -B clean verify`, exit 0, JAR `63064226...`). Esta auditoría verificó de forma independiente el mismo commit `0ebcf18`: **243/243 pruebas verdes, 0 fallos, 0 errores, 0 omitidas, exit code 0, BUILD SUCCESS**. La correspondencia de conteo de pruebas es exacta. El hash del JAR recompilado (`43c39f5a...`) difiere del del builder (`63064226...`) por diferencias de empaquetado/entorno; no se atribuye equivalencia byte a byte. Ninguna evidencia de otro SHA (`bc69036`, `b5fd45a`) se cuenta como nueva; cada una se cita con su SHA correspondiente.

## Dictamen

**QA_APPROVED** para `0ebcf1873a1df5a9aea321c9c9efa4cd7ac62ae8`.

Todos los criterios de la issue #5 quedan resueltos y acreditados:
- H1: fallback de intenciones integrado en `Api` bajo contrato estricto v3.1 (JSON estricto, evidencia literal, gating IDLE/pending/control/Unknown, red fuera de transacción, sesiones aisladas, degradación segura, inmunidad de controles, no URL de modelo).
- H2: contrato STT 400/422 vs 401/429/5xx y limpieza de temporales bajo `.processing`.
- H3: síntesis TTS real `edge-tts` acreditada con MP3 decodable y trazabilidad conservada, sin repetir proveedor.
- R1: regresión completa 243/243 verdes, incluida SSE #6 (`computeIfPresent` preservado).

La entrega se remite a Codex para la Fase 4 (aprobación arquitectónica final). No se modifica producción ni pruebas, no se hace merge/push ni cierre de issue.
