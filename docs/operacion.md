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
- **Procedimiento de rollback ante falla**: La migración v0→v2 opera bajo principio fail-closed dentro de una única transacción. Si la base legacy presenta inconsistencias (huérfanos, types incorrectos, constraints violados), la migración se aborta y se revierte automáticamente, preservando `user_version=0` y los datos intactos. En caso de aborto en el arranque, detener el proceso nuevo, no intentar reparar o resembrar a ciegas y restaurar el backup previo restableciendo el binario de la versión anterior.
- **Comportamiento del contador de piezas**: La numeración de piezas nuevas es monótona y sobrevive a operaciones de `reset` y `restore`. Resetear el catálogo de una sesión devuelve las 8 piezas originales pero conserva el high-water mark numérico, por lo que la siguiente creación utilizará el número inmediatamente posterior al mayor emitido históricamente.
- **Límites de concurrencia y contención**: El pool de conexiones SQLite está configurado en `maximum-pool-size=1` con `busy_timeout=5000` y `synchronous=NORMAL` sobre SQLite WAL. Esta configuración serializa las transacciones dentro del proceso y previene colisiones. No se deben conectar múltiples procesos o réplicas sobre el mismo archivo `.db`.
