# Medios, Procedencia e Inventario de Recursos

Este documento describe la procedencia, especificaciones técnicas y el inventario criptográfico de los recursos de medios estáticos incluidos en la aplicación.

---

## 1. Procedencia y Trazabilidad de Fotografías

- **Origen**: Fotografías de demostración obtenidas de la plataforma Unsplash con licencia de uso libre, capturadas por fotógrafos humanos; no representan obras originales del taller de alfarería de Norma.
- **Fuentes originales (Unsplash ID)**:
  1. `1578749556568-bc2c40e68b61` (taller y cerámica artesanal)
  2. `1493106641515-6b5631de4bb9` (trabajo al torno)
  3. `1610701596007-11502861dcfa` (vasos y vajilla rústica)
  4. `1565193566173-7a0ee3dbe261` (jarrones y vasijas)
- **Derivación**: Los 8 recursos (`pieza-1.webp` a `pieza-8.webp`) corresponden a encuadres y recortes de estas 4 imágenes fotográficas humanas originales.
- **Declaración ética y de integridad**: **Ninguna imagen ha sido generada por inteligencia artificial** (DALL-E, Midjourney, Stable Diffusion u otros modelos generativos). No se simula autoría ni se realiza atribución ficticia.
- **Uso previsto**: Destinados exclusivamente a la vidriera inicial de evaluación. Deben ser reemplazados por fotografías autorizadas de las piezas reales del taller antes de una puesta en producción definitiva.

---

## 2. Inventario Criptográfico de Imágenes (`src/main/resources/static/assets/`)

Todas las imágenes cumplen estrictamente con:
- Contenedor WebP con chunk lossy `VP8 `.
- Sin metadatos superfluos o privados (bloques `EXIF`, `XMP `, `ICCP`, `ANIM`, `ANMF` ausentes; flags de animación/perfil en `VP8X` en 0).
- Resolución canónica uniforme: **800 x 600 px**.
- Restricción estricta de peso: **tamaño < 80.000 bytes**.

| Archivo | Dimensión | Formato | Tamaño (bytes) | Hash SHA-256 |
| :--- | :--- | :--- | :--- | :--- |
| `pieza-1.webp` | 800 x 600 | WebP (VP8 lossy) | 52.328 | `365d0360f061b84a4e4fa6e7dd4ca85231666a8d67c796bdb0aa7b4f27fb37a7` |
| `pieza-2.webp` | 800 x 600 | WebP (VP8 lossy) | 10.988 | `c15195fb60a6c4707710b4833d249c33cc99ed588fa8767e998ed4c23730972f` |
| `pieza-3.webp` | 800 x 600 | WebP (VP8 lossy) | 15.054 | `ab8b44ac2dc020b58ee8405635ce7bdd921287fa1446da058f2d1d41795042a1` |
| `pieza-4.webp` | 800 x 600 | WebP (VP8 lossy) | 29.792 | `fc0abb484a8622db9e4265f30b282ca0a3a27978305734f5782defc0257ddf6d` |
| `pieza-5.webp` | 800 x 600 | WebP (VP8 lossy) | 38.426 | `e07db765feefe119ed51d5418faa70727c4498675a84007fe6a2b2b608b98f2f` |
| `pieza-6.webp` | 800 x 600 | WebP (VP8 lossy) | 9.036 | `99d98672c9e6ab2749a92033bcf2f7271aa3841426bba841dfe928c626a10e91` |
| `pieza-7.webp` | 800 x 600 | WebP (VP8 lossy) | 13.604 | `6bb6ec6d24d6f5af9f4a886698e2a82c1157e528607c0c45e31a6595efd3bd4e` |
| `pieza-8.webp` | 800 x 600 | WebP (VP8 lossy) | 27.104 | `0dd65b5c297d46af98b6194b356350876a2bff83d0231c4bae4785237ba522bc` |

---

## 3. Inventario Criptográfico de Audios de Demostración (`src/main/resources/static/assets/`)

Los 4 archivos de audio corresponden a muestras sonoras de síntesis de voz utilizadas para la botonera de demostración del jurado.

| Archivo | Duración | Bitrate | Tamaño (bytes) | Hash SHA-256 |
| :--- | :--- | :--- | :--- | :--- |
| `demo-create.mp3` | 3.120 s | 48 kbps | 18.720 | `25bfcb4eeb43a6dfe1f671bc368f8b64cf85852f32ba0d2efc90b913465b7c15` |
| `demo-image.mp3` | 3.648 s | 48 kbps | 21.888 | `aa435c531920ef9b9eb5dc229509910b85ad6d12b22d96ca56c5697162fcec46` |
| `demo-price.mp3` | 3.528 s | 48 kbps | 21.168 | `e83836d7077b6c69ec40a2884b66e0fa1ee203ff1df78cace88aa30dc8ba1a6a` |
| `demo-sold.mp3` | 2.424 s | 48 kbps | 14.544 | `0da71c5037e881f52cc87419e2bf82cc4d3b6684fdcb05429de5c5af7638dbcd` |

---

## 4. Límites y Trazabilidad de la Interfaz Web

- **Aclaración de operación en `app.js`**: En el modo de demostración para el jurado, al accionar un botón de muestra de audio, el navegador reproduce el archivo `.mp3` correspondiente mientras simultáneamente envía al backend la transcripción predefinida fija en texto (no envía el binario de audio al endpoint STT de Groq). Esto garantiza una evaluación fluida y determinista sin consumir cuota de tokens ni depender de conectividad externa con Groq Whisper.
- **Naturaleza de la voz**: Los audios fueron sintetizados mediante herramientas automáticas de prueba; no constituyen grabaciones de voz de una persona real llamada Norma.
