# Reporte de Construcción Correctiva — Issue #6: Vidriera accesible con SSE (v2.1)

**Fecha:** 2026-11-13  
**Agente:** Antigravity (Software Builder / Implementer)  
**Coordinador:** Orca (Lead Software Engineer y Orquestador Técnico)  
**Tarea / Dispatch:** `task_439c9f85845f` / `ctx_decc904f5975` (continuación de `task_bcc51c9f95ca` / `ctx_74037e27e6e5` y diagnóstico `task_2d54903f5c8c`)  
**Terminal Builder:** `term_7f7d8270-a58a-478b-831d-3b43b8c6fcce`  
**Terminal Coordinador:** `term_104a5a3f-cca1-4813-a727-30ddd7a9152f`  
**Worktree:** `/home/gonzalocar/orca/workspaces/Norma-/reconcile-6-storefront`  
**Rama:** `GonzaCarrillo/reconcile-6-storefront`  
**Commit base de fase 2 v2.1:** `e89c04bd225460683f004b6a59510d9003b1e191`  
**Commit implementado y verificado:** `59e4e41d2e8d92b76511a195ac9409fb2c9b1869`  
**Diseño de referencia:** Codex v2.0 (`docs/orchestration/issue-6-corrective-design.md`) y Especificación v2.1 (`docs/orchestration/issue-6-sse-design-v2.1.md`)  

---

## 1. Alcance Cerrado y Resumen de la Intervención

1. **Código de producción estático (CSS/JS/HTML):**
   - Preservado intacto tras demostrarse en la inspección visual/DOM previa que la grilla responsive, la uniformidad de tarjetas (0.00 px de diferencia), la proporción 4:3 de fotografías, el reflow sin scroll horizontal, el diálogo modal `<dialog id="zoom">` y el simulador semántico operan al 100% de conformidad.
2. **Aceptación manual de Zoom 200% (V1-zoom200):**
   - Conforme a la instrucción oficial del Coordinador Orca (`msg_651772357c1e`), el usuario probó y aceptó manualmente la funcionalidad de zoom nativo al 200% en su navegador de escritorio (*"la #6 el zoom ya lo probe yo y funciona, dalo por hecho..."*). El bloqueo ambiental previo queda formalmente levantado por decisión y verificación directa del usuario, sin manipulación de zoom CSS ni escala DPR emulada.
3. **Aislamiento de fallos en el ciclo de vida SSE (`Api.java` — v2.1):**
   - Implementada la especificación técnica de Codex v2.1 para resolver de raíz el fallo intermitente HTTP 500 detectado durante la auditoría QA de Fase 3 (`issue-6-corrective-qa.md` / `issue-6-sse-diagnosis.md`).
   - `Api.publish(String id)`:
     - Remoción inmediata y sincrónica del emisor fallido de la lista `CopyOnWriteArrayList` en caso de error en `e.send(...)`.
     - Limpieza atómica e idempotente del mapa de sesiones `streams` si la lista queda vacía (`streams.remove(id, list)`).
     - Protección de `e.complete()` con bloque `try-catch (Exception ignored)` para evitar que excepciones asíncronas de Tomcat 10.1 (`IllegalStateException` / `AsyncRequestNotUsableException: Response not usable after response errors`) escapen del método `publish` y aborten la mutación transaccional con HTTP 500.
     - Protección idéntica en el callback `onTimeout` ante cierres concurrentes.
     - Acceso package-private al mapa `streams` para posibilitar auditoría y pruebas unitarias deterministas sin romper encapsulamiento público.
4. **Pruebas y Verificación:**
   - Creado `src/test/java/com/norma/taller/ApiPublishTest.java` con 5 pruebas unitarias deterministas (aislamiento de fallos, saneamiento de emisores, sesiones independientes, control positivo de fallo del baseline).
   - Actualizado `src/test/java/com/norma/taller/StorefrontSseTest.java` asegurando aserciones de código HTTP 200 en mutaciones posteriores al cierre del lector SSE offline.
5. **Frontera estricta e invariantes respetadas:**
   - Cero modificaciones a la lógica de catálogo, persistencia, esquemas SQLite v2, parsing de intenciones, dependencias Maven ni lógica de voz/media (#5).

---

## 2. Implementación Técnica Detallada (Codex v2.1)

### 2.1 Modificación en `Api.java`
```java
 void publish(String id){
  var list=streams.get(id);
  if(list==null)return;
  for(var e:list){
   try{
    e.send(SseEmitter.event().name("catalog-update").data("actualizado"));
   }catch(Exception ex){
    list.remove(e);
    if(list.isEmpty())streams.remove(id,list);
    try{e.complete();}catch(Exception ignored){}
   }
  }
 }
```
- **Garantías logradas:**
  1. Aislamiento transaccional total: la mutación de base de datos nunca falla por el estado de la conexión SSE del cliente.
  2. Eliminación de carreras: si un cliente desconectado produce varias mutaciones seguidas, el emisor roto ya fue retirado en la primera iteración, impidiendo acumulación de fallos.
  3. Difusión a emisores activos: si un emisor falla, la iteración continúa y los emisores activos de la misma o de otras sesiones reciben su notificación en tiempo y forma.

### 2.2 Suite Unitaria `ApiPublishTest.java`
- `testBrokenEmitterDoesNotThrowAndIsRemoved`: Verifica que un emisor que lanza `IOException` en `send()` e `IllegalStateException` en `complete()` no propaga excepción y es retirado de la lista y del mapa.
- `testHealthyEmitterStillReceivesWhenAnotherFails`: Verifica que un emisor sano en la misma sesión recibe el evento aun cuando otro emisor falla.
- `testSessionIsolation`: Verifica que un fallo en la sesión A no afecta la entrega de eventos a la sesión B.
- `testPublishOnNonExistentSession`: Verifica que publicar en una sesión inexistente o sin emisores es un no-op seguro.
- `testPositiveControlBaselineBehavior`: Control positivo que reproduce y demuestra que el código anterior sin try-catch arrojaba la excepción asíncrona hacia el llamador.

---

## 3. Evidencia Funcional Previa (Métricas de Reflow y Viewports)

Se mantienen vigentes los resultados obtenidos en el arnés de ejecución aislada en servidor real (`docs/orchestration/issue-6-corrective-evidence/builder/builder_evidence.json`):

| Viewport | Ancho (CSS px) | Alto (CSS px) | Tarjetas | Diff Altura | Scroll Horizontal | Aspect Ratio Fotos (1..3) |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| `desktop_1440` | 1440 | 900 | 8 | **0.00 px** | **false** | 1.333 (4:3) |
| `tablet_768` | 768 | 1024 | 8 | **0.00 px** | **false** | 1.333 (4:3) |
| `reflow_640_zoom200` | 640 | 900 | 8 | **0.00 px** | **false** | 1.333 (4:3) |
| `mobile_375` | 375 | 812 | 8 | **0.00 px** | **false** | 1.333 (4:3) |
| `mobile_320_min` | 320 | 640 | 8 | **0.00 px** | **false** | 1.333 (4:3) |

- **Tipografía (#font):** Escala de 22.0 px a 28.6 px (+30% exacto).
- **Diálogo modal (#zoom):** Foco atrapado en `#close-zoom`, cierre con `Escape` y retorno de foco.
- **Simulador semántico:** 11 controles presentes y accesibles.

---

## 4. Evidencia de Compilación y Verificación Limpia (JDK 25)

Siguiendo el protocolo oficial de `testing-and-qa`:
- **Commit exportado y auditado:** `59e4e41d2e8d92b76511a195ac9409fb2c9b1869`
- **Ruta de exportación limpia:** `/home/gonzalocar/orca/workspaces/Norma-/reconcile-6-storefront/target/qa.66XhTA`
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
  - Registro de verificación (`verify.log`): `cf87e7d64938cb78cae14b285eed3d933deeb3876d606f147cad12486e8d39f5`
  - Archivo ejecutable empaquetado (`taller-1.0.0.jar`): `6e4d0012d0e920af847ebeb2447c8773bc28c14e766c2f8a0a850b99455051a8`
- **Suites ejecutadas:** 17 suites Surefire, **197 tests ejecutados, 0 fallos, 0 errores, 0 omitidos** (incluye los 5 tests de `ApiPublishTest` y `StorefrontSseTest`).

### Desglose de Suites Surefire XML (`target/surefire-reports/` en exportación limpia):
| Archivo de Reporte | Tests | Fallos | Errores | Omitidos |
|---|:---:|:---:|:---:|:---:|
| `TEST-com.norma.taller.ApiAudioTest.xml` | 4 | 0 | 0 | 0 |
| `TEST-com.norma.taller.ApiPublishTest.xml` | 5 | 0 | 0 | 0 |
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
| **Total General** | **197** | **0** | **0** | **0** |

---

## 5. Artefactos de Evidencia Generados
- `docs/orchestration/issue-6-sse-design-v2.1.md`: Especificación técnica v2.1 de aislamiento SSE aprobada.
- `docs/orchestration/issue-6-corrective-evidence/builder/sse/commit.txt`: Hash del commit verificado (`59e4e41d2e8d92b76511a195ac9409fb2c9b1869`).
- `docs/orchestration/issue-6-corrective-evidence/builder/sse/clean-export-verify.log`: Log completo de la ejecución limpia de Maven verify.
- `docs/orchestration/issue-6-corrective-evidence/builder/sse/clean-export-verify.log.sha256`: Suma SHA-256 del log.
- `docs/orchestration/issue-6-corrective-evidence/builder/sse/taller-1.0.0.jar.sha256`: Suma SHA-256 del binario empaquetado.
- `docs/orchestration/issue-6-corrective-evidence/builder/sse/TEST-com.norma.taller.ApiPublishTest.xml`: Reporte Surefire XML de pruebas deterministas de publish.
- `docs/orchestration/issue-6-corrective-evidence/builder/sse/TEST-com.norma.taller.StorefrontSseTest.xml`: Reporte Surefire XML del test de integración de SSE.

---

## 6. Declaración de Diferencia Documental y Estado de Entrega
1. **Diferencia documental:** Conforme al estándar `testing-and-qa`, se declara expresamente que la verificación limpia fue ejecutada sobre el commit de código `59e4e41d2e8d92b76511a195ac9409fb2c9b1869`. El commit resultante que incluye este reporte y los artefactos de evidencia no altera el código compilable ni los binarios de la aplicación.
2. **Listo para Fase 3 (Re-auditoría QA por GitHub Copilot):** La implementación correctiva se encuentra cerrada, verificada y documentada, lista para que el Lead Software Engineer (Orca) asigne la Fase 3 a GitHub Copilot y la posterior Fase 4 a Codex.
