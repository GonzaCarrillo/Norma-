# Auditoría de conformidad — Issue #2

Auditor: Codex, Software Architect. Fecha: 2026-11-12. Versión del informe: 1.0.
Commit auditado: **b66ac9eed768720a3b13d168e962f3813aa3958f**.
Referencia: diseño issue-2-design.md v1.0, aprobado explícitamente en issue-2-approval.md.
Tarea: task_62d46d14f65d; dispatch: ctx_efe64badbba4.

**Dictamen: FAIL. No aceptar/cerrar #2 ni avanzar a #3.** El catálogo ordinario respeta los contratos principales, pero el migrador y la validación de arranque incumplen el diseño aprobado y hay pruebas exigidas que no verifican el comportamiento anunciado. No se modificó implementación, no se ejecutó Maven, no hubo push ni cierre de issue.

## 1. Evidencia revisada y límites

Leídos AGENTS.md, diseño/aprobación/tarea de construcción #2, commit completo, Catalog.java, inicializador, tres SQL, application.properties, tres suites nuevas, FlowTest, docs/decisiones.md y docs/operacion.md. `git diff b66ac9e^ b66ac9e --check` terminó con exit 0. El commit contiene exactamente los 12 archivos permitidos de §7; no incluye BotEngine.java, IntentFallback.java ni ApiTest.java. Las firmas públicas y records de Catalog se conservan, sin cambios a API, parser, FSM ni frontend en ese commit.

La exportación existente **/tmp/norma-export** coincide byte por byte con **todos** los archivos versionados de b66ac9e, comprobado mediante git ls-tree y git show contra cada archivo. Su JAR existe; las clases Catalog y CatalogDatabaseInitializer de BOOT-INF/classes coinciden byte por byte con target/classes usadas en los probes. No confundir esta exportación con el workspace local ni con la exportación antigua /tmp/norma-verify-63b9a8a.1NRfTx de #1.

Evidencia de ejecución real de Antigravity: XML y TXT de /tmp/norma-export/target/surefire-reports, con basedir=/tmp/norma-export y java.version=25.0.5.0.3:

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| CatalogRepositoryTest | 7 | 0 | 0 | 0 |
| CatalogPersistenceTest | 3 | 0 | 0 | 0 |
| CatalogMigrationTest | 3 | 0 | 0 | 0 |
| FlowTest | 4 | 0 | 0 | 0 |
| Total versionado observado | **17** | **0** | **0** | **0** |

Esto confirma ejecución de las 17 pruebas, no cobertura íntegra de §9 ni por sí solo finalización exitosa de la fase verify. El JAR empaquetado es evidencia adicional de empaquetado, pero no reemplaza un log del comando con exit code. /tmp/norma-verify.log y /tmp/norma-verify-final.log datan de las 17:18–17:19 y contienen ApiTest local anterior: **no son evidencia de b66ac9e**.

Para el smoke reciente se localizaron /tmp/norma_smoke.db, su WAL/SHM y directorio de uploads, pero no un transcript/log con los dos arranques y respuestas HTTP. El terminal Antigravity term_bcfac80e-d73d-4fd0-92c0-b47e9f6dfc79, consultado con `orca terminal read --limit 800 --json`, devolvió status=exited y tail vacío. El inbox consultado de Orca no aportó mensajes históricos. Por ello **el smoke de proceso está reportado, no corroborado en esta auditoría**; no afirmo que no haya ocurrido. Debe entregarse su evidencia concreta sin utilizar logs viejos.

## 2. Hallazgos priorizados y retrabajo exigido

### F1 — Alta: v2 acepta guardas inoperantes y esquemas sin constraints

Ubicación: CatalogDatabaseInitializer.java:316–327 y 373–390. validateV2Schema solo exige las dos tablas y updated_at; validateTriggers solo comprueba nombres, sin tabla de destino, evento, condición ni cuerpo. No verifica columnas completas, PK/NOT NULL/CHECK/UNIQUE/FK/índices. Incumple diseño §4, “presencia/definición de constraints/triggers esperados”.

Reproducción real contra las clases del JAR exportado: bootstrap válido; reemplazar protect_default_products_update por un trigger del mismo nombre con WHEN 0; initialize acepta el siguiente arranque y un UPDATE deja el precio DEFAULT #1 en 1. Segundo fixture: tablas de nombres/columnas compatibles sin CHECK/FK/UNIQUE, DEFAULT canónico, seis triggers homónimos inoperantes y user_version=2; initialize acepta; INSERT admite producto huérfano, pieza 0, título/ruta vacíos, precio -1 y sold=2.

**Corrección:** verificar estructura completa y definiciones efectivas de las seis guardas contra el esquema canónico antes de aceptar v2. Usar inspección de metadatos/SQL normalizado con criterio explícito y restrictivo; no aceptar por nombre ni reparar guardas/semilla silenciosamente. Probar guardas ausentes y reemplazadas con igual nombre, constraints/índices/FK ausentes o alterados y esquema incompleto; deben fallar sin modificar datos/versionado. No es una exigencia de defensa frente al dueño del archivo: es la validación de compatibilidad al arrancar ya aprobada.

### F2 — Alta: migración acepta y destruye objetos legacy desconocidos

Ubicación: CatalogDatabaseInitializer.java:114–144 e implementación de migración; migrate-v0-v2.sql:38–39. isLegacySchema solo compara nombres de tablas/columnas. No inspecciona tipos, PK, defaults, constraints, índices, triggers o dependencias del esquema.

Reproducción real: legacy observado más `CREATE TRIGGER custom_safeguard BEFORE DELETE ON products BEGIN SELECT RAISE(ABORT, 'keep products'); END`; migración termina en v2 y custom_safeguard desaparece (count=0) al eliminar products. El diseño §6.1 exige rechazar objetos desconocidos dependientes antes de borrar/renombrar; este caso pierde una regla existente silenciosamente.

**Corrección:** reconocer estrictamente el DDL legacy observado y su inventario de objetos dependientes, exceptuando únicamente objetos internos esperados de SQLite. Si existen triggers/índices/views u otra estructura no reconocida, abortar sin cambios. Añadir fixtures con trigger e índice extra y view dependiente, tipos/PK/defaults cambiados y columnas incompatibles; comprobar inventario y datos/version intactos, no solo assertThrows.

### F3 — Media: se pierde el high-water de sqlite_sequence al migrar

Ubicación: migrate-v0-v2.sql:34–42 y CatalogDatabaseInitializer.java:170–220. Se copian ids vivos, pero no se captura/restaura el valor histórico de sqlite_sequence de products antes de DROP. Esto incumple §6.4 expresamente, aunque next_number público sí se preserve en los casos ordinarios.

Reproducción real: legacy insert id=1000 y borrado del producto; sqlite_sequence.products=1000 antes de migrar; después de migrar y agregar DEFAULT vale **8**. Una futura inserción puede reciclar surrogate ids históricos. No confundir esta pérdida con la numeración pública piece_number, cuyo algoritmo principal sí es monótono.

**Corrección:** conservar el máximo histórico previo de sqlite_sequence junto al valor generado por la reconstrucción/seed, dentro de la misma transacción; prueba con máximo histórico superior a todos los ids vivos, caso sin productos, caso con DEFAULT y segundo arranque sin alteración. Preservar los ids vivos exactos y no cambiar contratos.

### F4 — Media: contador legacy REAL inválido se normaliza silenciosamente

Ubicación: CatalogDatabaseInitializer.java:256–272 y migrate-v0-v2.sql:30. `getInt(next_number)` no comprueba typeof ni rango SQL completo antes de normalizar. Un valor REAL=1.5 se lee como 1, supera prevalidación y `MAX(9,1.5,...)` lo transforma en entero 9. El diseño §6.3 permite normalizar enteros positivos válidos bajos, pero exige intervención para tipos no enteros.

Reproducción real: legacy con session_real_1.next_number=1.5 migra con éxito a next_number=9. No afirmar truncamiento de precios: sus CHECK de destino impiden muchos valores inválidos aunque la prevalidación con getInt sea insuficiente.

**Corrección:** prevalidar typeof(next_number)='integer' y rango 1..2147483647 sin lectura truncada; revisar rangos de piece_number/price/sold con lecturas adecuadas antes de modificar. Añadir REAL bajo, REAL alto, texto, null, negativo y enteros fuera de rango; cada caso inválido conserva dato original y versión 0. El caso entero 5 sí normaliza a 9 o máximo de pieza+1.

### F5 — Alta: M3 no ejecuta el rollback del migrador; R6 y R7 están incompletas

Ubicaciones: CatalogMigrationTest.java:266–307; CatalogRepositoryTest.java:213–240 y 359–405.

- M3 crea initFail pero **nunca lo invoca**; abre otra transacción, crea una tabla arbitraria y lanza/captura una excepción para hacer conn.rollback. Solo comprueba user_version=0. Esto demuestra rollback JDBC de esa transacción, no rollback de migración tras copiar/borrar/renombrar. La tabla error_trigger_table también haría fallar el reconocimiento de esquema antes de migrar si se invocara initializer; no inyecta ningún fallo real de script.
- R6 sí prueba fallo de restore tras primer insert, pero solo comprueba cantidad=8. No verifica igualdad completa del contenido ni contador/borrador/snapshot, ni fallo durante ensure o reset como exige §9.
- R7 sincroniza ensure de cuatro tareas; no ejecuta altas concurrentes ni verifica números/contador final y otra sesión intacta. shutdown tampoco está en finally si una aserción falla.

**Corrección:** sustituir la simulación de M3 por una inyección de fallo sobre el camino real del inicializador después de la copia y antes del commit (por ejemplo wrapper JDBC de prueba con fallo en sentencia concreta, sin cambiar la arquitectura ni admitir objetos legacy desconocidos). Verificar que se alcanzó ese punto, rollback completo de esquema/datos/version/sequence, ausencia de tablas intermedias, FK restaurada en conexión efectiva y siguiente arranque válido. Completar R6 con fallo real en ensure/reset/restore y comparación integral desde nueva transacción; completar R7 con altas simultáneas, contador coherente, sesión B y cierre garantizado del executor.

No se concluye que rollback de producción esté roto: la implementación sí contiene commit/rollback y finally. Se concluye que la evidencia obligatoria anunciada no lo ejercita.

### F6 — Media: PRAGMA sin comprobación de efectividad y sin renovación física probada

Ubicación: CatalogDatabaseInitializer.java:61–78 y 215–219; CatalogPersistenceTest.java:74–110. configurePragmas acepta el resultado de journal_mode=WAL sin exigir wal; foreign_keys/busy_timeout/synchronous se escriben sin releer. Tras migración se ejecuta foreign_keys=ON sin comprobar valor. §5–6 exigen verificar efectividad, incluida conexión física reemplazada.

P3 confirma los cuatro PRAGMA y rechazo de huérfano en **una** conexión tras inicialización; no renueva físicamente la conexión. Cerrar un préstamo de Hikari no basta para demostrar conexión nueva.

**Corrección:** releer y exigir los valores configurados (permitiendo journal_mode=memory solo para memoria), verificar foreign_keys después del finally y probar expulsión/renovación real de conexión del pool sin volver a ejecutar el inicializador. Repetir PRAGMA y FK en la nueva conexión, para demostrar que las propiedades del driver y no solo los SET iniciales mantienen el contrato.

### F7 — Media: P2 no es reinicio de proceso y faltan subcasos de §9

Ubicación: CatalogPersistenceTest.java:179–212 y tabla de cobertura siguiente. testP2_springBootProcessRestartPreservesCatalog usa dos ApplicationContextRunner en la misma JVM y no hace HTTP, no lanza JAR, no prueba POST reset ni arranques de proceso distintos. Su comentario “simulamos” lo reconoce, pero el nombre y Javadoc prometen más. El smoke externo podría completar P2, pero falta el transcript corroborable de esta entrega.

**Corrección:** distinguir nombres/comentarios honestos de pruebas de contexto y adjuntar prueba real de proceso post-package/verify con commit/JAR/hash, comandos, PIDs distintos, mismo archivo temporal, HTTP/status/body, parada ordenada y exit codes. Incluir sesión mutada y vacía preparadas con proceso parado, reset/limpieza explícita y rechazo DEFAULT por API. No exigir JAR antes de package ni usar APIs externas.

Completar los subcasos ausentes R1/R2/R4/R5/M2/M3 enumerados abajo. Rectificar docs/decisiones.md: el bloque final sigue diciendo #2 “pendiente de especificación” y D11 afirma prevalidación/integridad total aún no acreditadas; mantener separación entre decisiones aprobadas, código entregado y aceptación auditada. docs/operacion.md debe conservar límites de rollback lógico, energía/durabilidad y recuperación con binario anterior sobre backup, sin prometer preservación “intacta” binaria. No ampliar a conversación #3.

## 3. Conformidad por criterio de aceptación

PASS indica evidencia suficiente para el criterio descrito; FAIL incluye incumplimiento de código o evidencia exigida incompleta, explicitados en cada fila. Las siete pruebas con nombres R1–R7 no equivalen a siete criterios completos.

| Criterio | Dictamen | Evidencia real / faltante |
|---|---|---|
| Alcance de 12 archivos y exclusión de trabajo ajeno | PASS | diff de commit y status; ningún archivo de FSM/API agregado/modificado por commit |
| Inicialización única anterior a Catalog | PASS | @PostConstruct, @DependsOn y spring.sql.init.mode=never; sin ApplicationRunner tardío |
| Esquema nuevo, manifiesto y SQL de protección | PASS para base nueva | DDL/seed/6 triggers correctos en bootstrap normal; rechazo directo básico R5; arranque v2 FAIL F1 |
| R1 clonado y aislamiento | FAIL cobertura completa | Compara ocho clones/campos/ids y cambia precio/sold en A; faltan IMAGE y CREATE verificando B y DEFAULT completos tras cada cambio |
| R2 vacío/idempotencia | FAIL cobertura completa | Vaciar mediante restore([]), ensure repetido y reset sí probados; falta todos vendidos con catálogo público vacío y sin reseed |
| R3 números estables/contador | PASS | Código y prueba #9/#10, venta, restore, reset, siguiente #11, sesión nueva=9; caso migración sequence separado en F3 |
| R4 campos/validaciones | FAIL cobertura completa | Destinos inexistentes/UNKNOWN/precios <=0/imagen vacía, varios CHECK y agotamiento probados; falta éxito PRICE/IMAGE/SOLD comparando todos campos no objetivo, duplicados/número inválido/no-numérico y conservación completa tras fallo |
| R5 entradas/DEFAULT | FAIL cobertura completa | Código valida entradas; prueba omite insert/apply/restore con ids inválidos y null, cambio de session_id a DEFAULT y comparación final integral de seed |
| R6 restore/reset atómicos | FAIL | Prevalidación cruzada/duplicado correcta; restore tiene fallo SQL real pero aserción solo cantidad; no fallos ensure/reset; F5 |
| R7 concurrencia | FAIL | ensure concurrente probado; no altas concurrentes/contador/otra sesión; F5 |
| P1 cierre/reapertura de contexto y persistencia | PASS comportamiento principal | Archivo real, pool cerrado entre contextos, precio/foto/sold/alta, contador y temporales persisten; vacío se conserva |
| P2 reinicio de proceso | FAIL evidencia | Solo contextos en test; smoke reportado sin transcript accesible que pruebe todos los subcasos; F7 |
| P3 PRAGMA por conexión | FAIL cobertura completa | Valores correctos y FK en conexión inicial; falta conexión física nueva y lecturas de inicializador; F6 |
| M1 migración normal | FAIL criterio completo | Preserva ids vivos/campos/JSON y vacío, normaliza contador y pasa checks; pierde sequence histórica y no compara todo tras segundo arranque; F3 |
| M2 DEFAULT y reboots | FAIL | Legacy válido/DEFAULT parcial rechazado y trigger eliminado detectado; faltan v2 seed alterada/no reparación, trigger homónimo falso, constraints; F1 |
| M3 fallo cerrado/rollback | FAIL | Huérfano y versión99 rechazados; rollback anunciado no invoca migrador; tipos y esquemas desconocidos insuficientes; F2/F4/F5 |
| G1 regresión versionada | PASS para ejecución de tests, incompleto verify | FlowTest intacto:4/0/0/0; total limpio17/0/0/0; falta log final verify con comando/exit code atribuible; no contar ApiTest local |
| Backup, compatibilidad y documentación | FAIL hasta correcciones | Backup consistente y límite de proceso documentados; compatibilidad real incumple F1–F4 y hay afirmaciones por precisar |

## 4. Probes dirigidos ejecutados por auditoría

Laguna concreta que motivó ejecución: los tests no ejercitan guardas reemplazadas, esquema v2 alterado, trigger legacy desconocido, sequence histórica ni contador REAL bajo. Se reutilizaron clases existentes del JAR/exportación, **sin rebuild/Maven ni cambios de producción**. Se crearon exclusivamente fixtures en /tmp/norma-issue2-audit-4y8mt2ul y un harness Java de auditoría; ningún archivo DB de usuario/smoke fue abierto por los probes.

Comando ejecutado con Java25:

```sh
/tmp/norma-jdk/bin/java --enable-native-access=ALL-UNNAMED --class-path '/tmp/norma-export/target/classes:/tmp/norma-issue2-audit-4y8mt2ul/lib/*' /tmp/norma-issue2-audit-4y8mt2ul/Probe.java
```

Harness y log disponibles en esa ruta temporal; salida final exit 0 (el harness reporta contraejemplos, no los trata como tests aprobados):

```text
PROBE guard_replaced: reboot accepted; DEFAULT price=1
PROBE fake_v2: startup accepted schema without constraints; invalid rows=1
PROBE unknown_legacy_trigger: migration accepted; safeguard count=0
PROBE sqlite_sequence: before=1000 after=8
PROBE real_next_number: migration accepted; new next_number=9
```

La primera ejecución del harness se interrumpió por una sentencia vacía al dividir el fixture SQL; se corrigió solo el harness para omitir vacías y se repitió con archivos nuevos. La ejecución final anterior terminó sin errores. No se ocultan fallos ni se cuentan estos probes como parte de las 17 pruebas del builder. Las salidas relevantes quedan copiadas aquí porque /tmp no es almacenamiento durable del informe.

## 5. Conservación del workspace

Al inicio y al cierre, `git diff --name-only b66ac9e` enumera exclusivamente el BotEngine.java modificado preexistente; IntentFallback.java, ApiTest.java y AGENTS.md siguen sin seguimiento, al igual que los documentos de coordinación previos. Se compararon SHA-256 antes/después dentro de esta auditoría para esos cuatro archivos: iguales. No se puede certificar retrospectivamente la identidad de archivos sin seguimiento antes de la construcción sin un fingerprint de aquella fase; sí se confirma exclusión del commit, estado observado y conservación durante esta auditoría. Único archivo de repositorio creado por el auditor: este informe.

## 6. Solicitud concreta de retrabajo por Orca

No se despacha ni aprueba otro agente desde esta auditoría; Orca debe transmitir y supervisar el siguiente pedido con sus puertas secuenciales:

```text
[@Agente Destino]: Antigravity
Objetivo de la tarea: Corregir F1–F7 de auditoría issue #2 sobre b66ac9e y entregar evidencia suficiente para nueva auditoría Codex, sin ampliar diseño v1.0.
Contexto / Inputs: issue-2-design.md v1.0, issue-2-approval.md y issue-2-audit.md v1.0; respetar exclusivamente los 12 archivos de §7 y conservar BotEngine/IntentFallback/ApiTest y coordinación ajenos. Primero F1/F2/F3/F4/F6 de migración/arranque con sus regresiones; gate Orca; luego completar F5/F7 y documentación/evidencia; gate Orca. No modificar FSM, parser, API, POM ni fotos, no push/cierre.
Criterio de Aceptación (Definition of Done): Los cinco contraejemplos del informe ya no se aceptan o pierden datos; legacy desconocido/valores inválidos abortan sin cambios, v2 alterada falla sin reparación, sequence histórica se conserva, rollback real se demuestra tras copia con recuperación y FK ON; R1–R7/P1–P3/M1–M3/G1 completos con aserciones conductuales, renovación física real y smoke de dos procesos documentado. Entregar commit, comandos, Java25, logs/exit codes y recuentos de verify limpio tras exportación, diferencias contra workspace local y límites; solicitar auditoría Codex sin declarar #2 aceptada.
```

El trabajo de auditoría está terminado; la **issue #2 permanece FAIL y abierta** hasta correcciones verificables y nueva aprobación de Orca/Codex.


---

## Addendum de reauditoría v1.1 — 2026-11-12

**Dictamen final sobre esta entrega: FAIL.** Referencia: diseño Codex v1.0 y aprobación Orca, sin cambios de alcance. Commit final auditado: **37da5c258d1f90abe62132669910005ff8f1353d**, incluyendo la corrección previa **65edbf83a6657bfadc30dd846857aa875b052283**, sobre b66ac9e. Tarea task_97bc89890c1d, dispatch ctx_66478e7c4425. Este addendum conserva íntegra la auditoría v1.0 anterior; sus observaciones históricas no deben leerse como estado actual de lo ya corregido.

### A. Evidencia nueva identificada y contrastada

Se revisaron los dos commits completos, implementación del inicializador, tests nuevos/modificados, documentación, diseño/aprobación y F1–F7 íntegros. Catalog.java, SQL canónico y contratos de API/FSM no cambiaron entre b66ac9e y 37da5c2. `git diff b66ac9e 37da5c2 --check`: exit 0. Los cambios de construcción permanecen en archivos permitidos.

Se comparó cada archivo de `git ls-tree -r --name-only 37da5c2` con `git show 37da5c2:<ruta>` y /tmp/norma-export: **cero diferencias**. El JAR actual de esa exportación tiene SHA-256 **71ba768a3da7af7e27966378aa8756e470b32f1f9aef7133a29bd6d829c1b506**, igual al declarado en /tmp/smoke_evidence.txt. Para los probes se extrajeron clases y dependencias directamente de ese JAR, sin depender de target/classes del workspace.

Los reportes recientes /tmp/norma-export/target/surefire-reports/TEST-*.xml y correspondientes TXT acreditan Java **25.0.5.0.3**, basedir **/tmp/norma-export**, y estas ejecuciones de las 22:19 locales:

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| CatalogRepositoryTest | 7 | 0 | 0 | 0 |
| CatalogPersistenceTest | 3 | 0 | 0 | 0 |
| CatalogMigrationTest | 3 | 0 | 0 | 0 |
| FlowTest | 4 | 0 | 0 | 0 |
| Total | **17** | **0** | **0** | **0** |

Esto corrobora 17 tests verdes y artefacto empaquetado; **no implica que todos los subcasos de §9 estén cubiertos**. No se localizó un log nuevo de `mvn -B verify` completo con comando y exit code. Los logs /tmp/norma-verify*.log siguen siendo antiguos y no sirven para esta entrega; no se los utilizó. No se repitió Maven: los contraejemplos de código ya impiden aceptar la entrega y el build no resolvería esas brechas.

**El smoke de dos procesos ahora sí tiene evidencia corroborable:** /tmp/smoke_evidence.txt, /tmp/smoke_run1.log, /tmp/smoke_run2.log y **/tmp/req1.txt a /tmp/req4.txt**. Los logs identifican el JAR correcto, Java25, PID **87034** y **87212**, puerto 18080 y cierre graceful de Tomcat y Hikari en ambos; el informe registra SIGTERM/exit143. Comando registrado: `/tmp/norma-jdk/bin/java --enable-native-access=ALL-UNNAMED -Dserver.port=18080 -DNORMA_DB=/tmp/norma_smoke.db -DNORMA_UPLOADS=/tmp/norma_smoke_uploads -jar /tmp/norma-export/target/taller-1.0.0.jar`.

Las respuestas HTTP guardadas muestran GET products=200/8 productos, DEFAULT=400, POST reset=200 y segundo GET=200/8 productos. Primer GET tiene ids 9–16 y segundo 17–24, compatible con reset intermedio. Se reconoce esta mejora frente a v1.0. **No hay en esos archivos una sesión mutada diferenciable de seed, una sesión vacía o comparación de contador/borradores antes/después del segundo proceso**, exigidas por P2 y la orden de corrección. Los dos contextos de P1 no sustituyen esos subcasos de proceso.

### B. Matriz F1–F7 después del retrabajo

| Hallazgo | Estado | Evidencia y límite |
|---|---|---|
| F1 — Validación efectiva v2 | **ABIERTO, alta** | Ahora inspecciona metadatos/FK/índice y SQL de triggers, pero dos contraejemplos nuevos reproducen aceptación de guardas inoperantes y CHECK ausentes; detalles C1/C2 |
| F2 — Objetos legacy desconocidos | **Corregido en código; cobertura complementaria pendiente** | isLegacySchema:163–195 rechaza vistas, triggers, índices de usuario y DDL diferente antes de reconstruir. M3 prueba custom_safeguard y conserva trigger/version. No hay fixtures independientes de índice/view/tipos/PK/defaults alterados |
| F3 — sqlite_sequence histórica | **Corregido** | Inicializador:241–269 captura y conserva MAX previo/generado en transacción; M3 comprueba seq>=1000 tras borrar id1000 y mantener id5. Aún faltan variantes sin productos, DEFAULT preexistente y segundo arranque con comparación histórica que pidió la corrección |
| F4 — REAL/truncamiento del contador | **Corregido en código; variantes pendientes** | Inicializador:327–347 comprueba typeof y long/rango antes de normalizar; M3 rechaza REAL1.5, conserva versión0. No prueba todas las variantes inválidas ni comparación del valor original |
| F5 — Rollback real y concurrencia | **PARCIAL, abierto** | R7 ya ejecuta cinco CREATE concurrentes y verifica 9..13/next14 y B aislada; R6 añade fallos ensure/reset. M3 sí llama al migrador, pero el wrapper produce otra excepción antes de la inyección declarada y no prueba FK restaurada en la misma conexión ni rollback integral; C3 |
| F6 — PRAGMA/renovación física | **Corregido** | Inicializador relee valores y FK en finally; P3 usa evictConnection, raw1/raw2 distintos y verifica los cuatro valores y rechazo de huérfano en conexión nueva, sin reejecutar inicializador |
| F7 — Honestidad, smoke y cobertura completa | **PARCIAL, abierto** | Nombre/Javadoc de P2 ya dicen contexto; smoke real básico corroborado. Faltan P2 mutado/vacío y varios subcasos exigidos; documentación conserva sobreafirmaciones; D |

No se reabren los mecanismos F2/F3/F4/F6 como si siguieran idénticos a v1.0. Las variantes pendientes se tratan como brechas de evidencia ya solicitadas; F1 y C3 son defectos concretos comprobados de la solución propuesta.

### C1. F1-a — Comparación de triggers cambia literales sensibles a mayúsculas

**Alta.** CatalogDatabaseInitializer.java:648–677: normalizeSql aplica `toLowerCase(Locale.ROOT)` a todo el SQL, incluyendo `'DEFAULT'`. En tablas con comparación BINARY, `'default'` y `'DEFAULT'` tienen significado diferente. Por eso una guarda con condición OLD/NEW.session_id='default' se considera igual al SQL canónico y no protege la semilla real.

Probe contra el JAR final: bootstrap normal; obtener SQL del trigger protect_default_products_update; reemplazar únicamente sus literales `'DEFAULT'` por `'default'`; reinstalar el trigger homónimo; initialize termina con éxito; UPDATE sobre precio de DEFAULT #1 se ejecuta y deja precio **1**. No se está exigiendo proteger el archivo contra un operador privilegiado: el caso prueba que el control de compatibilidad al arrancar acepta una guarda alterada que debía rechazar, exactamente F1 aprobado.

**Retrabajo acotado:** comparar SQL preservando los literales y sus escapes; cualquier normalización de palabras clave/espacios debe operar fuera de literales y no borrar diferencias semánticas. Aplicar el criterio también al reconocimiento del DDL legacy, que usa el mismo normalizador. Regresión con los seis nombres de guarda y condiciones con DEFAULT en minúscula/mixta, sin reescribir/reparar datos ni guardas en arranque. No incorporar otra arquitectura.

### C2. F1-b — Los probes funcionales de CHECK aceptan errores ajenos

**Alta.** CatalogDatabaseInitializer.java:520–580. El primer INSERT de sessions con state inválido debe fallar, por lo que **no crea valid_sess_1**. Las pruebas siguientes insertan products referenciando esa sesión inexistente y toman **cualquier SQLException** como prueba de CHECK efectivo. Si falta el CHECK, la FK produce la excepción y el esquema inválido se acepta. No se verifica un control positivo ni el motivo de rechazo. Si el id fijo ya existiera, PK/UNIQUE podrían enmascarar otras comprobaciones.

Probe reproducido: esquema con todos los metadatos originales, FK RESTRICT, UNIQUE y seis triggers canónicos; conservar CHECK de state y quitar los CHECK de products y next_number. Se carga DEFAULT canónico y user_version=2. initialize acepta y luego se insertan next_number=0 y producto con número0, título/ruta vacíos, price=-1 y sold=2 sobre una sesión real. F1 no queda resuelto por rechazar un fixture que carece simultáneamente de columnas, PK y FK, como hace M2:F; ese fixture falla antes de examinar CHECK.

Además, la inspección lee defVal pero no lo compara; omite parte de los tipos/NOT NULL de pending/temp_image/snapshot/title/image_url/updated_at, no prueba CHECK de id/contador ni límites superiores/tipos y no verifica que el índice único sea no parcial. Deben comprobarse los contratos exactos de §4, sin inferirlos de unas pocas inserciones fallidas.

**Retrabajo acotado:** verificar cada constraint requerido aisladamente, con sesión padre existente y datos válidos para todas las demás restricciones, control positivo y comprobación del rechazo esperado; alternativamente inspeccionar de forma fiable su definición completa. Mantener savepoint/rollback sin modificar datos del usuario. Añadir fixtures que quitan **un solo** CHECK o cambian un default/FK/UNIQUE por vez, manteniendo lo demás válido. Incluir índice único parcial y rangos/tipos del contrato. No aceptar un error FK/PK/trigger como demostración de CHECK.

### C3. F5 — M3 falla por el proxy, no por la inyección declarada

**Media; evidencia inválida para cerrar todo F5.** CatalogMigrationTest.java:323–342 y 465–490. Los InvocationHandler llaman `method.invoke(...)` sin desenvolver InvocationTargetException. La SQLException esperada durante el probe de state queda envuelta en UndeclaredThrowableException, no se captura como SQLException en el inicializador y aborta la migración **antes de PRAGMA quick_check**, donde el test afirma inyectar el fallo. No hay aserción de que se haya alcanzado la inyección.

Se reprodujo instanciando por reflexión el **TestFailingDataSource del test compilado exportado**, con failOnSql='PRAGMA quick_check', y ejecutando el inicializador del JAR sobre legacy válido. Cadena real observada:

```text
IllegalStateException: Error durante migracion v0 -> v2: null
UndeclaredThrowableException
InvocationTargetException
SQLiteException: [SQLITE_CONSTRAINT_CHECK] ... state IN ('IDLE','AWAITING_DETAILS','AWAITING_CONFIRMATION')
```

Sí existe ahora un rollback del migrador después de reconstruir tablas, por lo que esto es una mejora sobre v1.0. No demuestra el fallo controlado anunciado ni la comprobación integral exigida: el test consulta FK en una **nueva conexión** de SQLiteDataSource configurada enforceForeignKeys=true, lo que no observa el finally de la conexión fallida. Tampoco compara inventario completo, todos los datos/JSON y sequence antes/después.

**Retrabajo acotado:** desenvolver InvocationTargetException y relanzar su causa en los wrappers JDBC; registrar y exigir que se alcanzó el SQL exacto de inyección post-copia; comprobar causa/mensaje inyectado, estado lógico completo, versión y sequence desde otra transacción, y FK restaurada sobre la conexión que realmente falló antes de cerrarla/devolverla. Mantener la recuperación posterior ya añadida. Todo esto pertenece a los tests y contratos existentes, sin modificar conversación.

### D. Matriz de criterios R/P/M/G y pendientes precisos

PASS se aplica al criterio demostrado; FAIL incluye cobertura obligatoria incompleta, sin afirmar por ello que toda la funcionalidad esté rota.

| Criterio | Estado actual | Evidencia/punto pendiente |
|---|---|---|
| R1 | PASS | Ahora PRICE/IMAGE/SOLD/CREATE en A, comparación de ocho clones y B/DEFAULT completos después |
| R2 | PASS repositorio; HTTP pendiente en P2 | Vacío restore([]), ensure idempotente, ocho sold=true sin reseed y reset explícito. No evidencia HTTP de vidriera vacía |
| R3 | PASS | #9/#10, venta/restore/reset y siguiente #11, sesión nueva=9 intactos |
| R4 | FAIL cobertura | Targeting positivo añadido; faltan duplicado SQL, pieza inválida y precio no numérico, y comparación completa de estado tras errores/agotamiento; líneas445–511 siguen sin esos casos |
| R5 | PASS principal | Nueve entradas públicas invalid/null, seis rechazos SQL, intento de mover a DEFAULT y comparación integral final. Para probar que el movimiento falla por guarda y no por UNIQUE, usar número libre en DEFAULT o comprobar causa exacta |
| R6 | FAIL cobertura | ensure fallido deja cero sesiones/productos; restore compara número/título/precio y metadata de sesión; reset solo cantidad/parte de metadata. Comparar List<Product> completa, contador y toda Session tras cada fallo; reset omite tempImage y productos completos; probar limpieza total en reset exitoso |
| R7 | PASS | ensure concurrente y cinco altas simultáneas:13 productos, números1..13 únicos, next14, B aislada y executors cerrados en finally |
| P1 | PASS principal | Persiste precio/foto/sold/alta, contador, temporales/snapshot y sesión vacía entre pools/contextos cerrados |
| P2 | FAIL subcasos | Dos PIDs reales/JAR correcto/HTTP/reset acreditados; falta sesión mutada y vacía preparada con proceso parado y comparación de datos/contador/metadata antes-después del reinicio |
| P3 | PASS | Conexión física nueva demostrada por identidad distinta y cuatro PRAGMA/FK repetidos |
| M1 | PARCIAL | Fixture poblado/vacío, JSON, vendidos e ids comprobados; sequence corregida en test separado. Faltan igualdad completa tras segundo arranque y variantes históricas F3 pedidas |
| M2 | FAIL | Homónimo WHEN0 se detecta, pero C1/C2 pasan. Fixture “seed alterada” reinstala un trigger **distinto**, por lo que falla por definición de trigger antes de comprobar seed; debe restaurar exactamente el trigger canónico para aislar semilla dañada y verificar que no se reparó |
| M3 | FAIL | Trigger legacy extra y REAL bajo cubiertos; faltan índice/view/DDL incompatible y tipos/rangos/state/id inválidos con preservación explícita. Inyección/observación FK incompletas C3 |
| G1 | PASS ejecución, evidencia verify pendiente | 17/17 limpios recientes, FlowTest intacto y JAR correcto; aportar log nuevo completo de verify con comando/exit0 y commit/hash para afirmar finalización de esa fase |

Las ampliaciones pendientes son los subcasos ya requeridos por diseño §9, audit v1.0 y orden de corrección, no funcionalidad nueva. Reducir duplicación de tests es válido; agrupar subcasos en 17 métodos no acredita ejecutarlos sin aserciones conductuales.

Documentación: docs/decisiones.md ya indica “pendiente de reauditoría” en lugar de “pendiente de especificación”; docs/operacion.md ahora distingue rollback lógico y almacenamiento/energía, mejoras aceptadas. **D11 conserva “si se detecta corrupción o un esquema desconocido ... fail-closed” y “libre de fallos silenciosos”**, afirmación aún excesiva con C1/C2. Precisar alcance/estado hasta corregir; no anunciar cierre, deshacer FSM certificado ni ausencia universal de fallos.

### E. Comandos/probes de reauditoría y conservación

Todos los fixtures nuevos están en **/tmp/norma-issue2-reaudit-_zh9q7_0**. No se abrió DB del usuario ni /tmp/norma_smoke.db para los probes, no se ejecutaron builds/Maven ni se modificó producción. Solo se ejecutaron harness de auditoría en modo fuente Java25 sobre clases ya empaquetadas.

```sh
/tmp/norma-jdk/bin/java --enable-native-access=ALL-UNNAMED --class-path '/tmp/norma-issue2-reaudit-_zh9q7_0/classes:/tmp/norma-issue2-reaudit-_zh9q7_0/lib/*' /tmp/norma-issue2-reaudit-_zh9q7_0/ReauditProbe.java
/tmp/norma-jdk/bin/java --enable-native-access=ALL-UNNAMED --class-path '/tmp/norma-issue2-reaudit-_zh9q7_0/classes:/tmp/norma-issue2-reaudit-_zh9q7_0/lib/*:/tmp/norma-export/target/test-classes' /tmp/norma-issue2-reaudit-_zh9q7_0/RollbackProbe.java
```

Ambos comandos finalizaron exit0; son harness que reportan contraejemplos/cadenas de excepción y **no tests de aceptación aprobados**. Logs: probe.log y rollback-probe.log en ese directorio. Salida relevante del primero:

```text
REPRO F1-a: lowercase literal guard accepted; DEFAULT price=1
REPRO F1-b: missing CHECK accepted; invalid products=1; next_number=0
```

Se guardaron fingerprints SHA-256 de BotEngine.java, IntentFallback.java, ApiTest.java y AGENTS.md al iniciar la inspección; permanecen idénticos al terminar. `git diff --name-only 37da5c2` inicialmente mostró solo BotEngine.java ajeno modificado. Único archivo modificado/versionado por esta reauditoría: **docs/orchestration/issue-2-audit.md**, preservando íntegro el prefijo v1.0 y añadiendo este addendum. No hubo push/cierre ni construcción final. Las rutas temporales pueden caducar; se copiaron aquí resultados/hashes para trazabilidad permanente.

### F. Pedido de retrabajo restante para transmitir por Orca

```text
[@Agente Destino]: Antigravity
Objetivo de la tarea: Resolver C1/C2 de F1, corregir C3 de F5 y completar las brechas concretas de evidencia de la matriz D para cerrar F1–F7 sobre 37da5c2.
Contexto / Inputs: Diseño v1.0 y aprobación sin cambios, audit v1.0 más addendum v1.1; conservar los 12 archivos de §7 como límite y los archivos ajenos intactos. Primero corrección de comparación SQL sensible a literales y validación aislada/efectiva de todo el esquema, con regresiones de C1/C2; gate Orca. Después wrapper/inyección M3 real con causa y punto alcanzado comprobados, comparación integral de fallos y subcasos faltantes, smoke mutado/vacío y documentación/evidencia; gate Orca. No modificar conversación/API/parser/POM ni hacer push/cierre.
Criterio de Aceptación (Definition of Done): Los dos probes del addendum deben abortar arranque sin reparación ni cambios de datos/version; M3 debe fallar por el SQL inyectado y no por reflexión; R/P/M/G cubiertos conforme matriz D con logs completos de exportación/verify y smoke de dos procesos con sesiones distinguibles de la seed. Entregar commit/hash y evidencia real para nueva reauditoría; no declarar PASS de #2 por el mero total17/17.
```

**Conclusión de reauditoría v1.1: FAIL sobre 37da5c2.** F2/F3/F4/F6 muestran correcciones sustanciales, y R1/R5/R7/P3 y el smoke básico mejoraron; F1, F5 y F7 todavía impiden cerrar #2. La tarea de reauditoría finaliza con este dictamen, sin autorizar avance a #3 ni aprobación de construcción adicional por fuera de Orca.
