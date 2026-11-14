# El Taller de Norma

Catálogo accesible y simulador conversacional sin credenciales. Java 25, Spring Boot 3.5, JDBC/SQLite WAL, SSE y HTML/CSS/JS.

## Requisitos y entorno

- **JDK 25**: Java Development Kit 25 (por ejemplo Oracle LTS u OpenJDK 25).
- **Maven**: 3.6.3 o superior (probado con Maven 3.8.7).
- **Configuración de entorno**: `JAVA_HOME` debe apuntar a tu instalación de JDK 25; `/tmp/norma-jdk` es únicamente la ruta disponible en el entorno de trabajo actual.
  ```bash
  export JAVA_HOME=/tmp/norma-jdk   # Reemplazar por la ruta a tu instalación de JDK 25
  export PATH="$JAVA_HOME/bin:$PATH"
  ```
  Verificá que ambas herramientas informen Java 25, ya que Maven puede utilizar otro JDK por defecto en el sistema:
  ```bash
  java -version
  mvn -version
  ```

## Compilación y ejecución

### Ejecución local (JAR empaquetado)

1. **Compilar y verificar:**
   ```bash
   mvn -B verify
   ```
2. **Ejecutar el JAR empaquetado:**
   ```bash
   java --enable-native-access=ALL-UNNAMED -jar target/taller-1.0.0.jar
   ```
3. **Acceder a la aplicación:**
   Abrir http://localhost:8080 en el navegador o comprobar `GET /api/ping`.

### Ejecución en contenedor (Docker / Docker Compose)

1. **Construir y levantar el contenedor:**
   ```bash
   docker compose up --build -d
   ```
2. **Verificar estado y salud del contenedor:**
   ```bash
   docker compose ps
   curl http://localhost:8080/api/ping
   ```
   El contenedor ejecuta como usuario no-root (`norma`, UID 10001 / GID observado 999), persiste la base de datos y los medios en el volumen nombrado `norma-data:/data`, y valida salud periódica mediante healthcheck HTTP.
3. **Detener el servicio:**
   ```bash
   docker compose down
   ```

## Configuración externa

La aplicación utiliza la infraestructura nativa de configuración de Spring Boot. Las variables y propiedades de la base son:

| Propiedad Spring | Variable / Alias | Predeterminado | Descripción |
|---|---|---|---|
| `server.port` | `PORT` (alias) / `SERVER_PORT` (nativo) | `8080` | Puerto TCP de escucha HTTP. |
| `spring.datasource.url` | `NORMA_DB` (alias ruta) / `SPRING_DATASOURCE_URL` (nativo) | `norma.db` | Ruta local SQLite (placeholder compone `jdbc:sqlite:<ruta>`). |
| `norma.uploads` | `NORMA_UPLOADS` (nativo/directo) | `uploads` | Directorio local escribible para imágenes y archivos subidos. |
| `groq.api.key` | `GROQ_API_KEY` (nativo/directo) | *(vacío)* | Clave opcional de API de Groq. No requerida para arranque de base ni ping. |

### Precedencia de configuración y resolución de propiedades

Para una **misma propiedad efectiva**, el orden de precedencia estándar de Spring Boot (de mayor a menor prioridad) es:
1. **Argumentos de línea de comandos (CLI)** (ej. `--server.port=18083`).
2. **Variables de entorno del sistema** (ej. variables nativas relajadas como `SERVER_PORT=18084` o variables directas como `NORMA_UPLOADS`).
3. **Archivo externo de propiedades** especificado mediante `--spring.config.additional-location=file:...` (ej. `server.port=18082`).
4. **Archivo empaquetado en la aplicación** (`src/main/resources/application.properties`).

#### Distinción entre variables nativas y aliases por placeholder
- `PORT` no es la variable nativa de Spring para `server.port`, sino un **alias** consumido a través del placeholder empaquetado `server.port=${PORT:8080}`. Si un archivo externo define `server.port=18082`, dicho valor reemplaza por completo la propiedad empaquetada que contenía el placeholder, por lo que definir `PORT=18081` no alterará el puerto si se usa dicho archivo externo.
- En cambio, la variable de entorno nativa de Spring `SERVER_PORT=18084` vincula directamente sobre la propiedad `server.port` y prevalece sobre el `server.port=18082` del archivo externo.
- A su vez, el argumento CLI `--server.port=18083` prevalece sobre ambos (entorno y archivo externo).
- De manera análoga, `NORMA_DB` es un alias por placeholder que compone la URL SQLite empaquetada (`jdbc:sqlite:${NORMA_DB:norma.db}`); la variable nativa `SPRING_DATASOURCE_URL` contiene la URL JDBC completa y prevalece sobre el archivo externo. En cambio, `NORMA_UPLOADS` y `GROQ_API_KEY` vinculan directamente sobre sus respectivas propiedades Spring mediante las reglas de nombres relajados de entorno.

#### Ejecución sin contenedor y archivos `.env`
- La ejecución directa con Java (`java -jar ...`) **no carga automáticamente archivos `.env`**.
- Para ejecutar sin Docker, se deben exportar las variables en el entorno del proceso (ej. `export PORT=...`) o definir un archivo `.properties` externo mediante `--spring.config.additional-location=file:/ruta/absoluta/norma.properties`.
- Nunca incluir ni versionar secretos o credenciales reales en el repositorio ni en archivos de ejemplo.

*Referencia técnica*: [Spring Boot External Application Configuration](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html).

## Reglas de producto

Una sola vidriera de lectura, sin carrito, pagos ni enlaces a WhatsApp. Las modificaciones requieren confirmación; cada evaluador tiene una sesión independiente. Fotos originales, nunca arte generado por IA. Precios enteros argentinos, números de pieza estables, texto mínimo 22 px y controles accesibles.

## Alcance y límites de entrega (Monolito verificado y Despliegue reproducible — Issue #8)

- Las dos especificaciones de la raíz (`arquitectura_y_especificaci_n_el_taller_de_norma.md` y `especificaci_n_t_cnica_y_stack_java_25.md`) son la fuente de requisitos del sistema.
- **Base Java 25 / Monolito verificado**: Compilación estricta en bytecode major 69 (Java 25), Spring Boot 3.5.16, endpoint de comprobación `GET /api/ping` (`PONG`), inicio sin claves requeridas.
- **Despliegue reproducible con Docker**:
  - Contenedor multi-etapa: etapa 1 con Maven 3.9 y JDK 25 Temurin (`mvn -B verify`); etapa 2 con JRE 25 Temurin sobre Ubuntu 24.04 Noble, `libwebp7`, entorno virtual Python con `edge-tts==7.3.1`.
  - Seguridad y permisos: usuario del sistema no-root `norma` (UID 10001 / GID observado 999), volumen `/data` propiedad de `norma` conteniendo `norma.db` y `uploads/`.
  - Flags de ejecución: `--enable-native-access=ALL-UNNAMED`, `-XX:+UseCompactObjectHeaders`, `-Xmx384m`.
  - Orquestación con `compose.yaml`: mapeo de puerto configurable `${PORT:-8080}:8080`, variable `GROQ_API_KEY` opcional, volumen persistente `norma-data:/data`, healthcheck integrado mediante sondeo HTTP sobre `/api/ping`.
- **Trazabilidad y reproducibilidad**:
  - Suite automatizada integral de 254 pruebas JUnit 5 / MockMvc / Spring Boot Test con 0 fallos.
  - Verificación comprobada de persistencia SQLite y archivos de medios WebP tras reinicio y recreación de contenedores sobre el mismo volumen.
  - Aislamiento multi-sesión verificado entre sesiones de evaluación independientes.
- **Límites reales y advertencias operativas**:
  - Sandbox de demostración: no provee autenticación de usuarios administrativos ni integración con endpoints de Meta WhatsApp Business API sin aprovisionamiento de credenciales y número habilitado.
  - La síntesis de voz utiliza `edge-tts` o Web Speech API en navegador; los ejemplos tienen transcripción conocida.
  - No se garantiza un SLA universal de baja latencia ni se afirma disponibilidad de despliegue público sin infraestructura aprovisionada por el operador.

Documentación de arquitectura y decisiones: [docs/decisiones.md](docs/decisiones.md).
Backlog de seguimiento: [docs/backlog.json](docs/backlog.json).
Operación y limitaciones: [docs/operacion.md](docs/operacion.md).
