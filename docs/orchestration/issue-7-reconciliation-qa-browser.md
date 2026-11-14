# Adenda QA Fase 3 — Verificación en Navegador Real (Issue #7)

**Dictamen**: QA_APPROVED (con limitaciones ambientales documentadas, sin defectos de producción)  
**Commit auditado**: `8ea839b479246ec1302c6e0a1b649d7fd2d7b38f`  
**Rama**: `GonzaCarrillo/reconcile-7-simulator`  
**Diseño de referencia**: `docs/orchestration/issue-7-reconciliation-design-v2.md` (Codex v2)  
**Informe QA base**: `docs/orchestration/issue-7-reconciliation-qa.md` (252/252 pruebas, `SimulatorContractTest` 9/9)  
**Auditor**: OpenCode / Nemotron (Fase 3, QA & Test Auditor)  
**Worktree**: `/home/gonzalocar/orca/workspaces/Norma-/reconcile-7-simulator`  
**Fecha**: 13 de Noviembre de 2026

---

## 1. Objetivo de la adenda

Completar la brecha de verificación en navegador real (GUI) que el informe base no pudo ejecutar por falta de control de navegador en el entorno. Se usa el ejecutable Orca CLI válido de la sesión (`/home/gonzalocar/.config/orca/linux-orca-cli-shim/orca`) con comandos `tab`/`goto`/`snapshot`/`click`/`fill`/`upload`/`keypress`/`eval`/`screenshot` sobre una instancia real del JAR empaquetado.

No se modificó código de producción ni tests. No se repitió `clean verify` (sin cambio de código). El JAR auditado es el mismo del informe base.

---

## 2. Entorno y servidor de verificación

| Elemento | Valor |
|---|---|
| JAR empaquetado | `target/qa.jkP7eX/export/target/taller-1.0.0.jar` (auditado, SHA-256 `fdb97e5b…48e878`) |
| JDK | Microsoft OpenJDK 25.0.4.1 (`/home/gonzalocar/.jdks/ms-25.0.4.1/bin/java`) |
| Puerto | Dinámico `34377` (asignado libre, no fijo) |
| Base de datos | SQLite temporal aislada bajo `target/qa-browser.jG3pUu/sim.sqlite` (WAL) |
| Uploads | Directorio temporal aislado bajo `target/qa-browser.jG3pUu/uploads` |
| Log del servidor | `target/qa-browser.jG3pUu/server.log` |
| Raíz QA propia | `target/qa-browser.jG3pUu/` (todo bajo el checkout, nada en `/tmp`) |
| `/api/ping` | `PONG`, HTTP 200 |
| Catálogo inicial | 8 piezas servidas sobre SQLite real |

Todo el estado de servidor (DB, uploads, logs, manifiesto) vive bajo `target/qa-browser.jG3pUu/` dentro del worktree; no se usó `/tmp` para datos de prueba.

---

## 3. Ejecución en navegador real

Se abrió una pestaña real del navegador integrado de Orca contra `http://localhost:34377/` (`browserPageId: 56f0b4cb-0d0f-4a1d-a6b2-02eb7d26f63c`) y se ejecutaron acciones reales (clic, fill, upload, keypress, eval) con snapshots y lecturas del DOM/texto de página.

**CLI Orca usado**: `/home/gonzalocar/.config/orca/linux-orca-cli-shim/orca` (no `/usr/bin/orca`, que es el lector de pantalla GNOME).

**Manifiesto de acciones y capturas**: `target/qa-browser.jG3pUu/evidence/manifest.json` (matriz por cláusula, acción, resultado, evidencia, estado). Las capturas de pantalla generadas por Orca residen en `/tmp/orca-browser-screenshots/` y se identifican por path exacto en el manifiesto; el coordinador las traslada a evidencia de checkout.

---

## 4. Matriz de resultados (navegador real)

| Cláusula | Acción ejecutada (real) | Resultado observado | Estado |
|---|---|---|---|
| C1 despliegue | Levantar JAR puerto dinámico + SQLite/uploads aislados | `PONG 200`; 8 piezas; sin credenciales externas | PASS |
| C2 teclado/foco | Abrir `#open`; cerrar con `Escape`; `document.activeElement.id` | Diálogo cierra; foco vuelve a `#open` | PASS |
| C2 cierre por botón | Clic `#close` "Volver al catálogo"; `document.activeElement.id` | Diálogo cierra; foco vuelve a `#open` | PASS |
| C3.1 create | Demo "pieza nueva" → propuesta → "Sí, confirmo" | Pieza #9 creada; "¡Listo Norma! Ya quedó actualizado en la página." | PASS |
| C3.1 cancelar | Demo create → "No, cancelar" | "Cancelé el cambio"; catálogo sin mutar; IDLE | PASS |
| C3.1 undo create | Confirmar create → "Me equivoqué: deshacer" | Vuelve a 8 piezas; "ya dejé todo como antes." | PASS |
| C3.2 price | Pieza #2 a $20.000 → confirmar | Precio $20.000 en catálogo | PASS |
| C3.2 undo price | Deshacer tras confirmar precio | Vuelve a $18.500 | PASS |
| C3.3 image | Demo "foto nueva" → confirmar | Foto pieza #2 actualizada | PASS |
| C3.3 undo image | Deshacer tras confirmar foto | Foto revertida a original | PASS |
| C3.4 sold | Vender pieza #2 → confirmar | Pieza #2 retirada (7 activas) | PASS |
| C3.4 undo sold | Deshacer tras confirmar venta | Pieza #2 reactivada (8 activas) | PASS |
| C4 foto real formulario | Subir PNG real + texto multipart → confirmar | Pieza #10 "Cuenco de prueba con foto real $12.000" creada con imagen | PASS |
| C6 aislamiento sesiones | Segunda pestaña (misma origen): 8 piezas; venta en A se refleja en B | Mismo origen comparte sessionId por defecto (estado compartido). Aislamiento por token de sesión verificado a nivel HTTP (`SimulatorContractTest` con tokens SESS_A/SESS_B distintos). No reproducible por pestaña en un mismo perfil sin tokens distintos — limitación ambiental del navegador, no defecto de backend. | LIMITACIÓN_DOCUMENTADA |
| C7.1 foto inválida | Subir archivo no-imagen (corrupto) + enviar | "Mandame una foto JPEG, PNG o WebP válida..." (error amigable); catálogo sin mutar | PASS |
| C7.4 micrófono ausente/denegado | Clic "Grabar audio" | Botón interactuable; no se pudo reproducir denegación de `getUserMedia` (entorno sin micrófono ni diálogo de permisos del navegador). Limitación ambiental, no defecto. | LIMITACIÓN_AMBIENTAL |
| C7.5 voz ausente / error y reintento | Texto de todas las respuestas visible en chat; botón "Reintentar" presente | Texto íntegro de Norma siempre visible; "Reintentar" disponible | PASS |

---

## 5. Honestidad de medios vs STT real

Los botones demo reproducen audio local y inyectan texto canónico; esto no se presenta como transcripción STT en tiempo real. Se confirmó que el texto de cada respuesta de Norma permanece íntegramente visible en el registro `#chat` independientemente del audio (degradación elegante). No se afirmó STT real sin prueba.

---

## 6. Defectos de producción

Ninguno. No se modificó ningún archivo de producción (`src/main/java`, `src/main/resources/static`), ni tests, ni esquemas, ni `pom.xml`. El commit `8ea839b` es únicamente sincronización documental; el código funcionó íntegramente en el navegador real.

---

## 7. Limitaciones registradas

1. **Aislamiento de "sesión B" por pestaña**: en un mismo perfil de navegador/origen, dos pestañas comparten la `sessionId` por defecto, por lo que reflejan el mismo estado. El aislamiento estricto por token de sesión sí quedó probado a nivel de contrato HTTP (`SimulatorContractTest.testResetIsolationBetweenSessions` con tokens `SESS_A`/`SESS_B` distintos). Para acreditar aislamiento gráfico entre dos sesiones distintas se requeriría forzar `sessionId` diferente por pestaña o usar perfiles de navegador aislados, lo cual no está disponible en este entorno.
2. **Micrófono denegado**: el entorno de ejecución no dispone de dispositivo de micrófono ni muestra el diálogo de permisos del navegador, por lo que no se pudo reproducir la denegación de `getUserMedia` de forma real. Se verificó que el botón "Grabar audio" es interactuable y no rompe la UI; la degradación de voz está cubierta por `testVoiceAbsenceKeepsText`.
3. **Capturas de pantalla**: generadas por Orca en `/tmp/orca-browser-screenshots/` (almacenamiento interno del runtime). Se registran los paths exactos en el manifiesto (`target/qa-browser.jG3pUu/evidence/manifest.json`) para que el coordinador las traslade a evidencia de checkout. No se leyó ni se escribió contenido ajeno en `/tmp`.

---

## 8. Dictamen

**QA_APPROVED** (con las dos limitaciones ambientales documentadas arriba, que no son defectos de producción).

Toda la funcionalidad del contrato Codex v2 que es ejecutable en este entorno se verificó contra el JAR real en navegador: despliegue aislado, teclado/foco, cuatro demos con proponer-sin-mutación/confirmar/cancelar/deshacer, foto real por formulario multipart, manejo de foto inválida, y texto de voz siempre presente. No hay defectos de producción ni regresiones.

Este dictamen, junto con el informe base (`issue-7-reconciliation-qa.md`) y el manifiesto de evidencia (`target/qa-browser.jG3pUu/evidence/manifest.json`), completa la auditoría de Fase 3 y habilita la Fase 4 de Codex. No implica merge, push a main ni cierre de issue por parte de OpenCode.
