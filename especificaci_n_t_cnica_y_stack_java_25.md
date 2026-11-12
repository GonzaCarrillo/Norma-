# 🛠️ Especificación Técnica e Ingeniería — El Taller de Norma

Este documento define el stack tecnológico definitivo, la estructura del proyecto, el esquema de datos y la configuración de despliegue utilizando **Java 25** y **Spring Boot** bajo una arquitectura de monolito compacto de latencia cero.

---

## 1. Stack Tecnológico Definido

| Capa / Componente | Tecnología Elegida | Justificación Técnica para el Challenge |
| :--- | :--- | :--- |
| **Lenguaje y Runtime** | **Java 25 (LTS/Current)** | Uso de **Virtual Threads (Project Loom)** para manejar streams de audio, procesos de sistema (`edge-tts`), peticiones HTTP a Groq y conexiones SSE sin bloquear hilos del sistema operativo. Uso de `records` y *Pattern Matching for switch* para modelar la Máquina de Estados Finita (FSM) sin *boilerplate*. |
| **Framework Backend** | **Spring Boot 3.4+ (Spring Web + Spring JDBC)** | Uso de `JdbcClient` moderno en lugar de JPA/Hibernate pesado: mapeo directo a `record` de Java en $1\text{ ms}$, arranque ultrarrápido y consumo mínimo de memoria RAM. |
| **Base de Datos** | **SQLite 3 (`sqlite-jdbc` en modo WAL)** | Base de datos embebida en un único archivo (`norma.db`) con `PRAGMA journal_mode=WAL;` para lecturas y escrituras concurrentes instantáneas ($0\text{ ms}$ de latencia de red) y aislamiento por `session_id`. |
| **Tiempo Real (Push)** | **Server-Sent Events (`SseEmitter`)** | Nativo en Spring Web. Actualiza la grilla de productos automáticamente cuando Norma confirma un cambio por audio, sin la complejidad de WebSockets bidireccionales ni problemas de CORS. |
| **Speech-to-Text (Oído)** | **Groq Cloud API (`whisper-large-v3-turbo`)** | Consumido mediante el nuevo `RestClient` síncrono de Spring sobre Virtual Threads. Transcribe audios en español rioplatense en $\approx 200\text{ ms}$ dentro de la capa gratuita. |
| **Motor de Intenciones (Cerebro)** | **Parser Heurístico Java 25 + Fallback Llama 3.3** | **Nivel 1 (Sin IA):** Regex + normalización de números hablados (*"quince mil"* $\rightarrow 15000$) + identificación por `#Pieza` o distancia de Levenshtein. **Nivel 2 (Fallback):** Si la frase es ambigua, consulta a `llama-3.3-70b-versatile` forzando salida JSON estricta. |
| **Text-to-Speech (Voz del Sobrino)** | **`edge-tts` (`es-AR-TomasNeural`)** | Ejecutado desde Java con `ProcessBuilder`. Genera notas de voz naturales con acento argentino real en $< 400\text{ ms}$, $100\%$ gratuito y sin límites de cuota. |
| **Procesamiento de Imágenes** | **Thumbnailator (`net.coobird:thumbnailator`)** | Librería liviana en Java sin dependencias externas. Recorta a proporción uniforme (`4:3`) y comprime cualquier foto subida desde la cámara a $< 80\text{ KB}$. |
| **Frontend (Vidriera + Simulador)** | **HTML5 Semántico + CSS3 + Vanilla JS (ES6+)** | Servido desde `src/main/resources/static`. Cero *bundles* pesados, cero problemas de CORS, carga inicial en $< 50\text{ ms}$, accesibilidad WCAG AAA (contraste $> 7:1$) y uso de `MediaRecorder` + `window.speechSynthesis`. |

---

## 2. Arquitectura de Directorios del Proyecto

```text
taller-tia-norma/
├── Dockerfile                          # Imagen multi-stage: Build con Maven (JDK 25) + Runtime con JRE 25, Python3 y edge-tts
├── pom.xml                             # Configuración Maven para Java 25 y dependencias livianas
├── norma.db                            # Base de datos SQLite autogenerada al iniciar
└── src/main/
    ├── java/com/norma/taller/
    │   ├── TallerNormaApplication.java # Clase principal + inicialización de esquema SQLite
    │   ├── config/
    │   │   └── AppConfig.java          # Habilitación de Virtual Threads y RestClient para Groq
    │   ├── controller/
    │   │   ├── CatalogController.java  # GET /api/products, GET /api/catalog/stream (SSE), POST /api/reset, GET /api/ping
    │   │   └── BotController.java      # POST /api/bot/message (Recibe audio, imagen o texto del simulador/webhook)
    │   ├── model/
    │   │   ├── Product.java            # Java Record: (Long id, String sessionId, int pieceNumber, String title, int price, String imageUrl, boolean sold)
    │   │   ├── BotState.java           # Enum: IDLE, AWAITING_DETAILS, AWAITING_CONFIRMATION
    │   │   ├── BotIntent.java          # Sealed interface / Record para CREATE, UPDATE_PRICE, UPDATE_IMAGE, SOLD, UNDO, UNKNOWN
    │   │   └── BotSession.java         # Estado en curso de la conversación + snapshot JSON para Rollback ("¡Uy, me equivoqué!")
    │   ├── repository/
    │   │   └── CatalogRepository.java  # Operaciones SQL rápidas con JdbcClient y clonado de semilla por sesión
    │   └── service/
    │       ├── NormaFsmService.java    # Máquina de Estados Finita con Pattern Matching de Java 25
    │       ├── SpeechService.java      # Integración STT (Groq Whisper) y TTS (ProcessBuilder -> edge-tts)
    │       ├── IntentParserService.java# Parser heurístico en Java + Fallback LLM estructurado
    │       ├── ImageService.java       # Pipeline de recorte, optimización y guardado de imágenes
    │       └── SseService.java         # Gestión de SseEmitters concurrentes por sessionId
    └── resources/
        ├── application.properties      # Configuración SQLite WAL, Virtual Threads y variables de entorno
        ├── schema.sql                  # Creación de tablas e inserción de las 8 piezas iniciales ('DEFAULT')
        └── static/
            ├── index.html              # Vidriera artesanal + Drawer interactivo del WhatsApp de Norma
            ├── css/
            │   └── estilos-taller.css  # Paleta papel/terracota, tipografía gigante y estilos del simulador
            ├── js/
            │   ├── vidriera.js         # Cliente SSE, zoom accesible, cambio de fuente y lectura por voz para clientes
            │   └── whatsapp-sim.js     # Grabación de micrófono (MediaRecorder), reproductor de audios y 4 chips de prueba
            └── assets/
                ├── seed-images/        # 8 fotos reales de cerámica artesanal optimizadas en WebP/JPEG
                └── demo-audios/        # 4 audios pre-grabados de Norma para prueba en 1 clic del jurado
```

---

## 3. Esquema de Base de Datos (SQLite)

El archivo `src/main/resources/schema.sql` se ejecuta automáticamente al levantar la aplicación y garantiza persistencia real con aislamiento total entre evaluadores:

```sql
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;

CREATE TABLE IF NOT EXISTS products (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    piece_number INTEGER NOT NULL,
    title TEXT NOT NULL,
    price INTEGER NOT NULL,
    image_url TEXT NOT NULL,
    sold INTEGER DEFAULT 0,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_products_session ON products(session_id);

CREATE TABLE IF NOT EXISTS bot_sessions (
    session_id TEXT PRIMARY KEY,
    current_state TEXT NOT NULL DEFAULT 'IDLE',
    pending_intent_json TEXT,
    temp_image_url TEXT,
    previous_snapshot_json TEXT,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### Lógica del Sandbox por Sesión (`session_id`)
1. Cuando el navegador del juez carga la página, genera o recupera de `localStorage` un identificador único (ej. `norma_sess_9x8a7`).
2. Al pedir `GET /api/products?sessionId=norma_sess_9x8a7`, si no existen registros para esa sesión, `CatalogRepository` ejecuta un `INSERT INTO products (...) SELECT ... FROM products WHERE session_id = 'DEFAULT'` en menos de $2\text{ ms}$.
3. Antes de ejecutar cualquier modificación confirmada por Norma (`CREATE`, `UPDATE_PRICE`, `UPDATE_IMAGE`, `SOLD`), el estado actual de los productos de esa sesión se serializa en `bot_sessions.previous_snapshot_json`. Si Norma dice *"¡Uy, me equivoqué!"*, se restaura ese snapshot en una sola transacción.

---

## 4.Aprovechamiento de Java 25 en el Backend

### A. `Sealed Interfaces` y `Records` para Intenciones Tipadas
En lugar de manejar mapas genéricos propensos a errores (`NullPointerException`), modelamos cada intención del chatbot usando características modernas del lenguaje:

```java
package com.norma.taller.model;

public sealed interface BotIntent {
    record CreatePiece(String title, Integer price, String imageUrl) implements BotIntent {}
    record UpdatePrice(Integer pieceNumber, String titleHint, Integer newPrice) implements BotIntent {}
    record UpdateImage(Integer pieceNumber, String titleHint, String newImageUrl) implements BotIntent {}
    record MarkAsSold(Integer pieceNumber, String titleHint) implements BotIntent {}
    record UndoLastAction() implements BotIntent {}
    record Confirmation(boolean confirmed, String correctionText) implements BotIntent {}
    record Unknown(String rawTranscript) implements BotIntent {}
}
```

### B. Máquina de Estados con *Pattern Matching for `switch`*
El núcleo de `NormaFsmService` evalúa las transiciones de forma exhaustiva y segura en tiempo de compilación:

```java
public BotResponse processIncomingIntent(BotSession session, BotIntent intent) {
    return switch (intent) {
        case BotIntent.UndoLastAction u -> executeRollback(session);
        case BotIntent.CreatePiece(var title, var price, var img) when img == null -> 
            askForMissingPhoto(session, title, price);
        case BotIntent.CreatePiece(var title, var price, var img) -> 
            prepareConfirmation(session, intent, 
                "Hola tía, recibí la foto. Entendí que es %s a %s pesos. ¿Lo subo así a la página?".formatted(title, formatSpokenPrice(price)));
        case BotIntent.UpdatePrice(var num, var hint, var newPrice) -> 
            preparePriceUpdateConfirmation(session, num, hint, newPrice);
        case BotIntent.UpdateImage(var num, var hint, var newImg) -> 
            prepareImageUpdateConfirmation(session, num, hint, newImg);
        case BotIntent.MarkAsSold(var num, var hint) -> 
            prepareSoldConfirmation(session, num, hint);
        case BotIntent.Confirmation(var ok, var correction) -> 
            handleConfirmationState(session, ok, correction);
        case BotIntent.Unknown(var raw) -> 
            buildVoiceReply("Perdón tía, salió medio cortado el audio. ¿Me repetís qué pieza querés cambiar y el precio?");
    };
}
```

---

## 5. Configuración de Dependencias (`pom.xml` para Java 25)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.3</version>
        <relativePath/>
    </parent>

    <groupId>com.norma</groupId>
    <artifactId>taller-tia-norma</artifactId>
    <version>1.0.0</version>
    <name>El Taller de Norma</name>

    <properties>
        <java.version>25</java.version>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
        <maven.compiler.release>25</maven.compiler.release>
    </properties>

    <dependencies>
        <!-- Web, REST y Server-Sent Events (SseEmitter) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Acceso a datos ultrarrápido con JdbcClient -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>

        <!-- Driver SQLite embebido -->
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>3.49.1.0</version>
        </dependency>

        <!-- Procesamiento y compresión de fotografías del taller -->
        <dependency>
            <groupId>net.coobird</groupId>
            <artifactId>thumbnailator</artifactId>
            <version>0.4.20</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 6. Configuración de `application.properties`

```properties
spring.application.name=taller-tia-norma
server.port=${PORT:8080}

# Habilitar Virtual Threads nativos de Java 25 en Tomcat y tareas asíncronas
spring.threads.virtual.enabled=true

# Conexión SQLite en archivo local con inicialización automática
spring.datasource.url=jdbc:sqlite:norma.db
spring.datasource.driver-class-name=org.sqlite.JDBC
spring.sql.init.mode=always

# Soporte de subida de fotos de cámara de alta resolución antes de comprimir
spring.servlet.multipart.max-file-size=15MB
spring.servlet.multipart.max-request-size=20MB

# Credenciales y modelos de Groq (STT + Fallback de Intenciones)
groq.api.key=${GROQ_API_KEY:}
groq.stt.model=whisper-large-v3-turbo
groq.llm.model=llama-3.3-70b-versatile

# Configuración de Voz Argentina (edge-tts)
norma.tts.voice=es-AR-TomasNeural
norma.tts.rate=+0%
```

---

## 7. Contratos de la API REST y Tiempo Real

| Método y Ruta | Descripción | Entrada | Salida |
| :--- | :--- | :--- | :--- |
| `GET /api/products` | Obtiene el catálogo actual de la sesión (o clona la semilla si es nueva). | `?sessionId=String` | `200 OK` $\rightarrow$ `List<Product>` |
| `GET /api/catalog/stream` | Canal `SseEmitter` abierto para actualizar la grilla sin recargar la página. | `?sessionId=String` | Stream `text/event-stream` (evento `catalog-update`) |
| `POST /api/bot/message` | Recibe un mensaje de Norma (audio grabado, audio demo, foto o texto). | `multipart/form-data`: `sessionId`, `audio` (opcional), `image` (opcional), `text` (opcional) | `200 OK` $\rightarrow$ `{ "replyText": "...", "replyAudioUrl": "/api/bot/audio/xyz.mp3", "state": "AWAITING_CONFIRMATION" }` |
| `POST /api/reset` | Restaura el catálogo original de Norma e invalida borradores de la sesión. | `?sessionId=String` | `200 OK` + Dispara actualización SSE a la vidriera |
| `GET /api/ping` | Endpoint ultraliviano para mantener despierta la JVM desde UptimeRobot. | Ninguna | `200 OK` (`"PONG"`) |

---

## 8. `Dockerfile` Unificado (Java 25 + `edge-tts`)

Este `Dockerfile` empaqueta el backend en Java 25 junto con el binario `edge-tts` para generar los audios con acento argentino sin depender de servicios pagos:

```dockerfile
# Etapa 1: Compilación con JDK 25
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests

# Etapa 2: Imagen de ejecución ultraliviana con JRE 25 + edge-tts
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Instalar Python3, pip y ffmpeg/edge-tts para síntesis de voz argentina
RUN apk add --no-cache python3 py3-pip ffmpeg && \
    pip3 install --no-cache-dir --break-system-packages edge-tts

# Directorios para almacenamiento temporal de audios y fotos optimizadas
RUN mkdir -p /app/uploads/images /app/uploads/audios

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Flags de optimización de arranque y memoria para contenedores gratuitos
ENTRYPOINT ["java", "-XX:+UseCompactObjectHeaders", "-Xmx384m", "-jar", "app.jar"]
```

> **Nota sobre Java 25 en el `Dockerfile`:** Se incluye el flag `-XX:+UseCompactObjectHeaders` (JEP 519 / Lilliput en Java 25), que reduce el tamaño de los encabezados de objetos en memoria a 64 bits, disminuyendo el consumo de RAM del contenedor entre un $10\%$ y un $20\%$ en servidores gratuitos con límite de $512\text{ MB}$.