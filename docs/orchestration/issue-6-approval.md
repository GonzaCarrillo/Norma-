# Aprobación de diseño — Issue #6

Orca aprueba `issue-6-design.md` versión 1.0 en commit `e0f6eada669b9b5070f64688a028a7cd946a7ddd`.
Fuente de despacho: Orca Lead Software Engineer / Coordinador (`term_104a5a3f-cca1-4813-a727-30ddd7a9152f`, tarea `task_b20a8cdcab02`, dispatch `ctx_b91142bc5242`).

## Alcance y decisiones aprobadas
- Aprobación íntegra de G0, scope §10 y matriz de aceptación V1–V8.
- Coordinador de refresco GET/SSE con control de invalidación/generación, sin polling continuo.
- Reconciliación de tarjetas en DOM por `pieceNumber` para preservar foco y contexto visual sin parpadeo ni pérdida de selección.
- Tipografía accesible: tamaño base raíz 22px en modo habitual, ampliación a ≥ 28.6px (≥30%) en modo grande aplicada al elemento raíz con unidades escalables.
- Grilla accesible con tarjetas de altura uniforme en cada fila (`grid-auto-rows: 1fr` o equivalente) sin recortar ni truncar títulos de 10 a 60 caracteres.
- Contraste estricto > 7:1 en todas las combinaciones texto/fondo visibles en estados normal, hover, focus, disabled, placeholder y modales.
- Foco de teclado riguroso: skip link, navegación natural, zoom dialog modal accesible con botón de retorno grande, Escape y preservación de foco ante actualizaciones/SSE.
- Lectura por voz nativa (`speechSynthesis`) con soporte para catálogo y pieza individual, botón de detención siempre accesible, cancelación consistente y fallback visible cuando el navegador o la voz no estén disponibles.
- Preservación íntegra de los IDs y funcionamiento del simulador existente (§7) sin alterar su contrato ni anticipar la Issue #7.
- Lista cerrada de archivos autorizados (§10): `index.html`, `style.css`, `app.js`, `StorefrontContractTest.java`, `StorefrontSseTest.java`, y documentación/evidencia de orquestación de la Issue #6. Prohibido tocar backend productivo, modelos, SQL, POM, configuración o pruebas existentes.
- Fases de construcción: B1 (estáticos, V1–V5, evidencia visual orca-cli browser, gate previo de Orca) y B2 (coordinador SSE, tests de contrato/transporte, V6–V8, verificación final).

## Gate Parcial B1 → B2 (Aprobado por Orca tras consulta Codex task_dc4c1cfb552f)
- **Estado:** APROBADO para avanzar a Fase B2.
- **Mensaje de despacho:** `msg_c42ce8a4bc12` y confirmación `msg_9e1b89291406`.
- **Condición y Residual:** V1-zoom200 real del navegador permanece PENDIENTE para auditoría independiente por OpenCode QA (bloquea merge/cierre final hasta verificación en navegador real con menú indicador 200%). Las capturas de CSS zoom 2.0x (`v1_css_zoom_200.png`), reflow WCAG (640px/320px) y DPR2 (`v1_zoom_200.png`) se registran como evidencia técnica complementaria sin declarar PASS total de V1-zoom200 por parte del builder.
- **Autorización:** Construcción de Fase B2 autorizada (coordinador SSE, `StorefrontContractTest.java`, `StorefrontSseTest.java`, V6–V8, `mvn -B clean verify`, y `issue-6-build-report.md`).
