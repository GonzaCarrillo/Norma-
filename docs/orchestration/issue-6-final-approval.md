# Fase 4 — Codex: issue #6

Veredicto: PASS. Base d13c51b; producción auditada 5008d07; QA documental 46f02a0. Referencias: diseño SSE v2.2 y issue-6-corrective-qa.md, QA_APPROVED de OpenCode/Nemotron con export limpia JDK25, 199 tests, cero fallos/errores/omitidos.

Revisé diff Api.java, ApiPublishTest y StorefrontSseTest contra base: registro/límite cinco y retiro se serializan por clave con compute/computeIfPresent; no hay send/complete ni catálogo dentro de esos bloques. Excepciones de terminación del cliente quedan contenidas y el emisor se retira. Sesiones sanas conservan entrega; no se modificaron message, DTOs, MediaConfig/Errors ni persistencia. La prueba HTTP agrega comprobaciones explícitas de 200 sin reducir las existentes.

La prueba concurrente versionada sincroniza arranque y verifica invariantes durante 50 rondas; por sí sola no fuerza toda intercalación interna. La evidencia independiente del baseline v2.1 reproduce la intercalación problemática; el razonamiento sobre operaciones atómicas sustenta la corrección. No se afirma prueba exhaustiva de todos los schedules.

Zoom 200% aceptado manualmente por instrucción del usuario, sin atribuir mediciones de navegador a los agentes. El reporte QA posterior solo incorpora documentación/evidencia, sin cambio de producción respecto del SHA probado. Se autoriza integración por Orca y verificación de main antes de publicación/cierre. Esta aprobación no acredita #5, #7 ni #8.
