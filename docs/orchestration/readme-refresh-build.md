# Informe de Construcción y Entrega Documental — README Refresh

**Fecha**: 2026-11-14  
**Rol**: Antigravity — Software Builder / Implementer (Fase 2)  
**Base inspeccionada**: `origin/main` (`32118e69f4588690e530ce22c8eb2d8a3b456775`), rama `GonzaCarrillo/docs-readme-completed`.  
**Diseño de referencia**: `docs/orchestration/readme-refresh-design.md` (Codex v1).  
**Alcance estricto**: Exclusivamente `README.md`, `docs/orchestration/readme-refresh-design.md` y `docs/orchestration/readme-refresh-build.md`.

---

## 1. Resumen de la Intervención

Se implementó la actualización integral solicitada para el archivo [README.md](../../README.md), conforme a los requerimientos del usuario y al contrato técnico documental especificado por Codex.

La reestructuración prioriza de manera prominente:
1. **Qué hace la aplicación**: Explicación completa del catálogo artesanal de 8 piezas, la vidriera en tiempo real vía Server-Sent Events (SSE), la máquina de estados finitos (FSM) con las 4 operaciones (`CREATE`, `PRICE`, `IMAGE`, `SOLD`) y sus controles conversacionales («Sí, confirmo», «No, cancelar», «Me equivoqué: deshacer», «Restaurar catálogo original»), la persistencia en SQLite WAL con sesión `DEFAULT` protegida y numeración monótona de piezas, el pipeline de procesamiento WebP (< 80.000 bytes) con límites estrictos, y la integración de voz e IA (STT Groq Whisper, fallback LLM y TTS con `edge-tts` / Web Speech API).
2. **Arranque rápido reproducible (Quickstart)**: Recetas claras y reproducibles para Docker Compose v2 (con parametrización de puerto `PORT=18080` y advertencia crítica sobre `docker compose down -v`) y ejecución local con JDK 25 y Maven (utilizando rutas genéricas verificables como `export JAVA_HOME=/ruta/a/jdk-25`, sin rutas efímeras de entornos de prueba).
3. **Precedencia de configuración Spring Boot**: Mantenimiento íntegro de la tabla de propiedades y profundización técnica sobre el orden de precedencia estándar y la distinción entre variables de entorno nativas (`SERVER_PORT`, `SPRING_DATASOURCE_URL`, `NORMA_UPLOADS`, `GROQ_API_KEY`) y aliases por placeholder (`PORT`, `NORMA_DB`).
4. **Guía de uso paso a paso**: Recorrido de evaluación completo, con advertencia técnica explícita sobre el comportamiento multi-pestaña (dos pestañas hacia el mismo origen comparten la misma sesión almacenada en `localStorage`).
5. **Honestidad técnica y límites del sistema**: Declaración transparente sobre las demos de audio (audio MP3 + texto fijo, sin STT real en demos), la procedencia de las fotos (stock humano Unsplash con licencia libre, no autoría de Norma ni arte por IA, con enlace a [docs/medios.md](../medios.md)), y la abstención de prometer WCAG AAA universal, SLA de latencia/concurrencia, producción segura o builds bit-a-bit reproducibles.
6. **Trazabilidad y CI**: Declaración de la suite de 254 pruebas automatizadas con JDK 25 y enlace directo al reporte verificado en GitHub Actions [Run #43547782533](https://github.com/GonzaCarrillo/Norma-/actions/runs/43547782533) sobre el commit `32118e6`, con nota metodológica para no atar resultados futuros al conteo histórico.

---

## 2. Archivos Modificados e Incorporados

| Archivo | Estado | Descripción |
|---|---|---|
| `README.md` | Modificado | Reorganización completa priorizando qué hace, quickstart reproducible, honestidad técnica, guías operativas y enlaces de trazabilidad. |
| `docs/orchestration/readme-refresh-design.md` | Creado | Contrato técnico breve de Codex v1 para el rediseño documental. |
| `docs/orchestration/readme-refresh-build.md` | Creado | Este informe de entrega y validación de Antigravity (Fase 2). |

*Ningún archivo de código de producción Java, suite de pruebas, archivo de configuración, workflow de CI o script fue alterado.*

---

## 3. Verificaciones Ejecutadas

Conforme al criterio de aceptación («verificar diff check / links y comandos contra entorno mínimo existente proporcional, no repetir 254 tests por solo docs»):

1. **Resolución de enlaces relativos en `README.md`**:
   - `docs/decisiones.md`: Existe (`docs/decisiones.md`).
   - `docs/operacion.md`: Existe (`docs/operacion.md`).
   - `docs/medios.md`: Existe (`docs/medios.md`).
   - `docs/backlog.json`: Existe (`docs/backlog.json`).
   - `docs/orchestration/readme-refresh-design.md`: Existe (`docs/orchestration/readme-refresh-design.md`).
   - Enlace externo a GitHub Actions: `https://github.com/GonzaCarrillo/Norma-/actions/runs/43547782533` (Run verificado sobre SHA `32118e6`).
   - Enlace externo a commit SHA: `https://github.com/GonzaCarrillo/Norma-/commit/32118e69f4588690e530ce22c8eb2d8a3b456775`.

2. **Alineación de comandos y parámetros con el repositorio**:
   - Docker Compose: Se verificó la coherencia con `compose.yaml` (servicio `taller`, mapeo `${PORT:-8080}:8080`, volumen `norma-data:/data`, healthcheck sobre `/api/ping`).
   - Dockerfile: Se verificó la coherencia con `Dockerfile` (imagen base JRE 25 Noble, usuario no-root `norma` UID 10001 / GID observado 999, volumen `/data`, `edge-tts` en `/opt/voice/bin`).
   - Configuración Spring: Se verificó la coherencia con `src/main/resources/application.properties` (`server.port=${PORT:8080}`, `spring.datasource.url=jdbc:sqlite:${NORMA_DB:norma.db}`, `norma.uploads=${NORMA_UPLOADS:uploads}`, `groq.api.key=${GROQ_API_KEY:}`).
   - Flags JVM: `--enable-native-access=ALL-UNNAMED` alineado con los requerimientos de `sqlite-jdbc 3.49.1.0` en Java 25.

3. **Verificación de Diff y Scope**:
   - `git status` muestra exclusivamente los 3 archivos autorizados.
   - Sin residuos de compilación, sin variables de entorno expuestas, sin credenciales versionadas.

---

## 4. Riesgos y Limitaciones

- La síntesis de voz en servidor requiere `edge-tts` disponible en el `PATH` del entorno o la ejecución dentro del contenedor Docker provisto; fuera de estos, la aplicación utiliza de forma segura el fallback por texto y la API de síntesis nativa del navegador.
- El reconocimiento de voz mediante micrófono depende de la conectividad con la API de Groq y de una clave válida configurada en `GROQ_API_KEY`; ante su ausencia, la aplicación permite operar íntegramente mediante texto y las muestras de audio provistas.
- Las dos pestañas bajo el mismo origen comparten el `localStorage` de la sesión del navegador; para probar evaluación multi-sesión independiente en una misma máquina se debe recurrir a pestañas en modo incógnito o navegadores distintos.

---

## 5. Dictamen de Entrega

La tarea de actualización documental queda concluida con éxito en Fase 2 y lista para la auditoría de Fase 3 por OpenCode / Nemotron (`qa-auditor`) y posterior revisión de conformidad arquitectónica de Fase 4 por Codex.
