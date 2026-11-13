# Reporte de Construcción Correctiva — Issue #6: Vidriera accesible con SSE (v2)

**Fecha:** 2026-11-13  
**Agente:** Antigravity (Software Builder / Implementer)  
**Coordinador:** Orca (Lead Software Engineer y Orquestador Técnico)  
**Tarea / Dispatch:** `task_bcc51c9f95ca` / `ctx_74037e27e6e5`  
**Terminal Builder:** `term_7f7d8270-a58a-478b-831d-3b43b8c6fcce`  
**Terminal Coordinador:** `term_104a5a3f-cca1-4813-a727-30ddd7a9152f`  
**Worktree:** `/home/gonzalocar/orca/workspaces/Norma-/reconcile-6-storefront`  
**Rama:** `GonzaCarrillo/reconcile-6-storefront`  
**Base de trabajo:** `d13c51b835dd1a32eadf7043957e02b918f5c3e2`  
**Diseño de referencia:** Codex v2.0 (`docs/orchestration/issue-6-corrective-design.md`)  

---

## 1. Alcance Cerrado y Declaración sobre Código de Producción (§10 y Diseño v2)

Conforme a la especificación correctiva aprobada por Orca y Codex:
- **Archivos de producción autorizados bajo condición de defecto demostrado:** `src/main/resources/static/index.html`, `src/main/resources/static/style.css`, `src/main/resources/static/app.js`.
- **Archivos de documentación y evidencia de construcción:** `docs/orchestration/issue-6-corrective-build.md`, `docs/orchestration/issue-6-corrective-evidence/builder/`.
- **Frontera de aislamiento e invariantes estrictas:** Ningún archivo de backend, Api, MediaService, audio/voz (#5), esquemas SQL, POM, propiedades globales ni documentación global fue modificado. Se respetó la coexistencia con la issue paralela #5 ejecutada en su propio worktree y procesos.

### Dictamen de Producción:
**SIN MODIFICACIONES EN CÓDIGO DE PRODUCCIÓN.**  
La inspección técnica exhaustiva de los componentes estáticos demostró que no existe defecto alguno en la implementación:
1. La grilla responsive (`style.css`) mantiene absoluta uniformidad de tarjetas (diferencia de altura: **0.00 px**) mediante `min-height: 5.4em`, `grid-auto-rows: 1fr` y tarjetas con `height: 100%`.
2. Las fotografías mantienen proporción 4:3 estricta (`aspect-ratio: 4 / 3; object-fit: cover`).
3. Ausencia total de scroll horizontal (`overflow-x: clip`, `max-width: 100%`) en todos los viewports probados (1440px, 768px, 640px, 375px y 320px).
4. El modo de letra grande eleva la raíz de 22px a 28.6px (+30%), verificado en runtime.
5. El diálogo modal nativo `<dialog id="zoom">` atrapa el foco en `#close-zoom`, captura `Escape` y retorna el foco al disparador de la foto.
6. El coordinador SSE (`app.js`) implementa deduplicación de refrescos (`inFlight`, `refreshPending`) y número generacional (`invalidationGen`), preservando el foco y reconciliando nodos por `data-piece-number`.
7. El simulador de taller (§7) preserva íntegramente todos sus controles (`#open`, `#sim`, `#close`, `#text`, `#photo`, `#record`, `button[type="submit"]`, `#yes`, `#no`, `#undo`, `#reset`).

Por lo tanto, en cumplimiento del criterio *"Modificar estáticos solo si la comprobación real demuestra un defecto; no insertar zoom CSS para simular aceptación"*, se preservaron intactos los archivos estáticos de producción.

---

## 2. Investigación Técnica y Reporte de Bloqueo Ambiental (V1-zoom200)

El criterio revocatorio de aceptación v2 exige:
> *"Navegador de escritorio: capturar versión, tamaño de ventana e indicador nativo 100% y luego 200% durante la medición. Emulación desactivada; CSS zoom no inyectado; scale/DPR no alterados para fabricar resultado. Registrar método nativo utilizado."*
> *"Sin acceso al indicador/control nativo: QA_REJECTED por bloqueo ambiental, sin sustituir por CSS zoom ni dar por cerrada #6."*

### Diagnóstico Técnico del Entorno:
1. **Entorno de ejecución:** Linux Mint 22.3 (x86_64, Linux 6.14.0-37-generic).
2. **Capacidades de navegador CLI disponibles:**
   - La herramienta provista para interacción con navegador es la CLI de Orca (`orca` v1.4.257), que gestiona pestañas Chromium headless/embebidas vía Chrome DevTools Protocol (CDP, Chromium `150.0.7871.250`).
   - Comandos disponibles: `tab create|close`, `viewport`, `goto`, `eval`, `click`, `fill`, `keypress`, `screenshot`.
3. **Imposibilidad técnica de capturar el indicador nativo de zoom:**
   - `orca screenshot` invoca internamente el método CDP `Page.captureScreenshot`. Este método captura exclusivamente el bitmap del viewport DOM de la página renderizada.
   - En arquitectura CDP y navegadores headless/sidecar, la interfaz de usuario de la ventana del sistema operativo (decoraciones de ventana, barra de direcciones, barra de menú y el menú/insignia nativa de porcentaje de zoom visual "100%" / "200%") **no forma parte del viewport DOM** y no es accesible ni capturable mediante la API de automatización.
   - Además, en el demonio headless de Orca, `orca screenshot` genera timeout (`CDP error (Page.captureScreenshot): Screenshot timed out — the browser page did not draw a frame`) debido a la ausencia de loop de composición visual continuo en pestañas en segundo plano.
4. **Ausencia de comandos CLI de zoom de ventana:**
   - `orca` CLI no provee opciones o banderas para fijar el nivel de zoom nativo del navegador de escritorio (como `Page.setZoomLevel` o atajos de UI de aplicación).
   - Las únicas opciones de escala disponibles en `orca` son `orca viewport --scale <n>` (que emula `deviceScaleFactor` / DPR). La especificación prohíbe expresamente el uso de `--scale 2` o `document.body.style.zoom = 2`.
5. **Inexistencia de herramientas de automatización de GUI de escritorio:**
   - No se encuentran instalados ni configurados arneses de automatización de ventanas de escritorio (como Playwright, Selenium con captura de ventana completa o herramientas OCR sobre ventana X11/Wayland).

### Dictamen de Bloqueo:
Conforme a la sección *Criterios verificables* de `docs/orchestration/issue-6-corrective-design.md`, se reporta formalmente un **BLOQUEO AMBIENTAL PRECISO PARA LA CAPTURA DEL INDICADOR NATIVO DE ZOOM 200%**. Se rechaza cualquier simulación mediante CSS zoom o scale DPR, manteniéndose la issue a la espera de un entorno con capacidades de captura de ventana de escritorio nativa para la auditoría QA.

---

## 3. Evidencia Funcional y de Reflow en Runtime Aislado

A pesar del bloqueo para capturar el indicador visual de ventana del SO, se verificó exhaustivamente el comportamiento responsive y de reflow en un servidor Spring Boot real arrancado desde el artefacto empaquetado:

- **Arnés de ejecución:** `docs/orchestration/issue-6-corrective-evidence/builder/run_builder_evidence.py`
- **Registro estructurado:** `docs/orchestration/issue-6-corrective-evidence/builder/builder_evidence.json`
- **Configuración de aislamiento:**
  - Puerto dinámico HTTP: asignado aleatoriamente por el sistema operativo (`server.port=0`).
  - Base de datos SQLite: `target/builder-runtime/smoke-db/taller.db` (esquema v2 inicializado y aislado).
  - Directorio de subidas: `target/builder-runtime/smoke-uploads/`.
  - Directorio temporal: `target/builder-runtime/smoke-tmp/`.
  - Variable de credenciales: `groq.api.key=` (vacía, sin llamadas externas).

### Resultados de Medición en Viewports:
| Viewport | Ancho (CSS px) | Alto (CSS px) | Tarjetas | Diff Altura | Scroll Horizontal | Aspect Ratio Fotos (1..3) |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| `desktop_1440` | 1440 | 900 | 8 | **0.00 px** | **false** | 1.333 (4:3) |
| `tablet_768` | 768 | 1024 | 8 | **0.00 px** | **false** | 1.333 (4:3) |
| `reflow_640_zoom200` | 640 | 900 | 8 | **0.00 px** | **false** | 1.333 (4:3) |
| `mobile_375` | 375 | 812 | 8 | **0.00 px** | **false** | 1.333 (4:3) |
| `mobile_320_min` | 320 | 640 | 8 | **0.00 px** | **false** | 1.333 (4:3) |

*(Nota: el viewport de 640 CSS px corresponde al reflow equivalente al 200% de aumento sobre un ancho de 1280 CSS px según el estándar WCAG SC 1.4.10).*

### Verificaciones Complementarias:
- **Tipografía (#font):** Tamaño base `:root` = 22.0 px; modo letra grande (`#font` activado) = 28.6 px; ratio de escala = **1.30** (+30% exacto); restauración a 22.0 px verificada.
- **Diálogo modal (#zoom):** Apertura sobre foto de pieza, foco inicial atrapado en `#close-zoom`, cierre con `Escape` y retorno de foco verificado.
- **Simulador (#sim):** Verificada la presencia y accesibilidad de todos los controles semánticos (`#open`, `#sim`, `#close`, `#text`, `#photo`, `#record`, `button[type="submit"]`, `#yes`, `#no`, `#undo`, `#reset`).
- **Smoke HTTP:**
  - `GET /api/ping` → `HTTP 200` `PONG`
  - `GET /api/products?sessionId=builder_smoke_123` → `HTTP 200`, 8 piezas no vendidas devueltas con numeración consecutiva 1..8.

---

## 4. Evidencia de Compilación y Verificación Limpia (JDK 25)

Siguiendo el protocolo oficial de `testing-and-qa` (§9):
- **Commit exportado y auditado:** `b165b2f6cedc75203604a8c7ee3726e3ebc0c97e`
- **Ruta de exportación limpia:** `/home/gonzalocar/orca/workspaces/Norma-/reconcile-6-storefront/target/qa.WqjwSe`
- **Entorno de compilación:**
  - JDK: Microsoft OpenJDK 25.0.4.1+1-LTS (`/home/gonzalocar/.jdks/ms-25.0.4.1`)
  - Maven: Apache Maven 3.8.7
  - Sistema Operativo: Linux 6.14.0-37-generic (amd64)
- **Secuencia de comandos ejecutada en la exportación:**
  ```bash
  export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1
  export PATH="$JAVA_HOME/bin:$PATH"
  mvn -B clean
  mkdir -p target/export-runtime/tmp target/export-runtime/uploads
  mvn -B verify \
    -DargLine="-Djava.io.tmpdir=$(pwd)/target/export-runtime/tmp" \
    -Dnorma.uploads="$(pwd)/target/export-runtime/uploads" \
    -Dgroq.api.key=
  ```
- **Resultado:** `BUILD SUCCESS` (Exit code: `0`, duración: `01:37 min`).
- **Sumas de comprobación SHA-256:**
  - Registro de verificación (`verify.log`): `5ba285ad1719c2bbbdcbac9a18499cb0fb4e653d2e9ab15d0e9334191a6d8a78`
  - Archivo ejecutable empaquetado (`taller-1.0.0.jar`): `7ccbb252d70813bf84a7428d3fae4780866d6b30b19cde040b21822bc3114c18`
- **Suites ejecutadas:** 16 suites Surefire, **192 tests ejecutados, 0 fallos, 0 errores, 0 omitidos**.

### Desglose de Suites Surefire XML (`target/surefire-reports/` en exportación):
| Archivo de Reporte | Tests | Fallos | Errores | Omitidos |
|---|:---:|:---:|:---:|:---:|
| `TEST-com.norma.taller.ApiAudioTest.xml` | 4 | 0 | 0 | 0 |
| `TEST-com.norma.taller.BotApiTest.xml` | 6 | 0 | 0 | 0 |
| `TEST-com.norma.taller.BotEngineTest.xml` | 30 | 0 | 0 | 0 |
| `TEST-com.norma.taller.BotPersistenceTest.xml` | 1 | 0 | 0 | 0 |
| `TEST-com.norma.taller.BotSessionCodecTest.xml` | 15 | 0 | 0 | 0 |
| `TEST-com.norma.taller.CatalogMigrationTest.xml` | 3 | 0 | 0 | 0 |
| `TEST-com.norma.taller.CatalogPersistenceTest.xml` | 3 | 0 | 0 | 0 |
| `TEST-com.norma.taller.CatalogRepositoryTest.xml` | 7 | 0 | 0 | 0 |
| `TEST-com.norma.taller.DemoMediaTest.xml` | 4 | 0 | 0 | 0 |
| `TEST-com.norma.taller.FlowTest.xml` | 4 | 0 | 0 | 0 |
| `TEST-com.norma.taller.IntentParserTest.xml` | 61 | 0 | 0 | 0 |
| `TEST-com.norma.taller.MediaHttpTest.xml` | 23 | 0 | 0 | 0 |
| `TEST-com.norma.taller.MediaServiceTest.xml` | 6 | 0 | 0 | 0 |
| `TEST-com.norma.taller.PhotoProcessorTest.xml` | 19 | 0 | 0 | 0 |
| `TEST-com.norma.taller.StorefrontContractTest.xml` | 6 | 0 | 0 | 0 |
| `TEST-com.norma.taller.StorefrontSseTest.xml` | 1 | 0 | 0 | 0 |
| **Total General** | **192** | **0** | **0** | **0** |

---

## 5. Estado de la Entrega y Próximos Pasos

1. **Commit y Trazabilidad:** La evidencia técnica de exportación limpia se ejecutó sobre el commit `b165b2f6cedc75203604a8c7ee3726e3ebc0c97e`. Esta actualización documental final incorpora los hashes y rutas resultantes de la ejecución de compilación, declarando explícitamente dicha diferencia documental conforme a `testing-and-qa`.
2. **Handoff a Orca:** Se entrega el commit para que el Lead Software Engineer coordine la **Fase 3: Auditoría QA independiente por GitHub Copilot** y posteriormente la **Fase 4: Aprobación arquitectónica por Codex**.
3. **No-operaciones garantizadas:** No se ha realizado merge a `main`, push remoto, cierre unilateral de issues ni activación de agentes no autorizados.
