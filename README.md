# El Taller de Norma
Catálogo accesible y simulador conversacional sin credenciales. Java 25, Spring Boot 3.5, JDBC/SQLite WAL, SSE y HTML/CSS/JS.

## Ejecutar
Requiere JDK 25 y Maven 3.6.3+. Ejecutar `mvn verify`, después `java -jar target/taller-1.0.0.jar` y abrir http://localhost:8080.

## Reglas
Una sola vidriera de lectura, sin carrito, pagos ni enlaces a WhatsApp. Las modificaciones requieren confirmación; cada evaluador tiene una sesión independiente. Fotos originales, nunca arte generado por IA. Precios enteros argentinos, números de pieza estables, texto mínimo 22 px y controles accesibles.

## Alcance
Las dos especificaciones de la raíz son la fuente de requisitos. El simulador funciona sin servicios externos mediante texto y ejemplos explícitos. La transcripción real requiere GROQ_API_KEY; la voz argentina requiere edge-tts y conectividad. El navegador ofrece lectura alternativa. No se presenta el simulador como integración real con WhatsApp.

Backlog trazable: [docs/backlog.json](docs/backlog.json). Operación y limitaciones: [docs/operacion.md](docs/operacion.md).
