# Issue #1 — Base Java 25 y decisiones de arquitectura

Versión: **1.0**, 2026-11-12. Autor: Codex, Software Architect.
Estado: **propuesta para aprobación de Orca; no autoriza construcción**.
Tarea: `task_20dda122a87c`, dispatch `ctx_afdaafd7f205`.
Issue: https://github.com/GonzaCarrillo/Norma-/issues/1 (OPEN al consultar).
Base inspeccionada: `796776fb9feb34cabbc76d222d8cf5a5dd45ec7a` más cambios locales no auditados.

## 1. Requisitos y límite de esta entrega

Fuentes leídas: `AGENTS.md`, `arquitectura_y_especificaci_n_el_taller_de_norma.md`, `especificaci_n_t_cnica_y_stack_java_25.md` y cuerpo de la issue obtenido con `gh`.
La issue pide Maven, configuración externa, README, registro de reglas/decisiones, Java 25, Spring Boot 3.4+ y monolito sin autenticación tradicional; no tiene dependencias.

Esta fase diseña una base reproducible y documenta sus límites. No implementa ni aprueba catálogo, FSM, voz, imágenes, SSE, interfaz, Docker, CI o despliegue de las issues #2–#8. Que existan commits y archivos de esas áreas no acredita su aceptación. No se crearán cuentas, contraseñas, magic links, integración real con WhatsApp, carrito ni pagos.

## 2. Estado observado frente a requisitos

| Requisito | Hecho inspeccionado | Acción de construcción propuesta |
| --- | --- | --- |
| Maven y Java 25 | `pom.xml`: parent Boot 3.5.16, `java.version=25`, plugin Boot. El parent local define `maven.compiler.release=${java.version}`. | Mantener versiones/coordenadas, explicitar `maven.compiler.release=25` para visibilidad y ordenar el XML sin cambiar dependencias. Verificar compilación y bytecode. |
| Runtime compatible | JDK 25 en `/tmp/norma-jdk`; Maven 3.8.7 disponible, pero usa JDK 21 por defecto. | Documentar selección de `JAVA_HOME` y `PATH`; verificar ambos antes de construir. |
| Monolito | `TallerApplication` arranca Spring Boot, Web MVC/JDBC y recursos estáticos en el mismo artefacto. | Conservar estructura y entry point, sin refactorizar paquetes. |
| Configuración externa | `PORT`, `NORMA_DB`, `NORMA_UPLOADS`, `GROQ_API_KEY` ya tienen placeholders y valores locales predeterminados. | Documentar contrato, precedencia, persistencia y comprobación por arranque con valores alternativos. |
| Concurrencia | Virtual threads habilitados; SQLite pool de una conexión y `busy_timeout=5000`. | Conservar valores; no prometer escalabilidad horizontal ni escrituras paralelas. |
| README | Hay ejecución mínima, reglas y alcance; faltan selección explícita de JDK, tabla de variables y evidencia exigida. | Completar pasos reproducibles y separar requisitos de capacidades verificadas. |
| Decisiones | `docs/decisiones.md` ya contiene afirmaciones sobre áreas futuras sin evidencia adjunta. | Registrar ADR de base y etiquetar decisiones futuras como pendientes de auditoría. |
| Sin autenticación tradicional | No hay starter Security en POM; API pública basada en identificador de sesión. | Documentar sandbox público y que sesión no acredita identidad ni autorización de administración real. |

Trabajo a preservar: `BotEngine.java` modificado; `IntentFallback.java` y `ApiTest.java` sin seguimiento; también `AGENTS.md` y documentación de orquestación sin seguimiento. No hacer reset, stash, limpieza ni inclusión global en commits. Los commits previos no tienen aprobación implícita.

## 3. Decisiones propuestas para aprobación

**D1 — Java 25, sin preview.** Compilación con release 25 y ejecución con JDK/JRE 25. Records, virtual threads y pattern matching no justifican `--enable-preview`. El JDK temporal es una ruta del entorno actual, no un prerrequisito universal. En otro equipo se usa la instalación equivalente.

**D2 — Spring Boot 3.5.16.** Mantener el parent exacto actual, que satisface 3.4+. La documentación oficial de [requisitos de Spring Boot 3.5.16](https://docs.spring.io/spring-boot/3.5/system-requirements.html), consultada en esta fase, declara compatibilidad hasta Java 25 y Maven 3.6.3+. No copiar la versión ilustrativa 3.4.3 del documento técnico ni migrar a Boot 4: ambas alternativas agregan cambios innecesarios. El parent administra plugins y dependencias Spring; conservar versiones explícitas actuales de SQLite/Thumbnailator/WebP, sin auditar con ello sus funciones.

**D3 — Un JAR ejecutable, un proceso.** Spring MVC/Tomcat, JDBC, recursos estáticos y configuración Spring en un monolito. Conservar `com.norma:taller:1.0.0` y `com.norma.taller.TallerApplication`; las diferencias de nombres respecto al árbol ilustrativo no alteran contrato funcional y evitan renombrados ajenos al objetivo. Sin JPA, broker, microservicios, Node ni Maven Wrapper obligatorio en esta issue.

**D4 — Configuración externa nativa de Spring.** No agregar dotenv ni un lector de secretos propio. Variables de entorno y archivo externo de propiedades; ningún secreto en el repositorio, logs o ejemplos. El `.env` ignorado no se carga automáticamente al ejecutar Java. La ausencia de clave Groq debe permitir iniciar el monolito; no se exige conectividad con proveedores para compilar o consultar ping.

**D5 — Sandbox sin login.** Mantener la decisión de producto y no agregar Spring Security. Un identificador de sesión compartido permite acceder a esa sesión: no es una cuenta ni un canal de administración de producción. No publicar ni desplegar durante #1. Autorización de un futuro webhook requiere diseño separado.

**D6 — Persistencia compatible, sin migración.** Conservar URL SQLite, driver, inicialización SQL y esquema existentes. No borrar una base para conseguir un arranque exitoso. Para aceptación se usa un directorio temporal nuevo. La elección de WAL y el esquema real se auditarán en #2; #1 sólo verifica que el contexto actual puede arrancar. No cambiar tablas para parecerse al esquema ilustrativo.

**D7 — Documentar honestamente.** Los objetivos de cold start 0 ms, latencia universal, voz gratuita sin límites y cumplimiento AAA no se consideran garantías probadas. No convertirlos en criterios de #1 ni repetirlos como resultados medidos. La eventual discrepancia WebP/Thumbnailator se conserva como tema de #4, no se resuelve aquí.

## 4. Contrato de configuración de la base

| Variable / propiedad | Predeterminado | Contrato y validación de aceptación |
| --- | --- | --- |
| `PORT` / `server.port` | `8080` | Puerto TCP libre; para el smoke test elegir uno alternativo. Un valor no numérico debe impedir el arranque con error de configuración. |
| `NORMA_DB` / `spring.datasource.url` | `norma.db` | Placeholder compone `jdbc:sqlite:<ruta>`; variable contiene ruta de archivo, no URL JDBC completa. Padre existente y escribible, fuera del repositorio en la prueba. |
| `NORMA_UPLOADS` / `norma.uploads` | `uploads` | Directorio escribible; la implementación actual lo crea al arrancar. Ruta absoluta recomendada fuera de desarrollo. |
| `GROQ_API_KEY` / `groq.api.key` | vacío | Secreto opcional para inicio; no imprimir ni comprobar con llamada real en #1. |
| `spring.threads.virtual.enabled` | `true` | Mantener y verificar valor efectivo; no equiparar virtual threads con mayor capacidad de escritura SQLite. |
| `spring.datasource.hikari.maximum-pool-size` | `1` | Mantener durante esta issue. |
| `spring.datasource.hikari.connection-init-sql` | `PRAGMA busy_timeout=5000` | Mantener, sin introducir nuevos PRAGMA. |
| `spring.sql.init.mode` | `always` | Mantener el recurso `schema.sql` existente, sin modificarlo. |
| `spring.servlet.multipart.max-file-size` / `max-request-size` | `15MB` / `20MB` | Límites existentes, conservar unidades y diferencia entre archivo y solicitud. |
| `server.error.include-message` | `never` | Mantener; no exponer configuración ni secretos por endpoints de diagnóstico. |

El README debe explicar que `--server.port=...` prevalece sobre entorno y que un archivo externo se añade con `--spring.config.additional-location=file:/ruta/norma.properties`; no versionar ese archivo con secretos. La [referencia oficial de configuración externa](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html) es la referencia técnica para su precedencia.

`groq.stt.model`, `groq.llm.model`, `norma.tts.voice` y `norma.tts.rate` aparecen en la especificación técnica, pero los consumidores actuales tienen modelos/voz hardcodeados. No agregar propiedades inertes ni afirmar que son configurables: documentar la brecha para sus issues; cambiar esos consumidores queda fuera de #1. El contrato externo de la base cubre puerto, almacenamiento, clave opcional y propiedades Spring existentes.

## 5. Contratos y fronteras funcionales

Para aceptación de la base sólo se utiliza el contrato ya existente `GET /api/ping`: sin parámetros ni credenciales, HTTP 200 y cuerpo `PONG`. No se autoriza modificar `Api.java` para esta comprobación.

Los contratos de productos, stream SSE (`catalog-update`), mensajes multipart, reset, estados FSM y medios quedan sin cambios y pendientes de sus fases. El ejemplo documental `replyAudioUrl=/api/bot/audio/...` difiere de las rutas `/media/...` actuales: registrar para diseño de medios, sin modificar ni declarar conforme aquí. Tampoco se redefine el JSON de producto ni el esquema de sesión. No se requieren pruebas funcionales nuevas de esas áreas en #1.

## 6. Lista exacta de archivos autorizables para Antigravity

Tras aprobación explícita de Orca de v1.0, permitir modificaciones sólo en:

1. `pom.xml`: XML legible, propiedad release explícita, conservar coordenadas, versiones, dependencias y plugin Boot.
2. `src/main/resources/application.properties`: comentarios y orden del contrato existente, sin cambiar los valores anteriores ni agregar propiedades sin consumidor.
3. `README.md`: requisitos, selección JDK, comandos Maven/JAR, variables, configuración externa, alcance y referencias.
4. `docs/decisiones.md`: decisiones D1–D7, fuentes, motivos, alternativas y límites de evidencia.

No se requiere crear clases ni pruebas nuevas, modificar `.gitignore` (ya cubre target/uploads/SQLite/.env), `docs/operacion.md`, Docker, compose, CI o backlog. Este archivo de diseño pertenece a Codex/Orca; Antigravity lo referencia sin reescribirlo. Cualquier necesidad de tocar otro archivo se eleva como bloqueo antes de actuar. Se pueden generar artefactos ignorados en `target/` para verificación; no se consideran entregables de código.

## 7. Construcción atómica y secuencial propuesta

Orca debe registrar primero aprobación de esta versión; luego despachar cada paso y validar su predecesor antes del siguiente:

| Paso | Archivos | Entrega verificable |
| --- | --- | --- |
| A1. Formalizar build | `pom.xml` | Versiones preservadas, release 25 explícito, POM efectivo y compilación con JDK 25 satisfactorios. |
| A2. Formalizar configuración | `application.properties` | Valores/consumidores conservados, arranque aislado sin clave y con puerto/rutas alternativas. |
| A3. Documentar base | `README.md`, `docs/decisiones.md` | Comandos ejecutables, tabla consistente con propiedades, alcance y decisiones sin afirmar conformidad futura. |
| A4. Entregar evidencia | sin nuevos archivos de producción | `mvn verify`, smoke test, bytecode, diff y estado final; informe por tarea, comandos, resultados, riesgos y pendientes a Orca. |

Si falla el contexto por código previo, informar error reproducible, no desactivar ni editar pruebas existentes. Orca deriva la incompatibilidad a Codex; no se autoriza una reparación de #2–#8 por el solo hecho de que `verify` las compile. Auditoría de Orca y revisión de conformidad de Codex preceden al cierre de #1.

## 8. Verificación prescrita, aún no ejecutada salvo sección 9

Ejecutar secuencialmente en la raíz y sin otro Maven activo. Antes, registrar `git status --short` y `git diff --stat` para atribuir cambios. Elegir un puerto libre; `18081` es ejemplo, no disponibilidad comprobada.

```bash
export JAVA_HOME=/tmp/norma-jdk
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
mvn -B help:effective-pom -Doutput=target/issue-1-effective-pom.xml
mvn -B -DskipTests compile
mvn -B verify
javap -verbose target/classes/com/norma/taller/TallerApplication.class
jar tf target/taller-1.0.0.jar
```

Esperado: Java/Maven informan JDK 25; POM efectivo release 25 y versiones concretas de plugins heredados; Maven termina `BUILD SUCCESS` sin pruebas fallidas u omitidas inesperadas. `javap` informa major version 69. El JAR contiene clases en `BOOT-INF/classes/` y dependencias en `BOOT-INF/lib/`. `-DskipTests` se usa sólo en la compilación intermedia, nunca como sustituto de `verify`. Registrar número real de pruebas y advertencias; un JAR previo no es evidencia de la ejecución actual.

Smoke test del JAR recién generado (mantener proceso en una terminal dedicada, otra para HTTP; terminar sólo ese proceso al finalizar):

```bash
issue1_runtime_dir=$(mktemp -d /tmp/norma-issue1.XXXXXX)
PORT=18081 NORMA_DB="$issue1_runtime_dir/norma.db" NORMA_UPLOADS="$issue1_runtime_dir/uploads" GROQ_API_KEY= \
  java --enable-native-access=ALL-UNNAMED -jar target/taller-1.0.0.jar
```

```bash
curl --fail --silent --show-error -i http://localhost:18081/api/ping
```

Esperado: arranque sin pedir claves/login, HTTP 200 `PONG`, archivo SQLite y directorio uploads creados bajo la ruta temporal impresa por el operador (no volcar secretos). La opción de acceso nativo permite las bibliotecas JNI existentes y no es preview. Registrar ruta, puerto, proceso y salida real. Detenerlo antes de repetir. No borrar archivos del usuario ni lanzar consultas que modifiquen el catálogo para demostrar #1.

Para verificar archivo externo, crear **fuera del repositorio** un `norma.properties` con `server.port=18082`, `spring.datasource.url=jdbc:sqlite:<otra-ruta-temporal>/norma.db`, `norma.uploads=<otra-ruta-temporal>/uploads` y `groq.api.key=`; arrancar con `--spring.config.additional-location=file:<ruta-absoluta>/norma.properties`. Esperado: ping en 18082 y almacenamiento en esa otra ruta. Para comprobar precedencia, repetir con `--server.port=18083` y esperar ping allí. Evitar heredar claves de entorno y no registrar contenidos secretos. Repetir finalmente con `PORT=invalid` y esperar salida no cero por binding de puerto, sin servidor escuchando. No hace falta agregar una suite que replique estos valores en código.

Al terminar: `git diff --check`, `git diff -- pom.xml src/main/resources/application.properties README.md docs/decisiones.md` y `git status --short`; comprobar que los archivos previos preservados siguen intactos respecto al inicio del trabajo y que no se incluyó ningún secreto.

## 9. Evidencia real de esta fase de diseño

- `gh issue view 1 --repo GonzaCarrillo/Norma- --json number,title,body,state,url,labels`: exit 0; issue OPEN, criterios y restricciones coinciden con sección 1.
- `git rev-parse HEAD`: exit 0; hash registrado al inicio. `git status --short` y `git log -6 --oneline`: exit 0; identifican cambios previos y commits de #1–#8 sin auditarlos.
- Lectura con `cat`, `rg`, `sed` de instrucciones, ambas especificaciones, POM, configuración, entry point, API, consumidores de configuración y pruebas existentes: sin editar código.
- `/tmp/norma-jdk/bin/java -version`: exit 0; Java 25.0.5.0.3, runtime Oracle 25.0.5.0.3+1-LTS-2.
- `mvn -version`: exit 0; Maven 3.8.7 ejecutándose sobre Ubuntu Java 21.0.12.1.
- `JAVA_HOME=/tmp/norma-jdk PATH=/tmp/norma-jdk/bin:$PATH mvn -version`: exit 0; Maven 3.8.7 ejecutándose sobre Java 25.0.5.0.3 en `/tmp/norma-jdk`.
- `rg` del parent POM en caché local: define `maven.compiler.release=${java.version}`. Es evidencia estática, no resolución remota ni compilación.
- Consulta web de documentación oficial de Boot 3.5: compatibilidad Java 25 y mínimo Maven confirmados. Abrir el POM remoto mediante herramienta web devolvió error de acceso; no se afirma resolución remota exitosa del artefacto.
- **No se ejecutaron compilación, pruebas, empaquetado, arranque, Docker ni despliegue** en esta fase, evitando concurrencia de builds y conservando el rol de arquitecto. Los resultados esperados de sección 8 no son resultados observados.

## 10. Riesgos, bloqueos y aprobación

No hay bloqueo para entregar el diseño. La aprobación de Orca es requisito pendiente para construir y todavía no se acredita el criterio «compila en Java 25».

Riesgos: Maven usa JDK 21 si no se configura; tests existentes pueden fallar o invocar TTS si está instalado; dependencias nativas SQLite/WebP requieren validación real en JDK 25; puertos propuestos pueden estar ocupados; rutas no escribibles impedirán el arranque; cambios locales ajenos pueden contaminar la evidencia. La configuración ausente de modelos/voz y las diferencias de esquema/rutas quedan registradas para las fases correspondientes. Si una brecha impide aceptar #1, Orca debe resolverla con una revisión versionada del diseño, sin ampliar alcance silenciosamente.

Entregable de esta fase: únicamente `docs/orchestration/issue-1-design.md`. Pendiente: aprobación versionada por Orca, construcción por Antigravity, evidencia de ejecución, auditoría y revisión arquitectónica. No cerrar issue, hacer commits ni push desde esta entrega.
