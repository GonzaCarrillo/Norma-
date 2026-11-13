# Reporte de Construcción y Verificación — Issue #5: Conciliación Fase 2 (Voz y Fallbacks)

**Fecha**: 13 de Noviembre de 2026  
**Rama**: `GonzaCarrillo/reconcile-5-voice`  
**Base**: `d13c51b835dd1a32eadf7043957e02b918f5c3e2`  
**Diseño de referencia**: `docs/orchestration/issue-5-reconciliation-qa-plan.md` y `docs/orchestration/issue-5-design.md` v1.0  
**Autor**: Antigravity (Software Builder / Implementer)  
**Estado**: Construcción completada y verificada al 100%.

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
  - Checksum SHA-256: `64c33beed1d45c8cde67d1be1e96e0a3cfe454f1afdaaf2c49f569fee1732c72`

---

## 2. Alcance Cerrado y Archivos Modificados

De conformidad estricta con el diseño aprobado y el protocolo de despacho:
- **Archivos de producción modificados**:
  - `src/main/java/com/norma/taller/MediaService.java`:
    - Incorporación de validaciones para STT (`transcribe`): rechazo de clave vacía (A1) y audio nulo o vacío (A2) con mensajes en español.
    - Manejo de excepciones de red/Groq (`RestClientException`) mapeadas a 400 (`IllegalArgumentException`).
    - Colaborador funcional privado `VoiceProcessRunner` para síntesis TTS desacoplada de la presencia física de `edge-tts` en el PATH.
    - Manejo estricto de límites en TTS (`speak`): rechazo inmediato de textos nulos, vacíos o mayores a 1000 caracteres sin spawn de procesos; terminación forzosa de procesos colgados (`p.destroyForcibly()`); eliminación determinista de archivos temporales ante cualquier fallo, timeout o salida con código distinto de cero.
    - Se preservó íntegro el constructor público con `@Value`, el campo `final Path root;` y la delegación de imágenes a `PhotoProcessor`.
- **Archivos de prueba modificados**:
  - `src/test/java/com/norma/taller/MediaServiceTest.java`:
    - 14 pruebas unitarias independientes del entorno del host.
    - Cobertura de A1 (clave vacía), A2 (audio nulo/vacío), A3 (error simulado de Groq, respuesta vacía), éxito de STT con verificación de eliminación de temporal `.webm`.
    - Cobertura de A4 (texto null, vacío, >1000 caracteres sin invocar proceso), A5 (binario ausente sin bloqueo), salida de proceso != 0, timeout con destrucción de proceso, archivo de 0 bytes y síntesis exitosa con archivo MP3 persistido.
  - `src/test/java/com/norma/taller/ApiAudioTest.java`:
    - 6 pruebas de integración MockMvc con `@MockitoSpyBean`.
    - Cobertura de A6 (audio sin clave Groq -> 400 y estado/catálogo preservados), solo texto (200), texto + audio simultáneos (A7 -> 400 "uno por vez"), foto + texto (200), A8 (audio válido con STT simulado -> pasa al motor y genera `replyAudioUrl` y transición de estado), y fallo de STT simulado con preservación de catálogo y sesión.
- **Documentación y evidencias**:
  - `docs/orchestration/issue-5-reconciliation-build.md`
  - `issue-5-reconciliation-evidence/builder/`
- **Archivos prohibidos no tocados**:
  - `Api.java`, `PhotoProcessor.java`, `IntentParser.java`, `BotEngine.java`, `BotModel.java`, `Catalog.java`, `CatalogDatabaseInitializer.java`, frontend/estáticos (`index.html`, `app.js`, `style.css`), `pom.xml`, `application.properties`, esquemas SQL ni migraciones.

---

## 3. Matriz de Aceptación A1–A8 y Evidencia de Cumplimiento

| ID | Criterio / Comportamiento | Implementación y Verificación | Estado |
|---|---|---|---|
| **A1** | `transcribe` con `groq.api.key=""` lanza `IllegalArgumentException` ("La transcripción no está configurada...") | `MediaServiceTest.transcribeConClaveVaciaLanzaIllegalArgumentExceptionMencionandoTranscripcion` | **PASS** |
| **A2** | `transcribe` con audio nulo/vacío lanza `IllegalArgumentException` ("No pude escuchar el audio...") | `MediaServiceTest.transcribeConAudioNuloLanzaIllegalArgumentExceptionMencionandoNoPudeEscuchar`, `transcribeConAudioVacio...` | **PASS** |
| **A3** | `transcribe` con fallo Groq simulado lanza `IllegalArgumentException` ("No pude transcribir ahora...") | `MediaServiceTest.transcribeConGroqFallandoLanzaIllegalArgumentExceptionMencionandoNoPudeTranscribir` | **PASS** |
| **A4** | `speak` con texto null, vacío o >1000 caracteres devuelve `null` sin invocar proceso | `MediaServiceTest.speakNuloDevuelveNullSinLlamarProceso`, `speakVacio...`, `speakMasDeMilCaracteres...` | **PASS** |
| **A5** | `speak` con binario ausente devuelve `null` sin bloqueo | `MediaServiceTest.speakConBinarioAusenteDevuelveNullSinBloqueo` | **PASS** |
| **A6** | `POST /api/bot/message` con audio y sin clave devuelve HTTP 400; estado y catálogo preservados | `ApiAudioTest.audioSinClaveDevuelve400TranscripcionYSesionPreservada` | **PASS** |
| **A7** | `POST /api/bot/message` texto + audio simultáneos devuelve HTTP 400 "uno por vez" | `ApiAudioTest.textoYAudioSimultaneosDevuelve400UnoPorVez` | **PASS** |
| **A8** | `POST /api/bot/message` con audio válido pasa al motor; `Reply` incluye `replyAudioUrl` | `ApiAudioTest.audioValidoConSttSimuladoPasaAlMotorYDevuelveReply` | **PASS** |
| **R1** | Regresión completa del monolito (suites de #1 a #6) | `mvn -B clean verify`: 202 pruebas ejecutadas, 0 fallos, 0 errores, 0 omitidos | **PASS** |

---

## 4. Reporte de Brecha Arquitectónica: Intent Fallback

De acuerdo con las directivas del plan y protocolo:
- La issue original #5 en GitHub hace mención a "fallback de intenciones". Sin embargo, en el monolito el componente de fallback por LLM (`IntentFallback`) se encuentra archivado en `docs/orchestration/archive/IntentFallback.java.txt` y no está inyectado ni cableado en `Api.java` ni `BotEngine.java`.
- Toda la interpretación semántica y gramatical recae en el analizador determinista `IntentParser`, que no depende de claves ni de red.
- **Declaración explícita**: No se activó ni inventó satisfacción de `IntentFallback` para evitar desalineaciones arquitectónicas o dependencias frágiles no contempladas en el diseño aprobado. Se reporta formalmente como limitación de diseño conocida para decisión de Orca y Codex.

---

## 5. Resumen de Verificación `mvn -B clean verify`

- **Comando**: `export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1 && export PATH=$JAVA_HOME/bin:$PATH && mvn -B clean verify`
- **Exit code**: `0`
- **Resultados de pruebas**:
  - `ApiAudioTest`: 6 tests, 0 failures, 0 errors, 0 skipped
  - `MediaServiceTest`: 14 tests, 0 failures, 0 errors, 0 skipped
  - Total global: **202 tests**, 0 failures, 0 errors, 0 skipped
- **Evidencias persistidas**:
  - `issue-5-reconciliation-evidence/builder/TEST-com.norma.taller.ApiAudioTest.xml`
  - `issue-5-reconciliation-evidence/builder/TEST-com.norma.taller.MediaServiceTest.xml`
  - `issue-5-reconciliation-evidence/builder/com.norma.taller.ApiAudioTest.txt`
  - `issue-5-reconciliation-evidence/builder/com.norma.taller.MediaServiceTest.txt`
  - `issue-5-reconciliation-evidence/builder/jar-sha256.txt`
