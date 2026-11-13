# Auditoría QA independiente — Issue #5 (Reauditoría H2/H3)

**Dictamen: QA_REJECTED**
**Commit auditado:** `b5fd45a73a1df5a9aea321c9c9efa4cd7ac62ae8`
**Build atendido:** `docs/orchestration/issue-5-reconciliation-build.md` (Antigravity)
**Informe previo (conservado sin modificar):** `docs/orchestration/issue-5-reconciliation-qa.md` v1 — `QA_REJECTED` sobre `bc69036c6f7a95761e4efaf34b29bb437bd5dca5`
**Diseño:** `docs/orchestration/issue-5-design.md` v1.0
**Plan QA:** `docs/orchestration/issue-5-reconciliation-qa-plan.md` v2
**Rama:** `GonzaCarrillo/reconcile-5-voice`
**Fecha:** 2026-11-13

## Alcance e identidad

Esta reauditoría evalúa exclusivamente las correcciones H2 y H3 del builder sobre `b5fd45a`:

- **H2:** contrato STT 400/422 vs 401/429/5xx y limpieza de temporales bajo `.processing`.
- **H3:** síntesis TTS real con `edge-tts` y MP3 decodable.

Scope del informe: únicamente `docs/orchestration/issue-5-reconciliation-qa.md` y la evidencia propia de QA bajo `issue-5-reconciliation-evidence/qa/b5fd45a/`. No se modifica producción ni pruebas.

**H1 (fallback de intenciones)** permanece abierto a cargo de Codex y no se cierra; no se emite `QA_APPROVED` integral aunque H2/H3 pasen.

Se conserva la evidencia previa de la auditoría Copilot sobre `bc69036` en `issue-5-reconciliation-evidence/qa/` sin modificarla, y las SHAs se distinguen explícitamente en cada entrada.

## Entorno y verificación independiente

- Linux x86_64, kernel `6.14.0-37-generic`.
- Microsoft OpenJDK `25.0.4.1`; Apache Maven `3.8.7`.
- Ejecución sobre el checkout limpio de `b5fd45a` (`git status` vacío; `src/` idéntico al de `b5fd45a`, sin diferencia vs `HEAD` en `src/`).
- Comando:

```bash
export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1
export PATH=$JAVA_HOME/bin:$PATH
mvn -B clean verify \
  -DargLine='-Djava.io.tmpdir=/tmp/opencode/qa-tmp'
```

**Resultado real:** exit code `0`, `BUILD SUCCESS`; 16 suites, 209 pruebas, 0 fallos, 0 errores y 0 omitidas. Surefire emitió el mensaje "Surefire is going to kill self fork JVM" tras el cierre de pruebas (comportamiento esperado del fork JVM, sin impacto en el resultado); Maven terminó con éxito y exit code 0. No se ocultó ni se excluyó ninguna prueba.

| Artefacto | SHA-256 |
|---|---|
| Log QA (`issue-5-reconciliation-evidence/qa/b5fd45a/verify.log`) | `43c124985c827451fa30dd62ef4c757e3b34c1aaa7f808acf2f0105e74630cbb` |
| Resumen Surefire (`issue-5-reconciliation-evidence/qa/b5fd45a/surefire-summary.txt`) | `e0cd3d975bdb184855d1b9531db21229151dda7463d3800e27fd225e7a851911` |
| JAR reconstruido (`target/taller-1.0.0.jar`) | `14aab6f6d591d41b1196a2784305732174d3692bc6a5316ad01232340a380dfd` |
| Commit auditoría | `b5fd45a73a1df5a9aea321c9c9efa4cd7ac62ae8` |

La evidencia propia de esta reauditoría está en `issue-5-reconciliation-evidence/qa/b5fd45a/`: log de verificación, resumen y XML Surefire, entorno, commit y hashes. `edge-tts` no está en PATH; no se llamó a Groq ni a proveedores externos; no se consumieron secretos.

Nota sobre SHAs: el JAR recompilado por esta auditoría (`14aab6f6...`) no coincide byte a byte con el JAR del builder (`e22ed0e7...`, registrado en `issue-5-reconciliation-evidence/builder/jar-sha256.txt`). Se registra la diferencia sin atribuir equivalencia de empaquetado, igual que hizo la auditoría previa con `bc69036`.

## Matriz de aceptación y hallazgos

| Criterio | Resultado | Evidencia y límite |
|---|---|---|
| A1 — STT sin clave | PASS | `MediaServiceTest.transcribeConClaveVacia...`; mensaje claro en español. |
| A2 — audio nulo/vacío/corrupto | PASS | 400 y 422 mapean a "No pude escuchar el audio"; cada prueba STT asertea `.processing` vacío. |
| A3 — fallo STT | PASS simulado | 401, 429, 500 y timeout de red mapean a "No puede transcribir ahora" sin exponer al proveedor externo. |
| A4 — límites TTS | PASS | Nulo, blank y >1000 caracteres no inician proceso; runner simulado. |
| A5 — `edge-tts` ausente | PASS | Degradación no bloqueante; prueba real condicionada a disponibilidad del binario. |
| A6 — audio sin clave, HTTP 400, estado intacto | PASS | MockMvc; sesión `IDLE` y 8 productos de la semilla permanecen. |
| A7 — texto y audio simultáneos | PASS | MockMvc verifica HTTP 400 y mensaje "uno por vez". |
| A8 — STT hacia motor y Reply | PASS simulado | MockMvc comprueba transición a `AWAITING_DETAILS`; STT y URL de audio son mocks. |
| R1 — regresión | PASS | 209/209 pruebas en `mvn clean verify` limpio. |
| Temporales STT/TTS | PASS | `assertProcessingDirectoryEmpty()` en cada prueba STT/TTS; `finally { Files.deleteIfExists(temp) }`. |
| Intent fallback | **ABIERTO — H-1** | `IntentFallback` archivado, no cableado. Véase H-1. |
| TTS reproducible | PASS — acreditado | Síntesis real `edge-tts es-AR-TomasNeural`; MP3 decodable con ffprobe. Véase H-3. |

### H-1 — Fallback de intenciones (pendiente; impide QA_APPROVED integral)

- **Esperado:** la issue #5 pide "Integrar Groq STT y fallback de intenciones"; el fallback debe tener comportamiento comprobable o la discrepancia debe resolverse mediante decisión de alcance aprobada.
- **Observado:** `IntentFallback` permanece archivado en `docs/orchestration/archive/IntentFallback.java.txt`; `BotEngine` recibe únicamente `IntentParser`; no hay fallback LLM en el flujo ejecutable ni prueba de comportamiento de fallback.
- **Estado:** **abierto**, a espera de la especificación arquitectónica de Codex y de la verificación de conflicto con la posible corrección Api SSE en #6 antes de ampliar scope. No emito `QA_APPROVED` integral.

### H-2 — Audio ilegible vs fallo de transcripción (RESUELTO)

- **Corrección:** `MediaService.transcribe` captura primero `RestClientResponseException`; 400 y 422 lanzan "No pude escuchar el audio. Probá otra vez o escribime."; 401, 429, 5xx e I/O/timeout lanzan "No puede transcribir ahora. Probá otra vez o escribime." sin exponer al proveedor. Temporales `.webm` bajo `root/.processing` y limpieza en `finally`.
- **Verificación propia:** pruebas con bytes corruptos no vacíos (400, 422), 401, 429, 500 y timeout, cada una con `assertProcessingDirectoryEmpty()`; `ApiAudioTest` verifica HTTP 400 con los mensajes y preservación de sesión (`IDLE`) y catálogo (8 productos). No se invocó a Groq.

### H-3 — Síntesis TTS real y MP3 decodable (RESUELTO)

- **Evidencia del builder:** `edge-tts 7.3.1` en entorno aislado `target/tts-env` (sin tocar POM ni dependencias del sistema); síntesis real con voz `es-AR-TomasNeural`; `sample-voice.mp3` y `tts-probe.json` en `issue-5-reconciliation-evidence/builder/`.
- **Verificación propia (ffprobe, exit code 0):** contenedor `mp3`, códec `mp3`, duración `4.392000` s, sample rate `24000` Hz, canales `1` (mono), bit rate `48000`, tamaño `26352` bytes; cabecera MP3 válida (`ID3` / sync frame).
- **SHA-256 de `sample-voice.mp3`:** `aa801018b941a92df9152b2e53c9ee77c5d76e99f2a41bc6719fcc77aa58b941`, idéntico al reportado por el builder. Origen confirmado: síntesis real, no bytes de marcador.

## Dictamen

**QA_REJECTED** para `b5fd45a73a1df5a9aea321c9c9efa4cd7ac62ae8`.

H2 y H3 quedan resueltos y acreditados con evidencia suficiente (209/209 pruebas verdes, MP3 real decodable y verificado, contrato de estado HTTP correcto, temporales limpiados). No obstante, H1 (fallback de intenciones) sigue abierto: no existe implementación activa ni decisión de alcance registrada, por lo que no es posible emitir `QA_APPROVED` integral. La entrega vuelve a Orca/Codex para resolver H1 y evaluar el conflicto con #6 antes de ampliar el alcance. No se aprueba arquitectura, no se integra, no se publica ni se cierra la issue.
