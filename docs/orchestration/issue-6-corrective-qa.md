# Auditoría QA correctiva — Issue #6 (v2.2)

**Dictamen:** QA_APPROVED  
**Commit auditado (HEAD producción v2.2):** `5008d07487f487f8428f5fad46a3a2e31eb7f3df`  
**Fix de producción (Api.java):** `ce9899eac3202d785293969bd38c1d9e62ba8e8e` (serialización atómica por clave del ciclo de vida SSE)  
**Commit de gobernanza sobre el fix:** `53a243d05d0226c6d29357205600ee7ca6c5a9cd` (evidencia de exportación limpia y control positivo baseline v2.2, solo documentos)  
**Base del diseño:** `265ab8e60e0a51c4a5d898a129d20c5d794cb5c8`  
**Diseño de referencia:** Codex v2.2, `docs/orchestration/issue-6-sse-design-v2.2.md`  
**Reporte de construcción (builder Antigravity):** `docs/orchestration/issue-6-corrective-build.md` (commit verificado `ce9899e`, 199 tests, BUILD SUCCESS)  
**Worktree / rama:** `/home/gonzalocar/orca/workspaces/Norma-/reconcile-6-storefront`, `GonzaCarrillo/reconcile-6-storefront`  
**Terminal auditora:** `term_0f04be1f-9a14-48e5-8160-aeef1ffa73b4`  
**Fecha:** 2026-11-13

---

## 1. Veredicto global

**QA_APPROVED** sobre `5008d07487f487f8428f5fad46a3a2e31eb7f3df`.

La ejecución limpia independiente sobre `git archive HEAD` completó `mvn -B clean verify` con `EXIT_CODE=0` (`BUILD SUCCESS`), 199 tests ejecutados, 0 fallos, 0 errores, 0 omitidos. El código de producción en `Api.java` cumple el contrato v2.2 (registro con `compute` y límite de 5 dentro del lock atómico de clave, retiro idempotente con `computeIfPresent`, I/O y terminación fuera de los bloques de reasignación, helper `removeEmitter` compartido e idempotente). La regresión concurrente es determinista (`CountDownLatch`, sin `Thread.sleep`), el control positivo del baseline es real (intercalación sobre código v2.1, sin lambda sintética), y la prueba HTTP SSE de desconexión/confirmación/reconexión se conserva intacta con sus asserts sin debilitar.

No se modificó código de producción, pruebas, POM, contratos, estáticos ni informes del builder/diseño. Este informe y su evidencia propia en `issue-6-corrective-evidence/qa/v2.2/` son los únicos archivos nuevos.

---

## 2. Entorno y recursos

| Ítem | Valor |
|---|---|
| JDK | Microsoft OpenJDK 25.0.4.1+1-LTS (`/home/gonzalocar/.jdks/ms-25.0.4.1`), `java.specification.version=25` |
| Maven | Apache Maven 3.8.7 |
| SO | Linux 6.14.0-37-generic (amd64) |
| Exportación | `git archive HEAD` → `target/qa-opencode/export/` (solo archivos rastreados, sin `.git`, sin archivos sin seguimiento) |
| DB/uploads/tmp | Aislados bajo `target/export-runtime/` propio de la exportación (`norma.uploads`, `java.io.tmpdir`), nunca recursos de #4 ni puertos compartidos |
| groq.api.key | Vacío (`""`); MediaService mockeado en tests de contrato/SSE; sin red Groq/TTS |
| Puerto HTTP/SSE | `RANDOM_PORT` (`SpringBootTest.WebEnvironment.RANDOM_PORT`) |

---

## 3. Compilación y regresión (re-ejecutada de forma independiente por QA)

Secuencia ejecutada desde la exportación limpia:

```bash
export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1
export PATH="$JAVA_HOME/bin:$PATH"
mvn -B clean
mkdir -p target/export-runtime/tmp target/export-runtime/uploads
mvn -B verify \
  -DargLine="-Djava.io.tmpdir=$(pwd -P)/target/export-runtime/tmp" \
  -Dnorma.uploads="$(pwd -P)/target/export-runtime/uploads" \
  -Dgroq.api.key=
```

**Resultado real:** `EXIT_CODE=0`, `BUILD SUCCESS`, duración `02:59 min`.  
**Tests Surefire:** 199 ejecutados, 0 fallos, 0 errores, 0 omitidos (17 suites).

**Hashes SHA-256:**
- Registro de verificación (`clean-verify.log`): `b80d3ea9f80534ba72918d4950e2b23e75af80bef7d033534301fff07b3cf0be`
- Artefacto empaquetado (`taller-1.0.0.jar`): `788fff32f1d9528aef61b3412c478476e169ae2ffc65bd2bdb0b4b0b15e4ff82`

**Desglose de suites Surefire (exportación limpia):**

| Suite | Tests | Fallos | Errores | Omitidos |
|---|---:|---:|---:|---:|
| ApiAudioTest | 4 | 0 | 0 | 0 |
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
| IntentParserTest | 61 | 0 | 0 | 0 |
| MediaHttpTest | 23 | 0 | 0 | 0 |
| MediaServiceTest | 6 | 0 | 0 | 0 |
| PhotoProcessorTest | 19 | 0 | 0 | 0 |
| StorefrontContractTest | 6 | 0 | 0 | 0 |
| StorefrontSseTest | 1 | 0 | 0 | 0 |
| **Total general** | **199** | **0** | **0** | **0** |

Nota: el reporte del builder citó DemoMediaTest con 4 tests; el desglose Surefire de esta ejecución independiente registra 3 tests para esa suite. El total de 199 y el resultado `0 fallos / 0 errores` coinciden con la salida de Maven y con el reporte del builder. No se usó `skipTests` ni exclusiones para obtener verde.

---

## 4. Verificación de criterios del diseño v2.2

Se contrastó cada criterio contra el código de producción en `src/main/java/com/norma/taller/Api.java` y las pruebas de comportamiento.

| # | Criterio (diseño v2.2) | Resultado | Evidencia / nota |
|---|---|---|---|
| C1 | Registro atómico por clave: `streams.compute(sessionId, ...)` con comprobación `list.size() >= 5` dentro del lock atómico de la clave | **PASS** | `Api.java:36-47`; crea `CopyOnWriteArrayList` si falta, rechaza por `IllegalArgumentException` sin mutar el mapa al superar el límite, añade el emisor y retorna la lista. |
| C2 | Retiro idempotente atómico: `streams.computeIfPresent(sessionId, ...)` retorna `null` si y solo si la lista queda vacía | **PASS** | `Api.java:49-54`; solo opera si la clave existe; preserva la lista si un hilo concurrente añadió un emisor (no orfana suscripciones activas). |
| C3 | Prohibido comprobar `list.isEmpty()` fuera de `compute`/`computeIfPresent` ni usar `streams.remove(key, list)` | **PASS** | Inspección de `Api.java`: no hay `streams.remove(...)` ni comprobación de `isEmpty()` fuera de las operaciones atómicas. |
| C4 | Helper unificado e idempotente `removeEmitter(sessionId, emitter)` usado por `onCompletion`, `onTimeout`, `onError`, fallo de handshake y fallo de `publish()` | **PASS** | `Api.java:42-51,55-54,62-63`; `testRemoveEmitterIdempotence` comprueba múltiples retiros como no-op seguro. |
| C5 | Ninguna operación bloqueante, I/O de socket (`emitter.send`), terminación (`emitter.complete`) ni catálogo dentro de `compute`/`computeIfPresent` | **PASS** | `publish()` itera sobre la lista COW fuera de locks de mapa; `e.complete()` envuelto en `try-catch`; `catalog.ensure()` fuera de los bloques atómicos. |
| C6 | Callbacks idempotentes | **PASS** | `removeEmitter` es idempotente; `testRemoveEmitterIdempotence` y `testBrokenEmitterDoesNotThrowAndIsRemoved` lo cubren. |
| C7 | Otra sesión sana (aislamiento) | **PASS** | `ApiPublishTest.testSessionIsolation`: fallos en sesión A no afectan entrega a sesión B. |
| C8 | Sin emitter huérfano | **PASS** | `ApiPublishTest.testConcurrentCleanupVsNewRegistrationDoesNotOrphanSubscription`: 50 rondas, la nueva suscripción permanece en `streams` y recibe `publish()`. |
| C9 | Regresión concurrente determinista (sin `Thread.sleep`) | **PASS** | `CountDownLatch` + `CyclicBarrier`-style sincronía por latch; `testConcurrentCleanupVsNewRegistrationDoesNotOrphanSubscription` y `testConcurrentRegistrationLimitFive`. |
| C10 | Límite de 5 bajo concurrencia: exactamente 5 aceptados, 5 rechazados con `IllegalArgumentException` | **PASS** | `testConcurrentRegistrationLimitFive`: 10 peticiones concurrentes, exactamente 5 aceptadas y 5 rechazadas, lista persistida en 5. |
| C11 | Control positivo del baseline real (sin lambda sintética) | **PASS** | Builder ejecutó intercalación determinista sobre código exacto de v2.1 (`commit 59e4e41`) en `target/v21-control/`; defecto reproducido: nueva suscripción huérfana (`streams.get(sessionId)` es NULL). Evidencia: `docs/orchestration/issue-6-corrective-evidence/builder/sse-v2.2/baseline-p2-interleaving.log`. No hay lambda de control positivo en las pruebas v2.2 (eliminada la sintética de v2.1). |
| C12 | Prueba HTTP SSE preservada: desconexión, confirmación, reconexión y recuperación por GET; asserts no debilitados | **PASS** | `StorefrontSseTest.testCompleteSseTransportLifecycle` intacto: servidor HTTP real (`RANDOM_PORT`), `HttpClient` lector SSE, streams A/B, evento inicial `conectado`, cierre de `readerA`, mutación offline vía API (HTTP 200 en POST), reapertura recibe `conectado`, GET refleja precio 35000, control positivo en B. |
| C13 | No se tocó catálogo, persistencia, contratos REST, esquema SQLite v2, estáticos frontend, audio/voz/`MediaConfig` | **PASS** | Diff de `ce9899e` sobre la base: solo `Api.java` (métodos `stream`/`publish`/`removeEmitter`), `ApiPublishTest.java` y `StorefrontSseTest.java` (alcance cerrado del diseño). `target/` gitignorado. |
| C14 | Zoom nativo 200% | **Aceptación manual del usuario** | El usuario aceptó explícitamente el zoom nativo ("la #6 el zoom ya lo probe yo y funciona, dalo por hecho"). Se conserva la atribución manual; QA no re-exige automatización ni atribuye evidencia técnica de navegador. |

---

## 5. Evidencia propia de QA (ruta y procedencia)

Guardada bajo `docs/orchestration/issue-6-corrective-evidence/qa/v2.2/` (archivos nuevos, propios de esta auditoría; no sobrescribe builder ni diseños):

- `commit.txt`: SHA auditado `5008d07487f487f8428f5fad46a3a2e31eb7f3df`.
- `clean-verify.log`: log completo de `mvn -B clean verify` sobre la exportación limpia (`EXIT_CODE=0`, `BUILD SUCCESS`). SHA-256 en `clean-verify.log.sha256` (`b80d3ea9…`).
- `taller-1.0.0.jar.sha256`: hash del artefacto `788fff32…`.
- `surefire-reports/TEST-*.xml`: reportes Surefire de las 17 suites (199 tests, 0 fallos/0 errores/0 omitidos).

---

## 6. Limitaciones

- El criterio de zoom nativo a 200% se cubre por aceptación manual explícita del usuario; esta auditoría no aporta evidencia técnica de indicador de menú del navegador, método ni capturas, y no re-exige automatización (atribución manual conservada por decisión del usuario).
- No se re-ejecutó el control positivo del baseline sobre v2.1 en esta sesión de QA; se inspeccionó y referenció la evidencia ya persistida por el builder en `docs/orchestration/issue-6-corrective-evidence/builder/sse-v2.2/baseline-p2-interleaving.log` (sin modificarla).
- No se modificó código de producción ni pruebas; no se desactivó ninguna prueba ni se usó `skipTests` ni exclusiones.

---

## 7. Conclusión

Todos los criterios de aceptación del diseño v2.2 están verificados: serialización atómica por clave (`compute`/`computeIfPresent`), retiro idempotente, I/O fuera de locks, callbacks idempotentes, aislamiento de sesiones, ausencia de emitter huérfano, regresión concurrente determinista, control positivo real del baseline (sin lambda sintética), límite de 5 bajo concurrencia, prueba HTTP SSE preservada con asserts intactos, y compilación limpia JDK 25 (`mvn -B clean verify`, `EXIT_CODE=0`, 199/0/0/0).

**Veredicto final: QA_APPROVED** sobre commit `5008d07487f487f8428f5fad46a3a2e31eb7f3df`.

Informe y evidencia autorizados para commit únicamente en rama `GonzaCarrillo/reconcile-6-storefront` (no `main`, sin push, sin merge, sin cierre de issue). Habilita la Fase 4 de Codex (aprobación arquitectónica final).
