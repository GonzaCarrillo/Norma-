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
- **Decisión**: Empleo exclusivo del sistema de configuración externa de Spring Boot (CLI, archivos `.properties` externos vía `--spring.config.additional-location`, y variables de entorno `PORT`, `NORMA_DB`, `NORMA_UPLOADS`, `GROQ_API_KEY`). Ningún secreto en el repositorio ni carga mágica de dotenv. Ausencia de clave Groq permite el inicio normal del monolito.
- **Consecuencias**: Compatibilidad estándar con despliegues en contenedores y servidores locales; precedencia determinista y trazable.

### D5 — Sandbox público sin autenticación tradicional
- **Estado**: Aprobado para la base en Issue #1.
- **Contexto**: El producto define un modelo de evaluadores mediante sesiones independientes basadas en tokens.
- **Decisión**: No incorporar Spring Security ni flujos tradicionales de login/contraseña. Un identificador de sesión compartido permite evaluar la vidriera en modo sandbox.
- **Consecuencias**: No debe considerarse un entorno con control de acceso administrativo ni roles de seguridad de producción. Cualquier webhook futuro requerirá un diseño de autorización específico.

### D6 — Persistencia compatible y sin migraciones en Issue #1
- **Estado**: Aprobado para la base en Issue #1.
- **Contexto**: SQLite con modo WAL y driver `org.xerial:sqlite-jdbc`.
- **Decisión**: Conservar la conexión Hikari única (`maximum-pool-size=1`), `PRAGMA busy_timeout=5000` y la inicialización mediante `schema.sql`. La auditoría detallada de tablas y modo WAL se delega a la Issue #2.
- **Consecuencias**: Cero riesgo de corrupción de datos durante el desarrollo base; no se eliminan bases de datos existentes para lograr arranques limpios.

### D7 — Documentación honesta y sin garantías no verificadas
- **Estado**: Aprobado e implementado en Issue #1.
- **Contexto**: Existen metas documentadas de cold start 0 ms, latencia universal o voz gratuita sin límites.
- **Decisión**: No declarar cumplidos requisitos no verificados empíricamente en la fase actual. Separar claramente la base técnica verificada de las áreas pendientes (#2 a #8).
- **Consecuencias**: Máxima transparencia técnica en las entregas y criterios de aceptación.

---

## Decisiones y Estado de Fases Posteriores (Pendientes de Auditoría)

- **Persistencia y Catálogo (Issue #2)**: Esquema relacional, inmutabilidad de DEFAULT, aislamiento transaccional y snapshots. *Estado: Pendiente de especificación y auditoría de Issue #2.*
- **FSM y Conversación (Issue #3)**: Máquina de estados conversacional, consumo de borradores en confirmación y resolución de referencias. *Estado: Pendiente de Issue #3.*
- **Medios y Formatos de Imagen (Issue #4)**: Procesamiento de WebP mediante Thumbnailator y proveedor ImageIO. *Estado: Pendiente de Issue #4.*
- **Audio, STT y TTS (Issue #5)**: Integración con Groq Whisper y síntesis Edge TTS. *Estado: Pendiente de Issue #5.*
- **SSE e Interfaz Web (Issue #6)**: Streaming de eventos SSE para catálogo y UI accesible. *Estado: Pendiente de Issue #6.*
- **Docker y Despliegue (Issue #7 - #8)**: Empaquetado en contenedor y publicación pública. *Estado: Pendiente de auditoría en sus respectivas fases.*
