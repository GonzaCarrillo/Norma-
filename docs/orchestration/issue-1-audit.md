# Auditoría de issue #1 — PASS en reauditoría v1.1

Dictamen vigente: **PASS**, según addendum v1.1 al final. Se conserva íntegro el dictamen v1.0 como historial de la entrega anterior.

Versión de auditoría: **1.0**, 2026-11-12. Auditor: Codex, Software Architect.
Tarea `task_dc8a6ed98974`; dispatch `ctx_14deb0cce2e7`.
Commit auditado: **63b9a8a67ce9143bcc072387a605ae39a3fb7349**.
Referencia: diseño `issue-1-design.md` **v1.0** y aprobación explícita de Orca en `issue-1-approval.md`.

## Dictamen

**FAIL: la entrega requiere correcciones documentales y precisión de la evidencia antes de aprobar #1.** No se identificó en el diff una modificación funcional de la base contraria al diseño. No se ejecutaron builds, pruebas ni servidores durante esta auditoría. El fallo de aceptación no significa que se haya observado una compilación fallida.

El commit toca exactamente los cuatro archivos aprobados: `pom.xml`, `src/main/resources/application.properties`, `README.md`, `docs/decisiones.md`. Mantiene versiones, coordenadas y dependencias; explicita release 25 y sólo comenta/reordena la configuración. No autoriza incorporar cambios previos ni resolver issues posteriores.

## Conformidad por criterio

| Criterio | Evaluación | Evidencia / límite |
| --- | --- | --- |
| Maven / Java 25 | Conforme estáticamente y respaldado por artefactos del árbol local | POM con `java.version=25`, release 25; POM efectivo confirma release 25, compiler 3.14.1 y surefire 3.5.6. No se volvió a ejecutar Maven. |
| Spring Boot 3.4+ | Conforme | Parent conservado 3.5.16, plugin efectivo y manifiesto JAR coinciden. |
| Compilación bytecode 69 | Evidencia local observada | Lectura binaria de `target/classes/com/norma/taller/TallerApplication.class`: major 69. No acredita por sí sola todas las clases ni el checkout aislado. |
| Verify sin omisiones | Evidencia parcial suficiente para confirmar el conteo reportado, no la ejecución íntegra del lifecycle | XML Surefire: ApiTest 2 y FlowTest 4; total 6, cero fallos, errores y omitidas. No hay log de `mvn verify` inspeccionado con `BUILD SUCCESS`. |
| JAR ejecutable / monolito | Estructura conforme | Manifiesto: Boot JarLauncher, Start-Class `com.norma.taller.TallerApplication`, Build-Jdk-Spec 25, Boot 3.5.16; entradas BOOT-INF/classes y BOOT-INF/lib presentes. No se ejecutó JAR en auditoría. |
| Sin autenticación tradicional | Conforme al cambio | No se agrega Security/login; README y D5 explican sandbox sin autorización administrativa de producción. No constituye auditoría de seguridad integral. |
| Configuración externa | Código/configuración conservados; documentación FAIL | Placeholders y defaults intactos. Orden general de precedencia incorrecto en README; ver F1. |
| README reproducible | FAIL | Ruta local presentada sin aclaración, omisión de `.env` y evidencia sin delimitar árbol local; F3/F4/F6. |
| Decisiones y alcance documentados | FAIL | D6 garantiza ausencia de corrupción sin sustento y asigna Docker a #7; falta explicitar brecha de modelos/voz; F2/F5/F7. |
| Smoke entorno / archivo / CLI / puerto inválido | Reportado por builder, no corroborado de forma independiente aquí | El encargo informa éxito y exit 1 para puerto inválido; no se localizaron logs en `target/` mediante búsqueda de `*.log`. No confundir la afirmación reportada con una ejecución observada por Codex. |
| Preservación y alcance | Diff del commit conforme; limitación de atribución | `git status` conserva BotEngine modificado e IntentFallback/ApiTest sin seguimiento. No se dispone de hashes de contenido previos para demostrar identidad completa del trabajo ajeno. |

## Hallazgos priorizados y correcciones exigidas

Prioridad P2: afecta la aceptación/documentación técnica de #1; P3: precisión menor de trazabilidad. No hay P0/P1 observado ni evidencia de corrupción real.

### F1 — P2: precedencia Spring incorrecta y aliases confundidos

Ubicación: `README.md:39–47`. El orden «CLI, archivo externo, variables de entorno, empaquetados» no es la precedencia general de Spring. Para las fuentes utilizadas, sobre **la misma propiedad efectiva**, el orden es CLI > variables de entorno > archivo externo > archivo empaquetado (sin pretender enumerar todas las fuentes posibles, como system properties).

Corrección exacta: reemplazar esa lista y explicar por separado:

- `PORT` es un alias consumido por `server.port=${PORT:8080}`; no es el nombre nativo de entorno de `server.port`.
- Un archivo externo que define `server.port=18082` reemplaza la definición empaquetada que contenía ese placeholder; por ello `PORT=18081` no fuerza 18081 en ese escenario.
- `SERVER_PORT=18084` sí corresponde a `server.port` y prevalece sobre `server.port=18082` del archivo. `--server.port=18083` prevalece sobre ambos.
- `NORMA_DB` también es un alias que compone la URL empaquetada; la variable nativa `SPRING_DATASOURCE_URL` contiene la URL completa y prevalece sobre el archivo. No afirmar que todos los nombres de la tabla tienen exactamente la misma relación: `NORMA_UPLOADS` y `GROQ_API_KEY` también corresponden directamente a las propiedades respectivas mediante la resolución de entorno de Spring.

Fundamento comprobado en la [referencia oficial de configuración externa de Spring Boot](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html): entorno tiene precedencia sobre config data y CLI sobre ambas; additional-location agrega archivos y no cambia su categoría de fuente. No se detectó necesidad de cambiar implementación.

### F2 — P2: garantía imposible de corrupción cero

Ubicación: `docs/decisiones.md:43`. «Cero riesgo de corrupción de datos» no se deriva de una conexión Hikari, WAL ni de no migrar; contradice D6/D7 del diseño. Tampoco los smoke tests prueban esa garantía.

Corrección exacta propuesta: «Se conserva la configuración y el esquema existentes sin introducir migraciones en #1; no se eliminan bases para lograr un arranque limpio y se verifica con almacenamiento temporal aislado. La integridad, concurrencia y recuperación se auditarán en #2; no se garantiza ausencia de corrupción».

### F3 — P2: ruta temporal presentada como configuración general

Ubicación: `README.md:9–13`. `/tmp/norma-jdk` existe en este entorno pero no en una instalación nueva. Contradice D1, que exige distinguir instalación equivalente y ruta local.

Corrección exacta: indicar «JAVA_HOME debe apuntar a tu instalación de JDK 25; `/tmp/norma-jdk` es sólo la ruta disponible en el entorno de trabajo actual». Mantener el ejemplo con esa aclaración o usar una ruta sustituible explícita. Añadir `java -version` y `mvn -version` y exigir que ambos muestren Java 25, porque Maven puede usar otro JDK.

### F4 — P2: `.env` no explicado en instrucciones de ejecución

Ubicación: sección Configuración externa de README. D4 del ADR dice «ni carga mágica de dotenv», pero eso no informa claramente al operador del README cómo ejecutar sin contenedor.

Corrección exacta: «La ejecución directa con Java no carga automáticamente archivos `.env`; exportá las variables en el entorno del proceso o usá un archivo `.properties` externo. No versionar secretos». No agregar dependencia dotenv ni cambiar `.gitignore`.

### F5 — P3: issue #7 mal clasificada

Ubicación: `docs/decisiones.md:60`. #7 es «Simulador de Norma sin credenciales», confirmado con `gh issue view 7`; coincide con `docs/backlog.json`. Docker/despliegue corresponde a #8.

Corrección exacta: agregar entrada de simulador #7, separar Docker/verificación/despliegue reproducible como #8 y conservar ambos como pendientes de auditoría. No iniciar su diseño o implementación.

### F6 — P2: evidencia del árbol local no atribuible a checkout reproducible del commit

Ubicación: `README.md:58` y entrega de verificación. Surefire confirma seis tests sobre JDK 25, pero dos pertenecen a `ApiTest.java` **sin seguimiento**. El JAR incluye `BOOT-INF/classes/com/norma/taller/IntentFallback.class`, también procedente de archivo sin seguimiento; el BotEngine del árbol depende de ese componente, mientras que `git show 63b9a8a:src/main/java/com/norma/taller/BotEngine.java` no lo hace. No es lícito afirmar que el commit contiene seis tests o que ese JAR fue construido sólo con sus archivos versionados.

Corrección acotada: calificar en README/ADR y reporte que la verificación observada corresponde al árbol de trabajo con cambios previos no auditados; indicar expresamente los tres archivos y el reparto 4+2. Mantener las instrucciones generales de build, sin prometer que un checkout de ese hash reproduce seis tests. Registrar comandos, salidas/logs disponibles y procedencia para los smoke tests reportados; si no se conservaron, declarar esa limitación.

Para aceptación reproducible del **commit aislado**, Orca debe disponer una comprobación separada y no destructiva de ese hash, sin mezclar esos archivos, con conteo real de sus pruebas; no se ejecuta ni se autoriza por este informe como cambio de código. Si decide aceptar sólo evidencia del árbol actual, registrar expresamente ese alcance y dejar pendiente la reproducibilidad del checkout. No exigir a Antigravity que agregue ApiTest/IntentFallback al commit de #1 ni que revierta BotEngine.

### F7 — P3: brecha de modelos y voz no trasladada al registro de decisiones

Ubicación: README/ADR, alcance de configuración. El diseño sección 4 exige documentar que `groq.stt.model`, `groq.llm.model`, `norma.tts.voice`, `norma.tts.rate` de la especificación no están conectadas a consumidores configurables. La tabla actual no afirma falsamente soportarlas, pero omite la brecha que se pidió registrar.

Corrección exacta: añadir al ADR de configuración que esos parámetros no son parte del contrato configurable actual, que modelos/voz están fijados en consumidores y que se revisarán en #5. No agregar propiedades inertes ni modificar Java.

## Evidencia realmente inspeccionada y limitaciones

Comandos de lectura ejecutados con éxito: `git show --stat 63b9a8a`, `git show 63b9a8a --`, `git status --short`, `git diff -- src/main/java/com/norma/taller/BotEngine.java`, lecturas de instrucciones/diseño/aprobación/despacho/backlog y archivos modificados. `git diff 63b9a8a^ 63b9a8a --check`: exit 0 sin errores de whitespace. `gh issue view 7 --repo GonzaCarrillo/Norma- --json number,title`: exit 0, confirma título.

Lectura estructurada con Python de XML Surefire: FlowTest 4 tests, ApiTest 2; cada suite con failures=0, errors=0, skipped=0, flakes=0. Ambas declaran java.home `/tmp/norma-jdk`, java.version `25.0.5.0.3`. No se publicaron las demás propiedades del proceso para evitar exponer configuración sensible. Existen dumps anteriores en el directorio; su mera presencia no invalida los XML exitosos ni prueba éxito de la última invocación completa de Maven.

Lectura XML del POM efectivo: release 25; plugins Boot 3.5.16, compiler 3.14.1, Surefire 3.5.6. Lectura binaria de clase: major 69. Lectura ZIP de JAR: manifiesto y estructura descritos arriba, además de IntentFallback. Son artefactos ignorados y mutables; no tienen asociación criptográfica a un checkout limpio ni logs completos de sus comandos generadores inspeccionados en esta tarea.

El builder reportó compilación, verify, smoke de entorno/archivo/CLI y rechazo de puerto inválido exit 1. Este reporte se conserva como evidencia recibida, con las corroboraciones parciales indicadas; Codex no observó aquí sus solicitudes HTTP, estado de rutas de almacenamiento ni procesos de arranque. No se afirma fallo de esas verificaciones ni se inventan salidas.

## Lista acotada de retrabajo y condición de reauditoría

Antigravity debe corregir **únicamente README.md y docs/decisiones.md** para F1–F7, dentro de la lista ya aprobada; POM y propiedades no necesitan cambios por estos hallazgos. Orca debe proporcionar o delimitar la evidencia de F6 y decidir la comprobación de reproducibilidad sin alterar trabajo ajeno. No repetir toda la suite por cambios exclusivamente documentales: verificar diff, enlaces, consistencia con propiedades/diseño y resultados de la eventual comprobación adicional justificada por F6.

Volver a Codex con hash de corrección, diff acotado y respuesta por hallazgo. Mantener #1 abierta hasta que Orca valide la entrega y la revisión de conformidad resulte PASS. Este informe es el único archivo creado por la auditoría; no se corrigieron documentos del builder ni código, no se hicieron commits, push ni cierre de issues.

## Addendum v1.1 — Reauditoría: PASS

Fecha: 2026-11-12. Auditor: Codex. Tarea `task_e5f9d28602ba`, dispatch `ctx_bded10649a06`.
Corrección auditada: **538ff07747569609b8aa7c76287c80d26335f540**, padre **63b9a8a67ce9143bcc072387a605ae39a3fb7349**. Referencia de aprobación: diseño v1.0 y `issue-1-approval.md`; autorización adicional de exportación aislada en `issue-1-correction-task.md`.

**PASS para el alcance de #1.** Las correcciones y la evidencia adicional resuelven los bloqueos de v1.0. No se extiende este dictamen a las funcionalidades de #2–#8 ni al trabajo local no auditado. No se ejecutó Maven ni se repitieron pruebas unitarias/integración durante la reauditoría.

### Resolución de F1–F7

| Hallazgo | Resultado | Corrección observada |
| --- | --- | --- |
| F1, precedencia | Resuelto | README ordena CLI > entorno > archivo externo > empaquetado para las fuentes explicadas; distingue `PORT`/`NORMA_DB` como aliases de placeholders y `SERVER_PORT`/`SPRING_DATASOURCE_URL` como propiedades nativas. Explica la resolución directa de uploads/clave. Es una selección de fuentes de Spring, no una enumeración exhaustiva. |
| F2, corrupción cero | Resuelto | D6 elimina la garantía, conserva esquema/configuración y remite integridad, concurrencia y recuperación a #2. |
| F3, JDK local | Resuelto | README identifica `/tmp/norma-jdk` como ruta local reemplazable y exige comprobar `java -version` y `mvn -version`. |
| F4, `.env` | Resuelto | README indica explícitamente que Java no lo carga y documenta entorno o `.properties` externo. |
| F5, issue #7 | Resuelto | ADR/README separan simulador #7 de Docker/verificación/despliegue #8, pendientes de sus fases. |
| F6, reproducibilidad | Resuelto | README/ADR distinguen seis tests locales (4+2 sin seguimiento) de cuatro versionados en exportación limpia. La exportación y el JAR se inspeccionaron y las comprobaciones HTTP faltantes se observaron directamente, según detalle siguiente. BotEngine local sigue modificado y queda fuera del dictamen, junto con IntentFallback y ApiTest sin seguimiento. |
| F7, modelos/voz | Resuelto | D4 registra las cuatro propiedades de la especificación que no tienen consumidores configurables y difiere su diseño a #5; no se introducen propiedades inertes ni Java nuevo. |

`git show --stat` y diff completo confirman que la corrección toca **sólo README.md y docs/decisiones.md**; POM, propiedades y Java no cambian respecto de 63b9a8a. `git diff 538ff077^ 538ff077 --check`: exit 0. Mensaje de commit referencia `(#1)` sin instrucción de cierre. `gh issue view 1 --repo GonzaCarrillo/Norma- --json state,closedAt`: exit 0, **OPEN**, closedAt null. No se hizo push ni cierre desde esta auditoría.

### Evidencia aislada de Antigravity corroborada

Directorio conservado: `/tmp/norma-verify-63b9a8a.1NRfTx`.

- Comparación de **los 37 archivos versionados** listados por `git ls-tree -r --name-only 63b9a8a` con sus contenidos mediante `git show`: **cero diferencias** frente a la exportación. No existen allí los fuentes ApiTest ni IntentFallback. Esto corrobora el contenido exportado; la invocación histórica de `git archive` fue reportada por Antigravity, no observada en directo por Codex.
- `mvn-verify.log`: compilación de seis fuentes con **release 25**, un fuente de pruebas, ejecución de FlowTest, **Tests run: 4, Failures: 0, Errors: 0, Skipped: 0**, empaquetado Boot y **BUILD SUCCESS**. Final registrado `2026-11-12T19:45:49-03:00`. No se omiten tests mediante flags en la salida observada.
- XML `target/surefire-reports/TEST-com.norma.taller.FlowTest.xml`: cuatro tests, cero failures/errors/skipped/flakes; Java **25.0.5.0.3**, java.home `/tmp/norma-jdk`. Coincide con el log.
- JAR exportado: manifiesto Boot **3.5.16**, Build-Jdk-Spec **25**, JarLauncher y Start-Class `com.norma.taller.TallerApplication`; las **13 clases de aplicación** dentro de BOOT-INF/classes tienen major **69**. No incluye IntentFallback. SHA-256 observado: `2d98b3f61986a3a5874c7d8e23ac71657478c5def2073b9192311f0b60be1c56`.
- Logs `smoke-env.log`, `smoke-ext.log`, `smoke-cli.log`: arranques en 18081, 18082 y 18083 respectivamente, inicialización de DispatcherServlet y apagado ordenado. `smoke-invalid.log`: fallo de binding de server.port por `invalid`. Archivo `smoke2/norma.properties`: puerto 18082, SQLite/uploads en rutas aisladas, clave vacía. Existen las bases temporales generadas.
- Advertencias reales en verify: Unsafe de Guava/Maven, acceso nativo SQLite, autoattach Mockito/ByteBuddy y CDS. El build termina exitosamente con ellas; no se desactivan pruebas ni se amplía #1 para corregir dependencias. No son evidencia de corrupción ni garantías de compatibilidad con JDK futuros.

El cambio 538ff077 es exclusivamente documental: el build aislado del padre 63b9a8a valida la misma implementación/POM/configuración que entrega la corrección, sin necesidad de reconstruir por los dos documentos.

### Brecha concreta y smoke observado por Codex

Los logs originales de arranque no conservaban las respuestas HTTP ni el código de salida del proceso inválido. Para corroborar esos criterios obligatorios se repitió **únicamente el smoke del JAR exportado existente**; no se ejecutaron tests ni compilación. Se usaron procesos propios, directorios temporales nuevos y sólo `/api/ping`, sin invocar proveedores.

Evidencia nueva: `/tmp/norma-issue1-reaudit-c92j9gys/results.json` y `env/server.log`, `file/server.log`, `cli/server.log`, `invalid/server.log` bajo ese directorio. Ejecución mediante Python `subprocess.Popen` y `urllib.request`, con JDK `/tmp/norma-jdk/bin/java --enable-native-access=ALL-UNNAMED -jar /tmp/norma-verify-63b9a8a.1NRfTx/target/taller-1.0.0.jar`. Se limitó el entorno heredado a PATH/HOME/LANG, se fijó JAVA_HOME y `GROQ_API_KEY=`; cada caso tuvo SQLite/uploads propios. No se imprimieron secretos.

| Caso observado | Entradas | Resultado real |
| --- | --- | --- |
| Entorno | `PORT=18081`, `NORMA_DB` y `NORMA_UPLOADS` bajo `env/` | GET /api/ping: **HTTP 200 PONG**, base creada y directorio uploads existente. |
| Archivo externo | `PORT=18081` y additional-location a `file/norma.properties` con `server.port=18082`, URL SQLite/uploads aislados | GET en 18082: **HTTP 200 PONG**, base/uploads presentes. Corrobora que el alias PORT no sustituye la propiedad externa. |
| CLI sobre entorno y archivo | `PORT=18081`, `SERVER_PORT=18084`, archivo con 18082 y `--server.port=18083` | GET en 18083: **HTTP 200 PONG**, base/uploads presentes. Corrobora prioridad CLI sobre variable nativa y archivo. |
| Puerto inválido | `PORT=invalid`, sin archivo externo ni argumento de puerto | Proceso termina con **exit 1** y log de fallo de binding de `server.port`. |

El script completo terminó con exit 0 y `ALL PASS`. Cada proceso iniciado fue terminado/esperado por el propio script antes de pasar al siguiente; no se tocaron procesos ajenos. Los resultados prueban inicio sin clave, configuración externa y el contrato ping; no prueban STT, TTS ni administración autenticada.

### Cierre técnico del addendum

Quedan demostrados para #1: base Maven/Java 25 y Boot 3.5.16, build/tests de archivos versionados sin omisiones, bytecode 69, JAR ejecutable, inicio sin clave, configuración externa por entorno/archivo/CLI, rechazo de puerto inválido y decisiones/alcance corregidos. **No quedan correcciones obligatorias de F1–F7.** La evidencia temporal debe preservarse por Orca si necesita retención posterior; sus rutas no son requisitos de instalación.

Se mantiene intacto el trabajo local no auditado, sin incorporarlo a la conformidad del commit. Codex sólo actualizó este informe; no modificó implementación ni documentos del builder, no creó commits, no hizo push ni cerró la issue. Corresponde a Orca registrar la aceptación final y decidir el cierre conforme al protocolo.
