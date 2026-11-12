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
