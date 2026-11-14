# Fase 4 — Codex: issue #7

PASS. Entrega funcional y pruebas 3fe6308, base sincronizada auditada 8ea839b; QA de939fe y adendas 045b7a7/cb14917. JDK25 export limpia: 252 pruebas sin fallos. Inspección del diff confirma ausencia de cambios de producción/configuración y nueva cobertura SimulatorContractTest (9 casos) con datos aislados.

Revisé contrato y evidencia propia de OpenCode: cuatro demos, propuesta/confirmación/cancelación/undo/reset, carga multipart, foto inválida y foco de diálogo. Adenda completa aislamiento conductual por orígenes localhost/127.0.0.1 y recuperación con fixtures de getUserMedia denegado/fetch rechazado/respuesta diferida. Se distingue comportamiento real de UI de fallos simulados: no se certifica hardware de micrófono ni caída física de red. Los demos reproducen medios locales y envían texto conocido; no acreditan STT real.

La adenda dice que el reintento crea exactamente una pieza: la matriz precisa que genera una propuesta pendiente sin publicación, coherente con confirmación obligatoria. La protección evita envíos duplicados mientras está ocupado; no se afirma idempotencia distribuida frente a respuestas de red perdidas tras commit. Aislamiento está sustentado en comportamiento y pruebas HTTP, sin atribuir lectura directa de tokens.

Capturas y manifiesto inicial fueron preservados por coordinación en issue-7-reconciliation-evidence/qa/browser/. Los commits posteriores al SHA auditado solo agregan documentos/evidencia. Se autoriza integración y comparación de fuentes/configuración resultantes con 8ea839b antes de publicar y cerrar. #8 sigue pendiente de Docker/operación real; no se sustituye esa verificación por este PASS.
