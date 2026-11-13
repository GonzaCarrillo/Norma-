# Saneamiento de base — 2026-11-13

Base inspeccionada: main 8db3f9e. Sin push ni cierre de issues.

Los seis despachos sin seguimiento issue-4-build-task.md, issue-4-qa-task.md, issue-6-build-task.md, issue-6-design-task.md, issue-6-qa-task.md y parallel-independence-task.md se incorporan como trazabilidad histórica con aviso de obsolescencia. No son instrucciones activas.

IntentFallback.java se conserva como archive/IntentFallback.java.txt: servicio Spring no utilizado por el flujo aprobado, sin autorización para activar llamadas LLM. ApiTest.java se conserva como archive/ApiTest.java.txt: prueba legacy con uploads compartidos y cobertura reemplazada por suites versionadas de API/medios. Ambos quedan fuera de compilación y descubrimiento de tests. No se perdió su contenido; solo se agregó una nota de archivo.

AGENTS.md fija las cuatro fases oficiales; qa-auditor define el veredicto de Copilot. Los planes issue-6-corrective-design.md y issue-5-reconciliation-qa-plan.md delimitan las pistas disjuntas.

Antes de abrir ambas pistas, Orca debe obtener verificación rápida de #4 por Copilot sobre la base saneada/exportación limpia: suite completa JDK25, matriz de medios y procedencia, reporte issue-4-reconciliation-qa.md con SHA, comandos y evidencia. Luego Codex revisa conformidad final. Esto habilita conciliación, no autoriza push/cierre previo al plan solicitado.

No se certifica salud del producto mediante este commit documental: el build y las pruebas corresponden al siguiente gate de Copilot. #7/#8 permanecen retenidas.
