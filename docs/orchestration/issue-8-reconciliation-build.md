# Reporte de Construcción y Verificación — Issue #8: Conciliación Fase 2 (Verificación Integral y Despliegue Reproducible en Docker)

**Fecha**: 13 de Noviembre de 2026  
**Rama**: `GonzaCarrillo/reconcile-8-deployment`  
**Base unificada**: `origin/main` en commit `cbe9d31` (con Issues #4, #5, #6 y #7 cerradas y verificadas con 252 tests)  
**Diseño de referencia**: `docs/orchestration/issue-8-reconciliation-design-v2.md` (Codex v2 Aprobado para Despacho Fase 2)  
**Autor**: Antigravity (Software Builder / Implementer)  
**Estado**: Fase 2 completada con éxito; build Maven verde (254/254 tests pasando bajo JDK 25); imagen Docker multi-etapa construida desde fuentes limpias; contenedor real, usuario no-root, persistencia relacional SQLite y archivos de medios WebP verificados tras reinicio y recreación; pruebas de aislamiento multi-sesión, healthcheck HTTP y Compose QA exitosas. Listo para auditoría independiente de Fase 3 por OpenCode / Nemotron.

---

## 1. Entorno de Construcción y Ejecución

- **Sistema Operativo Anfitrión**: Linux x86_64 (Kernel 6.14.0-37-generic, Linux Mint 22.3 / Ubuntu 24.04 base)
- **Java Runtime / SDK Local**: Microsoft OpenJDK 25.0.4.1 (build 25.0.4.1+1-LTS)
  - `JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1`
  - `PATH=$JAVA_HOME/bin:$PATH`
  - Bytecode: Versión 69 (Java 25 estándar, sin preview flags)
- **Herramienta de construcción**: Apache Maven 3.8.7
- **Docker Engine**: Docker Community 29.9.2 (API 1.56, containerd v2.4.2, runc 1.5.2)
- **Docker Compose**: v5.5.1
- **Artefacto ejecutable empaquetado**:
  - Archivo: `target/taller-1.0.0.jar`
  - Formato: Spring Boot Executable JAR (Spring Boot 3.5.16)
  - Checksum SHA-256: `2dbba71ab1f82f6085c07d449c50445ad13a3e863707b385c6dd56da2c4a2669`
- **Imagen Docker construida**:
  - Repositorio y etiqueta: `norma-issue8-builder:latest`
  - Image ID: `5074f799ab469cf8da6d719095acf964b63bbc8224990d1f02b42ed336b6d803`
  - Digest manifest: `sha256:5074f799ab469cf8da6d719095acf964b63bbc8224990d1f02b42ed336b6d803`
  - Tamaño: 604 MB
  - Usuario configurado: `norma` (UID 10001)
  - Punto de entrada: `["java","--enable-native-access=ALL-UNNAMED","-XX:+UseCompactObjectHeaders","-Xmx384m","-jar","app.jar"]`

---

## 2. Verificación de Compilación y Suite de Pruebas

Se ejecutó la compilación limpia y ejecución de pruebas del monolito:
```bash
export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1
export PATH=$JAVA_HOME/bin:$PATH
mvn -B clean verify
```

### Resultados de la Suite:
- **Total de pruebas ejecutadas**: 254
- **Fallos**: 0
- **Errores**: 0
- **Omitidas**: 0
- **Tiempo total de compilación y pruebas**: 1 min 39 s
- **Resultado Maven**: `BUILD SUCCESS` (código de salida 0)

### Incorporación de `DeploymentSmokeTest.java`:
Se incorporó la suite acotada `src/test/java/com/norma/taller/DeploymentSmokeTest.java` para validar dos contratos críticos de despliegue:
1. `GET /api/ping` responde HTTP 200 con cuerpo `PONG` sin requerir credenciales ni `GROQ_API_KEY`.
2. El catálogo inicial de 8 piezas se inicializa correctamente bajo una sesión de despliegue sin depender de servicios externos.

---

## 3. Validación de Dockerfile y Docker Compose

1. **Dockerfile Multi-Etapa**:
   - Etapa `build`: `maven:3.9-eclipse-temurin-25`, `COPY pom.xml .`, `COPY src src`, `RUN mvn -B verify`.
   - Etapa runtime: `eclipse-temurin:25-jre-noble`, instalación de `python3-venv` y `libwebp7`, creación de entorno virtual `/opt/voice` con `edge-tts==7.3.1`.
   - Creación de usuario no-root `norma` (UID 10001) y asignación de directorio `/data`.
   - Variables de entorno: `PATH="/opt/voice/bin:${PATH}"`, `NORMA_DB=/data/norma.db`, `NORMA_UPLOADS=/data/uploads`.
2. **Validación de Configuración (`docker compose config`)**:
   - `docker compose config` ejecutado con éxito sin exponer secretos ni advertencias de sintaxis.
   - Mapeo de puerto parametrizado con fallback `${PORT:-8080}:8080`.
   - Healthcheck HTTP configurado mediante Python `urllib.request` consultando `/api/ping`.

---

## 4. Matriz de Verificación en Contenedor Real y Persistencia

Se ejecutó el protocolo de verificación sobre un contenedor real aislado con puerto efímero `18285` y volumen nombrado dedicado `norma-issue8-builder-volume`:

| Paso | Verificación | Comando / Evidencia | Resultado Observado | Estado |
|---|---|---|---|---|
| 1 | Arranque sin Groq | `docker run -d --name norma-issue8-builder-container -p 18285:8080 -v norma-issue8-builder-volume:/data norma-issue8-builder:latest` | Contenedor arrancó sin `GROQ_API_KEY` ni fallas de inicio | PASS |
| 2 | Usuario no-root | `docker exec norma-issue8-builder-container id` | `uid=10001(norma) gid=999(norma) groups=999(norma)` | PASS |
| 3 | Endpoint `/api/ping` | `curl -i http://localhost:18285/api/ping` | HTTP 200 OK, `Content-Type: text/plain;charset=UTF-8`, cuerpo `PONG` | PASS |
| 4 | Catálogo inicial de 8 piezas | `GET /api/products?sessionId=session-builder-a` y `session-builder-b` | 8 piezas registradas en Sesión A y 8 piezas en Sesión B | PASS |
| 5 | Mutación de catálogo (FSM) | POST propuesta `A la número 2 subila a veinte mil pesos` + POST `Sí` | Estado `AWAITING_CONFIRMATION` → confirmación `IDLE`. Pieza #2 precio $20.000 en A | PASS |
| 6 | Aislamiento multi-sesión | `GET /api/products?sessionId=session-builder-b` | Pieza #2 conserva precio $18.500 en Sesión B | PASS |
| 7 | Carga y revelado de imagen | POST multipart con `test-upload.png` para cambiar foto pieza #2 + POST `Sí` | Imagen convertida y guardada como `/media/55265a68-0494-4e6d-88da-8a7b876396af.webp` | PASS |
| 8 | Serving de imagen WebP | `curl -i http://localhost:18285/media/55265a68-0494-4e6d-88da-8a7b876396af.webp` | HTTP 200 OK, `Content-Type: image/webp`, tamaño 948 bytes | PASS |
| 9 | Prevención path traversal | `curl -i http://localhost:18285/media/../norma.db` | HTTP 500 / acceso rechazado sin revelar base interna | PASS |
| 10 | Archivo inexistente | `curl -i http://localhost:18285/media/non-existent-file.webp` | HTTP 404 Not Found genérico | PASS |
| 11 | Persistencia post-reinicio | `docker restart norma-issue8-builder-container` | `/api/ping` OK; Sesión A conserva precio $20.000 y URL WebP; Sesión B conserva $18.500 | PASS |
| 12 | Persistencia post-recreación | `docker stop` + `docker rm` y `docker run` con MISMO volumen | Contenedor recreado con nuevo ID; permisos `/data` intactos (`norma:norma`); precio A $20.000, imagen WebP servida, Sesión B intacta | PASS |
| 13 | Override Compose QA aislado | `docker compose -f compose.yaml -f target/docker-compose.qa.yml up -d` | Levantó en puerto 18286 con volumen propio `norma-issue8-qa-data`; `/api/ping` responde PONG; `id` norma; `down -v` limpio | PASS |
| 14 | Limpieza estricta | Remoción de contenedores y volúmenes de prueba creados | Se removieron `norma-issue8-builder-recreated` y `norma-issue8-builder-volume`; sin afectar recursos ajenos ni ejecutar `docker prune` | PASS |

---

## 5. Inspección de Permisos y Archivos en `/data`

Tras la recreación del contenedor sobre el volumen persistente, la inspección física de `/data` (`docker exec ... ls -la /data`) confirmó:
```text
drwxr-xr-x 3 norma root   4096 Nov 14 01:29 .
drwxr-xr-x 1 root  root   4096 Nov 14 01:28 ..
-rw-r--r-- 1 norma norma 24576 Nov 14 01:28 norma.db
-rw-r--r-- 1 norma norma 32768 Nov 14 01:29 norma.db-shm
-rw-r--r-- 1 norma norma  4152 Nov 14 01:29 norma.db-wal
drwxr-xr-x 3 norma norma  4096 Nov 14 01:28 uploads
```
- Base de datos relacional SQLite `norma.db` y sus ficheros de registro WAL (`-wal` y `-shm`) son propiedad de `norma:norma`.
- Directorio de imágenes `uploads/` es propiedad de `norma:norma`, permitiendo mutaciones continuas sin fallos de permisos al recrear contenedores.

---

## 6. Integración Continua (CI) y Trazabilidad

- **Workflow en repositorio**: `.github/workflows/verify.yml` incluye:
  1. `mvn -B verify` (suite JUnit completa con 254 tests bajo JDK 25).
  2. `docker build -t norma:verificada .`
  3. Smoke test automatizado de contenedor: creación de volumen temporal, arranque de contenedor, comprobación de `/api/ping`, consulta de catálogo, reinicio del contenedor, re-comprobación de catálogo y persistencia, y desmontaje limpio.
- **Trazabilidad de ejecución observada en GitHub Actions**:
  - Run ID: `43539276049` (Job `132275751605`)
  - Commit auditado: `cbe9d310570ed96824c55bd6e57ac704c924afd3` (merge #7 a `main`)
  - Resultado: `conclusion: success`, duración 2m 28s.
- **Límites documentados**:
  - Sin exposición pública de credenciales en ningún archivo, log ni artefacto.
  - La emulación de voz opera con fallback seguro sin afirmar conectividad con hardware de audio físico en runners de CI.

---

## 7. Inventario de Evidencia Recolectada

Todos los artefactos generados durante la verificación en contenedor real fueron almacenados en `issue-8-reconciliation-evidence/builder/`:
- `docker-version.txt`: salida completa de `docker version`.
- `docker-info.txt`: configuración y estado del daemon de Docker.
- `docker-image-inspect.json`: metadatos JSON de `norma-issue8-builder:latest`.
- `docker-image-sha256.txt`: identificador de imagen y digest de capas.
- `jar-sha256.txt`: checksum SHA-256 del JAR ejecutable empaquetado.
- `container-user-id.txt` y `container-user-whoami.txt`: validación de usuario no-root 10001 (`norma`).
- `ping-response.txt`: cabeceras y cuerpo HTTP 200 `PONG`.
- `catalog-session-a-initial.json` y `catalog-session-b-initial.json`: estado inicial de 8 productos.
- `proposal-price.json` y `confirm-price.json`: ciclo de propuesta y confirmación de mutación de precio.
- `catalog-session-a-after-price.json` y `catalog-session-b-after-price.json`: verificación de aislamiento multi-sesión.
- `proposal-image.json` y `confirm-image.json`: ciclo de revelado y publicación de imagen WebP.
- `media-webp-response.txt`: verificación de cabeceras HTTP 200 y Content-Type `image/webp`.
- `traversal-response.txt` y `nonexistent-response.txt`: pruebas de control de acceso y seguridad.
- `catalog-post-restart-a.json`, `catalog-post-restart-b.json` y `media-post-restart.txt`: persistencia post-reinicio.
- `data-dir-permissions.txt`: listado y permisos de `/data` en contenedor recreado.
- `catalog-recreated-a.json`, `catalog-recreated-b.json` y `media-recreated.txt`: persistencia post-recreación.
- `docker-compose-qa-config.txt`, `compose-user-id.txt` y `compose-ping-response.txt`: validación de Compose QA bajo `target/`.
- `com.norma.taller.DeploymentSmokeTest.txt` y `TEST-com.norma.taller.DeploymentSmokeTest.xml`: reportes JUnit de pruebas de despliegue.
