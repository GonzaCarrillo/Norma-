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


---

## Addendum de auditoría final v1.2 — 2026-11-12

**Dictamen: FAIL sobre 123d23a766afee27b7ebf8f5c0bbd45113ff7ec9.** Diseño aprobado: v1.0, sin ampliación de alcance. Auditor Codex; tarea task_968492c17ed2; dispatch ctx_ffdd7323bbc9. Se conservaron íntegros el informe v1.0 y addendum v1.1. Esta entrega **sí repara los contraejemplos exactos C1–C3**, pero introduce una regresión reproducida de reinicio con identificadores de sesión válidos (C4) y no completa toda la evidencia exigida. No se modifica implementación ni se autoriza cierre/avance a #3.

### 1. Evidencia final contrastada

Leídos AGENTS.md, diseño v1.0 y aprobación, auditoría completa con addendum v1.1, orden de segundo retrabajo, `git show 123d23a` y sus cuatro archivos, suites completas afectadas, reporte final y evidencias temporales. `git diff 123d23a^ 123d23a --check` finalizó exit0. Solo se modificaron archivos permitidos en el commit: inicializador, CatalogMigrationTest, CatalogRepositoryTest y docs/decisiones.md.

Comparación byte por byte de todos los archivos versionados de 123d23a contra /tmp/norma-export mediante git ls-tree/git show: **cero diferencias**. La exportación no incluye IntentFallback.java ni ApiTest.java ajenos. SHA-256 del JAR final actual: **f99b483f69fe8051d0f7d5bd36fcb3020aee691615802704c6108522b1a605c1**; coincide con el reporte de entrega final. Las clases/dependencias usadas por los probes se extrajeron de ese JAR, no de target del workspace.

**La brecha anterior del log verify queda resuelta:** /tmp/clean_verify_full.log y /tmp/norma_final_delivery_report.txt muestran compilación release25, clean en /tmp/norma-export/target, ejecución de las cuatro suites, empaquetado/repackage y **BUILD SUCCESS**, finalizado a las **22:59:09 -03:00**. Los XML de /tmp/norma-export/target/surefire-reports contienen basedir=/tmp/norma-export, java.version=25.0.5.0.3 y:

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| CatalogRepositoryTest | 7 | 0 | 0 | 0 |
| CatalogPersistenceTest | 3 | 0 | 0 | 0 |
| CatalogMigrationTest | 3 | 0 | 0 | 0 |
| FlowTest | 4 | 0 | 0 | 0 |
| Total | **17** | **0** | **0** | **0** |

El comando de entrega declarado es `mvn verify` con Java25 sobre exportación limpia; el log incluye clean y todas las fases anteriores. El auditor **no repitió Maven**, pues ya existe evidencia de build y el defecto de arranque se comprueba sin recompilar. No usar /tmp/norma-verify-b2.log como log de exportación: sus rutas corresponden al workspace. Tampoco los logs de las 17:19 ni los de #1.

### 2. C1–C3 reejecutados: reparaciones comprobadas

Se ejecutó un harness dirigido usando clases del **JAR final f99b...** y el TestFailingDataSource compilado de la exportación, exclusivamente sobre nuevos fixtures temporales. Se compararon inventario SQL completo, filas completas de sessions/products, sqlite_sequence y user_version antes/después en cada rechazo/rollback, no identidad binaria del archivo (WAL puede variar).

- **C1 cerrado para el contraejemplo exacto:** trigger homónimo con `'default'` en vez de `'DEFAULT'` se rechaza por diferencia de definición; inventario/datos/sequence/versión permanecen lógicamente iguales. El literal conserva su caso en la comparación.
- **C2 cerrado para el contraejemplo exacto:** fixture con metadatos/FK/UNIQUE y seis guardas válidas, DEFAULT exacto, pero sin CHECK de products/next_number se rechaza por DDL no canónico; preservación lógica completa=true. La validación ahora comprueba DDL íntegro, defaults/nullability, índice no parcial y probes con padre válido y causa CHECK. C4, abajo, es una regresión de esos nuevos probes positivos, no la persistencia del antiguo bypass FK.
- **C3 cerrado:** el wrapper desenvuelve InvocationTargetException y la inyección **sí alcanza PRAGMA quick_check**; causa real “Injected test failure on: PRAGMA quick_check”, flag=true y FK=1 capturada en la conexión fallida antes de cerrar. Snapshot lógico completo coincide antes/después del rollback, incluyendo borrador/snapshot no nulos del fixture; el siguiente arranque sin inyección migra a user_version=2. Ya no falla por un error incidental de reflexión.

No se reabren estos tres contraejemplos como si no hubieran sido atendidos. Sus tests nuevos y los probes de auditoría respaldan la reparación.

### 3. C4 — Alta: una sesión pública válida impide el siguiente arranque

**Ubicación:** CatalogDatabaseInitializer.java:590–610, especialmente INSERT del control positivo en línea596. El inicializador escribe siempre `valid_sess_check_1` en sessions dentro del savepoint. Ese identificador es válido según Catalog.valid y no está reservado por contrato. Si una sesión real ya lo tiene, el control positivo falla por PK y el inicializador aborta, aunque tablas, datos y DEFAULT sean completamente válidos. Los nombres fijos valid_sess_check_2/3/4 también pueden producir colisiones en los probes negativos y provocar rechazo por motivo ajeno a CHECK.

**Reproducción comprobada contra JAR final:**

1. Inicializar archivo nuevo normalmente (v2 canónica).
2. Comprobar `Catalog.valid("valid_sess_check_1")`: devuelve el identificador sin error.
3. Preparar la sesión legítima exactamente como ensure: INSERT sessions(id) y clonación SQL de las ocho piezas desde DEFAULT. No alterar esquema, seed, guardas ni contador.
4. Volver a ejecutar initialize sobre ese archivo: falla con **SQLITE_CONSTRAINT_PRIMARYKEY / UNIQUE constraint failed: sessions.id**. Todos los datos/versión permanecen iguales, pero el servicio no puede iniciar.

El mismo estado puede producirse mediante GET /api/products?sessionId=valid_sess_check_1, pues la API admite el identificador y llama ensure. No se usó la API del usuario para provocar el caso; solo fixture aislado. El defecto afecta la garantía de reinicio de datos válidos y potencialmente la migración de una sesión legacy homónima.

**Retrabajo exacto:** los probes de inicialización deben usar identificadores válidos nuevos cuya ausencia se garantice sin tocar sesiones existentes, o una estrategia equivalente aislada que no colisione con datos de usuario. No “resolver” prohibiendo esos ids en Catalog.valid ni borrando/reutilizando filas: eso cambia el contrato aprobado y puede perder datos. Mantener el control positivo, el rechazo por CHECK y rollback de probes. Agregar regresión para cada uno de los cuatro ids fijos ya existentes y sesiones con contenido/contador/JSON, incluyendo un catálogo vacío, en arranque v2 y upgrade legacy. Inicializar repetidamente debe funcionar y preservar íntegros todos los datos, sin dejar filas de prueba ni consumir sequence.

### 4. Smoke de procesos: lo acreditado y lo pendiente

Se revisaron **/tmp/smoke_evidence_b2.txt**, **/tmp/run_smoke.sh**, **/tmp/norma_proc1.log**, **/tmp/norma_proc2.log**, /tmp/resp_default.json, /tmp/resp_mutated_init.json, /tmp/resp_empty_init.json y /tmp/reset_resp.json. Los logs prueban procesos Java25 **103393** y **103513** con cierre graceful de Tomcat/Hikari. El reporte muestra disponibilidad de la sesión mutada **8→7→7**, sesión vacía **8→0→0**, rechazo DEFAULT400 y reset **7→8** con pieza1 repuesta. Esta evidencia **sí supera** el smoke básico anterior y acredita los resultados observados de venta/vacío a través de dos procesos.

Se atendió la precisión de la tarea: además del reporte se buscó evidencia de metadata en logs/scripts/resultados de /tmp y se abrió **solo en lectura** la base fixture /tmp/norma_smoke.db. Estado final observado:

```text
smoke_empty_02   IDLE  pending=NULL temp_image=NULL snapshot=NULL next_number=9
smoke_mutated_01 IDLE  pending=NULL temp_image=NULL snapshot=NULL next_number=9
```

Los null finales en la sesión mutada serían normales después de reset; **no prueban pérdida de metadata**. El problema de evidencia es que run_smoke.sh **nunca prepara pending/temp_image/snapshot no nulos ni cambia next_number**, ni captura una comparación DB antes/después del reinicio. P1 sí verifica metadata no nula entre contextos, y C3 la preserva en rollback; ninguno equivale a ese subcaso del smoke de procesos solicitado expresamente en el segundo retrabajo.

Otros límites concretos de ese harness:

- Escribe sold y borra productos mediante Python **mientras el primer proceso sigue vivo**; la orden exigía preparar el fixture con el proceso parado. Mover esa preparación a después del primer cierre y antes del segundo arranque permite un snapshot consistente y respeta el contrato de una instancia/escritor activo.
- Usa `--norma.upload.dir` en vez de la propiedad efectiva **norma.uploads**; no acredita aislamiento de uploads mediante ese argumento. Repetir con la propiedad correcta y directorio temporal.
- El hash de smoke registrado es **c8c1c64c513ef56ebca4ef19e48a128196eade450db3a128d8e422d064bcbd3c**, anterior al clean/repackage del JAR final **f99b...**. Puede deberse al reempaquetado, no demuestra por sí mismo código distinto; no hay comparación conservada de clases/recursos entre esos dos artefactos. Repetir sobre el JAR final cuyo hash se entrega, o aportar comparación verificable de su contenido relevante.
- El script imprime “PASSED” sin aserciones de igualdad para varios conteos y hace `wait ... || true`, sin registrar exit codes. Los valores observados son correctos para los escenarios ejecutados, pero conviene convertir expectativas en aserciones y registrar HTTP/status/body, comando, hash y salidas de ambos procesos para entrega reproducible.

**Retrabajo de evidencia, sin funcionalidad nueva:** tras corregir C4 y empaquetar/exportar, smoke final con ruta/puerto temporales, hash de JAR fijo, preparación de sesión mutada/otra vacía con proceso detenido y contador>9, estado/borrador/foto/snapshot no nulos válidos en al menos una sesión, snapshot DB previo y posterior antes del reset, HTTP de ambas sesiones/DEFAULT y reset, limpieza metadata comprobada después de reset y contador conservado. Registrar comandos y exit codes reales; no ejecutar proveedores externos ni tocar BotEngine.

### 5. Matriz F1–F7 actualizada

| Hallazgo | Estado v1.2 | Evidencia y límite |
|---|---|---|
| F1 | **Contraejemplos cerrados; regresión C4 abierta** | C1/C2 rechazan y preservan esquema/datos/version; DDL/guardas más estrictos. Nuevo uso de ids fijos rompe arranque de estado válido |
| F2 | **Corregido en mecanismo** | Reconocimiento legacy exacto y rechazo de trigger/índice/view; tests de índice/view añadidos, faltan variantes de DDL y comparación de preservación completa anunciadas |
| F3 | **Corregido en mecanismo** | Sequence histórica conservada y segundo arranque comparado. Comentario anuncia sin productos/con DEFAULT, pero fixture adicional solo cubre id5 vivo tras borrar id1000; variantes prometidas no están implementadas |
| F4 | **Corregido en mecanismo** | typeof/long/rango y tests REAL1.5/texto/negativo/out-of-range. Persisten faltantes de assertions del valor original; null requiere considerar NOT NULL del legacy en vez de afirmar cobertura inexistente |
| F5 | **C3 cerrado; cobertura integral aún parcial** | Flag/cause inyectado y FK misma conexión demostrados; R6 mejora reset y metadata. Aserciones completas de restore/rollback y M3 aún no están todas en tests versionados |
| F6 | **PASS** | PRAGMA leídos, FK finally y renovación física con raw1 != raw2 y checks repetidos en P3 permanecen intactos |
| F7 | **Parcial** | D11 ya elimina garantías absolutas y se dispone de clean verify exitoso; P2 procesos mutado/vacío demostrado parcialmente, metadata/hashes/fixture parado aún pendientes |

### 6. Matriz R/P/M/G y no regresión

No se exige agregar otro framework o ampliar FSM. FAIL de evidencia indica subcaso aprobado sin demostrar, no afirma que el dato se pierda en producción.

| Criterio | Dictamen actual | Evidencia/punto exacto pendiente |
|---|---|---|
| R1 | PASS | Clones completos y PRICE/IMAGE/SOLD/CREATE aislados; B y DEFAULT conservados |
| R2 | PASS repositorio | ensure de sesión vacía/toda vendida, no reseed y reset explícito; smoke también acredita catálogo vacío tras proceso |
| R3 | PASS | Numeración monótona a través de venta/restore/reset y sesión nueva; sin cambios al algoritmo |
| R4 | PASS reglas principales; parcial igualdad íntegra | Agregó duplicado, negativo y no numérico SQL, targeting y agotamiento. sessBeforeErrors se captura pero no se compara; la lista tras errores compara solo parte de Product y agotamiento solo cantidad/contador. Exigir igualdad de todos los datos relevantes antes/después, no afirmar “full state” todavía |
| R5 | PASS | IDs inválidos/null en todos los métodos, seis guardas y traslado a DEFAULT con número libre999 y causa del trigger, seed completa sin cambios |
| R6 | PARCIAL | Fallos ensure/reset/restore reales; reset ahora compara número/título/precio/foto/sold y toda metadata, y reset exitoso limpia campos. restore aún compara número/título/precio, omite id/sessionId/foto/sold; usar igualdad de records/listas y Session para todos los fallos, desde transacción independiente |
| R7 | PASS | ensure y cinco altas concurrentes, unicidad/next14/B aislada y cierre executors |
| P1 | PASS comportamiento principal; C4 bloquea garantía universal | Metadata no nula y cambios persistidos entre contextos en archivo; no cubre colisión de ids de validación |
| P2 | PARCIAL / FAIL aceptación completa | Dos procesos y venta/vacío acreditados; metadata/contador/harness/hash final pendientes en §4; caso válido C4 impide reinicio |
| P3 | PASS | Valores PRAGMA y FK en nueva conexión física, sin reejecutar inicializador |
| M1 | PARCIAL | IDs vivos/campos/JSON/vacío normalizados comprobados; segundo arranque del fixture solo compara versión/cantidad DEFAULT, no contenido completo de usuarios |
| M2 | PASS contraejemplos principales; cobertura aislada parcial | Guardas minúsculas, WHEN0, seed alterada con trigger exacto y DDL inválido rechazados. Test sin CHECK carga títulos P1..P8 no canónicos, pese al comentario “semilla canónica”; DDL se comprueba primero y el probe de auditoría con seed exacta sí demuestra C2. Añadir variantes de un solo CHECK/default/FK/índice con causa y preservación para aislar cada rechazo |
| M3 | PASS rollback dirigido auditor; tests integrales parciales | C3 comprobado con snapshot lógico completo del auditor. Test versionado compara parte del catálogo/tabla y versión, no inventario/datos/JSON/sequence completos. Índice/view extra solo assertThrows; variantes state/id/tipos/PK/defaults incompatibles sin fixture completo. Agregar assertions sin cambiar arquitectura |
| G1 | PASS ejecución | clean verify BUILD SUCCESS y 17/17 Java25 en exportación idéntica, FlowTest intacto; no contar ApiTest del workspace |
| Alcance/workspace | PASS | Commit de cuatro archivos autorizados; solo documento de auditoría modificado por auditor, archivos ajenos preservados |

Las pendientes de cobertura anteriores ya estaban expresamente en v1.1 y la orden de retrabajo. No se exige multiplicar métodos JUnit: pueden agruparse con assertions de comportamiento y causa, sin confundir cantidad17 con cumplimiento exhaustivo. **Aunque se aceptaran las limitaciones de evidencia, C4 por sí solo exige FAIL.**

### 7. Probes ejecutados, trazabilidad y conservación

Lagunas concretas que justificaron el harness: verificar cierre C1/C2 con preservación, comprobar punto real C3 y confirmar colisión de ids nuevos de validación. Ningún build ni Maven ejecutado por el auditor. Fixtures y harness nuevos únicamente en **/tmp/norma-issue2-finalaudit-y3jph9te**; datos del usuario sin cambios. La base smoke solo se abrió con URI SQLite mode=ro para resolver la precisión sobre metadata.

```sh
/tmp/norma-jdk/bin/java --enable-native-access=ALL-UNNAMED --class-path '/tmp/norma-issue2-finalaudit-y3jph9te/classes:/tmp/norma-issue2-finalaudit-y3jph9te/lib/*:/tmp/norma-export/target/test-classes' /tmp/norma-issue2-finalaudit-y3jph9te/FinalAuditProbe.java
```

Exit0: el harness reporta contraejemplos y no constituye una suite aceptada. Log completo: /tmp/norma-issue2-finalaudit-y3jph9te/probe.log. Resultados relevantes conservados aquí:

```text
C1 REJECT trigger alterado; logical_preserved=true
C2 REJECT DDL sin CHECK; logical_preserved=true
C4 public_valid_id=valid_sess_check_1
C4 legitimate_session_restart REJECT SQLITE_CONSTRAINT_PRIMARYKEY / UNIQUE constraint failed: sessions.id; logical_preserved=true
C3 cause=Injected test failure on: PRAGMA quick_check; logical_preserved=true
C3 isFailReached=true
C3 getFkOnFailingConnection=1
C3 recovery_version=2
```

Se compararon fingerprints SHA-256 de BotEngine.java, IntentFallback.java, ApiTest.java y AGENTS.md antes/después: idénticos. Se preservó íntegro el prefijo del informe anterior y solo se añadió este addendum. El único archivo modificado/versionado por esta auditoría es docs/orchestration/issue-2-audit.md; no hubo push/cierre ni modificación de implementación.

### 8. Pedido restante para Orca y Antigravity

```text
[@Agente Destino]: Antigravity
Objetivo de la tarea: Resolver la regresión de reinicio C4 y completar las assertions/evidencia pendientes de v1.2 sobre 123d23a, sin cambiar diseño ni contratos.
Contexto / Inputs: Diseño v1.0 aprobado, audit completo v1.0/v1.1/v1.2 y orden previa; archivos permitidos de §7 y trabajo ajeno intacto. Primero eliminar colisiones de probes con sesiones válidas y añadir regresión para los cuatro ids y legacy/v2, conservando controles y rollback; gate Orca. Luego completar igualdad íntegra R4/R6/M1/M3, variantes aisladas M2/F2-F4 ya solicitadas y smoke final del §4 con metadata/contador no triviales, fixture preparado con proceso parado y hash del JAR entregado; gate Orca. No tocar FSM/API/POM ni hacer push/cierre.
Criterio de Aceptación (Definition of Done): Arranque repetido sobre cualquier sesión válida de esos fixtures funciona sin tocar su contenido/contador/JSON/sequence; C1/C2 siguen rechazando sin reparación y C3 falla en la inyección correcta con FK restaurada. Exportación idéntica al nuevo commit, clean verify completo Java25 sin omisiones, smoke final de dos procesos con snapshots before/after completos y HTTP/status/body/exit codes, hashes y logs concordantes. Solicitar nueva auditoría Codex; no declarar #2 completa por total17/17.
```

**Conclusión final de esta auditoría v1.2: FAIL sobre 123d23a.** Se reconocen reparaciones C1–C3 y build reproducible; queda un defecto concreto de reinicio (C4) y brechas acotadas de evidencia. Issue #2 permanece abierta hasta corrección y aceptación de Orca/Codex.


---

## Addendum de reauditoría final v1.3 — 2026-11-12

**DICTAMEN: PASS para issue #2 sobre 09d12467b8917f474eb3fd6579afaf90aefc4769**, incorporando la reparación C4 de **f0ba2d05a2dd533c6a9339c368e70717a60c3a75**. Referencia: diseño Codex v1.0 aprobado por Orca, sin cambios de contratos ni alcance. Tarea task_6c15ea62c5d7, dispatch ctx_17dca5c010eb. Este dictamen corresponde al estado actual y sustituye los FAIL históricos para los hallazgos corregidos, conservando sus evidencias y límites íntegros arriba.

La revisión conjunta de implementación, regresiones versionadas, exportación limpia y smoke real proporciona evidencia suficiente de los criterios aprobados de persistencia, aislamiento, seed, numeración, restauración y migración de #2. No se certifican por extensión FSM/deshacer de #3, medios/voz, frontend/SSE ni despliegue de issues posteriores. El auditor no cierra la issue ni hace push; la aceptación administrativa final sigue en Orca.

### 1. Alcance y trazabilidad comprobados

Se revisaron AGENTS.md, diseño/aprobación, historial completo de auditoría v1.0–v1.2, orden de tercer retrabajo, ambos commits, código/tests afectados, contrato Catalog y evidencia consolidada. `git diff 123d23a 09d1246 --check`: exit0. Los cambios de estos dos commits están limitados a CatalogDatabaseInitializer.java, CatalogMigrationTest.java y CatalogRepositoryTest.java, archivos permitidos por §7. No se alteraron POM, API, parser, BotEngine, frontend, seed ni contracts REST.

Se compararon **todos los archivos versionados** de 09d1246 con /tmp/norma-export usando git ls-tree/git show: **cero diferencias**. Las clases Catalog y CatalogDatabaseInitializer dentro de BOOT-INF/classes del JAR coinciden con las de target/classes de la exportación. La exportación y su suite no incluyen IntentFallback.java ni ApiTest.java locales sin seguimiento.

**SHA-256 del JAR ejecutado y entregado:**

```text
4bd8ad9558ce997b7759f30259ce6c4710aaa1d757c16a25d07e59412728b9c7
```

Calculado directamente sobre /tmp/norma-export/target/taller-1.0.0.jar; coincide con /tmp/norma_final_delivery_v1_3_report.txt y /tmp/smoke_evidence_final.txt. Queda resuelta la discrepancia entre JAR de smoke y JAR final que señalaba v1.2.

### 2. Build y tests observados, sin repetir ejecución

Fuentes: **/tmp/norma-verify-clean-export-final.log**, **/tmp/norma_final_delivery_v1_3_report.txt** y **/tmp/norma-export/target/surefire-reports/TEST-*.xml**, con sus TXT. El log de exportación incluye clean, compilación release25, pruebas, JAR/repackage y **BUILD SUCCESS**, finalizado **2026-11-12T23:15:02-03:00**. XML: java.version=**25.0.5.0.3**, basedir=**/tmp/norma-export**.

| Suite versionada | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| CatalogRepositoryTest | 7 | 0 | 0 | 0 |
| CatalogPersistenceTest | 3 | 0 | 0 | 0 |
| CatalogMigrationTest | 3 | 0 | 0 | 0 |
| FlowTest | 4 | 0 | 0 | 0 |
| Total | **17** | **0** | **0** | **0** |

Comando de verificación de la entrega: Maven clean/verify con JDK /tmp/norma-jdk en la exportación; el resultado anterior se observa en el log y artefactos. No se confunde con target del workspace, ApiTest sin seguimiento ni logs antiguos. En esta auditoría **no se ejecutaron Maven, tests ni nuevos probes**: no apareció una nueva brecha funcional que justificase repetirlos. El total17 se usa como recuento de métodos ejecutados, no como supuesto de exhaustividad de combinaciones.

### 3. Cierre de F1–F7 y C1–C4

| Hallazgo | Estado final | Código y evidencia de cierre |
|---|---|---|
| F1 / C1 / C2 | PASS | DDL canónico completo, defaults/tipos/nullability, FK RESTRICT, UNIQUE no parcial, seis definiciones de trigger sensibles a literales y probes aislados con padre/control positivo y causa CHECK. Los tests de guardas homónimas/minúsculas, esquema inválido y semilla alterada siguen en M2; la corrección nueva no modifica esos controles |
| F2 | PASS | Reconocimiento restrictivo de legacy y rechazo de objetos/DDL desconocidos previo a reconstrucción; casos trigger, índice y view en M3. No hay drop/reseed para “reparar” datos incompatibles |
| F3 | PASS | Se preserva max(sequence histórica previa, sequence generada) en la transacción; fixture id1000 borrado/id5 vivo y segundo arranque comprueban el high-water, junto a snapshots completos de M1/M3 |
| F4 | PASS | typeof entero y rango mediante long antes de normalizar next_number; productos con tipos/rangos validados; REAL1.5/texto/negativo/out-of-range rechazados en M3. Un entero positivo bajo válido se normaliza según diseño |
| F5 / C3 | PASS | Wrapper propaga la causa SQL, flag y mensaje exigen alcanzar quick_check; FK capturada en la conexión fallida antes de close; snapshot lógico completo igual tras rollback y recuperación posterior. R6 ejercita fallos ensure/restore/reset y R7 altas concurrentes |
| F6 | PASS | Lectura efectiva de PRAGMA, FK restablecida en finally y prueba de renovación física con conexión raw distinta y checks/FK repetidos en P3 |
| F7 | PASS funcional | Test de contexto nombrado honestamente, P2 externo con dos procesos y hash final concordante, fixture parado con metadata/contador no triviales, documentación sin garantía física universal; límite del exit code anotado en §6 |
| C4 | PASS | Se eliminan los cuatro INSERT de ids públicos fijos y se generan ids efímeros UUID dentro de savepoint, con rollback/release garantizados. M2 incluye sesiones públicas preexistentes tanto en v2 como legacy y compara snapshots exactos después de reinicializar |

La regresión C4 versionada contiene valid_sess_check_1/2 y valid_sess_prod_1/2; no contiene exactamente los cuatro ids que figuraban en los probes viejos (check_1/2/3/4). La inspección del código confirma que **todos** los probes anteriores check_1/2/3/4 fueron reemplazados por ids aleatorios, por lo que no queda la colisión determinista de check_3/4. No se ha cambiado Catalog.valid ni reservado nombres de clientes. La prueba confirma ausencia de efectos persistentes en tablas, datos, sequence y versión; no se afirma probabilidad matemática cero de colisión de UUID.

### 4. Matriz de criterios R/P/M/G del diseño aprobado

El dictamen por criterio combina pruebas conductuales reales y revisión del contrato/SQL; no significa que cada combinación posible tenga un método JUnit propio. Se priorizan los escenarios de aceptación originales y regresiones que reprodujeron defectos, sin ampliar la fase a requisitos nuevos.

| Criterio | Dictamen | Evidencia observada |
|---|---|---|
| R1 — Seed/clonado/aislamiento | PASS | Ocho clones con números/campos correctos e ids propios, next9; PRICE/IMAGE/SOLD/CREATE en A conservan B y DEFAULT |
| R2 — Inicialización/vacío | PASS | La fila sessions es marcador; ensure repetido, restore([]) y todas vendidas no resembran; reset explícito repone ocho. Smoke devuelve [] tras segundo proceso |
| R3 — Numeración estable | PASS | Alta9/10, venta, restore y reset no rebajan next; siguiente11; otra sesión empieza9. Reinicio de proceso conserva next15 incluso tras reset |
| R4 — Validación/targeting | PASS | Cambios positivos limitados al campo objetivo, destinos inexistentes/UNKNOWN rechazados; precios inválidos, ruta/título vacíos, duplicado/número inválido, REAL/no-numérico y agotamiento. 09d1246 añade igualdad de todos los campos Product y Session tras errores de apply |
| R5 — Límites/DEFAULT | PASS | Nueve entradas públicas validan sesión incluida null/DEFAULT; seis guardas SQL; traslado hacia DEFAULT con número libre999 falla por causa de guarda; comparación final de seed completa |
| R6 — Restore/reset/atomicidad | PASS | Lote cruzado/duplicado se valida antes de mutar; fallo real durante ensure deja cero fila/clones; restore fallido conserva Product completo y Session; reset fallido conserva contenido y metadata; reset exitoso restaura seed y limpia estado/pending/temp/snapshot |
| R7 — Concurrencia en proceso | PASS | ensure concurrente y cinco CREATE: números únicos9..13, next14, sesión B intacta, timeouts y cierre de executors |
| P1 — Persistencia de archivo | PASS | Pools/contextos cerrados y reabiertos conservan precio/foto/sold/alta, contador, borrador/foto/snapshot y sesión vacía |
| P2 — Reinicio real | PASS | JAR final exacto, PIDs112232/112346, mismo archivo temporal, fixture con primer proceso detenido, venta/vacío y metadata no nula/next15 preservados; GET/DEFAULT400/POST reset200 y limpieza sin rebajar contador |
| P3 — WAL y conexiones | PASS | Archivo WAL, foreign_keys1, busy_timeout5000, synchronous NORMAL; nueva conexión física raw distinta y rechazo de huérfano |
| M1 — Migración normal/idempotencia | PASS | Legacy poblado/vacío, vendidos y JSON; ids/campos preservados, contador normalizado, DEFAULT añadido, foreign_key_check vacío y quick_check ok. 09d1246 compara snapshot completo tras segundo arranque |
| M2 — DEFAULT/compatibilidad | PASS | Legacy DEFAULT válido conserva ids, parcial se rechaza, v2 con seed alterada/guardas ausentes u homónimas/DDL incompatible se rechaza; C4 legacy y v2 reinicializan sin alterar snapshot |
| M3 — Fallo cerrado/rollback | PASS | Huérfanos, versión futura, trigger/índice/view extra y tipos/rangos inválidos rechazados. Inyección real post-copia en quick_check con causa y punto alcanzado, FK misma conexión, snapshot completo intacto y arranque de recuperación exitoso |
| G1 — Regresión limpia | PASS | FlowTest intacto4 + nuevas suites13 =17/0/0/0, JDK25, clean verify y JAR de exportación idéntica al commit; no se usa ApiTest local como evidencia versionada |

Las variantes ilustrativas adicionales mencionadas en los addenda (cada CHECK/default alterado de forma aislada, todo valor inválido combinatorio, todos los estados de metadata en cada fixture) no están todas representadas individualmente en las suites. No se declara cobertura exhaustiva de esas variaciones. La revisión del DDL exacto y las validaciones deterministas, junto con las regresiones y snapshots añadidos, permite cerrar los defectos y criterios funcionales de #2; no se detectó una incompatibilidad concreta pendiente que justifique otro ciclo de construcción. Los límites físicos permanecen fuera de esas garantías.

### 5. Smoke final: evidencia y comparación antes/después

Fuentes contrastadas: **/tmp/run_smoke_v2.sh**, **/tmp/smoke_evidence_final.txt**, **/tmp/norma_proc1_final.log**, **/tmp/norma_proc2_final.log**, **/tmp/resp_default.json**, **/tmp/resp_mutated_init.json**, **/tmp/resp_empty_init.json**, **/tmp/resp_p2_mutated.json**, **/tmp/resp_p2_empty.json** y **/tmp/resp_reset.json**. El consolidado v1_3 copia el reporte. No usar smoke_evidence_b2.txt ni reportes con hash c8c1/f99b para aceptar este commit.

Ambos procesos usan el JAR SHA4bd8... y comandos con argumentos efectivos:

```sh
/tmp/norma-jdk/bin/java -jar /tmp/norma-export/target/taller-1.0.0.jar --server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/norma_smoke_final.db --norma.uploads=/tmp/norma_smoke_final_uploads
```

Primer proceso PID112232 crea ambas sesiones con ocho productos; DEFAULT responde400. Sus logs muestran cierre graceful de Tomcat y Hikari antes de preparar fixture con Python/SQLite. Se marca vendida pieza1, se vacía la segunda sesión y se guarda:

```text
('smoke_mutated_01', 'AWAITING_DETAILS',
 '{"draft_title":"Pieza Borrador","draft_price":12000}',
 '/uploads/temp_smoke.png', '[{"piece_number":1,"sold":1}]', 15)
```

Segundo proceso PID112346 devuelve200/siete piezas disponibles sin #1, la otra sesión devuelve200/[] y el tuple completo anterior se registra igual antes de reset; las assertions del script exigen estado, campos no nulos y next15. POST reset=200 devuelve ocho piezas, repone #1 y registra:

```text
('smoke_mutated_01', 'IDLE', None, None, None, 15)
```

El script verifica los null y preservación de next15. Logs de ambos procesos contienen “Graceful shutdown complete” y “HikariPool-1 - Shutdown completed”. Las respuestas JSON guardadas coinciden con las cantidades y exclusión de pieza vendida. El JSON de borrador/snapshot del smoke es opaco para el repositorio; se verifica su persistencia exacta, no su validez para el consumo FSM futuro de #3.

### 6. Límites de evidencia y observación no bloqueante

**El campo “Process exited with code0” del smoke no acredita el código del proceso Java:** run_smoke_v2.sh hace `wait "$PID" || true` y luego lee `$?`, por lo que registra el resultado de esa lista y puede ocultar SIGTERM143. Esta auditoría no repite esa afirmación como hecho observado. Lo acreditado de la parada ordenada son los logs de cierre de ambos componentes y que el primer proceso terminó antes de preparar fixture/arrancar el segundo. La imprecisión del campo del reporte no invalida los resultados HTTP/DB y el cierre real corroborados, ni revela un fallo funcional del catálogo; se registra como límite de instrumentación, no como motivo para reconstruir la aplicación. Si se reutiliza el harness, capturar el status original de wait antes de normalizarlo y actualizar su etiqueta, sin atribuir al Java un exit0 no observado.

Los conteos HTTP se registran y se contrastaron manualmente contra respuestas y logs; el harness no convierte todos los conteos en assertions automáticas. No se afirma CI para ese script temporal. Los logs/reportes en /tmp pueden caducar; se conservaron en este addendum hash, PIDs, comandos, recuentos y tuples relevantes. El código/tests versionados son la evidencia reproducible persistente.

No se ha migrado una base de producción desconocida ni hecho prueba de corte eléctrico. La garantía examinada es transaccional/lógica bajo SQLite WAL y una instancia por archivo, con backup previo y rechazo conservador de esquemas no reconocidos. synchronous=NORMAL no acredita supervivencia de todos los commits frente a pérdida física de energía; ese límite ya está documentado y no es un incumplimiento funcional nuevo. No se declara autenticación administrativa, escalado multiproceso ni latencia universal.

### 7. Conservación y conclusión

El auditor solo realizó lectura/revisión, comparación de archivos/hashes y escritura de este addendum; **no ejecutó builds/tests/probes ni abrió bases de usuario** en esta reauditoría. Se guardaron fingerprints iniciales de BotEngine.java, IntentFallback.java, ApiTest.java y AGENTS.md y se compararon al finalizar: iguales. Se conserva el contenido previo completo del informe. Único archivo del repositorio modificado/versionado por el auditor: docs/orchestration/issue-2-audit.md.

**PASS de conformidad técnica de #2 sobre 09d12467b8917f474eb3fd6579afaf90aefc4769.** F1–F7 y C1–C4 quedan cerrados para el alcance funcional aprobado con los límites anteriores expresos. No se solicita más implementación para #2 desde esta auditoría. Orca conserva la decisión final de aceptación/cierre y la coordinación de la siguiente fase; no hubo push ni cierre de issue por Codex.
