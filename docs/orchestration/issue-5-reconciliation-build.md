# Reporte de Construcción y Verificación — Issue #5: Conciliación Fase 2 (Construcción H1 Fallback v3 y Pruebas Deterministas)

**Fecha**: 13 de Noviembre de 2026  
**Rama**: `GonzaCarrillo/reconcile-5-voice`  
**Base auditada**: `06f86f7` (HEAD con auditoría previa OpenCode QA_APPROVED para H2/H3)  
**Diseño de referencia**: `docs/orchestration/issue-5-fallback-design-v3.md` (Codex v3 Aprobado para Despacho), `docs/orchestration/issue-5-reconciliation-qa-plan.md` e `docs/orchestration/issue-5-design.md` v1.0  
**Autor**: Antigravity (Software Builder / Implementer)  
**Estado**: Componente `IntentFallback` y suite determinista implementados; H2/H3 preservados y revalidados; `Api.java` y `BotEngine.java` no modificados en espera del Gate de Integración coordinado tras el cierre de SSE #6.

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
  - Checksum SHA-256: `b75e29c0e1ea8e47da9bad045b575478438ff20493340f14dd130acf106efe42`

---

## 2. Alcance Cerrado y Archivos Modificados

Se mantuvo rigurosamente el alcance cerrado autorizado para Antigravity para este despacho:
- **Producción**:
  - `src/main/java/com/norma/taller/IntentFallback.java` (NUEVO):
    - Componente `@Component` Spring con contrato:
      `public Optional<String> normalize(String original, String image, List<Catalog.Product> products)`
    - Constructor `@Autowired` con `@Value("${groq.api.key:}")`, `IntentParser` y `ObjectMapper`.
    - Constructor desacoplado para fixtures de pruebas con inyección de `RestClient`.
    - Configuración Groq: endpoint `https://api.groq.com/openai/v1`, modelo `llama-3.3-70b-versatile`, temperatura `0`, `response_format: {"type": "json_object"}`, timeouts `connectTimeout = 3s` y `readTimeout = 8s`, sin reintentos automáticos.
    - Sin clave, timeout, error HTTP (4xx/5xx) o JSON inválido -> degradación segura a `Optional.empty()`.
    - No registra secretos, claves de API ni mensajes del usuario en bitácoras.
    - Parseo estricto con Jackson: `STRICT_DUPLICATE_DETECTION` activado, verificación de 4 campos exactos (`operation`, `targetEvidence`, `priceEvidence`, `titleEvidence`), comprobación de tipo textual estricto sin coerción (`isTextual()`), y rechazo de tokens residuales trailing.
    - Anti-alucinación: verificación de que toda evidencia no vacía sea una subcadena literal del texto `original` (`original.contains(evidence)`).
    - Validación de destino: patrón explícito de pieza (`numero/pieza/#`), sin ambigüedades en el texto original, y existencia activa en `products` (`!product.sold()`).
    - Validación de precio: `parser.price(priceEvidence) > 0`.
    - Validación de título: longitud entre 1 y 100 caracteres.
    - Imagen: la URL proviene estrictamente del parámetro de entrada `image`, nunca del modelo LLM.
    - Reconstrucción canónica local y verificación estricta con `parser.interpret`. Solo se acepta si retorna `BotModel.Complete` con acción idéntica a los campos validados.
    - Inmunidad contra inyección de control: si `original` es un comando de control (`CONFIRM`, `CANCEL`, `UNDO`), se descarta de inmediato sin consultar al LLM.
  - `src/main/java/com/norma/taller/MediaService.java`:
    - Preservada la implementación aprobada en H2 (discriminación 400/422 vs 401/429/5xx, limpieza de `.processing`) y H3 (integración local y no bloqueante con `edge-tts`).
- **Pruebas**:
  - `src/test/java/com/norma/taller/IntentFallbackTest.java` (NUEVO):
    - 21 pruebas unitarias deterministas sin red real ni consumo de cuota Groq (usando mocks con `Answers.RETURNS_SELF` para fluent builder de `RestClient`).
    - Cobertura exhaustiva:
      1. Casos válidos de normalización: `CREATE`, `PRICE`, `IMAGE`, `SOLD`.
      2. Defensas anti-alucinación: modelo inventa título, precio o pieza no presentes en `original`; intento de `CREATE` sin imagen.
      3. Estructura JSON estricta: campos desconocidos, claves duplicadas, coerción de tipos (número en vez de string), contenido residual trailing.
      4. Consistencia con catálogo: pieza inexistente, pieza ya vendida, múltiples piezas ambiguas en el original.
      5. Inmunidad de controles: rechazo inmediato de "si", "no", "cancela", "deshacer", etc., sin invocar al LLM.
      6. Resiliencia de infraestructura: clave ausente/en blanco (sin llamadas HTTP), errores 401, 429, 500 y timeout de red.
  - `src/test/java/com/norma/taller/MediaServiceTest.java`:
    - 20 pruebas unitarias deterministas H2/H3 preservadas y verificadas verdes.
  - `src/test/java/com/norma/taller/ApiAudioTest.java`:
    - 7 pruebas de integración MockMvc H2 preservadas y verificadas verdes.
- **Documentación y diseño**:
  - `docs/orchestration/issue-5-fallback-design-v3.md`: Especificación técnica formal v3.0 aprobada por Codex.
  - `docs/orchestration/issue-5-reconciliation-build.md`: Este reporte.
  - `issue-5-reconciliation-evidence/builder/`: Reportes Surefire (XML y TXT para `IntentFallbackTest`, `MediaServiceTest` y `ApiAudioTest`), `jar-sha256.txt`, audio sintético MP3 y metadatos `ffprobe`.
- **Archivos estrictamente no modificados**:
  - `Api.java` y `BotEngine.java` no fueron tocados (aislamiento intencional para evitar colisiones con Issue #6).
  - `IntentParser.java`, `BotModel.java`, `Catalog.java`, `PhotoProcessor.java`, Dockerfile, POM, SQL, propiedades de configuración y frontend permanecen intactos.

---

## 3. Estado de los Requerimientos de la Issue #5

| Requerimiento | Estado | Detalle y Acreditación |
|---|---|---|
| **H-2 (STT Audio Ilegible vs Fallo)** | Aprobado / Verificado | Discriminación HTTP 400/422 vs 401/429/5xx, limpieza `.processing` garantizada en `finally`. 20 tests unitarios y 7 de integración verdes. |
| **H-3 (TTS Síntesis Real edge-tts)** | Aprobado / Verificado | Síntesis real con `edge-tts 7.3.1` (voz `es-AR-TomasNeural`), MP3 verificado con `ffprobe` (24kHz mono, 4.39s), degradación no bloqueante probada. |
| **H-1 (Fallback de Intenciones LLM)** | Construido y Verificado (Aislado) | Componente `IntentFallback` implementado bajo diseño Codex v3. 21 pruebas deterministas verdes. Listo para cableado en `Api.java` durante el siguiente gate de integración tras merge de `main`. |

---

## 4. Resumen de Verificación `mvn -B clean verify`

- **Comando**: `export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1 && export PATH=$JAVA_HOME/bin:$PATH && mvn -B clean verify`
- **Exit code**: `0` (BUILD SUCCESS)
- **Resultados de la suite completa**:
  - `IntentFallbackTest`: 21 tests, 0 failures, 0 errors, 0 skipped
  - `MediaServiceTest`: 20 tests, 0 failures, 0 errors, 0 skipped
  - `ApiAudioTest`: 7 tests, 0 failures, 0 errors, 0 skipped
  - Suite global del monolito: **230 tests**, 0 failures, 0 errors, 0 skipped
- **Evidencias persistidas en `issue-5-reconciliation-evidence/builder/`**:
  - `TEST-com.norma.taller.IntentFallbackTest.xml`
  - `com.norma.taller.IntentFallbackTest.txt`
  - `TEST-com.norma.taller.MediaServiceTest.xml`
  - `com.norma.taller.MediaServiceTest.txt`
  - `TEST-com.norma.taller.ApiAudioTest.xml`
  - `com.norma.taller.ApiAudioTest.txt`
  - `jar-sha256.txt` (b75e29c0e1ea8e47da9bad045b575478438ff20493340f14dd130acf106efe42)
  - `sample-voice.mp3`
  - `tts-probe.json`
