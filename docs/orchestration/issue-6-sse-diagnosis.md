# Diagnóstico Técnico — Fallo Intermitente HTTP 500 en Ciclo de Vida SSE (Issue #6)

**Fecha:** 2026-11-13  
**Agente:** Antigravity (Software Builder / Implementer)  
**Coordinador:** Orca (Lead Software Engineer y Orquestador Técnico)  
**Tarea / Dispatch:** `task_2d54903f5c8c` / `ctx_da62f6aa396a`  
**Terminal Builder:** `term_7f7d8270-a58a-478b-831d-3b43b8c6fcce`  
**Terminal Coordinador:** `term_104a5a3f-cca1-4813-a727-30ddd7a9152f`  
**Worktree:** `/home/gonzalocar/orca/workspaces/Norma-/reconcile-6-storefront`  
**Rama:** `GonzaCarrillo/reconcile-6-storefront`  
**Commit auditado (QA Copilot):** `9e11580f03bc82800938541008e566a3f32ba384`  
**Commit dictamen QA Copilot:** `4ed0fb7f0e5dbbcc2e92dd1ba6165da05140811a`  
**Referencia de auditoría:** `docs/orchestration/issue-6-corrective-qa.md`  

---

## 1. Registro Formal de Aceptación Manual Explícita del Usuario (V1-zoom200)

Conforme a la instrucción formal recibida de la coordinación técnica vía canal de orquestación (`msg_651772357c1e`):
> *"El usuario acaba de confirmar: la #6 el zoom ya lo probe yo y funciona, dalo por hecho, y continua con tus tareas. Registrar como aceptación manual explícita del usuario para V1-zoom200; no atribuir esa prueba a builder/Copilot ni fabricar capturas o mediciones. Se levanta bloqueo ambiental de zoom por esta aceptación."*

### Declaración de Trazabilidad:
1. El criterio **V1-zoom200 (zoom nativo de navegador 100% a 200% con reflow)** queda **ACEPTADO MANUALMENTE POR EL USUARIO** en su propio entorno de escritorio.
2. Esta verificación no se atribuye falsamente a la ejecución automatizada de Antigravity ni de Copilot QA en los entornos CLI/sidecar, no se fabrican capturas de ventana del sistema operativo ni se inyecta zoom CSS.
3. El bloqueo ambiental reportado en `issue-6-corrective-build.md` queda formalmente **LEVANTADO** en virtud de la aceptación expresa del usuario final.

---

## 2. Inspección y Reproducción del Fallo Intermitente HTTP 500

### 2.1 Evidencia Observada por Copilot en `commit 4ed0fb7`
- **Archivo de log analizado:** `docs/orchestration/issue-6-corrective-evidence/qa/clean-verify-initial.log.gz` (SHA-256: `7a453c922971e47330085fe407c44c1eff67db38425199fddcdc77f67e45b1a7`).
- **Punto de falla en prueba:** `StorefrontSseTest.java:433` (paso 8 del test: confirmación offline tras cerrar el lector SSE `readerA.close()`).
- **Error reportado:** `500 Internal Server Error: "{"message":"No pude completar el pedido. Intentá otra vez."}"`.

### 2.2 Stack Trace Exacto del Servidor
```text
2026-11-13T18:28:16.077-03:00 ERROR 158503 --- [taller-de-norma] [mcat-handler-22] com.norma.taller.ApiErrors : Fallo de solicitud

org.springframework.web.context.request.async.AsyncRequestNotUsableException: Response not usable after response errors.
	at org.springframework.web.context.request.async.StandardServletAsyncWebRequest$LifecycleHttpServletResponse.obtainLockOrRaiseException(StandardServletAsyncWebRequest.java:338) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.web.context.request.async.StandardServletAsyncWebRequest$LifecycleHttpServletResponse.flushBuffer(StandardServletAsyncWebRequest.java:320) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.http.server.ServletServerHttpResponse.flush(ServletServerHttpResponse.java:106) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.http.server.DelegatingServerHttpResponse.flush(DelegatingServerHttpResponse.java:61) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitterReturnValueHandler$DefaultSseEmitterHandler.complete(ResponseBodyEmitterReturnValueHandler.java:325) ~[spring-webmvc-6.2.19.jar:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.complete(ResponseBodyEmitter.java:283) ~[spring-webmvc-6.2.19.jar:6.2.19]
	at com.norma.taller.Api.publish(Api.java:42) ~[classes/:na]
	at com.norma.taller.Api.message(Api.java:30) ~[classes/:na]
```
Precedido en la pila asíncrona de Tomcat por:
```text
java.lang.IllegalStateException: A non-container (application) thread attempted to use the AsyncContext after an error had occurred and the call to AsyncListener.onError() had returned. This is not allowed to avoid race conditions.
	at org.apache.catalina.core.AsyncContextImpl.check(AsyncContextImpl.java:535) ~[tomcat-embed-core-10.1.55.jar:10.1.55]
	at org.apache.catalina.core.AsyncContextImpl.dispatch(AsyncContextImpl.java:152) ~[tomcat-embed-core-10.1.55.jar:10.1.55]
	at org.springframework.web.context.request.async.StandardServletAsyncWebRequest.dispatch(StandardServletAsyncWebRequest.java:171) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.web.context.request.async.WebAsyncManager.setConcurrentResultAndDispatch(WebAsyncManager.java:525) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.web.context.request.async.WebAsyncManager.lambda$startDeferredResultProcessing$8(WebAsyncManager.java:477) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.web.context.request.async.DeferredResult.setResultInternal(DeferredResult.java:272) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.web.context.request.async.DeferredResult.setErrorResult(DeferredResult.java:287) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitterReturnValueHandler$DefaultSseEmitterHandler.complete(ResponseBodyEmitterReturnValueHandler.java:329) ~[spring-webmvc-6.2.19.jar:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.complete(ResponseBodyEmitter.java:283) ~[spring-webmvc-6.2.19.jar:6.2.19]
	at com.norma.taller.Api.publish(Api.java:42) ~[classes/:na]
	at com.norma.taller.Api.message(Api.java:30) ~[classes/:na]
```

### 2.3 Reproducción Experimental Dirigida (Builder)
Se construyó un script de reproducción aislado en `docs/orchestration/issue-6-corrective-evidence/builder/sse/reproduce_sse_race.py`, ejecutando contra el JAR `target/taller-1.0.0.jar` con base de datos SQLite y puerto dinámico propios.
- **Evidencia generada:** `docs/orchestration/issue-6-corrective-evidence/builder/sse/sse_repro_results.json` y `docs/orchestration/issue-6-corrective-evidence/builder/sse/sse_repro_server.log`.
- **Comportamiento observado:** Al abrir una conexión SSE, cerrarla abruptamente desde el socket TCP e inmediatamente enviar una solicitud POST a `/api/bot/message`, el servidor registra en **7 de 7 iteraciones** exactamente la misma traza de error en `com.norma.taller.ApiErrors: Fallo de solicitud` con `AsyncRequestNotUsableException` originada en `Api.publish(Api.java:42)`.

---

## 3. Análisis de Causa Raíz (RCA)

La inspección de solo lectura del código fuente en `src/main/java/com/norma/taller/Api.java` revela el mecanismo exacto del defecto:

```java
// Líneas 33-43 en Api.java:
@GetMapping(value="/api/catalog/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@RequestParam String sessionId){
  catalog.ensure(sessionId);
  var emitter=new SseEmitter(300000L);
  var list=streams.computeIfAbsent(sessionId,k->new CopyOnWriteArrayList<>());
  if(list.size()>=5)throw new IllegalArgumentException("Ya hay varias ventanas abiertas para esta sesión.");
  list.add(emitter);
  Runnable cleanup=()->{
    list.remove(emitter);
    if(list.isEmpty())streams.remove(sessionId,list);
  };
  emitter.onCompletion(cleanup);
  emitter.onTimeout(()->{cleanup.run();emitter.complete();});
  emitter.onError(e->cleanup.run());
  try{
    emitter.send(SseEmitter.event().name("catalog-update").data("conectado"));
  }catch(IOException e){
    cleanup.run();
  }
  return emitter;
}

void publish(String id){
  for(var e:streams.getOrDefault(id,new CopyOnWriteArrayList<>()))
    try{
      e.send(SseEmitter.event().name("catalog-update").data("actualizado"));
    }catch(Exception ex){
      e.complete(); // <-- CULPRIT: Invocación insegura y sin try-catch
    }
}
```

### Mecánica del Fallo y Condición de Carrera:
1. **Desconexión del cliente:** Cuando un cliente cierra la conexión SSE (`readerA.close()`), el socket TCP subyacente se cierra o envía RST.
2. **Estado asíncrono en Tomcat 10.1:** El hilo del despachador de Tomcat procesa el cierre de socket y dispara `AsyncListener.onError()`. Conforme al estándar Servlet y la implementación de Tomcat (`AsyncContextImpl.check`), una vez que `onError()` ha retornado, un hilo de aplicación externo **no tiene permitido invocar operaciones sobre ese `AsyncContext`**, para prevenir condiciones de carrera.
3. **Llamada a `publish()` durante un POST:** Cuando el cliente envía a continuación una petición HTTP POST (`/api/bot/message` o `/api/reset`), `Api.message` ejecuta la lógica de negocio (`bot.process`) y luego invoca `publish(sessionId)` en la línea 30.
4. **Disparo de excepción en `e.send`:** `publish` itera sobre `streams.get(id)`. Como Tomcat procesa la limpieza de manera asíncrona mediante callbacks, el emisor desconectado aún reside en la lista en memoria. `e.send(...)` falla inmediatamente arrojando `IOException` ("Broken pipe" o "Socket closed").
5. **Invocación letal de `e.complete()`:** El bloque `catch (Exception ex)` de la línea 42 intenta "completar" el emisor llamando `e.complete()`.
   - `ResponseBodyEmitter.complete()` adquiere el bloqueo e invoca `handler.complete()`.
   - `DefaultSseEmitterHandler.complete()` intenta `ServerHttpResponse.flush()`, lo cual falla y ejecuta `deferredResult.setErrorResult(ioException)`.
   - `DeferredResult` intenta hacer dispatch en `StandardServletAsyncWebRequest`, pero Tomcat detecta que el `AsyncContext` ya falló previamente y arroja:
     `java.lang.IllegalStateException: A non-container (application) thread attempted to use the AsyncContext after an error had occurred and the call to AsyncListener.onError() had returned.`
     o bien `AsyncRequestNotUsableException: Response not usable after response errors.`
6. **Propagación descontrolada hacia la API REST:** En la línea 42 de `Api.java`, la llamada `e.complete()` **no está rodeada por un bloque try-catch**. La excepción de Tomcat (`AsyncRequestNotUsableException` / `IllegalStateException`) escapa libremente de `publish(sessionId)`.
7. **Conversión en HTTP 500:** La excepción no capturada aborta `Api.message(sessionId, ...)` antes de poder retornar la respuesta del bot. `@ExceptionHandler(Exception.class)` en `ApiErrors` captura el error, escribe en el log `ERROR ... ApiErrors : Fallo de solicitud` y devuelve al cliente HTTP 500: `{"message":"No pude completar el pedido. Intentá otra vez."}`.
8. **Intermitencia:** Si el hilo asíncrono de Tomcat ejecuta `cleanup.run()` milisegundos antes de que el POST llame a `publish()`, el emisor ya fue removido de la lista y el test pasa. Si el POST llega mientras el emisor roto sigue en la lista y Tomcat ya marcó el error en el `AsyncContext`, el POST falla con HTTP 500.

---

## 4. Propuesta Mínima de Corrección para Codex (Fase 1 / Especificación v2.1)

En estricta observancia del protocolo de `AGENTS.md`, Antigravity **no modifica código de producción en este turno** y somete a aprobación de Codex y Orca la siguiente solución arquitectónica mínima:

### 4.1 Principio de Aislamiento de Notificaciones (Fire-and-Forget / Fault-Tolerant)
El método `publish(String id)` tiene una responsabilidad puramente de difusión y notificación hacia clientes reactivos. **Bajo ninguna circunstancia un fallo de transporte en un cliente SSE debe abortar la transacción de negocio ni romper la respuesta REST de un endpoint transaccional (POST message / POST reset)**.

### 4.2 Propuesta de Implementación para `Api.java`
Modificar el método `publish` para que:
1. Remueva inmediatamente el emisor roto de la lista activa en memoria.
2. Si la lista queda vacía, libere la entrada en `streams`.
3. Envuelva el intento de cierre `e.complete()` en un bloque try-catch silenciado, asegurando que ninguna excepción asíncrona de Tomcat pueda escapar.

```java
// Propuesta para Api.java:
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
                // Previene que AsyncRequestNotUsableException / IllegalStateException
                // de Tomcat escapen hacia la petición HTTP transaccional del llamador.
            }
        }
    }
}
```

### 4.3 Propuesta de Prueba de Regresión para `StorefrontSseTest.java`
Incorporar una prueba dirigida de regresión que fuerce desconexiones abruptas del stream SSE mientras se ejecutan ráfagas concurrentes de mutaciones POST (`/api/bot/message` y `/api/reset`), asegurando:
1. Que el 100% de las solicitudes POST retornen HTTP 200 con sus respuestas de negocio válidas.
2. Que los emisores desconectados se descarten limpiamente sin dejar residuos en `streams`.
3. Que la reconexión posterior recupere el snapshot completo sin excepciones de servidor.

---

## 5. Resumen de Archivos y Estado para Orca / Codex

- **Archivos creados en esta fase de diagnóstico:**
  - `docs/orchestration/issue-6-sse-diagnosis.md` (este informe técnico).
  - `docs/orchestration/issue-6-corrective-evidence/builder/sse/reproduce_sse_race.py` (arnés de reproducción).
  - `docs/orchestration/issue-6-corrective-evidence/builder/sse/sse_repro_results.json` (resultados y trazas estructuradas).
  - `docs/orchestration/issue-6-corrective-evidence/builder/sse/sse_repro_server.log` (log crudo con trazas completas de Tomcat).
- **Archivos de producción modificados:** Ninguno (a la espera de la especificación técnica de Codex).
- **Estado de la Issue #6:**
  - V1-zoom200: Bloqueo ambiental levantado por **Aceptación manual explícita del usuario**.
  - Intermitencia SSE: Causa raíz identificada y demostrada empíricamente; lista para diseño correctivo de Codex.
