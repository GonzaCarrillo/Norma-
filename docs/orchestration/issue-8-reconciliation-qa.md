# Informe de Auditoría QA Independiente (Fase 3) — Issue #8: Conciliación Verificación Integral y Despliegue Reproducible

**Dictamen**: QA_APPROVED  
**Auditor**: OpenCode / Nemotron (Fase 3 QA & Test Auditor)  
**Fecha**: 13 de Noviembre de 2026  
**Rama de trabajo**: `GonzaCarrillo/reconcile-8-deployment`  
**Worktree**: `/home/gonzalocar/orca/workspaces/Norma-/reconcile-8-deployment`  
**SHA auditado**: `0348d125367903c3a4cedd5a43f85f3ee4a35d9d` (HEAD, commit `feat(#8): implementar verificación integral, contratos y despliegue reproducible Docker v2`)  
**Base unificada (diseño)**: `cbe9d31` (Issues #4, #5, #6 y #7 cerradas, 252 tests)  
**Diseño de referencia**: `docs/orchestration/issue-8-reconciliation-design-v2.md` (Codex v2, aprobado para despacho Fase 2)  
**Estado del checkout**: `git status --short` limpio (sin archivos sin seguimiento incorporados a la exportación)

---

## 1. Alcance y reglas aplicadas

- Auditoría de Fase 3 únicamente: build, suite completa de pruebas, regresión y validación de casos borde sobre el commit entregado por Antigravity. No se modificó producción, tests, contratos, esquemas ni configuración de builder.
- No se infiere "CI verde" por la mera existencia de `.github/workflows/verify.yml`; se verificó localmente.
- Escritura restringida a `docs/orchestration/issue-8-reconciliation-qa.md`, `issue-8-reconciliation-evidence/qa/` y `target/` (propio). No se tocó `docs/operacion.md`, `README.md`, Dockerfile, compose.yaml, `pom.xml` ni fuentes de producción.
- No se usó `sudo`, no se modificó el daemon Docker ni recursos ajenos. No se ejecutó `docker prune`. Contenedores/volúmenes/imagen QA con nombres únicos y eliminados tras la evidencia.
- Sin credenciales reales, sin STT/TTS externo, sin navegador (no requerido por este criterio), sin afirmación de despliegue público.

---

## 2. Entorno de ejecución verificado

| Elemento | Valor observado |
|---|---|
| SO anfitrión | Linux x86_64, Kernel 6.14.0-37-generic, Linux Mint 22.3 |
| JDK | Microsoft OpenJDK 25.0.4.1 LTS (`/home/gonzalocar/.jdks/ms-25.0.4.1`), bytecode 69 |
| JAVA_HOME | `/home/gonzalocar/.jdks/ms-25.0.4.1` |
| Maven | Apache Maven 3.8.7 |
| Docker Engine | 29.9.2 (API 1.56), containerd v2.4.2, runc 1.5.2, Compose v5.5.1 |
| `docker version` / `docker info` | target/qa-evidence/docker-version.txt, docker-info.txt (exit 0) |
| Puerto QA (1er contenedor) | `36352` (aleatorio, target/qa-evidence/qa-port.txt) |
| Puerto QA (recreado) | aleatorio (target/qa-evidence/qa-port2.txt) |
| Volumen QA | `norma-issue8-qa-data` (único, eliminado tras evidencia) |
| Imagen QA | `norma-issue8-qa:qa-local` |

---

## 3. Exportación limpia y compilación (sin archivos sin seguimiento)

Procedimiento de exportación limpia de `git archive HEAD` (no incorpora archivos sin seguimiento):

```bash
rm -rf target/qa-export && mkdir -p target/qa-export
git archive HEAD | tar -x -C target/qa-export
export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1
export PATH=$JAVA_HOME/bin:$PATH
cd target/qa-export
mvn -B clean verify   # exit code 0
```

**Resultado Maven**: `BUILD SUCCESS`, código de salida `0`.  
Log: `issue-8-reconciliation-evidence/qa/qa-maven-verify.log` (el archivo de log se volatilizó con `mvn clean` por escribirse dentro de `target/`; comando, exit code y conteos quedan registrados aquí).

| Métrica | Valor |
|---|---|
| Pruebas ejecutadas | 254 |
| Fallos | 0 |
| Errores | 0 |
| Omitidas | 0 |
| `DeploymentSmokeTest` | 2/2 (pingHealthcheckContract, deploymentStartsWithoutExternalCredentials) |
| Tiempo total | ~2 min 15 s |

**Hash JAR (reconstrucción independiente QA)**:  
`0ed1f43b47deaba57cf2a86c4480a9b0dd7ec915f46ea61e2d260feb7e5d9236  target/taller-1.0.0.jar`  
(`issue-8-reconciliation-evidence/qa/qa-jar-sha256.txt`)

**Trazabilidad vs builder**: el JAR de reconstrucción QA tiene hash distinto al registrado por el builder (`2dbba71a...`). Esperado: las compilaciones no son byte-reproducibles entre rutas/timestamps distintos; el origen es idéntico porque ambos parten de `git archive HEAD` del mismo SHA `0348d12`. El contenido de fuentes es el mismo; el hash del artefacto empaquetado no es criterio de aprobación.

---

## 4. Construcción Docker independiente desde la exportación

```bash
docker build -t norma-issue8-qa:qa-local -f target/qa-export/Dockerfile target/qa-export
```
**Exit code**: 0. La etapa `build` ejecutó `mvn -B verify` dentro de `maven:3.9-eclipse-temurin-25` (254 pruebas verdes en la imagen) y la etapa runtime empaquetó `eclipse-temurin:25-jre-noble` + `libwebp7` + venv `edge-tts==7.3.1` + usuario `norma` UID 10001.

| Elemento | Valor |
|---|---|
| Imagen QA | `norma-issue8-qa:qa-local` |
| Image ID | `sha256:ee8d6226ca7faf72887bdd68c7312c6e68bb2bbf9428fcfd75c772db8db7cb5e` |
| Tamaño | 604065006 bytes |
| Usuario configurado | `norma` |
| Entrypoint | `java --enable-native-access=ALL-UNNAMED -XX:+UseCompactObjectHeaders -Xmx384m -jar app.jar` |
| Variables | `NORMA_DB=/data/norma.db`, `NORMA_UPLOADS=/data/uploads` |
| Build log | `issue-8-reconciliation-evidence/qa/docker-build.log` |
| Imagen builder (referencia, no reutilizada) | `norma-issue8-builder:latest`, `sha256:5074f799...` |

La imagen QA es independiente (nombre, ID y digest distintos del builder); se construyó desde la exportación limpia del SHA auditado, no se reutilizó la imagen del builder.

---

## 5. Matriz de verificación en contenedor real (criterios del diseño)

Puerto efímero `36352` (1er contenedor `norma-issue8-qa-container`), volumen `norma-issue8-qa-data`, sin `GROQ_API_KEY` (arranque sin credenciales externas). Evidencia en `issue-8-reconciliation-evidence/qa/`.

| Paso | Verificación | Resultado observado | Estado |
|---|---|---|---|
| P1 | Usuario no-root (`docker exec id`) | `uid=10001(norma) gid=999(norma) groups=999(norma)` (`qa-user-id.txt`) | PASS |
| P2 | Healthcheck `GET /api/ping` | HTTP 200 OK, cuerpo `PONG` (`qa-ping-response.txt`) | PASS |
| P3 | Catálogo inicial 8 piezas | Sesión A: 8 productos; Sesión B: 8 productos (`qa-catalog-session-a-initial.json`, `-b-initial.json`) | PASS |
| P4 | Mutación de precio (FSM) | Sesión A: propuesta → `AWAITING_CONFIRMATION`, "sí" → `IDLE`, pieza #2 a $20.000 (`qa-proposal-price.json`, `qa-confirm-price.json`, `qa-catalog-session-a-after-price.json`) | PASS |
| P5 | Aislamiento multi-sesión | Sesión B: pieza #2 conserva $18.500 (`qa-catalog-session-b-after-price.json`) | PASS |
| P6 | Carga foto real + texto + "sí" | Multipart PNG + texto → `AWAITING_CONFIRMATION`, "sí" → `IDLE`, `imageUrl=/media/a70b6858-cf17-4581-bcc7-26167ef24f25.webp` (`qa-proposal-image.json`, `qa-confirm-image.json`, `qa-catalog-session-a-after-image.json`) | PASS |
| P7 | Serving WebP | `GET /media/<uuid>.webp` → HTTP 200, `Content-Type: image/webp`, 946 bytes (`qa-media-webp-response.txt`) | PASS |
| P8 | Path traversal bloqueado | `GET /media/../norma.db` → HTTP 500, sin exponer ruta interna (`qa-traversal-response.txt`) | PASS |
| P9 | Persistencia tras `docker restart` | `/api/ping` PONG; Sesión A $20.000 + WebP intactos; Sesión B $18.500 intacto (`qa-ping-post-restart.txt`, `qa-catalog-post-restart-a.json`, `-b.json`, `qa-media-post-restart.txt`) | PASS |
| P10 | Persistencia tras recreación con MISMO volumen | `docker stop`+`docker rm`+`docker run -v norma-issue8-qa-data:/data`; nuevo contenedor `norma-issue8-qa-recreated`; Sesión A $20.000+WebP, Sesión B $18.500, permisos `/data` `norma:norma` (`qa2-catalog-recreated-a.json`, `-b.json`, `qa2-data-perms.txt`, `qa2-user-id.txt`) | PASS |
| — | Cancelación | Propuesta luego "no" → `IDLE`, "Cancelé el cambio", pieza #2 en B se mantiene $18.500 (`qa-cancel-proposal.json`, `qa-cancel-no.json`, `qa-catalog-session-b-after-cancel.json`) | PASS |
| — | Archivo inexistente | `GET /media/non-existent-file.webp` → HTTP 404 genérico (`qa-nonexistent-response.txt`) | PASS |

**Healthcheck**: el `healthcheck` de `compose.yaml` apunta a `GET /api/ping` (Python `urllib.request`, timeout 3s) esperando HTTP 200 `PONG`. Verificado funcionalmente mediante `curl` contra el endpoint real del contenedor (PONG, HTTP 200). El contenedor se ejecutó con `docker run` (puedo/volumen QA únicos); el contrato del healthcheck queda demostrado por la respuesta del endpoint.

**Permisos y datos**: `/data` contiene `norma.db`, `norma.db-shm`, `norma.db-wal` y `uploads/`, todos propiedad `norma:norma` (`qa2-data-perms.txt`). Volumen eliminado tras la evidencia; no se ejecutó `docker prune`.

---

## 6. Limpieza de recursos QA

Tras la evidencia se eliminaron exclusivamente:
- Contenedor `norma-issue8-qa-recreated` (`docker rm -f`)
- Volumen `norma-issue8-qa-data` (`docker volume rm`)

El contenedor `norma-issue8-qa-container` ya había sido `docker rm` previo a la recreación. No se usó `docker prune`. Imagen `norma-issue8-qa:qa-local` conservada como evidencia de trazabilidad (no es recurso de runtime compartido). Recursos ajenos del anfitrión (imágenes/contenedores de otros proyectos) intactos.

---

## 7. Limitaciones observadas

- El archivo de log completo de Maven (`qa-maven-verify.log`) no se conserva como artefacto porque `mvn clean` eliminó `target/` (donde se escribía vía `tee`) al inicio de la ejecución; quedan registrados comando, exit code `0`, `BUILD SUCCESS` y los conteos de Surefire (254/0/0/0).
- La reconstrucción QA del JAR tiene hash distinto al del builder (`0ed1f43b...` vs `2dbba71a...`): no se afirma reproducibilidad byte-a-byte del empaquetado, únicamente identidad de fuentes (`git archive HEAD` del mismo SHA) y veredicto de suite verde.
- No se ejecutó `docker compose` para el ciclo de vida principal (se usó `docker run` con nombres QA únicos); el healthcheck se validó contra el endpoint real `/api/ping`, que es exactamente lo que comprueba el healthcheck de Compose. No se afirma despliegue en servidor público ni dominio de producción.
- No se requiere navegador para este criterio (issue #8 se centra en backend/contenedor); no se abrió browser.

---

## 8. Veredicto

Todos los criterios de la matriz del diseño v2 (P1–P10), el arranque sin `GROQ_API_KEY`, la confirmación y cancelación de la FSM, el aislamiento de sesión B, la carga y revelado de foto WebP real, el serving de medios, el bloqueo de traversal, la persistencia tras reinicio y recreación sobre el mismo volumen, y el usuario no-root UID 10001, fueron comprobados contra el contenedor real construido desde la exportación limpia del SHA `0348d12`. Suite de pruebas: 254/254, exit code 0.

**QA_APPROVED**. El informe y la evidencia quedan en:
- `docs/orchestration/issue-8-reconciliation-qa.md`
- `issue-8-reconciliation-evidence/qa/`

Este dictamen habilita la Fase 4 de Codex (aprobación arquitectónica final). No implica merge, push a main ni cierre de issue por QA.
