# Issue #5 — Plan de conciliación QA v2

Autor: Codex. Base: main 8db3f9e con implementación 2361764 integrada localmente. Estado: plan para Orca/Copilot; no constituye QA ni aprobación final.

## Alcance cerrado

Copilot audita código sin modificarlo. Entrega docs/orchestration/issue-5-reconciliation-qa.md y evidencia propia en issue-5-reconciliation-evidence/. Si hay defectos, Antigravity los corrige en worktree exclusivo: src/main/java/com/norma/taller/MediaService.java (solo transcribe/speak y sus colaboradores de voz), src/test/java/com/norma/taller/MediaServiceTest.java, ApiAudioTest.java y docs/orchestration/issue-5-reconciliation-build.md. Conserva constructor/root y delegación de imagen a PhotoProcessor.

Prohibidos en esta pista: estáticos, Api, PhotoProcessor, modelos/motor/parser, SQL, POM, propiedades y assets. Si corregir requiere cruzar esta frontera, detener la parte afectada y volver a Codex/Orca; reevaluar independencia de #6. IntentFallback histórico no se activa ni se compila.

## Entrada y auditoría

Orca asigna a GitHub Copilot con AGENTS.md y qa-auditor. Tras validación rápida de #4, auditar la base saneada identificada por SHA, en checkout propio y exportación limpia. JDK 25, java -version, mvn -version y mvn -B clean verify; registrar exit code, Surefire, hash del JAR y log. Un workflow presente no demuestra ejecución exitosa.

Comprobar la matriz A1–A8 del diseño issue-5-design.md contra comportamiento real: ausencia de clave STT, audio inválido, error/cuota/timeout Groq, resultado vacío, texto/audio incompatible, transcripción válida simulada hacia FSM y conservación de sesión/productos ante fallos. Revisar connect/read timeouts y limpieza de temporales incluso ante excepciones.

TTS: null/vacío y >1000 caracteres sin proceso; binario ausente, salida no cero, timeout, archivo ausente/vacío y éxito con MP3; limpieza de salida fallida y terminación del proceso propio. Pruebas deterministas no deben depender de que edge-tts esté ausente en el PATH. Distinguir inspección estática, fixtures/procesos simulados y proveedor real. No consumir claves ni afirmar voz real verificada sin evidencia.

Preservar Reply y fallback legible cuando replyAudioUrl es null; ninguna confirmación automática ni llamada de proveedor dentro de la transacción del motor. Revisar la brecha de alcance: GitHub #5 menciona fallback de intenciones, mientras el diseño v1 lo deja desconectado; reportarla como discrepancia para decisión explícita de Orca, no declararla satisfecha silenciosamente.

## Gates y aislamiento

QA_APPROVED solo con criterios completos y evidencia atribuible al commit; en otro caso QA_REJECTED con severidad, reproducción, esperado/observado y causa. Los defectos vuelven a Antigravity, después a Copilot y finalmente a Codex (issue-5-reconciliation-approval.md). Sin correcciones necesarias, pasar directamente de QA a Fase 4.

DB/uploads/temp bajo checkout propio, puertos dinámicos y sin contenedores compartidos. No dos Maven en el mismo target. Pista disjunta de #6; si ambas terminan, integrar de a una y revalidar impacto en la base restante. #7/#8 retenidas hasta QA de Copilot y aprobación final de Codex para ambas. No push de main ni cierre durante el saneamiento.
