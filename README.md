# El Taller de Norma

Catálogo accesible y simulador conversacional sin credenciales. Java 25, Spring Boot 3.5, JDBC/SQLite WAL, SSE y HTML/CSS/JS.

## Requisitos y entorno

- **JDK 25**: Java Development Kit 25 (por ejemplo Oracle LTS o OpenJDK 25).
- **Maven**: 3.6.3 o superior (probado con Maven 3.8.7).
- Entorno de compilación: Configurar variables `JAVA_HOME` y `PATH`:
  ```bash
  export JAVA_HOME=/tmp/norma-jdk
  export PATH="$JAVA_HOME/bin:$PATH"
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

La aplicación utiliza la infraestructura nativa de configuración de Spring Boot. Las variables de entorno y sus valores predeterminados son:

| Variable de entorno | Propiedad Spring | Predeterminado | Descripción |
|---|---|---|---|
| `PORT` | `server.port` | `8080` | Puerto TCP de escucha HTTP. Prevalece la opción CLI `--server.port=...`. |
| `NORMA_DB` | `spring.datasource.url` | `norma.db` | Ruta local al archivo SQLite (compone `jdbc:sqlite:<ruta>`). |
| `NORMA_UPLOADS` | `norma.uploads` | `uploads` | Directorio local escribible para imágenes y archivos subidos. |
| `GROQ_API_KEY` | `groq.api.key` | *(vacío)* | Clave opcional de API de Groq para transcripción y LLM. No requerida para arranque de base ni ping. |

### Precedencia y archivo de configuración externo

1. **Argumentos de línea de comandos (CLI)**: Tienen la máxima prioridad (ej. `--server.port=18083`).
2. **Archivo externo de propiedades**: Puede especificarse mediante:
   ```bash
   java --enable-native-access=ALL-UNNAMED -jar target/taller-1.0.0.jar --spring.config.additional-location=file:/ruta/absoluta/norma.properties
   ```
3. **Variables de entorno**: (ej. `PORT`, `NORMA_DB`, `NORMA_UPLOADS`, `GROQ_API_KEY`).
4. **Valores empaquetados**: Definidos en `src/main/resources/application.properties`.

*Referencia técnica*: [Spring Boot External Application Configuration](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html).

## Reglas de producto

Una sola vidriera de lectura, sin carrito, pagos ni enlaces a WhatsApp. Las modificaciones requieren confirmación; cada evaluador tiene una sesión independiente. Fotos originales, nunca arte generado por IA. Precios enteros argentinos, números de pieza estables, texto mínimo 22 px y controles accesibles.

## Alcance y límites de esta entrega (Issue #1)

- Las dos especificaciones de la raíz (`arquitectura_y_especificaci_n_el_taller_de_norma.md` y `especificaci_n_t_cnica_y_stack_java_25.md`) son la fuente de requisitos del sistema.
- **Base Java 25 / Monolito verificado**: Compilación estricta en bytecode 69 (Java 25), Spring Boot 3.5.16, endpoint de comprobación `GET /api/ping` (`PONG`), inicio sin claves requeridas.
- **Sandbox público**: El identificador de sesión compartida no es un mecanismo de autenticación tradicional ni provee control de acceso administrativo de producción.
- **Funcionalidades de fases futuras**: Las capacidades de catálogo, FSM conversacional, transcripción de voz con Groq, síntesis TTS argentina, procesamiento SSE, Docker y despliegue público corresponden a las issues #2–#8 y no forman parte de las garantías auditadas de esta issue #1. No se garantizan tiempos de respuesta universales ni cold start de 0 ms sin infraestructura dedicada.

Documentación de arquitectura y decisiones: [docs/decisiones.md](docs/decisiones.md).
Backlog de seguimiento: [docs/backlog.json](docs/backlog.json).
Operación y limitaciones: [docs/operacion.md](docs/operacion.md).
