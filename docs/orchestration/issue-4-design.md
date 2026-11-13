# Diseño de issue #4 — Fotografías y medios de demostración

Versión **1.0**, 2026-11-13. Autor: **Codex / Software Architect**.
Estado: **propuesto para aprobación de Orca; no autoriza construcción**.
Issue: [#4](https://github.com/GonzaCarrillo/Norma-/issues/4).
Base inspeccionada: **876d3361fef76cf09e5f04b4ac8a1a56efb01b73**.
Rama: `GonzaCarrillo/issue-4-media`; worktree: `/home/gonzalocar/orca/workspaces/Norma-/issue-4-media`.
Tarea `task_464a2852775d`, dispatch `ctx_fd49307a09d6`.

## 1. Fuentes, evidencia y discrepancias

Se leyeron AGENTS.md vigente, skill architect-spec, ambas especificaciones raíz, issue #4 mediante `gh issue view 4 --json title,body,state,url`, backlog, medios, operación, decisiones, diseño/aprobación/auditoría #3, MediaService, Api/MediaConfig/ApiErrors, POM, propiedades, semilla/esquema, contratos de Catalog/inicializador, suites versionadas y consumidor app.js. `gh issue view` confirma #1–#3 CLOSED y #4 OPEN. La auditoría #3 v1.3 está registrada en commit **1ad780a**: su PASS corresponde a implementación **cdabcae** y reporte **f5de345**, no constituye una prueba del pipeline de imágenes. Su evidencia histórica informa 130 pruebas versionadas; no se ejecutaron esas pruebas otra vez ni se presume que existan sus temporales en este worktree.

Inspección actual: `git status --short` vacío al comenzar; rama y HEAD corroborados con Git. `ORCA_WORKTREE_ID` y `ORCA_WORKSPACE_ID` identifican este cwd; las variables literales `ORCA_WORKTREE_PATH`/`ORCA_WORKSPACE_NAME` no están exportadas en esta sesión. Se opera exclusivamente aquí. ApiTest.java e IntentFallback.java ajenos **no existen en este checkout**: según instrucción de Orca permanecen en la raíz anterior; no se copiaron, modificaron ni versionaron. No se certifica su contenido a partir de los hashes históricos de #3.

| Área | Hecho observado en fuentes/archivos | Brecha que resuelve #4 |
|---|---|---|
| Dependencias | Thumbnailator 0.4.20 y org.sejda.imageio:webp-imageio 0.1.6 declarados; Java25/Boot3.5.16 | No cambiar stack; falta lectura explícita de orientación antes de perder metadata |
| Entrada | image() rechaza vacío, >15×1024² bytes y >24.000.000 píxeles antes de read(0) | Reader elegido por detección pero sin allowlist; errores de decodificación pueden resultar 500; sin límite por eje ni política de animación/color |
| Transformación | BufferedImage → Thumbnailator size(800,600).crop(CENTER) | No hay corrección de exposición/calidez ni tratamiento EXIF explícito; no se acredita orientación ni geometría sólo leyendo esa llamada |
| Codificación | UUID.webp directo en root, calidad float decreciente; devuelve si Files.size<80000 | Tipo de compresión se toma del índice0 sin identificar lossy; número de iteraciones depende de float; en fallos intermedios puede quedar archivo parcial; sin publicación atómica |
| Serving | /media/** mediante resource handler sobre root; errores genéricos en ApiErrors | No se midió MIME, 404 ni traversal; no hay zona de staging privada |
| Configuración | 15MB por parte, 20MB por petición; NORMA_UPLOADS externo | Conservar límites y no trasladar temporales a un volumen ajeno |
| Tests | BotApiTest tiene 6 métodos, mockea MediaService.image; FlowTest usa URLs, otras suites prueban FSM/SQL | No hay suite versionada que compruebe pixels, EXIF, codec WebP real, compresión fallida o serving físico |
| Recursos | 8 WebP y 4 MP3 directamente en assets; seed y app.js usan esas URLs | No existen carpetas seed-images/demo-audios de la estructura ilustrativa; conservar URLs para no tocar SQL/UI |
| Documentación | medios declara cuatro fuentes Unsplash y ocho encuadres; operación declara voz sintética | Falta correspondencia archivo→fuente/transformación y detalle de procedencia; no son ocho tomas independientes ni obras verificadas de Norma |

`docs/decisiones.md` aún dice #2 pendiente de reauditoría y #3 pendiente: es estado histórico desactualizado frente a issues cerradas y auditoría v1.3; actualizar sólo esas etiquetas en construcción, preservando las decisiones. Las specs raíz contienen diseños ilustrativos superados (paquetes, DDL, rutas de audio, configuración, garantías de latencia): rigen las decisiones aprobadas #1–#3, no se reintroducen sus ejemplos.

### Inventario local comprobado

Pillow abrió los ocho archivos como **WEBP RGB 800×600, sin EXIF**, todos con bytes estrictamente menores que 80000. Se inspeccionaron visualmente los ocho: 1/5 platos y cuencos, 2/6 jarrones, 3/7 vasos, 4/8 manos trabajando al torno; cada par usa encuadres diferentes del mismo motivo. Esto acredita contenido/encuadre local, **no demuestra autoría, licencia ni ausencia de IA por sí solo**. La procedencia humana es declaración existente que debe trazarse a las fuentes en B3.

| URL bajo /assets/ | Bytes | SHA-256 actual |
|---|---:|---|
| pieza-1.webp | 52328 | 365d0360f061b84a4e4fa6e7dd4ca85231666a8d67c796bdb0aa7b4f27fb37a7 |
| pieza-2.webp | 10988 | c15195fb60a6c4707710b4833d249c33cc99ed588fa8767e998ed4c23730972f |
| pieza-3.webp | 15054 | ab8b44ac2dc020b58ee8405635ce7bdd921287fa1446da058f2d1d41795042a1 |
| pieza-4.webp | 29792 | fc0abb484a8622db9e4265f30b282ca0a3a27978305734f5782defc0257ddf6d |
| pieza-5.webp | 38426 | e07db765feefe119ed51d5418faa70727c4498675a84007fe6a2b2b608b98f2f |
| pieza-6.webp | 9036 | 99d98672c9e6ab2749a92033bcf2f7271aa3841426bba841dfe928c626a10e91 |
| pieza-7.webp | 13604 | 6bb6ec6d24d6f5af9f4a886698e2a82c1157e528607c0c45e31a6595efd3bd4e |
| pieza-8.webp | 27104 | 0dd65b5c297d46af98b6194b356350876a2bff83d0231c4bae4785237ba522bc |

`ffprobe` leyó duración de MP3, sin tags de autor/voz/proveedor recuperados; no se escucharon ni transcribieron en este diseño. Esas duraciones no prueban que el texto coincida con el ejemplo dinámico de app.js.

| URL bajo /assets/ | Bytes | Segundos | SHA-256 |
|---|---:|---:|---|
| demo-create.mp3 | 18720 | 3.120 | 25bfcb4eeb43a6dfe1f671bc368f8b64cf85852f32ba0d2efc90b913465b7c15 |
| demo-image.mp3 | 21888 | 3.648 | aa435c531920ef9b9eb5dc229509910b85ad6d12b22d96ca56c5697162fcec46 |
| demo-price.mp3 | 21168 | 3.528 | e83836d7077b6c69ec40a2884b66e0fa1ee203ff1df78cace88aa30dc8ba1a6a |
| demo-sold.mp3 | 14544 | 2.424 | 0da71c5037e881f52cc87419e2bf82cc4d3b6684fdcb05429de5c5af7638dbcd |

## 2. Decisiones y compatibilidad

Se propone endurecer solamente la responsabilidad de fotografía y verificar recursos locales. `MediaService.image(MultipartFile): String throws IOException` permanece como fachada; un **PhotoProcessor package-private**, sin componente Spring ni endpoint nuevo, concentra validación, transformación, codificación y almacenamiento. MediaService lo construye con su root; mantiene constructor público, campo root y métodos transcribe/speak intactos. Una costura package-private de encoder permite provocar fallos/tamaños en pruebas sin depender de la entropía de una fotografía; el camino normal siempre usa el provider WebP instalado.

Se conserva monolito, paquete plano, Java25, Boot, Thumbnailator, provider WebP, multipart, DTO Reply y enum FSM. Nada de ImageMagick, procesos nuevos, servicios de imagen, IA, reconocimiento de objetos, recorte semántico, filtros generativos, nuevas tablas ni propiedades de voz. No se cambia codec, parser, motor, Catalog, inicializador, SQL ni high-water. La imagen se procesa **antes** de entrar al motor transaccional; el motor sigue siendo quien decide si conserva la URL como borrador y si publica después de confirmar.

El contrato HTTP aprobado #3 permanece: sessionId validada, texto+audio no vacíos juntos dan 400 antes de medios; límite de texto 1000; foto puede acompañar texto o audio; STT ocurre antes de foto y puede fallar sin procesarla; TTS después del motor puede devolver null. No cambiar orden, SSE/publicación, confirmación, corrección ni undo. Falta de foto o parte image de longitud0 sigue significando **sin foto en el endpoint**, porque Api la omite; llamada directa a image() con parte vacía es error 400. No convertir ausencia en error ni exigir nuevos campos.

## 3. Entrada, formatos y límites

1. Tamaño real admitido: **1 a 15728640 bytes inclusive (15MiB)**. Comprobar getSize y además contar streaming hasta límite+1 al copiar a staging; no confiar sólo en metadata multipart ni usar getBytes sin cota. >límite detectado por servicio da400; cuando lo corta el contenedor multipart conserva 413 existente. Petición completa 20MiB y límite por parte 15MiB no cambian. No igualar ambos errores alterando el API.
2. Tipo real por firma de contenedor **y** formato del ImageReader: allowlist JPEG/JPG, PNG y WebP estáticos. Ignorar extensión/nombre/MIME declarados para decidir formato: WebP de app.js puede llegar con nombre foto.jpg. JPEG válido declarado application/octet-stream se acepta; texto/PDF/SVG/GIF/BMP/TIFF/HEIC/HEIF/AVIF/RAW no se aceptan aunque digan image/jpeg. Un plugin adicional no amplía la allowlist. HEIC común en algunos teléfonos debe exportarse/compartirse como JPEG; no prometer cualquier formato de cámara.
3. Cabecera antes de raster: width,height positivos, cada eje≤**16384**, producto en long≤**24000000**, ambos inclusive. Rechazar antes de read(0) si exceden. Sólo raster de hasta 8 bits por canal RGB/grises/indexado y alfa opcional; PNG16bits, HDR y JPEG CMYK/YCCK no soportados deben solicitar conversión a JPEG RGB/PNG8. No hacer reinterpretaciones de color silenciosas; JPEG YCbCr habitual decodificado por ImageIO a RGB sí se admite. Revisar tipos de imagen del reader antes de raster y confirmar ColorModel/SampleModel después.
4. Rechazar contenedores animados (PNG con acTL, WebP con flag ANIMATION/ANIM/ANMF), longitudes de chunks/segmentos fuera del archivo, cabeceras truncadas o firma contradictoria. No seleccionar silenciosamente el primer frame de una animación. Leer imagen completa; null, error o advertencia de decodificación por datos dañados/truncados se traduce a imagen inválida400. No confundir IOException del filesystem con contenido inválido. No se promete detección universal de archivos políglotas ni aislamiento de bugs nativos.
5. Presupuesto de aplicación: **un procesamiento de foto activo por instancia**, mediante Semaphore.tryAcquire, sin cola de imágenes en memoria. Si está ocupado, IOException→500 genérico existente y reintento del cliente, sin mutar conversación. Liberar permiso en finally en cualquier salida. No serializar STT/TTS ni transacciones; el permiso cubre sólo image(). Esto limita multiplicación concurrente de rasters, no fija un SLA ni elimina la memoria multipart del contenedor.
6. Como máximo un raster fuente completo de 24MP (hasta 96MB en representación8bit de 4 canales), más buffers pequeños y memoria del decoder. No crear otra copia orientada de24MP: transformación geométrica sobre región fuente hacia el destino reducido. Cerrar readers/streams y liberar referencias por etapa. Registrar memoria pico/heap/RSS y caso 24MP en entorno de construcción; la cota de píxeles **no es una prueba de un techo total de memoria** ni asegura compatibilidad con todo heap. No resolver un OOM aumentando límites o capturando OutOfMemoryError para continuar.

## 4. Orientación, recorte y revelado determinista

### Metadata y dependencia propuesta

Añadir únicamente **com.drewnoakes:metadata-extractor:2.19.0**, con su transitiva Maven normal registrada en reporte; no incorporar otro decoder. Lee EXIF/TIFF; la transformación de píxeles sigue en Java/Thumbnailator. Se justifica porque el BufferedImage actual ya perdió EXIF y no debe dependerse de autorrotación implícita de Thumbnailator al pasarle un raster. Alternativa descartada: implementar un parser TIFF propio o inferir orientación por relación de lados.

Leer **sólo bloques EXIF**, no expandir zTXt/iTXt/iCCP, XMP, miniaturas ni otros datos no necesarios. Extraer APP1 Exif de JPEG, eXIf de PNG o EXIF de WebP mediante recorrido de contenedor con longitudes validadas; suma EXIF máxima **262144 bytes**. Pasar esos bytes a ExifReader con offset TIFF adecuado (saltar Exif\0\0 cuando exista), no ImageMetadataReader genérico sobre todas las metadata. Sólo la orientación de IFD0 gobierna la foto; orientación de miniatura no la reemplaza. Sin EXIF/orientación:1. Orientación fuera de 1–8, bloques EXIF malformados, excesivos o múltiples orientaciones IFD0 contradictorias:400 foto inválida; no aceptar un fallo de metadata como orientación 1. Errores reportados por las Directory de EXIF cuentan como metadata malformada. No copiar metadata a salida ni registrar GPS/datos personales.

Fuentes primarias consultadas para esta propuesta: [metadata-extractor y coordenadas Maven](https://github.com/drewnoakes/metadata-extractor), [ExifReader 2.19.0](https://raw.githubusercontent.com/drewnoakes/metadata-extractor/2.19.0/Source/com/drew/metadata/exif/ExifReader.java), [PNG eXIf 2.19.0](https://raw.githubusercontent.com/drewnoakes/metadata-extractor/2.19.0/Source/com/drew/imaging/png/PngMetadataReader.java). El soporte descrito en esas fuentes no sustituye pruebas del adaptador JPEG/PNG/WebP de #4. [Thumbnailator](https://github.com/coobird/thumbnailator/wiki/FAQ) delega formatos en ImageIO; [provider WebP](https://github.com/sejda-pdf/webp-imageio) documenta reader/writer y binarios nativos, cuya disponibilidad efectiva se debe probar en el entorno destino.

### Geometría normativa

Interpretar primero EXIF en un sistema lógico orientado. Para un píxel fuente(x,y) y dimensiones crudas W,H:

| EXIF | Coordenada lógica(x',y') | Dimensiones lógicas |
|---|---|---|
| 1 | (x,y) | W,H |
| 2 | (W−1−x,y) | W,H |
| 3 | (W−1−x,H−1−y) | W,H |
| 4 | (x,H−1−y) | W,H |
| 5 | (y,x) | H,W |
| 6 | (H−1−y,x) | H,W |
| 7 | (H−1−y,W−1−x) | H,W |
| 8 | (y,W−1−x) | H,W |

Sobre dimensiones lógicas Wo,Ho: `k=min(floor(Wo/4),floor(Ho/3))`. Si k=0 rechazar foto demasiado chica400. Región lógica de recorte: ancho4k, alto3k; origen `(floor((Wo−4k)/2),floor((Ho−3k)/2))`. Cuando sobra píxel impar queda a derecha/abajo. Esta definición da4:3 exacto sin estirar: se elimina margen, no se cambian proporciones del objeto.

Mapear la región a coordenadas crudas mediante la inversa EXIF; usar Thumbnailator para el redimensionado uniforme de esa región (destino previo a rotación600×800 si EXIF intercambia ejes;800×600 en otro caso), y aplicar orientación/reflexión a resolución reducida. Resultado final **800×600 exacto**, incluso para entradas menores (upscale uniforme explícito). No forceSize sobre un rectángulo de otra proporción. No autorrotar una segunda vez; no usar detección de objetos. Un recorte puede cortar bordes de una pieza: se centra geométricamente, no se promete conservar todo el objeto ni reconstruirlo.

Convertir a RGB8 sRGB y componer alfa sobre **blanco #FFFFFF** antes del revelado. El perfil embebido puede ser interpretado por ImageIO si lo soporta; salida sRGB sin perfil/GPS/EXIF. Rechazar conversiones fallidas en vez de producir colores arbitrarios; no prometer gestión de color de imprenta.

### Corrección sutil exacta

Sobre cada canal sRGB8 ya compuesto/reducido, una sola vez: `R'=clamp(round(R*1.03*1.01))`, `G'=clamp(round(G*1.03))`, `B'=clamp(round(B*1.03*0.99))`, donde clamp limita a0..255 y round usa redondeo al entero más cercano con .5 hacia arriba para valores positivos. Es ganancia fija+3% y sesgo cálido±1%; no estima iluminación ni usa IA, auto white balance, contraste local, sharpening o histogramas. Se aplica siempre al upload y nunca de nuevo dentro de cada intento de compresión. Negro sigue negro; blanco satura a blanco; un gris medio debe seguir la fórmula en la etapa previa al codec. No se declara recuperación de detalle quemado/subexpuesto ni fidelidad cromática exacta tras WebP con pérdida. Los ocho recursos existentes no se recodifican repetidamente para aparentar este paso.

## 5. Compresión, éxito y fallos

- Usar el **modo con pérdida identificado explícitamente** del provider instalado; no asumir que getCompressionTypes()[0] es lossy. Registrar reader/writer/clase/modo efectivos. Falta de writer/reader WebP o carga nativa fallida es fallo de capacidad del servidor 500, nunca devolver una URL .webp con JPEG/PNG adentro ni recurrir silenciosamente a otro stack.
- Lista de calidades exacta e inmutable, por enteros: **[80,70,60,50,40,30,20,10]/100f**. Como máximo8 codificaciones para una entrada. Cada intento usa el mismo raster revelado800×600, writer/stream nuevos o reiniciados correctamente y archivo vacío/truncado nuevo; no recodificar la salida previa, no acumular bytes ni usar bucle float decreciente.
- Éxito sólo con candidato **0<bytes<80000**:79999 admite,80000 rechaza. Detener en el primer candidato admisible; comprobar archivo cerrado, firma/formato WebP, decodificación y dimensiones800×600, y ausencia de metadata identificatoria antes de publicar. La salida no hereda orientación: ya está aplicada a píxeles.
- Si los8 candidatos son≥80000, error 400 «No pude reducir esta foto a menos de 80 KB. Probá con otra.» y limpieza. No disminuir resolución ni superar límite para forzar éxito; no se promete éxito para cualquier contenido. Un candidato malformado del encoder es fallo500 del servidor, no una foto exitosa. IOException de almacenamiento o fallo nativo/writer también500 genérico; no transformar cualquier excepción inesperada en culpa de la usuaria.
- Los intentos acotan trabajo de codificación, **no tiempo de ejecución universal**: ImageIO/JNI no ofrece aquí cancelación dura segura. No crear futures/hilos con timeout que sigan consumiendo memoria tras responder; no afirmar «ultrarrápido»,400ms ni interrupción garantizada de codec.

## 6. Almacenamiento, limpieza y entrega HTTP

`root` es el NORMA_UPLOADS absoluto y normalizado, creado por MediaService como hoy. Sólo el operador configura esa ruta. Crear `root/.processing/` privado al resource handler; ahí viven original temporal y candidatos, con nombres aleatorios generados por servidor. No incorporar nombre de cliente, sessionId, título o extensión suministrada al path. Todos los accesos resuelven debajo de root, sin seguir enlaces que escapen de su ruta real; no procesar un staging preexistente que sea symlink.

Publicación: escoger UUID aleatorio nuevo (`UUID.randomUUID().toString()+".webp"`); candidato validado se mueve desde staging hacia `root/<uuid>.webp` con **ATOMIC_MOVE**, sin REPLACE_EXISTING. Staging dentro del mismo root permite mismo filesystem/volumen. No exponer bytes parciales: si ese filesystem no soporta move atómico, abortar500 y documentar requisito, sin fallback a copy de archivo público. Si el nombre ya existe, elegir otro UUID antes de mover, sin sobrescribir; el directorio es de escritura exclusiva del proceso/operador confiable. URL retornada exactamente `/media/<uuid>.webp`; nunca se retorna ruta física. Uploads con mismo nombre original producen URLs diferentes. Mantener espacio de nombres histórico de imágenes y MP3; no renombrar URLs guardadas.

`finally` elimina **sólo los temporales de esa llamada**, libera reader/writer/streams/permiso en vacío, inválida, límite, agotamiento y errores de I/O. Si la validación/postpublicación interna falla antes de retornar URL, eliminar también el final creado por esta llamada. Si la limpieza falla por filesystem, preservar el error principal y registrar error de limpieza sin payload/metadata; reportarlo como fallo operativo verificable, no garantizar borrado físico bajo permisos/disco roto. No borrar todos los staging de otras llamadas o sesiones al arrancar. Restos tras caída del proceso se inventarían para mantenimiento posterior, no se limpian a ciegas.

**Límite deliberado:** después de retornar URL, el archivo pertenece al ciclo existente de conversación. Si el motor falla, ignora foto en una confirmación o conserva otra URL, puede quedar un archivo final sin referencia. No introducir una transacción filesystem/SQL ni modificar Api.message/codec/motor para eliminarlo: #3 excluyó limpieza física de medios. Tampoco borrar al cancelar, resetear, cambiar foto o deshacer: snapshot/borrador u otra referencia puede necesitar la foto antigua. La retención/cuota/recolección segura quedan como riesgo operativo ya documentado, no resuelto por la limpieza de candidatos fallidos de #4.

**MediaConfig** mantiene `/media/**`, agregando un PathResourceResolver local que impida servir `.processing` (incluidas variantes normalizadas/escapadas) y recursos cuya ruta real escape de root. No enumerar directorios. Conservar serving de MP3 y archivos históricos, sin imponer UUID a URLs viejas. WebP debe responder `Content-Type: image/webp`, MP3 `audio/mpeg`, sin depender del MIME de upload; comprobar también `/assets/pieza-N.webp` y `/assets/demo-*.mp3`. Sólo si la configuración nativa no produce esos tipos, registrar explícitamente mappings webp/mp3 mediante `MediaConfig.configureContentNegotiation`; no habilitar negociación por query ni alterar produces/consumes REST. Esta es la única posible configuración MVC global compartida: declarar en reporte si el hunk resultó necesario.

Un recurso ausente, staging o traversal denegado devuelve404 sin bytes privados. El advice genérico actual puede convertir NoResourceFoundException en500: agregar tratamiento **limitado a solicitudes /media/** para404 `{ "message": "No encontré ese archivo." }`; en otras rutas delegar al comportamiento genérico existente. No reconfigurar errores de endpoints REST aprobados ni prometer un body especial para rechazos previos del contenedor HTTP. Tests deben comprobar el bloqueo con paths simples y codificados. No agregar caché, CDN, nuevas rutas o permisos por sesión: UUID evita colisiones, no constituye autorización. El sandbox sigue siendo público.

| Caso de image() | HTTP por API existente | Mensaje público/efecto |
|---|---|---|
| Vacía directa o >15MiB en servicio | 400 | «La foto está vacía o pesa más de 15 MB.»; endpoint omite parte vacía |
| Formato no soportado | 400 | «Mandame una foto JPEG, PNG o WebP válida. Si está en HEIC, exportala como JPEG.» |
| Raster/metadata malformada, animación, color/profundidad no soportados | 400 | Aviso específico en español sin excepción interna; pedir foto estática JPEG/PNG8/WebP |
| >24MP o eje>16384 | 400 | «La foto es demasiado grande. Usá hasta 24 megapíxeles y 16384 píxeles por lado.» |
| Recorte lógico menor que4×3 | 400 | «La foto es demasiado chica. Mandame una más grande.» |
| Ocho candidatos sin cumplir tamaño | 400 | Mensaje de compresión anterior; sin URL ni restos de llamada |
| Contenedor corta parte/petición | 413 | ApiErrors.large existente, sin procesar motor |
| I/O, proveedor ausente, codec inesperado, procesamiento ocupado | 500 | ApiErrors.error existente: «No pude completar el pedido. Intentá otra vez.» |

Todos los400/500 de imagen antes del motor conservan productos y Session completa preexistente (pending/temp_image/snapshot/next_number incluidos). No se promete ausencia de STT previa para foto+audio: el orden aprobado la permite. No deben disparar procesamiento de bot/TTS/SSE por esa solicitud fallida.

## 7. Ocho recursos y procedencia humana

Aceptación propuesta: **ocho recursos fotográficos locales, derivados de cuatro fotografías humanas existentes**, como declara medios.md. La issue no exige ocho autores ni ocho tomas independientes; este diseño no lo afirmará. Mantener las URLs `/assets/pieza-1.webp` a8 y los binarios actuales si se puede verificar su origen; ya cumplen dimensiones y tamaño. La escena al torno es demostrativa y los títulos semilla no certifican correspondencia exacta con la pieza retratada. No cambiar títulos/SQL para aparentar esa correspondencia.

B3 debe completar docs/medios.md con una fila por recurso: motivo real, fuente exacta que se haya consultado, fotógrafo sólo si verificado (o «no identificado»), fecha de comprobación, enlace a las condiciones de uso de esa fuente, transformaciones conocidas y hash final. Fuentes **declaradas actualmente**, no revalidadas en este diseño:

- images.unsplash.com/photo-1578749556568-bc2c40e68b61;
- images.unsplash.com/photo-1493106641515-6b5631de4bb9;
- images.unsplash.com/photo-1610701596007-11502861dcfa;
- images.unsplash.com/photo-1565193566173-7a0ee3dbe261.

La URL actual unsplash.com/photos/1578749556568-bc2c40e68b61 parece usar identificador de entrega de imagen como slug de página: **no certificar esa página ni inventar atribución**; comprobar fuente y corregir enlace sólo con evidencia. La pareja visual1/5,2/6,3/7,4/8 es inferencia de correspondencia entre encuadres locales, no una comprobación criptográfica contra originales remotos. Si no puede verificarse un origen/permiso, registrar bloqueo a Orca; sustituir sólo archivos de assets autorizados por fotografías humanas de origen verificable y medir de nuevo. No inventar autor, licencia, fecha de descarga original ni afirmar que son obras de Norma. No es necesario guardar originales de alta resolución con GPS en static.

Para demos, inventariar las cuatro URLs MP3/hashes/duración y declaración «síntesis de demostración, no voz ni grabación real de Norma». Proveedor/voz/fecha exactos quedan «no verificados» si falta registro: que MediaService.speak use TomasNeural no prueba que los binarios se generaron con él. En app.js se reproduce MP3 pero se envía **texto conocido**, no STT; los textos de precio/foto/venta se construyen con destino dinámico y no se acredita coincidencia universal con el MP3 fijo. Documentar esta limitación para #5/#7, sin regenerar audios, rediseñar voz o tocar UI en #4. La evaluación por texto y estos recursos locales debe funcionar sin descargar fotografías/audios de terceros en tiempo de ejecución.

## 8. Lista cerrada de archivos y recursos compartidos

Este despacho de diseño modifica **únicamente este documento**. Tras aprobación, la construcción puede modificar solamente:

| Archivo | Cambio autorizado |
|---|---|
| src/main/java/com/norma/taller/MediaService.java | Delegación image a PhotoProcessor y preparación segura de root; mantener constructor/root y transcribe/speak |
| src/main/java/com/norma/taller/PhotoProcessor.java (nuevo) | Pipeline y helpers/costura package-private de encoder; sin framework/SQL/red |
| src/main/java/com/norma/taller/Api.java | Sólo MediaConfig resolver privado/MIME si necesario y ApiErrors404 de /media; Api endpoints/message/publish intactos |
| pom.xml | Sólo dependencia metadata-extractor2.19.0; no upgrades ni otros cambios |
| src/test/java/com/norma/taller/PhotoProcessorTest.java (nuevo) | Geometría, validación, EXIF, revelado, compresión, limpieza y límite de concurrencia |
| src/test/java/com/norma/taller/MediaHttpTest.java (nuevo) | Real image + MVC/HTTP multipart/serving, aislamiento y regresión de Reply |
| src/test/java/com/norma/taller/DemoMediaTest.java (nuevo) | 8 recursos + 4MP3, dimensiones/formato/bytes/hashes según inventario final |
| docs/medios.md | Matriz/procedencia y límites de demos verificados |
| docs/operacion.md | Contrato de imagen, staging, atomic move, memoria/concurrencia y limpieza limitada |
| docs/decisiones.md | Decisiones #4 aprobadas y corrección de estado histórico #2/#3; preservar ADR previos |
| docs/orchestration/issue-4-build-report.md (nuevo) | Commit/entorno/comandos/resultados/evidencia y límites reales |
| src/main/resources/static/assets/pieza-1.webp, pieza-2.webp, pieza-3.webp, pieza-4.webp, pieza-5.webp, pieza-6.webp, pieza-7.webp, pieza-8.webp | Sólo si procedencia no puede acreditarse y Orca valida sustitución en B3; conservar nombres/URLs; sin IA |

No se necesitan fixtures binarios nuevos: generar en tests geometría de colores/círculos y contenedores corruptos; obtener casos derivados JPEG/PNG con EXIF insertado determinísticamente desde un seed local. Son fixtures técnicos sin IA; no se presentan como fotografías nuevas tomadas con un teléfono. Una foto real adicional para evidencia manual requiere autorización de incorporación/licencia, no usar el worktree anterior. Pruebas deben construir sus datos bajo temporales **dentro del worktree asignado** o su exportación autorizada, limpiando sólo propios; no usar bases/uploads de otra sesión.

Fuera de alcance: application.properties (conservar 15/20MB), AGENTS.md, backlog/coordinación ajena, tests existentes, ApiTest/IntentFallback ajenos, toda la UI app.js/index/style, MP3 existentes, Docker/CI, motor/codec/model/parser/Catalog/inicializador/SQL. Si una brecha requiere tocarlos, detener esa parte y volver a Orca; no expandir scope para facilitar paralelismo.

**Entidades y configuración global:** no cambia Product/Session/Reply ni SQLite v2, DEFAULT, snapshot o FSM; cambia implementación de image, almacenamiento root/.processing, dependencia/classpath, resolución MVC /media y potencial registro de MIME global. Constantes de imagen quedan en PhotoProcessor; no agregar variables de entorno. Comparte MediaService con #5 y Api/MVC/estáticos/POM con posibles trabajos web/pruebas: este documento no declara independiente otra issue ni autoriza otro worktree.

**Recursos de pruebas:** DB SQLite nueva por contexto, archivo bajo directorio aislado del test; una conexión Hikari como contrato. NORMA_UPLOADS nuevo por suite/contexto, sin rutas comunes ni instancia real en 8080. MockMvc para contrato; para413 real usar RANDOM_PORT (`server.port=0`) porque MockMvc no reproduce límites de parseo multipart. Sin Groq, claves, edge-tts ni navegador/micrófono compartido: usar spy sobre MediaService real para stub de transcribe/speak únicamente. No mutar ImageIO global (`scanForPlugins`, registry, setUseCache) para simular ausencia de writer; inyectar encoder/capacidad interna. Ejecutar Maven secuencial dentro del checkout; no competir por target ni sqlite/upload de otro contexto. Un eventual probe de heap se ejecuta secuencialmente y declara memoria/puerto/paths propios; no requiere proceso global fijo.

## 9. Matriz de aceptación y verificaciones previstas

Nada de esta tabla ha sido ejecutado como aceptación de #4 en este despacho. Las propiedades actuales de assets se observaron con Pillow/ffprobe y revisión visual, no con el futuro pipeline Java.

| ID | Comportamiento a comprobar | Evidencia mínima |
|---|---|---|
| V1 | JPEG baseline/progresivo, PNG8 RGB/gris/indexado/alfa y WebP estático válidos; extensión/MIME distintos no alteran detección | Provider real, decodificar salida; JPEG y WebP enviados ambos con filename foto.jpg admiten |
| V2 | Vacío directo, basura disfrazada, formatos excluidos/HEIC, cabecera truncada, datos dañados, metadata corrupta/excesiva/contradictoria, animación, PNG16/CMYK |400 específico; antes/después del directorio iguales; inválidos HTTP no alteran Session/Product; vacío HTTP conserva semántica sin foto |
| L1 |15MiB exactos admitidos por tamaño,15MiB+1 rechazado; stream que miente en getSize;24MP exactos vs+1; eje16384 vs16385; producto long | Pruebas de validación previas al raster; un caso real de24MP decodificado y memoria registrada; cabecera enorme demuestra que no se llamó decode |
| L2 | Multipart parte>15MiB/petición>20MiB | Servidor random-port con cliente real devuelve413 y mensaje actual; sin bot/media/SSE; no sustituir por MockMvc |
| O1 | Orientaciones EXIF 1–8 en JPEG/PNG/WebP, ausencia=1, IFD0 no thumbnail | Fixture asimétrico con cuadrantes/etiquetas; comparar resultado esperado por tabla, incluyendo espejos 5/7; endian TIFF II/MM |
| G1 | Horizontal, vertical, cuadrada, dimensiones impares, panorámica y pequeña |800×600; márgenes descartados conforme fórmula, centro correcto, círculo sigue circular dentro de tolerancia de interpolación/WebP; k=0 rechaza |
| C1 | Revelado exactamente una vez, RGB/gris/alfa sobre blanco, saturación0/255 | Aserciones de píxeles antes del encoder contra fórmula; comparación postcodec con tolerancia declarada; dos ejecuciones mismo raster/pixels con entorno fijo |
| W1 |WebP real lossy, tamaño estricto, metadata ausente | Decodificar archivo real y comprobar formato/dimensiones/bytes<80000; no basta extensión o mock |
| W2 | Reintenta con calidades/lista exacta y termina; 79999 acepta/80000 no, éxito posterior y ocho fallos | Encoder controlado sólo para bordes/fallos; cada intento recibe mismo raster; caso imposible400 y ningún final/temporal; no basar imposible en una foto aleatoria |
| F1 | Fallo en copiar, decode, writer, validación final, move atómico no soportado; limpieza fallida reportada | Inyección local mínima donde filesystem real no permite reproducir; resto integración filesystem real; recursos/permiso liberados y bytes viejos intactos |
| F2 | UUID nuevos, mismo filename cliente, nombre ../escape.jpg, dos cargas concurrentes | URLs distintas, sin paths cliente; un solo decode activo, segunda carga ocupada500 sin espera; siguiente carga válida funciona; ninguna llamada borra datos de otra |
| H1 |GET WebP subido, MP3 histórico y assets, faltante, staging y traversal simple/codificado |200+MIME correcto para válidos,404/sin bytes privados para denegados; ningún fichero parcial servido durante write; probar root con espacios |
| A1 |Foto real→DETAILS; foto+texto→CONFIRMATION; confirmar→publicación; IMAGE→confirmar→undo restaura URL antigua | Real MediaService.image/encoder+Catalog/BotEngine, STT/TTS stub; preservar Reply y productos antes de sí; URL antigua sigue accesible tras undo/reset; no ampliar semántica FSM |
| A2 |Imagen inválida en DETAILS y CONFIRMATION, texto+audio simultáneos, error de almacenamiento | Comparar Session entera, Product, snapshot/next_number y no SSE/bot/TTS en fallo; validación mixta ocurre antes de image |
| D1 |Ocho WebP locales y cuatro MP3 sin dependencia remota | URLs existentes200/tipos; ocho ratios y tamaño; manifiesto de procedencia y revisión visual honesta de parejas/motivos; sin atribución ficticia |
| R1 |Regresión de #1–#3 y evidencia reproducible | Todas las suites versionadas previas intactas, clean verify completo, resultados por suite/commit, sin contar ApiTest externo |

No exigir identidad binaria universal del WebP entre plataformas/JNI; determinismo significa mismo orden, región, fórmula, lista de calidades y decisión de aceptación. En mismo entorno fijado se pueden comparar píxeles/bytes para detectar aleatoriedad; documentar plataforma y provider. Visual QA de fotografías no acredita WCAG, STT ni TTS. Si se mide tiempo, registrar entrada, bytes, CPU/RAM/JDK/provider, calentamiento y distribución; no convertir400ms o catálogo completo en requisito universal sin medición.

## 10. Plan secuencial con gates

**G0 — Aprobación Orca.** Aprobar esta versión/hash y sus decisiones explícitas: JPEG/PNG8/WebP estáticos y límites cámara; EXIF/dependencia; fórmulas de crop/revelado;8 intentos / 80000 estricto/sin reducción de resolución; un slot sin cola/error 500 por ocupación; staging privado/move atómico/404 de medios; ocho recursos de cuatro originales y verificación de procedencia; limpieza limitada/lista cerrada. Sólo entonces Orca despacha construcción a Antigravity con formato AGENTS. Ningún paso siguiente comienza mientras el anterior no esté aprobado.

**B1 — Entrada y transformación (Antigravity).** POM, PhotoProcessor, delegación image y casos V1/V2/L1/O1/G1/C1. Mantener voz/API sin cambios de comportamiento. Reportar comandos/entorno/capacidad provider, memoria del caso 24MP y fallos reales; commit atómico de archivos autorizados. **Gate Orca B1** antes de B2; dudas de diseño regresan a Codex vía Orca.

**B2 — Compresión, almacenamiento y serving (Antigravity).** W1/W2/F1/F2/H1 y Api limitado a MediaConfig/404; preservar URLs legacy. Registrar clase/modo WebP, límites de limpieza/memoria y cualquier mapping MIME global añadido. Commit acotado y **gate Orca B2** antes de B3.

**B3 — Recursos, integración y evidencia (Antigravity).** D1, matriz fuente por archivo y demos, docs, L2/A1/A2/R1. Si procedencia no se verifica, elevar bloqueo; no generar imágenes ni afirmar permiso. No tocar binarios sanos sin motivo. `mvn -B -Dtest=PhotoProcessorTest,MediaHttpTest,DemoMediaTest test` por etapas cuando existan y después `mvn -B clean verify`, secuencialmente con JDK25. Registrar versiones, comandos exactos, exit codes, reportes Surefire, casos ejecutados, hashes y límites. No afirmar PASS si se omite algún caso. Entregar reporte y commits en rama; política de push para builder la fija Orca, no este diseño.

**G4 — OpenCode QA independiente.** Por instrucción explícita posterior del coordinador, el orden de esta entrega es **Codex diseño → Antigravity → OpenCode QA → Codex Approval → aceptación Orca**, que prevalece sobre el orden anterior del AGENTS. Orca comprueba modelo/configuración disponible antes de despachar; Codex no lo lanza ni sustituye. OpenCode audita commit exacto en worktree/datos aislados, compila/ejecuta matriz y regresiones, entrega PASS/FAIL y defectos reproducibles. Builder corrige producción ante FAIL y QA reverifica el commit nuevo.

**G5 — Codex Approval final.** Después del QA, Codex revisa conformidad del commit final contra diseño aprobado, scope, límites y evidencia QA; emite PASS/FAIL sin implementar arreglos. Si hubo cambios relevantes tras QA, no reutilizar aquel PASS sin justificar correspondencia; volver a verificar lo afectado. **G6 — Orca acepta/cierra** sólo con ambos PASS atribuibles a versión final y criterios completos; este despacho no integra, despliega ni cierra #4.

La entrega del **diseño** incluye revisión documental/diff, commit sólo de este archivo y push normal de su rama a origin, autorizados explícitamente por Orca en mensaje posterior al encargo original que decía no push. No se hace push main ni se inicia construcción. Riesgos abiertos para aprobación: cap de 24MP no equivale a techo total de RSS; compatibilidad JNI depende de plataforma; disponibilidad de move atómico del volumen; procedencia original aún no revalidada; cuatro fotos fuente para ocho encuadres; retención de finales y demos de voz quedan fuera de este pipeline. Ninguno de esos puntos está presentado como capacidad ya verificada.
