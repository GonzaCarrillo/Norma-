# Auditoría QA correctiva — Issue #6 (v2)

- **Dictamen:** QA_REJECTED
- **Commit auditado:** `9e11580f03bc82800938541008e566a3f32ba384`
- **Base:** `d13c51b835dd1a32eadf7043957e02b918f5c3e2`
- **Diseño:** Codex v2.0, `docs/orchestration/issue-6-corrective-design.md`
- **Gate:** autorización de Orca recibida en `msg_f0fc1a38c742`, confirmando v2 para este despacho.
- **Worktree/rama:** `/home/gonzalocar/orca/workspaces/Norma-/reconcile-6-storefront`, `GonzaCarrillo/reconcile-6-storefront`.

La auditoría se ejecutó sobre `git archive HEAD` exportado en `target/qa.sojvNu/export`; el worktree estaba limpio antes de QA. El diff desde la base contiene únicamente reportes y evidencia documental. No hay cambios en estáticos, backend, contratos, pruebas, configuración ni dependencias.

## Entorno y evidencia de build

- Linux 6.14.0-37-generic; Microsoft OpenJDK `25.0.4.1+1-LTS`; Apache Maven `3.8.7`.
- Primera ejecución limpia `mvn -B clean verify`: **exit 1**, 192 tests, 0 failures, 1 error, 0 skipped. `StorefrontSseTest.testCompleteSseTransportLifecycle` recibió HTTP 500 en `StorefrontSseTest.java:433`, al confirmar una mutación después de cerrar el stream A. El log de servidor registra `IllegalStateException`/`AsyncRequestNotUsableException` al completar un `SseEmitter` cuyo contexto async ya había fallado.
- Repetición dirigida `mvn -B -Dtest=StorefrontSseTest test`: **exit 0**, 1 test, sin errores.
- Segunda ejecución limpia `mvn -B clean verify`: **BUILD SUCCESS**, exit 0; 16 suites, 192 tests, 0 failures, 0 errors, 0 skipped. El error inicial no se reprodujo; se conserva como resultado intermitente observado, no como fallo confirmado de forma repetible.
- SHA-256 del JAR de la ejecución exitosa: `84714824d8a9062ad8c817406224c76052196f6971dfc6049d9d94c5afe723d7`.
- Hashes de logs: primera verificación `7a453c922971e47330085fe407c44c1eff67db38425199fddcdc77f67e45b1a7`; prueba dirigida `9a0e57886fdd2b3843effbfeb4cfeb460c61ae1516f40a974c1de514f3a4ab37`; repetición limpia `ba2e44bbc55f5df075deeb428c3953bdb3ab63cc44ab3ce5b64fc14740fd7b64`.
- Logs comprimidos sin modificar (`.log.gz`) y XML Surefire están en `docs/orchestration/issue-6-corrective-evidence/qa/`; al descomprimir, los logs coinciden con los hashes indicados.

## Matriz de aceptación

| Criterio | Resultado | Evidencia y límite |
|---|---|---|
| V1 — Zoom nativo a 100% y 200%, con indicador visible, emulación desactivada | **Aceptación manual del usuario; sin evidencia técnica aportada** | El usuario confirmó manualmente que probó y el zoom funciona. La observación de QA sobre la falta de acceso al indicador queda como antecedente técnico, no como bloqueo vigente de este gate. No se informaron navegador, medidas, método ni capturas; Copilot no los atribuye a su auditoría. Ver adenda. |
| V1 — Reflow, títulos largos y capturas de la misma ventana a 200% | **BLOCKED / evidencia parcial** | `docs/orchestration/issue-6-corrective-evidence/builder/builder_evidence.json` informa 8 tarjetas uniformes, ratio fotográfico 4:3 y sin scroll para 1440, 768, 640, 375 y 320 CSS px. No acredita zoom nativo: todos los campos `screenshot` son `null`, no contiene la verificación con títulos largos al 200%, y el proceso del servidor figura con `exit_code=-9`. |
| Navegación por teclado, diálogo de foto, Escape y retorno de foco | **PASS, solo en navegador embebido al zoom por defecto** | Tab enfocó “Ir a las piezas”; Enter abrió la foto y enfocó `#close-zoom`; Escape cerró y devolvió el foco al botón de la pieza. Esto no sustituye V1-zoom200. |
| Simulador: apertura/cierre por teclado y retorno de foco | **PASS, solo en navegador embebido al zoom por defecto** | Enter abrió `#sim` y enfocó `#close`; Escape cerró y devolvió foco a `#open`. |
| Build y regresiones automatizadas | **PASS en repetición; resultado intermitente observado** | La segunda verificación limpia completó 192 tests. La primera verificación limpia falló en el POST posterior a la desconexión SSE; la repetición dirigida y la segunda suite completa pasaron. No se modificó ni excluyó ninguna prueba. |
| V2–V8 y voz/fallback | **Sin cambio de producción; no revalidados íntegramente en navegador** | El diff no contiene estáticos. `StorefrontContractTest` y `StorefrontSseTest` pasaron en la suite final; la auditoría correctiva no reproduce el flujo de voz externa ni afirma cobertura visual completa para estos criterios. |

## Bloqueos y hallazgos

1. **Limitación técnica observada para V1-zoom200.** Durante la auditoría Copilot no obtuvo una captura del indicador nativo: el proveedor GUI no expuso menú ni atajo de zoom controlable. Esta observación queda supersedida como bloqueo de aceptación por la confirmación manual del usuario registrada en la adenda; no se convierte en evidencia técnica de navegador o medición.
2. **Observación intermitente de SSE.** Reproducción observada una vez: cerrar el lector del stream A y ejecutar la confirmación offline en la secuencia de `StorefrontSseTest`; resultado observado HTTP 500, esperado respuesta satisfactoria y recuperación del estado mediante GET al reconectar. El log apunta a la finalización de un emitter async ya fallido. No se reprodujo en la prueba aislada ni en la segunda suite completa; se informa sin atribuir causa definitiva ni cambiar código fuera de alcance.

**Conclusión:** `QA_REJECTED` mientras siga abierto el hallazgo de SSE descrito arriba. La aceptación manual del zoom no resuelve ese hallazgo ni constituye `QA_APPROVED`. No se realizaron cambios de producción.

## Adenda — Aceptación manual de V1-zoom200

**Fuente:** confirmación manual del usuario, comunicada por Orca en el despacho `task_ad01f46c353f` / `ctx_5e59a6ba24b0`: «la #6 el zoom ya lo probe yo y funciona, dalo por hecho, y continua con tus tareas».

Conforme a esa instrucción, la confirmación del usuario satisface el gate de V1-zoom200 para esta revisión y reemplaza el requisito de que Copilot repita la ejecución automatizada. El usuario no reportó navegador, versión, dimensión de ventana, valores observados, método ni capturas. No se inventa ni se atribuye esa evidencia a Copilot; la limitación técnica observada durante esta auditoría queda registrada solo como antecedente.

El hallazgo intermitente de SSE permanece **ABIERTO**. Antigravity tiene asignado su diagnóstico en `ctx_da62f6aa396a`; no se emite `QA_APPROVED` hasta recibir y revisar la resolución sobre la entrega correspondiente. Esta adenda es documental: no se repitieron pruebas ni se modificó código de producción.

## Reauditoría SSE v2.1 — 2026-11-13

- **Dictamen de esta reauditoría:** QA_REJECTED
- **SHA auditado:** `e79e3b1523263927396f090e1c417362dba30728`
- **Base de implementación v2.1:** `e89c04bd225460683f004b6a59510d9003b1e191`
- **Diseño:** `docs/orchestration/issue-6-sse-design-v2.1.md`
- **Entorno:** Linux 6.14.0-37-generic; Microsoft OpenJDK `25.0.4.1+1-LTS`; Apache Maven `3.8.7`.
- **Worktree:** `/home/gonzalocar/orca/workspaces/Norma-/reconcile-6-storefront`, rama `GonzaCarrillo/reconcile-6-storefront`; limpio al inicio, HEAD `e79e3b1523263927396f090e1c417362dba30728`.

El usuario aceptó manualmente el zoom nativo según la adenda anterior; esa decisión se conserva y no se repitió la prueba. El resto de esta reauditoría inspeccionó el diff desde `e89c04b`, verificó la evidencia de builder y ejecutó `mvn -B clean verify` sobre `git archive HEAD` en `target/qa-sse.rlpc3A/export`, con base de pruebas y puertos del runner, sin modificar pruebas ni producción.

### Build y evidencia

- **Resultado real:** `BUILD SUCCESS`, exit code `0`; 17 suites, 197 tests, 0 failures, 0 errors, 0 skipped.
- **JAR SHA-256:** `68ece117c11487365adc91d7367adb19a7b3336f3f47e4e5624d461d141928a3`.
- **Log sin comprimir SHA-256:** `2e1e0c48d14fa38f545f00b2e98ebb2a8e6cf8d92f698f874afa65a74a974418`.
- **Basedir Surefire:** `target/qa-sse.rlpc3A/export`.
- Log, XML Surefire de las 17 suites, resumen, SHA y commit están en `docs/orchestration/issue-6-corrective-evidence/qa/v2.1/`.

### Verificaciones específicas

| Criterio | Resultado | Evidencia |
|---|---|---|
| `send()` fallido no propaga, emisor retirado y mapa limpiado; `complete()` falla con excepción runtime | PASS | `ApiPublishTest.testBrokenEmitterDoesNotThrowAndIsRemoved` mockea `IOException` en `send()` e `IllegalStateException` en `complete()`, comprueba no propagación, retiro y eliminación de la clave. |
| Emisor sano continúa tras fallo de otro | PASS | `testHealthyEmitterStillReceivesWhenAnotherFails` comprueba llamada a `send()` del sano y retiro del roto. |
| Aislamiento entre sesiones | PASS | `testSessionIsolation` verifica que publicar en A no notifica B y que B sí recibe al publicar su sesión. |
| Control positivo del baseline | PARCIAL | `testPositiveControlBaselineBehavior` reproduce en una lambda el `catch` anterior sin protección y comprueba que el mock de `complete()` propaga. No invoca directamente una implementación baseline; el diff confirma que el commit padre sí llamaba `e.complete()` sin protección. |
| SSE HTTP real: desconectar, confirmar mutación, reconectar y recuperar por GET | PASS | `StorefrontSseTest` usa servidor aleatorio, cierra `readerA`, ejecuta dos POSTs cuya respuesta exitosa exige HTTP 200, reabre el stream y comprueba `catalog-update/conectado` y precio 35000 por GET. La prueba conserva los asserts de aislamiento y eventos previos. |
| Fuera de la frontera SSE | PASS por inspección de diff | `message`, DTOs, `MediaConfig` y `ApiErrors` no se modificaron; no se alteraron contrato, POM, SQL o estáticos. |

### Hallazgo bloqueante

**P2 — Carrera en retiro de lista con suscripción simultánea.** En el callback `cleanup` y en `publish`, el código retira el emitter, comprueba `list.isEmpty()` y luego ejecuta `streams.remove(sessionId, list)`. Entre la comprobación y el `remove`, un `stream()` concurrente puede obtener esa misma lista mediante `computeIfAbsent` y añadir un nuevo emitter; el retiro condicional todavía elimina del mapa la lista ahora no vacía. La nueva suscripción queda huérfana y no recibirá publicaciones posteriores. Es una intercalación reproducible por el orden de operaciones observado en código, no una falla empírica de la suite; no hay prueba que la cubra. Bloquea QA hasta resolver/justificar la sincronización del ciclo de vida y añadir una regresión para registro concurrente con cleanup del último emitter.

**Conclusión de v2.1:** aunque el build y los casos SSE solicitados pasan, no emito `QA_APPROVED` porque la limpieza puede retirar una lista que ya contiene una conexión activa. La adenda manual del zoom permanece aceptada. No modifiqué código de producción ni pruebas; la corrección corresponde a Antigravity y requiere una nueva reauditoría sobre su SHA.
