# Operación y entrega
## Inicio local
JDK 25 y Maven: `mvn verify`, `java --enable-native-access=ALL-UNNAMED -jar target/taller-1.0.0.jar`.
Abrir http://localhost:8080. El catálogo y los borradores sobreviven a reinicios.

## Contenedor
`docker compose up --build -d`. Comprobar `curl http://localhost:8080/api/ping`.
El volumen norma-data contiene SQLite y medios. No ejecutar varias réplicas sobre el mismo archivo.
Para producción, usar servidor siempre encendido, volumen persistente y proxy TLS. El micrófono exige HTTPS o localhost.
Variables: PORT (8080), NORMA_DB (norma.db), NORMA_UPLOADS (uploads), GROQ_API_KEY (opcional).
La imagen instala edge-tts. Fuera de Docker: instalarlo en un entorno virtual y agregar su binario al PATH.
No subir claves a Git.

## Prueba del jurado
Abrir Modo evaluación. Elegir ejemplo de alta y confirmar: aparece #9. Cambiar precio, foto o marcar vendida; confirmar cada uno. “Me equivoqué” revierte el último cambio una sola vez. “Restaurar catálogo original” limpia borrador e historial. Otra sesión de navegador tiene catálogo independiente.
Los ejemplos tienen transcripción conocida: no prueban STT externo. Los audios son síntesis de demostración, no grabaciones reales de Norma.
La voz del asistente intenta es-AR-TomasNeural; si falla, se muestra texto y se ofrece síntesis del navegador. No se garantiza voz argentina si el dispositivo no tiene esa voz.
Sin GROQ_API_KEY, el micrófono devuelve un aviso y permite seguir por texto.
## Límites pendientes para uso real
El simulador no envía ni recibe mensajes de WhatsApp. Un webhook de Meta requiere cuenta, teléfono, verificación de firmas, autorización del emisor y credenciales no suministradas. No exponer este sandbox como administración real.
Las sesiones son capacidades aleatorias, no cuentas autenticadas. Una instalación pública necesita límites de tráfico, limpieza programada de sesiones y medios, cuota de disco y política de retención. No borrar archivos referenciados por snapshots.
No se garantiza WCAG AAA integral sin auditoría manual de tecnologías de asistencia. Se implementan tamaños, contraste, teclado, diálogos y mensajes accesibles.
No hay destino público configurado. La receta Docker prepara el despliegue, no acredita una URL publicada. Los tiempos de respuesta dependen del hosting y proveedores; medir antes de prometer los objetivos del documento.

## Operación de Persistencia y Migración (Issue #2)
- **Protocolo de backup previo a migración**: Antes de desplegar una actualización con cambio de versión sobre una base existente en producción, detener todas las instancias del monolito para garantizar que no existan conexiones activas. Realizar una copia de seguridad consistente de `norma.db` junto con los archivos auxiliares `norma.db-wal` y `norma.db-shm` (si están presentes) y del directorio de medios `uploads/`. Registrar checksum SHA-256 de los archivos respaldados.
- **Procedimiento de rollback ante falla**: La migración v0→v2 opera bajo principio fail-closed dentro de una única transacción. Si la base legacy presenta inconsistencias (huérfanos, types incorrectos, constraints violados o triggers/índices desconocidos), la migración se aborta y se revierte lógicamente a nivel transacción, preservando `user_version=0` y los datos relacionales preexistentes. No obstante, caídas de energía o fallos de almacenamiento subyacentes requieren restaurar el backup físico; en caso de aborto en el arranque, detener el proceso nuevo, no intentar reparar o resembrar a ciegas y restaurar el backup previo restableciendo el binario de la versión anterior.
- **Comportamiento del contador de piezas**: La numeración de piezas nuevas es monótona y sobrevive a operaciones de `reset` y `restore`. Resetear el catálogo de una sesión devuelve las 8 piezas originales pero conserva el high-water mark numérico, por lo que la siguiente creación utilizará el número inmediatamente posterior al mayor emitido históricamente.
- **Límites de concurrencia y contención**: El pool de conexiones SQLite está configurado en `maximum-pool-size=1` con `busy_timeout=5000` y `synchronous=NORMAL` sobre SQLite WAL. Esta configuración serializa las transacciones dentro del proceso y previene colisiones. No se deben conectar múltiples procesos o réplicas sobre el mismo archivo `.db`.

## Operación de Medios e Imágenes (Issue #4)
- **Formatos y Límites de Entrada**: Formatos admitidos: JPEG/JPG, PNG8 y WebP estático. Se rechazan con 400 formatos políglotas, WebP animados (`ANIM`/`ANMF` o flag `animation` en `VP8X`), perfiles ICC incrustados y metadatos EXIF excesivos (> 256 KiB) o contradictorios. Límites físicos: tamaño máximo de 15 MiB (`spring.servlet.multipart.max-file-size=15MB`), resolución máxima de 16.384 px por eje y límite total de 24 Megapíxeles ($W \times H \le 24.000.000$).
- **Concurrencia y Huella de Memoria**: El procesador de imágenes opera con un semáforo estricto de concurrencia de **1 slot activo** (`Semaphore(1)`). Si llega una segunda petición mientras se procesa un raster, se rechaza de inmediato con HTTP 500 ("Servidor ocupado procesando otra imagen.") sin encolar imágenes en memoria. El procesamiento de imágenes de hasta 24 MP genera un pico temporal de memoria acotado (~65-99 MB de heap delta transitorio) que se libera inmediatamente tras la transformación y codificación.
- **Pipeline de Revelado y Codificación**: Recorte centrado proporcional 4:3 con factor $k = \min(\lfloor W_o/4 \rfloor, \lfloor H_o/3 \rfloor)$. Redimensionamiento uniforme a 800x600 px con composición sobre fondo blanco sólido `#FFFFFF` (para eliminar transparencia) y corrección de contraste sRGB8 ($R'=1.0403 \times R, G'=1.03 \times G, B'=1.0197 \times B$). Codificación en WebP con compresión con pérdida explícita en hasta 8 intentos con calidades decrecientes `[80, 70, 60, 50, 40, 30, 20, 10]`. Se aprueba el primer candidato cuyo tamaño cumpla estrictamente $0 < \text{bytes} < 80.000$. Si ningún candidato baja de 80.000 bytes, se rechaza con 400 sin alterar la resolución de 800x600.
- **Staging y Publicación Atómica**: El archivo entrante y los candidatos se escriben en el directorio temporal privado `<norma.uploads>/.processing/` con nombres opacos generados por el servidor. La publicación final se realiza mediante `Files.move(..., ATOMIC_MOVE)` hacia `<norma.uploads>/<UUID>.webp`. Para garantizar que `ATOMIC_MOVE` no falle, el directorio staging `.processing/` reside obligatoriamente en el mismo sistema de archivos que la raíz de uploads.
- **Serving Seguro y Prevención de Traversal**: Las imágenes publicadas se sirven públicamente a través de `/media/**` con cabecera `Content-Type: image/webp` y control de caché `Cache-Control: public, max-age=86400`. Cualquier intento de path traversal (`..`), solicitud de archivos dentro de `.processing/`, enlaces simbólicos hacia fuera o archivos inexistentes es interceptado y responde HTTP 404 genérico ("No encontré ese archivo.") sin exponer rutas internas ni fugas de infraestructura.
- **Política de Limpieza y Durabilidad**: En caso de error o éxito, el bloque `finally` del procesador elimina rigurosamente los temporales de entrada y candidatos generados durante la llamada en curso. Los archivos ya publicados en `<norma.uploads>/` no son eliminados automáticamente por el procesador, preservando la estabilidad referencial de catálogos y snapshots de reversión (`undo`).

## Operación de Despliegue y Contenedorización (Issue #8)
- **Estructura Multi-Etapa de la Imagen Docker**:
  - Etapa 1 (`build`): basada en `maven:3.9-eclipse-temurin-25`, compila y ejecuta la verificación completa con `mvn -B verify` a partir de `pom.xml` y `src/`, generando `target/taller-1.0.0.jar`.
  - Etapa 2 (runtime): basada en `eclipse-temurin:25-jre-noble` (Ubuntu 24.04 LTS), instala `libwebp7` y aprovisiona un entorno virtual Python (`/opt/voice`) con `edge-tts==7.3.1`.
- **Seguridad y Usuario No-Root**:
  - Se crea un usuario del sistema `norma` con UID/GID numérico 10001 (`useradd -r -u 10001 norma`).
  - El directorio de datos `/data` se crea y asigna en propiedad a dicho usuario antes de la directiva `USER norma`.
  - El binario se ejecuta como usuario no privilegiado 10001, sin permisos de superusuario ni capacidades adicionales.
- **Volumen Persistente y Almacenamiento**:
  - La directiva `VOLUME /data` y las variables de entorno `NORMA_DB=/data/norma.db` y `NORMA_UPLOADS=/data/uploads` redirigen toda persistencia al volumen nombrado (`norma-data:/data`).
  - Al crearse el volumen nombrado, Docker inicializa la propiedad del directorio `/data` con los permisos del contenedor (usuario 10001 `norma`).
  - Tanto la base de datos relacional SQLite (en modo WAL con archivos `-wal` y `-shm`) como las imágenes WebP procesadas sobreviven de forma transparente a reinicios (`docker restart`) y a la recreación o actualización de contenedores (`docker rm` + `docker run`) siempre que se monte el mismo volumen nombrado.
- **Flags de Optimización JVM**:
  - `ENTRYPOINT ["java","--enable-native-access=ALL-UNNAMED","-XX:+UseCompactObjectHeaders","-Xmx384m","-jar","app.jar"]`.
  - `--enable-native-access=ALL-UNNAMED`: requerido para llamadas a SQLite JDBC Loader en JDK 25 sin advertencias terminales.
  - `-XX:+UseCompactObjectHeaders`: reduce la huella de memoria en 64-bit JVM.
  - `-Xmx384m`: tope de heap acotado para despliegue ligero en entornos con recursos controlados.
- **Orquestación con Docker Compose**:
  - `compose.yaml` estandariza el servicio `taller`, mapeando el puerto configurable `${PORT:-8080}:8080`, asociando el volumen `norma-data:/data`, reinicio automático `unless-stopped` y comprobación de salud continua `healthcheck` mediante sondeo HTTP (`/api/ping`) con intervalo de 30s y tiempo límite de 5s.
  - Para pruebas o entornos QA concurrentes, se debe emplear un override de Compose (`-f compose.yaml -f target/docker-compose.qa.yml`) con un nombre de proyecto aislado (`-p norma-qa`) y puerto libre distinto al 8080 estándar para evitar colisiones.
- **Procedimiento de Backup del Contenedor**:
  - Detener el contenedor del monolito: `docker compose stop`.
  - Ejecutar un volcado consistente del volumen:
    `docker run --rm -v norma-data:/data -v $(pwd)/backups:/backup ubuntu:24.04 tar czf /backup/norma-backup-$(date +%Y%m%d%H%M%S).tar.gz -C /data .`
  - Reanudar el contenedor: `docker compose start`.
- **Límites Reales**:
  - No exponer el contenedor a redes públicas sin un proxy TLS inverso (Nginx, Caddy o Traefik) con terminación SSL, ya que la API de micrófono del navegador requiere HTTPS en cualquier origen distinto de `localhost`.
  - El monolito asume instancia única por archivo de datos SQLite (`maximum-pool-size=1`). No conectar múltiples réplicas concurrentes al mismo archivo de volumen.


