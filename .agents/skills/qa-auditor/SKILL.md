---
name: qa-auditor
description: Auditar como OpenCode / Nemotron una entrega de Norma con Maven y JDK 25, pruebas JUnit/MockMvc/SQLite y evidencia funcional; emitir QA_APPROVED o QA_REJECTED antes de la aprobación final de Codex.
---

# Auditoría independiente de Norma

1. Leer AGENTS.md, la issue, el diseño aprobado y su lista cerrada de archivos. Registrar ruta, rama, SHA, `git status --short` y diff contra la base aprobada. No corregir producción ni cambiar contratos durante QA.
2. Confirmar JDK 25 y Maven con `java -version` y `mvn -version`. La instalación local observada es `/home/gonzalocar/.jdks/ms-25.0.4.1`; configurar JAVA_HOME/PATH si corresponde. Seguir el procedimiento de exportación limpia de `../testing-and-qa/SKILL.md`: probar fuentes de `git archive HEAD`, sin incorporar archivos sin seguimiento.
3. Ejecutar `mvn -B clean verify` en la exportación. Registrar comando, exit code real, log, hash del JAR `target/taller-1.0.0.jar` y conteos tests/failures/errors/skipped de Surefire. No usar skipTests ni exclusiones para obtener verde. Ejecutar un solo Maven por checkout; SQLite/uploads/temp propios, RANDOM_PORT para HTTP/SSE y sin contenedores o volúmenes compartidos.
4. Contrastar cada criterio con pruebas de comportamiento: JUnit 5/AssertJ para unidades, Spring Boot Test/MockMvc para multipart/errores, SQLite real para persistencia y transacciones, HttpClient/EventSource real para SSE. Verificar confirmación/cancelación/undo, aislamiento de sesiones y regresiones de suites previas. STT/TTS externos se simulan para errores deterministas; distinguirlos de una llamada real a Groq/edge-tts. No exponer claves.
5. Para frontend, verificar navegador, teclado/foco, estados y reconexión según el diseño. Zoom nativo requiere indicador del navegador al 200%, CSS zoom sin alterar y emulación desactivada; DPR, viewport scale y body.style.zoom no acreditan ese criterio. Si el entorno no permite comprobarlo, registrar el bloqueo.
6. Entregar `docs/orchestration/issue-N-qa.md` con SHA/base/diseño, entorno, comandos/resultados, rutas de evidencia, matriz por criterio y limitaciones. Cada defecto incluye severidad, reproducción, esperado y observado. Con todos los criterios comprobados emitir QA_APPROVED; ante fallo o evidencia obligatoria ausente emitir QA_REJECTED, identificando el bloqueo ambiental si existe. No inferir CI verde por la existencia de un workflow.
7. Reportar a Orca. Las correcciones corresponden a Antigravity; reverificar defecto y regresión afectada sobre el nuevo SHA. QA_APPROVED habilita Fase 4 de Codex, no merge, push de main ni cierre por OpenCode.
