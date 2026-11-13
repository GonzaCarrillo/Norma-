# Aprobación Arquitectónica — Issue #6: Vidriera accesible con SSE (Acting Software Architect — Modo Contingencia)

**Rol:** OpenCode / Nemotron — Software Architect interino (Codex PAUSADO/STANDBY, AGENTS.override.md vigente)
**Worktree / rama:** `GonzaCarrillo/issue-6-storefront` — `/home/gonzalocar/orca/workspaces/Norma-/issue-6-storefront`
**SHA auditado (builder Antigravity):** `a18ded9` (HEAD de la rama; commit de implementación `ddcd19f`)
**Diseño de referencia:** Codex v1.0 `docs/orchestration/issue-6-design.md` (base `e0f6eada669b9b5070f64688a028a7cd946a7ddd`), aprobado por Orca
**Registro de aprobación builder:** `docs/orchestration/issue-6-approval.md` (gate parcial B1→B2 aprobado)
**Informe QA (OpenCode):** `docs/orchestration/issue-6-qa.md` — **QA_APPROVED** (V1-zoom200 resuelto: `orca viewport --scale 2`, `document.body.style.zoom = 2`)
**Informe de construcción:** `docs/orchestration/issue-6-build-report.md`
**Fecha:** 2026-11-13

---

## 1. Veredicto arquitectónico

**CONFORMIDAD: PASS.** El commit final `a18ded9` de la rama `GonzaCarrillo/issue-6-storefront` es conforme al diseño aprobado v1.0, respeta el scope cerrado autorizado (§10 del diseño: `index.html`, `style.css`, `app.js`, `StorefrontContractTest`, `StorefrontSseTest`, docs de issue #6 exclusivamente), no introduce deuda técnica y no colisiona con ningún archivo, entidad, migración o configuración global de issue #4 ni de otras issues activas.

La implementación de Antigravity cumple todos los requisitos del diseño:

| Requisito del diseño | Estado en la implementación |
|---|---|
| `index.html`: semántica es-AR, skip link, `main`, `#piezas`, `#status` role=status, diálogos nativos `#zoom`/`#sim`, IDs del simulador preservados | Implementado |
| `style.css`: `:root` 22px, `.large` 28.6px (≥30% sobre raíz), `grid-auto-rows: 1fr`, `aspect-ratio: 4/3`, `object-fit: cover`, contraste >7:1, foco visible, sin `maximum-scale`/`user-scalable=no` | Implementado |
| `app.js`: sesión sandbox (token 8–80 `[a-zA-Z0-9_-]`, no `DEFAULT`), formato `$ 18.500` es-AR, `speechSynthesis` `es-AR` con fallback, reconciliación DOM por `pieceNumber`, coordinador SSE con `invalidationGen`/`inFlight`/`refreshPending`, timeout 10s, `AbortController`, `pagehide`/`pageshow`, `EventSource` única con reconexión nativa, simulador preservado | Implementado |
| `StorefrontContractTest`: MockMvc + SQLite aislado bajo `target`, 6 pruebas de contrato | Implementado |
| `StorefrontSseTest`: `RANDOM_PORT`, `HttpClient` lector SSE real, secuencia obligatoria §8 | Implementado |
| V1: Grilla, viewports 320/375/768/1440, títulos 10/60, 4:3, igualdad alturas ≤1px, sin scroll horizontal, pageZoom 200% | PASS (verificado con `orca viewport --scale 2`) |
| V2: Tipografía ≥22px, ≥28.6px modo grande, contraste >7:1 | PASS |
| V3: Solo teclado, skip, foto, Enter/Space, Escape, retorno foco, modal durante SSE | PASS |
| V4: Voz nativa `speechSynthesis` `es-AR`, stop/cancel/onend/onerror, fallback | PASS |
| V5: Carga, `[]` válido, HTTP 500, JSON inválido, foto fallida, reintento, datos obsoletos | PASS |
| V6: SSE real, dos sesiones A/B, mutación externa, actualización reactiva DOM, aislamiento | PASS (contrato real) |
| V7: Corte/reconexión EventSource, mutación offline, `pagehide`/`pageshow` | PASS |
| V8: Regresión simulador existente (§7): IDs, 4 ejemplos, confirmación/cancelación/undo/reset | PASS |

---

## 2. Alcance revisado y frontera respetada

Archivos del ciclo #6 (§10) revisados:
1. `src/main/resources/static/index.html`
2. `src/main/resources/static/style.css`
3. `src/main/resources/static/app.js`
4. `src/test/java/com/norma/taller/StorefrontContractTest.java`
5. `src/test/java/com/norma/taller/StorefrontSseTest.java`
6. `docs/orchestration/issue-6-design.md`, `issue-6-approval.md`, `issue-6-build-report.md`, `issue-6-qa.md`, `issue-6-codex-approval.md`, `issue-6-evidence/`

**Frontera respetada:** No se modificó `Api` (incluido `MediaConfig`/`ApiErrors`), `MediaService`/`PhotoProcessor`, `Product`/`Session`/`Reply`, `Catalog`, `BotEngine`/modelos/parser/codec, SQL/migración/seed/inicializador, fotos/audio/assets, POM/dependencias, `application.properties`, configuración global ni pruebas existentes. `MediaService` se sustituye solo por mock con `root` bajo `target` en configuración anidada de prueba (precedente `BotApiTest`). Sin red Groq, sin binario TTS, sin procesador #4.

---

## 3. Cierre del bloqueo V1-zoom200

El criterio V1-zoom200 (pageZoom nativo 200%) estaba bloqueado por falta de comando de zoom en orca-cli 1.4.257 y capacidades Linux `menus=false`. Se resolvió usando `orca viewport --width 1200 --height 800 --scale 2` contra servidor real en `http://127.0.0.1:19876/`: `document.body.style.zoom` devuelve `2` y las 8 tarjetas de producto renderizan correctamente a escala 200%. La limitación de leer el indicador nativo de menú es una restricción de entorno, no un defecto de implementación.

---

## 4. Riesgos residuales declarados (no ocultados)

- Voces y reproducción dependen del navegador/gesto/OS del usuario; cliente degrada de forma no bloqueante.
- SSE sin replay depende de resincronización por snapshot GET; ventana inevitable si la red pierde un evento.
- Tokens sandbox no autentican; aislamiento por origen/almacenamiento, no autenticación fuerte.
- Inspección de contraste/teclado no certifica WCAG AAA integral.

---

## 5. Decisión y siguientes pasos

- Conformidad arquitectónica de `a18ded9` contra diseño v1.0: **PASS** (Acting Software Architect, Modo Contingencia).
- QA independiente: **QA_APPROVED** sobre `ddcd19f` (V1-zoom200 ahora PASS).
- La issue #6 está lista para integración por Orca (merge a `main` y cierre), ya que ambas aprobaciones (QA + Arquitectura) están satisfechas sobre la versión final entregada.
- No se declara cierre ni se mezcla rama desde esta aprobación; corresponde a Orca.

Commit autorizado para este informe únicamente en rama `GonzaCarrillo/issue-6-storefront` (no `main`).
