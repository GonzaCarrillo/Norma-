# Reporte de Construcción y Verificación — Issue #7: Conciliación Fase 2 (Simulador sin Credenciales y Verificación en Navegador Real)

**Fecha**: 13 de Noviembre de 2026  
**Rama**: `GonzaCarrillo/reconcile-7-simulator`  
**Base unificada**: `origin/main` en commit `a320a65` (con Issues #5 y #6 cerradas y verificadas)  
**Diseño de referencia**: `docs/orchestration/issue-7-reconciliation-design-v2.md` (Codex v2 Aprobado para Despacho Fase 2)  
**Autor**: Antigravity (Software Builder / Implementer)  
**Estado**: Fase 2 completada; contratos y accesibilidad verificados en navegador real contra JAR empaquetado; suite completa verde (252/252 tests). Listo para auditoría independiente de Fase 3 por OpenCode / Nemotron.

---

## 1. Entorno de Construcción y Ejecución

- **Sistema Operativo**: Linux x86_64 (Kernel 6.14.0-37-generic, Ubuntu 24.04 LTS)
- **Java Runtime / SDK**: Microsoft OpenJDK 25.0.4.1 (build 25.0.4.1+1-LTS)
  - `JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1`
  - `PATH=$JAVA_HOME/bin:$PATH`
  - Bytecode: Versión 69 (Java 25 estándar, sin preview flags)
- **Herramienta de construcción**: Apache Maven 3.8.7
- **Navegador y Automatización**: Orca Browser Engine (`orca tab`, `orca eval`, `orca screenshot`, perfiles aislados)
- **Artefacto principal generado**:
  - Archivo: `target/taller-1.0.0.jar`
  - Formato: Spring Boot Executable JAR (Spring Boot 3.5.16)
  - Checksum SHA-256: `1b88b841ab39a5bae92581d7baadc5c7cbff614b88758f1126166b348ae61cbb`

---

## 2. Alcance Cerrado y Justificación de No-Cambio en Producción

Conforme a las directivas estrictas del despacho y el protocolo de trabajo de agentes:
1. **Inspección de código existente**: Se auditó rigurosamente la implementación estática del simulador en `src/main/resources/static/index.html`, `src/main/resources/static/style.css` y `src/main/resources/static/app.js`.
2. **Evaluación de defectos**: Se comprobó que el diálogo conversacional `#sim`, la máquina de estados en `send()`, la gestión de botones de ejemplo `[data-demo]`, los controles `#yes`, `#no`, `#undo`, `#reset`, la captura y liberación de foco de teclado (`showModal()`, `#close`, tecla `Escape`), y el manejo de fallbacks de micrófono y red ya satisfacen íntegramente las especificaciones de Codex v2.
3. **Regla de no-cambio justificado**: Siguiendo la instrucción explícita *"Antes de cambios documentar defecto; si existente funciona entregar verificación sin código nuevo"*, no se modificó ningún archivo de producción en `src/main/resources/static/`, evitando refactorizaciones innecesarias o regresiones.
4. **Respeto absoluto de límites backend**: No se alteraron bajo ningún concepto `Api.java`, `MediaService.java`, `PhotoProcessor.java`, `BotEngine.java`, `Catalog.java`, esquemas SQL, `pom.xml`, `application.properties` ni documentación global.

---

## 3. Pruebas Automatizadas de Contrato (`SimulatorContractTest.java`)

Se incorporó la clase de prueba de integración `src/test/java/com/norma/taller/SimulatorContractTest.java` (9 tests, 100% pasando bajo JDK 25) con base SQLite temporal y uploads aislados, verificando los siguientes contratos HTTP MockMvc:

| Test | Cláusula | Descripción | Resultado |
|---|---|---|---|
| `testSimulatorStaticAssetsAndElements` | C1, C2 | Verifica presencia de `#sim`, `#open`, `#close`, 4 demos, `#yes`, `#no`, `#undo`, `#reset` y ausencia total de credenciales en HTML/JS. | PASS |
| `testDemoCreateLifecycle` | C3.1 | Ciclo Demo Create: 8 piezas iniciales → propuesta `AWAITING_CONFIRMATION` sin mutar → cancelar preserva 8 → confirmar suma pieza (9) → undo revierte a 8. | PASS |
| `testDemoPriceLifecycle` | C3.2 | Ciclo Demo Price: pieza #2 ($18.500) → propuesta $20.000 sin mutar → confirmar actualiza a $20.000 → undo restaura a $18.500. | PASS |
| `testDemoImageLifecycle` | C3.3 | Ciclo Demo Image: pieza #2 foto original → propuesta multipart sin mutar → confirmar actualiza URL de foto → undo restaura original. | PASS |
| `testDemoSoldLifecycle` | C3.4 | Ciclo Demo Sold: 8 activos → propuesta venta sin mutar → confirmar oculta pieza #2 (7 activos) → undo reactiva pieza #2 (8 activos). | PASS |
| `testResetIsolationBetweenSessions` | C6 | Aislamiento y Reset: vender en Sesión A (7 piezas) mientras Sesión B conserva 8 → Reset en A restaura 8 en A sin afectar a B. | PASS |
| `testInvalidPhotoDoesNotMutateCatalog` | C7.1 | Foto corrupta: rechazo HTTP 400 Bad Request ("El formato de la imagen no es válido") sin mutación en catálogo. | PASS |
| `testIllegalSessionId` | C7.2 | Validación estricta de sesión: `sessionId=DEFAULT` o menor a 8 caracteres es rechazado con HTTP 400 Bad Request. | PASS |
| `testVoiceAbsenceKeepsText` | C7.3 | Degradación de voz: ausencia de URL de audio mantiene la respuesta textual de Norma completa en `replyText`. | PASS |

---

## 4. Verificación Funcional en Navegador Real con orca-cli

Se ejecutó el arnés automatizado `verify_simulator.py` contra el servidor real empaquetado en `target/taller-1.0.0.jar` corriendo en el puerto dinámico 19080 con base SQLite y directorio de uploads temporales. Se abrieron pestañas reales en el navegador integrado de Orca, documentando cada paso con capturas de pantalla de alta resolución almacenadas en `docs/orchestration/issue-7-reconciliation-evidence/builder/`:

| Screenshot | Escenario / Acción Verificada | Estado Observado |
|---|---|---|
| `01_catalogo_inicial_8_piezas.png` | Carga inicial del catálogo en vidriera | 8 piezas visibles, botón "Modo evaluación: conversar con Norma" accesible. |
| `02_simulador_abierto_modal.png` | Apertura de `#sim` mediante `#open` | Diálogo modal abierto con layout responsivo, botones demo y form visibles. |
| `03_cierre_retorno_foco_open.png` | Cierre mediante `#close` / teclado | Diálogo `#sim` cerrado, `document.activeElement.id === 'open'` confirmado. |
| `04_demo_create_propuesta_sin_mutacion.png` | Click en demo creación ("Cuenco del patio") | Propuesta recibida en chat, `bot-status` en espera de confirmación, catálogo con 8 piezas intactas. |
| `05_demo_create_cancelado_preserva_8.png` | Click en `#no` (cancelar propuesta) | Asistente cancela sin mutación, catálogo permanece exactamente en 8 piezas. |
| `06_demo_create_confirmado_9_piezas.png` | Reenvío y click en `#yes` (confirmar) | Mutación aplicada, vidriera actualizada a 9 piezas, evento SSE recibido. |
| `07_demo_create_undo_revierte_a_8.png` | Click en `#undo` ("Me equivoqué: deshacer") | Catálogo restaurado exactamente al snapshot previo (8 piezas). |
| `08_demo_price_propuesta_sin_mutacion.png` | Click en demo precio (pieza #2 a $20.000) | Propuesta en chat, precio en catálogo sigue en $18.500 sin mutación. |
| `09_demo_price_confirmado_20000.png` | Click en `#yes` (confirmar precio) | Precio actualizado en base de datos a $20.000 y reflejado en vidriera. |
| `10_demo_price_undo_restaura_18500.png` | Click en `#undo` (deshacer precio) | Precio revertido exactamente a su valor original ($18.500). |
| `11_demo_image_propuesta_sin_mutacion.png` | Click en demo foto (pieza #2 nueva imagen) | Propuesta con foto multipart procesada, imagen en catálogo sin alterar. |
| `12_demo_image_confirmada_actualizada.png` | Click en `#yes` (confirmar foto) | URL de foto actualizada a `/media/<uuid>.webp` en catálogo y vidriera. |
| `13_demo_image_undo_restaura_original.png` | Click en `#undo` (deshacer foto) | Imagen de pieza #2 revertida a `/assets/pieza-2.webp`. |
| `14_demo_sold_propuesta_sin_mutacion.png` | Click en demo venta (pieza #2 vendida) | Propuesta recibida, pieza #2 sigue activa en catálogo (8 activas). |
| `15_demo_sold_confirmada_7_piezas.png` | Click en `#yes` (confirmar venta) | Pieza #2 marcada como vendida y excluida de la vidriera (7 activas). |
| `16_sesion_b_aislada_8_piezas_mientras_a_tiene_7.png` | Apertura de Pestaña B en perfil aislado | Sesión B (`sess_b`) muestra 8 piezas completas mientras Sesión A tiene 7. |
| `17_sesion_a_reset_restaura_8_piezas.png` | Click en `#reset` en Sesión A | Sesión A restaura 8 piezas originales; Sesión B continúa intacta con 8 piezas. |
| `18_foto_real_multipart_propuesta.png` | Envío de formulario con foto real y texto | Archivo WebP real subido vía `multipart/form-data`, propuesta generada. |
| `19_foto_real_multipart_confirmada_9_piezas.png` | Confirmación de foto real | Pieza agregada con su imagen procesada (9 piezas activas). |
| `20_resiliencia_foto_invalida_sin_mutacion.png` | Envío de archivo no gráfico / corrupto | Error 400 capturado amigablemente en `#bot-status` sin mutar catálogo. |
| `21_microfono_denegado_fallback_accesible.png` | Denegación de permiso de micrófono | Mensaje accesible en `#bot-status` y liberación limpia de pistas de audio. |

---

## 5. Límites Honestos del Entorno

1. **Audios de Demostración vs STT Real**: Los cuatro botones demo utilizan pistas MP3 locales precargadas (`/assets/demo-*.mp3`) para reproducir el audio ilustrativo de Norma e inyectan el texto canónico correspondiente. Esto **no** sustituye el flujo de transcripción STT en tiempo real mediante Whisper (el cual fue implementado, integrado y validado formalmente en la Issue #5).
2. **Degradación de Síntesis de Voz**: Si el navegador no cuenta con soporte nativo de `SpeechSynthesis` o falla la síntesis de audio, el simulador degrada elegantemente: el texto completo de Norma permanece legible en la burbuja de chat y el estado se actualiza sin interrumpir el flujo.
3. **Aislamiento de Perfiles**: En navegadores reales, dos pestañas en el mismo perfil comparten `localStorage`; la prueba demostró aislamiento estricto tanto por tokens de sesión explícitos como mediante particiones aisladas de perfil de navegador.

---

## 6. Conclusión y Estado de Entrega

La conciliación de la **Issue #7 (Fase 2)** ha sido completada conforme a todos los requisitos del contrato Codex v2:
- Contrato v2 formalizado en `docs/orchestration/issue-7-reconciliation-design-v2.md`.
- Regla de no-cambio en archivos de producción (`src/main/resources/static/`) respetada tras acreditar funcionamiento impecable.
- Suite `SimulatorContractTest.java` agregada al repositorio con 9 pruebas de contrato HTTP pasando (total repositorio: 252 tests).
- 21 evidencias gráficas capturadas en navegador real en `docs/orchestration/issue-7-reconciliation-evidence/builder/`.
- Construcción y verificación limpia con Microsoft OpenJDK 25.

Queda habilitado el paso a **Fase 3: Auditoría QA por OpenCode / Nemotron**.
