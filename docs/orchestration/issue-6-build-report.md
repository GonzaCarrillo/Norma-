# Reporte de Construcción — Issue #6: Vidriera accesible con SSE

**Fecha:** 2026-11-13  
**Agente:** Antigravity (Software Builder / Implementer)  
**Coordinador:** Orca (Lead Software Engineer y Orquestador Técnico)  
**Tarea / Dispatch:** `task_b20a8cdcab02` / `ctx_b91142bc5242`  
**Worktree:** `/home/gonzalocar/orca/workspaces/Norma-/issue-6-storefront`  
**Rama:** `GonzaCarrillo/issue-6-storefront`  
**Diseño de referencia:** Codex v1.0 (`docs/orchestration/issue-6-design.md`, hash base `e0f6eada669b9b5070f64688a028a7cd946a7ddd`)  
**Registro de aprobación:** `docs/orchestration/issue-6-approval.md`  

---

## 1. Inventario cerrado de archivos y alcance (§10)

En estricto cumplimiento de la sección §10 de la especificación técnica aprobada, la construcción se restringió exclusivamente a los siguientes archivos autorizados:

1. `src/main/resources/static/index.html` (interfaz semántica, controles de accesibilidad, diálogo modal nativo, skip-link, preservación del simulador).
2. `src/main/resources/static/style.css` (estilos accesibles, `:root` 22px / ampliado 28.6px, grilla uniforme `grid-auto-rows: 1fr`, contraste estricto > 7:1, reflow móvil 320px).
3. `src/main/resources/static/app.js` (gestión de sesión sandbox, formato `$ 18.500`, síntesis de voz nativa `speechSynthesis`, reconciliación DOM por `pieceNumber`, coordinador SSE con deduplicación y número de generación).
4. `src/test/java/com/norma/taller/StorefrontContractTest.java` (nueva suite de contrato Spring Boot + MockMvc con base de datos SQLite aislada bajo `target`).
5. `src/test/java/com/norma/taller/StorefrontSseTest.java` (nueva suite de transporte SSE con Spring Boot `RANDOM_PORT`, servidor HTTP real y `HttpClient`).
6. `docs/orchestration/issue-6-approval.md` (registro formal de aprobaciones de diseño y gates).
7. `docs/orchestration/issue-6-build-report.md` (este documento).
8. `docs/orchestration/issue-6-evidence/` (harnesses de verificación `verify_v1_v5.py`, `verify_b2.py`, resultados JSON y capturas PNG).

**Frontera respetada:** Ningún archivo productivo backend (`Api.java`, `MediaConfig`, `MediaService.java`, `PhotoProcessor.java`, `Catalog.java`, `BotEngine.java`, entidades, esquemas/semillas SQL, POM, configuración global `application.properties`) ni ninguna prueba existente fue modificada. No se realizaron cambios fuera de este worktree ni en la rama `main`.

---

## 2. Resumen de Fases y Commits

### Fase B1 — Front-end estático y accesibilidad (Commits `b354982`, `cd8e7f8`, `cd16641`, `2aa6ad8`)
- **V1 (Grilla, viewports y uniformidad):** Viewports probados a 320, 375, 768 y 1440 CSS px. Diferencia de altura entre tarjetas: 0.00 px (exacta uniformidad vertical mediante `min-height: 5.4em` en `h2` y `grid-auto-rows: 1fr`). Proporción de fotos 4:3 estricta (`aspect-ratio: 4 / 3; object-fit: cover`). Cero scroll horizontal en 320px. Títulos de 10 caracteres, 60 caracteres y palabras largas no se truncan ni rompen el diseño.
- **V2 (Tipografía y contraste sRGB WCAG AAA):** `:root` base 22px; modo ampliado eleva raíz a 28.6px (+30%). Medición de contrastes calculados en todos los componentes y estados: texto normal (13.22:1 a 17.78:1), hover de botones (12.02:1), botones deshabilitados (13.84:1), placeholders de inputs (13.29:1), contorno de foco (16.45:1). Todos superan estrictamente el umbral > 7:1.
- **V3 (Navegación sólo por teclado y diálogo modal):** Tabulación cíclica, skip-link visible hacia `#piezas`, apertura de zoom con Enter/Espacio sobre fotos interactivas. Diálogo nativo `<dialog id="zoom">` con foco inicial en botón `#close-zoom`, captura de Escape y retorno del foco al disparador original. Cierre automático al venderse la pieza en SSE.
- **V4 (Lectura por voz nativa):** Implementación con `window.speechSynthesis` y preferencia por voz `es-AR`. Controles `#listen` y `#silence` en barra superior y `#zoom-silence` dentro del modal. Ciclo de detención, error, fin y fallback visible cuando la API no está disponible en el navegador.
- **V5 (Estados de carga y tolerancia a fallos):** Gestión explícita de catálogo vacío (`[]`), error HTTP 500, servidor no disponible con preservación de datos anteriores y aviso de desactualización, fallback de foto rota sin alterar la grilla y botón `#retry` accesible.
- **Gate B1 → B2:** Aprobado formalmente por Orca (`msg_c42ce8a4bc12` y confirmación `msg_9e1b89291406`) tras consulta con Codex (`task_dc4c1cfb552f`).

### Fase B2 — Coordinador SSE, pruebas automatizadas y transporte reactivo (Commit `ddcd19f`)
- **Coordinador SSE (`app.js`):** Suscripción única a `/api/catalog/stream?sessionId=...`. Separación de estado de conexión (`conectando`, `abierta`, `reconectando`, `noDisponible`) y estado de datos (`cargando`, `listo`, `vacío`, `error`). Eventos SSE tratados exclusivamente como invalidaciones que disparan `requestRefresh()`. Mecanismo de deduplicación en vuelo (`inFlight`, `refreshPending`) y número generacional (`invalidationGen`) para evitar carreras por respuestas GET tardías. Reconciliación DOM conservando nodos por `data-piece-number` sin mover el foco activo. Ciclo de vida con `pagehide` (cierre de stream y cancelación de voz) y `pageshow` (reconexión limpia tras bfcache).
- **`StorefrontContractTest.java`:** 6 pruebas unitarias/integración MockMvc:
  1. `testStaticAssetsAndAccessibilityControls`: Recursos estáticos (`/`, `/index.html`, `/style.css`, `/app.js`) y existencia de todos los controles semánticos y de simulación.
  2. `testInitialProductsContract`: GET inicial devuelve 8 productos no vendidos con `pieceNumber` 1..8, precios enteros y estructura esperada.
  3. `testSessionValidationContract`: Validación de tokens de sesión; rechazo de `DEFAULT`, tokens cortos o caracteres inválidos con HTTP 400 `{ "message": "La sesión no es válida." }`.
  4. `testEmptyCatalogRemainsEmpty`: Catálogo vacío real mediante `Catalog.restore` permanece `[]` tras GET sin disparar clonación de semilla canónica.
  5. `testPriceProposalAndCancellationLifecycle`: Propuesta de precio no muta datos antes de confirmación; cancelación con "No" preserva precio; confirmación con "Sí" actualiza datos en GET; aislamiento respecto a sesión B.
  6. `testSoldUndoResetAndSessionBIsolation`: Venta omite pieza en GET; undo la repone; reset restaura las 8 canónicas; sesión B permanece intacta en todo momento.
- **`StorefrontSseTest.java`:** 1 prueba de transporte real sobre Spring Boot `RANDOM_PORT`:
  - Servidor HTTP real y Java `HttpClient` con lector de stream SSE continuo por líneas.
  - Apertura de streams concurrentes para sesiones A y B, verificación de Content-Type `text/event-stream` y evento inicial `catalog-update` / `conectado`.
  - Secuencia de 8 pasos con barreo de cola por paso: propuesta de precio -> evento `actualizado` -> GET sin cambio; confirmación -> evento -> GET con nuevo precio; venta -> pieza omitida; undo -> pieza repuesta; reset -> 8 canónicas restauradas.
  - Aislamiento estricto: la sesión B no recibe ningún evento durante la ventana negativa de 750 ms tras cada mutación de A. Control positivo en B verificado.
  - Desconexión forzada de stream A, mutación offline vía API, reapertura del stream recibiendo `conectado`, y recuperación del cambio perdido mediante snapshot GET.
- **Verificaciones de navegador V6, V7, V8 (`verify_b2.py`):**
  - **V6 (SSE real y actualización reactiva):** Ejecutado en orca-cli browser contra servidor real en puerto 40761. Mutaciones externas aplicadas vía API; tarjeta 1 actualizada a `$ 20.000` en el DOM sin recargar la página; pieza 2 removida tras venta confirmada; repuesta tras deshacer; restaurada tras reset. Aislamiento de sesión B verificado (catálogo B intacto). Tolerancia a ráfagas de 5 invalidaciones simultáneas sin bloqueos. Capturas: `v6_sse_price_updated.png`, `v6_sse_sold_removed.png`, `v6_sse_undo_restored.png`.
  - **V7 (Desconexión y recuperación snapshot):** EventSource cerrado deliberadamente para simular corte; estado visual muestra *"Reconectando con el taller…"*; catálogo mutado en backend a `$ 30.000` mientras el cliente estaba offline; cliente reconectado; evento `conectado` dispara GET y recupera el precio `$ 30.000` en el DOM. Simulación de bfcache: stream cerrado en `pagehide` y recreado limpiamente en `pageshow`. Captura: `v7_reconnected_snapshot.png`.
  - **V8 (Regresión completa del simulador existente):** Apertura modal con `#open`; propuesta textual con validación de respuesta conversacional; cancelación con `#no`; ejecución de botón demo `[data-demo="price"]` confirmada con `#yes` y reflejada en la tarjeta de fondo; botón `#undo` revierte el cambio; botón `#reset` restaura catálogo original canónico; clic en `#record` muestra fallback sin fallos de script; cierre con `#close` devuelve el foco a `#open`. Capturas: `v8_simulator_open.png`, `v8_simulator_flow.png`.

---

## 3. Evidencia Limpia del Commit de Exportación (skill `testing-and-qa` & §9)

De acuerdo con el protocolo de pruebas limpias, se generó un archivo tar del árbol de Git (`git archive HEAD`) en una carpeta temporal aislada dentro de `target/qa.gJ1PRR`, y se ejecutó la suite completa de compilación y verificación:

- **Commit auditado:** `ddcd19f86782be73ba9decc7fe8e529ee2bc270c`
- **Ruta de exportación:** `/home/gonzalocar/orca/workspaces/Norma-/issue-6-storefront/target/qa.gJ1PRR`
- **Entorno de ejecución:**
  - JDK: Microsoft OpenJDK 25.0.4.1+1-LTS (`/home/gonzalocar/.jdks/ms-25.0.4.1`)
  - Maven: Apache Maven 3.8.7
  - Sistema Operativo: Linux 6.14.0-37-generic (amd64)
- **Comando de verificación:**
  ```bash
  mvn -B clean
  mkdir -p target/issue6-runtime/tmp target/issue6-runtime/uploads
  mvn -B verify \
    -DargLine="-Djava.io.tmpdir=$(pwd)/target/issue6-runtime/tmp" \
    -Dnorma.uploads="$(pwd)/target/issue6-runtime/uploads" \
    -Dgroq.api.key=
  ```
- **Código de salida (Exit Code):** `0` (BUILD SUCCESS)
- **Duración de compilación:** 02:02 min
- **Sumas de comprobación SHA-256:**
  - Registro de verificación (`verify.log`): `8a85c0995b9bceca743358630e9ccaaa6b3fde4fda2abebc7f4a07b221ae5682`
  - Archivo ejecutable final (`taller-1.0.0.jar`): `737a8462f8f80b3648a6d6d99ea0f7b62595e3bf9cba91671b3e90a17264566d`
- **Prueba de arranque Smoke del artefacto exportado:**
  Ejecución con base de datos SQLite y directorio de subidas aislados bajo puerto 40762; `curl http://localhost:40762/api/ping` respondió exitosamente `PONG`. Detención ordenada con código de salida limpio.

### Desglose de suites Surefire XML (11 suites, 137 tests en verde):
| Archivo de reporte Surefire | Tests | Fallos | Errores | Omitidos |
|---|:---:|:---:|:---:|:---:|
| `TEST-com.norma.taller.BotApiTest.xml` | 6 | 0 | 0 | 0 |
| `TEST-com.norma.taller.BotEngineTest.xml` | 30 | 0 | 0 | 0 |
| `TEST-com.norma.taller.BotPersistenceTest.xml` | 1 | 0 | 0 | 0 |
| `TEST-com.norma.taller.BotSessionCodecTest.xml` | 15 | 0 | 0 | 0 |
| `TEST-com.norma.taller.CatalogMigrationTest.xml` | 3 | 0 | 0 | 0 |
| `TEST-com.norma.taller.CatalogPersistenceTest.xml` | 3 | 0 | 0 | 0 |
| `TEST-com.norma.taller.CatalogRepositoryTest.xml` | 7 | 0 | 0 | 0 |
| `TEST-com.norma.taller.FlowTest.xml` | 4 | 0 | 0 | 0 |
| `TEST-com.norma.taller.IntentParserTest.xml` | 61 | 0 | 0 | 0 |
| `TEST-com.norma.taller.StorefrontContractTest.xml` | 6 | 0 | 0 | 0 |
| `TEST-com.norma.taller.StorefrontSseTest.xml` | 1 | 0 | 0 | 0 |
| **Total** | **137** | **0** | **0** | **0** |

---

## 4. Declaración Explícita de Residuales y Límites

1. **V1-zoom200 (Zoom 200% real de navegador):** Conforme a lo acordado en el Gate B1 entre Orca y Codex (`task_dc4c1cfb552f`), la validación del zoom 200% nativo del navegador se mantiene como ítem expresamente **PENDIENTE** para la auditoría independiente de OpenCode QA en su entorno con GUI/indicador de zoom. Antigravity aporta como evidencia técnica complementaria: capturas de zoom CSS 2.0x (`v1_css_zoom_200.png`), reflow responsivo a 320px/640px sin scroll horizontal ni solapamientos, y DPR=2 (`v1_zoom_200.png`), sin atribuirse un dictamen final de PASS sobre el zoom nativo.
2. **Diferencia documental posterior:** Este informe se incorpora en un commit atómico documental posterior al commit exportado `ddcd19f86782be73ba9decc7fe8e529ee2bc270c`, declarando explícitamente dicha diferencia conforme al estándar de `testing-and-qa`.
3. **Dependencias del entorno:** La lectura de voz por compradores depende de la compatibilidad y configuración de voces del motor de síntesis del sistema operativo del usuario. El cliente gestiona la ausencia o falla de voces de manera degradada y no bloqueante.
