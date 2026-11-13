# Registro de Decisiones de Arquitectura (ADR) y Riesgos

Este documento registra las decisiones arquitectónicas tomadas para **El Taller de Norma**, sus fundamentos, alternativas consideradas y límites de evidencia.

---

## Decisiones Aprobadas — Base Issue #1 (Diseño Codex v1.0)

### D1 — Java 25 sin flags preview
- **Estado**: Aprobado e implementado en Issue #1.
- **Contexto**: El stack técnico exige Java 25. Las capacidades de records, virtual threads y pattern matching están estandarizadas en Java 25.
- **Decisión**: Compilación y ejecución estricta con Java 25 sin requerir `--enable-preview`. El bytecode resultante corresponde a major version 69.
- **Consecuencias**: Compatibilidad a largo plazo y estabilidad en producción. Se utiliza `--enable-native-access=ALL-UNNAMED` únicamente para bibliotecas JNI nativas (SQLite / WebP), lo cual es independiente de características preview del lenguaje.

### D2 — Spring Boot 3.5.16
- **Estado**: Aprobado e implementado en Issue #1.
- **Contexto**: La especificación técnica exige Spring Boot 3.4+.
- **Decisión**: Se mantiene el parent exacto `org.springframework.boot:spring-boot-starter-parent:3.5.16`, el cual declara soporte completo y oficial para Java 25 y Maven 3.6.3+. No se degrada a la versión ilustrativa 3.4.3 ni se migra a Boot 4.
- **Consecuencias**: Gestión centralizada y probada de dependencias y plugins Maven compatibles con Java 25.

### D3 — Monolito en un único JAR ejecutable
- **Estado**: Aprobado e implementado en Issue #1.
- **Contexto**: Se requiere una arquitectura simple, desplegable en un solo proceso con recursos web estáticos, API REST, SQLite y virtual threads.
- **Decisión**: Un único proceso Java empaquetado en `target/taller-1.0.0.jar` bajo las coordenadas `com.norma:taller:1.0.0` y entry point `com.norma.taller.TallerApplication`.
- **Consecuencias**: Cero dependencias de microservicios, brokers externos o entornos Node.js para servir la aplicación web.

### D4 — Configuración externa nativa de Spring Boot
- **Estado**: Aprobado e implementado en Issue #1.
- **Contexto**: Necesidad de parametrizar puertos, rutas de almacenamiento y credenciales sin alterar el código ni exponer secretos.
- **Decisión**: Empleo exclusivo del sistema de configuración externa de Spring Boot (CLI, variables de entorno y archivos `.properties` externos vía `--spring.config.additional-location`). Ningún secreto en el repositorio ni carga mágica de dotenv. Ausencia de clave Groq permite el inicio normal del monolito.
- **Brecha de modelos y voz documentada**: Parámetros mencionados en la especificación técnica preliminar (`groq.stt.model`, `groq.llm.model`, `norma.tts.voice`, `norma.tts.rate`) no forman parte del contrato de configuración externa de la base en Issue #1; los modelos y voz se encuentran fijos en los componentes correspondientes y su diseño/parametrización configurable se revisará en la Issue #5. No se agregan propiedades inertes ni código sin consumidor en la base.
- **Consecuencias**: Compatibilidad estándar con despliegues en contenedores y servidores locales; precedencia determinista y trazable.

### D5 — Sandbox público sin autenticación tradicional
- **Estado**: Aprobado para la base en Issue #1.
- **Contexto**: El producto define un modelo de evaluadores mediante sesiones independientes basadas en tokens.
- **Decisión**: No incorporar Spring Security ni flujos tradicionales de login/contraseña. Un identificador de sesión compartido permite evaluar la vidriera en modo sandbox.
- **Consecuencias**: No debe considerarse un entorno con control de acceso administrativo ni roles de seguridad de producción. Cualquier webhook futuro requerirá un diseño de autorización específico.

### D6 — Persistencia compatible y sin migraciones en Issue #1
- **Estado**: Aprobado para la base en Issue #1.
- **Contexto**: SQLite con modo WAL y driver `org.xerial:sqlite-jdbc`.
- **Decisión**: Se conserva la configuración y el esquema existentes sin introducir migraciones en #1; no se eliminan bases para lograr un arranque limpio y se verifica con almacenamiento temporal aislado. La integridad, concurrencia y recuperación se auditarán en #2; no se garantiza ausencia de corrupción.
- **Consecuencias**: Se respeta la evolución secuencial de la arquitectura sin alteraciones anticipadas del esquema relacional.

### D7 — Documentación honesta y sin garantías no verificadas
- **Estado**: Aprobado e implementado en Issue #1.
- **Contexto**: Existen metas documentadas de cold start 0 ms, latencia universal o voz gratuita sin límites.
- **Decisión**: No declarar cumplidos requisitos no verificados empíricamente en la fase actual. Separar claramente la base técnica verificada de las áreas pendientes (#2 a #8). En la verificación, distinguir explícitamente el árbol de trabajo local (6 tests, incluyendo suites/clases sin seguimiento) de una exportación limpia versionada (`git archive`, 4 tests versionados).
- **Consecuencias**: Máxima transparencia técnica en las entregas y criterios de aceptación.

---

## Decisiones Aprobadas — Issue #2 (Diseño Codex v1.0 / Aprobación Orca)

### D8 — Semilla canónica DEFAULT inmutable y clonación relacional
- **Estado**: Aprobado e implementado en Issue #2.
- **Contexto**: La especificación exigía clonar una semilla relacional inmutable de 8 piezas para cada nueva sesión de evaluador. En el código legacy previo se creaban constantes Java duplicadas y no existía un registro de semilla en base de datos.
- **Decisión**: Se crea la sesión canónica `DEFAULT` en `seed.sql` conteniendo exactamente las 8 piezas originales. Se instalan triggers SQL estrictos (`protect_default_sessions_*`, `protect_default_products_*`) que abortan cualquier intento de inserción, actualización o borrado directo sobre `DEFAULT`. La primitiva `ensure(id)` inicializa sesiones nuevas clonando desde `DEFAULT` mediante `INSERT SELECT` en una única transacción atómica.
- **Consecuencias**: Manifiesto relacional único y libre de duplicación en código Java; inmutabilidad garantizada a nivel motor relacional sin flags desactivables en caliente.

### D9 — Sesión como marcador único de inicialización y catálogo vacío
- **Estado**: Aprobado e implementado en Issue #2.
- **Contexto**: Existía ambigüedad sobre si un catálogo sin productos debía interpretarse como una sesión no inicializada y repoblarse automáticamente.
- **Decisión**: La existencia de la fila en la tabla `sessions` es el único marcador de inicialización. Si un usuario o evaluador vacía su catálogo (`restore([], ...)` o vende todas las piezas), llamadas subsecuentes a `ensure(id)` o `all(id)` respetan el catálogo vacío y jamás re-siembran productos. Solo la acción explícita `reset(id)` repone las 8 piezas de la semilla.
- **Consecuencias**: Idempotencia estricta en `ensure` y soporte integral para sesiones con vidriera vacía.

### D10 — Numeración monótona de alta-water mark a través de reset y restore
- **Estado**: Aprobado e implementado en Issue #2.
- **Contexto**: Se requería que los números de pieza emitidos públicamente fueran estables y no se reciclaran dentro de una sesión, incluso tras reversiones o restauraciones de catálogo.
- **Decisión**: `next_number` funciona como contador histórico monótono (high-water mark). Las altas de piezas asignan `next_number` y lo incrementan. Al ejecutar `restore` o `reset`, el contador se ajusta a `MAX(contador_anterior, MAX(pieza_restaurada)+1, 9)`, garantizando que un alta posterior a un reset o rollback no reutilice numeración previamente emitida. Los huecos en la secuencia son válidos y esperados.
- **Consecuencias**: Estabilidad e inmutabilidad referencial de las piezas para clientes y evaluadores.

### D11 — Migrador conservador fail-closed v0→v2 sin dependencias externas
- **Estado**: Aprobado e implementado en Issue #2.
- **Contexto**: Migración segura de esquemas legacy sin frameworks pesados (como Flyway o JPA) que alterarían el monolito.
- **Decisión**: Implementación de `CatalogDatabaseInitializer` que coordina la inspección y migración con `PRAGMA user_version=2`. La migración reconstruye tablas con el patrón seguro `create-new -> copy -> drop-old -> rename-new`, prevalidando tipos enteros, constraints, rangos y ausencia de huérfanos antes de modificar datos. Si se detecta corrupción o un esquema desconocido, el inicializador revierte la transacción de forma cerrada (fail-closed), preservando los datos relacionales preexistentes y `user_version=0`.
- **Consecuencias**: Actualización atómica en el ámbito transaccional relacional, con integridad validada mediante `foreign_key_check` y `quick_check`. La durabilidad física ante cortes de energía o caídas de hardware depende de las políticas del sistema de archivos y exige backups previos al despliegue.


---

## Decisiones y Estado de Fases Posteriores

- **Persistencia y Catálogo (Issue #2)**: Esquema relacional v2, inmutabilidad de DEFAULT, aislamiento transaccional, snapshots y migración fail-closed implementados y cubiertos por suites de pruebas R1–R7, P1–P3 y M1–M3. *Estado: Implementado y verificado por Antigravity; pendiente de reauditoría de conformidad por Codex y aprobación final de Orca.*
- **FSM y Conversación (Issue #3)**: Máquina de estados conversacional, consumo de borradores en confirmación y resolución de referencias. *Estado: Pendiente de Issue #3.*
- **Medios y Formatos de Imagen (Issue #4)**: Procesamiento de WebP mediante Thumbnailator y proveedor ImageIO. *Estado: Pendiente de Issue #4.*
- **Audio, STT y TTS (Issue #5)**: Integración con Groq Whisper, síntesis Edge TTS y parametrización de modelos/voces. *Estado: Pendiente de Issue #5.*
- **SSE e Interfaz Web (Issue #6)**: Streaming de eventos SSE para catálogo y UI accesible. *Estado: Pendiente de Issue #6.*
- **Simulador de Norma sin credenciales (Issue #7)**: Simulador conversacional y vidriera accesible sin credenciales. *Estado: Pendiente de Issue #7.*
- **Docker, Verificación y Despliegue Reproducible (Issue #8)**: Empaquetado en contenedor, verificación reproducible y despliegue. *Estado: Pendiente de Issue #8.*
