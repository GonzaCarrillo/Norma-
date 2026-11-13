# Issue #6 — Especificación Técnica Codex v2.2: Serialización Atómica por Clave del Ciclo de Vida SSE

**Autor:** Codex (Software Architect)  
**Coordinador / Autorización:** Orca (`task_5b89cd42690b` / `ctx_ebcd89119efe`)  
**Base:** `265ab8e60e0a51c4a5d898a129d20c5d794cb5c8`  
**Referencia de hallazgo:** Hallazgo P2 en reauditoría QA de Copilot (`docs/orchestration/issue-6-corrective-qa.md`)  
**Estado:** Aprobado para implementación inmediata por Antigravity (Fase 2)  

---

## 1. Contexto y Diagnóstico del Hallazgo P2

En la reauditoría de la versión v2.1 (commit `e79e3b1`), GitHub Copilot identificó un hallazgo de severidad P2 relativo a una condición de carrera entre el retiro del último emisor y el registro concurrente de una nueva conexión SSE para la misma sesión:

> *"P2 — Carrera en retiro de lista con suscripción simultánea. En el callback cleanup y en publish, el código retira el emitter, comprueba list.isEmpty() y luego ejecuta streams.remove(sessionId, list). Entre la comprobación y el remove, un stream() concurrente puede obtener esa misma lista mediante computeIfAbsent y añadir un nuevo emitter; el retiro condicional todavía elimina del mapa la lista ahora no vacía. La nueva suscripción queda huérfana y no recibirá publicaciones posteriores."*

### Análisis de la Intercalación en v2.1:
1. En v2.1, el registro realizaba:
   ```java
   var list = streams.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
   if (list.size() >= 5) throw ...;
   list.add(emitter);
   ```
2. Y el retiro en `cleanup` y `publish`:
   ```java
   list.remove(emitter);
   if (list.isEmpty()) streams.remove(sessionId, list);
   ```
3. Aunque `streams.remove(sessionId, list)` valida la identidad del valor (`list`), si el hilo B obtuvo esa misma instancia `list` vía `computeIfAbsent` y añadió su nuevo emisor justo antes de que el hilo A ejecute `remove`, el mapa todavía asocia `sessionId` a la instancia `list`. Por tanto, `streams.remove(sessionId, list)` remueve la lista del mapa a pesar de contener la nueva conexión activa del hilo B.
4. Adicionalmente, el chequeo `if (list.size() >= 5)` en `stream()` ocurría fuera de la exclusión mutua de `computeIfAbsent`, permitiendo que múltiples registros concurrentes sobrepasaran transitoriamente el límite de 5 ventanas.

---

## 2. Contrato de la Corrección v2.2

### 2.1 Serialización Atómica por Clave en `ConcurrentHashMap`
Todas las mutaciones de membresía del mapa `streams` para una sesión dada deben ejecutarse dentro de operaciones atómicas sobre el nodo/bucket de la clave `sessionId`:

1. **Registro Atómico (`ConcurrentHashMap.compute`):**
   ```java
   streams.compute(sessionId, (k, currentList) -> {
       var list = currentList != null ? currentList : new CopyOnWriteArrayList<SseEmitter>();
       if (list.size() >= 5) {
           throw new IllegalArgumentException("Ya hay varias ventanas abiertas para esta sesión.");
       }
       list.add(emitter);
       return list;
   });
   ```
   - Si `currentList` es nulo, se crea una nueva `CopyOnWriteArrayList`.
   - La comprobación `list.size() >= 5` se evalúa dentro del lock atómico de la clave. Si supera el límite, se lanza `IllegalArgumentException` sin mutar el mapa.
   - Se añade el nuevo emisor y se devuelve la lista para fijar o mantener el mapeo en `streams`.

2. **Retiro Idempotente Atómico (`ConcurrentHashMap.computeIfPresent`):**
   ```java
   void removeEmitter(String sessionId, SseEmitter emitter) {
       streams.computeIfPresent(sessionId, (k, list) -> {
           list.remove(emitter);
           return list.isEmpty() ? null : list;
       });
   }
   ```
   - Solo opera si la clave existe en el mapa.
   - Retira el emisor específico de la lista actual.
   - Devuelve `null` si y solo si la lista quedó vacía, lo que instruye atómicamente a `ConcurrentHashMap` a remover la clave.
   - Si un hilo concurrente añadió un nuevo emisor a la lista mientras otro hilo retiraba el anterior, `list.isEmpty()` evaluará a `false` y devolverá `list`, preservando intacta la suscripción activa.
   - Queda estrictamente prohibido comprobar `list.isEmpty()` fuera de `compute` o utilizar `streams.remove(key, list)`.

3. **Helper Unificado e Idempotente:**
   - Los callbacks `onCompletion`, `onTimeout`, `onError`, el fallo de envío inicial en `stream()` y el fallo de notificación en `publish()` deben invocar exclusivamente `removeEmitter(sessionId, emitter)`.

4. **Operaciones Fuera del Lock de la Clave:**
   - Ninguna operación bloqueante, llamada a catálogo (`catalog.ensure`), I/O de socket (`emitter.send`) o terminación (`emitter.complete`) debe ejecutarse dentro de la función de reasignación de `compute` o `computeIfPresent`.
   - En `publish(String id)`:
     ```java
     var list = streams.get(id);
     if (list == null) return;
     for (var e : list) {
         try {
             e.send(SseEmitter.event().name("catalog-update").data("actualizado"));
         } catch (Exception ex) {
             removeEmitter(id, e);
             try { e.complete(); } catch (Exception ignored) {}
         }
     }
     ```
     La iteración se realiza sobre la lista COW fuera de locks de mapa. La terminación `e.complete()` es best-effort y protegida con `try-catch` para no propagar excepciones asíncronas de Tomcat.

---

## 3. Alcance Cerrado Autorizado

- **Archivos de producción modificables:**
  - `src/main/java/com/norma/taller/Api.java` (únicamente métodos `stream`, `publish` y helper `removeEmitter`).
- **Archivos de prueba y verificación:**
  - `src/test/java/com/norma/taller/ApiPublishTest.java` (añadir regresión concurrente determinista con latches/barreras y límite de 5; eliminar lambda sintética de control positivo).
  - `src/test/java/com/norma/taller/StorefrontSseTest.java` (preservar intactas aserciones HTTP reales).
- **Archivos de documentación y evidencia:**
  - `docs/orchestration/issue-6-sse-design-v2.2.md` (este documento).
  - `docs/orchestration/issue-6-corrective-build.md` (reporte de construcción actualizado).
  - `docs/orchestration/issue-6-corrective-evidence/builder/sse-v2.2/` (evidencia limpia de build, test Surefire y control positivo de falla de baseline).
- **Prohibiciones estrictas:**
  - No modificar lógica de catálogo, persistencia, contratos REST, esquema SQLite v2 ni estáticos frontend.
  - No tocar código de audio, voz o `MediaConfig` (coexistencia con issue #5).
  - No realizar merge a `main`, push remoto ni cierre de issues.

---

## 4. Estrategia de Pruebas y Validación

1. **Regresión Concurrente Determinista:**
   - Prueba unitaria concurrente con `CountDownLatch` y `CyclicBarrier` que ejecute simultáneamente el registro de una nueva suscripción y el cleanup del último emisor existente en la misma sesión.
   - Demostración de que la nueva suscripción permanece en el mapa y recibe exitosamente los eventos de `publish()`. Prohibido el uso de `Thread.sleep` como mecanismo de sincronización.
2. **Límite de 5 Bajo Concurrencia:**
   - Lanzamiento concurrente de 10 peticiones simultáneas de registro para una misma sesión sincronizadas con `CyclicBarrier`.
   - Aserción de que exactamente 5 obtienen emisor y exactamente 5 reciben `IllegalArgumentException`, manteniendo la lista en tamaño 5.
3. **Control Positivo del Baseline Real:**
   - Ejecución verificable en directorio temporal `target/` con el código baseline previo que demuestre el fallo real (excepción no protegida y/o descarte de suscripción por carrera), contrastado contra el paso exitoso en la versión v2.2.
4. **Gobernanza:**
   - La aceptación manual del Zoom 200% permanece vigente y consolidada por decisión del usuario.
   - Se registra el relevo de la fase de auditoría QA a OpenCode / Nemotron conforme a la instrucción del usuario.
