# Reporte de Construcción Correctiva — Issue #6: Vidriera accesible con SSE (v2.2)

**Fecha:** 2026-11-13  
**Agente:** Antigravity (Software Builder / Implementer)  
**Coordinador:** Orca (Lead Software Engineer y Orquestador Técnico)  
**Tarea / Dispatch:** `task_5b89cd42690b` / `ctx_ebcd89119efe` (resolución de P2 QA_REJECTED sobre `commit 265ab8e`)  
**Terminal Builder:** `term_7f7d8270-a58a-478b-831d-3b43b8c6fcce`  
**Terminal Coordinador:** `term_104a5a3f-cca1-4813-a727-30ddd7a9152f`  
**Worktree:** `/home/gonzalocar/orca/workspaces/Norma-/reconcile-6-storefront`  
**Rama:** `GonzaCarrillo/reconcile-6-storefront`  
**Commit base de fase 2 v2.2:** `265ab8e60e0a51c4a5d898a129d20c5d794cb5c8`  
**Commit implementado y verificado:** `ce9899eac3202d785293969bd38c1d9e62ba8e8e`  
**Diseño de referencia:** Codex v2.0 (`docs/orchestration/issue-6-corrective-design.md`), v2.1 (`docs/orchestration/issue-6-sse-design-v2.1.md`) y v2.2 (`docs/orchestration/issue-6-sse-design-v2.2.md`)  

---

## 1. Alcance Cerrado y Resumen de la Intervención v2.2

Conforme a la especificación correctiva aprobada por Orca y Codex (v2.2):
1. **Resolución del Hallazgo P2 de Concurrencia:**
   - GitHub Copilot identificó en v2.1 que la comprobación `list.isEmpty()` y la posterior llamada `streams.remove(sessionId, list)` no eran atómicas respecto a nuevos registros `stream()` concurrentes. Si un cliente abría una conexión justo en esa ventana, obtenía la misma lista mediante `computeIfAbsent` y la llamada diferida `streams.remove(sessionId, list)` eliminaba la clave del mapa, dejando la nueva conexión huérfana.
   - En v2.2 se serializan atómicamente por clave todas las mutaciones sobre el mapa `streams`:
     - **Registro:** Se ejecuta exclusivamente mediante `streams.compute(sessionId, ...)`. Obtiene o crea la lista `CopyOnWriteArrayList`, evalúa el límite de 5 ventanas (`list.size() >= 5`) y añade el emisor dentro del lock del nodo en `ConcurrentHashMap`.
     - **Retiro:** Se ejecuta exclusivamente mediante `streams.computeIfPresent(sessionId, ...)`. Retira solo el emisor indicado de la lista actual y devuelve `null` si y solo si la lista queda vacía dentro de la misma operación atómica.
     - **Helper común:** Se introdujo `removeEmitter(sessionId, emitter)` compartido por `onCompletion`, `onTimeout`, `onError`, fallo de handshake inicial en `stream()` y fallo de notificación en `publish()`. Es completamente idempotente.
     - **Operaciones fuera de lock:** Las notificaciones I/O (`e.send(...)`), la terminación protegida (`try { e.complete(); } catch (Exception ignored) {}`), y las verificaciones de catálogo (`catalog.ensure(...)`) se ejecutan estrictamente fuera de `compute` o `computeIfPresent`.
2. **Código de Producción Estático:**
   - Preservado intacto tras verificarse que la grilla responsive, la uniformidad de tarjetas (0.00 px), la proporción 4:3 de fotografías, el diálogo modal `<dialog id="zoom">` y el simulador semántico operan al 100% de conformidad.
3. **Aceptación manual de Zoom 200% (V1-zoom200):**
   - Confirmada y aceptada manualmente por el usuario. El bloqueo ambiental queda formalmente levantado y cerrado sin manipulación de zoom CSS ni DPR emulado.
4. **Gobernanza de QA:**
   - Conforme a la instrucción del usuario comunicada en el despacho, GitHub Copilot finalizó su ciclo de auditoría y las futuras fases de auditoría QA quedan asignadas a OpenCode / Nemotron.

---

## 2. Implementación Técnica en `Api.java` (v2.2)

```java
 @GetMapping(value="/api/catalog/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
 public SseEmitter stream(@RequestParam String sessionId){
  catalog.ensure(sessionId);var emitter=new SseEmitter(300000L);
  streams.compute(sessionId,(k,currentList)->{
   var list=currentList!=null?currentList:new CopyOnWriteArrayList<SseEmitter>();
   if(list.size()>=5)throw new IllegalArgumentException("Ya hay varias ventanas abiertas para esta sesión.");
   list.add(emitter);
   return list;
  });
  Runnable cleanup=()->removeEmitter(sessionId,emitter);
  emitter.onCompletion(cleanup);
  emitter.onTimeout(()->{cleanup.run();try{emitter.complete();}catch(Exception ignored){}});
  emitter.onError(e->cleanup.run());
  try{emitter.send(SseEmitter.event().name("catalog-update").data("conectado"));}catch(IOException e){cleanup.run();}
  return emitter;
 }
 void removeEmitter(String sessionId,SseEmitter emitter){
  streams.computeIfPresent(sessionId,(k,list)->{
   list.remove(emitter);
   return list.isEmpty()?null:list;
  });
 }
 void publish(String id){
  var list=streams.get(id);
  if(list==null)return;
  for(var e:list){
   try{
    e.send(SseEmitter.event().name("catalog-update").data("actualizado"));
   }catch(Exception ex){
    removeEmitter(id,e);
    try{e.complete();}catch(Exception ignored){}
   }
  }
 }
```

---

## 3. Demostración Empírica del Defecto Baseline y Pruebas Deterministas

### 3.1 Control Positivo sobre el Baseline Real (sin lambdas sintéticas)
En cumplimiento de la exigencia del despacho:
- Se ejecutó una prueba de intercalación determinista en un entorno temporal (`target/v21-control/`) sobre el código exacto de v2.1 (`commit 59e4e41`).
- **Defecto reproducido:** Cuando el cleanup retira el último emisor y un nuevo `stream()` se suscribe concurrentemente antes de que se ejecute `streams.remove(sessionId, list)`, el método `streams.remove` del baseline elimina la lista del mapa a pesar de que ya contiene la nueva suscripción activa.
- **Evidencia persistida:** `docs/orchestration/issue-6-corrective-evidence/builder/sse-v2.2/baseline-p2-interleaving.log` (SHA-256: `dc7aa757c8bbe37dda6d55f49a15807f5b64446c87197f1553bb6d5d6a626206`). Registro explícito: `[DEMOSTRACIÓN DEFECTO BASELINE] La nueva suscripción quedó huérfana: streams.get(sessionId) es NULL`.

### 3.2 Nueva Suite Unitaria Concurrente en `ApiPublishTest.java`
Se implementaron 7 pruebas unitarias deterministas:
1. `testBrokenEmitterDoesNotThrowAndIsRemoved`: Emisor roto con fallos en `send()` y `complete()` es retirado y el mapa limpiado sin propagar excepción HTTP 500.
2. `testHealthyEmitterStillReceivesWhenAnotherFails`: Emisores sanos en la misma sesión continúan recibiendo eventos tras el fallo de otro emisor.
3. `testSessionIsolation`: Fallos en sesión A no afectan la entrega a sesión B.
4. `testPublishOnNonExistentSession`: Publicación en sesión inexistente es no-op seguro.
5. `testConcurrentCleanupVsNewRegistrationDoesNotOrphanSubscription`: **Regresión P2 determinista.** 50 rondas con `CountDownLatch` ejecutando simultáneamente el retiro del emisor previo y el registro de un nuevo emisor. En el 100% de las rondas, la nueva suscripción permanece registrada en `streams` y recibe `publish()` sin orfanarse.
6. `testConcurrentRegistrationLimitFive`: 10 peticiones concurrentes simultáneas de registro con `CountDownLatch`. Exactamente 5 son aceptadas y exactamente 5 son rechazadas con `IllegalArgumentException`, manteniendo la lista en exactamente 5 emisores.
7. `testRemoveEmitterIdempotence`: Múltiples retiros del mismo emisor son no-ops seguros que no corrompen el mapa.

---

## 4. Evidencia de Compilación y Verificación Limpia (JDK 25)

Siguiendo el protocolo oficial de `testing-and-qa`:
- **Commit exportado y auditado:** `ce9899eac3202d785293969bd38c1d9e62ba8e8e`
- **Ruta de exportación limpia:** `/home/gonzalocar/orca/workspaces/Norma-/reconcile-6-storefront/target/qa.3FRXag`
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
- **Resultado:** `BUILD SUCCESS` (Exit code: `0`, duración: `02:34 min`).
- **Sumas de comprobación SHA-256:**
  - Registro de verificación (`verify.log`): `e7e4ce4be868c9f2b3427b0f8853a62961fb276d03e54ef0b08cc106e91a93d4`
  - Archivo ejecutable empaquetado (`taller-1.0.0.jar`): `35e37c5855f1f461ef0a1f24dca069d855245a77e9fefa03705146ad340c589d`
- **Suites ejecutadas:** 17 suites Surefire, **199 tests ejecutados, 0 fallos, 0 errores, 0 omitidos** (incremento neto de 2 tests deterministas sobre v2.1).

### Desglose de Suites Surefire XML (`target/surefire-reports/` en exportación limpia):
| Archivo de Reporte | Tests | Fallos | Errores | Omitidos |
|---|:---:|:---:|:---:|:---:|
| `TEST-com.norma.taller.ApiAudioTest.xml` | 4 | 0 | 0 | 0 |
| `TEST-com.norma.taller.ApiPublishTest.xml` | 7 | 0 | 0 | 0 |
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
| **Total General** | **199** | **0** | **0** | **0** |

---

## 5. Artefactos de Evidencia Persistidos
Directorio: `docs/orchestration/issue-6-corrective-evidence/builder/sse-v2.2/`
- `commit.txt`: Hash del commit verificado (`ce9899eac3202d785293969bd38c1d9e62ba8e8e`).
- `clean-export-verify.log`: Log completo de la ejecución de `mvn clean verify`.
- `clean-export-verify.log.sha256`: Hash SHA-256 del log.
- `taller-1.0.0.jar.sha256`: Hash SHA-256 del artefacto empaquetado.
- `TEST-com.norma.taller.ApiPublishTest.xml`: Reporte Surefire XML de las 7 pruebas de aislamiento y concurrencia.
- `TEST-com.norma.taller.StorefrontSseTest.xml`: Reporte Surefire XML del test de integración SSE.
- `baseline-p2-interleaving.log`: Log de reproducción del defecto de carrera P2 sobre el baseline v2.1.
- `baseline-p2-interleaving.log.sha256`: Hash SHA-256 del log de control positivo.

---

## 6. Declaración de Diferencia Documental y Estado de Entrega
1. **Diferencia documental:** Conforme a `testing-and-qa`, se declara que la compilación y pruebas limpias se ejecutaron sobre el commit `ce9899eac3202d785293969bd38c1d9e62ba8e8e`. La adición de este reporte final y los archivos de evidencia en un commit documental no altera el código compilable ni los binarios generados.
2. **Listo para Reauditoría QA (OpenCode / Nemotron):** La implementación v2.2 se entrega formalmente al Coordinador Orca para su remisión a la auditoría QA independiente de OpenCode, previa a la aprobación final de Codex (Fase 4).
