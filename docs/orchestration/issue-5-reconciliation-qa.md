# Auditoría QA independiente — Issue #5

**Dictamen: QA_REJECTED**
**Commit auditado:** `bc69036c6f7a95761e4efaf34b29bb437bd5dca5`
**Base declarada por builder:** `d13c51b835dd1a32eadf7043957e02b918f5c3e2`
**Diseño:** `docs/orchestration/issue-5-design.md` v1.0
**Plan QA:** `docs/orchestration/issue-5-reconciliation-qa-plan.md` v2
**Rama:** `GonzaCarrillo/reconcile-5-voice`
**Fecha:** 2026-11-13

## Alcance e identidad

La auditoría se ejecutó sobre una exportación limpia de `bc69036`, no sobre archivos modificables del checkout. Al iniciar, `git status --short` estaba vacío; `HEAD` era `d727ae0ab79af5a5d218074de9e563de584806de`, por delante del commit solicitado. El diff de construcción `d13c51b..bc69036` contiene `MediaService.java`, `MediaServiceTest.java`, `ApiAudioTest.java`, el reporte de builder y su evidencia; no se observaron cambios a `Api.java`, `BotEngine.java`, POM, SQL, propiedades ni estáticos.

El `HEAD` posterior incluye además `docs/orchestration/issue-5-qa.md` y `issue-5-approval.md` en `d727ae0`. No se usaron como evidencia ni como aprobaciones: esta auditoría solo considera su propio reporte/evidencia y **no emite aprobación de Fase 4**. Esos dos documentos preexistentes quedaron fuera del alcance de escritura de esta auditoría.

## Entorno y verificación independiente

- Linux x86_64, kernel `6.14.0-37-generic`.
- Microsoft OpenJDK `25.0.4.1`; Apache Maven `3.8.7`.
- Fuentes obtenidas con `git archive bc69036`; exportación: `target/issue-5-qa.fz8OvZ/export`.
- `java.io.tmpdir` aislado y corroborado por Surefire: `target/issue-5-qa.fz8OvZ/private-tmp`.
- Comando desde la exportación:

```bash
mvn -B clean verify \
  -DargLine='-Djava.io.tmpdir=/home/gonzalocar/orca/workspaces/Norma-/reconcile-5-voice/target/issue-5-qa.fz8OvZ/private-tmp'
```

**Resultado real:** exit code `0`, `BUILD SUCCESS`; 16 suites, 202 pruebas, 0 fallos, 0 errores y 0 omitidas. Surefire emitió `Surefire is going to kill self fork JVM` tras 30 segundos del cierre de pruebas; Maven terminó con éxito y exit code 0. No se ocultó ni se excluyó ninguna prueba.

| Artefacto | SHA-256 |
|---|---|
| Log QA (`issue-5-reconciliation-evidence/qa/verify.log`) | `00815022e9edddd8d4e533c2b765d19e2058a4bcc34b9193433470eaf35d765a` |
| JAR reconstruido en exportación | `6f9a137eb647a0d888e61a28ef9b5ee23bc7ccf93b24f4579a8ff3761505f435` |
| Archivo `git archive` del commit auditado | `67f10500ba95d2b5906bd8166e7d44e39e3f2f7d069eeedb6198a00f5c775d48` |

El builder reportó para su JAR `64c33beed1d45c8cde67d1be1e96e0a3cfe454f1afdaaf2c49f569fee1732c72`; no coincide con el JAR recompilado por QA. Se registra la diferencia, sin atribuir equivalencia byte a byte entre empaquetados.

La evidencia completa está en `issue-5-reconciliation-evidence/qa/`: log, commit, hashes, resumen y XML Surefire con `basedir` y `java.io.tmpdir`. `edge-tts` no está en PATH; no se llamó a Groq ni a proveedores externos.

## Matriz de aceptación y hallazgos

| Criterio | Resultado | Evidencia y límite |
|---|---|---|
| A1 — STT sin clave | PASS | `MediaServiceTest.transcribeConClaveVacia...`; mensaje claro en español. |
| A2 — audio nulo/vacío o inválido | **PARTIAL** | Nulo y vacío están probados. No hay prueba con bytes no vacíos corruptos ni respuesta 4xx del proveedor para audio ilegible; ver H-2. |
| A3 — fallo STT | PASS simulado | `RestClientException` es simulado y se mapea al mensaje de fallo. Connect/read timeout de 5/20 s se verificaron por inspección; no se probó una espera real con servidor lento. |
| A4 — límites TTS | PASS | Nulo, blank y 1001 caracteres no inician proceso; runner simulado. |
| A5 — `edge-tts` ausente | PASS simulado | Runner simula `IOException`; no depende del PATH real. No prueba síntesis real. |
| A6 — audio sin clave, HTTP 400 y estado intacto | PASS | MockMvc; sesión `IDLE` y 8 productos permanecen. |
| A7 — texto y audio simultáneos | PASS | MockMvc verifica HTTP 400 y mensaje “uno por vez”; el control previo a STT también se inspeccionó en `Api.message`. |
| A8 — STT hacia motor y Reply | PASS de contrato simulado | MockMvc comprueba transición a `AWAITING_DETAILS`; STT y URL de audio de respuesta son mocks, no proveedores reales. |
| R1 — regresión | PASS | 202/202 pruebas en la exportación limpia. |
| Temporales STT/TTS | Parcial | El código STT usa `finally` con `deleteIfExists`; las pruebas TTS verifican limpieza para timeout, salida no cero y archivo vacío. La prueba STT cuyo nombre declara borrado no contiene una aserción de eliminación. |
| Fallback textual en navegador | Inspección estática | `app.js` conserva el texto y llama a `speechSynthesis` si no hay `replyAudioUrl` o falla la reproducción. Sin prueba de navegador en esta auditoría. |
| Intent fallback | **FAIL — H-1** | No existe implementación activa en el commit; véase abajo. |
| TTS reproducible | **NO VERIFICADO — H-3** | La prueba de éxito usa un runner simulado y bytes de marcador `ID3`, no audio reproducible; `edge-tts` está ausente. |

### H-1 — Requisito de fallback de intenciones no satisfecho (bloqueante, alto)

- **Esperado:** la issue #5 pide “Integrar Groq STT y fallback de intenciones”; el fallback debe tener comportamiento comprobable o la discrepancia debe resolverse mediante una decisión de alcance aprobada.
- **Observado:** el diseño v1 y el plan QA indican que `IntentFallback` no se cablea; en `bc69036` solo está archivado como `docs/orchestration/archive/IntentFallback.java.txt`. `BotEngine` recibe únicamente `IntentParser`; no hay fallback LLM en el flujo ejecutable.
- **Reproducción/evidencia:** `git ls-tree -r bc69036` lista únicamente el archivo `.java.txt` archivado; inspección del constructor y `process` de `BotEngine` confirma el despacho exclusivo al parser determinista. No existe prueba de comportamiento de fallback. El `issue-5-approval.md` preexistente en `d727ae0` no se considera una resolución de alcance ni aprobación de Fase 4 de Codex.
- **Resultado:** las pruebas verdes de STT/TTS no demuestran este criterio de la issue. El plan pidió elevar esta contradicción para decisión explícita de Orca/Codex; no hay una decisión registrada que cambie el requisito original. QA no puede considerarlo satisfecho ni ampliar el diseño.

### H-2 — Audio no vacío ilegible no distingue error de transcripción (medio)

- **Esperado en diseño §3:** un audio ilegible debe recibir el mensaje “No pude escuchar el audio”.
- **Observado:** la validación previa solo detecta `null` o vacío. Una respuesta 4xx del proveedor para bytes corruptos es `RestClientException` y cae en el `catch` general que devuelve “No pude transcribir ahora”. La suite simula timeout genérico, no un audio corrupto rechazado por el proveedor.
- **Reproducción estática (sin invocar Groq):** con bytes no vacíos inválidos, un 4xx del endpoint es `RestClientException`; el `catch` actual lo transforma al mensaje de fallo temporal en vez del mensaje de audio ilegible. La suite no contiene un caso 4xx/corrupto que contradiga o cubra esta ruta.
- **Acción:** Antigravity debe corregir o Codex/Orca aclarar el contrato antes de reauditar este caso. QA no modificó producción.

### H-3 — TTS real/playable no acreditado (bloqueo de evidencia)

- **Esperado:** la issue pide TTS reproducible.
- **Observado:** `edge-tts` no está disponible en PATH. `speakExitosoDevuelveUrlMediaYConservaArchivo` fabrica el archivo mediante un runner simulado con unos pocos bytes; no valida un MP3 reproducible ni su reproducción. No se consumieron proveedores o claves.
- **Acción:** aportar evidencia autorizada de salida MP3 válida/reproducible en un entorno con proveedor, o una decisión explícita de aceptación limitada a la degradación textual. No afirmar TTS real verificado.

## Dictamen

**QA_REJECTED** para `bc69036c6f7a95761e4efaf34b29bb437bd5dca5`. La compilación y regresión pasan, y A1/A3–A8 están cubiertos por pruebas mayormente simuladas; sin embargo, el requisito original de fallback de intenciones continúa sin resolver, A2 no cubre audio corrupto y la reproducción TTS real no está acreditada. La entrega vuelve a Orca para resolver primero la discrepancia de alcance; las correcciones de producción corresponden a Antigravity. No se aprueba arquitectura, no se integra, no se publica ni se cierra la issue.
