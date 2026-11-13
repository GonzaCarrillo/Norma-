# Aprobación arquitectónica — Issue #6: Vidriera accesible con SSE (Codex)

**Rol:** Codex — Software Architect  
**Worktree / rama:** `GonzaCarrillo/issue-6-storefront` — `/home/gonzalocar/orca/workspaces/Norma-/issue-6-storefront`  
**SHA auditado (builder Antigravity):** `ddcd19f86782be73ba9decc7fe8e529ee2bc270c`  
**Diseño aprobado:** Codex v1.0 `docs/orchestration/issue-6-design.md` (base `e0f6eada669b9b5070f64688a028a7cd946a7ddd`), aprobado por Orca en `docs/orchestration/issue-6-approval.md`  
**Informe QA (OpenCode):** `docs/orchestration/issue-6-qa.md`, commit `81f869d`  
**Fecha:** 2026-11-13  
**Archivo autorizado:** §10 del diseño, ítem 10 (`docs/orchestration/issue-6-codex-approval.md`)

---

## 1. Veredicto arquitectónico

**CONFORMIDAD: PASS en todo lo verificable estáticamente y por contratos; BLOQUEADO en V1-zoom200 (pageZoom nativo 100→200%).**

La implementación de Antigravity en `ddcd19f` cumple los contratos, la lista cerrada de archivos (§10) y los criterios V1–V8 verificables sin navegador de escritorio con indicador de zoom nativo. El único criterio sin acreditar es **V1-zoom200**, que el coordinador mantiene como hard block: no se declara PASS con CSS `zoom=2`, DPR=2 ni reflow WCAG 1.4.10 emulados. Hasta que OpenCode verifique pageZoom nativo en navegador de escritorio con menú indicador 200% en la misma ventana, el merge y el cierre de la issue permanecen bloqueados.

---

## 2. Alcance revisado y frontera respetada

Se audita el commit `ddcd19f` contra el diseño v1.0 aprobado. Archivos del ciclo #6 (§10) revisados:

1. `src/main/resources/static/index.html` — semántica es-AR, skip link, `main`, `#piezas`, `#status` role=status, diálogos nativos `#zoom`/`#sim`, IDs del simulador preservados (§7).
2. `src/main/resources/static/style.css` — `:root` 22px, `.large` 28.6px (≥30% sobre raíz), `grid-auto-rows: 1fr`, `aspect-ratio: 4/3`, `object-fit: cover`, contraste estricto >7:1, foco visible, sin `maximum-scale`/`user-scalable=no`.
3. `src/main/resources/static/app.js` — sesión sandbox (token 8..80 `[a-zA-Z0-9_-]`, no `DEFAULT`), formato `$ 18.500` es-AR, `speechSynthesis` con `lang='es-AR'` y fallback, reconciliación DOM por `pieceNumber`, coordinador SSE con `invalidationGen`/`inFlight`/`refreshPending`, timeout 10s, `AbortController`, `pagehide`/`pageshow`, `EventSource` única con reconexión nativa, simulador preservado.
4. `src/test/java/com/norma/taller/StorefrontContractTest.java` — MockMvc + SQLite aislado bajo `target`, 6 pruebas de contrato.
5. `src/test/java/com/norma/taller/StorefrontSseTest.java` — `RANDOM_PORT`, `HttpClient` lector SSE real, secuencia obligatoria §8.
6. `docs/orchestration/issue-6-design.md`, `issue-6-approval.md`, `issue-6-build-report.md`, `issue-6-evidence/` — documentación y evidencia del builder.

**Frontera respetada:** Sin cambios en `Api` (incluido `MediaConfig`/`ApiErrors`), `MediaService`/`PhotoProcessor`, `Product`/`Session`/`Reply`, `Catalog`, `BotEngine`/modelos/parser/codec, SQL/migración/seed/inicializador, fotos/audio/assets, POM/dependencias, `application.properties`, configuración global ni pruebas existentes. `MediaService` se sustituye solo por mock con `root` bajo `target` en configuración anidada de prueba (precedente `BotApiTest`), siguiendo §8 del diseño. Sin red Groq, sin binario TTS, sin procesador #4.

---

## 3. Conformidad contra criterios de aceptación V1–V8

| ID | Criterio | Veredicto | Base de la aprobación |
|---|---|---|---|
| V1 | Grilla, viewports 320/375/768/1440, títulos 10/60, 4:3, igualdad alturas ≤1px, sin scroll horizontal | **PASS** | CSS inspeccionado + evidencia QA `qa_v1_viewport_*.png`, `qa_v1_titles.png`, reflow 640/320 sin scroll H. |
| V1 (sub) | pageZoom nativo 100→200% con indicador de menú | **BLOQUEADO** | Hard block del coordinador. orca-cli 1.4.257 sin comando pageZoom; capabilities Linux `hotkey=false`/`menus=false`. CSSzoom/DPR/reflow son complementarios, **no** se declaran PASS. |
| V2 | Tipografía ≥22px normal, ≥28.6px modo grande en raíz; contraste >7:1 estados/hover/focus/disabled/placeholder | **PASS** | `:root` 22px / `.large` 28.6px (ratio 1.30) en CSS; contraste de evidencia builder revisada (12 pares >7 estrictos, mín 12.01:1; foco 16.45:1 ≥3). |
| V3 | Solo teclado: skip, foto, Enter/Space, Escape, retorno foco, modal durante SSE, pieza vendida, superposición simulador/zoom | **PASS** | Diálogo nativo `<dialog>`, foco inicial en `#close-zoom`, Escape cierra y restaura, `#open`/`#close` gestionan foco; evidencia builder revisada. |
| V4 | Voz nativa `speechSynthesis`, `es-AR`, stop/cancel/onend/onerror, fallback sin speechSynthesis | **PASS** | `app.js` implementa ciclo consistente; fallback visible sin `ReferenceError`; voces dependen del SO del usuario (límite ambiental declarado). |
| V5 | Carga, `[]` válido, HTTP 500, JSON inválido, foto fallida, reintento, datos previos obsoletos | **PASS** | Estados `cargando/listo/vacío/error`, `#retry`, fallback de foto reserva 4:3, datos anteriores conservados; evidencia builder revisada. |
| V6 | SSE real, dos sesiones A/B, mutación externa vía API, actualización reactiva DOM, precio/venta/undo/reset, aislamiento B, ráfaga | **PASS** | `StorefrontSseTest` real en `mvn -B verify` (EXIT_CODE=0): servidor HTTP real, `HttpClient` lector SSE, evento inicial `conectado`/`actualizado`, GET autoridad, aislamiento B 750ms, reconexión snapshot. Evidencia builder `verify_b2.py` revisada. |
| V7 | Corte/reconexión EventSource, mutación offline recuperada por snapshot GET, bfcache pagehide/pageshow | **PASS** | `StorefrontSseTest` + `app.js` (`pagehide` cierra stream, `pageshow` recrea una suscripción); evidencia builder `v7_reconnected_snapshot.png` revisada. |
| V8 | Regresión simulador (§7): IDs, 4 ejemplos, confirmación/cancelación/undo/reset, micrófono fallback, foco `#open` | **PASS** | IDs preservados; `StorefrontSseTest` y evidencia builder `v8_simulator_*.png` revisada; `#record` fallback sin errores de script. |

**Resumen:** V1=PASS (salvo zoom200 nativo BLOQUEADO), V2–V8=PASS.

---

## 4. Verificación de calidad de la entrega QA

- QA entregada en `docs/orchestration/issue-6-qa.md` (commit `81f869d`, push a `origin/GonzaCarrillo/issue-6-storefront`).
- QA recompila independientemente: `mvn -B clean` + `mvn -B verify` con JDK 25.0.4.1, Maven 3.8.7, `target/issue6-runtime` aislado (tmp/uploads/db), `groq.api.key=""`.
- Resultado QA: `EXIT_CODE=0`, BUILD SUCCESS, 137 tests / 0 fallos / 0 errores / 0 omitidos; `basedir` en XML apunta a exportación del commit `ddcd19f`.
- Smoke del JAR: `PONG` en `/api/ping`, productos JSON, puerto dinámico, status real de `wait` capturado (no `wait … || true`).
- Evidencia QA propia bajo `docs/orchestration/issue-6-evidence/` con prefijo `qa_*`, sin sobrescribir evidencia del builder (`v*_evidence.json`, PNGs del builder intactos).

La evidencia del builder (`target/qa.gJ1PRR`, verify.log hash `8a85c099…`, JAR hash `737a8462…`, EXIT_CODE=0) fue verificada como recibida, no asumida ciegamente, y coincide con `issue-6-build-report.md`.

---

## 5. Riesgos residuales y límites (declarados, no ocultados)

- **V1-zoom200 pageZoom nativo:** sin acreditación. Requiere navegador de escritorio con menú indicador 200% en la misma ventana durante medición y captura. Bloquea merge/cierre (residual acordado Orca–Codex).
- Voces y reproducción dependen del navegador/gesto/OS del usuario; cliente degrade de forma no bloqueante.
- SSE sin replay depende de resincronización por snapshot GET; ventana inevitable si la red pierde un evento.
- Tokens sandbox no autentican; aislamiento por origen/almacenamiento, no autenticación fuerte.
- Inspección de contraste/teclado no certifica WCAG AAA integral.

---

## 6. Decisión y siguientes pasos

- Conformidad arquitectónica de `ddcd19f` contra diseño v1.0: **PASS** en código, contratos, pruebas y documentación revisables estáticamente.
- Veredicto global de la issue: **BLOQUEADO** por V1-zoom200 (pageZoom nativo 200% sin acreditar). No se emite PASS total ni se autoriza merge/cierre.
- Tras verificación real de pageZoom nativo por OpenCode y resolución del bloqueo, esta aprobación arquitectónica podrá reemitirse como PASS total sobre el mismo SHA (o sobre el SHA final si hay cambios relevantes, lo cual obligaría a reevaluar pruebas/conformidad afectadas; no reutilizar PASS sin justificar identidad del código).
- Cierre y aceptación final corresponden a Orca (G3), no a Codex.

Commit autorizado para este informe únicamente en rama `GonzaCarrillo/issue-6-storefront` (no `main`).
