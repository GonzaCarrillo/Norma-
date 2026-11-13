# Reporte de Construcción y Verificación — Issue #4: Pipeline Fotográfico y Medios

**Fecha**: 13 de Noviembre de 2026  
**Rama**: `GonzaCarrillo/issue-4-media`  
**Diseño de referencia**: `docs/orchestration/issue-4-design.md` v1.0 (Aprobado por Orca `e2b6f524ec8a09b2f12b0b12237b3920addcc01e`)  
**Autor de construcción**: Antigravity (Software Builder)  
**Estado**: Gates B1, B2 y B3 implementados y verificados al 100%.

---

## 1. Entorno de Construcción y Ejecución

- **Sistema Operativo**: Linux x86_64 (Kernel 6.6+)
- **Java Runtime / SDK**: Microsoft OpenJDK 25.0.4.1 (build 25.0.4.1+1-LTS)
  - Ruta requerida: `JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1` (el Java por defecto en `$PATH` del host es OpenJDK 21; para compilar con `release 25` debe fijarse `JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1 PATH=$JAVA_HOME/bin:$PATH`).
  - Bytecode: Versión 69 (Java 25 estándar, sin preview flags)
  - Parámetros de ejecución JNI: `--enable-native-access=ALL-UNNAMED` para bibliotecas nativas de SQLite (`sqlite-jdbc`) y WebP (`webp-imageio`).
- **Herramienta de construcción**: Apache Maven 3.8.7
- **Artefacto principal generado**:
  - Archivo: `target/taller-1.0.0.jar`
  - Formato: Spring Boot Executable JAR (Spring Boot 3.5.16)
  - Checksum SHA-256: `e16ccd350570cb3973fffb02cc2b52d81d961dc13f674ff46e7a5772b5fc9aaf`

---

## 2. Trazabilidad de Commits Atribuibles a la Issue #4

| Commit | Fase / Gate | Descripción |
| :--- | :--- | :--- |
| `06e482e` | Gate B1 | Implementación de `PhotoProcessor` base con validación de contenedores (JPEG, PNG8, WebP estático), límites de 15 MiB / 24 MP / 16384 px, extracción EXIF IFD0 (orientaciones 1..8) con `metadata-extractor:2.19.0`, recorte 4:3, redimensionado 800x600 y corrección tonal sRGB8. Pruebas V1/V2/L1/O1/G1/C1 en `PhotoProcessorTest`. Aprobado por Orca (`msg_019a3f47c35e`). |
| `6bcd742` | Gate B2 | Implementación de compresión WebP en hasta 8 intentos (`[80..10]`), umbral estricto < 80.000 bytes (con límite par 79.998 bytes), parseo RIFF estricto sin metadata/animación, ranura única de concurrencia (`Semaphore(1)`), staging privado en `.processing/`, publicación `ATOMIC_MOVE` y endpoint seguro `/media/**` con fail-safe 404. Pruebas W1/W2/F1/F2/H1 en `MediaHttpTest`. Aprobado por Orca (`msg_019a3f4e9f34`). |
| `c6be99b` | Gate B3 | Incorporación de tests L2 (413 real sobre `RANDOM_PORT`), resiliencia ante FS sin `ATOMIC_MOVE`, symlinks, ciclo de vida completo FSM A1/A2 con `BotEngine`, inventario D1 y suite `DemoMediaTest`, inventario y procedencia en `docs/medios.md`, operaciones en `docs/operacion.md`, ADR en `docs/decisiones.md` y registro de aprobación. |
| *(Actual)* | Sub-tarea D-PROCEDENCIA | Corrección de la brecha D-PROCEDENCIA reportada por OpenCode QA: matriz individual completa en `docs/medios.md` para los 8 recursos WebP y 4 MP3 (motivo real, pareja inferida, fuente exacta, fotógrafo verificado o no identificado sin inventar, fecha de comprobación, licencia y transformaciones aplicadas). |

---

## 3. Matriz de Cumplimiento Técnico (§9 del Diseño)

| Requisito / Clave | Criterio de Aceptación | Prueba Asociada | Resultado |
| :--- | :--- | :--- | :--- |
| **V1** | Allowlist de formatos: JPEG, PNG8 y WebP estático permitidos. Rechazo 400 de formatos no soportados (TIFF, GIF, BMP, HEIC). | `PhotoProcessorTest.testV1_*` | **PASS** |
| **V2** | Rechazo de WebP animados (chunks `ANIM`/`ANMF` o flag `animation` en `VP8X`), perfiles ICC, y EXIF corrupto o > 256 KiB. | `PhotoProcessorTest.testV2_*` | **PASS** |
| **L1** | Límites de cámara: rechazo 400 si archivo > 15 MiB, eje > 16.384 px o producto > 24 MP. Caso 24 MP procesado con memoria acotada. | `PhotoProcessorTest.testL1_*` | **PASS** |
| **L2** | Servidor real en `RANDOM_PORT`: multipart > 15 MiB genera 413 Payload Too Large en el borde de Spring Boot, limpia temporales y no altera estado. | `MediaHttpTest.testL2_*` | **PASS** |
| **O1** | Orientación EXIF tags 1..8: rotaciones y flips aplicados correctamente en raster 800x600 con comprobación cromática por cuadrantes. | `PhotoProcessorTest.testO1_*` | **PASS** |
| **G1** | Recorte centrado determinista 4:3 con factor $k = \min(\lfloor W/4 \rfloor, \lfloor H/3 \rfloor)$; rechazo 400 si $k=0$. | `PhotoProcessorTest.testG1_*` | **PASS** |
| **C1** | Corrección sRGB8 sobre blanco sólido `#FFFFFF` ($R'=1.0403 \times R, G'=1.03 \times G, B'=1.0197 \times B$) con comprobación matemática exacta. | `PhotoProcessorTest.testC1_*` | **PASS** |
| **W1** | Bucle de 8 calidades `[80, 70, 60, 50, 40, 30, 20, 10]` sobre el mismo raster; corte en el primer candidato < 80.000 bytes. Error 400 si ninguno cumple. | `MediaHttpTest.testW1_*` | **PASS** |
| **W2** | Parseo estricto RIFF/WEBP: verificación de longitud declarada, alineación a 2 bytes, descarte de metadatos privados y confirmación de raster 800x600. | `MediaHttpTest.testW2_*` | **PASS** |
| **F1** | Staging privado en `.processing/`, publicación mediante `ATOMIC_MOVE`, limpieza en `finally` y fallo seguro con 500 ante volúmenes sin atomic move. | `MediaHttpTest.testF1_*` | **PASS** |
| **F2** | Bloqueo estricto de concurrencia a 1 ranura (`Semaphore(1)`); segunda petición simultánea responde 500 rápido sin cola de memoria. | `MediaHttpTest.testF2_*` | **PASS** |
| **H1** | Serving seguro `/media/**`: 200 con `image/webp` y caché 86400; bloqueo de path traversal (`..`), archivos ocultos, `.processing/` y symlinks con 404 genérico. | `MediaHttpTest.testH1_*` | **PASS** |
| **D1** | Inventario de 8 imágenes WebP (800x600, lossy VP8, < 80.000 B) y 4 audios MP3 en classpath y disco; verificación de hashes SHA-256 y serving HTTP. | `DemoMediaTest.testD1_*` | **PASS** |
| **A1** | Integración FSM con `BotEngine`: subida de foto real -> `AWAITING_DETAILS` -> precio/nombre -> `AWAITING_CONFIRMATION` -> `Sí` -> `IDLE` con pieza #9 WebP; cambio de foto en pieza 2 y `undo` revirtiendo URL a semilla con preservación de URL previa. | `MediaHttpTest.testA1_*` | **PASS** |
| **A2** | Aislamiento y validación en `/api/bot/message`: envío simultáneo de texto y audio rechazado con 400 sin invocar fotos; fotos corruptas rechazadas con 400 sin tocar catálogo ni sesiones. | `MediaHttpTest.testA2_*` | **PASS** |

---

## 4. Resultados de la Suite Completa de Pruebas (Surefire)

Ejecución de `mvn -B clean verify` (Exit Code: 0):

| Suite de Pruebas | Clase | Pruebas | Fallos | Errores | Omitidas | Tiempo (s) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| Bot API | `com.norma.taller.BotApiTest` | 6 | 0 | 0 | 0 | 0.12 s |
| Bot Engine | `com.norma.taller.BotEngineTest` | 30 | 0 | 0 | 0 | 2.11 s |
| Bot Persistence | `com.norma.taller.BotPersistenceTest` | 1 | 0 | 0 | 0 | 0.35 s |
| Bot Session Codec | `com.norma.taller.BotSessionCodecTest` | 15 | 0 | 0 | 0 | 0.31 s |
| Catalog Migration | `com.norma.taller.CatalogMigrationTest` | 3 | 0 | 0 | 0 | 1.85 s |
| Catalog Persistence | `com.norma.taller.CatalogPersistenceTest` | 3 | 0 | 0 | 0 | 0.82 s |
| Catalog Repository | `com.norma.taller.CatalogRepositoryTest` | 7 | 0 | 0 | 0 | 0.45 s |
| **Demo Media (Nuevo)** | `com.norma.taller.DemoMediaTest` | **3** | **0** | **0** | **0** | **1.75 s** |
| Conversational Flow | `com.norma.taller.FlowTest` | 4 | 0 | 0 | 0 | 0.28 s |
| Intent Parser | `com.norma.taller.IntentParserTest` | 61 | 0 | 0 | 0 | 0.18 s |
| **Media HTTP (Nuevo)** | `com.norma.taller.MediaHttpTest` | **23** | **0** | **0** | **0** | **23.20 s** |
| **Photo Processor (Nuevo)** | `com.norma.taller.PhotoProcessorTest` | **19** | **0** | **0** | **0** | **16.52 s** |
| **TOTAL GENERAL** | **12 Clases** | **175** | **0** | **0** | **0** | **78.40 s** |

- **Pruebas específicas de medios (Issue #4)**: 45 pruebas (19 en `PhotoProcessorTest`, 23 en `MediaHttpTest`, 3 en `DemoMediaTest`).
- **Pruebas de regresión históricas**: 130 pruebas anteriores completamente verdes.

---

## 5. Trazabilidad de Archivos Modificados y Creados

```text
Modificados:
  pom.xml                                           (Dependencia com.drewnoakes:metadata-extractor:2.19.0)
  src/main/java/com/norma/taller/Api.java           (MediaConfig / WebMvcConfigurer para serving seguro /media/** y error 404)
  src/main/java/com/norma/taller/MediaService.java  (Delegación a PhotoProcessor sin alterar interfaz de audio/voz)
  docs/medios.md                                    (Inventario criptográfico, procedencia humana y notas de UI)
  docs/operacion.md                                 (Sección operativa de medios, memoria, staging, concurrencia y serving)
  docs/decisiones.md                                (ADRs D12 a D17 correspondientes a Issue #4)
  docs/orchestration/issue-4-approval.md            (Registro de aprobaciones de diseño y gates B1, B2 y B3)

Nuevos:
  src/main/java/com/norma/taller/PhotoProcessor.java (Motor de procesamiento, validación, compresión WebP y staging)
  src/test/java/com/norma/taller/PhotoProcessorTest.java (Suite de pruebas unitarias V1, V2, L1, O1, G1, C1)
  src/test/java/com/norma/taller/MediaHttpTest.java (Suite de pruebas HTTP/MockMvc L2, W1, W2, F1, F2, H1, A1, A2)
  src/test/java/com/norma/taller/DemoMediaTest.java (Suite de verificación D1 para los 12 assets estáticos)
  docs/orchestration/issue-4-build-report.md        (Este informe técnico consolidado)
```

---

## 6. Límites y Supuestos Operativos

1. **Requisito de volumen para ATOMIC_MOVE**: El directorio configurado en `norma.uploads` debe encontrarse en un sistema de archivos que soporte `ATOMIC_MOVE` (POSIX `rename` atómico sobre el mismo mount point). Si el almacenamiento no soporta movimiento atómico, el procesador aborta con HTTP 500 y limpia staging para evitar archivos incompletos.
2. **Concurrencia fotográfica deliberadamente serializada**: Se definió 1 slot simultáneo para procesamiento fotográfico. Esto previene picos de memoria concurrentes en hardware modesto. Si la tasa de subida de fotos crece, peticiones concurrentes reciben 500 inmediato; una arquitectura escalada requeriría workers asíncronos desacoplados fuera del monolito.
3. **Modo demo en interfaz web**: La botonera de demostración en `app.js` reproduce el archivo MP3 mientras envía la transcripción fija por texto; esto está documentado explícitamente en `docs/medios.md` para evitar falsas asunciones de transcripción de voz en tiempo real sin Groq.
4. **Ausencia de Webhook de WhatsApp**: La interacción se realiza mediante el simulador web (`/api/bot/message`), ya que la integración con WhatsApp requiere cuentas empresariales y certificados externos no provistos en este entorno.
