# Especificación de Diseño y Contrato Codex v2 — Conciliación Issue #8: Verificación Integral y Despliegue Reproducible

**Versión**: 2.0 (Aprobada para Despacho Fase 2)  
**Fecha**: 13 de Noviembre de 2026  
**Autor**: Codex (Software Architect) — Registrado por Antigravity (Software Builder)  
**Issue de Referencia**: [#8: Verificación integral y despliegue reproducible](https://github.com/GonzaCarrillo/Norma-/issues/8)  
**Base unificada**: `origin/main` en commit `cbe9d31` (con Issues #4, #5, #6 y #7 cerradas y verificadas con 252 tests)  
**Rama de trabajo**: `GonzaCarrillo/reconcile-8-deployment`  
**Worktree**: `/home/gonzalocar/orca/workspaces/Norma-/reconcile-8-deployment`  

---

## 1. Resumen y Propósito de la Conciliación

La issue #8 tiene como objetivo verificar la integridad del monolito Java 25 / Spring Boot 3.5.16 y garantizar un despliegue en contenedor Docker completamente reproducible, seguro y persistente, demostrando la operatividad efectiva en ejecución real y no la mera existencia teórica de recetas de empaquetado.

El diseño preliminar v1.0 asumía erróneamente el cierre administrativo de la issue sin transitar formalmente por las fases de construcción (Fase 2) ni auditoría de control de calidad independiente (Fase 3). Este documento **supersede y anula dicha omisión**, reestableciendo el flujo mandatorio del protocolo de agentes:
1. Formalización del contrato de arquitectura y despliegue v2.
2. Fase 2 (Antigravity): compilación limpia bajo JDK 25, verificación estricta de `docker compose config`, construcción real de imagen Docker desde fuentes versionadas, ejecución de suite de validación en contenedor con volumen dedicado e inspección de persistencia tras reinicio y recreación, y documentación operativa.
3. Incorporación opcional y acotada de `DeploymentSmokeTest.java` para verificar de forma automatizada los contratos del endpoint `/api/ping` y arranque con catálogo accesible sin credenciales externas obligatorias.
4. Habilitación de auditoría independiente de Fase 3 (OpenCode / Nemotron) y dictamen arquitectónico final de Fase 4 (Codex).

---

## 2. Alcance Cerrado y Reglas de Modificación

El alcance de modificación para la Issue #8 queda estrictamente delimitado a los siguientes artefactos:

### 2.1. Archivos permitidos de infraestructura y empaquetado
- `Dockerfile`: construcción multi-etapa (etapa 1 con `maven:3.9-eclipse-temurin-25` y verificación con `mvn -B verify`; etapa 2 con `eclipse-temurin:25-jre-noble`, dependencias `libwebp7`, venv Python con `edge-tts==7.3.1`, usuario no-root `norma` UID 10001, volumen `/data` y variables `NORMA_DB=/data/norma.db` y `NORMA_UPLOADS=/data/uploads`).
- `compose.yaml`: servicio `taller`, mapeo de puerto configurable `${PORT:-8080}:8080`, volumen nombrado `norma-data:/data`, `GROQ_API_KEY` opcional, reinicio `unless-stopped` y `healthcheck` HTTP sobre `/api/ping`.
- `.dockerignore`: exclusión estricta de `.git`, `target`, `uploads`, `*.db*`, `.env`, `.agents`, `docs`, `issue-*` y logs para optimizar el contexto de construcción.
- `.github/workflows/verify.yml`: flujo CI de verificación de compilación Maven (`mvn -B verify`), construcción Docker (`docker build`) y smoke test de contenedor y persistencia SQLite en volumen temporal aislado.

### 2.2. Pruebas y Contratos de Despliegue
- `src/test/java/com/norma/taller/DeploymentSmokeTest.java` (NUEVO): prueba de integración MockMvc que valida formalmente el contrato de salud `GET /api/ping` respondiendo 200 con `PONG` y verificación de catálogo accesible sin credenciales.

### 2.3. Documentación y Evidencia
- `README.md`: actualización de guía de compilación, ejecución local y en contenedor Docker, y delimitación del alcance verificado.
- `docs/operacion.md`: incorporación de la sección de despliegue en contenedor, permisos, volumen `/data`, banderas JVM y límites operativos.
- `docs/orchestration/issue-8-reconciliation-design-v2.md` (este documento).
- `docs/orchestration/issue-8-reconciliation-build.md`: reporte técnico completo de construcción, ejecución en contenedor real y preservación de evidencia.
- `issue-8-reconciliation-evidence/builder/`: logs, hashes SHA-256, inspecciones Docker y transcripciones de peticiones HTTP.

### 2.4. Prohibición estricta de modificación
- Fuentes Java de producción (`src/main/java/**`).
- Código frontend, estilos y recursos estáticos (`src/main/resources/static/**`).
- Esquemas SQL, migraciones y datos base (`schema.sql`).
- Modelo relacional y dependencias de `pom.xml`.
- Pruebas existentes (`src/test/java/com/norma/taller/**` salvo la nueva clase `DeploymentSmokeTest.java`).
- Modificación de pertenencia a grupos de usuario o alteración de configuración global del sistema anfitrión.

---

## 3. Contrato Técnico de Contenedorización y Despliegue

### Cláusula 1: Monolito Ejecutable y Base de Ejecución
- Entorno de ejecución: Eclipse Temurin JRE 25 sobre Ubuntu 24.04 Noble.
- Flags JVM obligatorias en `ENTRYPOINT`:
  - `--enable-native-access=ALL-UNNAMED`: habilita acceso nativo seguro para SQLite JDBC.
  - `-XX:+UseCompactObjectHeaders`: optimización de cabeceras de objetos en 64-bit.
  - `-Xmx384m`: límite superior de heap acotado para entornos controlados.
- Hilos virtuales habilitados por configuración de Spring Boot (`spring.threads.virtual.enabled=true`).

### Cláusula 2: Seguridad y Usuario No Privilegiado
- Creación explícita del usuario no-root `norma` con UID y GID numéricos 10001 (`useradd -r -u 10001 norma`).
- El contenedor opera permanentemente bajo la directiva `USER norma`.
- Ningún archivo del runtime requiere privilegios de superusuario ni `sudo`.

### Cláusula 3: Persistencia y Gestión de Almacenamiento
- Montaje del volumen persistente sobre `/data`.
- Variable `NORMA_DB=/data/norma.db` y `NORMA_UPLOADS=/data/uploads`.
- El directorio `/data` es inicializado con permisos del usuario `norma` en la construcción de la imagen.
- Persistencia estricta: los datos de la base SQLite (modo WAL con archivos `-wal` y `-shm`) y las imágenes procesadas en WebP (`/data/uploads/<UUID>.webp`) deben sobrevivir inalterados a operaciones de reinicio (`docker restart`) y recreación de contenedores (`docker run -v <volumen>:/data`).

### Cláusula 4: Verificación de Salud (`healthcheck`)
- Endpoint de sondeo: `GET /api/ping`.
- Respuesta esperada: HTTP 200 OK con cuerpo exacto `PONG`.
- El sondeo se realiza vía script Python usando la librería estándar `urllib.request` con timeout de 3s, intervalo de 30s, timeout de 5s y 3 reintentos.

### Cláusula 5: Ausencia de Secretos y Configuración Opcional
- El sistema debe iniciar y operar con total funcionalidad de catálogo, persistencia y simulación sin necesidad de definir `GROQ_API_KEY`.
- Prohibición absoluta de credenciales o tokens quemados en código, archivos Compose, logs de Docker o repositorios.

---

## 4. Matriz de Pruebas en Contenedor Real (Protocolo de Auditoría)

| Paso | Verificación | Comando / Acción | Resultado Esperado |
|---|---|---|---|
| P1 | Usuario no-root | `docker exec <cont> id` | `uid=10001(norma)` |
| P2 | Comprobación de salud | `curl -i http://localhost:<port>/api/ping` | HTTP 200 OK, `PONG` |
| P3 | Catálogo inicial | `curl http://localhost:<port>/api/products?sessionId=sA` | 8 productos iniciales |
| P4 | Mutación de precio | POST propuesta + POST "Sí" en sesión A | Pieza #2 actualizada a $20.000 |
| P5 | Aislamiento multi-sesión | `curl http://localhost:<port>/api/products?sessionId=sB` | Pieza #2 en sesión B conserva $18.500 |
| P6 | Carga de imagen real | POST multipart con PNG real + POST "Sí" | Pieza #2 actualizada con `/media/<UUID>.webp` |
| P7 | Serving WebP | `curl -i http://localhost:<port>/media/<UUID>.webp` | HTTP 200 OK, `Content-Type: image/webp` |
| P8 | Seguridad path traversal | `curl -i http://localhost:<port>/media/../norma.db` | HTTP 500 / 404 (acceso bloqueado) |
| P9 | Persistencia tras reinicio | `docker restart <cont>` y re-verificación | Catálogo A ($20.000, WebP) y B ($18.500) intactos |
| P10 | Persistencia tras recreación | `docker stop`, `docker rm`, `docker run` con MISMO volumen | Catálogo A ($20.000, WebP), B intacto, permisos `/data` OK |
| P11 | Aislamiento Compose QA | `docker compose -f compose.yaml -f target/docker-compose.qa.yml up -d` | Puerto libre (18286), proyecto QA aislado, ping OK |
| P12 | Limpieza de prueba | Remoción exclusiva de contenedores y volúmenes de prueba | Recursos ajenos y del anfitrión intactos, sin `docker prune` |

---

## 5. Trazabilidad de CI y Límites Declarados

- **Distinción entre workflow presente y run observado**:
  - Workflow: `.github/workflows/verify.yml` incluye `mvn -B verify`, `docker build` y smoke test de contenedor/persistencia.
  - Run observado en GitHub Actions para el commit base `cbe9d31`: ID `43539276049` (Job `132275751605`), completado con éxito (`conclusion: success`) en 2m28s.
- **Límites técnicos documentados**:
  - No se declara despliegue en servidor público externo ni dominio en producción.
  - La síntesis de voz física local mediante fixture no certifica voz humana real sino emulación vía `edge-tts` o Web Speech API en navegador cliente.
