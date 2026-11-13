# Informe de QA Independiente — Issue #4 (c6be99b)

**Agente:** OpenCode (QA & Tester Auditor) — modelo designado NVIDIA Nemotron 3.5 Super VL 120B A12B  
**Worktree:** `/home/gonzalocar/orca/workspaces/Norma-/issue-4-media`  
**Rama:** `GonzaCarrillo/issue-4-media` (actualizada con `origin/GonzaCarrillo/issue-4-media`)  
**Commit auditado:** `c6be99bae1da8626ed7f229cb2bdac03ee1459f0`  
  `feat(media): completar gate B3 de issue #4 con procedencia, integración FSM, 413 real, tests y docs`  
**Diseño de referencia:** `docs/orchestration/issue-4-design.md` v1.0 (aprobado por Orca en `e2b6f524ec8a09b2f12b0b12237b3920addcc01e`)  
**Registro de aprobación:** `docs/orchestration/issue-4-approval.md` (Gates B1, B2, B3 aprobados)  
**Informe de construcción (builder):** `docs/orchestration/issue-4-build-report.md`  

---

## 1. Entorno de ejecución y verificación

| Ítem | Valor observado |
| :--- | :--- |
| Sistema operativo | Linux 6.14.0-37-generic x86_64 |
| Java por defecto en `$PATH` | OpenJDK 21.0.12.1 (**no** soporta `release 25`) |
| JDK usado para el build | `/home/gonzalocar/.jdks/ms-25.0.4.1` → `openjdk version "25.0.4.1" 2026-08-18 LTS` (Microsoft-14951822) |
| Corrobora build-report | **Sí**: build-report declara "Microsoft OpenJDK 25.0.4.1"; JDK 25 localizado y usado |
| Herramienta de construcción | Apache Maven 3.8.7 |
| Spring Boot | 3.5.16 (verificado en arranque de pruebas) |
| `pom.xml` | `java.version=25`, `maven.compiler.release=25`; metadata-extractor 2.19.0 añadido |

### Comando ejecutado (QA independiente)
```
JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1 \
PATH=/home/gonzalocar/.jdks/ms-25.0.4.1/bin:$PATH \
mvn -B clean verify
```

### Resultado del build
- **Exit code:** `0`
- **Resultado:** `BUILD SUCCESS`
- **Tests run:** `175`, **Failures:** `0`, **Errors:** `0`, **Skipped:** `0`
- Tiempo total: 03:44 min

> Nota sobre el JDK por defecto: invocar `mvn` sin `JAVA_HOME` apunta a OpenJDK 21 y falla con `error: release version 25 not supported`. El build reportado por el builder solo es reproducible usando explícitamente el JDK 25 localizado. Esto se documenta como hallazgo de entorno (ver §4, hallazgo H-ENTORNO).

---

## 2. Corrobora de artefactos y logs del builder

| Afirmación del build-report | Valor observado en QA | Estado |
| :--- | :--- | :--- |
| JAR `target/taller-1.0.0.jar` | Reconstruido con éxito desde `c6be99b` | PASS |
| SHA-256 del JAR del builder (artefacto pre-existente en worktree, verificado **antes** de mi `clean`) | `e16ccd350570cb3973fffb02cc2b52d81d961dc13f674ff46e7a5772b5fc9aaf` — coincide con el build-report | PASS |
| SHA-256 del JAR reconstruido de forma independiente por QA | `3711e18d5123cd59c061d3b098d2489ff0fab15890996ba62fc98c413cd40b9d` | Diferente (ver H-JAR-SHA) |
| Commit asociado | `c6be99b` (HEAD, coincide con el task y con `origin`) | PASS |
| `mvn -B clean verify` exit code 0 | Verificado: `EXIT_CODE=0` | PASS |
| 175 pruebas totales (45 medios + 130 regresión) | Verificado por Surefire: 175 run / 0 fallos | PASS |

> H-JAR-SHA: el JAR reconstruido por QA tiene un SHA distinto al del builder. Causa esperada: metadatos de entrada del ZIP empaquetado por el Spring Boot repackage (timestamps de entradas, orden de manifiesto/capas), no diferencia de código ni de pruebas. El artefacto del builder (`e16ccd35...`) se observó presente en el worktree antes de la reconstrucción independiente. No es posible obtener un JAR byte-identical sin el entorno exacto de construcción del builder; esto no invalida el resultado de pruebas.

### Per suite (Surefire, `target/surefire-reports/*.txt`)

| Suite | Pruebas | Fallos | Errores | Omitidas |
| :--- | :---: | :---: | :---: | :---: |
| BotApiTest | 6 | 0 | 0 | 0 |
| BotEngineTest | 30 | 0 | 0 | 0 |
| BotPersistenceTest | 1 | 0 | 0 | 0 |
| BotSessionCodecTest | 15 | 0 | 0 | 0 |
| CatalogMigrationTest | 3 | 0 | 0 | 0 |
| CatalogPersistenceTest | 3 | 0 | 0 | 0 |
| CatalogRepositoryTest | 7 | 0 | 0 | 0 |
| DemoMediaTest (D1) | 3 | 0 | 0 | 0 |
| FlowTest | 4 | 0 | 0 | 0 |
| IntentParserTest | 61 | 0 | 0 | 0 |
| MediaHttpTest (L2,W1,W2,F1,F2,H1,A1,A2) | 23 | 0 | 0 | 0 |
| PhotoProcessorTest (V1,V2,L1,O1,G1,C1) | 19 | 0 | 0 | 0 |
| **TOTAL** | **175** | **0** | **0** | **0** |

Pruebas específicas de medios (issue #4): **45** (19 + 23 + 3). Regresión #1–#3: **130**. Coincide exactamente con el build-report.

---

## 3. Matriz de aceptación §9 (verificación independiente)

Cada criterio está respaldado por las pruebas de la suite que pasan al 100%.

| ID | Comportamiento | Pruebas que lo cubren | Resultado QA |
| :--- | :--- | :--- | :--- |
| **V1** | Allowlist JPEG/PNG8/WebP estáticos; detección por firma, indiferente a extensión/MIME | `PhotoProcessorTest.testV1_*` | **PASS** |
| **V2** | Rechazo 400: vacío, basura, formatos excluidos/HEIC, cabecera truncada, datos dañados, EXIF corrupto/excesivo/contradictorio, animación, PNG16/CMYK; Session/Product preservados | `PhotoProcessorTest.testV2_*` | **PASS** |
| **L1** | 1–15 MiB admitido, >15 MiB rechazado (getSize y streaming), eje ≤16384, producto ≤24MP (long), caso 24MP real con memoria registrada | `PhotoProcessorTest.testL1_*` | **PASS** |
| **L2** | Servidor real `RANDOM_PORT`; parte >15 MiB / petición >20 MiB → 413 en el borde; sin bot/media/SSE; limpia temporales | `MediaHttpTest.testL2_*` | **PASS** |
| **O1** | EXIF orientaciones 1–8 en JPEG/PNG/WebP, ausencia=1, IFD0 rige sobre miniatura, endian II/MM, verificación de píxeles por cuadrantes | `PhotoProcessorTest.testO1_*` | **PASS** |
| **G1** | Recorte centrado 4:3 `k=min(floor(Wo/4),floor(Ho/3))`, k=0 → 400; 800×600 exacto; círculo sin deformar; impares | `PhotoProcessorTest.testG1_*` | **PASS** |
| **C1** | Revelado una vez; alfa sobre #FFFFFF; fórmula exacta R'=clamp(round(R*1.03*1.01)), G'=clamp(round(G*1.03)), B'=clamp(round(B*1.03*0.99)); determinismo | `PhotoProcessorTest.testC1_*` | **PASS** |
| **W1** | WebP lossy real, 8 calidades [80..10], corte en primer candidato <80000; 79999 acepta, 80000 rechaza | `MediaHttpTest.testW1_*` | **PASS** |
| **W2** | Lista exacta de calidades; 8 intentos agotados → 400 sin final/temporal; sin recodificar previa | `MediaHttpTest.testW2_*` | **PASS** |
| **F1** | Staging `.processing/`, publicación `ATOMIC_MOVE`, limpieza en `finally`, fallo 500 sin atomic move, semáforo liberado, candidatos corruptos/metadata/dimensiones rechazados y limpiados | `MediaHttpTest.testF1_*` | **PASS** |
| **F2** | UUID únicos, nombres de cliente no expuestos, traversal `../` bloqueado, un solo slot activo (segunda carga → 500 rápido), siguiente carga válida funciona | `MediaHttpTest.testF2_*` | **PASS** |
| **H1** | Serving `/media/**`: 200 + `image/webp` / `audio/mpeg`; 404 sin bytes privados para faltante, `.processing`, traversal simple y codificado, symlinks | `MediaHttpTest.testH1_*` | **PASS** |
| **A1** | FSM foto real: AWAITING_DETAILS → AWAITING_CONFIRMATION → IDLE con pieza #9 WebP; cambio de foto pieza 2 y `undo` restaura URL previa; referencial estable | `MediaHttpTest.testA1_*` | **PASS** |
| **A2** | Texto+audio simultáneos → 400 antes de medios; foto corrupta → 400 sin tocar catálogo/sesiones; no SSE/TTS/bot en fallo | `MediaHttpTest.testA2_*` | **PASS** |
| **D1** | 8 WebP locales y 4 MP3: 200, MIME correcto, dimensiones 800×600, tamaño <80000, hash SHA-256, chunks RIFF estrictos, sin EXIF/XMP/ICCP/ANIM/ANMF, serving HTTP | `DemoMediaTest.testD1_*` | **PASS** |
| **R1** | Regresión #1–#3: suites versionadas previas intactas, `clean verify` completo verde | Todas las suites no-media | **PASS** |

**Veredicto de ejecución de la matriz:** **PASS** (todos los criterios verificados por pruebas verdes).

---

## 4. Hallazgos y límites (no son fallos de prueba; requieren atención de Orca/Antigravity)

### H-ENTORNO — JDK por defecto es 21, el build requiere JDK 25 explícito
- **Severidad:** Media (entorno / reproducibilidad)
- **Reproducción:** `mvn -B clean verify` sin `JAVA_HOME` → `error: release version 25 not supported`; con `JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1` → BUILD SUCCESS, exit 0.
- **Esperado:** el build-report declara Java 25 (corroborado: JDK 25 localizado en `/home/gonzalocar/.jdks/ms-25.0.4.1`), pero no documenta que el `java` por defecto en `$PATH` es JDK 21 ni la ruta del JDK 25.
- **Observado:** cualquier ejecutor que use `mvn` sin `JAVA_HOME` explícito fallará.
- **Evidencia:** `java -version` (21.0.12.1); `ls /home/gonzalocar/.jdks/ms-25.0.4.1`; salida de build con JDK 25 (exit 0, 175 pruebas).
- **Acción:** documentar `JAVA_HOME` obligatorio en el reporte de construcción; no es un defecto de código.

### H-JAR-SHA — SHA del JAR reconstruido difiere del declarado por el builder
- **Severidad:** Baja (evidencia / trazabilidad de artefacto)
- **Esperado:** reproducir byte-identical `target/taller-1.0.0.jar` con SHA `e16ccd350570cb3973fffb02cc2b52d81d961dc13f674ff46e7a5772b5fc9aaf`.
- **Observado:** artefacto del builder (pre-existente, verificado antes del `clean` de QA) = `e16ccd35...` (coincide con build-report); reconstrucción independiente de QA = `3711e18d...`.
- **Causa:** metadatos de entrada del ZIP repackageado (timestamps/orden), no diferencia de código ni pruebas.
- **Evidencia:** `sha256sum target/taller-1.0.0.jar` antes y después del `mvn clean verify` de QA.
- **Acción:** aceptar el SHA del builder (`e16ccd35...`) como evidenciado en el worktree; no exigir byte-identity entre entornos de construcción distintos.

### D-PROCEDENCIA — docs/medios.md no completa aún la matriz de procedencia por recurso (diseño §7)
- **Severidad:** Media-Alta (aceptación B3 / documentación)
- **Requisito del diseño §7:** una fila por recurso con motivo real, fuente exacta consultada, fotógrafo solo si verificado (o "no identificado"), fecha de comprobación, enlace a condiciones de uso de la fuente, transformaciones conocidas y hash final.
- **Estado observado en `docs/medios.md`:** declara 4 fuentes Unsplash (IDs de imagen) y 8 encuadres, y por recurso tiene dimensión/formato/tamaño/hash SHA-256. Faltan: fuente exacta **por recurso**, fotógrafo verificado, fecha de comprobación, enlace a condiciones de uso, transformaciones conocidas.
- **Límite de diseño §1/§7:** la procedencia humana y la ausencia de IA son declaraciones existentes; no se puede atestiguar autoría/licencia/ausencia de IA solo a partir del hash. No se debe inventar autor, licencia, fecha de descarga original ni afirmar que son obras de Norma.
- **Observado:** verificación visual local (8 archivos WEBP RGB 800×600 sin EXIF, bytes <80000) acredita contenido/encuadre, no autoría ni licencia.
- **Acción:** este ítem **no lo modifica QA** (instrucción: no tocar documentación/evidencia del builder ni corregir fallos; reportar a Orca para que Antigravity lo corrija). Marcar como **pendiente de aceptación B3** hasta que `docs/medios.md` complete la matriz por recurso o se eleve un bloqueo fundado a Orca.

### V2-ICCP — Cobertura parcial de rechazo de perfil ICC en la entrada
- **Severidad:** Baja (cobertura de prueba)
- **Esperado (diseño V2):** rechazo 400 de perfiles ICC en la entrada.
- **Observado:** `verifyWebPCandidate` rechaza `ICCP` en el **candidato de salida**; los parsers de entrada (`parseJpeg`/`parsePng`/`parseWebP`) no rechazan explícitamente un chunk ICCP/ICC en el archivo subido, y no hay prueba que lo exija a fallar.
- **Evidencia:** lectura estática de `src/main/java/com/norma/taller/PhotoProcessor.java` (métodos `parse*`) y ausencia de prueba de entrada con ICCP.
- **Acción:** observación de cobertura; no bloquea la matriz actual porque ninguna prueba lo exige en rojo. Se deja a criterio de Antigravity/Orca si se añade prueba de entrada con ICCP.

---

## 5. Alcance respetado (prohibiciones de QA)

No se modificó: `MediaService.java`, `Api.java`, `PhotoProcessor.java`, `pom.xml`, configuración, SQL, assets ni pruebas de producción. No se contactó a otros agentes, no se abrieron issues, no se hizo merge ni push a `main`, no se cerró la issue. Los únicos archivos creados por QA son `docs/orchestration/issue-4-qa.md` y `issue-4-evidence/` (este informe y su evidencia), autorizados por Orca para esta auditoría.

---

## 6. Veredicto y próximos pasos

- **Ejecución de pruebas y matriz §9:** **PASS** — 175/175 pruebas verdes, exit code 0, build reproducible con JDK 25.
- **Aceptación completa de #4:** **PENDIENTE** por el ítem **D-PROCEDENCIA** (`docs/medios.md` sin matriz de procedencia por recurso exigida en diseño §7). Este ítem debe ser corregido por Antigravity y re-verificado por QA; no se declara `QA_APPROVED` ni `PASS` definitivo hasta que esté satisfecho.

El presente informe y su evidencia se entregan a Orca para la revisión de conformidad por Codex y la aceptación final. No se cierra la issue ni se abre ninguna nueva.
