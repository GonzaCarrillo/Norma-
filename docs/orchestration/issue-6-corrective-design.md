# Issue #6 — Especificación correctiva de aceptación v2

Autor: Codex. Base: main 8db3f9e. Estado: propuesta para validación de Orca; sin implementación ni aceptación final.

## Hallazgo y contrato

La aprobación histórica 78450e9 acredita V1-zoom200 con viewport scale y body.style.zoom=2. Esa evidencia no satisface el diseño v1.0. Se revoca únicamente esa conclusión de aceptación; conservar los informes originales como historia y enlazar esta corrección. No eliminar el modo de letra grande ni el zoom de fotografía: son funcionalidades independientes.

No cambian REST, SSE, esquema SQLite, DTOs, activos ni dependencias. Catálogo GET sigue siendo autoridad; las mutaciones requieren confirmación. Mantener V2–V8 y regresión del simulador.

## Lista cerrada y secuencia

Antigravity trabaja en worktree exclusivo creado desde la base saneada. Escritura permitida: src/main/resources/static/index.html, style.css, app.js; docs/orchestration/issue-6-corrective-build.md y issue-6-corrective-evidence/. Modificar estáticos solo si la comprobación real demuestra un defecto; no insertar zoom CSS para simular aceptación.

Copilot escribe únicamente issue-6-corrective-qa.md y evidencia propia en issue-6-corrective-evidence/qa/. Codex escribe issue-6-corrective-approval.md después de QA_APPROVED. No editar informes históricos para convertir sus resultados en PASS.

1. Orca valida v2 y confirma disponibilidad de navegador de escritorio con control nativo verificable.
2. Antigravity arranca el JAR con puerto dinámico y DB/uploads propios. Entrega reproducción y corrección mínima, si hace falta; registra expresamente si no hubo cambio de producción.
3. Copilot verifica de forma independiente el mismo SHA y emite QA_APPROVED/QA_REJECTED.
4. Codex contrasta diff y evidencia; Orca conserva integración/cierre posteriores.

## Criterios verificables

- Navegador de escritorio: capturar versión, tamaño de ventana e indicador nativo 100% y luego 200% durante la medición. Emulación desactivada; CSS zoom no inyectado; scale/DPR no alterados para fabricar resultado. Registrar método nativo utilizado.
- Con catálogo de ocho piezas, títulos largos y modo normal/grande, verificar a 200% que no hay solapamientos ni pérdida de texto/controles; fotos 4:3, tarjetas uniformes y ausencia de scroll horizontal conforme V1. Capturar rectángulos y screenshots de la misma ventana.
- Navegar por teclado, abrir/cerrar foto y simulador, Escape y retorno de foco. Revalidar estados, voz con fallback y actualización SSE cuando el cambio de estáticos pueda afectarlos.
- Ejecutar mvn -B clean verify con JDK 25 sobre exportación del commit. Informe atribuido a SHA, comandos, exit code y XML. Evidencia histórica puede complementar, no reemplazar el criterio faltante.
- Sin acceso al indicador/control nativo: QA_REJECTED por bloqueo ambiental, sin sustituir por CSS zoom ni dar por cerrada #6.

## Independencia

Ningún archivo de MediaService, pruebas de voz, Api, SQL, POM, propiedades, Docker o documentación global se modifica en esta pista. #5 usa exclusivamente backend de voz y sus pruebas. Máximo dos pistas de issues; suite de #4 en main/export termina antes de lanzarlas. Cada checkout tiene target, procesos y datos propios; serializar solo recursos compartidos realmente detectados. #7/#8 permanecen en espera.
