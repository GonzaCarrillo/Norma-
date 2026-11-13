# Issue #6 — Vidriera accesible con SSE

Versión **1.0**, 2026-11-13. Autor: Codex, Software Architect.
Estado: **PROPUESTO; requiere aprobación versionada de Orca antes de construir**.
Tarea `task_eaba5fdac25d`, dispatch `ctx_5a2b5dee9884`.
Base inspeccionada: `876d3361fef76cf09e5f04b4ac8a1a56efb01b73` (`origin/main` local).
Rama: `GonzaCarrillo/issue-6-storefront`; worktree: `/home/gonzalocar/orca/workspaces/Norma-/issue-6-storefront`.
Issue consultada: [#6, Vidriera accesible con SSE](https://github.com/GonzaCarrillo/Norma-/issues/6), OPEN, dependencia actualizada a #1–#3 integradas.

## 1. Autoridad, fuentes y límites

Se inspeccionaron AGENTS.md, skill architect-spec, ambas especificaciones de raíz, docs/decisiones.md y backlog.json, el HTML/JS/CSS actuales, Api/Catalog/BotEngine/MediaService, esquema/semilla/migración/inicializador, configuración/POM y pruebas existentes pertinentes. Se consultaron diseño/aprobación #3 y su auditoría, cuyo anexo final v1.3 registra PASS (el FAIL inicial es histórico). No se ejecutó Maven, navegador, TTS ni prueba funcional en este diseño: los comportamientos descritos como actuales proceden de lectura de código; las verificaciones propuestas siguen pendientes.

El encargo de Orca autoriza Paralelo Controlado #4/#6 tras el filtro Codex `task_b51063ab4dd7`: #6 consume API/SSE integrados, #4 construye medios en su propio worktree. #5 no es dependencia funcional: lectura de compradores con speechSynthesis nativo, diferente del TTS del asistente. No se espera #5 ni se sustituye su trabajo. El orden numérico del backlog y estados históricos de decisiones.md no anulan esta autorización actual; esos documentos globales no se editan aquí.

Se preservan todos los criterios de #6. No se divide la issue, no se anticipa #7, no se cambia el catálogo canónico ni se reemplazan fotos/audio. No se añaden carrito, pagos, botones de compra, enlaces WhatsApp/comerciales ni frameworks/dependencias. El texto informativo sobre el WhatsApp habitual permanece como texto sin enlace. No se promete cumplimiento WCAG AAA integral, latencias universales, voz argentina instalada, disponibilidad continua ni entrega durable de eventos.

## 2. Hechos y brechas comprobadas

| Archivo / área actual | Hecho observado | Decisión propuesta |
|---|---|---|
| index.html | lang es-AR, skip link, main, grilla, status role=status, ayudas y dos dialog nativos ya presentes | Conservar estructura y separación del simulador; mejorar nombres/estados/foco sin nueva pantalla |
| app.js / tarjetas | Render por createElement/textContent; número, foto, título y precio es-AR; img 4:3, zoom con botón | Conservar contenido seguro y orden por pieceNumber; reconciliar tarjetas para conservar foco y zoom al refrescar |
| style.css / grilla | Columnas adaptativas y flex por tarjeta; h2 tiene min-height, pero un título largo puede aumentar una fila sin igualar todas | Igualar alto entre filas sin truncar títulos; ver §4 y prueba con 10/60 caracteres |
| style.css / letra | :root 22px; body.large cambia font-size, mientras botones/títulos/números/precios usan rem y no crecen | Aplicar ampliación a la raíz (22px → al menos 28.6px), con todas las medidas tipográficas escalables |
| style.css / diálogo | Zoom centrado de hasta 1000px, max-height imagen; no prueba de foco/Escape/viewport pequeño | Zoom de pantalla disponible, cierre grande siempre alcanzable por scroll, foco y Escape explícitos |
| app.js / voz | Cancel previo, voz es-AR si existe y lang es-AR; stop sólo se muestra al escuchar catálogo | Ciclo de reproducción/fin/error/cancelación consistente; detención también tras leer una pieza; fallback visible |
| app.js / carga | load reemplaza toda la grilla en cada GET; catch conserva el array previo pero sólo pide recargar página | Carga/vacío/error separados y reintento accesible, estado obsoleto visible; ninguna respuesta parcial destruye vista válida |
| app.js / concurrencia | load inicial, send, reset y SSE pueden disparar GET superpuestos; no hay control de orden | Un coordinador de refrescos con una consulta en vuelo y señal pendiente, evitando regresión por respuestas tardías |
| app.js / SSE | Un EventSource por página, catalog-update → load, error → mensaje; sin limpieza ni recuperación explícita del estado | Suscripción única, reconexión nativa y resincronización GET en cada apertura; estados de conexión separados de datos |
| Api / SSE | Primer evento catalog-update con data conectado; posteriores actualizado; timeout 300000ms, límite 5 streams por sesión; sin id/replay/heartbeat | Tratar todo evento como invalidación, nunca como producto/confirmación; no exigir cambio del backend |
| Api / bot | publish después de cada bot.process, incluso borrador/cancelar/no-op, además de reset | GET es autoridad: evento no implica mutación; probar catálogo sin cambio antes de confirmar |
| app.js / simulador | Existen formulario, 4 ejemplos, micrófono, sí/no, undo, reset, audio y fallback; comparten session/products/load/speak | Mantener IDs, listeners, campos multipart y comportamiento básico; únicamente adaptar conexiones con refresco/voz comunes |
| app.js / sesión | localStorage norma-session, UUID nuevo si falta, fallback en memoria si almacenamiento falla; query sessionId común | Conservar identidad por origen y aislamiento sandbox; validar token guardado antes de usarlo; no aceptar DEFAULT |

No se infiere que fotos actuales cumplan el pipeline de #4 ni se certifica su contenido humano por inspección de nombres. Se consumen las URLs existentes tal como las provee el catálogo; la procedencia y optimización de medios corresponden a #4.

## 3. Contratos invariantes (sin cambios backend)

- `GET /api/products?sessionId=<token codificado>` → 200 array de Product: `id`, `sessionId`, `pieceNumber`, `title`, `price`, `imageUrl`, `sold`. Sólo devuelve no vendidos, ordenados por número. El cliente usa identidad `(sessionId,pieceNumber)`, nunca id SQL (restore puede regenerarlo). No renumerar ni rellenar huecos. Precio entero positivo en pesos, formato `$ 18.500` sin centavos mediante Intl.NumberFormat es-AR. SessionId coincide con la sesión activa.
- Token existente: 8..80 caracteres `[a-zA-Z0-9_-]`, distinto de DEFAULT. Un UUID cumple. Validar localStorage sin registrar tokens; reemplazar sólo valores inválidos de la clave propia. Almacenamiento no disponible → token por vida de página y aviso de que al recargar puede iniciar otra sesión. No añadir login, cookie de autorización, parámetro URL para apropiarse de otra sesión ni canal global. Pestañas del mismo origen comparten sesión deliberadamente; dos sesiones requieren perfiles/contextos de almacenamiento separados.
- `GET /api/catalog/stream?sessionId=...` → text/event-stream, evento nombrado `catalog-update`, data textual `conectado` o `actualizado`. Sin payload de catálogo, revision, Last-Event-ID ni orden durable. Hay SSE también antes de confirmar: no aplicar cambios optimistas. `open` significa transporte abierto; sólo un GET exitoso acredita datos sincronizados. No esperar cinco minutos para probar reconexión.
- `POST /api/bot/message`, multipart `sessionId`, `text`, `image`, `audio` opcionales conforme contrato vigente: texto y audio no vacíos juntos → 400; foto puede acompañar uno. Reply conserva `replyText`, `replyAudioUrl` nullable y `state` (IDLE/AWAITING_DETAILS/AWAITING_CONFIRMATION). La FSM de #3 decide confirmación, cancelación, deshacer de un paso y números. No interpretar texto de Reply como catálogo ni condicionar GET al audio.
- `POST /api/reset?sessionId=...` → 200 objeto `message`, publica SSE, repone 8 piezas sólo de la sesión y limpia borrador/snapshot; no reduce high-water. Acción existente de evaluación; no añadir confirmación nueva ni reset automático por vacío/error/reconexión.
- `GET /api/ping` devuelve PONG; `/assets/*` y `/media/**` permanecen rutas actuales. No introducir rutas alternativas ni objetos DTO nuevos.
- Errores existentes: 400 validación con `{message}`, 413 archivo grande, 500 mensaje genérico. La vidriera convierte fallos de red, HTTP, JSON inválido o respuesta inesperada en error comprensible y reintento, sin mostrar stack, HTML del servidor ni detalles internos. Escribir títulos/mensajes con textContent. Validar forma básica de array/campos usados antes de publicar vista; nunca convertir 500/JSON inválido en catálogo vacío. URLs de imagen sólo rutas locales de assets/media del contrato; ninguna navegación comercial ni interpretación HTML.

No cambian Product, Session, Reply, FSM, validación de servidor, catálogo, esquema SQLite v2, DEFAULT inmutable, numeración monótona ni conexión Hikari única. El aislamiento es sandbox por token, no autenticación ni autorización fuerte.

## 4. Interfaz, uniformidad y accesibilidad verificables

HTML semántico en español argentino, estética papel cálido/terracota, fuentes locales de sistema; sin textura o ilustración generada para representar piezas. Header con ayudas, main con estados y catálogo, footer y acceso de evaluación separado. Tarjeta: número, foto interactiva, título completo, precio. Sólo la foto abre zoom; no agregar acciones comerciales.

**Grilla:** 4:3 constante para fotos, object-fit cover sin deformación y dimensiones de reserva. Columnas de ancho igual que se adaptan hasta una columna; tarjetas con cuerpo flex y precio alineado. Para igualar TODAS las filas, usar filas implícitas de igual fracción con altura intrínseca suficiente para la tarjeta más alta (por ejemplo grid-auto-rows:1fr) y no fijar un alto que recorte texto. Verificar además última fila, títulos 10/60 caracteres, palabra larga, fuente ampliada y zoom del navegador; el criterio se prueba con rectángulos reales, no sólo leyendo CSS. Tolerancia de redondeo ≤1 CSS px entre alturas de tarjetas de un mismo render. Si una imagen falla: espacio 4:3 reservado, texto “No pude cargar la foto de la pieza…” y resto de datos legibles, sin loop de recarga; deshabilitar o explicar ampliación no disponible y mantener foco útil. No sustituir por una pieza inventada.

**Letra y reflow:** todo texto visible (incluidos botones, estados, inputs, ejemplos, mensajes, precios y diálogo) con tamaño calculado ≥22 CSS px en modo normal. Control Letra más grande con aria-pressed y etiqueta alterna; modo ampliado aumenta raíz al menos 30%, no sólo body. No reducir tipografía en media queries ni escalar con transform. Preservar zoom del navegador, sin maximum-scale/user-scalable=no. A 320 CSS px y a 200% de zoom: sin scroll horizontal de la página ni recortes/solapamientos; texto puede extender alto y diálogos permiten scroll vertical. Botón evaluación no tapa última tarjeta ni controles; reservar espacio o integrarlo al flujo en móvil. No conservar desplazamiento lateral de header/main con simulador abierto si vuelve ilegible la grilla.

**Contraste:** medir colores calculados de cada pareja texto/fondo en estado normal, hover, focus, error, vacío, placeholder y controles, incluidos simulador, diálogos y modo ampliado. Objetivo estricto >7:1 (no redondear 7.00 a aprobado); usar luminancia relativa sRGB y `(Lclaro+0.05)/(Loscuro+0.05)`. Elegir parejas con margen, conservar tema artesanal sin texto terracota claro sobre papel. Evitar opacity en contenedor disabled que degrade texto; la deshabilitación se comunica también por atributo y estilo de borde. Documentar tabla de colores/ratios medidos. Contraste de foco y límites no textuales se revisa separadamente (objetivo ≥3:1), sin usar contraste de texto como certificado AAA de toda la página.

**Teclado/foco:** skip link visible al enfocarse lleva a piezas; orden DOM y tabulación coherentes, controles button nativos con nombres claros; Enter/Espacio abren la foto. Zoom nativo `dialog` modal con nombre que identifica pieza, foto con alt útil, botón gigante Volver a las piezas y foco inicial en ese botón. Tab/Shift+Tab permanecen dentro, Escape y botón cierran, ambos cancelan lectura de pieza y devuelven foco al disparador. No cerrar al hacer clic dentro de la foto ni exigir puntero. Si SSE cambia precio/foto de esa pieza, actualizar contexto sin mover foco; si la vende, cerrar zoom, cancelar voz y enfocar siguiente foto disponible, anterior o encabezado de piezas con tabindex=-1 cuando ya no haya piezas. Si refresh elimina el control enfocado fuera de zoom, aplicar igual fallback. Si sólo cambian datos, preservar nodos por pieceNumber y no robar foco (especialmente dentro del simulador). Evitar overlays simultáneos: abrir simulador cierra zoom primero. Cerrar simulador devuelve foco a Modo evaluación como hoy pretende su control. Probar Escape y foco en ambos; no rediseñar chat de #7.

**Anuncios:** estados de datos y conexión con role=status/aria-live=polite, mensajes breves sin leer toda la grilla tras cada SSE; aria-busy durante carga. Error accionable junto a botón Reintentar; nunca depender sólo de color. La región completa de tarjetas no es aria-live. Datos anteriores visibles tras error llevan aviso explícito de que pueden estar desactualizados. Un éxito de datos no borra aviso de transporte desconectado, ni un open borra un error GET.

## 5. Lectura nativa de compradores

Acciones del usuario inician la voz: Escuchar catálogo lee snapshot ordenado de productos; abrir foto lee número, título y precio de la pieza. Idioma es-AR, elegir voz es-AR si disponible y voz española como alternativa si existe, dejando selección final al navegador. No descargar voces, llamar TTS backend ni esperar #5. Si getVoices está vacío, no bloquear indefinidamente; usar idioma solicitado y atender voiceschanged para futuras lecturas, sin arrancar voz sola.

Antes de nueva lectura cancelar la anterior. Durante cualquier lectura de comprador, Detener/Dejar de escuchar está disponible; debe alcanzarse también desde el zoom modal (botón de detención dentro del diálogo, cierre conserva además su función). onend/onerror/cancel restauran el estado coherente de controles. Detener cancela cola, no deja texto “leyendo” ni reinicia voz por SSE. Si cambia el catálogo durante lectura, cancelarla y anunciar que hay cambios y puede volver a escuchar, para no seguir enumerando precios/piezas viejos. No leer automáticamente al refrescar/reconectar.

Detectar tanto speechSynthesis como SpeechSynthesisUtterance y fallos de reproducción. Sin soporte/voz operativa: aviso legible “Este navegador no ofrece lectura en voz alta. Podés leer las piezas en pantalla”, ayudas no engañosas y catálogo/zoom plenamente utilizables. No arrojar ReferenceError desde detener/cerrar si API falta. Probar soporte ausente, cola, fin normal, error, cambio de lectura y cierre por Escape. La voz del simulador conserva replyAudioUrl con fallback textual/nativo existente; los ajustes al helper común no deben ocultar replyText ni impedir enviar/confirmar. La detención solicitada por el usuario cancela síntesis nativa; no prometer parar audios ajenos del sistema.

## 6. Coordinador de refresco y transporte SSE

Responsabilidades internas en app.js, sin nuevos archivos públicos ni framework: estado de sesión, consulta/render de catálogo, conexión SSE, voz/zoom y adaptadores del simulador existente. No reorganizar backend ni crear arquitectura global nueva.

Estado separado: `datos = cargando | listo | vacío | error`, catálogo válido más reciente y bandera obsoleto; `conexión = conectando | abierta | reconectando | noDisponible`. Un catálogo [] válido es vacío y no dispara reset. Reintentar manual consulta GET; no envía comandos ni crea otra sesión.

Flujo de invalidación (pseudocódigo de contrato, no implementación):

1. Crear una suscripción EventSource y solicitar carga inicial. Todas las fuentes (inicio, catalog-update, open, envío/reset exitoso y reintento manual) llaman al mismo refresco.
2. Cada solicitud marca pendiente. Si ya hay GET en vuelo, no lanzar otro: se hará una consulta adicional al terminar. Cada vuelo captura generación de invalidación; si entró otra invalidación, descartar su respuesta para render y consultar otra vez. No aplicar respuestas tardías anteriores a una invalidación conocida.
3. GET con tiempo límite finito (10 segundos), cache no-store y AbortController. Liberar estado ocupado en finally. Publicar array validado y reconciliar DOM sólo si sigue vigente la generación; error vigente conserva último catálogo y muestra reintento. Cada nueva invalidación/reintento puede recuperar un error. Sin bucle inmediato infinito por un GET fallido; no usar polling continuo.
4. onerror deja EventSource reconectar por mecanismo nativo; marcar reconectando, conservar datos, no construir streams adicionales en cada error. onopen pide GET aunque no haya evento nuevo; el evento inicial conectado también invalida y se agrupa por el coordinador. Al volver la red el próximo GET válido recupera los cambios perdidos. Si EventSource pasa a CLOSED de manera permanente, mostrar acción explícita Reconectar que cierra la instancia anterior antes de crear una nueva. Reintentos manuales no acumulan listeners ni streams.
5. Si EventSource no existe: GET inicial funciona y el aviso ofrece Actualizar catálogo manualmente; no afirmar tiempo real. Si está disponible pero falla, estado visible y acciones recuperables, sin overlay que bloquee teclado.
6. pagehide cierra EventSource, aborta GET y cancela síntesis; pageshow tras bfcache puede crear exactamente una suscripción y refrescar. No dejar flag ocupado atascado ni reutilizar vuelo/instancia de una página cerrada.

La garantía es convergencia al catálogo vigente después de GET exitoso tras invalidación/apertura; no replay de cada mutación ni exactamente una notificación. Hay una ventana inevitable si la red pierde un evento; reconectar siempre resuelve por snapshot GET. No cambiar heartbeat, timeout, límite de streams o mensajes de Api. Fallo backend reproducible que impida criterios → detener parte afectada y elevar a Orca, sin reparación fuera del scope.

## 7. Preservación del simulador existente

Conservar IDs `sim`, `open`, `close`, `chat`, `message`, `text`, `photo`, `record`, `yes`, `no`, `undo`, `reset`, `bot-status`, `reply-audio` y data-demo create/price/image/sold, o demostrar adaptación completa de todos sus consumidores si un cambio mínimo fuese indispensable. Preferencia: no renombrarlos. Conservar FormData y sessionId compartido con productos/SSE; controles de confirmación, cancelación y undo conservan semántica #3; reset permanece explícito. send/reset deben llamar al coordinador de refresco, respetar busy y no quedar bloqueados por una voz no disponible. No implementar reconocimiento de voz nuevo, ejemplos nuevos, integración WhatsApp ni nueva gestión conversacional de #7.

Regresión de navegador requerida: abrir/cerrar por teclado; formulario texto → propuesta de precio → cancelar sin cambio; repetir → sí → precio actualizado; venta → sí → retiro; undo → vuelve; reset → 8 piezas y sesión B intacta. Comprobar foto+texto, cuatro botones de ejemplo, micrófono no soportado/denegado y ausencia de voz muestran fallback útil y no rompen el formulario. La disponibilidad real de micrófono/TTS es límite ambiental a registrar, no PASS inventado ni excusa para omitir la regresión textual.

## 8. Pruebas y evidencia de aceptación

Sólo dos nuevas clases Java; fixtures/harness específicos pueden ir en docs/orchestration/issue-6-evidence/. No modificar pruebas existentes ni agregar librerías. MockMvc no ejecuta JS/CSS: sus aserciones no acreditan teclado, contraste, render ni reconexión del navegador.

**StorefrontContractTest:** Spring Boot + MockMvc, SQLite real en archivo aislado bajo target propio, datos por sesión; probar estáticos 200 y vínculos de recursos/controles, contrato GET inicial 8, esqueleto Product/campos/números/precios/URLs esperados; invalidación de sesión (incluido DEFAULT) y mensaje 400; catálogo vacío real (fixture de test por Catalog.restore, sin tocar DEFAULT) permanece [] tras GET; precio/venta antes y después de confirmación vía multipart y cancelación; undo/reset y aislamiento B. Probar Reply null audio utilizable. No hacer tests que sólo busquen nombres de funciones CSS/JS como sustituto de comportamiento.

**StorefrontSseTest:** Spring Boot RANDOM_PORT (server.port=0), servidor HTTP real, SQLite real aislado, Java HttpClient/lector SSE de eventos separados por línea vacía. No sustituir SseEmitter ni llamar publish como evidencia funcional. En ambas clases se permite reemplazo exclusivamente de MediaService en configuración anidada de prueba (@Primary/mock con root válido bajo target, siguiendo precedente BotApiTest): speak devuelve null, transcribe controlado si se ejercita audio, image devuelve ruta local fixture. Api/BotEngine/Catalog y transporte reales; no red Groq, binario TTS ni procesador #4 en estas pruebas. Bean real que eventualmente se instancie también recibe uploads aislados y clave vacía.

Secuencia SSE obligatoria:

- Abrir stream A y B, verificar status/content-type y evento inicial catalog-update/conectado; mantener lectores vivos y colas por stream con identificador local de recepción. GET inicial A/B registrado.
- POST precio por multipart a A: consumir evento actualizado, GET mantiene precio previo y Reply pide confirmar. POST No: nuevo evento y GET sin cambio. Repetir propuesta, consumir evento, POST Sí: esperar evento posterior y GET A con nuevo precio; B sin cambio. No confundir evento inicial o de borrador con evento de confirmación: drenar/barrear cada paso y comprobar el dato persistido.
- Venta confirmada A: GET posterior al evento omite pieza. Undo: GET posterior la repone. Reset: GET posterior devuelve 8 canónicas; B continúa intacta. Aislamiento: B no recibe eventos de mutación A durante ventana negativa acotada (por ejemplo 750ms tras completar cada paso) y sí recibe su propia mutación como control positivo. Comparar además datos B para no depender sólo de ausencia temporal.
- Cerrar conexión A, mutar A mediante API mientras está desconectada, reabrir y recibir conectado; GET refleja cambio perdido. Esta prueba acredita recuperación del protocolo por snapshot; prueba de EventSource/reconexión automática del cliente es separada en navegador.
- Connect timeout 3s, espera de evento ≤5s, límite total de escenario ≤30s; ausencia esperada B usa timeout corto, sin bloquear readLine principal. Cerrar InputStreams, streams A/B, futures, ejecutores y clientes en finally/try-with-resources; cerrar contextos y DB antes de limpieza. No dormir cinco minutos ni leer stream completo con body String (nunca termina). No exigir entrega durable ni cero eventos para borradores.

**Matriz de navegador / evidencia medible** (ejecutada por Antigravity y repetida independientemente por OpenCode sobre SHA final):

| ID | Escenario | Evidencia necesaria |
|---|---|---|
| V1 | 320/375/768/1440 CSS px; títulos 10 y 60 caracteres, palabra larga; normal/ampliado, 200% zoom | Capturas con viewport y rectángulos de tarjetas, foto 4:3, igualdad de alturas ≤1px, sin scroll horizontal ni solapamiento; no truncar títulos |
| V2 | 22px mínimo y aumento ≥30% en todo texto; contraste textual >7:1 en estados/componentes | Reporte de estilos calculados y tabla de pares/ratios incluyendo hover/focus/disabled/placeholder, con colores y fórmula; inspección de foco visible |
| V3 | Sólo teclado: skip, herramientas, foto, Tab inverso, Escape, cierre, retorno; modal durante SSE y pieza vendida | Registro de activeElement antes/durante/después, nombres accesibles, orden y comportamiento real; captura modal móvil sin botón inaccesible |
| V4 | Voz presente, ausente, error, fin, stop, lectura pieza y catálogo; cambio durante voz | Harness/stub sólo navegador para inspeccionar utterance/lang/cancel/handlers; prueba nativa donde disponible y límites de voces declarados; toda acción sigue disponible sin voz |
| V5 | Carga pendiente, [] válido, HTTP 500, red caída, JSON inválido, foto fallida y reintento | Inyección controlada sólo de respuesta en navegador con registro de fixture; status correcto, datos anteriores marcados obsoletos, recuperación real; captura cada estado |
| V6 | SSE real con dos páginas A mismo token y B distinto; POST hecho por otra página/API, sin usar send/load local de la vista observada | Red muestra catalog-update y GET posterior; precio/venta antes/después de confirmar, cancelar, undo/reset y B intacta; probar rafaga y respuestas tardías del GET con harness dirigido |
| V7 | Red interrumpida/reanudada o cierre de conexión: reconexión EventSource real; cambio durante corte, bfcache/retorno | Traza red y DOM convergente tras apertura, aviso recuperado, una suscripción viva por página; soporte EventSource ausente documentado con actualización manual |
| V8 | Simulador existente y no regresión §7 | Transcript de controles/formulario, cuatro ejemplos conectados y fallback micrófono/voz; cambios sólo confirmados, undo/reset y B sin cambios |

Para V1/V5 se permiten fixtures técnicos (títulos sintéticos, imágenes geométricas identificadas como prueba) sólo en pruebas/evidencia, inyectados sin editar seed ni assets. No crear fotos AI de cerámica ni presentar fixtures como catálogo real. V6/V7 y prueba Java SSE usan servicio real; una simulación de EventSource aislada sólo complementa casos de API ausente o carreras, no reemplaza transporte real. Cuatro ejemplos con foto pueden usar fixtures técnicos controlados en una instancia de auditoría; distinguir esto de verificación del pipeline #4. Una captura no acredita por sí sola accesibilidad ni actualización funcional.

## 9. Aislamiento y recursos

- Toda escritura dentro del cwd/rama asignados; target de este checkout para DB, WAL/SHM, uploads, temp JVM y binarios/harness efímeros. Nuevos tests crean subdirectorios únicos por clase/contexto dentro de target y usan @DynamicPropertySource para datasource URL/`norma.uploads`/clave vacía; no basename fijo ni SQLite del repo. @TempDir sólo con parent explícito bajo target o java.io.tmpdir propio; no copiar patrón de temp global por inercia. Cerrar pool antes de borrar archivos; conservar logs/evidencia sin datos sensibles.
- Maven secuencial por checkout (no compilar mientras hay otra ejecución Maven local). No hay razón demostrada para serializar Maven con #4: target y DB son distintos. Serializar sólo un recurso fijo realmente compartido e identificado; no desactivar pruebas para forzar paralelo.
- Verificar JDK Java 25 y Maven reales. Ejecutar limpieza y verificación completas en fase builder/QA, con tmp JVM y uploads bajo target propios y groq.api.key vacía. Las suites heredadas usan Files.createTempDirectory/@TempDir sin parent explícito y algunos contextos usan uploads por defecto: encauzarlos mediante propiedades JVM y Spring por ejecución, sin editar esas pruebas. Secuencia reproducible equivalente a clean verify: `mvn -B clean`; después crear `target/issue6-runtime/tmp` y `target/issue6-runtime/uploads`; después `mvn -B verify` con `-DargLine=-Djava.io.tmpdir=<cwd-absoluto>/target/issue6-runtime/tmp`, `-Dnorma.uploads=<cwd-absoluto>/target/issue6-runtime/uploads` y `-Dgroq.api.key=`. Cada argumento con ruta se cita como un argumento completo según shell. No ejecutar ambos Maven simultáneamente; el primer clean elimina target y por eso los directorios se crean después. No guardar evidencia final sólo en target. Capturar ambos comandos/exit codes, versiones y XML reales; verificar en el fork de pruebas las propiedades efectivas y que ningún contexto use DB/uploads ajenos. Si una suite ignora estos overrides y necesita recurso externo, reportar bloqueo a Orca antes de ejecutarla así; no editarla fuera del scope ni ocultar el límite. No sobreescribir argLine previo si hubiera agentes/opciones necesarios: conservarlos y añadir sólo tmpdir.
- Navegador con servidor de esta pista en puerto asignado dinámicamente (`--server.port=0` y leer puerto efectivo del log/Orca ports); no compose8080 ni volumen norma-data. Usar origen/puerto diferente de #4 y contextos A/B aislados; no intercambiar tokens ni confiar en aislamiento por path de URL. No arrancar dos procesos sobre la misma DB.
- Para navegador sin red TTS, usar PATH del proceso apuntando primero a un stub local `target/.../bin/edge-tts` que falla inmediatamente sin red, con fallback existente; clave Groq vacía y temp/uploads/DB propios. Es recurso efímero, no cambio productivo ni configuración global. Probar controles de grabación sin enviar audio real a proveedor. Si se usa servidor Spring de harness de prueba con MediaService stub, declararlo y seguir usando Api/BotEngine/Catalog reales. No certificar STT/TTS ni optimización de medios.
- Evidencia durable sólo bajo issue-6-evidence, con SHA del árbol auditado, versiones/OS/navegador, viewport, puerto/origen (sin token), fecha, comandos, resultados y límites. No registrar claves, audios personales, tokens completos ni datos de otras sesiones. No tocar procesos ni almacenamiento de otra pista.

## 10. Lista cerrada de archivos y gates

Archivos autorizados durante el ciclo completo de #6:

1. `src/main/resources/static/index.html`
2. `src/main/resources/static/style.css`
3. `src/main/resources/static/app.js`
4. Nueva `src/test/java/com/norma/taller/StorefrontContractTest.java`
5. Nueva `src/test/java/com/norma/taller/StorefrontSseTest.java`
6. `docs/orchestration/issue-6-design.md`
7. `docs/orchestration/issue-6-approval.md`
8. `docs/orchestration/issue-6-build-report.md`
9. `docs/orchestration/issue-6-qa.md`
10. `docs/orchestration/issue-6-codex-approval.md`
11. `docs/orchestration/issue-6-evidence/` exclusivamente para evidencia/fixtures/harness de esta issue.

Esta entrega de Codex crea **únicamente issue-6-design.md**. No fabricar aprobaciones, QA ni reportes de construcción vacíos. Runtime target es efímero y no se versiona.

Prohibido modificar Api (incluidos MediaConfig/ApiErrors), MediaService/PhotoProcessor, Product/Session/Reply, Catalog, BotEngine/modelos/parser/codec, SQL/migración/seed/inicializador, fotos/audio/assets, POM/dependencias, application.properties, configuración global, pruebas existentes, docs de medios/operación/decisiones/backlog o archivos de #4. Ninguna refactorización para sortear esta frontera. Necesidad de cruzarla → bloqueo a Orca antes de tocar; no partir issue ni eliminar criterio.

Secuencia y responsables (orden explícito del encargo, con validación previa de diseño):

- **G0 Codex diseño → Orca validación:** revisar y aprobar v1.0 en issue-6-approval.md con SHA, scope y criterios. Sin aprobación no construir. No hay agentes lanzados por Codex.
- **B1 Antigravity:** construir vista/estados/grilla/letra/zoom/voz en estáticos, preservar simulador. Verificar V1–V5 y regresión dirigida; entregar commit atómico con reporte a Orca. Esperar gate antes de B2.
- **B2 Antigravity:** completar coordinación SSE y pruebas nuevas contrato/transporte, V6–V8 y regresión completa; `clean verify` y evidencia visual/funcional atribuibles al commit final. Dejar todos los cambios propios commiteados en rama, inventario exacto y limitaciones. No merge/publicación/cierre.
- **G1 OpenCode QA independiente:** Orca verifica disponibilidad e identificador real del modelo solicitado antes de despacharlo, sin sustitución silenciosa. Audita commit builder contra v1.0, ejecuta regresiones/compilación y matriz proporcional completa; PASS/FAIL en issue-6-qa.md con entorno/comandos/resultados/evidencia. Defectos regresan a Antigravity; QA no arregla producción.
- **G2 Codex Approval:** revisar conformidad final del código y de evidencia QA sobre mismo SHA y versión aprobada, emitir PASS/FAIL en issue-6-codex-approval.md. Preguntas de diseño vuelven vía Orca. Un cambio posterior obliga a reevaluar pruebas/conformidad afectadas; no reutilizar PASS sin justificar identidad del código.
- **G3 Orca aceptación/integración/publicación:** sólo con criterios V1–V8, contratos y SSE real satisfechos y ambos PASS correspondientes al final. Dos issues activas máximo desde diseño hasta merge (#4/#6). Actualizar base de la pista restante al integrar antes de iniciar siguiente issue; si integración cambia contenido relevante revalidar. Orca mantiene aceptación/cierre, no Codex.

Riesgos residuales expresos: voces y reproducción dependen de navegador/gesto/OS; SSE sin replay depende de resincronización; tokens sandbox no autentican; assets existentes no prueban #4; inspección de contraste/teclado no certifica WCAG AAA integral. No hay bloqueo de diseño demostrado para cumplir #6 dentro del scope al momento de esta lectura; toda garantía funcional queda pendiente de implementación y evidencia real.
