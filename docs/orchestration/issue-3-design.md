# Issue #3 — Conversación confirmada y deshacer

Versión **1.0**, 2026-11-12. Codex, Software Architect.
Estado: **PROPUESTO, pendiente de aprobación de Orca; no autoriza construcción**.
Tarea `task_3c323d5d4628`; dispatch `ctx_dc21a864fb93`.
Base inspeccionada: `97f24ff`; catálogo aprobado: `09d1246`, auditoría #2 v1.3 PASS.

## 1. Evidencia, alcance y contratos ya aprobados

Se leyeron AGENTS.md, ambas especificaciones de raíz completas, diseño/auditoría #2, BotEngine, IntentParser, IntentFallback, Catalog, inicializador, schema.sql, Api, MediaService, configuración, POM, FlowTest, ApiTest y pruebas de catálogo (contratos, persistencia, migración y rollback). GitHub consultado mediante `gh issue view`: [#3](https://github.com/GonzaCarrillo/Norma-/issues/3) OPEN exige parser argentino, estados persistentes, cuatro operaciones, confirmar, cancelar/corregir y rollback de un paso; #1 y #2 están CLOSED. #5 corresponde a Groq/STT/fallback/edge-tts; #8 a verificación integral, CI y despliegue. No se ejecutaron compilación ni pruebas en esta fase de diseño; los PASS previos pertenecen a #2.

Se conserva lo aprobado en #2: esquema SQLite v2 exacto, seed DEFAULT inmutable, una conexión Hikari, WAL, sessionId validado por Catalog.valid, identidad pública `(sessionId,pieceNumber)`, `all` incluye vendidos, `restore` puede regenerar ids SQL y conserva high-water, `reset` limpia conversación y snapshot sin rebajar contador. No cambiar firmas/records públicos de Catalog ni DTO REST. La especificación llama bot_sessions a lo que el repositorio ya resuelve como sessions: no renombrar.

“Ningún cambio sin confirmar” se refiere a productos públicos: persistir un borrador, una foto temporal o inicializar una sesión nueva no es publicar una acción. UNDO inmediato y reset explícito son excepciones expresas de las especificaciones; no añaden otra confirmación. SOLD oculta la pieza mediante sold=true, nunca DELETE. No se crean operaciones de renombrado, reactivar vendido, varias acciones en un mensaje ni historial multinivel.

Todas las políticas siguientes de FSM, gramática y JSON son **decisiones nuevas a aprobar por Orca**, no garantías heredadas del PASS de #2.

## 2. Brechas comprobadas del código actual y trabajo previo

| Componente | Brecha observada | Resolución propuesta |
|---|---|---|
| IntentParser.price | Busca primer fragmento de dígitos; puede truncar decimales, aceptar varios montos, confundir #2 con precio y aceptar gramática hablada inválida | Consumir expresión de precio completa y única; distinguir referencia, título y monto antes de normalizar |
| IntentParser.parse | Regex por subcadenas (`saca`, `cambia`), CREATE por defecto y coincidencias de título por contains | Gramática de acciones explícitas y CREATE reconocible, resolución única sin adivinar; Unknown/NeedsDetails tipados |
| IntentParser.Intent | kind libre y campos nullable permiten combinaciones imposibles | Nuevo dominio sellado para FSM; conservar record legacy como adaptador a Catalog.apply |
| BotEngine | Deserializa pendiente/snapshot sin validar forma/estado/sesión; corrección solo de precio; errores de JSON producen 500 sin recuperación conversacional | Codec estricto, invariantes y recuperación descritas abajo |
| BotEngine | Transacción exterior ya une snapshot/apply/limpieza y restore/consumo, pero no prueba sus fallos ni duplicados concurrentes | Mantener unidad atómica, sin capturar errores SQL dentro; pruebas con fallo real y concurrencia |
| BotEngine diff previo | Añade inyección IntentFallback y llamada de red en process @Transactional | Preservar durante diseño; solicitar a Orca autorizar retirar exclusivamente el acoplamiento del motor al implementar #3 |
| IntentFallback sin seguimiento | Integración Groq real, prompts/JSON/reparse y timeouts; puede reinterpretar monto ambiguo | Pertenece a #5; no incorporarla a #3 ni ejecutarla desde FSM, incluso si hay clave |
| Api | STT e imagen antes de bot, TTS después (correcta separación); audio reemplaza texto; publica SSE en todos los mensajes | Mantener API y comportamiento de publicación actual en #3; no certificar SSE #6 ni voz #5. Ajustar solo mezcla texto/audio explícita descrita en §8 |
| FlowTest | Cubre flujo feliz pero no regresiones parser, rollback fallido, JSON inválido o carreras | Conservar y complementar pruebas conductuales |
| ApiTest sin seguimiento | catalogAndValidation útil como antecedente; imageOptimization es medios #4, consolidación general #8 | Preservar íntegro, no agregar/modificar/versionar este archivo en #3; crear prueba API acotada nueva |

No hay partes de implementación previa listas para declararse aceptadas. Orca puede autorizar refactorizar el BotEngine versionado **incluyendo la retirada de las dos adiciones locales** (campo/constructor y fallback de parse), tras preservar su diff y dejar trazabilidad; no revertir el archivo entero ni borrar IntentFallback. Sin esa autorización, la integración del motor queda bloqueada y se eleva a Orca. Pruebas específicas de #3 son necesarias ahora; #8 no justifica posponerlas. El fallback real y su validación de propuestas, voz y empaquetado siguen fuera del alcance.

## 3. Dominio y contratos internos tipados

Ubicación propuesta: paquete existente `com.norma.taller`, sin reorganizar el proyecto. Los siguientes son contratos de diseño, no implementación final:

- `BotModel.State`: IDLE, AWAITING_DETAILS, AWAITING_CONFIRMATION, nombres persistidos y públicos actuales.
- `Action` sellada: `Create(title,price,imageUrl)`, `ChangePrice(pieceNumber,newPrice)`, `ChangeImage(pieceNumber,newImageUrl)`, `Sell(pieceNumber)`. Campos obligatorios, validados, sin null; pieceNumber int positivo hasta 2147483646, price int positivo hasta 2147483647.
- `Draft(operation?, target?, title?, price?, imageUrl?, candidates)` admite campos ausentes solo en DETAILS; operation enum CREATE/PRICE/IMAGE/SOLD, no kind arbitrario. Target es numerito ya resuelto o referencia pendiente; candidates son numeritos disponibles, nunca productos de otra sesión. El motor vuelve a resolver contra catálogo actual al completar.
- `ParseResult` sellado: `Complete(Action)`, `NeedsDetails(Draft, MissingField)`, `Ambiguous(Draft,List<Integer>)`, `Invalid(Reason)` y `Unknown`. MissingField: OPERATION/TARGET/TITLE/PRICE/IMAGE. Prioridad de pregunta: operación, destino, título, precio, imagen. Nunca convertir error a una acción por defecto.
- `Control` sellado: Confirm, Cancel, Undo, Correction(field,value), None. Se extrae antes del parser de acciones; coincidencia de frase completa, salvo prefijos de corrección explícitos. No buscar sí/no dentro de nombres.
- `BotResult(Reply reply, boolean catalogChanged)` solo interno si resulta útil a pruebas; `BotEngine.process(String,String,String)` y `Reply(replyText,replyAudioUrl,state)` permanecen públicos e iguales. No es necesario exponer catalogChanged por REST ni modificar SSE en esta fase.
- `IntentParser.parse(...)` y `price(...)` conservan adaptadores para FlowTest/consumidores existentes: price retorna Integer o null; parse devuelve Intent legacy solo para acción completa, lanza IllegalArgumentException clara para los demás casos. FSM usa el método tipado nuevo `interpret(text,image,products,draft)`.
- `Intent` legacy(kind,number,title,price,image) mantiene forma y tokens CREATE/PRICE/IMAGE/SOLD exigidos por Catalog.apply. Un único adaptador convierte Action válida; no pasar UNKNOWN/UNDO/CONFIRM ni borrador incompleto a Catalog. Ignorar campos no utilizados no sirve de validación.

Imágenes proceden de MediaService o rutas internas existentes; no interpretar texto del usuario como URL nueva. No resolver acceso al filesystem ni llamar proveedores desde parser/codec/transacción. Títulos nuevos trim, 1..100 caracteres Unicode, espacios internos normalizados; sin extracción de precio desde dígitos del título. Sin límites nuevos a títulos ya existentes del catálogo/snapshot: #2 admite cualquier título no vacío.

No se necesita interfaz de proveedor ejecutable en #3. El punto futuro de extensión #5 será una propuesta de ParseResult sin autoridad para confirmar, cancelar, deshacer o mutar; fuera de transacción y con revalidación local contra sesión/catalogo actuales. No crear cliente ni no-op obligatorio, ni permitir que el bean IntentFallback preexistente sea una dependencia del motor.

## 4. Parser determinista, numeritos y precios

Normalizar caso/acentos para reconocer comandos y referencias; conservar texto original del título y **no eliminar puntos, comas o signos del monto antes de validarlo**. Máximo de mensaje 1000 caracteres ya existente en API; aplicar igual límite en entrada directa al motor. Referencias #N, pieza N, número N, la número N, admiten N decimal sin signo o número hablado canónico. La referencia se extrae y excluye de precio. Dos referencias distintas o número y nombre contradictorios requieren aclaración, aunque una exista.

Para acciones existentes, buscar solo disponibles de esa sesión. Primero número explícito válido; inexistente o vendido explica “No encuentro la pieza #N disponible; decime otro numerito”. Nombre: coincidencia normalizada completa, o secuencia completa de palabras del nombre que identifica una sola pieza (p.ej. “maceta” solo si hay una); nunca subcadena de palabra. Si varias, preguntar por sus numeritos/títulos; una coincidencia exacta compartida también es ambigua. No usar Levenshtein automático: la especificación propone similitud como medio, pero exige aclarar ambigüedad; un error de tipeo pregunta de nuevo. No seleccionar “la primera”. “Sacá la maceta azul” resuelve por nombre único y prepara SOLD.

Reconocer frases requeridas: “Ya vendí la pieza 2”, “Sacá [nombre]”; “A la número 2 subila a veinte mil pesos”, “cambiá el precio de #2 a ...”; “Esta foto es para cambiar la de la pieza 2”; alta “[nombre] a [precio]”, “Es/Esta es [nombre] a [precio]”, “Agregá/Publicá [nombre] a [precio]”. “Subí [nombre] a ...” solo alta si no hay referencia existente ni verbo de precio; “subila” con destino es PRICE. Palabras de acción en un título no son por sí solas un comando. Dos acciones explícitas en el mismo mensaje: pedir una por vez, sin preparar ninguna. Foto sola es dato incompleto, no evidencia de CREATE frente a IMAGE.

Precio: pesos enteros **1..2147483647**, compatible con int/SQL aprobado. Se elimina el límite arbitrario previo 100000000. Aceptar expresión completa, con $ opcional y “peso/pesos” final opcional:

| Forma | Regla / ejemplos |
|---|---|
| Entero decimal | `15000`, `18.500`, `2.147.483.647`; puntos solo grupos de tres, primer grupo 1..3 dígitos. Entero sin agrupar sin ceros iniciales salvo cero (que es inválido por rango) |
| Multiplicador | `15 mil`, `15 lucas`, `quince mil`, `quince lucas` →15000; un único multiplicador, coeficiente entero 1..999; no fracciones de luca |
| Hablado canónico | unidades 0..29, decenas 30..90 con “y” unidad 1..9, centenas canónicas (cien/ciento..., doscientos..novecientos); miles como grupo1..999 + mil + grupo0..999; millón/millones con grupo1..999 y resto canónico; composición total dentro de rango |
| Coloquial requerido | grupo1..99 seguido directamente por centena100..900 (con resto canónico0..99) significa miles + resto: `dieciocho quinientos`→18500, `quince doscientos cincuenta`→15250. No aplicar esta regla a `ciento cincuenta`→150 |

Se admite forma abreviada de miles hasta 999999 y millones hablados hasta el rango int; una cantidad mayor puede darse en dígitos. Los grupos de palabras deben consumirse enteros, en orden, sin sumar palabras arbitrarias. Aceptar “un/una” solo como uno ante mil/millón, “veintiún” ante multiplicador. `mil` sin coeficiente=1000, `un millón`=1000000. Rechazar “lucas” sola. Operaciones aritméticas con long y comprobación de desbordamiento antes de convertir.

Rechazar sin truncar ni preparar confirmación: cero, negativos de dígitos o “menos ...”, signos +, comas/centavos (`15,50`, `15.000,00`), agrupación inválida (`15.00`), exponentes, fracciones, overflow, `quince veinte`, `mil mil`, `15 o 20 mil`, `15000 20000`, texto residual no permitido en la expresión. `#2` nunca es monto. No normalizar `15,5` a 15 ni a 155. “Cuenco 2 a 15000” conserva 2 en título. Si no hay delimitador inequívoco de monto/título, preguntar nombre y precio de nuevo. En contexto de precio faltante/corrección se admite monto solo.

Confirmaciones exactas tras normalización de tildes y puntuación terminal: sí, dale, subilo, está bien, confirmo, confirmar. Cancelación exacta: no, pará, cancelar, cancelá. “No sé”, “sí pero ...”, “sí, no” no confirman. Undo: deshacer, me equivoqué, uy me equivoqué, volvé atrás, uy no me equivoqué volvé atrás (puntuación flexible); no contiene-match como “no me equivoqué”. No interpretar fotos adjuntas a sí/no como confirmación/cancelación: pedir que mande solo la respuesta, conservando borrador.

Correcciones explícitas: “no, dije [monto]”, “dije [monto]”, “el precio es [monto]”; “no, la pieza [referencia]”; “el nombre es [título]” solo CREATE; “esta es la foto correcta” con imagen para CREATE/IMAGE. Monto solo es complemento en DETAILS, no corrección silenciosa en CONFIRMATION. Corregir campo no aplicable, varios campos ambiguos o inválidos conserva borrador y explica qué puede corregir. No implementar renombrado de pieza existente. Toda corrección válida vuelve a preguntar con el resumen completo; nunca aplica en ese turno.

## 5. FSM exhaustiva y política de mensajes

Persistencia canónica: IDLE pending=null/temp_image=null; DETAILS contiene borrador parcial o foto sola; CONFIRMATION contiene Action completa y validada. Snapshot es independiente del borrador. “Conservar” significa no modificar productos, snapshot ni campos de borrador; se permite responder sin actualizar timestamps.

| Entrada | IDLE | AWAITING_DETAILS | AWAITING_CONFIRMATION |
|---|---|---|---|
| Vacía, whitespace, sin foto | Ayuda, conserva IDLE | Repetir campo faltante, conservar | Repetir resumen/pregunta, conservar |
| Foto sola | Guardar foto, DETAILS, pedir nombre/precio o destino | Si falta foto, completar; si ya había, reemplazar foto del borrador y volver a pedir datos/resumen | Conservar, pedir confirmar/cancelar o corrección explícita de foto |
| Acción nueva completa | Guardar borrador, CONFIRMATION | No sustituir operación activa; si es reiteración compatible que completa exactamente lo faltante, fusionar; si es otra acción, pedir cancelar primero | Conservar y pedir sí/no/corrección; no sustituir |
| Datos parciales / aclaración | DETAILS si operación/datos útiles son inequívocos; si no, ayuda sin borrador | Fusionar solo campos faltantes/coherentes; ambigüedad sigue DETAILS; completo válido→CONFIRMATION | Conservar, explicar correcciones admitidas |
| Confirm exacto | “No hay un cambio pendiente para confirmar”; IDLE | “Me falta ... antes de confirmar”; conservar | Revalidar y aplicar una vez, snapshot+limpieza atómicos→IDLE |
| Cancel exacto | “No hay un cambio pendiente”; conservar historial | Limpiar pending/temp, IDLE; conservar snapshot | Igual que DETAILS |
| Corrección explícita | Sin borrador, pedir acción | Validar, actualizar solo campo permitido; completo→CONFIRMATION | Validar y reemplazar borrador, CONFIRMATION, volver a preguntar |
| Undo exacto | Restaurar una vez si snapshot válido, IDLE | Undo válido restaura y descarta borrador→IDLE; sin snapshot conserva borrador | Igual que DETAILS |
| Unknown, monto inválido, referencias múltiples | Aclaración dirigida; no publicar | Conservar lo ya válido, pedir precisión | Conservar y repetir opciones |
| Reset HTTP explícito | Catalog.reset aprobado→IDLE | Igual, descarta borrador e historial | Igual |

Foto y detalles del mismo mensaje se interpretan juntos; foto seguida de audio usa la transcripción como detalles. También se admite nombre/precio antes de foto: DETAILS conserva ambos y una foto posterior completa sin exigir repetirlos. Si la primera foto no tiene operación, siguientes datos con un único destino pueden determinar IMAGE; nombre+precio determina CREATE. Un número solo en DETAILS con candidatos resuelve destino de la operación ya elegida, no inicia una acción nueva.

Una operación en borrador nunca se reemplaza por otra silenciosamente. Un mensaje completo compatible debe coincidir con todos los campos ya conocidos (o usar corrección explícita); si difiere, indicar cancelar o corregir. Cancelar, aclarar o preparar otra acción después de cancelar no borra el último undo. Snapshot se reemplaza únicamente tras la próxima mutación confirmada efectiva. Una corrección que deja el precio/foto igual al actual se permite preguntar, pero al confirmar responde “Ya estaba así, no hice cambios”, limpia borrador y conserva snapshot previo. SOLD ya vendido no es acción preparable. Así no se pierde undo por una operación sin efecto.

Preconfirmación: validar nuevamente acción, existencia/disponibilidad del destino y campos. Si el destino desapareció o se vendió, no aplicar; limpiar ese destino, pasar a DETAILS y pedir otro número. Agotamiento CREATE: no publicar ni tomar snapshot, conservar borrador y explicar que no se pueden asignar más numeritos; ofrecer cancelar/restaurar sin prometer que reset libera numeración. Todos los resultados declaran el state realmente persistido.

## 6. Persistencia JSON, compatibilidad y errores

Sin migración DDL ni cambio de user_version. `sessions.pending` contiene envelope nuevo `{"version":1,"sessionId":"...","draft":{...}}`; discriminante de operación y campos de Draft de §3, candidatos listados como numeritos. `temp_image` conserva imagen para compatibilidad de lectura; si aparece en draft y columna debe coincidir exactamente. CONFIRMATION exige todos los campos de Action; DETAILS exige al menos operación, foto o referencia/datos útiles, y no puede contener una acción completa que se aplique sin pregunta.

Snapshot nuevo: `{"version":1,"sessionId":"...","products":[Product...]}` usando **todos** los productos de Catalog.all, vendidos incluidos. No guardar nextNumber para rebobinarlo, ni borrar archivos de medios al deshacer/cancelar/reset. `Catalog.restore` conserva high-water; id SQL de Product es opaco y puede regenerarse. Validar lote completo antes de restore: sesión exacta en envelope y cada Product, pieceNumber único/en rango, title/imageUrl no vacíos, price entero positivo int, sold boolean real, id entero long positivo. Lista vacía es snapshot válido; null/no campo products no lo son. No imponer límite 100 al título histórico ni cambiar campos para “reparar” snapshot.

Codec explícito sin polimorfismo Jackson por nombres de clases, sin coerción string→número/boolean ni decimal→int, sin claves duplicadas/versión desconocida/campos inesperados. Diferenciar JSON null de ausencia de snapshot. Leer por árbol y validar antes de mapear si la configuración general permite coerciones. No volcar contenido de mensajes/snapshots a logs de error ni al usuario.

Compatibilidad perezosa en el motor, nunca en inicializador #2:

- Legacy pending con exactamente shape Intent(kind,number,title,price,image) conocido se convierte/valida; CREATE/IMAGE sin foto en DETAILS se acepta; foto sola con pending=null sigue válida. Ignorar únicamente campos no aplicables conocidos de Intent, que el parser anterior rellenaba incluso para PRICE/SOLD. Campos requeridos nunca se infieren.
- CONFIRMATION legacy válido se reescribe como envelope y **se vuelve a mostrar la pregunta sin aplicar ese mensaje**, incluso si era sí. Así no se confirma ciegamente un formato previo no auditado.
- Snapshot legacy `List<Product>` se acepta solo con validación estricta completa y sesión de cada fila coincidente; [] válido. No hay obligación de que current catalog coincida con snapshot: por definición es estado anterior.
- Fixtures opacos de pruebas #2 (`{"draft":9}`, `[{"piece":1}]`) siguen persistiendo byte a byte por Catalog/inicializador; el motor los considera inválidos cuando se intenta usarlos. Esto no cambia el contrato opaco de Catalog.
- Pending corrupto/incompatible o combinación state/columnas inválida: limpiar solo pending/temp y volver IDLE, conservar snapshot, responder “No pude recuperar el cambio pendiente. Mandámelo de nuevo”; **no procesar además el mensaje entrante**. Si el mensaje es undo y snapshot es válido, puede ejecutar undo primero sin depender del borrador.
- Snapshot corrupto/incompatible al pedir undo: no restaurar ni consumir ni limpiar borrador; responder “No pude recuperar el cambio anterior. El catálogo sigue igual; podés cancelar el cambio pendiente o restaurar el catálogo original”. Conservar el JSON para diagnóstico; reset explícito lo limpia. Un nuevo commit confirmado puede sustituirlo por un snapshot nuevo válido.
- Un snapshot corrupto no bloquea comandos normales. Un fallo de DB/serialización durante escritura propaga excepción y revierte toda la transacción; API conserva 500 genérico. No limpiar como si fuera corrupción de datos conocida ni anunciar éxito.

## 7. Transacciones, concurrencia y alcance de idempotencia

Una instancia y una conexión física por archivo es el modelo aprobado. Usar `@Transactional` sobre entrada pública process, Catalog uniéndose con REQUIRED. Nada externo se ejecuta dentro: solo normalización/parser determinista, JSON y JDBC. Reset sigue su frontera actual Catalog.reset. No agregar synchronized/locks locales ni REQUIRES_NEW que pudiera esperar otra conexión del pool de uno; no autoinvocar un método esperando que Spring cree otra transacción.

Secuencia de confirmación en **una** transacción: ensure → leer sesión/catálogo → validar pendiente y destino → detectar no-op → serializar snapshot completo previo → snapshot(id,json) → apply(id,adaptador) → conversation(IDLE,null,null) → commit. Ninguna excepción de apply/restore se captura dentro de ese bloque para continuar/limpiar y devolver éxito. Validaciones de dominio previsibles ocurren antes de escribir; SQL y errores inesperados escapan para rollback. Respuesta de éxito solo después de commit del proxy.

Undo: leer/validar snapshot → restore(id,lista) → snapshot(id,null) → conversation(IDLE,null,null) → commit. Si restore/consumo/limpieza falla, catálogo y metadatos previos completos permanecen. Undo no crea snapshot de la acción de deshacer: segundo undo dice que no hay cambio, sin redo. Con snapshot ausente no cambia borrador ni productos. Una nueva confirmación sustituye el único slot anterior; dos cambios A,B seguidos deshacen solo B. Reset aprobado limpia slot; undo después de reset no recupera estado previo al reset.

La conexión única serializa transacciones reales de process/reset: dos sí concurrentes para el mismo borrador no pueden leer ambos pendiente antes de consumirlo; el segundo lee IDLE y no aplica. Undo concurrentes: uno restaura/consume, otro informa ausencia. Sí vs reset/undo: resultado equivale al orden de adquisición de conexión, jamás combinación parcial. Sesiones distintas se serializan físicamente pero siguen aisladas. Timeout/SQL error responde fallo genérico; no reintentar comandos automáticamente.

Garantía acotada: sí duplicado secuencial o concurrente del **mismo borrador sin otro borrador intercalado** no repite acción. API no tiene eventId/draftId: un sí retrasado que llega después de crear otro borrador es indistinguible de confirmación nueva. No prometer exactly-once de transporte ni deduplicar texto por hashes/tiempos; no agregar claves de idempotencia/revisiones al API en #3. #5 debe resolver propuestas externas obsoletas con contexto revalidado; una futura exigencia de identificar eventos requiere diseño y aprobación separados. No se garantiza orden de finalización HTTP de STT concurrentes, solo orden de entrada transaccional; no ampliar con colas de audio ahora.

## 8. API y experiencia de recuperación

Conservar rutas, multipart sessionId/text/image/audio y respuesta `{replyText,replyAudioUrl,state}`. Sin nuevo status FSM, sin campos obligatorios/eventIds nuevos ni endpoints. sessionId inválida y mensaje largo mantienen 400; errores conversacionales/precio ambiguo devuelven 200 con explicación y estado; SQL inesperado 500 genérico. SSE existente se mantiene fuera de transacción tras process/reset, incluso en respuestas sin cambio; corregir su semántica/entrega es #6.

Foto+texto usa text; foto+audio usa transcripción obtenida por MediaService antes de process. Cuando se adjuntan texto no vacío **y** audio no vacío, no sobrescribir una fuente ni concatenarlas adivinando: 400 “Mandame el texto o el audio con los detalles, uno por vez”; sin tocar catálogo/borrador. Imagen puede acompañar cualquiera. STT fallido no invoca bot ni pierde su borrador. En #3 se verifica con stub/mock de MediaService, sin exigir proveedor real. No modificar su implementación de voz/imagen. TTS posterior puede devolver null: el texto es siempre la recuperación; no repetir apply por falta de audio.

Preguntas deben incluir número/título de destino y precio argentino `$ 18.500` sin centavos. Alta muestra nombre y precio, número se asigna recién al confirmar. IMAGE aclara que cambia foto de #N; SOLD que retira #N. Recuperaciones específicas: precio inválido→ejemplos “quince mil”/“15 lucas”; destino ambiguo→números y títulos disponibles; mensaje desconocido→“Decime qué pieza querés cambiar, o mandame una foto con nombre y precio”; borrador pendiente→“Tengo ... pendiente. Decime sí, no, o qué querés corregir”. Nunca afirmar publicación antes de commit ni exponer JSON/stack traces/credenciales. Ninguna respuesta promete reproducción TTS o funcionamiento de proveedores no auditados.

## 9. Archivos propuestos y plan secuencial de construcción

**Lista exacta a autorizar por Orca** (no es autorización de este documento):

| Archivo | Cambio permitido |
|---|---|
| src/main/java/com/norma/taller/BotModel.java (nuevo) | Tipos de dominio/FSM y adaptador legacy |
| src/main/java/com/norma/taller/BotSessionCodec.java (nuevo) | Envelope, validación estricta y lectura legacy |
| src/main/java/com/norma/taller/IntentParser.java | Parser tipado/gramática, mantener adaptadores |
| src/main/java/com/norma/taller/BotEngine.java | FSM/transacción, retirada acotada del acoplamiento local fallback tras autorización expresa |
| src/main/java/com/norma/taller/Api.java | Solo rechazo explícito texto+audio simultáneos; preservar DTO/rutas/publicación |
| src/test/java/com/norma/taller/IntentParserTest.java (nuevo) | Gramática, cantidades, ambigüedad y controles |
| src/test/java/com/norma/taller/BotSessionCodecTest.java (nuevo) | Compatibilidad y corrupción estricta |
| src/test/java/com/norma/taller/BotEngineTest.java (nuevo) | FSM y operaciones/rollback/concurrencia |
| src/test/java/com/norma/taller/BotPersistenceTest.java (nuevo) | Archivo real, cierre/reapertura y undo |
| src/test/java/com/norma/taller/BotApiTest.java (nuevo) | Contratos #3 con medios simulados |
| docs/orchestration/issue-3-build-report.md (nuevo) | Evidencia, commit, comandos, resultados y límites |

No tocar Catalog.java, inicializador, schema/seed/migración SQL, configuración, POM, suites Catalog*, FlowTest, MediaService, assets, frontend, IntentFallback.java ni ApiTest.java. Si hace falta cualquiera de ellos, detener parte afectada y elevar a Orca; no modificar pruebas #2 para ocultar regresiones. No agregar archivos sin seguimiento ajenos. Capturar diff/hash de BotEngine/IntentFallback/ApiTest/AGENTS antes de construir; preservar fuentes previas sin stash/reset y documentar hunks incorporados/excluidos.

1. **B1 — Dominio y codec**: crear BotModel y BotSessionCodec y sus tests. Gate Orca: formas completas/parciales/legacy y JSON inválidos verificadas sin modificar persistencia #2.
2. **B2 — Parser**: gramática y adaptadores con tests parametrizados. Depende B1 aprobado; gate Orca: tabla de números y referencias, cero inferencias peligrosas.
3. **B3 — Motor**: FSM y transacción usando B1/B2 aprobados; BotEngineTest con rollback real y carreras. Requiere permiso explícito de Orca para los dos hunks locales de fallback. Gate Orca: ningún proveedor desde process ni commit parcial.
4. **B4 — Borde y persistencia**: ajuste API limitado, BotApiTest y BotPersistenceTest, informe reproducible. Depende B3 aprobado; gate Orca y auditoría Codex contra v1.0 antes de aceptación/cierre.

Cada orden de Orca debe usar el formato AGENTS y especificar tarea, versión, archivos y evidencia. Antigravity entrega por etapa comando/error reproducible/diagnóstico si falla; no avanzar con predecesora pendiente. Commit del builder solo por autorización de Orca; no push/cierre/despliegue desde diseño.

## 10. Criterios de aceptación y pruebas obligatorias

| ID | Comportamiento y evidencia exigida |
|---|---|
| P1 | Parser parametrizado: ejemplos de ambas specs y tabla §4; límites1/intmax, overflow, decimales/agrupación/signos/múltiples montos/residuos rechazados; #N y dígitos de título separados del precio; no-op no consume historial |
| P2 | Por número y nombre único prepara destino correcto; nombres duplicados/parciales múltiples, referencias contradictorias/vendido/inexistente aclaran; otra sesión y DEFAULT nunca se usan; verbo como subcadena de título no elige acción |
| F1 | CREATE, PRICE, IMAGE, SOLD: comparar todos campos públicos y contador antes del sí; tras sí solo efectos previstos. Alta sin foto espera; foto+texto y foto→detalles, detalles→foto funcionan. No publicar en cancelar/corregir/aclarar/vacío |
| F2 | Recorrer cada fila de tabla FSM en los tres estados con aserciones de estado/pending/temp/snapshot/productos; corrección precio, título de alta, destino y foto; inválida conserva borrador; nuevo comando distinto no lo reemplaza; sí/no con foto no aplican |
| F3 | Sí dos veces, sí sin pendiente y sí antes de completar no crean/venden de nuevo; dos sí sincronizados producen exactamente una mutación/avance de contador y un solo snapshot. Sí retrasado con otro borrador se documenta como límite, no supuesto de deduplicación |
| U1 | Deshacer cada operación, incluye restaurar vendidos y lista vacía; slot se consume una vez, no redo, A+B solo revierte B. Cancelar/preparar/no-op conservan slot. Undo durante borrador válido lo descarta; sin slot lo conserva; reset limpia ambos; alta→undo→alta no recicla número |
| J1 | Codec rechaza desconocidos, duplicados JSON, campos faltantes, tipos/coerción/overflow, versión futura, sesión cruzada/duplicados de pieza; [] snapshot válido. Legacy válido se reabre y re-pregunta antes de aplicar, opacos #2 no se cambian al arrancar y se recuperan según §6 al usarlos |
| T1 | Fixture aislado inyecta fallo SQL real después de guardar snapshot/aplicar y antes de limpiar pendiente, y después de restore antes de consumir slot; rollback comprobado desde transacción nueva con igualdad de productos lógicos y Session completos/contador. No catch que devuelva éxito ni test que solo lance excepción artificial fuera del camino real |
| T2 | Barreras/timeouts y executor en finally: confirm concurrente, undo concurrente, confirm vs reset; resultado en uno de los órdenes seriales descritos, sin parciales, otra sesión/DEFAULT íntegros. No suponer orden de threads; no llamar concurrencia desde transacción exterior del test |
| D1 | Archivo temporal real: cerrar contexto y pool, reabrir con mismo DB; recuperar DETAILS y CONFIRMATION, confirmar, cerrar/reabrir y undo, reabrir y verificar consumo/contador y aislamiento. SQL id puede regenerarse; comparar número/campos, no prometer igualdad de surrogate id tras restore |
| A1 | MockMvc: multipart foto+texto/foto+audio con stub de transcripción, foto seguida detalles; JSON y states existentes; texto+audio400 sin cambio, STT fallo no procesa, replyAudioUrl null conserva texto/acción una sola vez. No red ni edge-tts en tests |
| G1 | FlowTest original y todas las suites #2 pasan intactas; no contar ApiTest local como evidencia versionada. clean verify en exportación limpia del commit, 0 fallos/errores/skip inesperados, pruebas de archivo y rollback no sustituidas por mocks |

Verificación prevista, **no ejecutada por el arquitecto**: JDK25 real, Maven versión registrada; B1 codec, B2 parser, B3 motor, B4 persistencia/API mediante `mvn -B -Dtest=<suite> test` por etapa; luego `mvn -B clean verify` secuencial. Tras commit autorizado, `git archive <commit>` en temporal nuevo y clean verify allí, sin IntentFallback/ApiTest locales ni secretos; registrar SHA, comandos, exit codes, XML y recuentos. No Maven paralelo ni skip de pruebas. No se exige despliegue ni integración Groq/TTS ni SLA de latencia para aceptar #3.

## 11. Decisiones pendientes de aprobación y riesgos

Orca debe aprobar v1.0 y explícitamente: retirada de hunks de fallback del BotEngine local; tipos internos y codec envelope manteniendo legacy; precio hasta intmax y gramática conservadora; política de borrador no reemplazable/nuevo mensaje y no-op; undo durante borrador y corrupción; rechazo texto+audio mixtos y lista de archivos/etapas. Se deducen de seguridad/confirmación de specs sin requerir nueva pregunta al usuario; no hay bloqueo funcional externo identificado para entregar el diseño.

Alternativas rechazadas: adivinar monto por primer número o similitud difusa arriesga publicar otro valor; agregar eventId/draftId impone contrato nuevo no solicitado; migrar DDL rompe v2 canónico innecesariamente; retener conexión durante LLM bloquea todas las sesiones; snapshot solo de pieza complica CREATE/SOLD y cambia contrato de restore ya probado. Snapshot completo usa espacio proporcional al catálogo, aceptable sin demostrar escalado masivo; no introducir truncamiento/retención silenciosa.

Límites: una instancia por archivo y sin auth administrativa, sin deduplicación durable de transporte, sin garantía de orden de STT o entrega SSE, sin limpieza de archivos referenciados por snapshots, sin afirmar recuperación física tras corte eléctrico. Esos límites no invalidan la atomicidad lógica exigida. Construcción y pruebas siguen pendientes de autorización de Orca; este documento no acepta el código previo ni cierra #3.
