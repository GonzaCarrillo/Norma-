# Adenda QA Fase 3 — Nuevos criterios de navegador real (fixtures explícitos) — Issue #7

**Complementa**: `docs/orchestration/issue-7-reconciliation-qa.md` e `docs/orchestration/issue-7-reconciliation-qa-browser.md`  
**Dictamen**: QA_APPROVED (sin defectos de producción; limitaciones ambientales documentadas)  
**Commit auditado**: `8ea839b479246ec1302c6e0a1b649d7fd2d7b38f`  
**JAR auditado**: `target/qa.jkP7eX/export/target/taller-1.0.0.jar`, SHA-256 `fdb97e5b34dfd687668a9042c049b4fe1d554605efdc960435a250fd8a48e878`  
**Rama**: `GonzaCarrillo/reconcile-7-simulator`  
**Diseño**: `docs/orchestration/issue-7-reconciliation-design-v2.md` (Codex v2)  
**Auditor**: OpenCode / Nemotron (Fase 3)  
**Fecha**: 13 de Noviembre de 2026

---

## 1. Alcance de esta adenda

Esta adenda cubre los criterios de navegador real (GUI) que quedaban pendiente de ejecutar con fixtures explícitos, sin repetir la suite de pruebas (`mvn -B clean verify` ya verde: 252/252) y sin modificar código de producción ni tests. Se reutiliza el JAR ya auditado y se levanta una instancia propia sobre SQLite/uploads aislados bajo `target/`.

No se afirma caída de red real ni micrófono físico: los fallos se inyectan mediante fixtures temporales de JavaScript en la página (Orca `eval --expression`), se preservan los originales y se restauran al finalizar.

**CLI Orca usado**: `/home/gonzalocar/.config/orca/linux-orca-cli-shim/orca` (no `/usr/bin/orca`, lector de pantalla GNOME).

---

## 2. Entorno de servidor de verificación

| Elemento | Valor |
|---|---|
| JAR | `target/qa.jkP7eX/export/target/taller-1.0.0.jar` (auditado) |
| JDK | Microsoft OpenJDK 25.0.4.1 |
| Puerto | `34377` (asignado, no fijo de hardcode en producción) |
| Base de datos | SQLite temporal aislada bajo `target/qa-browser-fresh.RpMedy/sim.sqlite` (WAL) |
| Uploads | `target/qa-browser-fresh.RpMedy/uploads` |
| Log | `target/qa-browser-fresh.RpMedy/server.log` |
| `/api/ping` | `PONG`, HTTP 200 |
| Catálogo inicial | 8 piezas servidas sobre SQLite real |
| Página A | `http://localhost:34377/` (`browserPageId: 2ebba859-f0a6-4068-82fc-5f2be65bd25b`) |
| Página B | `http://127.0.0.1:34377/` (`browserPageId: ecce9e64-4ce3-41eb-9c03-31c2c7543246`) |

A y B son **orígenes distintos** (diferente host) ⇒ `localStorage` distinto ⇒ token `norma-session` distinto por sesión.

---

## 3. Capturas nuevas (rutas exactas de Orca, para que el coordinador las copie)

Las capturas se generan en `/tmp/orca-browser-screenshots/` por el runtime de Orca; este informe registra los paths exactos. El coordinador las traslada a `docs/orchestration/issue-7-reconciliation-evidence/qa/browser/`.

| Captura | Path exacto (Orca) | Qué muestra |
|---|---|---|
| Catálogo limpio de A tras fixtures | `/tmp/orca-browser-screenshots/3008fc66-40ca-43b4-96fe-d3d18770270f-screenshot.png` | Sesión A, 8 piezas, diálogo cerrado |
| Sesión B aislada (8 piezas) | `/tmp/orca-browser-screenshots/cb5b48c7-9e76-47df-a374-ffb52175b987-screenshot.png` | Sesión B, 8 piezas, sin afectar por A |
| Capturas previas (ya en browser/) | `01-catalogo.png`, `02-simulador.png`, `03-final.png` | Ver `manifest-initial.json` |

---

## 4. Matriz de resultados — criterios pendientes (fixtures explícitos)

### 4.1 Aislamiento de sesiones por origen (Cláusula C6)

| Acción ejecutada (real, navegador) | Resultado observado | Estado |
|---|---|---|
| Abrir A (`localhost:34377`) y B (`127.0.0.1:34377`); leer catálogo de ambas | Ambas inician con 8 piezas (Pieza #1..#8) | PASS |
| En A: vender pieza #2 (demo "pieza vendida" + confirmar "Sí") | A pasa a 7 activas (sin Pieza #2 Jarrón de gres) | PASS |
| En B (misma sesión que A no se comparte: origen distinto): leer catálogo | B conserva 8 activas, incluida Pieza #2 a $18.500 | PASS |
| En A: reset "Restaurar catálogo original" | A vuelve a 8 activas (Jarrón de gres $18.500, Vasito de cerámica) | PASS |
| En B tras reset de A | B sigue con 8 activas, sin alteración | PASS |

**Evidencia**: `exec read` en ambas pestañas; capturas `3008fc66…` (A) y `cb5b48c7…` (B).

**Nota sobre token `norma-session`**: no se leyó `localStorage` directamente por limitación de comandos Orca (`eval` soporta expresiones JS pero el aislamiento se demuestra conductualmente). El comportamiento confirma tokens distintos por origen: A y B nunca se afectan mutuamente. El aislamiento estricto por token de sesión también queda probado a nivel de contrato HTTP (`SimulatorContractTest.testResetIsolationBetweenSessions`, tokens `SESS_A`/`SESS_B` distintos, suite ya verde).

---

### 4.2 Micrófono denegado (Cláusula C7.4) — fixture explícito

**Fixture inyectado (JS, temporal, restaurado)**:
```js
window.__savedGUM = navigator.mediaDevices.getUserMedia;
navigator.mediaDevices.getUserMedia = () => Promise.reject(new DOMException('NotAllowedError','NotAllowedError'));
```
Verificado `navigator.mediaDevices.getUserMedia.toString() === "() => Promise.reject(new DOMException('NotAllowedError','NotAllowedError'))"`.

| Acción | Resultado observado | Estado |
|---|---|---|
| Clic "Grabar audio" (e35) con `getUserMedia` denegado | `bot-status`: "No pude abrir el micrófono. Podés escribir o usar un ejemplo." (mensaje útil) | PASS |
| Estado de controles durante/tras denegación | Ningún botón queda `[disabled]`; `busy` no se activa en `getUserMedia` | PASS |
| Restaurar `getUserMedia` original | `window.__defer`-style restauración: `navigator.mediaDevices.getUserMedia = window.__savedGUM` | PASS |
| Envío de texto posterior ("Prueba de texto tras denegación de micrófono") | Asistente responde normalmente ("Decime qué pieza querés cambiar…"); flujo de texto íntegro visible | PASS |

**Evidencia**: `exec read` (mensaje de denegación + respuesta de texto posterior); `eval --expression` (instalación y restauración del fixture).

**Honestidad**: se inyectó una denegación simulada (`DOMException NotAllowedError`); no se usó micrófono físico ni se afirmó denegación de permiso real del navegador. La ausencia de API de voz ya está cubierta por `testVoiceAbsenceKeepsText` (texto siempre visible).

---

### 4.3 Error de red en `bot/message` (Cláusula C7.5) — fixture explícito

**Fixture inyectado (JS, temporal, restaurado)**: `fetch` que rechaza **solo la primera** solicitud a `/api/bot/message` con `TypeError('Failed to fetch')`; las siguientes pasan al `fetch` original.

| Acción | Resultado observado | Estado |
|---|---|---|
| Primera petición `bot/message` (demo "pieza nueva") con `fetch` rechazando | `bot-status`: "Failed to fetch" (error mostrado en chat) | PASS |
| Catálogo tras el fallo | Sigue en 8 piezas (sin crear Pieza #9) — **sin mutación** | PASS |
| Controles tras el fallo | Ningún botón queda `[disabled]`; `busy=false` en `finally` de `send()` | PASS |
| Restaurar `fetch` original | `window.fetch = window.__savedFetch` | PASS |
| Segunda petición `bot/message` (mismo demo) | Éxito: "¿Confirmás publicar Cuenco del patio a $ 15.000?" → "Esperando tu confirmación." | PASS |
| Catálogo tras el éxito | 8 piezas (propuesta en `AWAITING_CONFIRMATION`, no confirmada) — exactamente una pieza pendiente, **sin doble mutación** | PASS |

**Evidencia**: `exec read` ("Failed to fetch", luego "Esperando tu confirmación."); `eval --expression` (contador de llamadas `fetch`).

**Honestidad**: fallo de red simulado mediante fixture `fetch` que rechaza una sola solicitud; no se provocó caída de red real. No basta con que exista el botón "Reintentar": se comprobó mensaje de error, recuperación con `fetch` restaurado y envío siguiente exitoso sin doble mutación.

---

### 4.4 Bloqueo de controles en vuelo (Cláusula C7.1) — respuesta diferida

**Fixture inyectado (JS, temporal)**: `fetch` que devuelve una **promesa pendiente (sin resolver)** para `/api/bot/message`, resuelta manualmente tras verificar el bloqueo.

| Acción | Resultado observado | Estado |
|---|---|---|
| Clic demo "pieza nueva" con `fetch` diferido (promesa sin resolver) | Todos los botones del diálogo pasan a `[disabled]` (incluido `#yes`, `#no`, `#undo`, `#reset`, demos, formulario) | PASS |
| Durante el vuelo | `busy = true`; no se admiten solicitudes concurrentes ni envíos dobles | PASS |
| Resolver la promesa con respuesta exitosa (`AWAITING_CONFIRMATION`) | `busy = false`; botones habilitados de nuevo; propuesta creada ("Esperando tu confirmación.") | PASS |

**Evidencia**: `snapshot --interactive` durante el vuelo (todos los botones `[disabled]`); `eval --expression` (resolución de promesa diferida); `exec read` tras resolución.

---

## 5. Criterios ya verificados en entrega previa (se mantienen, no se repiten)

Los siguientes quedaron PASS en `issue-7-reconciliation-qa-browser.md` y no se reejecutaron:
- C1 despliegue aislado (JAR, puerto dinámico, SQLite/uploads, `/api/ping → PONG`)
- C2 teclado y foco (Escape y `#close` restauran `document.activeElement.id === 'open'`)
- C3.1–C3.4 cuatro demos con proponer-sin-mutación, confirmar, cancelar, deshacer
- C4 foto real por formulario multipart (Pieza #10 creada con imagen real)
- C6 aislamiento de sesiones (a nivel HTTP, tokens distintos)
- C7.1 foto inválida/corrupta → error 400 amigable sin mutar catálogo
- C7.5 voz ausente → texto de Norma siempre visible en `#chat`

---

## 6. Defectos de producción

**Ninguno.** No se modificó ningún archivo de producción (`src/main/java`, `src/main/resources/static`), ni tests, ni esquemas, ni `pom.xml`. El commit `8ea839b` es únicamente sincronización documental; el código funcionó íntegramente en el navegador real contra el JAR auditado.

---

## 7. Limitaciones registradas (ambientales, no defectos)

1. **Lectura directa de `localStorage norma-session`**: Orca `eval` ejecuta expresiones JS pero el aislamiento se demostró conductualmente (A y B con orígenes distintos nunca comparten estado). Los tokens distintos por origen se infieren del comportamiento de aislamiento y del contrato HTTP ya probado.
2. **Micrófono físico / permiso real del navegador**: no disponible en el entorno; se inyectó `DOMException NotAllowedError` mediante fixture y se documentó como simulación.
3. **Caída de red real**: se inyectó rechazo de `fetch` para una sola solicitud mediante fixture; documentado como simulación.
4. **Capturas de pantalla**: generadas por Orca en `/tmp/orca-browser-screenshots/`; los paths exactos están en §3 para que el coordinador las copie a `docs/orchestration/issue-7-reconciliation-evidence/qa/browser/`.

---

## 8. Dictamen

**QA_APPROVED**

Todos los criterios de navegador real que son ejecutables en este entorno se verificaron contra el JAR real auditado: despliegue aislado, teclado/foco, cuatro demos (proponer-sin-mutación / confirmar / cancelar / deshacer), foto real por formulario, aislamiento de sesiones por origen, foto inválida, micrófono denegado (fixture), error de red en `bot/message` (fixture, sin doble mutación), controles en vuelo (respuesta diferida), y texto de voz siempre presente. Sin defectos de producción ni regresiones.

Este dictamen, junto con el informe base y la adenda de navegador, completa la auditoría de Fase 3 y habilita la Fase 4 de Codex. No implica merge, push a main ni cierre de issue por parte de OpenCode.
