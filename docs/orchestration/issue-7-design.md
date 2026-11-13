# Diseño de issue #7 — Simulador de Norma sin credenciales

Versión **1.0**, 2026-11-13. Autor: **OpenCode / Nemotron — Software Architect interino (Modo Contingencia)**.
Issue: [#7](https://github.com/GonzaCarrillo/Norma-/issues/7).
Base inspeccionada: `ff8f750` (rama `GonzaCarrillo/issue-7-simulator`, basada en `main` con #4, #5, #6 mergeados).
Rama: `GonzaCarrillo/issue-7-simulator`; worktree: `/home/gonzalocar/orca/workspaces/Norma-/issue-7-simulator`.

---

## 1. Verificación de cumplimiento con código existente (Issue #6)

La issue #7 depende de #6 (orden anterior del backlog), ya mergeada a `main`. Al inspeccionar el código actual en `main`, el simulador de Norma ya está **completamente implementado** en los archivos estáticos de #6:

| Archivo | Contenido relevante para #7 |
|---|---|
| `src/main/resources/static/index.html` | Dialog `#sim` (panel conversacional), form `#message`, input `#photo`, botón `#record`, ejemplos `[data-demo]` (create/price/image/sold), botones `#yes`/`#no`/`#undo`/`#reset`, `#reply-audio` |
| `src/main/resources/static/app.js` | Gestión de sesión sandbox (token 8–80, no `DEFAULT`), `send()` a `POST /api/bot/message`, `requestApi`, ejemplos con MP3 locales, `MediaRecorder` para micrófono, confirmación/cancelación/deshacer/reset, `speechSynthesis` lectura, SSE reconexión |
| `src/main/resources/static/style.css` | Estilos del simulador: `.bubble`, `.examples`, `#chat`, `#sim[open]`, accesibilidad (foco, contraste, teclado) |

---

## 2. Criterios de aceptación #7 vs implementación actual

| Criterio #7 | Estado en código actual (main) | Evidencia |
|---|---|---|
| Panel conversacional | Implementado | `index.html` dialog `#sim` con `#chat`, `#message`, botones de acción |
| Micrófono | Implementado | `app.js` §9: `navigator.mediaDevices.getUserMedia` + `MediaRecorder`, `#record` |
| Carga de foto | Implementado | `index.html` input `#photo` `accept="image/jpeg,image/png,image/webp"`; `app.js` envía foto en `FormData` |
| Cuatro ejemplos | Implementado | `index.html` 4 botones `[data-demo="create|price|image|sold"]`; `app.js` reproduce MP3 local y envía acción |
| Cuatro operaciones (create, price, image, sold) | Implementado | `app.js` `send()` → `POST /api/bot/message`; backend #3 gestiona CREATE/PRICE/IMAGE/SOLD con confirmación |
| Cancelar | Implementado | `app.js` `$('no').onclick = () => send('No')` |
| Deshacer | Implementado | `app.js` `$('undo').onclick = () => send('Me equivoqué')` |
| Restaurar catálogo original | Implementado | `app.js` `$('reset').onclick` → `POST /api/reset` |
| Desde navegador | Implementado | Todo es frontend estático + API REST, sin credenciales |
| Sesiones aisladas | Implementado | `app.js` sesión sandbox `localStorage` + `sessionId` en cada request |
| Confirmación explícita | Implementado | Backend #3 FSM: AWAITING_CONFIRMATION requiere sí/no antes de aplicar |

---

## 3. Conclusión de diseño #7

**Issue #7 ya está resuelta por la implementación de #6.** No hay archivos nuevos que crear ni código de producción que modificar. Los criterios de aceptación de #7 se verifican contra el código existente en `main`:

- Cuatro operaciones ejecutables desde el navegador ✓
- Cancelar, deshacer, restaurar ✓
- Micrófono y carga de foto ✓
- Cuatro ejemplos con audio local ✓
- Sin credenciales, sesiones aisladas ✓

**No se requiere Fase 2 (construcción) ni Fase 3 (QA de código nuevo) para #7.** El QA de #7 quedó cubierto por el QA de #6 (V8 = regresión simulador PASS, 137 tests).

Siguiente issue disponible: **#8** (Verificación integral y despliegue reproducible), que depende de #7 (ahora verificada).

Commit autorizado para este documento únicamente en rama `GonzaCarrillo/issue-7-simulator` (no `main`).
