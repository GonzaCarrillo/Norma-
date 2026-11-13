# Reporte de Construcción y Verificación — Issue #5: Conciliación Fase 2 (Integración H1 en Api v3.1 y Verificación Integral)

**Fecha**: 13 de Noviembre de 2026  
**Rama**: `GonzaCarrillo/reconcile-5-voice`  
**Base unificada**: Merge limpio de `origin/main` (`8cdacbe`, Issue #6 cerrada) en commit `55ba012` sobre la base previa de H1 (`c920ed3`) y H2/H3 (`06f86f7`)  
**Diseño de referencia**: `docs/orchestration/issue-5-fallback-design-v3.md` (Codex v3.1 Aprobado para Despacho — Gate de Integración en Api), `docs/orchestration/issue-5-reconciliation-qa-plan.md` e `docs/orchestration/issue-5-design.md` v1.0  
**Autor**: Antigravity (Software Builder / Implementer)  
**Estado**: Integración de `IntentFallback` en `Api.java` completada y verificada; suite completa del repositorio verde (243/243 tests). Listo para auditoría de Fase 3 por OpenCode / Nemotron.

---

## 1. Entorno de Construcción y Ejecución

- **Sistema Operativo**: Linux x86_64 (Kernel 6.14.0-37-generic)
- **Java Runtime / SDK**: Microsoft OpenJDK 25.0.4.1 (build 25.0.4.1+1-LTS)
  - `JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1`
  - `PATH=$JAVA_HOME/bin:$PATH`
  - Bytecode: Versión 69 (Java 25 estándar, sin preview flags)
- **Herramienta de construcción**: Apache Maven 3.8.7
- **Artefacto principal generado**:
  - Archivo: `target/taller-1.0.0.jar`
  - Formato: Spring Boot Executable JAR (Spring Boot 3.5.16)
  - Checksum SHA-256: `63064226b360fa03a228e2f6ae5aff9f1e986eb431498b297367b0f7f9a16280`

---

## 2. Alcance Cerrado y Archivos Modificados

Se respetaron rigurosamente los límites de alcance autorizados para este gate de integración:
- **Producción**:
  - `src/main/java/com/norma/taller/Api.java`:
    - Inyección explícita en constructor Spring `@Autowired Api(Catalog, BotEngine, MediaService, IntentParser, IntentFallback)`.
    - Constructor adaptador `Api(Catalog, BotEngine, MediaService)` delegando a 5 argumentos con `new IntentParser()` y `fallback = null` para compatibilidad en pruebas unitarias aisladas sin activar red.
    - En `POST /api/bot/message`: orquestación de normalización previa a `bot.process`:
      1. `catalog.ensure(sessionId)`
      2. Carga de `sess = catalog.session(sessionId)` y `products = catalog.all(sessionId)`.
      3. Condición estricta de invocación: `fallback != null && "IDLE".equals(sess.state()) && sess.pending() == null && parser.extractControl(text, photo) instanceof BotModel.None && parser.interpret(text, photo, products, null) instanceof BotModel.Unknown`.
      4. Si se cumple, ejecuta `fallback.normalize(text, photo, products)`. Si retorna comando canónico, sustituye `text` antes de `bot.process(...)`. Si retorna vacío, conserva `text` intacto.
    - Ejecución estrictamente **fuera** de `@Transactional`: sin retención de locks de SQLite durante la llamada HTTP al proveedor externo.
    - Preservados intactos los contratos de endpoints, el manejo de errores, la creación de `Reply` y la publicación SSE atómica (`publish(sessionId)`).
  - `src/main/java/com/norma/taller/IntentFallback.java`:
    - Implementación desacoplada y robusta de normalización LLM (`llama-3.3-70b-versatile`, temperatura 0, JSON estricto de 4 campos, verificación literal de evidencias, sin coerción de tipos, inmunidad de controles y validación con `parser.interpret`).
  - `src/main/java/com/norma/taller/MediaService.java`:
    - Mantiene intacta la lógica aprobada de STT (discriminación 400/422 vs 401/429/5xx, limpieza de `.processing`) y TTS (síntesis local con `edge-tts`, degradación no bloqueante).
- **Pruebas**:
  - `src/test/java/com/norma/taller/IntentFallbackApiTest.java` (NUEVO):
    - 6 pruebas de integración HTTP MockMvc con fallback espiado (`@MockitoSpyBean`):
      1. Flujo completo: texto desconocido + foto activa fallback, pasa a `AWAITING_CONFIRMATION` sin mutar catálogo; confirmación `"si"` posterior muta el catálogo a `IDLE` sin volver a llamar al LLM.
      2. Acreditación de aislamiento transaccional: verificación explícita de `TransactionSynchronizationManager.isActualTransactionActive() == false` dentro de la llamada a `fallback.normalize(...)`.
      3. Comandos deterministas directos nunca invocan a fallback.
      4. Señales de control (`si`, `no`, `deshacer`) nunca invocan a fallback.
      5. Sesiones en estado no-`IDLE` (ej: `AWAITING_DETAILS`) nunca invocan a fallback.
      6. Degradación segura ante fallo o `Optional.empty()` de fallback: conserva flujo nativo del bot sin HTTP 500.
      7. Aislamiento entre sesiones concurrentes A y B.
  - `src/test/java/com/norma/taller/IntentFallbackTest.java`:
    - 21 pruebas unitarias deterministas sin red para el componente de fallback.
  - `src/test/java/com/norma/taller/ApiPublishTest.java`:
    - 7 pruebas unitarias de concurrencia y fallo de emisores SSE verificadas verdes sin modificación de aserciones.
  - `src/test/java/com/norma/taller/MediaServiceTest.java`:
    - 20 pruebas unitarias deterministas H2/H3 verificadas verdes.
  - `src/test/java/com/norma/taller/ApiAudioTest.java`:
    - 7 pruebas de integración MockMvc H2 verificadas verdes.
- **Documentación y diseño**:
  - `docs/orchestration/issue-5-fallback-design-v3.md`: Especificación técnica formal v3.1 aprobada.
  - `docs/orchestration/issue-5-reconciliation-build.md`: Este reporte.
  - `issue-5-reconciliation-evidence/builder/`: Reportes Surefire actualizados (`ApiAudioTest`, `ApiPublishTest`, `IntentFallbackApiTest`, `IntentFallbackTest`, `MediaServiceTest`), `jar-sha256.txt`, audio MP3 y sonda `ffprobe`.
- **Archivos estrictamente no modificados**:
  - `BotEngine.java`, `IntentParser.java`, `BotModel.java`, `Catalog.java`, `PhotoProcessor.java`, Dockerfile, POM, SQL, propiedades de configuración y frontend permanecen intactos.

---

## 3. Estado de los Requerimientos de la Issue #5

| Requerimiento | Estado | Detalle y Acreditación |
|---|---|---|
| **H-2 (STT Audio Ilegible vs Fallo)** | Aprobado / Verificado | Discriminación HTTP 400/422 vs 401/429/5xx, limpieza `.processing` garantizada en `finally`. 20 tests unitarios y 7 de integración verdes. |
| **H-3 (TTS Síntesis Real edge-tts)** | Aprobado / Verificado | Síntesis real con `edge-tts 7.3.1` (voz `es-AR-TomasNeural`), MP3 verificado con `ffprobe` (24kHz mono, 4.39s), degradación no bloqueante probada. |
| **H-1 (Fallback de Intenciones LLM)** | Construido e Integrado en Api | Componente `IntentFallback` conectado en `Api.message(...)` bajo diseño Codex v3.1. 21 pruebas unitarias y 6 pruebas de integración MockMvc verdes. Fuera de `@Transactional`. |

---

## 4. Resumen de Verificación `mvn -B clean verify`

- **Comando**: `export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1 && export PATH=$JAVA_HOME/bin:$PATH && mvn -B clean verify`
- **Exit code**: `0` (BUILD SUCCESS)
- **Resultados de la suite completa**:
  - `IntentFallbackApiTest`: 6 tests, 0 failures, 0 errors, 0 skipped
  - `IntentFallbackTest`: 21 tests, 0 failures, 0 errors, 0 skipped
  - `ApiAudioTest`: 7 tests, 0 failures, 0 errors, 0 skipped
  - `MediaServiceTest`: 20 tests, 0 failures, 0 errors, 0 skipped
  - `ApiPublishTest`: 7 tests, 0 failures, 0 errors, 0 skipped
  - Suite global del monolito: **243 tests**, 0 failures, 0 errors, 0 skipped
- **Evidencias persistidas en `issue-5-reconciliation-evidence/builder/`**:
  - `TEST-com.norma.taller.IntentFallbackApiTest.xml`
  - `com.norma.taller.IntentFallbackApiTest.txt`
  - `TEST-com.norma.taller.IntentFallbackTest.xml`
  - `com.norma.taller.IntentFallbackTest.txt`
  - `TEST-com.norma.taller.ApiPublishTest.xml`
  - `com.norma.taller.ApiPublishTest.txt`
  - `TEST-com.norma.taller.ApiAudioTest.xml`
  - `com.norma.taller.ApiAudioTest.txt`
  - `TEST-com.norma.taller.MediaServiceTest.xml`
  - `com.norma.taller.MediaServiceTest.txt`
  - `jar-sha256.txt` (63064226b360fa03a228e2f6ae5aff9f1e986eb431498b297367b0f7f9a16280)
  - `sample-voice.mp3`
  - `tts-probe.json`
