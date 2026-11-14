# Contrato Breve de Rediseño Documental de README (Codex v1)

**Fecha**: 2026-11-14  
**Rol**: Codex — Software Architect (especificación y contrato documental)  
**Base**: `origin/main` (`32118e69f4588690e530ce22c8eb2d8a3b456775`), 8 issues cerradas, CI remoto Run #43547782533 exitoso.  
**Alcance**: Exclusivamente `README.md`, `docs/orchestration/readme-refresh-design.md` y `docs/orchestration/readme-refresh-build.md`.  
**Prohibiciones**: Sin modificaciones en código de producción Java, configuración Spring, scripts de empaquetado, workflows de GitHub Actions, `AGENTS.md` ni archivos locales del IDE (`.idea`). La arquitectura del sistema no se altera.

---

## 1. Motivación y Objetivos

El usuario solicitó una actualización integral del `README.md` tras el cierre exitoso de las 8 issues del proyecto y la verificación remota de CI. El objetivo es reorganizar el documento raíz para:
1. Priorizar de forma destacada **qué hace la aplicación** y ofrecer un **quickstart reproducible e inmediato**.
2. Documentar las funcionalidades reales implementadas en el monolito: catálogo accesible con 8 piezas, vidriera en tiempo real con SSE, FSM conversacional con 4 intenciones (`CREATE`, `PRICE`, `IMAGE`, `SOLD`), sesiones SQLite persistentes aisladas por token de navegador, pipeline de revelado WebP con límites estrictos, y simulador accesible para el jurado.
3. Proporcionar recetas operativas reproducibles para Docker Compose v2 y ejecución local con JDK 25 y Maven, con variables genéricas verificables (`/ruta/a/jdk-25`), sin rutas efímeras de contenedores previos.
4. Mantener la precisión técnica de la configuración de Spring Boot (precedencia estándar y distinción entre variables nativas y aliases por placeholder).
5. Practicar **honestidad técnica absoluta**: aclarar el funcionamiento simulado de las demos de audio (audio MP3 + texto fijo, sin STT en demos), la procedencia de las fotografías (stock Unsplash humano con licencia libre, no autoría de Norma ni arte generado por IA), y abstenerse de formular promesas infundadas (no prometer WCAG AAA formal, no prometer SLA de latencia, no prometer producción segura sin proxy TLS ni hosting público preconfigurado, no prometer reproducibilidad bit-a-bit).
6. Trazabilidad rigurosa: vincular la suite de 254 pruebas y el CI verde al commit `32118e6` y a la ejecución de GitHub Actions Run #43547782533, aclarando que no se debe atar futuros resultados al conteo histórico.

---

## 2. Estructura Requerida del README.md

El nuevo `README.md` debe organizarse bajo las siguientes secciones lógicas:

1. **Encabezado y Presentación**:
   - Nombre: *El Taller de Norma*.
   - Síntesis clara: Catálogo artesanal accesible y simulador conversacional en tiempo real sin credenciales obligatorias. Stack: Java 25, Spring Boot 3.5, JDBC con SQLite WAL, SSE y frontend nativo (HTML5/CSS/JS).

2. **¿Qué hace El Taller de Norma?**:
   - **Catálogo y vidriera accesible**: 8 piezas iniciales; Server-Sent Events (SSE) para actualización instantánea; navegación secuencial por teclado con manejo estricto de foco; tipografía base mínima de 22 px (`font-size: 22px`) y botón «Letra más grande» (28.6 px); lectura por voz fallback mediante Web Speech API del navegador con mensaje alternativo si el navegador no tiene síntesis de voz; diálogo modal accesible de foto ampliada («zoom manual»).
   - **Simulador conversacional (Asistente de WhatsApp)**: diálogo de evaluación con máquina de estados finitos (FSM); 4 intenciones (`CREATE`, `PRICE`, `IMAGE`, `SOLD`); ciclo de propuesta en borrador (`AWAITING_CONFIRMATION`) con confirmación explícita («Sí, confirmo»), cancelación («No, cancelar»), reversión inmediata («Me equivoqué: deshacer», `undo`) y restauración total («Restaurar catálogo original», `reset`); 4 botones de ejemplo (demos MP3); carga de fotos y grabación por micrófono; manejo de errores amigable en español.
   - **Persistencia y sesiones SQLite**: SQLite en modo WAL (`norma.db`); sesión `DEFAULT` inmutable y protegida; cada usuario opera con un UUID propio generado en `localStorage`; contador monótono de piezas (`next_number` sobrevive a reversiones y `reset`).
   - **Procesamiento seguro de imágenes**: Formatos aceptados JPEG, PNG8 (sin canal alfa ni paletas incompatibles) y WebP estático; transformación a 800x600 px 4:3 sobre fondo blanco `#FFFFFF`, corrección sRGB8, compresión WebP en calidades decrecientes (80 a 10) garantizando tamaño estrictamente menor a 80.000 bytes (< 80 KB); límites de 15 MiB de subida, 16.384 px por eje y 24 MP totales; rechazo con 400 a políglotas, WebP animados (`ANIM`/`ANMF`/flag `animation`), perfiles ICC incrustados y metadatos EXIF > 256 KiB; semáforo de concurrencia de 1 slot activo.
   - **Voz e Inteligencia Artificial (STT, LLM, TTS)**:
     - STT: Whisper de Groq (`whisper-large-v3-turbo`) vía `GROQ_API_KEY`. Sin clave, el micrófono informa amigablemente y habilita continuar por texto.
     - Fallback LLM: Opcional, solo en estado `IDLE` cuando el parser determinista no reconoce el mensaje (`llama-3.3-70b-versatile` en Groq). Ante dudas o sin clave, responde como `Unknown` en estado `IDLE` sin mutar el catálogo ni confirmar por su cuenta.
     - TTS: `edge-tts` externo con voz `es-AR-TomasNeural` y timeout de 12 s. Si no está disponible o supera los 12 s, fallback transparente al texto visible y a la síntesis nativa por Web Speech API del cliente.
   - **Honestidad técnica y límites del sistema**:
     - Demos de audio: reproducen audio estático mientras envían texto conocido predefinido; no invocan STT real.
     - Fotografías: derivadas de fotógrafos humanos con licencia libre en Unsplash; no son piezas elaboradas por Norma ni imágenes de IA.
     - No prometer certificación WCAG AAA universal (requiere auditorías con lectores de pantalla y hardware especializado).
     - No prometer SLA de baja latencia ni alta concurrencia.
     - No prometer producción segura ni hosting público preconfigurado sin proxy TLS y autenticación administrativa previa.
     - No prometer builds bit-a-bit reproducibles.

3. **Arranque rápido reproducible (Quickstart)**:
   - **Opción 1: Docker (Recomendada)**:
     - Requisitos: Docker Engine y Docker Compose v2.
     - Comandos: `docker compose up --build -d`.
     - Verificación: `docker compose ps`, `docker compose logs -f taller`, `curl http://localhost:8080/api/ping`.
     - Navegación: `http://localhost:8080`.
     - Parametrización de puerto: `PORT=18080 docker compose up --build -d` y comprobación con `curl http://localhost:18080/api/ping`.
     - Detención: `docker compose down` (conserva el volumen `norma-data`).
     - Advertencia: `docker compose down -v` destruye el volumen `norma-data`, eliminando definitivamente la base de datos y los medios subidos.
   - **Opción 2: Ejecución local (JAR empaquetado)**:
     - Requisitos: JDK 25 y Maven >= 3.6.3.
     - Configuración genérica de `JAVA_HOME=/ruta/a/jdk-25` (no fijar `/tmp/norma-jdk`).
     - Verificación de versiones con `java -version` y `mvn -version`.
     - Compilación: `mvn -B clean verify`.
     - Ejecución: `java --enable-native-access=ALL-UNNAMED -jar target/taller-1.0.0.jar`.
     - `edge-tts` opcional en local (incluido en Docker); sin él la aplicación opera con fallback por texto y Web Speech API.
     - Persistencia local: `norma.db` y `uploads/` se crean en el directorio de trabajo actual (`cwd`). No reutilizar bases temporales de QA.
     - Secretos: exportar variables en el entorno (`export GROQ_API_KEY=...`). Advertir que `java -jar` no carga automáticamente archivos `.env`.

4. **Configuración externa y precedencia Spring**:
   - Tabla completa de propiedades (`server.port`, `spring.datasource.url`, `norma.uploads`, `groq.api.key`).
   - Orden de precedencia estándar de Spring Boot (CLI > variables de entorno > archivo externo adicional `--spring.config.additional-location` > archivo empaquetado).
   - Distinción técnica rigurosa entre aliases por placeholder (`PORT`, `NORMA_DB`) y variables nativas vinculadas (`SERVER_PORT`, `SPRING_DATASOURCE_URL`, `NORMA_UPLOADS`, `GROQ_API_KEY`).

5. **Guía breve de uso (Paso a paso)**:
   - Pasos para abrir la aplicación, evaluar el catálogo, interactuar con el simulador, emitir intenciones, confirmar borradores, deshacer cambios con «Me equivoqué» y restaurar el catálogo inicial.
   - Comportamiento multi-pestaña: dos pestañas en el mismo navegador y hacia el mismo origen comparten el `localStorage` (`norma-session`) y por ende pertenecen a la misma sesión de evaluación; para evaluar catálogos independientes se debe recurrir al modo incógnito o a un navegador diferente.

6. **Pruebas automatizadas e Integración Continua (CI)**:
   - Suite de 254 pruebas JUnit 5, MockMvc y Spring Boot Test con JDK 25.
   - Enlace directo a la ejecución verificada en GitHub Actions: [Run #43547782533](https://github.com/GonzaCarrillo/Norma-/actions/runs/43547782533) sobre SHA `32118e6`.
   - Declaración explícita de evidencia histórica fechada sin atar resultados futuros a un conteo estático.

7. **Enlaces a documentación técnica**:
   - Enlaces relativos funcionales a `docs/decisiones.md`, `docs/operacion.md`, `docs/medios.md` y `docs/backlog.json`.

---

## 3. Criterios de Aceptación (Definition of Done)

1. `README.md` redactado en español argentino claro, accesible y profesional.
2. Todos los enlaces relativos dentro del repositorio resuelven a archivos existentes válidos.
3. Los comandos de Docker y Maven son exactos y reproducibles contra el repositorio actual.
4. No se introducen dependencias, plugins, código ni configuraciones nuevas en el repositorio.
5. El informe de entrega `docs/orchestration/readme-refresh-build.md` registra las verificaciones efectuadas, el diff y las limitaciones identificadas.
