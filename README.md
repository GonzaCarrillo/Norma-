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

## Alcance y límites de esta entrega (Issue #1)

- Las dos especificaciones de la raíz (`arquitectura_y_especificaci_n_el_taller_de_norma.md` y `especificaci_n_t_cnica_y_stack_java_25.md`) son la fuente de requisitos del sistema.
- **Base Java 25 / Monolito verificado**: Compilación estricta en bytecode 69 (Java 25), Spring Boot 3.5.16, endpoint de comprobación `GET /api/ping` (`PONG`), inicio sin claves requeridas.
- **Trazabilidad y reproducibilidad de pruebas**:
  - En el **árbol de trabajo local** con cambios previos no auditados se verificaron 6 pruebas (4 de `FlowTest` versionadas y 2 de `ApiTest` sin seguimiento), y el JAR del árbol incluyó la clase sin seguimiento `IntentFallback`.
  - En una **exportación aislada y limpia** de los archivos estrictamente versionados del commit (`git archive`) se ejecutaron y aprobaron las 4 pruebas de `FlowTest` versionadas (`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`), sin clases sin seguimiento en su JAR, generando bytecode major 69 y superando los smoke tests aislados de ping, archivo externo, precedencia CLI y rechazo de puerto inválido.
- **Sandbox público**: El identificador de sesión compartida no es un mecanismo de autenticación tradicional ni provee control de acceso administrativo de producción.
- **Funcionalidades de fases futuras**: Las capacidades de catálogo (#2), FSM conversacional (#3), procesamiento de imágenes (#4), transcripción y audio (#5), interfaz web SSE (#6), simulador sin credenciales (#7) y Docker / despliegue (#8) corresponden a issues posteriores y no forman parte de las garantías de esta issue #1. No se garantizan tiempos de respuesta universales ni cold start de 0 ms sin infraestructura dedicada.

Documentación de arquitectura y decisiones: [docs/decisiones.md](docs/decisiones.md).
Backlog de seguimiento: [docs/backlog.json](docs/backlog.json).
Operación y limitaciones: [docs/operacion.md](docs/operacion.md).
