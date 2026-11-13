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

## Decisiones Aprobadas — Issue #4 (Diseño Codex v1.0 / Aprobación Orca)

### D12 — Allowlist de formatos estáticos y rechazo de contenedores inseguros
- **Estado**: Aprobado e implementado en Issue #4.
- **Contexto**: Exposición a vulnerabilidades por formatos políglotas, animaciones no deseadas (denegación de servicio por frames infinitos) y metadatos EXIF excesivos o maliciosos.
- **Decisión**: Allowlist estricta para JPEG/JPG, PNG8 y WebP estático. Se prohíben y rechazan con HTTP 400 WebP animados (chunks `ANIM`/`ANMF` o flag `animation` en cabecera `VP8X`), perfiles de color ICC incrustados y bloques EXIF mayores a 262.144 bytes o corruptos. La extracción de orientación EXIF (tags 1..8) se limita al bloque IFD0 mediante `metadata-extractor:2.19.0`.
- **Consecuencias**: Superficie de ataque reducida y rechazo seguro en el borde de entrada antes de instanciar rasters no confiables.

### D13 — Normalización geométrica determinista y revelado consistente
- **Estado**: Aprobado e implementado en Issue #4.
- **Contexto**: Las fotos de los usuarios pueden provenir de múltiples cámaras con distintas relaciones de aspecto, orientaciones EXIF y canales alfa.
- **Decisión**: Se aplica un recorte centrado con relación 4:3 determinista con factor $k = \min(\lfloor W_o/4 \rfloor, \lfloor H_o/3 \rfloor)$ (rechazo si $k=0$), seguido de redimensionamiento proporcional a 800x600 px y rotación estricta según el tag EXIF 1..8. El raster se compone sobre fondo blanco sólido `#FFFFFF` para neutralizar transparencia y se aplica corrección tonal sRGB8 determinista ($R'=1.0403 \times R, G'=1.03 \times G, B'=1.0197 \times B$).
- **Consecuencias**: Catálogo visual homogéneo con relación 4:3 en 800x600 px y colores calibrados sin artefactos de transparencia.

### D14 — Bucle de codificación con pérdida en 8 intentos y límite estricto de 80.000 bytes
- **Estado**: Aprobado e implementado en Issue #4.
- **Contexto**: Se requiere garantizar que ninguna imagen publicada sobrepase el presupuesto de red móvil para Norma (< 80 KB) sin reducir la resolución prefijada de 800x600 px.
- **Decisión**: Se ejecuta un bucle de compresión con pérdida explícita sobre el raster revelado utilizando las calidades `[80, 70, 60, 50, 40, 30, 20, 10]`. Se acepta el primer candidato cuyo tamaño cumpla $0 < \text{bytes} < 80.000$ (en la práctica, el tamaño máximo admisible es 79.998 bytes debido a la alineación RIFF par). Si tras el intento con calidad 10 el tamaño aún iguala o supera 80.000 bytes, se rechaza la imagen con HTTP 400 sin degradar la resolución geométrica.
- **Consecuencias**: Garantía criptográfica y binaria del cumplimiento de cuota de ancho de banda sin sorpresas visuales por resoluciones degradadas.

### D15 — Ranura única de procesamiento fotográfico y control de memoria
- **Estado**: Aprobado e implementado en Issue #4.
- **Contexto**: La decodificación y transformación de imágenes de hasta 24 MP (p. ej. 6000x4000 px) requiere entre 65 y 100 MB de memoria de trabajo transitoria por imagen. En un servidor con recursos limitados, múltiples peticiones simultáneas podrían causar agotamiento de memoria (OOM).
- **Decisión**: Se implementa un semáforo de un único permiso (`Semaphore(1)`) que controla la sección crítica del procesador. Cualquier petición concurrente adicional es rechazada inmediatamente con HTTP 500 ("Servidor ocupado procesando otra imagen.") sin retener buffers ni encolar tareas.
- **Consecuencias**: Cero riesgo de saturación de heap o contención descontrolada de memoria bajo ráfagas concurrentes.

### D16 — Aislamiento en staging, publicación atómica y serving seguro con fail-safe 404
- **Estado**: Aprobado e implementado en Issue #4.
- **Contexto**: Prevenir condiciones de carrera, archivos parcialmente escritos visibles públicamente y ataques de path traversal hacia el sistema de archivos del host.
- **Decisión**: Los archivos temporales se alojan en `<norma.uploads>/.processing/` con nombres opacos. Una vez validado el candidato, se publica atómicamente a la raíz de uploads mediante `Files.move(..., ATOMIC_MOVE)` con un UUID v4. El endpoint `/media/**` bloquea accesos con `..`, nombres ocultos, symlinks fuera de uploads y peticiones a `.processing/`, respondiendo siempre HTTP 404 genérico sin fugar rutas internas.
- **Consecuencias**: Consistencia atómica en el sistema de archivos y aislamiento contra vulnerabilidades de divulgación de datos.

### D17 — Procedencia honesta de recursos de demostración y límites de UI
- **Estado**: Aprobado e implementado en Issue #4.
- **Contexto**: Transparencia y cumplimiento de autoría en las imágenes de demostración iniciales y comportamiento de la interfaz web.
- **Decisión**: Se utilizan 8 encuadres WebP derivados de 4 fotografías humanas de Unsplash (sin IA). Se documenta explícitamente en `docs/medios.md` el inventario con hashes SHA-256 de las 8 imágenes y 4 audios MP3. En la interfaz web (`app.js`), los botones de demostración reproducen el MP3 correspondiente pero envían texto predefinido, documentándose con total honestidad sin pretender simular integración con STT en caliente durante el modo demo.
- **Consecuencias**: Integridad profesional y trazabilidad auditada de los recursos estáticos.

---

## Decisiones y Estado de Fases Posteriores

- **Persistencia y Catálogo (Issue #2)**: Esquema relacional v2, inmutabilidad de DEFAULT, aislamiento transaccional, snapshots y migración fail-closed implementados y cubiertos por suites de pruebas R1–R7, P1–P3 y M1–M3. *Estado: Implementado y verificado por Antigravity; conforme en auditoría técnica.*
- **FSM y Conversación (Issue #3)**: Máquina de estados conversacional, consumo de borradores en confirmación y resolución de referencias. *Estado: Implementado y verificado.*
- **Medios y Formatos de Imagen (Issue #4)**: Procesamiento de WebP, validación de formatos, límites de 15 MiB / 24 MP / 16384 px, normalización 4:3 a 800x600 px, codificación con pérdida < 80.000 bytes, ranura única de concurrencia, almacenamiento atómico y serving seguro. *Estado: Implementado y verificado por Antigravity (B1–B3 completados, 45 pruebas dedicadas y 175 pruebas totales en verde); listo para auditoría independiente OpenCode y revisión final Codex.*
- **Audio, STT y TTS (Issue #5)**: Integración con Groq Whisper, síntesis Edge TTS y parametrización de modelos/voces. *Estado: Pendiente de Issue #5.*
- **SSE e Interfaz Web (Issue #6)**: Streaming de eventos SSE para catálogo y UI accesible. *Estado: Pendiente de Issue #6.*
- **Simulador de Norma sin credenciales (Issue #7)**: Simulador conversacional y vidriera accesible sin credenciales. *Estado: Pendiente de Issue #7.*
- **Docker, Verificación y Despliegue Reproducible (Issue #8)**: Empaquetado en contenedor, verificación reproducible y despliegue. *Estado: Pendiente de Issue #8.*
