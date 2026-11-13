# Reporte de Construcción y Verificación — Issue #5: Conciliación Fase 2 (Corrección H2 / H3)

**Fecha**: 13 de Noviembre de 2026  
**Rama**: `GonzaCarrillo/reconcile-5-voice`  
**Base auditada**: `bc69036` (HEAD con auditoría Copilot `5ccb0d2`)  
**Diseño de referencia**: `docs/orchestration/issue-5-reconciliation-qa-plan.md` y `docs/orchestration/issue-5-design.md` v1.0  
**Informe de Auditoría atendido**: `docs/orchestration/issue-5-reconciliation-qa.md` (Dictamen `QA_REJECTED` de Copilot)  
**Autor**: Antigravity (Software Builder / Implementer)  
**Estado**: Hallazgos H2 y H3 corregidos y acreditados con evidencia real; H1 permanece abierto a nivel de diseño Codex.

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
  - Checksum SHA-256: `e22ed0e754f800f92a12a69497c46b78b9126b0034cee48298be26496e1084eb`

---

## 2. Alcance Cerrado y Archivos Modificados

Se mantuvo rigurosamente el alcance cerrado autorizado para Antigravity:
- **Producción**:
  - `src/main/java/com/norma/taller/MediaService.java`:
    - Discriminación precisa de códigos de estado HTTP en `RestClientResponseException`:
      - HTTP 400 y HTTP 422 (audio ilegible/corrupto rechazado por transcriptor) devuelven `IllegalArgumentException("No pude escuchar el audio. Probá otra vez o escribime.")`.
      - HTTP 401, 403, 429 y 5xx/red/timeout devuelven `IllegalArgumentException("No pude transcribir ahora. Probá otra vez o escribime.")` sin exponer al proveedor externo. No se clasifica todo 4xx como audio corrupto.
    - Los archivos temporales `.webm` se alojan bajo `root.resolve(".processing")` y se limpian en `finally { Files.deleteIfExists(temp); }`.
    - `VoiceProcessRunner` verifica ejecutabilidad de herramienta local aislada bajo `target/tts-env/bin/edge-tts` o recurre al binario en `PATH`, conservando degradación no bloqueante si está ausente.
    - Se conservan idénticos el constructor `@Autowired` con `@Value`, el campo `final Path root;` y la delegación de imagen a `PhotoProcessor`.
- **Pruebas**:
  - `src/test/java/com/norma/taller/MediaServiceTest.java`:
    - 20 pruebas unitarias deterministas.
    - Cobertura exhaustiva de H2: pruebas con bytes corruptos no vacíos ante respuestas 400 y 422; pruebas ante 401, 429, 500 y timeout de red; prueba de éxito STT; y aserción explícita en cada una de ellas de que el directorio `.processing` queda sin archivos residuales (`assertProcessingDirectoryEmpty()`).
    - Cobertura de H3: prueba determinista con runner simulado, casos límite (nulo, vacío, >1000 caracteres, binario ausente, salida != 0, timeout con `destroyForcibly`, archivo 0 bytes) y prueba de acreditación con herramienta real si está disponible.
  - `src/test/java/com/norma/taller/ApiAudioTest.java`:
    - 7 pruebas de integración MockMvc con `@MockitoSpyBean`.
    - Pruebas ante audio ilegible (400 "No pude escuchar el audio") y fallo de transcripción (401/429 "No pude transcribir ahora"), verificando en ambos casos la preservación del estado de sesión (`IDLE`) y la integridad del catálogo (8 productos de la semilla).
- **Evidencias y reportes**:
  - `docs/orchestration/issue-5-reconciliation-build.md`
  - `issue-5-reconciliation-evidence/builder/` (Surefire XML/TXT, JAR SHA-256, audio real MP3 y reporte `ffprobe` en JSON).
- **Archivos no modificados**:
  - Se preservaron intactos `Api.java`, `BotEngine.java`, `BotModel.java`, `IntentParser.java`, `Catalog.java`, `PhotoProcessor.java`, Docker, POM, SQL, propiedades de configuración y assets de frontend.
  - Se preservaron intactos los documentos preexistentes `docs/orchestration/issue-5-qa.md` y `docs/orchestration/issue-5-approval.md` del commit `d727ae0`.

---

## 3. Resolución de Hallazgos

### H-2: Audio Ilegible vs. Fallo de Transcripción y Eliminación de Temporales
- **Causa original reportada por Copilot**: La captura genérica de `RestClientException` clasificaba cualquier fallo HTTP de Groq (incluso un 400 por payload corrupto) como "No pude transcribir ahora", y la suite no probaba respuestas 4xx del proveedor ni asertaba la eliminación del temporal de audio.
- **Corrección**:
  - `MediaService.transcribe`: Captura primero `RestClientResponseException`. Si el código de estado es `400` (Bad Request) o `422` (Unprocessable Entity), lanza `"No pude escuchar el audio. Probá otra vez o escribime."`. Para otros códigos (401 Unauthorized, 403 Forbidden, 429 Too Many Requests, 5xx Server Error) o excepciones de I/O/timeout (`RestClientException`), lanza `"No pude transcribir ahora. Probá otra vez o escribime."`.
  - Se garantiza que el temporal `.webm` se cree bajo `root.resolve(".processing")` y se elimine en el bloque `finally`.
  - Se añadieron pruebas unitarias en `MediaServiceTest` con payloads corruptos no vacíos ante 400, 422, 401, 429, 500 y timeout, verificando la eliminación del temporal mediante `assertProcessingDirectoryEmpty()`.
  - Se añadieron pruebas en `ApiAudioTest` verificando la respuesta HTTP 400 con los mensajes correspondientes y la preservación de sesión y catálogo.

### H-3: Acreditación de Síntesis Real TTS y Reproducibilidad
- **Causa original reportada por Copilot**: `edge-tts` no estaba presente en el PATH y la prueba unitaria utilizaba un archivo fabricado con bytes de marcador ID3, sin audio real reproducible ni metadatos verificables.
- **Corrección y Evidencia**:
  - Se instaló la herramienta `edge-tts 7.3.1` en un entorno aislado bajo `target/tts-env` sin afectar dependencias del sistema ni POM.
  - Se ejecutó síntesis real con la voz argentina `es-AR-TomasNeural`:
    `edge-tts --voice es-AR-TomasNeural --text "El catálogo de El Taller de Norma fue actualizado exitosamente." --write-media issue-5-reconciliation-evidence/builder/sample-voice.mp3`
  - Se decodificó y analizó con `/usr/bin/ffprobe` (registrado en `issue-5-reconciliation-evidence/builder/tts-probe.json`):
    - Formato contenedor: `mp3` (MPEG ADTS, layer III, v2)
    - Codec: `mp3`
    - Duración: `4.392000` segundos
    - Bitrate: `48000` bps (48 kb/s)
    - Sample rate: `24000` Hz
    - Canales: 1 (mono)
    - Tamaño de archivo: `26352` bytes
    - SHA-256 de `sample-voice.mp3`: `aa801018b941a92df9152b2e53c9ee77c5d76e99f2a41bc6719fcc77aa58b941`
  - `MediaServiceTest` incluye la verificación `speakExitosoConProveedorRealGeneraMp3ReproducibleSiDisponible` que, de contar con el binario local o en PATH, valida la generación de audio real reproducible con cabeceras MP3 válidas (`ID3` o sync frame `0xFF 0xE0`).

### H-1: Requisito de Fallback de Intenciones (Declaración de Estado)
- Conforme a la instrucción del despacho: "H1 fallback sigue en diseño Codex y no se autoriza activarlo todavía."
- `IntentFallback` permanece archivado en `docs/orchestration/archive/IntentFallback.java.txt` y no ha sido inyectado en `BotEngine` ni en `Api`. Toda la interpretación de comandos se resuelve a través del analizador gramatical determinista `IntentParser`.
- Este requerimiento permanece formalmente **abierto** a la espera de la especificación arquitectónica correspondiente por parte de Codex.
- Se advierte expresamente que el commit `d727ae0` no constituye una aprobación válida de Fase 4 de Codex.

---

## 4. Resumen de Verificación `mvn -B clean verify`

- **Comando**: `export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1 && export PATH=$JAVA_HOME/bin:$PATH && mvn -B clean verify`
- **Exit code**: `0` (BUILD SUCCESS)
- **Resultados de pruebas**:
  - `ApiAudioTest`: 7 tests, 0 failures, 0 errors, 0 skipped
  - `MediaServiceTest`: 20 tests, 0 failures, 0 errors, 0 skipped
  - Total global: **209 tests**, 0 failures, 0 errors, 0 skipped
- **Evidencias persistidas**:
  - `issue-5-reconciliation-evidence/builder/TEST-com.norma.taller.ApiAudioTest.xml`
  - `issue-5-reconciliation-evidence/builder/TEST-com.norma.taller.MediaServiceTest.xml`
  - `issue-5-reconciliation-evidence/builder/com.norma.taller.ApiAudioTest.txt`
  - `issue-5-reconciliation-evidence/builder/com.norma.taller.MediaServiceTest.txt`
  - `issue-5-reconciliation-evidence/builder/sample-voice.mp3`
  - `issue-5-reconciliation-evidence/builder/tts-probe.json`
  - `issue-5-reconciliation-evidence/builder/jar-sha256.txt`
