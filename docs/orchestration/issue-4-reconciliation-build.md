# Reporte de Construcción y Verificación — Issue #4: Conciliación Fase 2 (Pipeline de Medios, Procedencia y Compatibilidad Integral)

**Fecha**: 13 de Noviembre de 2026  
**Rama**: `GonzaCarrillo/reconcile-4-media-final`  
**Base unificada**: `origin/main` en commit `a320a65` (con conciliación de Issue #5 Voz/Fallback e Issue #6 SSE integradas)  
**Diseño de referencia**: `docs/orchestration/issue-4-reconciliation-design.md` v1.0 e `docs/orchestration/issue-4-design.md` v1.0 (aprobado por Orca)  
**Autor**: Antigravity (Software Builder / Implementer & Reconciliation)  
**Estado**: Verificación completa del pipeline de medios y suite de regresión del repositorio ejecutadas con éxito bajo Microsoft OpenJDK 25. Suite de medios (45/45) y suite global del monolito (243/243) 100% verdes. Cero modificaciones de código de producción, tests o assets requeridas. Listo para auditoría de Fase 3 por OpenCode / Nemotron.

---

## 1. Entorno de Construcción y Ejecución

- **Sistema Operativo**: Linux x86_64 (Kernel 6.14.0-37-generic)
- **Java Runtime / SDK**: Microsoft OpenJDK 25.0.4.1 (build 25.0.4.1+1-LTS)
  - `JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1`
  - `PATH=$JAVA_HOME/bin:$PATH`
  - Bytecode: Versión 69 (Java 25 estándar)
- **Herramienta de construcción**: Apache Maven 3.8.7
- **Artefacto principal generado**:
  - Archivo: `target/taller-1.0.0.jar`
  - Formato: Spring Boot Executable JAR (Spring Boot 3.5.16)
  - Checksum SHA-256: `e05509a6d0f41da82e134304815149bb6d7b5c59f863980de153bddb77a97d8e`

---

## 2. Alcance Cerrado y Respeto de Fronteras

En estricto cumplimiento con la instrucción de despacho:
- **Archivos modificados / creados en este despacho**:
  - `docs/orchestration/issue-4-reconciliation-design.md` (especificación formal de conciliación v1.0)
  - `docs/orchestration/issue-4-reconciliation-build.md` (este reporte)
  - `issue-4-reconciliation-evidence/builder/` (evidencias Surefire XML/TXT, hashes criptográficos de JAR y assets, y resumen de verificación)
- **Archivos estrictamente NO modificados**:
  - Código de producción (`PhotoProcessor.java`, `MediaService.java`, `Api.java`, etc.) intacto.
  - Suites de pruebas (`PhotoProcessorTest.java`, `MediaHttpTest.java`, `DemoMediaTest.java`, etc.) intactas.
  - Recursos estáticos (`src/main/resources/static/assets/*`) intactos.
  - Documentos de medios y decisiones previas (`docs/medios.md`, `docs/operacion.md`, `docs/decisiones.md`) intactos.
  - No se generaron imágenes por IA ni se alteraron dependencias en `pom.xml`.

---

## 3. Matriz de Cumplimiento vs Diseño Técnico

| ID | Criterio de Diseño | Estado | Evidencia y Acreditación Verificada |
|:---|:---|:---:|:---|
| **V1** | Allowlist formatos JPEG (baseline/progresivo), PNG8 (RGB/gris/alfa) y WebP estático; detección por contenedor independiente de extensión/MIME | **CONFORME** | `PhotoProcessorTest.testV1_*` valida aceptación de JPEG y WebP válidos enviados como `foto.jpg` o `data.bin`. |
| **V2** | Rechazo 400 ante vacío directo, archivos corruptos, HEIC, animación PNG/WebP, PNG16, CMYK y metadatos excesivos/contradictorios | **CONFORME** | `PhotoProcessorTest.testV2_*` y `MediaHttpTest` verifican HTTP 400 con mensajes específicos sin filtrar detalles internos. |
| **L1** | Límites cámara: 15 MiB, eje ≤16384 px, producto ≤24 MP (long) evaluados antes de `read(0)`; caso 24 MP decodificado sin OOM | **CONFORME** | `PhotoProcessorTest.testL1_*` valida rechazo de 24MP+1 sin decodificar y procesa caso 24MP real (heap delta registrado: 99 MB). |
| **L2** | Multipart límite: partes >15 MiB cortadas por el contenedor Servlet emitiendo HTTP 413 real con `RANDOM_PORT` | **CONFORME** | `MediaHttpTest.testL2_MultipartPartExceeds15MB_ReturnsReal413_PayloadTooLarge` sobre Tomcat en puerto dinámico verifica HTTP 413 y ausencia de temporales en `.processing`. |
| **O1** | Orientaciones EXIF IFD0 1–8 en JPEG/PNG/WebP, ausencia=1, bloque EXIF ≤262.144 B | **CONFORME** | `PhotoProcessorTest.testO1_*` comprueba rotación y reflexiones con fixtures asimétricos exactos en orientaciones 1 a 8. |
| **G1** | Recorte centrado 4:3 con $k=\min(\lfloor W_o/4 \rfloor, \lfloor H_o/3 \rfloor)$, redimensionado uniforme a 800×600 px sobre fondo blanco | **CONFORME** | `PhotoProcessorTest.testG1_*` verifica dimensiones de salida exactas 800×600 y rechazo 400 si $k=0$. |
| **C1** | Revelado determinista y corrección tonal sutil única: $R'=\text{clamp}(\text{round}(R \times 1.03 \times 1.01))$, $G'=\text{clamp}(\text{round}(G \times 1.03))$, $B'=\text{clamp}(\text{round}(B \times 1.03 \times 0.99))$ | **CONFORME** | `PhotoProcessorTest.testC1_*` verifica la aplicación exacta de la fórmula matemática en canales sRGB8 antes de la compresión. |
| **W1** | Proveedor WebP lossy instalado, salida WebP real, dimensiones 800×600, ausencia de metadatos identificatorios | **CONFORME** | `PhotoProcessorTest.testW1_*` decodifica la salida publicada y verifica formato WebP y ausencia de EXIF/GPS. |
| **W2** | Hasta 8 intentos con calidades [80, 70, 60, 50, 40, 30, 20, 10]/100f; aceptación si $0 < \text{bytes} < 80.000$; rechazo con 80.000 B; si todos superan, 400 sin downscale | **CONFORME** | `PhotoProcessorTest.testW2_*` y costura `WebPEncoder` controlada validan comportamiento de frontera (79999 admite, 80000 rechaza) y 8 reintentos exactos. |
| **F1** | Resiliencia de I/O: borrado en `finally` únicamente de temporales de la llamada, staging en `.processing`, atomic move no soportado → 500 sin archivo expuesto | **CONFORME** | `PhotoProcessorTest.testF1_*` y `MediaHttpTest.testF1_*` acreditan limpieza y no polución de uploads ante errores. |
| **F2** | UUID único por archivo, nombres clientes y `../` neutralizados; concurrencia: slot único `Semaphore(1)`, segundo intento recibe 500 sin cola de memoria | **CONFORME** | `PhotoProcessorTest.testF2_*` y `MediaHttpTest.testF2_*` prueban dos procesamientos concurrentes (uno pasa, el segundo 500 inmediato; la siguiente petición funciona). |
| **H1** | Serving `/media/**`: 200 + `Content-Type: image/webp` o `audio/mpeg`; bloqueo estricto de `.processing` y directory traversal (`..`); 404 `"No encontré ese archivo."` | **CONFORME** | `MediaHttpTest.testH1_ServingHttp_Webp_Mp3_NotFound_And_BlockedProcessing` valida 200, tipos MIME y 404 específicos. |
| **A1** | Integración FSM BotEngine: subida de foto real → `AWAITING_DETAILS`, confirmación → publicación atómica en catálogo, `undo` restaura foto previa | **CONFORME** | `MediaHttpTest.testA1_FullLifecycle_RealImage_BotEngine_Confirmation_Undo` verifica el ciclo completo sin mocks en el motor. |
| **A2** | Aislamiento ante errores: imagen inválida o error en medios no altera sesión, catálogo ni dispara eventos SSE o TTS | **CONFORME** | `MediaHttpTest.testA2_*` comprueba que la sesión previa y los productos permanecen idénticos tras un rechazo 400 o 413. |
| **D1** | 8 WebP humanos locales y 4 audios MP3 en `static/assets/`: dimensiones 800×600, < 80 KB, sin IA, hashes exactos vs `docs/medios.md` | **CONFORME** | `DemoMediaTest` comprueba los 12 archivos estáticos contra sus hashes SHA-256 e inventario de procedencia. |
| **R1** | Regresión completa del monolito limpia en JDK 25 | **CONFORME** | `mvn -B clean verify` ejecuta 243 pruebas en 19 suites sin ningún fallo (0 failures, 0 errors, 0 skipped). |

---

## 4. Resultados de Verificación de Suites de Pruebas

### 4.1 Suite Focalizada de Medios (`PhotoProcessorTest`, `MediaHttpTest`, `DemoMediaTest`)
- **Comando**:
  ```bash
  export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1 && export PATH=$JAVA_HOME/bin:$PATH
  mvn -B -Dtest=PhotoProcessorTest,MediaHttpTest,DemoMediaTest test
  ```
- **Resultado**: `BUILD SUCCESS` (45 tests ejecutados en 01:20 min)
  - `MediaHttpTest`: 23 tests, 0 failures, 0 errors, 0 skipped (41.87 s)
  - `DemoMediaTest`: 3 tests, 0 failures, 0 errors, 0 skipped (1.768 s)
  - `PhotoProcessorTest`: 19 tests, 0 failures, 0 errors, 0 skipped (11.55 s)

### 4.2 Suite Completa de Regresión y Export Limpia (`mvn -B clean verify`)
- **Comando**:
  ```bash
  export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1 && export PATH=$JAVA_HOME/bin:$PATH
  mvn -B clean verify
  ```
- **Resultado**: `BUILD SUCCESS` (243 tests ejecutados en 02:00 min)
  - `CatalogMigrationTest`: 9 tests, 0 failures, 0 errors, 0 skipped
  - `CatalogPersistenceTest`: 7 tests, 0 failures, 0 errors, 0 skipped
  - `CatalogRepositoryTest`: 23 tests, 0 failures, 0 errors, 0 skipped
  - `BotPersistenceTest`: 8 tests, 0 failures, 0 errors, 0 skipped
  - `BotSessionCodecTest`: 12 tests, 0 failures, 0 errors, 0 skipped
  - `IntentParserTest`: 29 tests, 0 failures, 0 errors, 0 skipped
  - `BotEngineTest`: 44 tests, 0 failures, 0 errors, 0 skipped
  - `FlowTest`: 3 tests, 0 failures, 0 errors, 0 skipped
  - `StorefrontSseTest`: 8 tests, 0 failures, 0 errors, 0 skipped
  - `StorefrontContractTest`: 8 tests, 0 failures, 0 errors, 0 skipped
  - `BotApiTest`: 6 tests, 0 failures, 0 errors, 0 skipped
  - `ApiAudioTest`: 7 tests, 0 failures, 0 errors, 0 skipped
  - `ApiPublishTest`: 7 tests, 0 failures, 0 errors, 0 skipped
  - `MediaServiceTest`: 20 tests, 0 failures, 0 errors, 0 skipped
  - `IntentFallbackTest`: 21 tests, 0 failures, 0 errors, 0 skipped
  - `MediaHttpTest`: 23 tests, 0 failures, 0 errors, 0 skipped
  - `DemoMediaTest`: 3 tests, 0 failures, 0 errors, 0 skipped
  - `PhotoProcessorTest`: 19 tests, 0 failures, 0 errors, 0 skipped
  - `IntentFallbackApiTest`: 6 tests, 0 failures, 0 errors, 0 skipped

---

## 5. Inventario de Evidencias Persistidas

Las siguientes evidencias se encuentran versionadas bajo `issue-4-reconciliation-evidence/builder/`:
1. `TEST-com.norma.taller.PhotoProcessorTest.xml`
2. `com.norma.taller.PhotoProcessorTest.txt`
3. `TEST-com.norma.taller.MediaHttpTest.xml`
4. `com.norma.taller.MediaHttpTest.txt`
5. `TEST-com.norma.taller.DemoMediaTest.xml`
6. `com.norma.taller.DemoMediaTest.txt`
7. `jar-sha256.txt`:
   ```
   e05509a6d0f41da82e134304815149bb6d7b5c59f863980de153bddb77a97d8e  target/taller-1.0.0.jar
   ```
8. `assets-sha256.txt`:
   ```
   25bfcb4eeb43a6dfe1f671bc368f8b64cf85852f32ba0d2efc90b913465b7c15  src/main/resources/static/assets/demo-create.mp3
   aa435c531920ef9b9eb5dc229509910b85ad6d12b22d96ca56c5697162fcec46  src/main/resources/static/assets/demo-image.mp3
   e83836d7077b6c69ec40a2884b66e0fa1ee203ff1df78cace88aa30dc8ba1a6a  src/main/resources/static/assets/demo-price.mp3
   0da71c5037e881f52cc87419e2bf82cc4d3b6684fdcb05429de5c5af7638dbcd  src/main/resources/static/assets/demo-sold.mp3
   365d0360f061b84a4e4fa6e7dd4ca85231666a8d67c796bdb0aa7b4f27fb37a7  src/main/resources/static/assets/pieza-1.webp
   c15195fb60a6c4707710b4833d249c33cc99ed588fa8767e998ed4c23730972f  src/main/resources/static/assets/pieza-2.webp
   ab8b44ac2dc020b58ee8405635ce7bdd921287fa1446da058f2d1d41795042a1  src/main/resources/static/assets/pieza-3.webp
   fc0abb484a8622db9e4265f30b282ca0a3a27978305734f5782defc0257ddf6d  src/main/resources/static/assets/pieza-4.webp
   e07db765feefe119ed51d5418faa70727c4498675a84007fe6a2b2b608b98f2f  src/main/resources/static/assets/pieza-5.webp
   99d98672c9e6ab2749a92033bcf2f7271aa3841426bba841dfe928c626a10e91  src/main/resources/static/assets/pieza-6.webp
   6bb6ec6d24d6f5af9f4a886698e2a82c1157e528607c0c45e31a6595efd3bd4e  src/main/resources/static/assets/pieza-7.webp
   0dd65b5c297d46af98b6194b356350876a2bff83d0231c4bae4785237ba522bc  src/main/resources/static/assets/pieza-8.webp
   ```
9. `verify-summary.txt`: Registro consolidado del entorno de ejecución, comandos y sumas SHA-256.

---

## 6. Dictamen de Builder

La entrega vigente de la Issue #4 está completamente conciliada y verificada contra `origin/main` (`a320a65`). Todos los contratos funcionales, de seguridad, de almacenamiento y de compatibilidad con las issues hermanas (#5 y #6) y consumidor frontend (#7) se encuentran plenamente preservados y acreditados con pruebas automatizadas verdes en JDK 25.

Entrega lista para Fase 3 (Auditoría QA independiente de OpenCode / Nemotron) y Fase 4 (Aprobación Arquitectónica Final de Codex).
