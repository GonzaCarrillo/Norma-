# Issue #6 — Especificación Técnica Codex v2.1: Aislamiento de Fallos en Ciclo de Vida SSE

**Autor:** Codex (Software Architect)  
**Coordinador / Autorización:** Orca (`msg_8e4314b9d1c7`) bajo autorización del usuario  
**Base:** `e89c04bd225460683f004b6a59510d9003b1e191`  
**Referencia de hallazgo:** `docs/orchestration/issue-6-sse-diagnosis.md` y `docs/orchestration/issue-6-corrective-qa.md`  
**Estado:** Aprobado para implementación inmediata por Antigravity (Fase 2)  

---

## 1. Contexto y Problema

Durante la ejecución de auditoría QA independiente en la Fase 3 (`commit 4ed0fb7`), se observó un fallo intermitente HTTP 500 en `StorefrontSseTest.java:433` tras desconectar el cliente SSE (`readerA.close()`) y ejecutar mutaciones offline:
`InternalServer 500 Internal Server Error: "{"message":"No pude completar el pedido. Intentá otra vez."}"`.

El diagnóstico de causa raíz (`issue-6-sse-diagnosis.md`) demostró que:
1. En `Api.publish(String id)`:
   ```java
   void publish(String id){
     for(var e:streams.getOrDefault(id,new CopyOnWriteArrayList<>()))
       try{
         e.send(SseEmitter.event().name("catalog-update").data("actualizado"));
       }catch(Exception ex){
         e.complete();
       }
   }
   ```
2. Cuando el cliente cierra la conexión TCP, `e.send(...)` lanza `IOException`.
3. El bloque `catch (Exception ex)` invoca `e.complete()`.
4. En Tomcat 10.1 / Spring WebMVC, invocar `complete()` sobre un emisor asíncrono cuyo socket ya falló o cuyo `AsyncListener.onError()` ya retornó lanza `IllegalStateException` y `AsyncRequestNotUsableException: Response not usable after response errors.`.
5. Como `e.complete()` no está encapsulado en un bloque `try-catch`, la excepción asíncrona escapa de `publish()` y aborta la petición transaccional HTTP del llamador (`/api/bot/message` o `/api/reset`), convirtiendo una mutación válida de base de datos en una respuesta HTTP 500.
6. Además, `publish` no remueve sincrónicamente el emisor fallido de la lista de `streams`, dejando emisores rotos que generan carreras en mutaciones consecutivas antes de que los callbacks diferidos de Tomcat se ejecuten.

---

## 2. Contrato de la Corrección v2.1

### 2.1 Principio de Aislamiento y Resiliencia de Notificaciones
- **Aislamiento transaccional:** La difusión de eventos SSE es una notificación reactiva desacoplada. Un emisor desconectado o con `send()` o `complete()` fallidos **nunca debe convertir en 500 la respuesta de una mutación del catálogo ya validada y persistida**.
- **Limpieza sincrónica e idempotente:** Ante cualquier fallo en `send(...)`, el emisor debe retirarse de inmediato de la colección de la sesión (`CopyOnWriteArrayList`), incluso si el intento posterior de `complete()` arroja una excepción en tiempo de ejecución. Si la lista queda vacía, debe removerse de `streams` de forma atómica e idempotente (`streams.remove(sessionId, list)`).
- **Notificación continua a emisores sanos:** El fallo de un emisor no debe abortar la iteración sobre los demás emisores de la misma sesión ni afectar a emisores de otras sesiones concurrentes.
- **Terminación best-effort:** `complete()` debe ejecutarse de forma protegida contra `Exception` para evitar que `AsyncRequestNotUsableException` o `IllegalStateException` de Tomcat se propaguen.
- **Protección de `onTimeout`:** Revisar el callback `onTimeout` para que la llamada a `emitter.complete()` también se realice de forma protegida, previniendo condiciones de doble fallo al vencer el temporizador sobre conexiones ya abortadas.
- **No ocultamiento de errores fuera de SSE:** No capturar `Error` (solo `Exception`) y no alterar la propagación de errores de dominio de la lógica de catálogo, parsing o persistencia.
- **Integridad de payload:** Mantener el evento `catalog-update` con data `actualizado` y `conectado`, preservando el snapshot GET como autoridad de datos.

---

## 3. Alcance Cerrado Autorizado

- **Archivos de producción modificables:**
  - `src/main/java/com/norma/taller/Api.java`: Exclusivamente `publish` y los callbacks/helpers de ciclo de vida SSE (`stream`, `onTimeout`, limpieza).
- **Archivos de pruebas modificables y nuevos:**
  - `src/test/java/com/norma/taller/ApiPublishTest.java`: Nueva suite unitaria/integración determinista con pruebas dirigidas de aislamiento, control positivo y múltiples emisores.
  - `src/test/java/com/norma/taller/StorefrontSseTest.java`: Robustecimiento del caso de desconexión/reconexión y mutaciones sin pausas.
- **Documentación y evidencia:**
  - `docs/orchestration/issue-6-sse-design-v2.1.md` (este documento).
  - `docs/orchestration/issue-6-corrective-build.md` (reporte de construcción actualizado).
  - `docs/orchestration/issue-6-corrective-evidence/builder/sse/` (evidencia de pruebas dirigidas y clean export).
- **Frontera prohibida estricta:**
  - No modificar `bot.process`, endpoints de audio/STT/TTS, constructores de `Api`, DTOs ni `Catalog`.
  - No modificar `MediaConfig`, `ApiErrors`, frontend estático (`index.html`, `style.css`, `app.js`), esquemas SQL, POM ni `application.properties`.
  - La Issue #5 no edita `Api` hasta que esta pista quede integrada.

---

## 4. Diseño Detallado de la Implementación

### 4.1 Modificación en `Api.java`
```java
// Implementación requerida para publish y helpers de limpieza:
void publish(String id) {
  var list = streams.get(id);
  if (list == null) return;
  for (var e : list) {
    try {
      e.send(SseEmitter.event().name("catalog-update").data("actualizado"));
    } catch (Exception ex) {
      list.remove(e);
      if (list.isEmpty()) {
        streams.remove(id, list);
      }
      try {
        e.complete();
      } catch (Exception ignored) {
        // Previene escape de AsyncRequestNotUsableException / IllegalStateException
      }
    }
  }
}
```
En el registro del emisor (`stream`):
```java
Runnable cleanup = () -> {
  list.remove(emitter);
  if (list.isEmpty()) streams.remove(sessionId, list);
};
emitter.onCompletion(cleanup);
emitter.onTimeout(() -> {
  cleanup.run();
  try {
    emitter.complete();
  } catch (Exception ignored) {}
});
emitter.onError(e -> cleanup.run());
```

---

## 5. Estrategia de Pruebas y Criterios de Aceptación de QA

1. **`ApiPublishTest.java` (Determinista):**
   - Emisor con `send()` que arroja `IOException` y `complete()` que arroja `IllegalStateException` / `AsyncRequestNotUsableException`:
     * Demostrar que `publish(sessionId)` no arroja ninguna excepción.
     * Demostrar que el emisor fallido es removido de la colección.
     * Demostrar que un segundo emisor sano en la misma sesión recibe la notificación exitosamente.
     * Demostrar aislamiento total respecto a sesiones B y C.
   - Control positivo que demuestre que el comportamiento previo sin try-catch arrojaba la excepción hacia el llamador.
2. **`StorefrontSseTest.java` (Transporte HTTP real):**
   - Ejecutar la secuencia de cierre abrupto de `readerA` seguida inmediatamente de mutación y confirmación sin demoras (`msgOfflineProp` y `msgYes`).
   - Verificar que ambas peticiones retornan HTTP 200 con su respuesta de negocio.
   - Verificar que la reapertura del stream recibe `conectado` y el catálogo refleja el nuevo estado.
3. **Verificación de Export Limpio (JDK 25):**
   - `git archive HEAD` hacia carpeta temporal aislada.
   - `mvn -B clean` seguido de `mkdir -p target/export-runtime/...` y `mvn -B verify`.
   - Exit code 0, 0 tests fallidos o con error, sumas SHA-256 registradas en `issue-6-corrective-build.md`.
4. **Zoom nativo:**
   - Formalmente registrado como aceptado manualmente por el usuario. Sin invención de datos ni capturas artificiales.
