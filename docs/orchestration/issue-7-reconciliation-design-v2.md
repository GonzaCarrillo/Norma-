# Especificación de Diseño y Contrato Codex v2 — Conciliación Issue #7: Simulador de Norma sin Credenciales

**Versión**: 2.0 (Aprobada para Despacho Fase 2)  
**Fecha**: 13 de Noviembre de 2026  
**Autor**: Codex (Software Architect) — Registrado por Antigravity (Software Builder)  
**Issue de Referencia**: [#7: Simulador de Norma sin credenciales](https://github.com/GonzaCarrillo/Norma-/issues/7)  
**Base unificada**: `origin/main` en commit `a320a65` (con Issues #5 y #6 cerradas y verificadas)  
**Rama de trabajo**: `GonzaCarrillo/reconcile-7-simulator`  
**Worktree**: `/home/gonzalocar/orca/workspaces/Norma-/reconcile-7-simulator`  

---

## 1. Resumen y Propósito de la Conciliación

La issue #7 tiene por objetivo garantizar y evidenciar que el simulador conversacional de Norma opera en el navegador de manera autónoma, accesible, sin credenciales de WhatsApp ni proveedores externos de IA, ejecutando las cuatro operaciones esenciales del catálogo (`create`, `price`, `image`, `sold`) mediante una FSM tipada con confirmación explícita, control de deshacer (`undo`) y restauración (`reset`), con aislamiento total entre sesiones.

A diferencia del enfoque preliminar de diseño v1.0 que asumía cierre administrativo sin ejecución formal de Fase 2 ni Fase 3, este contrato v2 formaliza el protocolo obligatorio del repositorio:
1. Registro explícito del contrato técnico v2 en el repositorio.
2. Ejecución de Fase 2 (Antigravity): verificación funcional exhaustiva contra el JAR empaquetado, suite de pruebas automatizadas HTTP `SimulatorContractTest.java`, inspección de accesibilidad, concurrencia, resiliencia y recolección de evidencia en navegador.
3. Regla de implementación mínima: documentar todo defecto previo a corregir; si el código actual ya satisface el contrato sin fallas, se entrega evidencia de no cambio en producción (`src/main/resources/static/`).
4. Habilitación de auditoría independiente de Fase 3 (OpenCode / Nemotron) y dictamen arquitectónico final de Fase 4 (Codex).

---

## 2. Alcance Cerrado y Reglas de Modificación

### 2.1. Archivos permitidos de producción (Solo simulador)
- `src/main/resources/static/index.html` (únicamente elementos `#open`, `#sim` y dependencias del simulador; sin alterar la vidriera, el zoom ni controles de voz del comprador).
- `src/main/resources/static/style.css` (únicamente selectores `.evaluation`, `#sim`, `#chat`, `.bubble`, `.examples`, `#bot-status` y diseño responsivo del simulador).
- `src/main/resources/static/app.js` (sección 9 de lógica del simulador; sin modificar la coordinación SSE, reconciliación del catálogo de vidriera ni lectura del comprador).

### 2.2. Archivos de prueba y automatización
- `src/test/java/com/norma/taller/SimulatorContractTest.java` (NUEVO: pruebas de integración HTTP MockMvc que validan los contratos REST expuestos para el simulador: `/api/products`, `/api/bot/message`, `/api/reset`, `/api/catalog/stream`, aislamiento multi-sesión, bloqueo de caracteres ilegales y rechazo de fotos corruptas).

### 2.3. Archivos de reporte y evidencias
- `docs/orchestration/issue-7-reconciliation-design-v2.md` (este documento).
- `docs/orchestration/issue-7-reconciliation-build.md` (reporte completo de construcción, ejecución de navegador y suite).
- `docs/orchestration/issue-7-reconciliation-evidence/builder/` (capturas de pantalla, transcripciones de consola, logs de pruebas y artefactos de ejecución).

### 2.4. Componentes terminantemente prohibidos de modificar
- `src/main/java/com/norma/taller/Api.java`
- `src/main/java/com/norma/taller/MediaService.java`
- `src/main/java/com/norma/taller/PhotoProcessor.java`
- `src/main/java/com/norma/taller/BotEngine.java` / `IntentParser.java` / `Catalog.java`
- Esquemas SQL, migraciones y `schema.sql`
- `pom.xml` y dependencias
- `src/main/resources/application.properties`
- Archivos estáticos en `src/main/resources/static/assets/`
- Documentos globales de gobernanza (`AGENTS.md`, `README.md`, etc.)

---

## 3. Contrato de Interacción y Comportamiento del Simulador (Codex v2)

### Cláusula 1: Despliegue en Entorno Real Aislado
- El servidor debe compilar y ejecutarse mediante el JAR empaquetado (`taller-1.0.0.jar`) con Microsoft OpenJDK 25, utilizando una base de datos SQLite temporal aislada (`file:...`) y un directorio de medios temporal, en un puerto dinámico efímero.
- Cero dependencias de credenciales externas (sin tokens de WhatsApp, Meta Graph API ni servicios en la nube).

### Cláusula 2: Accesibilidad de Teclado y Ciclo de Foco
- El botón disparador `#open` ("Modo evaluación: conversar con Norma") debe ser operable mediante teclado (`Enter` o `Espacio`).
- Al invocar `showModal()` sobre el diálogo `#sim`, el diálogo debe capturar la interacción sin desbordar el viewport (soporta layout responsivo de dos columnas en pantallas ≥1100px o overlay modal en pantallas móviles).
- El diálogo debe poder cerrarse mediante teclado:
  1. Activando el botón `#close` ("Volver al catálogo").
  2. Presionando la tecla `Escape`.
- En ambos casos de cierre, el foco del navegador debe regresar inmediatamente al elemento disparador `#open`.

### Cláusula 3: Cuatro Botones Demo y Máquina de Estados
El panel conversacional contiene 4 botones de ejemplo (`data-demo="create|price|image|sold"`). Cada uno debe demostrar:
1. **Estado Inicial**: Inspección del catálogo antes de iniciar el comando.
2. **Propuesta sin Mutación**: Al presionar el demo, se envía el comando (`POST /api/bot/message`) con texto y foto de muestra (en `create` e `image`). El asistente responde proponiendo la acción y solicitando confirmación (`AWAITING_CONFIRMATION`). El catálogo consultado vía `/api/products` no sufre ninguna mutación mientras no se confirme.
3. **Cancelar (`#no` / "No")**: Al presionar cancelar, el asistente confirma que no se realizaron cambios y el estado retorna a `IDLE`. El catálogo permanece intacto.
4. **Confirmar (`#yes` / "Sí")**: Al confirmar, el asistente ejecuta la mutación en el catálogo, emite una notificación y publica el evento SSE `catalog-update`. La vidriera y el endpoint `/api/products` reflejan la modificación de forma inmediata.
5. **Deshacer (`#undo` / "Me equivoqué")**: Tras una confirmación exitosa, invocar deshacer revierte exactamente un paso la base de datos gracias al snapshot transaccional (`catalog.restore`), retornando la pieza a su estado previo.
6. **Restaurar Catálogo (`#reset` / `POST /api/reset`)**: Al accionar el botón de reset, el catálogo del usuario actual se restaura a las 8 piezas iniciales, limpiando el chat del simulador y emitiendo `catalog-update` por SSE.

### Cláusula 4: Carga Real de Foto y Texto Multipart
- El formulario `#message` permite ingresar un texto (`#text`) y adjuntar un archivo de imagen real (`#photo`, formatos JPEG, PNG o WebP).
- El envío utiliza `multipart/form-data` con los campos `sessionId`, `text` y opcionalmente `image`.
- Al subir una foto nueva con un texto de comando (p. ej. cambio de foto de pieza o creación), la foto es procesada, reescalada y almacenada en `/media/`, requiriendo confirmación antes de asociarse definitivamente al producto.

### Cláusula 5: Honestidad en Medios Locales vs STT Real
- Los botones demo reproducen archivos de audio precargados (`/assets/demo-create.mp3`, etc.) para simular la experiencia auditiva de Norma.
- El contrato establece de forma transparente que dicha reproducción local junto con el texto inyectado en la petición **no** constituye transcripción STT en tiempo real por el navegador (el cual fue probado y auditado exhaustivamente en la Issue #5).

### Cláusula 6: Aislamiento Estricto entre Sesiones
- Cada navegador o pestaña opera con un token de sesión independiente (`norma-session`, formato `[a-zA-Z0-9_-]{8,80}`, no `DEFAULT`).
- Las modificaciones de catálogo, creación de piezas, ventas, cancelaciones y resets efectuados en una Sesión A no alteran bajo ninguna circunstancia el catálogo ni el estado conversacional de una Sesión B.

### Cláusula 7: Resiliencia, Bloqueo de Controles y Casos Borde
1. **Bloqueo de controles en vuelo**: Mientras una petición HTTP esté en curso (`busy = true`), todos los botones de acción del diálogo `#sim` permanecen deshabilitados (`disabled = true`) y los eventos de teclado están blindados para impedir solicitudes concurrentes o envíos dobles.
2. **Foto inválida o corrupta**: Si se envía un archivo no gráfico o corrupto como imagen, el servidor retorna HTTP 400 con mensaje de error; el simulador captura la excepción, presenta el mensaje amigable en el chat y `#bot-status`, y el catálogo no sufre alteración.
3. **Manejo de red o error de servidor**: Si el backend no responde o arroja error, el simulador muestra el mensaje de falla sin bloquear permanentemente la interfaz y permitiendo reintentar.
4. **Micrófono ausente o denegado**: Si `getUserMedia` falla o el usuario deniega el permiso, `#bot-status` muestra un mensaje explicativo accesible y la interfaz detiene y limpia los tracks y temporizadores de grabación.
5. **Degradación de voz**: Si el navegador no soporta síntesis de voz o falla la reproducción de audio, el texto de la respuesta permanece íntegramente visible en el registro `#chat`.

---

## 4. Plan de Verificación de Fase 2 (Definition of Done)

1. **Pruebas Automatizadas**:
   - Creación de `src/test/java/com/norma/taller/SimulatorContractTest.java` cubriendo los contratos HTTP del simulador (creación, edición de precio, cambio de foto con multipart real, venta, confirmación, cancelación, undo, reset, aislamiento de sesiones y manejo de errores 400).
   - Ejecución de `mvn -B clean verify` bajo Microsoft OpenJDK 25.
2. **Pruebas en Navegador Real con orca-cli**:
   - Lanzamiento de la aplicación en puerto dinámico con SQLite y directorio temporal.
   - Navegación e interacción real con el navegador integrado de Orca (`orca tab`, `orca click`, `orca fill`, `orca screenshot`).
   - Evidencia gráfica y tabular de cada paso del contrato (foco por teclado, cuatro demos, cancelación, confirmación, undo, reset, aislamiento multi-sesión).
3. **Dictamen de Código de Producción**:
   - Inspección del código actual en `src/main/resources/static/`. Si cumple al 100% las cláusulas del contrato, mantener la regla de no-cambio justificado. En caso de encontrar un defecto específico, aplicar la corrección mínima y atómica.
4. **Reporte de Construcción**:
   - Elaboración de `docs/orchestration/issue-7-reconciliation-build.md` con todos los comandos reproducibles, hashes, logs de salida y tabla de trazabilidad.
