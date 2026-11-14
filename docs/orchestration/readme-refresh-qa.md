# Informe de Auditoría QA Documental — README Refresh (Fase 3)

**Agente auditor:** OpenCode / Nemotron (QA & Test Auditor, rol Fase 3 per AGENTS.md y `.agents/skills/qa-auditor/SKILL.md`)  
**Terminal:** term_173955d0-9dd8-4c40-bd57-29330be49f2e  
**Task:** task_8d64f3b15887  
**SHA auditado (HEAD README):** `ef3712ea54cb82dc50dbeaad4470e08b137dbc89`  
**SHA base aprobada (diseño Codex v1):** `32118e69f4588690e530ce22c8eb2d8a3b456775`  
**Diseño de referencia:** `docs/orchestration/readme-refresh-design.md` (Codex v1)  
**Informe de entrega Fase 2 (Antigravity):** `docs/orchestration/readme-refresh-build.md`  
**Alcance de verificación:** Documental únicamente (README.md y los 3 docs autorizados). No se ejecutó la suite de 254 pruebas ni build de Docker por ser solo docs.  
**Dictamen:** `QA_REJECTED`

---

## 1. Entorno y comandos ejecutados

- JDK 25: `/home/gonzalocar/.jdks/ms-25.0.4.1` (JAVA_HOME configurado). `java -version` reporta 25.
- Maven: `mvn -version` reporta Maven 3.8.7+ sobre JDK 25.
- Revisión estática de código/config reales: `src/main/java/com/norma/taller/Api.java`, `PhotoProcessor.java`, `BotEngine.java`, `IntentParser.java`, `BotModel.java`, `src/main/resources/application.properties`, `compose.yaml`, `Dockerfile`, `src/main/resources/static/{index.html,style.css,app.js}`.
- Resolución de enlaces relativos en README contra árbol de archivos: `docs/decisiones.md`, `docs/operacion.md`, `docs/medios.md`, `docs/backlog.json`, `docs/orchestration/readme-refresh-design.md` — todos existen.
- `git status --short`: limpio antes de la intervención de QA (solo el informe de QA se añade al commit).
- No se ejecutó `mvn clean verify` ni `docker compose build`: validación proporcionada documental, sin cambio ejecutable que lo requiera.

---

## 2. Matriz de verificación por criterio

| # | Criterio (input del requerimiento) | Resultado | Evidencia / observación |
|---|---|---|---|
| 1 | Comando JDK 25 (`java -version`, `JAVA_HOME=/ruta/a/jdk-25`) | COHERENTE | README L119-129; JDK local `/home/gonzalocar/.jdks/ms-25.0.4.1` |
| 2 | Comando Maven (`mvn -version`, `mvn -B clean verify`) | COHERENTE | README L117, L133; aplicación.properties |
| 3 | JAR empaquetado `target/taller-1.0.0.jar` | COHERENTE | README L138; Dockerfile L11 copia el JAR |
| 4 | Docker Compose `docker compose up --build -d` | COHERENTE | README L75; compose.yaml L1 `build: .` |
| 5 | Healthcheck `/api/ping` y logs | COHERENTE | README L82-88; compose.yaml L11-16; Api.java L23 |
| 6 | Puerto alternativo `PORT=18080` (solo hostmapping, interior 8080) | COHERENTE | README L94-99; compose.yaml L5 `"${PORT:-8080}:8080"`; Dockerfile L14 `EXPOSE 8080` |
| 7 | `docker compose down` conserva volumen; `down -v` destruye `norma-data` | COHERENTE | README L103-110 (advertencia); compose.yaml L9-10 |
| 8 | Persistencia local cwd (`norma.db`, `uploads/`) | COHERENTE | README L147; application.properties L15, L35 |
| 9 | `GROQ_API_KEY` sin carga automática de `.env` en `java -jar` | COHERENTE | README L148-152; application.properties L38 `${GROQ_API_KEY:}` |
| 10 | TTS `edge-tts` opcional en local, incluido en Docker | COHERENTE | README L51, L146; Dockerfile L7 instala edge-tts |
| 11 | STT requiere `GROQ_API_KEY` (Whisper Groq) | COHERENTE | README L49; MediaService (sin clave → micrófono informa) |
| 12 | Demos de audio sin STT (MP3 estático + texto fijo) | COHERENTE | README L54; MediaServiceTest audio estático |
| 13 | Sesiones por origen (`localStorage norma-session`) | COHERENTE | README L38, L199; app.js L9, L12 |
| 14 | FSM 4 ops (`CREATE`, `PRICE`, `IMAGE`, `SOLD`) | COHERENTE | README L23-27; BotModel.java L25 |
| 15 | Confirmar / cancelar / undo / reset | COHERENTE | README L28-33; BotEngine.java |
| 16 | SQLite WAL, pool=1, busy_timeout=5000, synchronous=NORMAL | COHERENTE | README L36; application.properties L19-22 |
| 17 | Imágenes 800x600, recorte 4:3, fondo #FFFFFF, <80000 bytes | COHERENTE | README L45; PhotoProcessor.java L29-31 |
| 18 | Límite subida 15 MiB | COHERENTE | README L43; PhotoProcessor.java L25; application.properties L28 |
| 19 | Eje 16384 px y 24 MP totales | COHERENTE | README L43; PhotoProcessor.java L26-27 |
| 20 | Accesibilidad: 22 px base, 28.6 px botón, teclado/foco, enlace salto, Web Speech fallback | COHERENTE (con nota menor) | README L14-19; style.css L2, L10, L58-59; index.html L12; app.js L44-93 |
| 21 | Enlaces relativos resuelven | COHERENTE | Todos los docs destino existen |
| 22 | CI Run #43547782533 vs SHA 32118e6 y 254 pruebas como evidencia histórica | COHERENTE (histórico, no re-ejecutado) | README L210-212; no se re-ejecutó la suite |
| 23 | No prometer hosting / seguridad admin / WhatsApp / AAA / SLA | COHERENTE | README L56-61 |
| 24 | No credenciales en ejemplos | COHERENTE | README L150 (`gsk_...` placeholder) |
| 25 | `http://localhost` sin TLS válido para desarrollo local | COHERENTE | README L88, L92, L143; honesto sobre proxy TLS en producción (L60) |

---

## 3. Defectos detectados (bloqueantes)

### Defecto D1 — Endpoint SSE incorrecto
- **Severidad:** Alta
- **Reproducción:** Leer README L13: `GET /api/events`. Leer `src/main/java/com/norma/taller/Api.java:53`: `@GetMapping(value="/api/catalog/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)`. Leer `src/main/resources/static/app.js:398`: `new EventSource('/api/catalog/stream' + query)`.
- **Resultado esperado:** README debe documentar `GET /api/catalog/stream`.
- **Resultado observado:** README documenta `GET /api/events`, que no existe en el código.
- **Acción requerida:** Antigravity corrige README a `/api/catalog/stream`.

### Defecto D2 — CREATE declara imagen opcional, el código la exige
- **Severidad:** Alta
- **Reproducción:** Leer README L24: `CREATE: Alta de una nueva pieza asignando título, precio e imagen opcional`. Leer `src/main/java/com/norma/taller/IntentParser.java:480`: `case CREATE -> (title != null && price != null && imageUrl != null)`. Leer `src/main/java/com/norma/taller/BotEngine.java:772`: exige `imageUrl` para CREATE.
- **Resultado esperado:** README debe declarar imagen requerida para CREATE (o quitar "opcional").
- **Resultado observado:** README contradice el contrato real del FSM.
- **Acción requerida:** Antigravity corrige README.

### Defecto D3 — PNG8 describe "sin canal alfa", el código admite alfa y lo composa
- **Severidad:** Media
- **Reproducción:** Leer README L42: `PNG8 (PNG indexado sin canal alfa ni paletas incompatibles)`. Leer `PhotoProcessor.java:418-422` (permite `componentSize <= 8`, incluye alfa) y `494-517` (composa alfa sobre `#FFFFFF`).
- **Resultado esperado:** README debe declarar que PNG con 8 bits/canal incluyendo alfa es aceptado y compuesta sobre fondo blanco.
- **Resultado observado:** README niega soporte de alfa que el código sí tiene.
- **Acción requerida:** Antigravity corrige README.

### Defecto D4 — Rechazo de perfil ICC presentado como generalizado (entrada), código solo lo rechaza en salida WebP
- **Severidad:** Media
- **Reproducción:** Leer README L44: rechaza `perfiles de color ICC incrustados` (implica entrada). Leer `PhotoProcessor.java:597-599` (`verifyWebPCandidate` rechaza ICCP solo en el candidato WebP de salida). No hay rechazo explícito de ICC en archivos de entrada (`analyzeContainer`/`readAndValidateSource` no inspeccionan ICC de entrada; solo EXIF > 256 KiB).
- **Resultado esperado:** README debe precisar que el rechazo de ICCP aplica al WebP generado (salida), no como política general de entrada.
- **Resultado observado:** README generaliza una política que es de salida.
- **Acción requerida:** Antigravity corrige README.

### Nota menor (no bloqueante) — Anillo de foco
- README L17 describe `outline: 3px solid #7d4420`; código `style.css:58-59` usa `outline: 4px solid #004b75`. El concepto (anillo de foco visible) es correcto; solo difieren valores exactos. Se deja como observación de precisión.

---

## 4. Limitaciones

- Validación documental únicamente; no se ejecutó `mvn -B clean verify` ni la suite de 254 pruebas (no hay cambio ejecutable que lo requiera y el requerimiento lo prohíbe para solo docs).
- No se construyó ni ejecutó el contenedor Docker (solo comparación estática contra `compose.yaml` y `Dockerfile`).
- Los enlaces externos a GitHub Actions (Run #43547782533) y commit SHA `32118e6` se citan como evidencia histórica declarada en el README; no se pudieron re-validar remotamente desde este worktree aislado (no se accedió a rutas externas).
- La verificación de accesibilidad (WCAG, lectores de pantalla) no se ejecutó; el README declara honestamente que no certifica WCAG AAA universal.

---

## 5. Dictamen

`QA_REJECTED`.

El README actualizado (`ef3712e`) presenta cuatro defectos técnicos contra el código real (D1–D4) que deben ser corregidos por Antigravity antes de que este informe pueda pasar a QA_APPROVED. Conforme al protocolo, **no se modificó el README, ni código de producción, ni configuración, ni AGENTS.md**; los defectos se reportan a Antigravity para corrección. Tras la corrección, el informe deberá ser reauditado sobre el nuevo SHA y, si todos los criterios se cumplen, emitirse QA_APPROVED para habilitar la Fase 4 de Codex.

**Archivo entregado:** `docs/orchestration/readme-refresh-qa.md` (único archivo del commit).
