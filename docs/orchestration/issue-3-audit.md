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

---

## Addendum v1.1 — Reauditoría de correcciones sobre f387257

Fecha: **2026-11-13**. Auditor: Codex, Software Architect.
Commit: **f387257056369598299d5422d91fc99576646204**.
Tarea `task_1714ac881bf1`; dispatch `ctx_31643efdf4a7`.
Referencias: diseño v1.0 y aprobación Orca sin cambios; hallazgos F1–F7 de v1.0 anterior. Se conserva íntegra la auditoría anterior como evidencia de su commit; los estados siguientes corresponden exclusivamente a esta corrección.

**Dictamen v1.1: FAIL.** Hay correcciones funcionales y mejoras reales de pruebas, pero F1–F4 no están completamente resueltos y G1 sigue sin evidencia de exportación/empaquetado final. No aceptar ni cerrar #3; no se requiere alterar arquitectura, contratos REST, catálogo aprobado ni alcance #4–#8.

### 1. Scope, preservación y procedimiento

Se revisaron diseño/aprobación, auditoría v1.0 completa, diff de los 11 archivos de f387257 y reporte actualizado; se contrastó implementación vigente, pruebas nuevas y XML locales con las afirmaciones del reporte. `git diff 4ff6b38 f387257 --check` termina exit0. Solo cambian los 11 archivos permitidos de la corrección; MediaConfig vuelve al registro obligatorio original. No hay cambio de Catalog, SQL, configuración, POM, MediaService, FlowTest ni suites Catalog*. La retirada previa de fallback sigue autorizada y no se reincorporó red dentro de process.

IntentFallback, ApiTest y AGENTS siguen sin seguimiento con los tres SHA-256 idénticos a §1 de v1.0 y al manifiesto previo de diseño. El gate aprobatorio de bloque2 registrado en el reporte se toma como contexto de Orca; su entrega al builder no se usa como defecto de código. La auditoría no modifica producción/pruebas ni despacha agentes; único cambio de repositorio: este addendum.

No se ejecutó Maven test/verify/package. La evidencia local existente evitó una repetición rutinaria de suites. Los probes se limitaron a verificar reparaciones y variantes del mismo contrato donde el código conserva pérdidas de información o genera metadata incompatible. Para no usar el JAR desactualizado, se copiaron **exactamente con git show f387257:<ruta>** BotModel.java, BotSessionCodec.java, IntentParser.java y BotEngine.java a `/tmp/norma-issue3-reaudit-v11/sources`, y se compilaron **solo esos cuatro archivos para el harness**, con javac25 `--release 25` contra dependencias existentes, a `classes` en ese mismo directorio (exit0). No se reconstruyó el proyecto ni se sustituyó ningún target del workspace.

El harness temporal arranca Spring sin servidor web, con esas clases primero en classpath, Catalog/configuración #2 intactos y DB/uploads efímeros nuevos; clave vacía, sin STT/TTS/fallback. `Probe.java`, `compile.log`, `local-classpath`, `probe.log` y `evidence.json` están en `/tmp/norma-issue3-reaudit-v11`. Dos ejecuciones dirigidas, la segunda añadió diagnóstico de la falsa ambigüedad de PRICE sin monto; ambas exit0 (impresión de contraejemplos, no PASS de ellos). No se abrieron bases de usuario.

### 2. Pruebas y artefactos: observado frente a reportado

Fuentes: **target/surefire-reports/TEST-*.xml** locales, con basedir del workspace, Java **25.0.4.1 Microsoft** y java.home `/home/gonzalocar/.jdks/ms-25.0.4.1`. El comando de inspección `JAVA_HOME=... PATH=<jdk>/bin:$PATH mvn -version` confirma **Apache Maven3.8.7**, /usr/share/maven, Java25 Microsoft. Esto corrige la discrepancia de versiones de v1.0; no sustituye un log histórico de clean verify.

| Suite real | XML local: tests | Fallos/errores/skips |
|---|---:|---|
| CatalogMigrationTest | 3 | 0/0/0 |
| CatalogPersistenceTest | 3 | 0/0/0 |
| CatalogRepositoryTest | 7 | 0/0/0 |
| FlowTest | 4 | 0/0/0 |
| IntentParserTest | 53 | 0/0/0 |
| BotSessionCodecTest | 15 | 0/0/0 |
| BotEngineTest | 23 | 0/0/0 |
| BotPersistenceTest | 1 | 0/0/0 |
| BotApiTest | 6 | 0/0/0 |
| ApiTest local ajeno | 2 | 0/0/0 |
| **Total local observado** | **117** | **0/0/0** |
| **Subconjunto con fuentes versionadas** | **115** | **0/0/0** |

**117 locales sí corroborados; 115 ejecutados en exportación final todavía solo reportados.** El subconjunto115 se deduce de XML locales excluyendo ApiTest2; no es un XML de ejecución limpia independiente. La exportación localizada `/tmp/norma-issue3-export-oFDNQq` sigue siendo la anterior: tiene86 pruebas, de las cuales BotEngine7, IntentParser46, Codec11 y API4. Comparar todos los archivos de f387257 mediante ls-tree/show contra esa carpeta da **12 discrepancias** (los 11 archivos de corrección y ausencia de la auditoría anterior). No se la atribuye a f387257.

Se buscaron XML de BotEngine y logs de verificación bajo /tmp y el proyecto; no se localizó otra exportación actual ni transcript completo atribuible a esta corrección. El reporte no especifica ruta/export/SHA/log. No se concluye que no exista evidencia en otro lugar ni que no se haya intentado el comando: **la evidencia entregada/localizada no permite corroborar su afirmación de clean archive verify exit0**.

JAR local encontrado: `target/taller-1.0.0.jar`, SHA-256 **8f642536d31f2d94d1c472b9523e75645943a8094991c41382c8c2ec1c0d2af1**. Mtime **00:17:09 -03:00**, anterior a esta corrección; BotEngine.class local es de01:10:37 y XML de BotEngine de01:19:54. Comparación BOOT-INF/classes con target/classes: **11 archivos distintos y2 ausentes en el JAR**; entre los distintos están BotEngine, IntentParser, BotSessionCodec, BotModel$Create y MediaConfig. Faltan BotEngine$1 e IntentParser$PricePortionResult presentes en target/classes. Por tanto, **el JAR local no es evidencia del empaquetado de los tests locales recientes**. La primera inspección encontró una entrada ausente; se repitió el comparador contabilizando faltantes explícitamente, sin alterar ningún artefacto.

Manifiesto de hashes de XML/JAR y diferencias: `/tmp/norma-issue3-reaudit-v11/evidence.json`, SHA-256 **9c2f03d90a9a99abf753c877ad05015941c45ee62a6757f58ff0ace7cc97ed7e**. La exportación/JAR anteriores conservan sus hashes de v1.0 y no se usan como entrega corregida.

### 3. Estado de F1–F7 y contraejemplos pendientes

#### F1 — Sigue abierto, alta: validación por variantes deja truncamientos y selección parcial

Los cuatro ejemplos literales de F1 v1.0 ahora aclaran/rechazan correctamente (número+nombre, pieza2.5, dos montos válidos y acciones separadas por punto y coma). Sin embargo, los mismos contratos fallan en estas variantes reproducidas contra las fuentes exactas de f387257:

| Mensaje | Resultado real | Contrato pendiente |
|---|---|---|
| `Sacá la número 2.5` | Complete Sell(2) | La tilde de número no debe eludir rechazo decimal |
| `Sacá la pieza 2,5` | Complete Sell(2) | No truncar una referencia decimal argentina |
| `Cambiá el precio de #2 a 15,50 por 20000` | Complete ChangePrice(2,20000) | No descartar monto inválido y elegir otro |
| Igual, primer monto `-15000` o `2147483648` | Complete ChangePrice(2,20000) en ambos | Negativo/overflow tampoco pueden desaparecer |
| `Cambiá el precio de #2 a 20000 Y sacá la pieza 2` | Complete Sell(2) | Normalización de caso también al detectar dos acciones |
| `Sacá la pieza ciento treinta y dos`, fixture con #130 y #132 | Complete Sell(130) | Consumir número hablado entero132, sin prefijo130 |
| `Cambiá el precio de #2` en seed | Ambiguous PRICE candidatos[2,4] | Falta precio; no existe nombre contradictorio |

Causas precisas: IntentParser.java:614–623 reconoce `numero` sin tilde solo sobre raw y separa por coma; después norm elimina la puntuación decimal. Líneas635–643 limitan la referencia hablada a3 tokens, aceptando un prefijo de una frase de4. Líneas697–724 solo cuentan **montos que parsean correctamente** y luego vuelven a tomar el último delimitador, perdiendo errores previos. Línea177 separa raw por `y` minúscula, aunque el resto usa texto normalizado. Líneas517–537 dejan `de` terminal al quitar el verbo/referencia y lo tratan como nombre parcial de Maceta de barro, inventando una contradicción con #2.

**Retrabajo exacto:** tokenización que preserve signos/separadores y posiciones de referencia antes de normalizar acentos/caso; rechazar tokens decimales con punto o coma sin depender de tilde; consumir toda expresión hablada canónica o aclarar, nunca su prefijo; validar la expresión de precio completa y única incluso si un fragmento es inválido, sin seleccionar último; detectar acciones múltiples con normalización de caso conservando texto original; contrastar únicamente una referencia nominal explícita, no palabras gramaticales residuales. No agregar sintaxis ni escoger una interpretación por defecto. Agregar regresiones de esta tabla junto con los casos ya corregidos.

#### F2 — Parcialmente resuelto: números corregidos y título aún se recorta dos veces

Confirmados: `Esta es Jarra`→Jarra, Estrella conserva Estrella, `treinta y veinte quinientos` y `cien cincuenta` ahora retornan null; `dieciocho cien`→18100. Modelo Create valida1..100 y normaliza espacios. Los tres precios obligatorios siguen funcionando: quince mil→15000, **15 lucas→15000**, dieciocho quinientos→18500.

Fallo reproducido: `Publicá Es amor a 15000` con foto → Create(title=`amor`), cuando el título es **Es amor**. IntentParser.java:191 consume el prefijo de comando en el patrón y línea202 vuelve a quitar un supuesto prefijo del título capturado. **Corrección:** extraer el prefijo exactamente una vez y conservar el título resultante; probar títulos que empiezan por Es/Esta es/Publicá como contenido tras un comando inequívoco. No cambiar la regla de prefijos para el texto sin comando.

El reporte afirma que `15 lucas` no forma parte de la especificación contractual: es falso; está explícito en ambas specs y diseño§4, y el parser actual lo soporta. Eliminar únicamente esa afirmación, conservar comportamiento y pruebas. `15k` sí sigue fuera de la gramática aprobada.

#### F3 — Parcialmente resuelto: completados básicos reparados, correcciones/reiteraciones aún incompletas

Confirmados por revisión/pruebas/probes: IMAGE que esperaba foto completa con foto sola; foto→#2 determina IMAGE; número desnudo entre candidatos se resuelve; otra alta incompatible **con foto** conserva borrador y explica cancelar; sí/no con foto en DETAILS pide respuesta sin foto; precio corregido en DETAILS se procesa por un handler propio.

Pendientes concretos:

1. Borrador PRICE de #2 a25000 en CONFIRMATION, luego `no, la pieza #3` devuelve “No pude aplicar esa corrección” y conserva target2. `extractControl` pasa literal `#3` a parseTargetNumber, que acepta solo número/frase sin marcador (IntentParser.java:118–123 y657–674). **La corrección usa la misma sintaxis de referencia aprobada**: debe aceptar #N/pieza N/número N/nombre único cuando corresponda, con validación y pregunta nueva; no crear un parser de destinos restringido a dígitos sin marcador. Mantener controles exactos y no mutar en ese turno.
2. Borrador `Publicá Plato a15000` sin foto; otro mensaje `Publicá Jarra a20000` **sin foto** devuelve “Me falta la foto. Mandámela...” para Plato, sin advertir incompatibilidad. El motor solo contrasta operation en standalone NeedsDetails (BotEngine.java:422–426), no título/precio ya conocidos. **Comparar todos los campos conocidos también para acciones parciales** y pedir cancelar/corregir sin mezclar ni inducir a que se completó la nueva. El caso equivalente con foto ya está bien protegido; conservar esa regresión.
3. `Cambiá el precio de #2` → falsa ambigüedad[2,4], luego `no, dije25000` sigue pidiendo numerito; el handler de corrección ya existe, pero la resolución inicial defectuosa de F1 perdió el destino inequívoco. Al corregir F1, este caso literal del hallazgo anterior debe conservar target2 y quedar listo para confirmar el precio corregido.

No se exige una operación nueva; son las mismas correcciones y protección de borrador de§4–5. Agregar pruebas de estado/pending/temp/snapshot/productos, no solo estado del reply.

#### F4 — Parcialmente resuelto: invariantes añadidos, pero el motor puede escribir un envelope que luego rechaza

Confirmados: envelope vacío y campos incompatibles se rechazan; IDLE con pending y DETAILS completo se recuperan; snapshots preservan espacios exactos de título/imagen; título nuevo tiene límite. El codec estricto y legacy siguen separados del DDL/contrato opaco de Catalog.

Fallo reproducido desde flujo normal, sin corrupción SQL inyectada:

1. `Cambiá el precio` → DETAILS con operación PRICE.
2. Foto sola → el motor persiste draft PRICE **con imageUrl** y temp_image, sigue DETAILS y pide numerito.
3. Mensaje vacío → codec rechaza imageUrl para PRICE; motor responde “No pude recuperar el cambio pendiente”, limpia borrador y vuelve IDLE.

BotEngine.java:368–371 agrega imagen al borrador de cualquier operación; BotSessionCodec.java:238–241 prohíbe ese campo en PRICE. **Corrección:** antes de persistir, aplicar invariantes de la operación; para PRICE/SOLD una foto inesperada debe conservar el borrador válido y pedir datos/corrección apropiados, sin añadir campo incompatible. Las fotos de CREATE/IMAGE mantienen las transiciones aprobadas. Validar roundtrip de cada payload generado por los handlers frente al codec y probar este recorrido de PRICE con igualdad de borrador previo, en lugar de relajar el codec para aceptar campos que el diseño prohíbe.

#### F5 — Cerrado para alcance aprobado

process valida1000 antes de ensure/SQL y las transcripciones quedan sujetas al mismo límite; preguntas muestran `$ 18.500`, número/título y candidatos con nombres, y explainMissingField respeta orden aprobado. Regresiones locales lo ejercitan. Los fallos de recuperación restantes quedan identificados en F1/F3/F4, sin reabrir requisitos nuevos de UX/TTS.

#### F6 — Cerrado

Guard de MediaConfig retirado; registro de `/media/**` vuelve al original. BotApiTest configura únicamente en test un MediaService mock con root temporal válido mediante TestConfiguration/Primary. No hay red ni TTS real en la suite BotApi; se añadieron foto+texto y foto→detalles→confirmación con TTS null y sí duplicado. No exigir cambiar producción para facilitar el stub.

#### F7 — Parcialmente resuelto: pruebas reales mejores, entrega limpia aún no corroborada

Mejoras comprobadas:

- **D1 real:** cinco contextos SpringApplicationBuilder dentro de try-with-resources, beans proxy y Hikari sobre mismo archivo; XML registra HikariPool13..17 iniciados y cerrados, secuencia01:19:38–01:19:42. Se recuperan DETAILS/CONFIRMATION/slot/contador y otra sesión. El motivo de FAIL D1 anterior queda cerrado; no se pide prueba de proceso fuera de D1.
- **T1:** conserva triggers SQL reales y añade igualdad de todos los campos/id/sessionId/Product record tras rollback desde transacción nueva.
- **T2:** confirm-vs-reset comprueba snapshot/temp/pending limpios, contador preservado y todos los campos lógicos de las8 piezas frente a DEFAULT; otra sesión compara records íntegros. Barreras/timeouts/finally se mantienen. Se acepta la evidencia funcional conjunta con transacción/conexión única, sin exigir fijar orden de threads.
- **U1:** nuevos tests verifican IMAGE undo, A+B→undo B, no-op/cancel/preparación con slot previo, undo durante borrador con/sin snapshot, catálogo vacío. **A1:**6 pruebas MockMvc reales con medios simulados y recorridos añadidos. Estos avances no se invalidan por el fallo de exportación.

Pendientes: artefactos/log limpio descritos en§2; corregir referencias de métodos inexistentes del reporte (`testResolveTargetSingleVsAmbiguous`, `testFsmStatesAndCorrections`, `testFsmCorrectionPriceTargetOnly`, `testUndoImageOperation`, `testUndoOnlyRevertsLastOperation`, `testUndoDuringDraftPreservesOrIgnores`, `testPersistenceAcrossRestarts`). Métodos reales incluyen testFsmTransitionsAndCorrections, testUndoImageChange, testTwoCommitsAndUndoSecondOnly, testUndoDuringDraftWithAndWithoutSnapshot y testPersistenceAcrossSpringContextRestarts. La nota sobre v1.0 también debe reconocer que **88 locales sí fue corroborado**; lo incorrecto era su tabla99 y atribución a exportación, no el total local88.

**Entrega requerida:** una vez corregida lógica, commit autorizado, exportación temporal nueva de ese SHA, comparación de todos sus archivos, `mvn -B clean verify` secuencial con JAVA_HOME/PATH explícitos, versión real, transcript completo y exit code observado, SHA del JAR final y XML con basedir de esa exportación. No reutilizar JAR local antiguo ni carpeta86 para afirmar115 corregidos. Si la evidencia actual existe en otra ruta, suministrarla antes de decidir repetir Maven; esta auditoría no ejecuta una suite solo para suplir un reporte incompleto.

### 4. Matriz v1.1 de criterios del diseño

| Criterio | Dictamen v1.1 | Evidencia / límite |
|---|---|---|
| P1 | FAIL | Números obligatorios y gramática corregidos; expresión inválida seguida de por20000 aún produce Complete, F1 |
| P2 | FAIL | Literales anteriores corregidos; número acentuado/coma y frase132 se truncan, falsa ambigüedad por de, F1 |
| F1 | PASS mecanismo | Cuatro operaciones esperan confirmación; IMAGE→foto y alta parcial→foto reparados; corrección completa de interpretación sigue pendiente P/F2 |
| F2 | FAIL | Reiteración parcial incompatible y corrección #N no cumplen tabla; foto en PRICE fabrica borrador inválido, F3/F4 |
| F3 | PASS mecanismo | Sí duplicado y concurrencia consumen un borrador; no-op conserva slot previo y tests nuevos lo ejercitan |
| U1 | PASS mecanismo | Cuatro operaciones, slot único, A+B, no-op/cancel/preparar, vacío, borrador y high-water; pruebas locales ampliadas |
| J1 | FAIL criterio completo | Rechazos y recuperación/snapshot exacto reparados; payload inválido generado por motor en uso normal, F4 |
| T1 | PASS | Triggers reales y Product/Session íntegros tras rollback, sin transacción exterior de test |
| T2 | PASS | Carreras reales y final completo de reset/aislamiento, revisión de conexión única/transacciones coherente |
| D1 | PASS | Cinco contextos/pools reales cerrados, XML Hikari y reapertura de FSM/slot/contador |
| A1 | PASS |6 XML locales verdes, stub con root, foto+texto/audio y confirmación con TTS null sin duplicación |
| G1 | FAIL evidencia |117 XML locales (115 fuentes versionadas), pero carpeta exportada86 vieja y JAR local desactualizado, sin transcript final atribuible |

PASS mecanismo/fila no acepta el commit global: la interpretación errónea de un destino o monto y la pérdida de un borrador válido impiden completar la issue aunque la transacción que los aplica sea atómica.

### 5. Retrabajo recomendado a Orca y límites de cierre

Orca debe transmitir a Antigravity, sin ampliar la lista de archivos autorizados, primero las correcciones pendientes de **F1/F2** con regresiones de las tablas; gate; después **F3/F4** y roundtrips de los estados generados; gate; finalmente reporte/evidencia final de **F7** y nueva auditoría. **F5/F6 y mejoras T1/T2/D1/A1/U1 ya comprobadas deben conservarse**, sin repetir su implementación ni rediseñar catálogo #2. No hace falta nueva integración externa, esquema, eventId o política de seguridad.

El builder debe mantener regresiones previas y no limitar cada corrección a las frases exactas del auditor: la unidad de validación es el contrato completo de token/expresión/estado, con pruebas proporcionales a las variantes que la implementación realmente admite. Se solicita rechazo/aclaración segura cuando la gramática no resuelve; no cobertura universal de español libre ni aumento del alcance.

Único archivo de repositorio modificado por Codex: **docs/orchestration/issue-3-audit.md**, anexando v1.1 sin borrar v1.0. Preservación ajena y diff de código contra f387257 se verifican al cierre. No push ni cierre de issue; la reauditoría queda entregada con **FAIL accionable**, pendiente de coordinación Orca.

---

## Addendum v1.2 — Reauditoría de 4f2eb56 y reporte f48e6d5

Fecha: **2026-11-13**. Auditor: Codex, Software Architect.
Implementación real: **4f2eb56951ba7793b9c758670d23829651ad50bc**.
Reporte documental: **f48e6d5fd2e879d55f2641dca9ec0f0ceb41cdd0**.
Tarea `task_4ee569f87069`; dispatch `ctx_e52898868681`.
Diseño/aprobación: v1.0 sin modificaciones; antecedentes v1.0/v1.1 conservados íntegros arriba.

**Dictamen v1.2: FAIL por dos comportamientos reproducidos dentro del contrato aprobado.** La evidencia de exportación, compilación, pruebas y empaquetado ahora está corroborada: no se mantiene el bloqueo anterior de G1. Quedan pendientes rechazo de residuos en precio y aclaración de nombres duplicados durante correcciones; no se exige comprensión de español libre ni funciones de issues posteriores.

### 1. Procedencia, verificación real y límites

Se revisaron AGENTS.md y aprobación conservadas, diseño y auditoría con addendum v1.1, diff completo de implementación (4 archivos: motor/parser y sus dos suites) y reporte posterior (únicamente build-report). Las correcciones literales de F1–F4 anteriores tienen regresiones nuevas que se ejecutaron en la entrega limpia. F5/F6 y T1/T2/D1/A1/U1 no se reabren por nuevas exigencias ni se solicita rehacerlos.

Exportación: **/tmp/norma-issue3-export-v11**. Comparación mediante `git ls-tree`, `git show 4f2eb56:<ruta>` y lectura de cada archivo exportado: **56 archivos versionados, cero discrepancias** frente al SHA real 4f2eb56951ba7793b9c758670d23829651ad50bc. Respecto de f48e6d5 difiere únicamente el reporte documental, como estaba previsto. IntentFallback.java y ApiTest.java ajenos están excluidos de la exportación; siguen sin seguimiento en workspace.

**Corrección factual de trazabilidad:** el SHA largo `4f2eb568f9ef298e3b1c6d3bc8527a050f757e23`, citado en encargo/reporte, **no existe en este repositorio** (`git cat-file -e` falla). El corto 4f2eb56 sí identifica inequívocamente el SHA real indicado arriba. La comparación integral de archivos permite atribuir la exportación al commit correcto sin depender del SHA mal transcrito; corregirlo en el reporte. Esta errata documental no se usa por sí sola para rechazar la implementación.

Evidencia primaria observada:

| Artefacto | Ruta / SHA-256 / resultado |
|---|---|
| Log completo | `/tmp/norma-issue3-export-v11/clean-verify.log`; **967da6e2665967fa6d273ae00a7b8c84f49df8e7b8a6f5be99035893163a2c6f** |
| JAR final | `/tmp/norma-issue3-export-v11/target/taller-1.0.0.jar`; **db5367895ccc85ffbe6e3420756b5782ef72df23cbdabf1ee8cc7bbce3dd3040** |
| Atribución de compilación | Log clean, compilación de9 fuentes con release25, pruebas, jar/repackage; fuentes exportadas idénticas al commit |
| Terminación observada | **BUILD SUCCESS**, tiempo40.111s, final2026-11-13T01:54:52-03:00 y **EXIT_CODE=0** al final del log |
| Runtime XML | Java **25.0.4.1 Microsoft**, java.home `/home/gonzalocar/.jdks/ms-25.0.4.1`, basedir de la exportación |
| Comando documentado coherente | `JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1 PATH=<ese-jdk>/bin:$PATH mvn -B clean verify`, Maven3.8.7 /usr/share/maven corroborado en reauditoría anterior |
| JAR frente a target/classes | **0 diferencias**;47 archivos `.class` y19 recursos,66 archivos totales bajo BOOT-INF/classes |

El reporte llama “66 entradas de clase” a los66 archivos: corregir la descripción a47 clases+19 recursos. No hay pérdida ni divergencia de empaquetado por ese error de redacción.

XML examinados y hashes: todos coinciden con la tabla publicada en build-report v1.2. Recuentos independientes:

| Suite | Exportación | Local | Fallos/errores/skips |
|---|---:|---:|---|
| CatalogMigrationTest | 3 | 3 | 0/0/0 |
| CatalogPersistenceTest | 3 | 3 | 0/0/0 |
| CatalogRepositoryTest | 7 | 7 | 0/0/0 |
| FlowTest | 4 | 4 | 0/0/0 |
| IntentParserTest | 60 | 60 | 0/0/0 |
| BotSessionCodecTest | 15 | 15 | 0/0/0 |
| BotEngineTest | 27 | 27 | 0/0/0 |
| BotPersistenceTest | 1 | 1 | 0/0/0 |
| BotApiTest | 6 | 6 | 0/0/0 |
| ApiTest ajeno | ausente | 2 | 0/0/0 local |
| **Total** | **126** | **128** | **0/0/0** |

Esta vez **126 versionadas y128 locales están ambos corroborados**, sin usar las dos ApiTest ajenas para aceptar exportación. Los conteos no equivalen a cobertura exhaustiva de toda frase ni sustituyen los dos contraejemplos siguientes. Manifiesto temporal de SHA/commit/log/JAR/XML: `/tmp/norma-issue3-reaudit-v12/evidence.json`, SHA-256 **c1071ddb160f2956a99038bdfa1aa64dc9c9aedb3be96790a2c9aea6527ccf69**.

### 2. Fallos restantes y retrabajo mínimo

#### R1 / F3 — Alta: corrección por nombre duplicado selecciona la primera pieza

Ubicaciones: **BotEngine.java:293–300** y **568–575**. Ambos handlers de corrección TARGET por String filtran títulos disponibles iguales y usan `findFirst()`. No exigen una única coincidencia, mientras que el parser ordinario sí detecta esa misma ambigüedad. §4 del diseño exige aclarar nombres exactos compartidos y prohíbe elegir la primera pieza; §5 conserva esa regla para correcciones en los tres estados.

Reproducción mínima con datos válidos, en una sesión nueva:

1. Seed contiene #3 Taza panzona a12000; agregar otra pieza #9 con título **Taza panzona**, precio9000 e imagen válida (Catalog.insert permitido; también es posible mediante alta confirmada).
2. Enviar `Cambiá el precio de #2 a 25000` → CONFIRMATION.
3. Enviar `no, la pieza Taza panzona` → responde **“¿Confirmás cambiar el precio de la pieza #3 (Taza panzona) a $25.000?”**, guarda target3 sin aclarar que #9 tiene el mismo nombre.
4. Un sí posterior cambia #3 a25000 mientras #9 queda9000. El resumen y la confirmación no justifican resolver arbitrariamente una referencia que el requisito manda aclarar.

En DETAILS ocurre lo mismo: iniciar `Cambiá el precio de #2` y corregir con el nombre duplicado guarda PRICE target3 y pide precio, sin preguntar numerito entre3/9. Como control positivo, `parser.interpret("Sacá la taza panzona",...)` con el mismo catálogo devuelve Ambiguous candidatos[3,9]. Por tanto no es una expectativa nueva ni limitación de español libre: dos recorridos del sistema tratan de manera incompatible la misma identidad nominal exacta.

**Corrección acotada:** en ambos handlers, resolver la lista completa y aceptar únicamente cardinalidad1; si hay varias coincidencias, conservar íntegro el borrador/snapshot, preguntar por numeritos/títulos candidatos y esperar la aclaración explícita. La corrección posterior `no, la pieza #9` debe seleccionar9, conservar precio/operación y volver a pedir confirmación. No cambiar esquemas/DTO, no prohibir duplicados globalmente ni agregar heurística de similitud. Agregar regresiones de CONFIRMATION y DETAILS con títulos iguales normalizados (mayúsculas/tildes), y controles de nombre único/#N; productos y metadata sin cambios durante la aclaración.

#### R2 / F1 — Alta: extracción de precio todavía descarta texto residual

Ubicación: **IntentParser.java:753–778**. La corrección detecta varios intentos numéricos con isPriceCandidate, pero elimina por completo los demás chunks y acepta el único que considera candidato. §4 exige consumir expresión completa y rechazar texto residual; la salida segura ante datos ajenos a la gramática es aclarar, no interpretar su significado.

Probes mínimos sobre catálogo seed:

| Mensaje | Resultado observado |
|---|---|
| `Cambiá el precio de #2 a 15000 por nada` | Complete ChangePrice(2,15000) |
| `Cambiá el precio de #2 a nada por 15000` | Complete ChangePrice(2,15000) |

El fragmento `nada` se pierde tanto antes como después del monto válido. La presencia de un único monto válido no vuelve válida una expresión que contiene otro delimitador y residuo no reconocido. Es la misma pérdida de información de F1; los casos previos decimal/negativo/overflow+otro monto ahora se rechazan correctamente, pero el contrato no se limita a esos tres tipos de fragmento.

**Corrección acotada:** validar también los tramos descartados dentro de la expresión de precio; después de aislar verbo/destino y delimitador permitido, consumir todo el importe y rechazar/aclarar si queda residuo no autorizado o más de un tramo de importe. No convertir todo el resto de la oración en candidatos por palabras numéricas ni implementar interpretación de “nada”: basta respuesta dirigida sin Complete y conservación de borrador. Mantener títulos con números, precio agrupado, números hablados y quince mil/15 lucas/dieciocho quinientos. Agregar estos dos contraejemplos con un precio válido único y sin residuos como control positivo.

### 3. Prueba dirigida realizada y conservación

Solo se ejecutó un harness temporal: `/tmp/norma-issue3-reaudit-v12/Probe.java`, salida `/tmp/norma-issue3-reaudit-v12/probe.log`, **exit0** como impresión de resultados, no PASS de los comportamientos. Usa JDK25 source-file launcher y el classpath del XML de la **exportación verificada** capturado en `/tmp/norma-issue3-v12-classpath`; compila únicamente el harness temporal. Se ejecuta BotEngine proxy real de Spring contra DB/uploads efímeros `norma-issue3-v12-db-*`, sin clave/red/TTS. No Maven, reconstrucción de producción ni acceso a DB de usuario.

Salida esencial:

```text
normal_name => Ambiguous ... candidates=[3,9]
duplicate_name_correction => CONFIRMATION ... #3 (Taza panzona) ... 25.000
pending_after_duplicate => PRICE target=3 price=25000
price_after_yes #3=25000 #9=9000
details_pending => PRICE target=3
residual:... a 15000 por nada => Complete ChangePrice(2,15000)
residual:... a nada por 15000 => Complete ChangePrice(2,15000)
```

Se comprobaron los hashes de IntentFallback, ApiTest y AGENTS: idénticos a los registrados en v1.0/v1.1 y al manifiesto anterior de diseño. `git diff 3532eeb f48e6d5 --check` terminó exit0. No se alteró ningún archivo ajeno; el registro de medios sigue como producción original y los stubs permanecen exclusivamente en tests.

### 4. Matriz final v1.2 de hallazgos y criterios

| Hallazgo anterior | Estado v1.2 | Evidencia |
|---|---|---|
| F1 | **Pendiente R2** | Variantes anteriores corregidas y ejecutadas; selección de chunk válido todavía omite residuos no autorizados |
| F2 | Cerrado | Prefijo extraído una vez, título Es amor preservado, gramática corregida y lucas conservado; regresiones verificadas |
| F3 | **Pendiente R1** | #N y parcial incompatible reparados; nombres duplicados en nueva ruta de corrección usan findFirst |
| F4 | Cerrado | Foto no aplicable conserva borrador PRICE/SOLD; campos por operación coherentes, roundtrip y recuperación del codec |
| F5 | Cerrado | Longitud uniforme, formato argentino y respuestas de identificación conservadas |
| F6 | Cerrado | Registro real de medios restaurado; stub/root solo en tests |
| F7 | Cerrado evidencia sustancial | Exportación exacta, XML126, log clean verify exitoso y JAR idéntico; corregir erratas de SHA largo y66 archivos en reporte |

| Criterio §10 | Dictamen v1.2 | Motivo |
|---|---|---|
| P1 | **FAIL** | Precio con residuos omitidos produce acción, R2 |
| P2 | **FAIL** | Nombre duplicado en corrección elige primera coincidencia, R1 |
| F1 | PASS mecanismo | Cuatro mutaciones solo tras confirmación, snapshot previo y limpieza atómicos |
| F2 | **FAIL** | Aclaración de destino durante correcciones no respeta ambigüedad, R1 |
| F3 | PASS | Sí duplicado/no-op/historial y serialización conservados |
| U1 | PASS | Undo cuatro operaciones, slot único, A+B, borrador, vacío, high-water y reset conservados |
| J1 | PASS | Codec estricto, legacy y snapshot íntegro; corrección anterior de payloads reparada |
| T1 | PASS | Triggers reales y rollback completo, pruebas exportadas verdes |
| T2 | PASS | Carreras reales y comprobación de resultados seriales/aislamiento |
| D1 | PASS | Cinco contextos/pools reales sobre archivo y recuperación de conversación/undo |
| A1 | PASS |6 pruebas API con medios simulados, DTO/rutas intactos y confirmación sin duplicación |
| G1 | PASS |126 versionadas, clean verify/log/exit0, exportación exacta y JAR concordante |

No se solicita repetir construcción/verificación para reparar únicamente redacción del reporte: corregir sus dos erratas es documental. Como R1/R2 sí requieren código, el builder deberá ejecutar sus regresiones y la verificación final sobre el nuevo commit autorizado, preservando todo lo ya cerrado. Orca coordina ese retrabajo mínimo y la siguiente revisión; no se aceptan las dos omisiones como deuda ni se amplía alcance para resolverlas.

Único archivo de repositorio modificado por esta reauditoría: **docs/orchestration/issue-3-audit.md**, agregando este v1.2. No producción, push ni cierre de issue. **FAIL de conformidad entregado; pendiente resolver R1/R2 antes de aceptación de Orca.**
