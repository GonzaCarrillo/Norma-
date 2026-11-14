# Especificación y Contrato de Conciliación — Issue #4: Fotografías y medios de demostración

**Versión**: 1.0 (Conciliación final para despacho)  
**Fecha**: 13 de Noviembre de 2026  
**Autor**: Antigravity (Software Builder / Implementer & Reconciliation)  
**Estado**: Contrato técnico conciliado sobre `origin/main` (`a320a65`) para auditoría de Fase 3 (OpenCode / Nemotron) y Aprobación Arquitectónica Final de Fase 4 (Codex).  
**Diseño de referencia base**: `docs/orchestration/issue-4-design.md` v1.0 (aprobado por Orca en `docs/orchestration/issue-4-approval.md`, con verificaciones históricas en `issue-4-codex-approval.md` y `issue-4-qa-revalidacion.md`).  
**Rama**: `GonzaCarrillo/reconcile-4-media-final`  
**Base commit**: `a320a65` (incluye conciliaciones de Issue #6 SSE y Issue #5 Voz/Fallback)  
**Tarea / Dispatch**: `task_b6679ff0dac2`, `ctx_2af859e5ac7c`

---

## 1. Propósito y Contexto de la Conciliación

El presente documento formaliza el contrato técnico y la preservación íntegra de la especificación de la Issue #4 sobre la rama final unificada `origin/main` (`a320a65`), donde ya coexisten las entregas de:
- **Issue #1, #2 y #3**: Monolito base, persistencia SQLite v2 (WAL, busy_timeout, foreign keys, migraciones), FSM de diálogo con catálogo transaccional, comandos de control y undo.
- **Issue #6**: Vidriera web (Storefront) con sincronización en tiempo real vía Server-Sent Events (SSE), serialización atómica de ciclo de vida de emisores por sesión y catálogo desacoplado.
- **Issue #5**: Procesamiento de voz (STT Groq Whisper con discriminación de errores 400/422 vs 401/429/5xx, síntesis TTS determinista con degradación segura) y normalización LLM desacoplada (`IntentFallback`) conectada en `Api.message(...)` fuera de `@Transactional`.

Esta conciliación certifica que el subsistema de medios (`PhotoProcessor`, `MediaService`, `MediaConfig`, `ApiErrors` y assets estáticos) se conserva **sin alteraciones funcionales innecesarias**, respetando al 100% el diseño original aprobado de Codex y garantizando la compatibilidad armónica con los flujos de voz (#5) y SSE (#6).

---

## 2. Conservación del Contrato Técnico Original (§3–§6 de `issue-4-design.md`)

Se ratifican los siguientes contratos de diseño sin desviaciones:

### 2.1 Formatos de Entrada y Allowlist Estricta
- **Formatos admitidos**: Únicamente **JPEG/JPG**, **PNG8** y **WebP estático**.
- **Detección independiente de MIME/Extensión**: La identificación del formato se realiza mediante análisis de la firma de bytes del contenedor (magic numbers) y validación del `ImageReader` de ImageIO. La extensión o MIME declarados por el cliente no determinan la admisión (un WebP enviado con nombre `foto.jpg` se procesa como WebP; un archivo de texto con extensión `.jpg` es rechazado).
- **Formatos excluidos**: Rechazo con HTTP 400 (`"Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG."`) para HEIC, HEIF, GIF, BMP, TIFF, SVG, PDF, AVIF o formatos RAW.
- **Contenedores animados y políglotas**: Rechazo estricto de PNG con chunk `acTL` y WebP con chunks `ANIM`/`ANMF` o flag `ANIMATION`.
- **Canales y profundidad**: Soporte para hasta 8 bits por canal (RGB, escala de grises, indexado con paleta, alfa opcional). Rechazo con HTTP 400 ante PNG de 16 bits por canal, HDR o JPEG en espacios de color CMYK/YCCK.

### 2.2 Límites de Cámara y Presupuesto de Memoria
- **Tamaño de archivo**: Rango permitido de **1 a 15.728.640 bytes (15 MiB)** inclusive. Peticiones multipart con partes que excedan 15 MiB son cortadas por el contenedor Servlet emitiendo HTTP 413 (`"El archivo es demasiado grande. Probá con uno más chico."`). Si el streaming en el servicio detecta >15 MiB, responde HTTP 400 (`"La foto está vacía o pesa más de 15 MB."`).
- **Dimensiones por eje**: Ancho y alto positivos con `max(width, height) <= 16384` px.
- **Límite de píxeles**: Producto en `long` `width * height <= 24.000.000` px (24 MP). La validación se ejecuta leyendo exclusivamente los metadatos de cabecera antes de decodificar el raster completo a memoria (`read(0)`).
- **Concurrencia de aplicación**: Exactamente **un procesamiento fotográfico activo por instancia** mediante `Semaphore(1).tryAcquire()`. Ante contención concurrente, responde inmediatamente HTTP 500 (`"No pude completar el pedido. Intentá otra vez."`) sin encolar rasters en memoria ni mutar la conversación. El permiso se libera estrictamente en el bloque `finally`.

### 2.3 Orientación EXIF y Metadatos
- **Dependencia**: `com.drewnoakes:metadata-extractor:2.19.0`.
- **Extracción acotada**: Lectura exclusiva del bloque EXIF IFD0 para JPEG (APP1), PNG (`eXIf`) y WebP (`EXIF`), con límite máximo de **262.144 bytes (256 KiB)** de metadatos.
- **Orientaciones**: Soporte exhaustivo para orientaciones 1 a 8 de la especificación TIFF/EXIF. La ausencia de bloque EXIF asume orientación 1 (neutra).
- **Rechazo de metadatos dañados**: Orientaciones fuera del rango 1–8, bloques EXIF corruptos, truncados o contradictorios entre directorios provocan HTTP 400 (`"La foto contiene metadatos dañados o no soportados."`).
- **Privacidad**: La salida generada no retiene metadatos EXIF, coordenadas GPS, marcas de tiempo ni perfiles ICC embebidos.

### 2.4 Geometría y Revelado Determinista
- **Recorte centrado 4:3**: Se calcula el factor de escala sobre dimensiones lógicas orientadas:
  $$k = \min\left(\left\lfloor \frac{W_o}{4} \right\rfloor, \left\lfloor \frac{H_o}{3} \right\rfloor\right)$$
  Si $k = 0$, la imagen es rechazada con HTTP 400 (`"La foto es demasiado chica. Mandame una más grande."`). El origen del recorte se ubica en:
  $$\left(\left\lfloor \frac{W_o - 4k}{2} \right\rfloor, \left\lfloor \frac{H_o - 3k}{2} \right\rfloor\right)$$
- **Escala uniforme y composición**: Redimensionado mediante Thumbnailator a dimensiones finales exactas de **800×600 px** sobre fondo blanco (`#FFFFFF`) para canales con transparencia.
- **Corrección tonal sutil (una sola vez sobre canales sRGB8)**:
  $$R' = \text{clamp}(\text{round}(R \times 1.03 \times 1.01))$$
  $$G' = \text{clamp}(\text{round}(G \times 1.03))$$
  $$B' = \text{clamp}(\text{round}(B \times 1.03 \times 0.99))$$
  Donde `clamp(v)` restringe al rango $[0, 255]$ y `round` utiliza redondeo al entero más cercano.

### 2.5 Codificación y Política de Compresión WebP
- **Encoder**: Proveedor WebP instalado en el runtime (`org.sejda.imageio:webp-imageio:0.1.6`) configurado explícitamente en modo con pérdida (lossy).
- **Lista de calidades inmutable**: Exactamente hasta 8 intentos con calidades enteras en orden decreciente:
  $$\mathcal{Q} = [80, 70, 60, 50, 40, 30, 20, 10] / 100\text{f}$$
- **Criterio estricto de aceptación**: Candidato válido si y solo si:
  $$0 < \text{bytes} < 80.000$$
  Un candidato con exactamente 80.000 bytes es rechazado. Se detiene en el primer intento admisible.
- **Agotamiento de intentos**: Si los 8 candidatos superan los 80.000 bytes, se retorna HTTP 400 (`"No pude reducir esta foto a menos de 80 KB. Probá con otra."`) sin reducir la resolución 800×600 y limpiando todos los archivos temporales.

### 2.6 Almacenamiento, Publicación Atómica y Serving HTTP
- **Directorio Staging**: `root/.processing/` aislado dentro de `NORMA_UPLOADS`.
- **Publicación Atómica**: El archivo candidato validado se transfiere a `root/<UUID>.webp` mediante `Files.move(..., StandardCopyOption.ATOMIC_MOVE)` sin `REPLACE_EXISTING`. Si el sistema de archivos subyacente no soporta movimiento atómico, aborta con HTTP 500 sin exponer archivos parciales en la raíz pública.
- **URL pública**: Formato estricto `/media/<uuid>.webp`. Nunca se exponen nombres del cliente ni rutas físicas del servidor.
- **Serving seguro**: `MediaConfig` registra un `PathResourceResolver` que intercepta `/media/**`:
  - Bloquea cualquier solicitud que contenga o inicie con `.processing` o `.`.
  - Bloquea secuencias de escape de directorio (`..`) resolviendo `toRealPath()`.
  - Archivos faltantes, accesos a staging o escapes de directorio retornan HTTP 404 estricto:
    ```json
    { "message": "No encontré ese archivo." }
    ```
  - Archivos válidos retornan HTTP 200 con cabecera `Content-Type: image/webp` para imágenes y `audio/mpeg` para audios MP3.
- **Limpieza de temporales**: El bloque `finally` de `PhotoProcessor.process` elimina **únicamente los temporales creados durante esa invocación**, preservando el estado de sesiones y archivos concurrentes.

---

## 3. Matriz de Procedencia de Recursos de Demostración (§7)

Se ratifica el inventario y trazabilidad honesta documentada en `docs/medios.md` sobre los 8 recursos WebP y 4 audios MP3 bajo `src/main/resources/static/assets/`:

| Recurso | Tipo | Motivo / Contenido | Dimensiones / Duración | Tamaño (Bytes) | Hash SHA-256 Verificado |
|---|---|---|---|---|---|
| `pieza-1.webp` | WebP | Vajilla artesanal: platos y cuencos | 800×600 px | 52.328 B | `365d0360f061b84a4e4fa6e7dd4ca85231666a8d67c796bdb0aa7b4f27fb37a7` |
| `pieza-2.webp` | WebP | Alfarería en torno: manos torneando | 800×600 px | 10.988 B | `c15195fb60a6c4707710b4833d249c33cc99ed588fa8767e998ed4c23730972f` |
| `pieza-3.webp` | WebP | Vajilla rústica en estante | 800×600 px | 15.054 B | `ab8b44ac2dc020b58ee8405635ce7bdd921287fa1446da058f2d1d41795042a1` |
| `pieza-4.webp` | WebP | Floreros y vasijas con ramas | 800×600 px | 29.792 B | `fc0abb484a8622db9e4265f30b282ca0a3a27978305734f5782defc0257ddf6d` |
| `pieza-5.webp` | WebP | Vajilla: detalle encuadre secundario | 800×600 px | 38.426 B | `e07db765feefe119ed51d5418faa70727c4498675a84007fe6a2b2b608b98f2f` |
| `pieza-6.webp` | WebP | Alfarería: detalle modelado arcilla | 800×600 px | 9.036 B | `99d98672c9e6ab2749a92033bcf2f7271aa3841426bba841dfe928c626a10e91` |
| `pieza-7.webp` | WebP | Vasija: detalle textura esmaltada | 800×600 px | 13.604 B | `6bb6ec6d24d6f5af9f4a886698e2a82c1157e528607c0c45e31a6595efd3bd4e` |
| `pieza-8.webp` | WebP | Jarrones: detalle botellas rústicas | 800×600 px | 27.104 B | `0dd65b5c297d46af98b6194b356350876a2bff83d0231c4bae4785237ba522bc` |
| `demo-create.mp3` | MP3 | Muestra de síntesis de voz (alta) | 3.120 s | 18.720 B | `25bfcb4eeb43a6dfe1f671bc368f8b64cf85852f32ba0d2efc90b913465b7c15` |
| `demo-image.mp3` | MP3 | Muestra de síntesis de voz (foto) | 3.648 s | 21.888 B | `aa435c531920ef9b9eb5dc229509910b85ad6d12b22d96ca56c5697162fcec46` |
| `demo-price.mp3` | MP3 | Muestra de síntesis de voz (precio) | 3.528 s | 21.168 B | `e83836d7077b6c69ec40a2884b66e0fa1ee203ff1df78cace88aa30dc8ba1a6a` |
| `demo-sold.mp3` | MP3 | Muestra de síntesis de voz (venta) | 2.424 s | 14.544 B | `0da71c5037e881f52cc87419e2bf82cc4d3b6684fdcb05429de5c5af7638dbcd` |

**Declaración de integridad**: Fotografías humanas sin contenido generado por inteligencia artificial ni atribución ficticia. Audios sintetizados de prueba para botonera del jurado en `app.js`.

---

## 4. Análisis de Interoperabilidad y Compatibilidad Cruzada

### 4.1 Compatibilidad con Issue #5 (Voz STT / TTS e IntentFallback)
- **Orden de orquestación en `Api.message(...)`**:
  1. `Catalog.valid(sessionId)` y validación de exclusividad texto/audio (si ambos presentes, HTTP 400).
  2. Longitud máxima de texto (1000 caracteres).
  3. Si viene audio: `media.transcribe(audio)` ejecuta STT contra Groq Whisper. Si el audio es ilegible o la API rechaza con 400/422, se lanza `IllegalArgumentException` (HTTP 400) **antes** de invocar `PhotoProcessor.process`, evitando procesar la imagen innecesariamente. Los temporales de audio `.processing/norma-audio-*.webm` se limpian de inmediato.
  4. Si viene imagen: `media.image(image)` ejecuta `PhotoProcessor.process(image)` retornando `/media/<uuid>.webp`.
  5. Carga de sesión y catálogo.
  6. Si la sesión está en `IDLE`, no hay operación pendiente y el texto no es control directo ni intención conocida, se invoca `fallback.normalize(text, photo, products)`. El fallback solo recibe la URL de la foto como metadato de contexto; no manipula streams, no retiene locks de base de datos ni afecta el staging de medios.
  7. Procesamiento de diálogo con `bot.process(sessionId, text, photo)`.
  8. Generación de síntesis TTS no bloqueante con `media.speak(replyText)`. Si `edge-tts` falla o se supera el timeout de 12 s, retorna `null` y la respuesta HTTP `Reply` incluye el texto y estado sin romper el contrato del cliente.
- **Aislamiento de fallos**: Un fallo en STT o TTS no deja archivos WebP residuales. Un fallo en el procesamiento de imagen (400, 413 o 500) interrumpe la petición antes de llamar a `bot.process`, `IntentFallback` o `media.speak`, protegiendo la sesión del bot y el catálogo de mutaciones espurias.

### 4.2 Compatibilidad con Issue #6 (Storefront y Server-Sent Events)
- **Publicación SSE atómica**: `publish(sessionId)` se dispara **únicamente** tras la finalización exitosa de `bot.process(sessionId, text, photo)`. Si la carga de la foto falla por tamaño, formato o saturación de concurrencia, nunca se emite evento SSE de actualización de catálogo.
- **Consumo de medios por la vidriera**: Cuando una pieza es dada de alta o se actualiza su foto a `/media/<uuid>.webp`, los navegadores suscritos a `/api/catalog/stream` reciben el evento `catalog-update`, consultan `/api/products` y renderizan la imagen mediante `GET /media/<uuid>.webp`, el cual es servido directamente por el `PathResourceResolver` de `MediaConfig` con `Content-Type: image/webp`.
- **Independencia de recursos**: Las pruebas de concurrencia SSE (`StorefrontSseTest`) operan sobre sus propios emisores y sesiones aisladas sin competir con el semáforo ni el almacenamiento de `PhotoProcessor`.

### 4.3 Compatibilidad con Pista #7 (Simulador y Frontend)
- La pista #7 modifica únicamente la interfaz de usuario en `index.html`, `app.js` y `style.css`.
- El contrato de consumo frontend se mantiene estrictamente idéntico: llamadas a `POST /api/bot/message` con multipart `sessionId`, `text`, `image` y `audio`, y visualización de recursos estáticos en `/assets/pieza-N.webp` y `/assets/demo-*.mp3`.

---

## 5. Delimitación Estricta de Alcance (Scope de Despacho)

- **Archivos autorizados de escritura en este despacho**:
  1. `docs/orchestration/issue-4-reconciliation-design.md` (este documento).
  2. `docs/orchestration/issue-4-reconciliation-build.md` (reporte de construcción y evidencia).
  3. `issue-4-reconciliation-evidence/builder/` (evidencia Surefire XML/TXT, sumas SHA-256 de JAR y assets, y logs).
- **Archivos estrictamente no modificados**:
  - Código de producción (`PhotoProcessor.java`, `MediaService.java`, `Api.java`, `BotEngine.java`, etc.).
  - Código de pruebas (`PhotoProcessorTest.java`, `MediaHttpTest.java`, `DemoMediaTest.java`, etc.).
  - Recursos estáticos (`src/main/resources/static/assets/*`).
  - Documentos operativos y de medios (`docs/medios.md`, `docs/operacion.md`, `docs/decisiones.md`).
  - POM, Dockerfile y configuración del entorno.
