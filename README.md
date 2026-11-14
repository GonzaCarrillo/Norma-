# El Taller de Norma

Catálogo artesanal accesible y simulador conversacional en tiempo real sin credenciales obligatorias. Construido sobre Java 25, Spring Boot 3.5, JDBC con SQLite WAL, Server-Sent Events (SSE) y frontend nativo en HTML5, CSS y JavaScript moderno.

---

## ¿Qué hace El Taller de Norma?

La aplicación resuelve de punta a punta la exhibición accesible y la administración conversacional asistida de piezas de cerámica y alfarería artesanal:

1. **Vidriera pública accesible en tiempo real**:
   - **Catálogo base**: Presenta una colección inicial de 8 piezas artesanales catalogadas, cada una con su número de pieza estable, título, precio en pesos argentinos enteros y fotografía en alta fidelidad.
   - **Sincronización en vivo (SSE)**: La vidriera se suscribe automáticamente a un canal de Server-Sent Events (`GET /api/catalog/stream`); ante cualquier modificación confirmada por el asistente, las tarjetas de piezas se agregan, actualizan o remueven de manera reactiva ante cada confirmación sin recargar la página.
   - **Diseño pensado para accesibilidad**:
     - Tipografía base legible de **22 px** (`font-size: 22px`) en todo el cuerpo del documento.
     - Botón **«Letra más grande»** que escala la tipografía a **28.6 px** (+30%) para personas con visión reducida.
     - Navegación secuencial por teclado con anillo de foco visible (`outline: 4px solid #004b75`) y enlace de salto directo al contenido principal (`#piezas`).
     - Diálogo modal accesible de foto ampliada (**zoom manual**) para inspeccionar detalles de la pieza sin distorsión.
     - Botón **«Escuchar catálogo»** con lectura por voz en voz alta; si el navegador no cuenta con soporte nativo para Web Speech API, muestra un mensaje informativo claro en pantalla sugiriendo continuar con la lectura visual.

2. **Simulador conversacional (Asistente de WhatsApp)**:
   - Diálogo modal de evaluación que simula la interacción con Norma a través de mensajes de WhatsApp.
   - **Máquina de estados finitos (FSM)**: Gestiona 4 operaciones de catálogo estrictamente tipadas:
     - `CREATE`: Alta de una nueva pieza asignando título, precio y fotografía requerida (la imagen puede enviarse primero o tras indicar los datos; el borrador se completa y solicita confirmación únicamente al contar con los tres elementos).
     - `PRICE`: Actualización del precio de una pieza existente identificada por su número.
     - `IMAGE`: Sustitución de la fotografía de una pieza existente.
     - `SOLD`: Registro de pieza vendida (se oculta automáticamente de la vidriera pública).
   - **Flujo conversacional seguro con confirmación**:
     - Ante cualquier instrucción válida, el asistente no muta el catálogo a ciegas: genera una propuesta de borrador y pasa al estado `AWAITING_CONFIRMATION` solicitando la validación del usuario.
     - **«Sí, confirmo»**: Aplica la mutación de forma atómica a la base de datos y propaga la actualización a la vidriera vía SSE.
     - **«No, cancelar»**: Descarta el borrador y regresa al estado en reposo (`IDLE`) sin alterar los datos.
     - **«Me equivoqué: deshacer» (`undo`)**: Permite revertir la última acción confirmada, restaurando el estado previo del catálogo.
     - **«Restaurar catálogo original» (`reset`)**: Restablece las 8 piezas originales de Norma, limpiando el borrador y el historial de reversiones.

3. **Persistencia robusta y sesiones SQLite aisladas**:
   - Persistencia local en SQLite bajo modo Write-Ahead Logging (WAL) con transacciones serializadas (`maximum-pool-size=1`, `busy_timeout=5000` y `synchronous=NORMAL`).
   - **Plantilla base protegida**: La sesión `DEFAULT` contiene el catálogo fundacional y está protegida contra modificaciones o reinicios destructivos.
   - **Aislamiento por evaluador**: Cada navegador genera un identificador de sesión único (UUID) almacenado en `localStorage` (`norma-session`). Las mutaciones y borradores de un evaluador solo impactan en su propia sesión, permitiendo que diferentes evaluadores interactúen con su propio catálogo sin interferir entre sí.
   - **Numeración monótona de piezas**: El contador histórico (`next_number`) sobrevive a operaciones de reversión (`undo`) y restauración (`reset`), asegurando que cada nueva pieza creada reciba siempre un número correlativo mayor al máximo histórico emitido.

4. **Pipeline seguro de revelado y procesamiento de imágenes**:
   - **Formatos de entrada admitidos**: JPEG/JPG, PNG (hasta 8 bits por canal: RGB, escala de grises o indexado, con canal alfa compuesto sobre fondo blanco sólido) y WebP estático.
   - **Límites estrictos de seguridad**: Tamaño máximo de archivo de 15 MiB (`spring.servlet.multipart.max-file-size=15MB`), resolución máxima de 16.384 px por eje y límite total de 24 Megapíxeles ($W \times H \le 24.000.000$).
   - **Validaciones defensivas y rechazos (HTTP 400)**: Se rechazan formatos políglotas, canales con más de 8 bits por componente, espacio de color CMYK, WebP animados (bloques `ANIM`/`ANMF` o flag `animation` en `VP8X`) y metadatos EXIF superiores a 256 KiB. En la salida, el proceso de codificación WebP suprime metadatos prescindibles y rechaza candidatos que retengan bloques de perfil de color (`ICCP`) incrustados para preservar su tamaño acotado.
   - **Revelado uniforme**: Recorte centrado proporcional 4:3, redimensionado exacto a 800x600 px sobre fondo blanco sólido `#FFFFFF` (para suprimir transparencias), corrección de contraste sRGB8 y codificación WebP lossy con calidades decrecientes (80 a 10) hasta lograr un tamaño estrictamente **menor a 80.000 bytes** (< 80 KB).
   - **Concurrencia protegida**: Semáforo estricto de 1 slot activo (`Semaphore(1)`) para evitar picos de memoria concurrentes en el procesamiento de rasters.

5. **Inteligencia Artificial y Voz (STT, LLM, TTS)**:
   - **Reconocimiento de voz (STT)**: Transcripción de audios mediante el modelo `whisper-large-v3-turbo` en Groq. Requiere configurar la variable `GROQ_API_KEY`. Si no se dispone de clave de API, el botón de micrófono informa amigablemente y permite al usuario redactar su mensaje por texto o utilizar las muestras pregrabadas.
   - **Fallback LLM opcional**: Cuando el mensaje del usuario no encaja en la gramática determinista local y la sesión se encuentra en estado `IDLE`, el sistema consulta opcionalmente al modelo `llama-3.3-70b-versatile` en Groq para extraer una intención candidata normalizada. El LLM jamás tiene autoridad para confirmar cambios ni alterar el catálogo; si no hay clave configurada o la llamada falla, el asistente responde amigablemente como `Unknown` en estado `IDLE` sin ejecutar ninguna mutación.
   - **Síntesis de voz (TTS)**: Síntesis en el servidor mediante el binario `edge-tts` empleando la voz argentina `es-AR-TomasNeural` con un timeout estricto de **12 segundos**. Si `edge-tts` no está instalado en el entorno o la generación excede los 12 segundos, el sistema efectúa un fallback transparente hacia el texto en pantalla y la síntesis nativa por Web Speech API del navegador.

6. **Honestidad técnica y procedencia de datos**:
   - **Demos de audio**: Los 4 botones de ejemplo del simulador («Escuchar ejemplo: pieza nueva», «precio», «foto nueva», «pieza vendida») reproducen un archivo `.mp3` estático local mientras envían de forma determinista la transcripción de texto predefinida; no consumen tokens ni prueban el endpoint de STT externo.
   - **Fotografías del catálogo**: Las 8 imágenes de la colección inicial provienen de fotografías de fotógrafos humanos con licencia libre en Unsplash (ver detalle en [docs/medios.md](docs/medios.md)); no son obras artesanales originales elaboradas por Norma ni imágenes generadas por modelos de inteligencia artificial generativa.
   - **Límites declarados**:
     - No se certifica formalmente cumplimiento WCAG AAA universal (requiere auditorías con lectores de pantalla y hardware asistivo específico).
     - No se ofrece garantía de SLA de baja latencia ni concurrencia masiva.
     - Es un sandbox de demostración: no incluye autenticación administrativa de usuarios ni conexión activa al webhook oficial de Meta WhatsApp Business API.
     - No se garantiza un despliegue público seguro sin la interposición de un proxy inverso TLS (HTTPS) y cuotas de servicio configuradas por el operador.
     - No se garantizan compilaciones bit-a-bit idénticas entre plataformas disímiles.

---

## Arranque rápido reproducible (Quickstart)

Podés levantar la aplicación de forma inmediata mediante contenedores Docker (método recomendado) o de manera directa en tu entorno local con Java 25.

### Opción 1: Ejecución con Docker (Recomendada)

**Requisitos**: Docker Engine y Docker Compose v2 (`docker compose`).

1. **Construir y levantar el contenedor en segundo plano**:
   ```bash
   docker compose up --build -d
   ```
   *La imagen multi-etapa compila el proyecto bajo Maven y JDK 25, y genera un runtime liviano sobre Ubuntu 24.04 LTS con JRE 25, `libwebp7`, usuario no-root `norma` (UID 10001 / GID observado 999) y un entorno virtual con `edge-tts` preinstalado.*

2. **Verificar el estado del contenedor, los logs y el sondeo de salud**:
   ```bash
   # Comprobar que el servicio esté activo y el healthcheck pase a healthy
   docker compose ps

   # Ver los logs de arranque de la aplicación
   docker compose logs -f taller

   # Validar la respuesta HTTP del endpoint de comprobación
   curl http://localhost:8080/api/ping
   ```

3. **Abrir en el navegador**:
   Accedé a [http://localhost:8080](http://localhost:8080).

4. **Cambio parametrizado de puerto TCP**:
   Si deseás utilizar otro puerto en el anfitrión (por ejemplo, el `18080`):
   ```bash
   PORT=18080 docker compose up --build -d
   curl http://localhost:18080/api/ping
   ```

5. **Detener el servicio y consideraciones de persistencia**:
   ```bash
   docker compose down
   ```
   > [!NOTE]
   > `docker compose down` detiene y remueve los contenedores **preservando íntegro el volumen persistente** `norma-data`. Al volver a iniciar el servicio con `docker compose up -d`, la base de datos `norma.db` y las fotos subidas se conservarán intactas.

   > [!WARNING]
   > Si ejecutás `docker compose down -v`, Docker eliminará de forma irreversible el volumen nombrado `norma-data`, borrando permanentemente la base de datos SQLite y todos los archivos almacenados en `uploads/`.

---

### Opción 2: Ejecución local (JAR empaquetado)

**Requisitos**:
- **JDK 25**: Java Development Kit 25 (por ejemplo Eclipse Temurin 25 u OpenJDK 25).
- **Maven**: 3.6.3 o superior (probado con Maven 3.8.7+).

1. **Configurar las variables de entorno de Java 25**:
   Asegurate de que `JAVA_HOME` apunte al directorio base de instalación de tu JDK 25 genérico:
   ```bash
   export JAVA_HOME=/ruta/a/jdk-25   # Ajustar a la ruta real de tu JDK 25 en el sistema
   export PATH="$JAVA_HOME/bin:$PATH"
   ```
   Verificá que ambas herramientas reporten Java 25:
   ```bash
   java -version
   mvn -version
   ```

2. **Compilar y empaquetar el proyecto**:
   ```bash
   mvn -B clean verify
   ```

3. **Ejecutar el JAR empaquetado**:
   ```bash
   java --enable-native-access=ALL-UNNAMED -jar target/taller-1.0.0.jar
   ```
   *(El flag `--enable-native-access=ALL-UNNAMED` habilita explícitamente el acceso nativo JNI para `sqlite-jdbc` en JDK 25, suprimiendo las advertencias del runtime).*

4. **Acceder a la aplicación**:
   Abrí [http://localhost:8080](http://localhost:8080) en tu navegador o comprobá el endpoint con `curl http://localhost:8080/api/ping`.

5. **Soporte de voz TTS en local y persistencia**:
   - `edge-tts` es opcional en ejecución local: si tenés instalado `edge-tts` en tu entorno (`pip install edge-tts==7.3.1`) y accesible en el `PATH`, la síntesis de voz en el servidor generará los audios `.mp3`; si no está instalado, la aplicación continuará funcionando normalmente mediante la síntesis nativa por Web Speech API del navegador y el texto en pantalla.
   - En ejecución local, el archivo de base de datos `norma.db` y la carpeta de imágenes `uploads/` se crean en el directorio de trabajo actual (`pwd`/cwd). No reutilices bases temporales de testing de QA.
   - **Gestión segura de secretos**: Para habilitar las capacidades de Groq (STT y LLM), exportá tu credencial en la terminal:
     ```bash
     export GROQ_API_KEY="gsk_..."
     ```
     *Nota sobre archivos `.env`*: La ejecución con `java -jar` **no carga automáticamente archivos `.env`**. Nunca guardes credenciales sensibles en archivos `.env` sin exportarlas de forma segura y jamás versiones secretos en Git.

---

## Configuración externa y resolución de propiedades

La aplicación utiliza la infraestructura de configuración nativa de Spring Boot. Las propiedades disponibles y sus variables asociadas son:

| Propiedad Spring | Variable / Alias | Predeterminado | Descripción |
|---|---|---|---|
| `server.port` | `PORT` (alias) / `SERVER_PORT` (nativo) | `8080` | Puerto TCP de escucha HTTP. |
| `spring.datasource.url` | `NORMA_DB` (alias ruta) / `SPRING_DATASOURCE_URL` (nativo) | `norma.db` | Ruta local SQLite (placeholder compone `jdbc:sqlite:<ruta>`). |
| `norma.uploads` | `NORMA_UPLOADS` (nativo/directo) | `uploads` | Directorio local escribible para imágenes y archivos subidos. |
| `groq.api.key` | `GROQ_API_KEY` (nativo/directo) | *(vacío)* | Clave opcional de API de Groq para STT y LLM fallback. No es requerida para el arranque base ni para `GET /api/ping`. |

### Precedencia de configuración y resolución de propiedades

Para una **misma propiedad efectiva**, el orden de precedencia estándar de Spring Boot (de mayor a menor prioridad) es:
1. **Argumentos de línea de comandos (CLI)** (ej. `--server.port=18083`).
2. **Variables de entorno del sistema** (ej. variables nativas relajadas como `SERVER_PORT=18084` o variables directas como `NORMA_UPLOADS`).
3. **Archivo externo de propiedades** especificado mediante `--spring.config.additional-location=file:...` (ej. `server.port=18082`).
4. **Archivo empaquetado en la aplicación** (`src/main/resources/application.properties`).

#### Distinción entre variables nativas y aliases por placeholder
- `PORT` no es la variable nativa de Spring para `server.port`, sino un **alias** consumido a través del placeholder empaquetado `server.port=${PORT:8080}`. Si un archivo externo define `server.port=18082`, dicho valor reemplaza por completo la propiedad empaquetada que contenía el placeholder, por lo que definir `PORT=18081` no alterará el puerto si se utiliza dicho archivo externo.
- En cambio, la variable de entorno nativa de Spring `SERVER_PORT=18084` vincula directamente sobre la propiedad `server.port` y prevalece sobre el `server.port=18082` del archivo externo.
- A su vez, el argumento CLI `--server.port=18083` prevalece sobre ambos (entorno y archivo externo).
- De manera análoga, `NORMA_DB` es un alias por placeholder que compone la URL SQLite empaquetada (`jdbc:sqlite:${NORMA_DB:norma.db}`); la variable nativa `SPRING_DATASOURCE_URL` contiene la URL JDBC completa y prevalece sobre el archivo externo. En cambio, `NORMA_UPLOADS` y `GROQ_API_KEY` vinculan directamente sobre sus respectivas propiedades Spring mediante las reglas de nombres relajados de entorno.

*Referencia técnica*: [Spring Boot External Application Configuration](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html).

---

## Guía breve de uso (Paso a paso)

Para evaluar todas las capacidades del sistema en pocos minutos:

1. **Explorar la vidriera**: Abrí [http://localhost:8080](http://localhost:8080). Observá las 8 piezas iniciales, probá la navegación accesible por teclado (Tab / Shift+Tab), aumentá el tamaño de letra con **«Letra más grande»** y probá la lectura en voz alta con **«Escuchar catálogo»**.
2. **Abrir el Modo evaluación**: Hacé clic en el botón inferior **«Modo evaluación: conversar con Norma»**. Se desplegará el simulador de WhatsApp.
3. **Probar una muestra predefinida**: En la botonera de ejemplos, seleccioná **«Escuchar ejemplo: pieza nueva»**. Se reproducirá la pista de audio y el simulador cargará la foto de prueba y el texto correspondiente.
4. **Evaluar la propuesta de borrador**: El asistente responderá indicando qué pieza creará y su precio, quedando en estado de espera de confirmación (`AWAITING_CONFIRMATION`).
5. **Confirmar la acción**: Hacé clic en **«Sí, confirmo»**. El asistente confirmará el alta y verás cómo en la vidriera de fondo aparece automáticamente la pieza `#9` vía SSE en tiempo real.
6. **Modificar precio o foto**: Escribí en el campo de texto: `A la número 2 subila a veinte mil pesos` y presioná **«Enviar mensaje»**. Ante la propuesta, confirmá con **«Sí, confirmo»**.
7. **Revertir cambios**: Si cometiste una equivocación, hacé clic en **«Me equivoqué: deshacer»**. El último cambio confirmado se anulará inmediatamente.
8. **Restaurar el catálogo original**: Para regresar el catálogo a su estado de fábrica de 8 piezas, hacé clic en **«Restaurar catálogo original»**. El catálogo volverá a sus piezas iniciales, pero la numeración histórica continuará creciendo monótonamente en futuras creaciones.

> [!IMPORTANT]
> **Comportamiento multi-pestaña**: Dos pestañas abiertas en el mismo navegador hacia el mismo origen (ej. `http://localhost:8080`) comparten el `localStorage` (`norma-session`) y por lo tanto operan sobre la misma sesión. Ambas pestañas verán y sincronizarán el mismo catálogo en tiempo real. Para probar dos catálogos independientes y aislados en simultáneo, abrí una pestaña en modo incógnito o utilizá un navegador diferente.

---

## Pruebas automatizadas e Integración Continua (CI)

- **Suite de pruebas completa**: El repositorio cuenta con una batería de **254 pruebas automatizadas** (JUnit 5, MockMvc y Spring Boot Test) que validan los controladores HTTP, los contratos de error de `ApiErrors`, las transacciones y FSM de `BotEngine`, la persistencia y migración v2 de SQLite WAL, y el pipeline de revelado WebP de `PhotoProcessor`.
  Para ejecutar la suite completa en local:
  ```bash
  mvn -B clean verify
  ```
- **Integración Continua (CI)**: Cada commit y Pull Request es verificado automáticamente en GitHub Actions bajo JDK 25 y Docker multi-etapa.
  - **Ejecución exitosa verificada**: [GitHub Actions Run #43547782533](https://github.com/GonzaCarrillo/Norma-/actions/runs/43547782533) sobre el commit SHA [`32118e6`](https://github.com/GonzaCarrillo/Norma-/commit/32118e69f4588690e530ce22c8eb2d8a3b456775) (13 de noviembre de 2026).
  - *Nota metodológica*: La cifra de 254 pruebas certifica la evidencia verificada para el commit `32118e6`. No se deben fijar resultados futuros a un conteo histórico estático, ya que nuevas pruebas o refactorizaciones legítimas pueden alterar dicho número en versiones posteriores.

---

## Documentación técnica y trazabilidad

- [docs/decisiones.md](docs/decisiones.md): Registro de decisiones arquitectónicas y de diseño técnico.
- [docs/operacion.md](docs/operacion.md): Manual de operación detallado, límites de infraestructura, protocolos de backup y mitigaciones de seguridad.
- [docs/medios.md](docs/medios.md): Procedencia, licencias e inventario criptográfico (hashes SHA-256) de las fotografías y audios de demostración.
- [docs/backlog.json](docs/backlog.json): Registro y seguimiento de issues resueltas.
- [docs/orchestration/readme-refresh-design.md](docs/orchestration/readme-refresh-design.md): Contrato técnico documental aprobado para el rediseño del README.
