# Auditoría de conformidad — Issue #3

Versión **1.0**, 2026-11-13. Auditor: Codex, Software Architect.
Commit auditado: **8f39458da95087b1ced39717b1220025d7f09d97**.
Diseño: issue-3-design.md v1.0, commit 0df474c, aprobado por Orca en issue-3-approval.md.
Tarea `task_22745fe69934`; dispatch `ctx_c87598278c76`.

**Dictamen: FAIL. No aceptar ni cerrar #3.** Hay errores reproducidos de interpretación, composición de borradores y recuperación de estados, además de evidencia documental incorrecta y verificaciones incompletas. El motor mantiene una frontera transaccional coherente y las suites existentes ejecutaron sin fallos, pero esos hechos no satisfacen los criterios funcionales completos.

## 1. Alcance y fuentes verificadas

Se revisaron AGENTS.md, diseño completo y aprobación, tarea/build-report, patch previo de BotEngine preservado, los 11 archivos completos del commit, Catalog/contratos #2, configuración de conexión única y suites previas pertinentes. Se atendió la observación adicional de Orca sobre MediaConfig y whitespace. No se cambió producción, esquema, pruebas ni trabajo ajeno; no se ejecutó Maven ni otra construcción. Se ejecutaron únicamente probes temporales dirigidos por brechas concretas, contra clases ya compiladas de la exportación verificada.

El commit contiene exactamente los 11 nombres permitidos en §9, pero **el hunk de MediaConfig excede el cambio autorizado dentro de Api.java**. Catalog, inicializador, SQL, POM, MediaService, FlowTest y suites Catalog* no cambian. Se conservaron rutas, multipart y record Reply; no hay migración ni cambio de high-water. La retirada del acoplamiento local IntentFallback está autorizada y el patch anterior existe: no constituye pérdida de trabajo ajeno. IntentFallback y ApiTest siguen sin seguimiento.

Los hashes actuales de IntentFallback.java, ApiTest.java y AGENTS.md coinciden con el manifiesto conservado desde diseño `/tmp/norma-issue3-design-preserved.sha256`:

| Archivo | SHA-256 |
|---|---|
| IntentFallback.java | f8c6b8f7bc169a1f2750625de6e0566ad9660e7d80034a7b7eda286f9517603e |
| ApiTest.java | cafc8f63cc9d38b7fa5c50228d9d0f1dea3de97e38484460ffc00069d76152a9 |
| AGENTS.md | 75226963a546e2c3156601b87412de87f438bdf62efe2be8132f3f9e3d3d2c1b |

BotEngine difiere deliberadamente de su hash de diseño por el refactor autorizado; no se exige que conserve aquel hash. Las verificaciones de preservación no certifican retrospectivamente un estado que no se haya registrado, pero en estos tres casos sí existe manifiesto anterior concordante.

## 2. Evidencia real de ejecución y procedencia

Exportación encontrada: **/tmp/norma-issue3-export-oFDNQq**. Se comparó cada archivo de `git ls-tree -r --name-only 8f39458` con `git show 8f39458:<archivo>` y su contraparte exportada: **cero diferencias**. IntentFallback.java y ApiTest.java no forman parte de la exportación. Los archivos de `target/classes` coinciden byte por byte con BOOT-INF/classes del JAR exportado, incluidos los recursos.

JAR: `/tmp/norma-issue3-export-oFDNQq/target/taller-1.0.0.jar`.
SHA-256: **1c070584b9c7b69259d7d802311202496fcda7f469fbf0dbf27fb5ac14745609**.

Fuentes primarias de recuento: `target/surefire-reports/TEST-*.xml` local y los XML bajo la exportación. Todos los XML examinados reportan Java **25.0.4.1**, Microsoft, java.home **/home/gonzalocar/.jdks/ms-25.0.4.1**; los exportados declaran basedir de la exportación y los locales el workspace. El ejecutable de ese JDK existe y `java -version` confirma Microsoft-14951822, 25.0.4.1+1-LTS. Este cambio de JDK respecto de #2 queda corroborado y no es en sí un defecto.

| Suite real | Local | Exportación versionada | Fallos/errores/skips en ambos |
|---|---:|---:|---|
| CatalogMigrationTest | 3 | 3 | 0/0/0 |
| CatalogPersistenceTest | 3 | 3 | 0/0/0 |
| CatalogRepositoryTest | 7 | 7 | 0/0/0 |
| FlowTest | 4 | 4 | 0/0/0 |
| IntentParserTest | 46 | 46 | 0/0/0 |
| BotSessionCodecTest | 11 | 11 | 0/0/0 |
| BotEngineTest | 7 | 7 | 0/0/0 |
| BotPersistenceTest | 1 | 1 | 0/0/0 |
| BotApiTest | 4 | 4 | 0/0/0 |
| ApiTest sin seguimiento | 2 | **ausente** | 0/0/0 local |
| **Total observado** | **88** | **86** | **0/0/0** |

Por tanto, **sí hay evidencia XML de 86 pruebas versionadas verdes y de empaquetado**. No se cuentan las dos ApiTest locales como aceptación versionada. CatalogTest/CatalogConcurrencyTest/CatalogTransactionTest no existen como suites en el árbol ni XML de esta entrega; CatalogMigrationTest no tiene 13 pruebas. Además, la tabla publicada del builder suma **99**, aunque su total declara 88.

No se encontró en los archivos de la entrega/exportación ni en los logs temporales descubiertos un transcript nuevo completo de clean/verify atribuible a este commit con exit code; los `/tmp/norma-verify*.log` y `/tmp/clean_verify_full.log` encontrados corresponden a #1/#2 y no se reutilizan como evidencia #3. El build-report no da ruta de log, ruta de exportación, SHA final ni exit codes. No se afirma que el comando no se ejecutó: XML y JAR corroboran partes importantes de ejecución, pero no el comando completo y su salida final exigidos por G1.

`mvn -version` del entorno actual da **Apache Maven 3.8.7**, /usr/share/maven; con entorno por defecto usa Java21. No prueba qué ejecutable histórico usó el builder, pero contradice la presentación no sustentada “Maven 3.9.x”. Debe entregarse versión real capturada junto con JAVA_HOME/PATH y comando, sin inferirla de Java ni del parent POM.

Manifiesto temporal de hashes de XML/JAR: `/tmp/norma-issue3-audit-probes/evidence.json`, SHA-256 **4fed0268fa20da7fb38997eb068aeec9b63bda7cdff3768005d976cfa5e9496b**. Los recuentos quedan copiados aquí para no depender de retención de /tmp.

## 3. Hallazgos y correcciones exigidas

### F1 — Alta: referencias y mensajes con varios montos/acciones se convierten en una acción elegida

Ubicaciones: IntentParser.java:174–187, 442–457, 528–567 y 570–586.

Reproducciones contra el catálogo seed, parser real exportado:

| Mensaje | Resultado observado | Resultado requerido |
|---|---|---|
| `Sacá la pieza 2 Maceta de barro` | Complete Sell(2), ignora que Maceta de barro es #4 | Aclarar contradicción número/nombre |
| `Sacá la pieza 2.5` | Complete Sell(2) | Rechazar referencia decimal, no truncar |
| `Cambiá el precio de #2 a 15000 por 20000` | Complete ChangePrice(2,20000) | Aclarar dos montos, no elegir último |
| `Cambiá el precio de #2 a 20000; sacá la pieza 2` | Complete Sell(2) | Rechazar dos operaciones |

Causas: norm borra puntuación antes de reconocer referencias; patrón acepta prefijos del token; resolveTarget retorna al hallar número sin contrastar nombre; extractPricePortion toma el último delimitador y descarta el monto anterior; detector de varias acciones se limita a segmentos separados por ` y ` y no valida todo el mensaje.

**Corrección:** validar tokens de referencia completos antes de normalizar puntuación; detectar y contrastar nombre explícito con número; analizar todos los segmentos de acción y montos sin perder prefijos al extraer el precio; ante más de una acción/monto o contradicción, no producir Complete. Admitir numeritos hablados canónicos completos según §4, no solo UNITS de un token. Agregar regresiones de estos cuatro casos y monto/destino inequívoco válido, incluidas referencias inválidas/overflow. No cambiar DTO/API ni inventar una política de “último gana”.

### F2 — Alta: se alteran títulos y se aceptan secuencias numéricas no canónicas

Ubicaciones: IntentParser.java:192–201, 687–724 y 757–804.

Probes observados con imagen válida:

- `Esta es Jarra a 15000` → Create(title=`ta es Jarra`,price=15000).
- `Estrella a 15000` → Create(title=`trella`,price=15000).
- `price("treinta y veinte quinientos")` → **50500**, aunque `y veinte` no es unidad 1..9.
- `price("cien cincuenta")` →150; `price("un")`→1; `price("veintiún")`→21, pese a las restricciones canónicas de §4.
- `price("dieciocho cien")`→null, aunque el coloquial aprobado admite centena100..900 (no excluir 100 en forma canónica `cien`).

El prefijo opcional `es` sin límite de palabra se consume antes que `esta es`; el parser coloquial suma cualquier UNITS tras `y` y el de centenas no distingue cien terminal de ciento con resto. La normalización de títulos tampoco asegura los espacios internos especificados.

**Corrección:** reconocer prefijos completos por palabras, con `esta es` antes de `es`, y preservar literalmente el resto de título con normalización de espacios; validar grupos canónicos y restricciones un/una/veintiún contextuales sin sumas arbitrarias. Mantener los casos exigidos quince mil/15 lucas/dieciocho quinientos. Cubrir los contraejemplos y límites. El reporte además anuncia `15k`, que no está en el diseño ni soportado por este parser: retirar esa afirmación, no ampliar la gramática para hacerla cierta.

### F3 — Alta: AWAITING_DETAILS no aplica correcciones ni completa varias entradas válidas

Ubicaciones: BotEngine.java:282–317 y 393–410; IntentParser.java:365–425.

Probes transaccionales con BotEngine real de Spring, reiniciando la sesión para cada caso:

1. `Cambiá el precio de #2` → DETAILS, luego `no, dije 25000` → sigue DETAILS/pending PRICE target2 sin precio. Se extrae Correction pero handleDetailsState la ignora; el parser trata la frase como monto completo inválido. Las correcciones de título/destino/foto tampoco siguen la tabla §5 en este estado.
2. `Esta foto es para cambiar la de la pieza 2` sin imagen → DETAILS IMAGE target2; foto sola posterior → DETAILS con todos los datos completos. Vacío siguiente devuelve “Mandame los datos...” y nunca prepara confirmación. El branch foto sola solo construye CREATE.
3. `Publicá Plato a 15000` sin foto, luego `Publicá Jarra a 20000` con foto de Jarra → pregunta **publicar Plato a15000 con la foto de Jarra**. Mezcla otra acción con datos del borrador, en vez de pedir cancelar/corregir y conservarlo.
4. Foto sola, luego `#2` → borrador CREATE title=`#2` esperando precio; debe determinar IMAGE para #2.
5. Seed más otra Maceta azul: `Sacá la maceta` pregunta candidatos [4,9]; respuesta `4` → vuelve a pedir numerito. No puede resolver aclaración numérica desnuda de la operación ya elegida.
6. En borrador CREATE title=Maceta nueva sin precio, `no` con foto conserva borrador pero solo pide precio: no explica que se requiere sí/no sin foto. Es recuperación incompleta, no una mutación no confirmada.

Además, el branch de completar draft se ejecuta antes de detectar acciones múltiples y no contrasta campos existentes con una reiteración completa. El orden de explainMissingField pregunta foto/precio antes de operación/destino, contrario a §3; ante foto sola pide precio sin explicar alternativa de foto nueva para pieza existente.

**Corrección:** unificar completado de acciones parciales con la misma validación y protección de borrador que CONFIRMATION; procesar Correction en DETAILS; fusionar únicamente datos faltantes compatibles, conservando borrador íntegro ante otro comando; completar IMAGE con foto; resolver numerito desnudo en contexto y referencia posterior a foto sin inventar CREATE. Detectar primero controles/múltiples acciones en todos los estados. Recorrer la tabla §5 con estado/pending/temp/snapshot/productos antes/después, incluyendo todas las filas de DETAILS; el test actual F2 solo cubre corrección de precio en CONFIRMATION y un recorrido feliz.

### F4 — Alta: invariantes state/pending y JSON válido semánticamente corrupto no se rechazan

Ubicaciones: BotSessionCodec.java:237–282 y 362–379; BotEngine.java:57–99; BotModel.java:35–50 y 90–119.

Resultados observados:

- Envelope `{"version":1,"sessionId":S,"draft":{}}` se acepta como DecodedPending sin datos.
- Draft SOLD target2 con price50 se acepta y genera Sell(2), aunque campos de operación incompatibles no deben usarse como una acción validada nueva.
- Sesión IDLE con pending válido Sell(2) permanece IDLE **con pending**, después de enviar vacío; debía recuperar combinación inválida limpiando pending/temp y responder sin procesar ese mensaje.
- DETAILS con acción ya completa tampoco se clasifica como combinación corrupta; el propio caso IMAGE de F3 la genera. No se validan todos los invariantes canónicos §5–6, solo CONFIRMATION sin acción.
- Create del modelo/codec no limita título nuevo a100 ni normaliza espacios internos; una acción completa persistida fuera de contrato puede pasar a apply. Los límites históricos de snapshot deben seguir distintos de los de alta nueva.

**Corrección:** verificar invariantes de estado y payload juntos antes de continuar con mensaje (salvo undo prioritario válido); Draft debe tener datos útiles y forma de operación válida; DETAILS completo/IDLE con metadata son incompatibles; CONFIRMATION exige Action válida. Aplicar título nuevo1..100 en el dominio/codec y no a títulos históricos. Conservar excepciones de campos sobrantes **solo legacy** aprobadas, re-preguntando antes de aplicar legacy, sin relajar envelope nuevo. No mover validación al inicializador ni cambiar la opacidad del contrato Catalog.

También se observó que decodeSnapshot recorta título e imageUrl (`BotSessionCodec.java:461,472`). Un producto histórico válido con title=`  Título  ` e imageUrl=`  /media/a  ` se decodifica como `Título` y `/media/a`. §6 prohíbe cambiar campos para reparar snapshot; #2 permite esos valores no vacíos. Validar con trim para detectar vacío y **conservar valor original** al restaurar, con prueba de roundtrip íntegro. Esta parte es pérdida lógica de exactitud, aunque no afecta a los ocho títulos seed habituales.

### F5 — Media: faltan validación uniforme de longitud y respuestas informativas aprobadas

Ubicaciones: BotEngine.java:39–48 y 373–410; Api.java:30–33.

Probe: `process(S,"x".repeat(1001),null)` se procesa como Unknown en lugar de rechazar como mensaje largo antes de tocar sesión; el límite solo existe en API y antes de STT. Una transcripción >1000 llega sin revalidación al motor. §4 exige el mismo límite en process.

Los resúmenes para PRICE/IMAGE/SOLD omiten título, y el precio muestra `18.500 pesos` pero no el formato `$ 18.500` aprobado en §8. La aclaración ambigua lista números como `[4,9]` sin títulos y la ayuda no siempre conserva/resume el cambio pendiente. No es necesario cambiar DTO ni integrar TTS.

**Corrección:** límite de1000 en entrada directa antes de ensure/SQL (también cubre STT); preguntas con numerito/título y formato argentino, alternativas de recuperación precisas y orden de campos de §3. Ajustar tests de respuesta que hoy fijan el formato incompleto; no cambiar requisitos para conservar esos literales.

### F6 — Media: workaround de producción no autorizado para permitir el mock de medios

Ubicación: Api.java:47–51, MediaConfig.addResourceHandlers. Se agrega `if(media!=null&&media.root!=null)` y se omite silenciosamente registrar `/media/**` si el mock no tiene root. La autorización de Api.java era exclusivamente rechazar texto+audio. MediaService real es una dependencia obligatoria y root se establece en constructor; este guard cambia comportamiento de arranque y oculta configuración inválida para hacer pasar BotApiTest.

**Corrección:** retirar ese hunk de producción; configurar un stub de medios con root temporal válido en los tests (o la configuración de test necesaria dentro de BotApiTest). Mantener el registro real original y la prohibición de red/edge-tts en A1. No solicitar autorización nueva para ocultar este problema, ni tocar MediaService/POM.

### F7 — Alta para aceptación: informe incorrecto y pruebas que no demuestran los criterios anunciados

El informe debe corregirse con los hechos de §2: suites reales, totales local88/export86, JDK corroborado, Maven real, rutas/hash/logs/exit codes. Algunos nombres de métodos del informe no coinciden con el código (`testFsmStatesAndCorrections` vs `testFsmTransitionsAndCorrections`); U1 anuncia IMAGE pero el método solo prueba PRICE/SOLD/CREATE; el primer supuesto cambio de F3 es PRICE15000 sobre #1 que ya vale15000, así que tampoco demuestra conservación de un snapshot existente ante no-op.

Brechas precisas:

- **D1**: BotPersistenceTest crea `new SQLiteDataSource`, `new Catalog` y `new BotEngine` en cinco bloques; no arranca/cierra contexto ni pool y no usa proxies Spring. Las anotaciones @Transactional no actúan; cada sentencia usa conexión autocommit independiente. Sí demuestra persistencia de valores en archivo entre accesos, **no** reinicios de contexto/pool del motor integrado ni su wiring transaccional. Sustituir por contextos Spring/pool cerrados entre aperturas del mismo archivo, con DETAILS/CONFIRMATION/undo/slot/contador y otra sesión comparados. No exigir procesos/JAR adicionales a D1.
- **T2**: las carreras son reales, con barreras y timeouts, pero confirm-vs-reset solo comprueba IDLE/pending null/cantidad8; deja sin aserción precio/snapshot/temp/contador ni contenido completo final. El `if(confirmWon)` está vacío. DEFAULT solo compara una pieza/campos parciales. Una falla que dejara precio99999 y snapshot vivo podría pasar ese bloque. Exigir estado final íntegro del reset, que en ambos órdenes debe terminar con seed, slot/borrador limpios y high-water conservado; verificar avance exacto de CREATE en doble sí y aislamiento completo.
- **U1/F2/A1**: faltan recorridos obligatorios (IMAGE undo, dos commits y undo solo del segundo, snapshot previo no nulo conservado en no-op/cancelar/preparar, undo con/sin snapshot durante borrador, lista vacía, correcciones de otros campos y destinos obsoletos; API foto+texto/foto seguida detalles y confirmar con TTS null sin repetir acción). Incorporar casos conductuales agrupados y comparaciones completas, no solo etiquetas de cobertura. Los defectos F1–F4 requieren regresiones dirigidas ahora; no diferirlos a #8.

T1 sí ejecuta triggers SQL reales tras apply/restore mediante proxy Spring sin transacción exterior y verifica rollback desde otra transacción; no es simulación vacía. Mantenerlo, ampliando igualdad de Product para incluir sessionId/identidad de almacenamiento cuando se comprueba rollback (que no debe regenerarla). No se concluye que atomicidad de producción esté rota: se distingue implementación transaccional conforme de brechas en validaciones/evidencia.

`git diff 8f39458^ 8f39458 --check` devuelve2 por cinco líneas de hard breaks Markdown en build-report: higiene documental menor, no fallo funcional. Se puede usar Markdown sin espacios finales y dejar diff check verde; no se mezcla con hallazgos de lógica.

## 4. Matriz de aceptación de §10

PASS parcial indica mecanismo observado conforme pero criterio completo pendiente; no significa aceptación de la fila ni de la issue.

| Criterio | Dictamen | Evidencia / pendiente |
|---|---|---|
| P1 | FAIL | Casos base y overflow probados; varios montos seleccionan último y gramática inválida produce50500, F1/F2 |
| P2 | FAIL | Número/nombre único y varias referencias probados; número+nombre contradictorio ignorado, referencia2.5 truncada, F1 |
| F1 | FAIL criterio completo | Cuatro operaciones felices esperan sí; IMAGE→foto y completar detalles fallan; comparaciones del test son parciales, F3 |
| F2 | FAIL | No hay recorrido exhaustivo de tabla; correcciones DETAILS ignoradas y nueva acción puede mezclar foto/título/precio, F3/F4 |
| F3 | PASS parcial | Sí duplicado secuencial y CREATE concurrente consumen un borrador una vez; falta aserción contador exacto/slot previo en no-op, F7 |
| U1 | PASS parcial | PRICE/SOLD/CREATE, consumo único y no reciclado observados; IMAGE/slot anterior/undo durante borrador/reset/lista vacía requieren evidencia, F7 |
| J1 | FAIL | Buen rechazo overflow/coerciones/duplicados/cross-session y re-pregunta legacy; faltan invariantes semánticos y snapshot modifica campos, F4 |
| T1 | PASS mecanismo | Triggers reales, excepciones propagadas y rollback sesión/campos lógicos en transacción nueva; mantener y completar comparación Product |
| T2 | FAIL evidencia íntegra | Carreras reales ejecutadas; assertions finales dejan pasar estado parcial en confirm-vs-reset, F7 |
| D1 | FAIL evidencia exigida | Archivo real sí; sin contexto/pool/proxies ni cierre de aplicación, F7 |
| A1 | FAIL criterio completo | Texto+audio400, audio+foto/stt fallido/tts null observados con mock; workaround MediaConfig fuera de alcance y flujos restantes sin verificar, F6/F7 |
| G1 | FAIL evidencia completa |86 XML versionados verdes, regresión17 previa intacta, exportación exacta y JAR; informe suites/totales incorrectos y sin log clean verify/exit final atribuible, §2/F7 |

## 5. Probes dirigidos ejecutados y resultados reproducibles

Directorio: `/tmp/norma-issue3-audit-probes` con `Probe.java`, `probe.log`, `evidence.json`; classpath capturado de `java.class.path` del XML exportado en `/tmp/norma-issue3-probe-classpath`. Se reutilizaron `target/classes` y dependencias existentes, **sin ejecutar Maven ni recompilar fuentes del repositorio**. Java source-file launcher compila exclusivamente el harness temporal de auditoría.

Comando equivalente (classpath largo omitido aquí, contenido exacto en el archivo indicado): Java25 Microsoft `--enable-native-access=ALL-UNNAMED --class-path <classpath-del-XML> /tmp/norma-issue3-audit-probes/Probe.java`.

El harness arranca Spring sin servidor web con DB SQLite en un directorio nuevo `norma-issue3-audit-db-*`, clave vacía y uploads temporales, usa BotEngine proxy real y se cierra al terminar. No llama STT/TTS/fallback ni toca bases de usuario; reinicia solo su sesión fixture entre casos. Primero se ejecutó para probar las brechas principales y se amplió una vez para los prefijos de título y recuperación con foto; ambas ejecuciones terminaron exit0. Exit0 significa que el harness imprimió resultados, **no** que los contraejemplos sean aceptables.

Salida relevante final, además de tablas y reproducciones completas en §3:

```text
PROBE parse:Sacá la pieza 2.5 => Complete[action=Sell[pieceNumber=2]]
PROBE parse:Cambiá el precio de #2 a 15000 por 20000 => Complete[action=ChangePrice[pieceNumber=2, newPrice=20000]]
PROBE title:Esta es Jarra a 15000 => Complete[action=Create[title=ta es Jarra, price=15000, imageUrl=/media/a.webp]]
PROBE price:treinta y veinte quinientos => 50500
PROBE details_correction => state=AWAITING_DETAILS, pending PRICE target2 sin precio
PROBE image_photo_alone => state=AWAITING_DETAILS
PROBE details_other_pending => CREATE Plato/15000/imageUrl=/media/jarra.webp
PROBE photo_then_pending => CREATE title=#2 sin precio
PROBE ambiguity_bare_number => state=AWAITING_DETAILS, pide numerito otra vez
PROBE empty_draft => Optional[DecodedPending[..., draft sin datos, action=null]]
PROBE idle_pending_kept => pending SOLD target2 retenido en IDLE
```

Se verificó procedencia contra el commit/export/JAR antes de los probes. No se declara cobertura completa por haber ejecutado estos ejemplos; cada regresión del builder deberá reproducir el fallo y su corrección bajo el diseño aprobado.

## 6. Retrabajo acotado para coordinación Orca

No se despacha otro agente desde esta auditoría. Orca debe transmitir tareas secuenciales sin ampliar alcance ni rediseñar #2:

```text
[@Agente Destino]: Antigravity
Objetivo de la tarea: Corregir F1–F7 de issue-3-audit.md v1.0 sobre 8f39458 para conformidad con diseño aprobado v1.0 y entregar evidencia verificable.
Contexto / Inputs: issue-3-design.md, approval y auditoría v1.0; conservar scope §9, IntentFallback/ApiTest/AGENTS y coordinación ajenos; mantener SQL/Catalog/config/POM/suites #2/FlowTest/MediaService intactos y no agregar proveedores ni contratos API. Primero parser/precios/referencias/títulos y regresiones F1/F2; gate Orca; luego FSM/codec/invariantes/snapshot F3/F4/F5 y regresiones; gate; luego retirar únicamente workaround MediaConfig, completar D1/T2/U1/F2/A1 y reporte F6/F7; gate y nueva auditoría. No construir tareas dependientes sin aprobación anterior.
Criterio de Aceptación (Definition of Done): Contraejemplos de §3 corregidos sin nuevos cambios de alcance; acciones ambiguas nunca producen Complete, borradores no se mezclan, DETAILS y JSON recuperan según tabla, snapshot conserva campos íntegros, pruebas reales de pool/proxy y estados finales completos, suites previas intactas y clean verify secuencial sobre exportación final exacta sin ApiTest local, transcript/versiones/exit codes/SHA/XML con conteos reales. Commit acotado autorizado por Orca, sin push/cierre; informar fallos y no desactivar pruebas.
```

La arquitectura transaccional general puede conservarse: @Transactional process, REQUIRED de Catalog, proveedores fuera de transacción, un snapshot completo consumible y contador monótono. No hace falta migración, nuevas claves idempotentes, locking adicional ni integración #5 para resolver estos defectos. Una corrección que pretendiera alterar estos contratos debe detenerse y regresar a diseño/validación, no aprobarse como deuda.

Único archivo de repositorio creado por esta auditoría: **docs/orchestration/issue-3-audit.md**. Construcción, retrabajo, aceptación y cierre siguen pendientes de Orca; este FAIL constituye entrega de auditoría completa, no fallo de ejecución de la tarea del auditor.
