# Informe de Auditoría QA Independiente (Fase 3) — Issue #7: Conciliación Simulador sin Credenciales

**Dictamen**: QA_APPROVED  
**Commit auditado**: `8ea839b479246ec1302c6e0a1b649d7fd2d7b38f` (`docs(issue-7): registrar sincronización documental con origin/main (cierre issue #4)`)  
**Rama**: `GonzaCarrillo/reconcile-7-simulator`  
**Diseño de referencia**: `docs/orchestration/issue-7-reconciliation-design-v2.md` (Codex v2, aprobado para despacho Fase 2)  
**Entregador Fase 2**: Antigravity (build reporte en `docs/orchestration/issue-7-reconciliation-build.md`)  
**Auditor**: OpenCode / Nemotron (Fase 3, QA & Test Auditor)  
**Worktree**: `/home/gonzalocar/orca/workspaces/Norma-/reconcile-7-simulator`  
**Fecha**: 13 de Noviembre de 2026

---

## 1. Alcance y reglas de escritura (confirmadas)

Esta auditoría es de Fase 3 únicamente: no se modifica código de producción, no se cambian contratos ni esquemas, no se desactiva prueba alguna, y no se infiere CI verde por la existencia de un workflow.

**Scope de escritura autorizado para esta entrega**:
- `docs/orchestration/issue-7-reconciliation-qa.md` (este informe)
- `docs/orchestration/issue-7-reconciliation-evidence/qa/` (evidencia propia de QA)
- `target/` (exportación y artefactos propios de QA, ignorados por git)

No se tocó producción, tests, builder-report, diseño ni evidencias del builder.

---

## 2. Estado del commit y diff scope

- `git status --short`: vacío (árbol de trabajo limpio).
- `git diff 3fe6308..HEAD -- src pom.xml src/main/resources`: **vacío** → cero diferencias en código fuente de producción, pruebas y configuración respecto a la entrega de Fase 2 (`3fe6308`).
- `git diff --stat HEAD`: vacío → no hay cambios no commiteados.
- `src/test/java/com/norma/taller/SimulatorContractTest.java` está presente y trackeada en HEAD (9 pruebas de contrato HTTP).
- Conclusión: el commit `8ea839b` es únicamente una sincronización documental de la Issue #4 sobre la base ya entregada por Antigravity; no introduce cambio funcional. No hay defecto de producción que inventar ni corregir.

---

## 3. Entorno de construcción y ejecución

| Elemento | Valor |
|---|---|
| Sistema operativo | Linux x86_64, Kernel 6.14.0-37-generic, Ubuntu 24.04 LTS |
| JDK / SDK | Microsoft OpenJDK 25.0.4.1 (build 25.0.4.1+1-LTS), bytecode versión 69 |
| `JAVA_HOME` | `/home/gonzalocar/.jdks/ms-25.0.4.1` |
| Maven | Apache Maven 3.8.7 |
| Java runtime check | `java -version` → openjdk 25.0.4.1 LTS |
| `mvn -version` → Maven 3.8.7, Java 25.0.4.1 |

---

## 4. Ejecución de exportación limpia y `mvn -B clean verify`

Procedimiento seguido (skill `testing-and-qa`, bloque "Evidencia limpia del commit"):

```bash
export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1
export PATH="$JAVA_HOME/bin:$PATH"
norma_qa_run="$(mktemp -d target/qa.XXXXXX)"
git rev-parse HEAD > "$norma_qa_run/commit.txt"          # 8ea839b...
git archive HEAD -o "$norma_qa_run/source.tar"
mkdir "$norma_qa_run/export" && tar -xf "$norma_qa_run/source.tar" -C "$norma_qa_run/export"
(
  cd "$norma_qa_run/export"
  mvn -B clean verify > verify.log 2>&1
  printf 'EXIT_CODE=%s\n' "$?" >> verify.log
)
```

**Exportación**: `target/qa.jkP7eX/export` (fuentes de `git archive HEAD`, sin archivos sin seguimiento del workspace).

**Comando ejecutado**: `mvn -B clean verify` dentro de la exportación.

**Exit code real**: `0`.

**Resultado**: `BUILD SUCCESS`.

**Tiempo total**: 02:05 min. Finalizado `2026-11-13T21:41:42-03:00`.

**No se usó** `skipTests`, exclusiones ni edición de suites previas. Ejecución única de Maven sobre el checkout de exportación.

**Evidencia**: `docs/orchestration/issue-7-reconciliation-evidence/qa/mvn_verify_qa.log`.

---

## 5. Artefacto: JAR empaquetado

| Elemento | Valor |
|---|---|
| Archivo | `target/taller-1.0.0.jar` (Spring Boot Executable JAR, Spring Boot 3.5.16) |
| Ruta exportada | `target/qa.jkP7eX/export/target/taller-1.0.0.jar` |
| Checksum SHA-256 | `fdb97e5b34dfd687668a9042c049b4fe1d554605efdc960435a250fd8a48e878` |

---

## 6. Conteo de pruebas (Surefire, XML reales)

Suites ejecutadas y conteos extraídos de `target/surefire-reports/TEST-*.xml`:

| Suite | Tests | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| ApiAudioTest | 7 | 0 | 0 | 0 |
| ApiPublishTest | 7 | 0 | 0 | 0 |
| BotApiTest | 6 | 0 | 0 | 0 |
| BotEngineTest | 30 | 0 | 0 | 0 |
| BotPersistenceTest | 1 | 0 | 0 | 0 |
| BotSessionCodecTest | 15 | 0 | 0 | 0 |
| CatalogMigrationTest | 3 | 0 | 0 | 0 |
| CatalogPersistenceTest | 3 | 0 | 0 | 0 |
| CatalogRepositoryTest | 7 | 0 | 0 | 0 |
| DemoMediaTest | 3 | 0 | 0 | 0 |
| FlowTest | 4 | 0 | 0 | 0 |
| IntentFallbackApiTest | 6 | 0 | 0 | 0 |
| IntentFallbackTest | 21 | 0 | 0 | 0 |
| IntentParserTest | 61 | 0 | 0 | 0 |
| MediaHttpTest | 23 | 0 | 0 | 0 |
| MediaServiceTest | 20 | 0 | 0 | 0 |
| PhotoProcessorTest | 19 | 0 | 0 | 0 |
| **SimulatorContractTest** | **9** | **0** | **0** | **0** |
| StorefrontContractTest | 6 | 0 | 0 | 0 |
| StorefrontSseTest | 1 | 0 | 0 | 0 |
| **TOTAL** | **252** | **0** | **0** | **0** |

**Evidencia XML de SimulatorContractTest**: `docs/orchestration/issue-7-reconciliation-evidence/qa/TEST-com.norma.taller.SimulatorContractTest.xml`.

---

## 7. Verificación real contra el JAR empaquetado (puerto dinámico, SQLite/uploads aislados)

Además de la suite, se levantó el JAR real empaquetado contra una base SQLite temporal y un directorio de uploads temporal, en un puerto dinámico efímero, sin credenciales externas:

```bash
java --enable-native-access=ALL-UNNAMED -jar target/taller-1.0.0.jar \
  --server.port=$PORT \
  --spring.datasource.url="jdbc:sqlite:$DB" \
  --norma.uploads="$UP"
```

**Resultados observados (reales, no simulados)**:
- `GET /api/ping` → `PONG`, HTTP 200.
- `GET /api/products?sessionId=sess_smoke_a` → JSON con las **8 piezas iniciales** del catálogo (pieceNumber 1..3…, `sold=false`, `imageUrl=/assets/pieza-*.webp`).
- Tomcat arrancó en el puerto dinámico, Hikari pool inició, SQLite en modo WAL, esquema v2 inicializado, base de datos creada en el archivo temporal aislado.
- Proceso detenido limpiamente tras la verificación; BD y uploads temporales eliminados.

**Evidencia**: `docs/orchestration/issue-7-reconciliation-evidence/qa/smoke_server.log`.

Esto confirma que el artefacto entregado es ejecutable, autónomo (sin WhatsApp/Meta/IA externa) y sirve el catálogo inicial de 8 piezas sobre SQLite real.

---

## 8. Matriz de criterios del contrato Codex v2 vs pruebas

| Cláusula | Criterio | Prueba que lo cubre | Resultado QA |
|---|---|---|---|
| C1 | Despliegue aislado, cero credenciales en HTML/JS | `testSimulatorStaticAssetsAndElements` (ausencia `AI_KEY`, `WHATSAPP_TOKEN`) | PASS |
| C1 | JAR empaquetado, JDK 25, SQLite temporal, puerto dinámico | Smoke real contra `taller-1.0.0.jar` (PONG / 8 piezas) | PASS |
| C2 | `#sim`, `#open`, `#close`, 4 demos, `#yes`/`#no`/`#undo`/`#reset` presentes | `testSimulatorStaticAssetsAndElements` | PASS |
| C3.1 | Create: propuesta sin mutación, cancelar, confirmar, undo | `testDemoCreateLifecycle` (8→propuesta→8→confirmar 9→undo 8) | PASS |
| C3.2 | Price: propuesta sin mutación, confirmar actualiza, undo restaura | `testDemoPriceLifecycle` (18500→propuesta→18500→confirmar 20000→undo 18500) | PASS |
| C3.3 | Image: foto multipart propuesta, confirmar actualiza URL, undo revierte | `testDemoImageLifecycle` (foto original→propuesta→`/media/nueva-foto-2.webp`→undo original) | PASS |
| C3.4 | Sold: propuesta sin mutar, confirmar oculta (7 activas), undo reactiva (8) | `testDemoSoldLifecycle` | PASS |
| C4 | Formulario multipart `sessionId`/`text`/`image` | Ejercido por `testDemoImageLifecycle` y `testInvalidPhotoDoesNotMutateCatalog` con `MockMultipartFile` | PASS |
| C5 | Honestidad medios locales vs STT real | Ninguna prueba afirma STT real; `mediaService.speak(any())→null` degrada audio y conserva texto | PASS (sin afirmación de STT real) |
| C6 | Aislamiento sesiones A/B; reset en A no afecta a B | `testResetIsolationBetweenSessions` (A 7 tras venta, B 8; reset A→8, B sigue 8) | PASS |
| C7.1 | Foto corrupta → HTTP 400, sin mutación | `testInvalidPhotoDoesNotMutateCatalog` (400, catálogo en 8) | PASS |
| C7.2 | `sessionId=DEFAULT` o <8 chars → HTTP 400 | `testIllegalSessionId` | PASS |
| C7.3 | Ausencia de voz mantiene texto íntegro | `testVoiceAbsenceKeepsText` (`replyAudioUrl` ausente, `replyText` no vacío) | PASS |

**Resultado de regresión**: suites previas (BotEngine, Flow, CatalogPersistence, StorefrontSse, etc.) todas verdes; 252/252 sin fallos. No se detectó regresión.

---

## 9. Evidencia de navegador (GUI) — limitación registrada

La cláusula C2 y parte de C3/C7 requieren interacción gráfica real (teclado/foco, demos visuales, apertura/cierre de teclado, foto real por formulario). El auditor **no pudo ejecutar interacción GUI real en navegador** porque la instalación local de `orca` CLI disponible no expone comandos de control de navegador (`tab`/`click`/`fill`/`screenshot` no existen en `orca --help` de este entorno), y no se dispone de herramienta de automatización de browser equivalente en el toolset de este worker.

**Qué sí se verificó de forma real y propia**:
- Ejecución real del JAR empaquetado sobre SQLite/uploads aislados en puerto dinámico, con respuestas HTTP reales (`/api/ping → PONG`, `/api/products → 8 piezas`).

**Evidencia complementaria del builder (no sustituye verificación propia de QA, se distingue como tal)**:
- 21 capturas de pantalla en `docs/orchestration/issue-7-reconciliation-evidence/builder/` (foco por teclado, cuatro demos, cancelar/confirmar/undo/reset, sesión B aislada, foto real multipart, foto inválida, micrófono denegado).
- Reporte de construcción del builder en `docs/orchestration/issue-7-reconciliation-build.md`.

**Limitación formal**: QA no puede afirmar haber comprobado por sí misma los criterios de interacción GUI (teclado/foco, demos visuales, apertura/cierre de teclado). Se toma como evidencia complementaria del builder y se registra como límite ambiental. Si se requiere acreditación formal de GUI, debe ejecutarse con un entorno que disponga de control de navegador (orca con browser o equivalente) sobre el mismo JAR verificado aquí.

---

## 10. Defectos

No se detectó defecto de producción. El commit es únicamente sincronización documental; código, pruebas y configuración son idénticos a la entrega de Fase 2 ya verificada. No hay defectos que reportar con severidad/reproducción/esperado/observado.

---

## 11. Dictamen

**QA_APPROVED**

Fundamento: `mvn -B clean verify` sobre exportación limpia de `git archive HEAD` → `BUILD SUCCESS`, exit code 0; 252 pruebas, 0 fallos, 0 errores, 0 omitidas; `SimulatorContractTest` 9/9 cubriendo las cláusulas del contrato; JAR empaquetado verificado realmente (puerto dinámico, SQLite/uploads aislados, `/api/ping → PONG`, 8 piezas iniciales); diff de producción/tests/config vacío respecto a la entrega de Fase 2; árbol de trabajo limpio; ningún defecto de producción.

Este dictamen habilita la Fase 4 de Codex (aprobación arquitectónica final). No implica merge, push a main ni cierre de issue por parte de OpenCode.

---

## 12. Rutas de evidencia entregadas

- `docs/orchestration/issue-7-reconciliation-qa.md` — este informe
- `docs/orchestration/issue-7-reconciliation-evidence/qa/mvn_verify_qa.log` — log completo de `mvn -B clean verify` con exit code
- `docs/orchestration/issue-7-reconciliation-evidence/qa/smoke_server.log` — log de arranque real del JAR (PONG, 8 piezas, SQLite WAL)
- `docs/orchestration/issue-7-reconciliation-evidence/qa/TEST-com.norma.taller.SimulatorContractTest.xml` — reporte XML Surefire de la prueba de contrato
- `docs/orchestration/issue-7-reconciliation-evidence/builder/` — evidencia complementaria del builder (21 capturas + reporte de construcción)
- Artefacto de construcción propio (ignorado por git): `target/qa.jkP7eX/` (JAR hash `fdb97e5b…48e878`)
