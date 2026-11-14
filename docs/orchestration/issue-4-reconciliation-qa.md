# Auditoría QA Fase 3 — Issue #4: Conciliación de Medios (Pipeline, Procedencia y Export Limpia JDK25)

**Agente auditor**: OpenCode / Nemotron (Fase 3 QA & Test Auditor)  
**Versión**: 1.0  
**Fecha**: 13 de Noviembre de 2026  
**Veredicto**: QA_APPROVED  
**Rama auditada**: `GonzaCarrillo/reconcile-4-media-final`  
**SHA auditado**: `a253ae8267560d6b71c1d1a847c6643fae487181`  
**Base aprobada (origin/main)**: `a320a65`  
**Diseño de referencia**: `docs/orchestration/issue-4-reconciliation-design.md` v1.0 y `docs/orchestration/issue-4-design.md` v1.0 (aprobado por Orca)  
**Evidencia**: `issue-4-reconciliation-evidence/qa/` (este despacho de QA)

---

## 1. Alcance y límites respetados

Este despacho de QA **no modificó** código de producción, pruebas, assets estáticos ni `docs/medios.md`. Escritura estrictamente limitada a:
- `docs/orchestration/issue-4-reconciliation-qa.md` (este informe)
- `issue-4-reconciliation-evidence/qa/` (evidencia propia de QA: log, commit, hash JAR, hash assets, reportes Surefire XML)
- `target/` propio (exportación limpia y artefactos de construcción)

No se llamó a Groq, ni a edge-tts, ni a ninguna fuente externa remota durante la auditoría. No se asumió autoría desconocida de los recursos de demostración; la atribución en `docs/medios.md` se respeta tal cual se declara («No identificado fehacientemente» donde no hay verificación).

---

## 2. Corroboración del diff vs base aprobada

Se comparó `a320a65` (base aprobada) frente a `a253ae8` (HEAD auditado):

```
docs/orchestration/issue-4-reconciliation-build.md
docs/orchestration/issue-4-reconciliation-design.md
issue-4-reconciliation-evidence/builder/TEST-com.norma.taller.DemoMediaTest.xml
issue-4-reconciliation-evidence/builder/TEST-com.norma.taller.MediaHttpTest.xml
issue-4-reconciliation-evidence/builder/TEST-com.norma.taller.PhotoProcessorTest.xml
issue-4-reconciliation-evidence/builder/assets-sha256.txt
issue-4-reconciliation-evidence/builder/com.norma.taller.DemoMediaTest.txt
issue-4-reconciliation-evidence/builder/com.norma.taller.MediaHttpTest.txt
issue-4-reconciliation-evidence/builder/com.norma.taller.PhotoProcessorTest.txt
issue-4-reconciliation-evidence/builder/jar-sha256.txt
issue-4-reconciliation-evidence/builder/verify-summary.txt
```

**Hallazgo**: el diff es **100 % documentación y evidencia** (659 inserciones, 0 modificaciones de código). Cero archivos de producción, pruebas, assets o `docs/medios.md` tocados. `git status --short` vacío en `a253ae8` (árbol limpio).

---

## 3. Entorno de ejecución (QA independiente)

| Campo | Valor observado |
|---|---|
| Sistema operativo | Linux x86_64, Kernel 6.14.0-37-generic |
| JDK / JVM | Microsoft OpenJDK 25.0.4.1 (25.0.4.1+1-LTS), bytecode 69 (Java 25) |
| `JAVA_HOME` | `/home/gonzalocar/.jdks/ms-25.0.4.1` |
| Maven | Apache Maven 3.8.7 |
| Locale / encoding | es_AR / UTF-8 |
| Memoria / cabeza | `java.awt.headless=true` |

Comando ejecutado (exportación limpia, `git archive HEAD`, sin archivos sin seguimiento):

```bash
export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1
export PATH=$JAVA_HOME/bin:$PATH
mvn -B clean verify
```

Ejecutado sobre una exportación aislada en `target/qa.W6ObBg/export` (derivada de `git archive HEAD`), no sobre el `target` del builder, para evitar reutilizar artefactos ajenos.

---

## 4. Resultado de la construcción limpia

- **Comando**: `mvn -B clean verify` (sin `skipTests`, sin exclusiones, sin editar suites)
- **Código de salida real**: `EXIT_CODE=0`
- **Resultado**: `BUILD SUCCESS`
- **Tiempo total**: 02:58 min
- **Log completo**: `issue-4-reconciliation-evidence/qa/verify.log`
- **Commit exportado**: `issue-4-reconciliation-evidence/qa/commit.txt` (`a253ae8...`)
- **JAR generado (export QA)**: `target/qa.W6ObBg/export/target/taller-1.0.0.jar`
- **SHA-256 del JAR QA**: `8457fd8761b9de3365b4b2d714fd4dafe65d93a2fc43590fdaf3cfb8360aa76b`
  (`issue-4-reconciliation-evidence/qa/jar-sha256.txt`)

Nota: el hash del JAR QA difiere del registrado por el builder (`e05509a6...`, sobre `target/taller-1.0.0.jar` del worktree de construcción) porque el repackage de Spring Boot sella timestamps; ambos builds son verdes e independientes. El veredicto de QA se sustenta en el build ejecutado por este auditor.

---

## 5. Conteo de pruebas (Surefire, export QA)

Total monolito: **243 tests, 0 failures, 0 errors, 0 skipped, 0 flakes**.

| Suite | Tests | Fallos | Errores | Omitidos | Notas |
|---|---:|---:|---:|---:|---|
| `PhotoProcessorTest` | 19 | 0 | 0 | 0 | V1/V2/L1/O1/G1/C1/W1/W2/F1/F2 |
| `MediaHttpTest` | 23 | 0 | 0 | 0 | L2 (413 real), H1 (servicio/MIME/404/bloqueo), F1/F2, A1/A2, W1/W2 |
| `DemoMediaTest` | 3 | 0 | 0 | 0 | D1 (8 WebP + 4 MP3, inventario y hashes) |
| `BotApiTest` (#5 voz) | 6 | 0 | 0 | 0 | Regresión voz / IntentFallback API |
| `IntentFallbackTest` (#5 voz) | 21 | 0 | 0 | 0 | Regresión voz / normalización |
| `IntentFallbackApiTest` (#5 voz) | 6 | 0 | 0 | 0 | Regresión voz / endpoint fallback |
| `MediaServiceTest` (#5 voz) | 20 | 0 | 0 | 0 | Regresión servicio de medios/voz |
| `ApiAudioTest` (#5 voz) | 7 | 0 | 0 | 0 | Regresión audio |
| `ApiPublishTest` (#5 voz) | 7 | 0 | 0 | 0 | Regresión publicación |
| `StorefrontSseTest` (#6 SSE) | 1 | 0 | 0 | 0 | Regresión SSE concurrencia |
| `StorefrontContractTest` (#6 SSE) | 6 | 0 | 0 | 0 | Regresión contrato vidriera |
| `FlowTest` (#6 SSE) | 4 | 0 | 0 | 0 | Regresión flujo SSE |
| Suites restantes (#1-#3) | 126 | 0 | 0 | 0 | Catálogo, persistencia, motor, sesión, parser |

Las suites de regresión #5 (voz/STT/TTS/IntentFallback) y #6 (SSE/Storefront) permanecen verdes sin mutación, confirmando compatibilidad cruzada preservada.

---

## 6. Matriz de criterios vs diseño aprobado

| ID | Criterio (diseño) | Estado QA | Evidencia ejecutada |
|---|---|---|---|
| V1 | Allowlist JPEG/PNG8/WebP estático; detección por contenedor independiente de extensión/MIME | CONFORME | `PhotoProcessorTest.testV1_*` (JPEG/WebP válidos como `foto.jpg`) |
| V2 | Rechazo 400: vacío, basura, HEIC, animación PNG/WebP, PNG16, CMYK, EXIF excesiva/contradictoria | CONFORME | `PhotoProcessorTest.testV2_*` + `MediaHttpTest` (HTTP 400, sin filtrar internos) |
| L1 | 15 MiB exacto admitido / +1 rechazado; eje ≤16384; producto ≤24 MP (long) antes de `read(0)`; caso 24 MP real sin OOM | CONFORME | `PhotoProcessorTest.testL1_*` (heap delta registrado) |
| L2 | Parte multipart >15 MiB → HTTP 413 real en Tomcat `RANDOM_PORT`; sin temporales en `.processing` | CONFORME | `MediaHttpTest.testL2_MultipartPartExceeds15MB_ReturnsReal413_PayloadTooLarge` |
| O1 | Orientaciones EXIF IFD0 1–8 en JPEG/PNG/WebP; ausencia=1; bloque EXIF ≤262.144 B | CONFORME | `PhotoProcessorTest.testO1_*` (fixtures asimétricos, espejos 5/7, II/MM) |
| G1 | Recorte centrado 4:3 `k=min(floor(Wo/4),floor(Ho/3))`; salida 800×600 sobre blanco; k=0 → 400 | CONFORME | `PhotoProcessorTest.testG1_*` |
| C1 | Revelado determinista 1×: `R'=clamp(round(R×1.03×1.01))`, `G'=clamp(round(G×1.03))`, `B'=clamp(round(B×1.03×0.99))` | CONFORME | `PhotoProcessorTest.testC1_*` |
| W1 | Proveedor WebP lossy instalado; salida WebP real 800×600; sin metadatos identificatorios | CONFORME | `PhotoProcessorTest.testW1_*` (decodifica salida publicada) |
| W2 | Hasta 8 calidades [80..10]/100f; éxito `0<bytes<80000`; 80000 rechaza; 8 fallos → 400 sin downscale | CONFORME | `PhotoProcessorTest.testW2_*` + costura `WebPEncoder` controlada |
| F1 | `finally` limpia solo temporales de la llamada; atomic-move no soportado → 500 sin archivo expuesto; symlink/traversal → 404 | CONFORME | `PhotoProcessorTest.testF1_*` + `MediaHttpTest.testF1_*` |
| F2 | UUID único; nombres cliente y `../` neutralizados; slot único `Semaphore(1)`, 2ª petición 500 inmediata, siguiente válida | CONFORME | `PhotoProcessorTest.testF2_*` + `MediaHttpTest.testF2_*` |
| H1 | `GET /media/**`: 200 + `image/webp`/`audio/mpeg`; bloqueo `.processing` y traversal; 404 `"No encontré ese archivo."` | CONFORME | `MediaHttpTest.testH1_ServingHttp_WebP_Mp3_Assets_AndBlockedPaths` |
| A1 | Ciclo real: foto → `AWAITING_DETAILS` → confirmación → publicación atómica → `undo` restaura previa | CONFORME | `MediaHttpTest.testA1_FullLifecycle_RealImage_BotEngine_Confirmation_Undo` |
| A2 | Imagen inválida / error de medios no altera sesión, catálogo, ni dispara SSE ni TTS | CONFORME | `MediaHttpTest.testA2_*` |
| D1 | 8 WebP humanos + 4 MP3 en `static/assets/`: 800×600, <80 KB, sin IA, hashes exactos vs `docs/medios.md` | CONFORME | `DemoMediaTest.testD1_*` + verificación criptográfica QA |
| R1 | Regresión completa limpia JDK25: 243 pruebas verdes | CONFORME | `mvn -B clean verify` EXIT_CODE=0 |

---

## 7. Verificación de procedencia de recursos de demostración (§7)

Los 12 recursos bajo `src/main/resources/static/assets/` se verificaron criptómicamente en este despacho de QA (`sha256sum` directo sobre los archivos del worktree) y coinciden byte a byte con `docs/medios.md` y con la evidencia del builder:

| Tipo | Recursos | Hashes verificados QA | Coinciden docs/medios.md |
|---|---|---|---|
| WebP | pieza-1 … pieza-8 (8) | 12 hashes SHA-256 individuales | Sí |
| MP3 | demo-create, demo-image, demo-price, demo-sold (4) | 4 hashes SHA-256 individuales | Sí |

Archivo de evidencia QA: `issue-4-reconciliation-evidence/qa/assets-sha256-qa.txt`.

**Atribución honesta (verificada en `docs/medios.md`)**:
- Fotografías: declaradas como obtenidas de Unsplash, capturadas por fotógrafos humanos, no obras de Norma, no generadas por IA. Donde el fotógrafo no pudo verificarse fehacientemente (slug de entrega CDN sin perfil certificado), se declara explícitamente «No identificado fehacientemente (slug de entrega CDN sin perfil certificado en cabeceras; no se inventa autoría)»; solo Jo Szczepanska (`@joszczepanska`) se declara verificada en el catálogo público de Unsplash para pieza-2 y pieza-6.
- Las parejas visuales (1/5, 2/6, 3/7, 4/8) se declaran como inferencias visuales, no como comprobaciones criptográficas contra originales remotos.
- Audios: declarados como síntesis de prueba, no voz ni grabación real de Norma; proveedor/voz/fecha «no verificados»; se aclara que `MediaService.speak` usando `es-AR-TomasNeural` no acredita que los binarios estáticos se generaran con ese motor.
- `app.js` reproduce el MP3 local mientras envía al backend la transcripción predefinida en texto (no envía binario al STT de Groq).

**No se llamó a Groq ni a TTS**; no se repitieron fuentes externas ya verificadas; no se asumió autoría desconocida. Se respetó el límite de no descargar fotografías/audios de terceros en tiempo de ejecución.

---

## 8. Limitaciones observadas

- El JAR no es reproducible a nivel de bytes entre builds (sellado de timestamps del repackage de Spring Boot); el hash QA (`8457fd87...`) difiere del del builder (`e05509a6...`) sin implicar diferencia de contenido funcional. El veredicto se basa en el build ejecutado por QA.
- La advertencia de Surefire «kill self fork JVM» (30 s tras `System.exit(0)`) es benigna; el proceso terminó con `EXIT_CODE=0` y `BUILD SUCCESS`.
- La cota de 24 MP no equivale a un techo total de memoria RSS; el caso 24 MP se ejecutó con heap delta registrado y sin OOM.
- La disponibilidad de movimiento atómico (`ATOMIC_MOVE`) y el proveedor WebP nativo dependen del volumin/plataforma; se acreditaron en este entorno Linux.
- La procedencia original de las fotografías fuente no se revalidó contra remotos (por diseño de la issue: sin descarga externa); se documenta el bloqueo/limitación en `docs/medios.md` sin atribución ficticia.
- Pista #7 (frontend) trabaja en checkout distinto con recursos DB/uploads/tmp/puertos aislados; no se auditó aquí.

---

## 9. Rutas de evidencia entregadas (QA)

- `issue-4-reconciliation-evidence/qa/commit.txt` — SHA auditado `a253ae8...`
- `issue-4-reconciliation-evidence/qa/verify.log` — log completo de `mvn -B clean verify` con `EXIT_CODE=0`
- `issue-4-reconciliation-evidence/qa/jar-sha256.txt` — SHA-256 del JAR QA `8457fd87...`
- `issue-4-reconciliation-evidence/qa/assets-sha256-qa.txt` — SHA-256 de los 12 assets
- `issue-4-reconciliation-evidence/qa/TEST-com.norma.taller.PhotoProcessorTest.xml` (19/0/0/0)
- `issue-4-reconciliation-evidence/qa/TEST-com.norma.taller.MediaHttpTest.xml` (23/0/0/0)
- `issue-4-reconciliation-evidence/qa/TEST-com.norma.taller.DemoMediaTest.xml` (3/0/0/0)
- `issue-4-reconciliation-evidence/qa/TEST-com.norma.taller.BotApiTest.xml` (6/0/0/0) — #5 voz
- `issue-4-reconciliation-evidence/qa/TEST-com.norma.taller.IntentFallbackTest.xml` (21/0/0/0) — #5 voz
- `issue-4-reconciliation-evidence/qa/TEST-com.norma.taller.IntentFallbackApiTest.xml` (6/0/0/0) — #5 voz
- `issue-4-reconciliation-evidence/qa/TEST-com.norma.taller.MediaServiceTest.xml` (20/0/0/0) — #5 voz
- `issue-4-reconciliation-evidence/qa/TEST-com.norma.taller.ApiAudioTest.xml` (7/0/0/0) — #5 voz
- `issue-4-reconciliation-evidence/qa/TEST-com.norma.taller.ApiPublishTest.xml` (7/0/0/0) — #5 voz
- `issue-4-reconciliation-evidence/qa/TEST-com.norma.taller.StorefrontSseTest.xml` (1/0/0/0) — #6 SSE
- `issue-4-reconciliation-evidence/qa/TEST-com.norma.taller.StorefrontContractTest.xml` (6/0/0/0) — #6 SSE
- `issue-4-reconciliation-evidence/qa/TEST-com.norma.taller.FlowTest.xml` (4/0/0/0) — #6 SSE

---

## 10. Dictamen

**QA_APPROVED**.

Todos los criterios de aceptación del diseño de conciliación de la Issue #4 están satisfechos: diff solo documentación, contratos de formatos/límites/EXIF/crop/WebP/staging/serving verificados por pruebas reales (multipart 413, 404, MIME, errores sin mutación), matriz de procedencia 8 WebP + 4 MP3 con atribución honesta en `docs/medios.md`, export limpia JDK25 verde (243 pruebas, 0 fallos), y regresiones #5 (voz) y #6 (SSE) preservadas.

Este dictamen habilita la Fase 4 de Codex (aprobación arquitectónica final). No implica merge, push a `main`, cierre de issue ni aprobación arquitectónica por parte de QA.
