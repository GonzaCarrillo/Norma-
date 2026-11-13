# Medios, Procedencia e Inventario de Recursos

Este documento describe la procedencia, especificaciones técnicas y el inventario criptográfico de los recursos de medios estáticos incluidos en la aplicación, en estricto cumplimiento con la sección §7 del diseño técnico de la Issue #4.

---

## 1. Procedencia y Trazabilidad de Fotografías

- **Origen**: Fotografías de demostración obtenidas de la plataforma Unsplash con licencia de uso libre, capturadas por fotógrafos humanos; no representan obras originales del taller de alfarería de Norma.
- **Fuentes originales consultadas (entrega CDN Unsplash)**:
  1. `https://images.unsplash.com/photo-1578749556568-bc2c40e68b61` (taller y cerámica artesanal, platos y cuencos)
  2. `https://images.unsplash.com/photo-1493106641515-6b5631de4bb9` (trabajo al torno con manos de alfarero)
  3. `https://images.unsplash.com/photo-1610701596007-11502861dcfa` (vasos y vajilla rústica en estante)
  4. `https://images.unsplash.com/photo-1565193566173-7a0ee3dbe261` (jarrones y vasijas con ramas ornamentales)
- **Derivación**: Los 8 recursos (`pieza-1.webp` a `pieza-8.webp`) corresponden a encuadres y recortes de estas 4 imágenes fotográficas humanas originales.
- **Declaración ética y de integridad**: **Ninguna imagen ha sido generada por inteligencia artificial** (DALL-E, Midjourney, Stable Diffusion u otros modelos generativos). No se simula autoría ni se realiza atribución ficticia.
- **Uso previsto**: Destinados exclusivamente a la vidriera inicial de evaluación. Deben ser reemplazados por fotografías autorizadas de las piezas reales del taller antes de una puesta en producción definitiva.

---

## 2. Matriz de Procedencia y Trazabilidad Individual por Recurso (§7)

Conforme a los criterios de aceptación de diseño §7, la siguiente matriz detalla de forma individual para cada uno de los 8 recursos: motivo real, pareja visual inferida, fuente exacta consultada, fotógrafo verificado (o indicación explícita de no identificado para no certificar atribuciones infundadas), fecha de comprobación, enlace a las condiciones de uso/licencia, transformaciones conocidas y hash SHA-256 final:

| Recurso | Motivo real | Pareja inferida | Fuente exacta consultada | Fotógrafo verificado | Fecha comprobación | Condiciones de uso / Licencia | Transformaciones aplicadas | Hash SHA-256 |
| :--- | :--- | :---: | :--- | :--- | :---: | :--- | :--- | :--- |
| `pieza-1.webp` | Vajilla de cerámica artesanal: pila de platos y cuencos esmaltados en tonos claros y carbón sobre estante de madera | 1 / 5 | `https://images.unsplash.com/photo-1578749556568-bc2c40e68b61` | No identificado fehacientemente (slug de entrega CDN sin perfil certificado en cabeceras; no se inventa autoría) | 2026-11-13 | [Unsplash License](https://unsplash.com/license) / [Terms](https://unsplash.com/terms) (Uso libre comercial y no comercial) | Encuadre centrado 4:3, redimensionado a 800x600 px, codificación WebP lossy (VP8), remoción de EXIF/metadatos/perfiles, tamaño 52.328 B (< 80.000 B) | `365d0360f061b84a4e4fa6e7dd4ca85231666a8d67c796bdb0aa7b4f27fb37a7` |
| `pieza-2.webp` | Alfarería en proceso: manos cubiertas de arcilla torneando una vasija en torno giratorio de alfarero | 2 / 6 | `https://images.unsplash.com/photo-1493106641515-6b5631de4bb9` | Jo Szczepanska (`@joszczepanska`) (verificado en catálogo Unsplash) | 2026-11-13 | [Unsplash License](https://unsplash.com/license) / [Terms](https://unsplash.com/terms) (Uso libre comercial y no comercial) | Encuadre centrado 4:3, redimensionado a 800x600 px, codificación WebP lossy (VP8), remoción de EXIF/metadatos/perfiles, tamaño 10.988 B (< 80.000 B) | `c15195fb60a6c4707710b4833d249c33cc99ed588fa8767e998ed4c23730972f` |
| `pieza-3.webp` | Vasos y vajilla: tazas y piezas de gres rústico esmaltado colocadas sobre estante de exhibición | 3 / 7 | `https://images.unsplash.com/photo-1610701596007-11502861dcfa` | No identificado fehacientemente (slug de entrega CDN sin perfil certificado en cabeceras; no se inventa autoría) | 2026-11-13 | [Unsplash License](https://unsplash.com/license) / [Terms](https://unsplash.com/terms) (Uso libre comercial y no comercial) | Encuadre centrado 4:3, redimensionado a 800x600 px, codificación WebP lossy (VP8), remoción de EXIF/metadatos/perfiles, tamaño 15.054 B (< 80.000 B) | `ab8b44ac2dc020b58ee8405635ce7bdd921287fa1446da058f2d1d41795042a1` |
| `pieza-4.webp` | Jarrones y floreros: vasijas de cerámica con ramas y semillas secas ornamentales sobre fondo oscuro | 4 / 8 | `https://images.unsplash.com/photo-1565193566173-7a0ee3dbe261` | No identificado fehacientemente (slug de entrega CDN sin perfil certificado en cabeceras; no se inventa autoría) | 2026-11-13 | [Unsplash License](https://unsplash.com/license) / [Terms](https://unsplash.com/terms) (Uso libre comercial y no comercial) | Encuadre centrado 4:3, redimensionado a 800x600 px, codificación WebP lossy (VP8), remoción de EXIF/metadatos/perfiles, tamaño 29.792 B (< 80.000 B) | `fc0abb484a8622db9e4265f30b282ca0a3a27978305734f5782defc0257ddf6d` |
| `pieza-5.webp` | Vajilla de cerámica artesanal: detalle secundario y encuadre cerrado de cuencos y platos apilados | 1 / 5 | `https://images.unsplash.com/photo-1578749556568-bc2c40e68b61` | No identificado fehacientemente (slug de entrega CDN sin perfil certificado en cabeceras; no se inventa autoría) | 2026-11-13 | [Unsplash License](https://unsplash.com/license) / [Terms](https://unsplash.com/terms) (Uso libre comercial y no comercial) | Encuadre centrado 4:3, redimensionado a 800x600 px, codificación WebP lossy (VP8), remoción de EXIF/metadatos/perfiles, tamaño 38.426 B (< 80.000 B) | `e07db765feefe119ed51d5418faa70727c4498675a84007fe6a2b2b608b98f2f` |
| `pieza-6.webp` | Alfarería en proceso: detalle de rotación al torno y modelado de arcilla con dedos | 2 / 6 | `https://images.unsplash.com/photo-1493106641515-6b5631de4bb9` | Jo Szczepanska (`@joszczepanska`) (verificado en catálogo Unsplash) | 2026-11-13 | [Unsplash License](https://unsplash.com/license) / [Terms](https://unsplash.com/terms) (Uso libre comercial y no comercial) | Encuadre centrado 4:3, redimensionado a 800x600 px, codificación WebP lossy (VP8), remoción de EXIF/metadatos/perfiles, tamaño 9.036 B (< 80.000 B) | `99d98672c9e6ab2749a92033bcf2f7271aa3841426bba841dfe928c626a10e91` |
| `pieza-7.webp` | Vasos y vajilla: detalle de textura y esmalte rústico de vasija de cerámica en estante | 3 / 7 | `https://images.unsplash.com/photo-1610701596007-11502861dcfa` | No identificado fehacientemente (slug de entrega CDN sin perfil certificado en cabeceras; no se inventa autoría) | 2026-11-13 | [Unsplash License](https://unsplash.com/license) / [Terms](https://unsplash.com/terms) (Uso libre comercial y no comercial) | Encuadre centrado 4:3, redimensionado a 800x600 px, codificación WebP lossy (VP8), remoción de EXIF/metadatos/perfiles, tamaño 13.604 B (< 80.000 B) | `6bb6ec6d24d6f5af9f4a886698e2a82c1157e528607c0c45e31a6595efd3bd4e` |
| `pieza-8.webp` | Jarrones y floreros: detalle de conjunto de botellas cerámicas ornamentales | 4 / 8 | `https://images.unsplash.com/photo-1565193566173-7a0ee3dbe261` | No identificado fehacientemente (slug de entrega CDN sin perfil certificado en cabeceras; no se inventa autoría) | 2026-11-13 | [Unsplash License](https://unsplash.com/license) / [Terms](https://unsplash.com/terms) (Uso libre comercial y no comercial) | Encuadre centrado 4:3, redimensionado a 800x600 px, codificación WebP lossy (VP8), remoción de EXIF/metadatos/perfiles, tamaño 27.104 B (< 80.000 B) | `0dd65b5c297d46af98b6194b356350876a2bff83d0231c4bae4785237ba522bc` |

*Nota sobre atribución e identificación*: Las parejas visuales (1/5, 2/6, 3/7, 4/8) corresponden a inferencias visuales entre los encuadres locales y las cuatro fotografías fuente, no a comprobaciones criptográficas contra los archivos fuente remotos. Donde la identificación del fotógrafo original no pudo contrastarse inequívocamente contra el catálogo público de Unsplash, se declara con rigor factual «No identificado fehacientemente» para no generar atribuciones erróneas o ficticias.

---

## 3. Matriz de Inventario y Procedencia de Audios de Demostración (`src/main/resources/static/assets/`)

Los 4 archivos de audio corresponden a muestras sonoras de síntesis de voz utilizadas para la botonera de demostración del jurado:

| Archivo | Motivo / Contenido declarado | Duración | Bitrate | Tamaño | Proveedor / Voz | Condiciones de uso | Hash SHA-256 |
| :--- | :--- | :---: | :---: | :---: | :--- | :--- | :--- |
| `demo-create.mp3` | Muestra de síntesis de voz para simular mensaje de alta de pieza | 3.120 s | 48 kbps | 18.720 B | No verificado fehacientemente (síntesis de prueba; no voz ni grabación real de Norma) | Recurso sintético local de prueba libre de derechos | `25bfcb4eeb43a6dfe1f671bc368f8b64cf85852f32ba0d2efc90b913465b7c15` |
| `demo-image.mp3` | Muestra de síntesis de voz para simular cambio de foto | 3.648 s | 48 kbps | 21.888 B | No verificado fehacientemente (síntesis de prueba; no voz ni grabación real de Norma) | Recurso sintético local de prueba libre de derechos | `aa435c531920ef9b9eb5dc229509910b85ad6d12b22d96ca56c5697162fcec46` |
| `demo-price.mp3` | Muestra de síntesis de voz para simular cambio de precio | 3.528 s | 48 kbps | 21.168 B | No verificado fehacientemente (síntesis de prueba; no voz ni grabación real de Norma) | Recurso sintético local de prueba libre de derechos | `e83836d7077b6c69ec40a2884b66e0fa1ee203ff1df78cace88aa30dc8ba1a6a` |
| `demo-sold.mp3` | Muestra de síntesis de voz para simular marcar pieza como vendida | 2.424 s | 48 kbps | 14.544 B | No verificado fehacientemente (síntesis de prueba; no voz ni grabación real de Norma) | Recurso sintético local de prueba libre de derechos | `0da71c5037e881f52cc87419e2bf82cc4d3b6684fdcb05429de5c5af7638dbcd` |

---

## 4. Límites y Trazabilidad de la Interfaz Web

- **Aclaración de operación en `app.js`**: En el modo de demostración para el jurado, al accionar un botón de muestra de audio, el navegador reproduce el archivo `.mp3` correspondiente mientras simultáneamente envía al backend la transcripción predefinida fija en texto (no envía el binario de audio al endpoint STT de Groq). Esto garantiza una evaluación fluida y determinista sin consumir cuota de tokens ni depender de conectividad externa con Groq Whisper.
- **Naturaleza de la voz**: Los audios fueron sintetizados mediante herramientas automáticas de prueba; no constituyen grabaciones de voz de una persona real llamada Norma. Que `MediaService.speak` utilice por defecto la voz `es-AR-TomasNeural` no acredita que los binarios estáticos preexistentes hayan sido generados con ese motor específico.
