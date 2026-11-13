# Diseño de issue #5 — Voz argentina y transcripción con fallbacks

Versión **1.0**, 2026-11-13. Autor: **OpenCode / Nemotron — Software Architect interino (Modo Contingencia)**.
Issue: [#5](https://github.com/GonzaCarrillo/Norma-/issues/5).
Base inspeccionada: `dadc2c2` (rama `GonzaCarrillo/issue-5-voice`, derivada de `GonzaCarrillo/issue-4-media` con issue #4 aprobada).
Rama: `GonzaCarrillo/issue-5-voice`; worktree: `/home/gonzalocar/orca/workspaces/Norma-/issue-5-voice`.

---

## 1. Fuentes, evidencia y discrepancias

Se leyeron `AGENTS.md` vigente, `AGENTS.override.md` (Modo Contingencia), skill `architect-spec`, ambas especificaciones raíz, issue #5 mediante `gh issue view 5`, backlog, `docs/medios.md`, `docs/operacion.md`, `docs/decisiones.md`, `MediaService`, `Api`/`MediaConfig`/`ApiErrors`, `IntentFallback`, `IntentParser`, `BotEngine`, `BotModel`, `Catalog`, POM, propiedades, suites versionadas.

`gh issue view` confirma #1–#4 CLOSED (en worktrees, pendientes de merge a `main` por Orca) y #5 OPEN. Depende de #4 (orden anterior del backlog), ya aprobada técnicamente (QA + Arquitectura). No se espera a merge a `main` para diseñar; la base de trabajo incluye los cambios de #4.

**Estado actual relevante:**

| Componente | Estado observado | Brecha que resuelve #5 |
|---|---|---|
| `MediaService.transcribe(MultipartFile)` | Groq Whisper `whisper-large-v3-turbo`, `language=es`. Lanza `IllegalArgumentException` si `key.isBlank()` o si Groq falla (`RestClientException`). Timeout de lectura 20s, connect 5s. | Funciona, pero sin fallback determinístico ni límites explícitos de texto de entrada al motor. |
| `MediaService.speak(String)` | `edge-tts --voice es-AR-TomasNeural`, `waitFor(12s)`. Devuelve `null` si falla. `Api.message` hace fallback a `speak(replyText)` del navegador si `replyAudioUrl` es null. | Ya tiene límite de 12s y degradación a navegador. Falta acotar longitud de texto a sintetizar y evidenciar límites. |
| `IntentFallback.parse(...)` | Groq LLM `llama-3.3-70b-versatile`. Devuelve `Optional.empty()` si `key.isBlank()` o cualquier excepción. **No está cableado** en ningún constructor (`Api`, `BotEngine` no lo inyectan). | Existe pero no se usa. El parser determinista `IntentParser` es la autoridad; `IntentFallback` es opcional solo con clave. |
| `IntentParser` | Determinista, sin red, sin clave. Maneja CREATE/PRICE/IMAGE/SOLD/UNDO/Cancel/Correction. | Es el fallback real de intenciones cuando no hay clave Groq. |
| `Api.message` | `hasAudio → text = media.transcribe(audio)`; `image → media.image(image)`; `bot.process(sessionId,text,photo)`; `Reply(replyText, media.speak(...), state)`. Texto+audio juntos → 400 antes de medios. | Contrato de endpoints intacto. Errores de STT/TTS llegan al usuario vía `ApiErrors` (400/500). |

`docs/decisiones.md` D4 documenta que `groq.api.key` es opcional y el arranque es normal sin clave. D4 también señala que parámetros de modelo/voz se revisarían en #5; este diseño no agrega propiedades inertes.

Las specs raíz contienen diseños ilustrativos superados: rigen las decisiones aprobadas #1–#4.

---

## 2. Decisiones y compatibilidad

**D5-1 — STT primario Groq, fallback a texto del usuario (no a parser de audio).**
`MediaService.transcribe` sigue siendo el único camino de audio a texto. Si falla (clave ausente, red, cuota, audio ilegible), lanza `IllegalArgumentException` con mensaje claro en español y `Api.message` lo traduce a HTTP 400. No se intenta reconstruir el texto del audio por otro medio; se le pide al usuario que escriba o use un ejemplo. Esto preserva el contrato aprobado en #3 (texto y audio no vacíos juntos se rechazan; STT ocurre antes de foto).

**D5-2 — TTS con límite de tiempo y degradación no bloqueante.**
`MediaService.speak` mantiene `edge-tts --voice es-AR-TomasNeural` con `waitFor(12s)` y descarta el archivo si falla. Se añade un límite de longitud de texto (máximo 1000 caracteres, coherente con el límite de mensaje en `Api`) para evitar síntesis interminables. Si `edge-tts` no está disponible o falla, `speak` devuelve `null` y `Api.message` hace fallback a `speechSynthesis` del navegador (ya implementado en `app.js`). No se bloquea indefinidamente.

**D5-3 — `IntentFallback` no cableado por defecto.**
`IntentFallback` requiere clave Groq y llama a un LLM. Como el parser determinista `IntentParser` ya cubre todas las operaciones sin red ni clave, `IntentFallback` se deja disponible como clase pero **no se inyecta** en `Api` ni `BotEngine`. Si en el futuro se desea activar, requiere clave Groq y diseño explícito. No se agrega como dependencia crítica.

**D5-4 — Sin nuevas dependencias, sin cambios de esquema, sin cambios de endpoint.**
No se modifica `pom.xml` (las dependencias Groq/edge-tts/Thumbnailator ya están), no se toca SQL/seed/migración, no se agregan propiedades de configuración nuevas (`groq.api.key` ya existe), no se cambia `Api` (endpoints, `MediaConfig`, `ApiErrors` intactos), no se cambia `Catalog`/`BotEngine`/`BotModel`/`IntentParser`.

**D5-5 — Compatibilidad con #4.**
`MediaService` conserva su constructor público, campo `root` y métodos `transcribe`/`speak` intactos. `PhotoProcessor` (de #4) no se toca. La imagen se procesa antes del motor transaccional; el audio/STT también ocurre antes del motor. El motor decide si conserva la URL como borrador y si publica después de confirmar.

---

## 3. STT: Groq con fallback graceful

**Entrada:** `MultipartFile audio` en `POST /api/bot/message`.

**Flujo:**
1. `media.transcribe(audio)` → Groq Whisper `whisper-large-v3-turbo`, `language=es`.
2. Si éxito → `text` se pasa a `bot.process(sessionId, text, photo)`.
3. Si `key.isBlank()` → `IllegalArgumentException("La transcripción no está configurada. Podés escribir el mensaje o usar un ejemplo.")` → HTTP 400.
4. Si Groq falla (red, cuota, timeout 20s) → `IllegalArgumentException("No pude transcribir ahora. Probá otra vez o escribime.")` → HTTP 400.
5. Si audio vacío o inválido → `IllegalArgumentException("No pude escuchar el audio. Probá otra vez o escribime.")` → HTTP 400.

En todos los casos de error, `Session`/`Product` se preservan (no se toca el motor ni SSE ni TTS por esa solicitud fallida).

**No bloqueo:** connect timeout 5s, read timeout 20s. No se agregan reintentos automáticos que bloqueen indefinidamente.

---

## 4. TTS: edge-tts con límites de tiempo

**Entrada:** `String text` (el `replyText` del motor).

**Flujo:**
1. Si `text == null` o `text.isBlank()` → devuelve `null` sin llamar a `edge-tts`.
2. Si `text.length() > 1000` → se trunca o se rechaza (coherente con el límite de mensaje de `Api`).
3. `edge-tts --voice es-AR-TomasNeural --text <text> --write-media <archivo>` con `waitFor(12s)`.
4. Si `exitValue == 0` y `Files.size > 0` → devuelve `/media/<uuid>.mp3`.
5. Si falla cualquier condición → `null`, y `Api.message` hace fallback a `speechSynthesis` del navegador.

**Archivos temporales:** `UUID.randomUUID() + ".mp3"` en `root`. Limpieza en `finally` si falla. Archivos exitosos se conservan bajo `root` y se sirven vía `MediaConfig /media/**`.

**No bloqueo:** 12s límite duro. No se agregan colas ni reintentos.

---

## 5. `IntentFallback`: disponible, no obligatorio

`IntentFallback.parse(text, image, products, parser)` llama a Groq LLM `llama-3.3-70b-versatile` para interpretar intención. Devuelve `Optional.empty()` si:
- `key.isBlank()` (sin clave Groq)
- Cualquier excepción de red/JSON/parseo
- El intent resultante es `UNDO` (el deshacer se maneja en el motor, no por LLM)

**No se cablea** en `Api` ni `BotEngine`. El parser determinista `IntentParser` es la autoridad para interpretar mensajes de texto. `IntentFallback` queda como clase pública disponible para activación futura con clave Groq.

---

## 6. Lista cerrada de archivos y recursos compartidos

Este diseño autoriza la construcción a modificar **únicamente**:

| Archivo | Cambio autorizado |
|---|---|
| `src/main/java/com/norma/taller/MediaService.java` | Ajuste de límites y mensajes en `transcribe` y `speak` (longitud de texto, timeouts, mensajes en español, limpieza de temporales). Mantener constructor/root y firma de métodos. |
| `src/test/java/com/norma/taller/MediaServiceTest.java` (nuevo) | Pruebas de `transcribe` (clave vacía, audio inválido, error de Groq) y `speak` (texto vacío, edge-tts ausente, fallback a null). Usar `root` bajo `target` del worktree. |
| `src/test/java/com/norma/taller/ApiAudioTest.java` (nuevo) | Pruebas de integración HTTP: `POST /api/bot/message` con audio (STT), error 400 cuando clave ausente, error 400 texto+audio, sesión preservada tras fallo de STT. Usar `MediaService` mock o real con `root` aislado. |
| `docs/orchestration/issue-5-design.md` | Este documento. |
| `docs/orchestration/issue-5-approval.md` | Registro de gates Orca (opcional, crear en construcción). |
| `docs/orchestration/issue-5-build-report.md` | Entorno, comandos, resultados, evidencia y límites reales. |
| `docs/orchestration/issue-5-qa.md` | Informe QA independiente (crear en auditoría). |

**No se modifica:** `IntentFallback.java` (no se cablea, no se toca), `IntentParser.java`, `BotEngine.java`, `BotModel.java`, `Api.java` (endpoints intactos), `MediaConfig`, `ApiErrors`, `Catalog`, `PhotoProcessor`, `schema.sql`, `seed.sql`, `migrate-v0-v2.sql`, `application.properties` (conservar `groq.api.key=${GROQ_API_KEY:}`), POM, assets estáticos, `index.html`/`style.css`/`app.js`, pruebas existentes.

**Recursos de pruebas:** DB SQLite nueva por contexto, archivo bajo directorio aislado del test. `NORMA_UPLOADS` nuevo por suite/contexto, sin rutas comunes. MockMvc para contrato; `edge-tts` no se ejecuta en pruebas unitarias (se simula su ausencia). No usar DB/uploads de otra sesión. Ejecutar Maven secuencial dentro del checkout.

---

## 7. Matriz de aceptación y verificaciones previstas

| ID | Comportamiento a comprobar | Evidencia mínima |
|---|---|---|
| A1 | `transcribe` con `groq.api.key=""` → `IllegalArgumentException` mensaje en español sobre transcripción no configurada | Prueba unitaria, `MediaService` con `root` bajo `target` |
| A2 | `transcribe` con audio inválido/basura → `IllegalArgumentException` mensaje en español | Prueba unitaria con archivo corrupto |
| A3 | `transcribe` con Groq fallando (simulado) → `IllegalArgumentException` "No pude transcribir ahora" | Prueba unitaria con `RestClient` que lanza excepción |
| A4 | `speak` con texto `null` o vacío → devuelve `null` sin llamar a `edge-tts` | Prueba unitaria |
| A5 | `speak` con texto válido pero `edge-tts` ausente → devuelve `null` sin bloquear | Prueba unitaria (no se invoca binario real) |
| A6 | `POST /api/bot/message` con audio y sin clave Groq → HTTP 400, mensaje en español, `Session`/`Product` preservados | `MockMvc` o servidor real con `MediaService` real y `groq.api.key=""` |
| A7 | `POST /api/bot/message` texto+audio → HTTP 400 antes de STT (contrato #3) | Ya cubierto por pruebas existentes; sin regresión |
| A8 | `POST /api/bot.message` con audio válido (mock de STT) → texto transcrito pasa al motor, `Reply` con `replyAudioUrl` | Integración con STT simulado |
| R1 | Regresión #1–#4: todas las suites previas intactas, `clean verify` completo verde | 175 pruebas previas + nuevas de #5 |

---

## 8. Plan secuencial con gates

**G0 — Aprobación Orca.** Aprobar este diseño/hash. Sólo entonces se despacha construcción a Antigravity.

**B1 — STT y TTS límites (Antigravity).** Modificar `MediaService.transcribe`/`speak` según D5-1/D5-2/D5-4. Crear `MediaServiceTest` y `ApiAudioTest`. Commit acotado. **Gate Orca B1** antes de B2.

**B2 — Evidencia y regresión (Antigravity).** `mvn -B clean verify` con JDK 25, todas las suites previas intactas + nuevas de #5. `docs/orchestration/issue-5-build-report.md`. **Gate Orca B2** antes de entrega.

**G3 — OpenCode QA independiente.** Compilar, ejecutar matriz y regresiones, entregar PASS/FAIL.

**G4 — Acting Architect Approval final.** Revisar conformidad del commit final contra este diseño. Emitir PASS/FAIL sin implementar arreglos.

**G5 — Orca acepta/cierra** sólo con QA + Arquitectura PASS sobre la versión final.

---

## 9. Riesgos residuales declarados (no ocultados)

- `edge-tts` depende del binario instalado en el entorno; si falta, `speak` devuelve `null` y el navegador provee `speechSynthesis` (degradación no bloqueante).
- Voces disponibles dependen del SO del usuario; `es-AR` puede no existir en todos los navegadores.
- STT requiere clave Groq y red; sin ellas, el sistema sigue funcionando por texto y ejemplos de audio demo (MP3 locales).
- `IntentFallback` no está cableado; no es una dependencia crítica para la aceptación de #5.
