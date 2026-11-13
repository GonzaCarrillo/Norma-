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
| V1 — Zoom nativo a 100% y 200%, con indicador visible, emulación desactivada | **BLOCKED** | Brave `156.1.98.63` abrió el JAR auditado en un perfil aislado; ventana de `1050×708`. `orca computer capabilities` informa captura/click disponibles, pero `hotkey=false` y `surfaces.menus=false`. El intento de abrir el menú nativo no lo mostró. No se pudo obtener ni verificar el indicador de 100%/200%; CSS zoom, DPR y viewport scale no se usaron. |
| V1 — Reflow, títulos largos y capturas de la misma ventana a 200% | **BLOCKED / evidencia parcial** | `docs/orchestration/issue-6-corrective-evidence/builder/builder_evidence.json` informa 8 tarjetas uniformes, ratio fotográfico 4:3 y sin scroll para 1440, 768, 640, 375 y 320 CSS px. No acredita zoom nativo: todos los campos `screenshot` son `null`, no contiene la verificación con títulos largos al 200%, y el proceso del servidor figura con `exit_code=-9`. |
| Navegación por teclado, diálogo de foto, Escape y retorno de foco | **PASS, solo en navegador embebido al zoom por defecto** | Tab enfocó “Ir a las piezas”; Enter abrió la foto y enfocó `#close-zoom`; Escape cerró y devolvió el foco al botón de la pieza. Esto no sustituye V1-zoom200. |
| Simulador: apertura/cierre por teclado y retorno de foco | **PASS, solo en navegador embebido al zoom por defecto** | Enter abrió `#sim` y enfocó `#close`; Escape cerró y devolvió foco a `#open`. |
| Build y regresiones automatizadas | **PASS en repetición; resultado intermitente observado** | La segunda verificación limpia completó 192 tests. La primera verificación limpia falló en el POST posterior a la desconexión SSE; la repetición dirigida y la segunda suite completa pasaron. No se modificó ni excluyó ninguna prueba. |
| V2–V8 y voz/fallback | **Sin cambio de producción; no revalidados íntegramente en navegador** | El diff no contiene estáticos. `StorefrontContractTest` y `StorefrontSseTest` pasaron en la suite final; la auditoría correctiva no reproduce el flujo de voz externa ni afirma cobertura visual completa para estos criterios. |

## Bloqueos y hallazgos

1. **Bloqueo de aceptación V1-zoom200.** Se requiere una captura de navegador de escritorio que muestre el control nativo a 100% y 200%, además de mediciones y capturas de la misma ventana. El proveedor GUI presente en esta sesión no expuso menú ni atajo de zoom controlable; la ventana de Brave usada para inspección pertenecía a un perfil QA separado y no se alteró la ventana preexistente del usuario. La vista embebida de Orca y las mediciones CSS no satisfacen el requisito. El diseño v2 exige `QA_REJECTED` cuando este control no se puede verificar.
2. **Observación intermitente de SSE.** Reproducción observada una vez: cerrar el lector del stream A y ejecutar la confirmación offline en la secuencia de `StorefrontSseTest`; resultado observado HTTP 500, esperado respuesta satisfactoria y recuperación del estado mediante GET al reconectar. El log apunta a la finalización de un emitter async ya fallido. No se reprodujo en la prueba aislada ni en la segunda suite completa; se informa sin atribuir causa definitiva ni cambiar código fuera de alcance.

**Conclusión:** `QA_REJECTED` por evidencia obligatoria de zoom nativo ausente. El build exitoso repetido y las verificaciones de teclado no autorizan aprobar V1 ni cerrar la issue. No se realizaron cambios de producción.
