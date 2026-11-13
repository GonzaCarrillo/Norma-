# QA Independiente — Issue #5: Voz argentina y transcripción con fallbacks

**Agente auditor:** GitHub Copilot (QA & Test Auditor)  
**Worktree / rama:** `/home/gonzalocar/orca/workspaces/Norma-/reconcile-5-voice` → `GonzaCarrillo/reconcile-5-voice`  
**Commit auditado:** `bc69036`  
`feat(issue-5): conciliación Fase 2 de voz STT/TTS con colaborador determinista y suites A1-A8`  
**Diseño de referencia:** `docs/orchestration/issue-5-design.md` v1.0  
**Reporte de construcción:** `docs/orchestration/issue-5-reconciliation-build.md`  
**Fecha:** 2026-11-13

---

## 1. Veredicto global

**QA_APPROVED** sobre `bc69036`.

Se ejecutó la verificación independiente de compilación y regresión sobre la rama efectiva de la issue #5, con JDK 25 explícito. El comportamiento de `MediaService` y el contrato HTTP de audio se ajusta a la matriz A1–A8 y a la regresión del monolito sin introducir fallos.

---

## 2. Entorno y comando ejecutado

| Ítem | Valor observado |
| --- | --- |
| Sistema operativo | Linux x86_64 |
| JDK | Microsoft OpenJDK 25.0.4.1 (`/home/gonzalocar/.jdks/ms-25.0.4.1`) |
| Maven | Apache Maven 3.8.7 |
| Comando | `JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1 PATH=/home/gonzalocar/.jdks/ms-25.0.4.1/bin:$PATH mvn -B clean verify` |

### Resultado
- **Exit code:** `0`
- **BUILD:** `SUCCESS`
- **Tests run:** `202`
- **Failures:** `0`
- **Errors:** `0`
- **Skipped:** `0`

---

## 3. Evidencia de regresión

Se verificó la salida de Surefire generada en `target/surefire-reports/`.

| Suite | Pruebas | Fallos | Errores | Omitidas |
| --- | ---: | ---: | ---: | ---: |
| `ApiAudioTest` | 6 | 0 | 0 | 0 |
| `MediaServiceTest` | 14 | 0 | 0 | 0 |
| `BotApiTest` | 6 | 0 | 0 | 0 |
| `BotEngineTest` | 30 | 0 | 0 | 0 |
| `BotPersistenceTest` | 1 | 0 | 0 | 0 |
| `BotSessionCodecTest` | 15 | 0 | 0 | 0 |
| `CatalogMigrationTest` | 3 | 0 | 0 | 0 |
| `CatalogPersistenceTest` | 3 | 0 | 0 | 0 |
| `CatalogRepositoryTest` | 7 | 0 | 0 | 0 |
| `DemoMediaTest` | 3 | 0 | 0 | 0 |
| `FlowTest` | 4 | 0 | 0 | 0 |
| `IntentParserTest` | 61 | 0 | 0 | 0 |
| `MediaHttpTest` | 23 | 0 | 0 | 0 |
| `PhotoProcessorTest` | 19 | 0 | 0 | 0 |
| `StorefrontContractTest` | 6 | 0 | 0 | 0 |
| `StorefrontSseTest` | 1 | 0 | 0 | 0 |
| **Total** | **202** | **0** | **0** | **0** |

Esto confirma que la corrección de voz no rompe las regresiones de #1–#6 ni la suite general del monolito.

---

## 4. Matriz A1–A8

| ID | Criterio | Resultado |
| --- | --- | --- |
| A1 | `transcribe` con clave vacía lanza `IllegalArgumentException` con mensaje claro en español | PASS |
| A2 | `transcribe` con audio inválido / nulo / vacío lanza `IllegalArgumentException` | PASS |
| A3 | fallo de Groq simulado produce `IllegalArgumentException` con mensaje de transcripción fallida | PASS |
| A4 | `speak` con texto nulo, vacío o >1000 caracteres devuelve `null` sin invocar proceso | PASS |
| A5 | `speak` con binario `edge-tts` ausente devuelve `null` sin bloqueo | PASS |
| A6 | `POST /api/bot/message` con audio y sin clave retorna `400` y conserva sesión/productos | PASS |
| A7 | texto + audio simultáneos retorna `400` antes de procesar medios | PASS |
| A8 | audio válido con STT simulado pasa al motor y genera `replyAudioUrl` + respuesta de voz | PASS |

Las pruebas unitarias e integración de `MediaServiceTest` y `ApiAudioTest` cubren estos casos de forma determinista y aislada, sin depender del PATH del sistema ni de una clave real de Groq.

---

## 5. Observaciones de diseño y alcance

- La corrección se limitó a `MediaService`, a las suites de prueba de issue #5 y a la evidencia documental autorizada.
- No se modificó `Api.java`, `IntentFallback`, `IntentParser`, `BotEngine`, POM, SQL, `application.properties` ni assets estáticos.
- El comportamiento de TTS mantiene degradación no bloqueante: si `edge-tts` no está disponible o falla, la app delega la síntesis al navegador (`speechSynthesis`), tal como se define en el diseño.
- La discrepancia de alcance de `IntentFallback` queda documentada como limitación de diseño y no es activada ni cableada para evitar un acoplamiento no aprobado.

---

## 6. Veredicto final

**QA_APPROVED** para el commit `bc69036`.

La implementación cumple el diseño `issue-5-design.md`, la evidencia de construcción `issue-5-reconciliation-build.md` es coherente con la ejecución real y la regresión completa del proyecto queda en verde en JDK 25.
