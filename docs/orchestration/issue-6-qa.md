# QA Independiente — Issue #6: Vidriera accesible con SSE

**Agente auditor:** OpenCode (OpenCode, modelo nvidia/nvidia/nemotron-3.5-super-vl-120b-a12b, verificado por el usuario)  
**Rama / worktree:** `GonzaCarrillo/issue-6-storefront` — `/home/gonzalocar/orca/workspaces/Norma-/issue-6-storefront`  
**Commit auditado (builder Antigravity):** `ddcd19f86782be73ba9decc7fe8e529ee2bc270c`  
**Diseño de referencia:** Codex v1.0 `docs/orchestration/issue-6-design.md` (base `e0f6eada669b9b5070f64688a028a7cd946a7ddd`)  
**Registro de aprobación:** `docs/orchestration/issue-6-approval.md` (gate parcial B1→B2 aprobado; V1-zoom200 pendiente para QA)  
**Reporte de construcción (builder):** `docs/orchestration/issue-6-build-report.md`  
**Fecha:** 2026-11-13

---

## 1. Veredicto global

**BLOQUEADO (no PASS total)** por el criterio residual **V1-zoom200 (pageZoom nativo 200% del navegador)**.

Todas las demás verificaciones de la matriz V1–V8, los contratos REST/SSE, la compilación y la regresión simulador están **PASS**. El criterio V1-zoom200 no puede acreditarse en este entorno porque la CLI de Orca (1.4.257) no expone comando/documento de zoom de página completo (pageZoom) y las capacidades de computación en Linux reportan `hotkey=false` y `menus=false`. CSS `zoom=2`, DPR=2 y reflow WCAG 1.4.10 se verificaron como **técnicas complementarias**, nunca como sustituto de pageZoom 200% con indicador nativo de menú. Conforme al residual acordado Orca–Codex, el merge y el cierre de la issue quedan **bloqueados** hasta que OpenCode realice la verificación en navegador de escritorio con menú indicador nativo 200% en la misma ventana durante medición y captura.

No se modificó código de producción, pruebas versionadas, POM, configuración global ni evidencia del builder. Este informe y sus evidencias propias son los únicos archivos nuevos.

---

## 2. Entorno y recursos

| Ítem | Valor |
|---|---|
| JDK | Microsoft OpenJDK 25.0.4.1+1-LTS (`/home/gonzalocar/.jdks/ms-25.0.4.1`), `java.specification.version=25` |
| Maven | Apache Maven 3.8.7 |
| SO | Linux 6.14.0-37-generic (amd64) |
| Navegador | Firefox 158.0.1 (embebido orca-cli), pestaña estable `438df00d-55cb-4690-b736-a96419cee582` |
| CLI Orca | `/home/gonzalocar/.config/orca/linux-orca-cli-shim/orca` v1.4.257 (`ORCA_CLI_COMMAND`) |
| Puerto servidor QA | Dinámico (`--server.port=0`), efectivo `36863` (navegador) y `37225` (smoke JAR) |
| DB/uploads/tmp | Aislados bajo `target/` propio (`browser-runtime`, `smoke-runtime`, `issue6-runtime`), nunca recursos de #4 ni puertos 8080/compose |
| groq.api.key | Vacío (`""`) en todas las ejecuciones; MediaService stub (mock, `root` bajo `target`) solo en tests de contrato/SSE |
| Sin red | Sin Groq, sin TTS de asistente, sin procesador #4; `edge-tts` stub local que falla inmediatamente sin red |

---

## 3. Compilación y regresión (independiente, re-ejecutada por QA)

Comando ejecutado (desde raíz del worktree, JDK 25):

```bash
mvn -B clean
mkdir -p target/issue6-runtime/tmp target/issue6-runtime/uploads
mvn -B verify \
  -DargLine="-Djava.io.tmpdir=$(pwd -P)/target/issue6-runtime/tmp" \
  -Dnorma.uploads="$(pwd -P)/target/issue6-runtime/uploads" \
  -Dgroq.api.key=
```

**Resultado:** `EXIT_CODE=0`, `BUILD SUCCESS`, duración `02:18 min`.

Desglose Surefire (export y local coinciden): **13 suites, 137 tests, 0 fallos, 0 errores, 0 omitidos**.

| Suite | Tests | Fallos | Errores | Omitidos |
|---|---:|---:|---:|---:|
| BotApiTest | 6 | 0 | 0 | 0 |
| BotEngineTest | 30 | 0 | 0 | 0 |
| BotPersistenceTest | 1 | 0 | 0 | 0 |
| BotSessionCodecTest | 15 | 0 | 0 | 0 |
| CatalogMigrationTest | 3 | 0 | 0 | 0 |
| CatalogPersistenceTest | 3 | 0 | 0 | 0 |
| CatalogRepositoryTest | 7 | 0 | 0 | 0 |
| FlowTest | 4 | 0 | 0 | 0 |
| IntentParserTest | 61 | 0 | 0 | 0 |
| StorefrontContractTest | 6 | 0 | 0 | 0 |
| StorefrontSseTest | 1 | 0 | 0 | 0 |
| **Total** | **137** | **0** | **0** | **0** |

`basedir` en los XML apunta a la exportación del commit `ddcd19f`. `groq.api.key=""`, `java.io.tmpdir` y `norma.uploads` bajo `target/issue6-runtime` propios. Ningún contexto usó DB/uploads ajenos.

Hashes del JAR generado en esta verificación QA (distinto del builder por recompilar localmente): `a979b168e4fbe22e8ab3c96ede34e23e8288fab406f67470f372baf727a8a738`.  
Hashes del export limpio del builder (verificados, coinciden con `issue-6-build-report.md`): verify.log `8a85c0995b9bceca743358630e9ccaaa6b3fde4fda2abebc7f4a07b221ae5682`, JAR `737a8462f8f80b3648a6d6d99ea0f7b62595e3bf9cba91671b3e90a17264566d`, `EXIT_CODE=0`.

---

## 4. Smoke test del artefacto (independiente)

`java --enable-native-access=ALL-UNNAMED -jar target/taller-1.0.0.jar` con DB SQLite y uploads aislados bajo `target/smoke-runtime`, puerto dinámico `37225`, `groq.api.key=`.

- `curl --fail http://127.0.0.1:37225/api/ping` → `PONG` (HTTP 200).
- `GET /api/products?sessionId=smoke_sess` → array JSON con 8 piezas no vendidas (pieceNumber 1..8).
- Proceso detenido con `kill`; **status real de `wait` capturado: `143`** (SIGTERM, cierre ordenado). No se usó `wait … || true`.

---

## 5. Matriz de aceptación V1–V8

| ID | Criterio | Veredicto | Evidencia / notas |
|---|---|---|---|
| V1 | Grilla, viewports 320/375/768/1440, títulos 10/60, reflow 640/320, igualdad alturas ≤1px, sin scroll horizontal | **PASS** | QA independiente vía `orca-cli` (viewport/eval/screenshot): 8 tarjetas, diffHeight 0.00 px, proporción 4:3, sin scroll H en todos los viewports; títulos 10/60/palabra larga sin romper filas; reflow 640 y 320 sin scroll horizontal. |
| V1 (sub) | **pageZoom nativo 200% con indicador de menú** | **BLOQUEADO** | No existe comando/documento de pageZoom en orca-cli 1.4.257; capabilities Linux `hotkey=false`, `menus=false`. CSS `zoom=2` (diffHeight 0), DPR=2 y reflow verificados como técnicas complementarias **sin declararlos PASS de pageZoom**. |
| V2 | Tipografía ≥22px normal, ≥28.6px (≥30%) modo grande en raíz; contraste textual >7:1 estados/hover/focus/disabled/placeholder | **PASS** (tipografía) / revisado (contraste) | Tipografía QA independiente: raíz 22 px, modo grande 28.6 px, ratio 1.30, todos los elementos ≥22/28.6. Contraste: builder evidenció 12 pares todos >7 estrictamente (mín 12.01:1, requerido >7; foco 16.45:1 req ≥3). QA no repitió contraste por limitación de estilos calculados headless; aceptado como evidencia recibida revisada, no asumida ciegamente. |
| V3 | Solo teclado: skip link, foto, Enter/Space, Escape, cierre, retorno foco, modal durante SSE, pieza vendida, superposición simulador/zoom | **PASS** | Evidencia builder `v1_v5_evidence.json` (V3) revisada: skip→#piezas, photo→zoom foco en #close-zoom, Escape restaura foco, Space abre, cierre automático al vender, simulador cierra zoom y retorna foco a #open. Capturas `v3_zoom_modal.png`, `v3_mobile_zoom_modal.png`. |
| V4 | Voz nativa: presente/ausente/error/fin/stop, lectura pieza y catálogo, cambio durante voz, fallback sin speechSynthesis | **PASS** | Evidencia builder `v1_v5_evidence.json` (V4) revisada: stub de ciclo utterance (`lang=es-AR`, cancel, onend, onerror), fallback visible sin excepción, límites ambientales declarados (voces dependen del SO). |
| V5 | Carga, `[]` válido, HTTP 500, red caída, JSON inválido, foto fallida, reintento, datos previos marcados obsoletos | **PASS** | Evidencia builder `v1_v5_evidence.json` (V5) revisada: catálogo vacío 0 tarjetas, error con 8 tarjetas retenidas + aviso obsoleto + botón Reintentar, foto fallida reserva 4:3 + texto explicativo sin loop. |
| V6 | SSE real, dos sesiones A/B, mutación externa vía API, actualización reactiva DOM sin recarga, precio/venta/undo/reset, aislamiento B, ráfaga de invalidaciones | **PASS** (contrato/SSE real) | `StorefrontSseTest` independiente en mi `mvn -B verify`: servidor HTTP real (`RANDOM_PORT`), Java `HttpClient` lector SSE, streams A y B, evento inicial `catalog-update/conectado`, propuesta sin confirmar (GET sin cambio), confirmación (precio 20000), venta (7 piezas), undo (repuesta), reset (8 canónicas), aislamiento B (750 ms ventana negativa + control positivo), reconexión snapshot tras corte (recupera `$ 35.000`), timeouts acotados, cierre de streams/futures en `finally`. Evidencia builder `verify_b2.py`/`v6_v8_evidence.json` (`v6_sse_price_updated.png`, `v6_sse_sold_removed.png`, `v6_sse_undo_restored.png`) revisada como recibida. |
| V7 | Corte/reconexión EventSource real, mutación offline recuperada por snapshot GET, ciclo bfcache pagehide/pageshow, una suscripción viva por página | **PASS** (contrato real) | `StorefrontSseTest` independiente: cierre de stream A, mutación offline vía API, reapertura recibe `conectado`, GET refleja cambio perdido. Evidencia builder `v7_reconnected_snapshot.png` revisada. EventSource ausente → GET manual documentado. |
| V8 | Regresión simulador existente (§7): IDs preservados, 4 ejemplos, confirmación/cancelación/undo/reset, micrófono fallback, foco #open al cerrar | **PASS** | Evidencia builder `verify_b2.py`/`v8_simulator_flow.png` (`v8_simulator_open.png`) revisada: abrir/cerrar modal, propuesta texto, cancelar #no, demo precio #yes, undo revierte, reset restaura 8, `#record` fallback sin errores de script, foco retorna a `#open`. |

**Resumen matriz:** V1=PASS (salvo zoom200 nativo BLOQUEADO), V2=PASS, V3=PASS, V4=PASS, V5=PASS, V6=PASS, V7=PASS, V8=PASS.

---

## 6. Contratos backend intactos

Se verificó que no se tocó `Api`, `MediaService`/`PhotoProcessor`, `Product`/`Session`/`Reply`, `Catalog`, `BotEngine`, SQL/migración/seed, POM, `application.properties` ni pruebas existentes. Los nuevos tests `StorefrontContractTest` y `StorefrontSseTest` usan `MediaService` mock con `root` bajo `target` (precedente `BotApiTest`), API/BotEngine/Catalog reales, `groq.api.key=""`, sin red Groq/TTS.

Contratos probados: `GET /api/products` (8 piezas, pieceNumber 1..8, precios enteros, sessionId, URLs locales), `GET /api/catalog/stream` (`text/event-stream`, `catalog-update` `conectado`/`actualizado`), `POST /api/bot/message` (multipart, 400 sesión inválida/DEFAULT, AWAITING_CONFIRMATION, replyAudioUrl null), `POST /api/reset` (8 canónicas, aislamiento sesión), `GET /api/ping` → PONG. Validación de token: rechazo `DEFAULT`, <8 chars y caracteres inválidos con HTTP 400 `{"message":"La sesión no es válida."}`.

---

## 7. Evidencia propia de QA (no sobrescribe builder)

Guardada bajo `docs/orchestration/issue-6-evidence/` con nombres propios de QA:

- `docs/orchestration/issue-6-qa.md` — este informe.
- `target/qa-harness/qa_issue6.py` — harness de navegador QA (orca-cli) contra servidor real propio (puerto 36863).
- `target/qa-harness/v6_test.py` — prueba focal V6 (SSE real dos sesiones).
- `target/issue6-runtime/verify.log` — log completo `mvn -B verify` (EXIT_CODE=0).
- `target/surefire-reports/TEST-*.xml` — reports Surefire locales (137 tests, 0 fallos/0 errores).
- `target/taller-1.0.0.jar` — artefacto recompilado localmente (hash `a979b168…`).
- Capturas QA propias (V1): `qa_v1_viewport_{320,375,768,1440}.png`, `qa_v1_titles.png`, `qa_v1_css_zoom_2x.png`, `qa_v1_dpr_2.png` (estas últimas registradas como técnicas complementarias, **no** como pageZoom 200%).

Evidencia del builder revisada como recibida (no sobrescrita, no asumida como PASS propia): `v1_v5_evidence.json`, `v6_v8_evidence.json`, y los PNG `v1_*`, `v2_*`, `v3_*`, `v4_*`, `v5_*`, `v6_*`, `v7_*`, `v8_*`.

---

## 8. Bloqueo concreto (V1-zoom200) y límites

**Bloqueo:** pageZoom nativo 200% del navegador con indicador de menú no acreditado.
- Motivo: orca-cli 1.4.257 no documenta comando/flag de zoom de página completa (`--zoom` no existe en `orca viewport` ni `orca browser`).
- Capacidades Linux de computación: `hotkey=false`, `menus=false` → no se puede disparar menú de zoom nativo ni leer indicador 200%.
- CSS `document.body.style.zoom='2'` (verificado, diffHeight 0), DPR=2 y reflow 640/320 son **técnicas complementarias**; no equivalen a pageZoom y **no se declaran PASS** de V1-zoom200.
- Acción requerida: verificación en navegador de escritorio con menú indicador nativo 200%, misma ventana durante medición y captura, reportada a Orca. Hasta entonces, merge y cierre de la issue quedan bloqueados (residual acordado Orca–Codex).

Límite honesto: contraste textual verificado mediante evidencia del builder (estilos calculados getComputedStyle); QA no repitió el medidor de contraste headless por limitación de fondos calculados en el entorno. Voces y reproducción dependen del navegador/gesto/OS del usuario (límite ambiental declarado por el builder, no excusa para omitir regresión textual, que sí se verificó).

---

## 9. Conclusión

La implementación de Antigravity en `ddcd19f` cumple los criterios V1–V8, contratos y transporte SSE real, con compilación limpia (137/0) y regresión simulador. La única impedimento para el dictamen PASS total y para el merge/cierre es **V1-zoom200 pageZoom nativo 200%**, declarado BLOQUEADO pendiente de verificación en navegador real con indicador de menú. No se reportan defectos de producción; no se modificó código de producto.

Commit autorizado para este informe y evidencias propias únicamente en rama `GonzaCarrillo/issue-6-storefront` (no `main`).
