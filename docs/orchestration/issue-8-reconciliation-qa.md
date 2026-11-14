# Informe de Re-Auditoría QA Independiente (Fase 3) — Issue #8: Corrección Codex v2.1

**Dictamen**: QA_APPROVED  
**Auditor**: OpenCode / Nemotron (Fase 3 QA & Test Auditor)  
**Fecha**: 13 de Noviembre de 2026  
**Rama de trabajo**: `GonzaCarrillo/reconcile-8-deployment`  
**Worktree**: `/home/gonzalocar/orca/workspaces/Norma-/reconcile-8-deployment`  
**SHA re-auditado**: `10f50540d4cf53ec937d86fac2cbe9c606ecb700` (HEAD, commit `fix(#8): corregir hallazgos Codex v2.1 en smoke test CI, backup determinista y aislamiento`)  
**Base anterior**: `b921027` (QA Fase 3 QA_APPROVED sobre `0348d12`, preservado sin modificar)  
**Diseño de referencia**: `docs/orchestration/issue-8-reconciliation-design-v2.1.md` (Codex v2.1, aprobado para despacho Fase 2 correctiva / Re-QA)  
**Estado del checkout**: `git status --short` limpio

---

## 1. Alcance y reglas aplicadas

- Re-auditoría de Fase 3 sobre la entrega corregida de Antigravity (commit `10f5054`). No se modificó producción, tests, esquemas ni configuración global del anfitrión.
- No se infiere "CI verde" por la mera existencia de `.github/workflows/verify.yml`; se ejecutó el smoke test de contenedor real y se verificó el healthcheck de Compose mediante `State.Health.Status`.
- Escritura restringida a `docs/orchestration/issue-8-reconciliation-qa.md`, `issue-8-reconciliation-evidence/qa/` y `target/` (propio).
- No se usó `sudo`, no se modificó el daemon Docker ni recursos ajenos. No se ejecutó `docker prune`. Sólo se eliminaron contenedor/volumen/red propios de QA.
- Sin credenciales reales, sin STT/TTS externo, sin navegador, sin afirmación de despliegue público.
- **Preservación histórica (design v2.1 §2.7)**: el informe de QA previo en el commit `b921027` NO fue modificado; queda como evidencia histórica en el repositorio. Este informe es la re-auditoría del nuevo SHA `10f5054`.

---

## 2. Entorno de ejecución verificado

| Elemento | Valor observado |
|---|---|
| SO anfitrión | Linux x86_64, Kernel 6.14.0-37-generic, Linux Mint 22.3 |
| JDK | Microsoft OpenJDK 25.0.4.1 LTS (`/home/gonzalocar/.jdks/ms-25.0.4.1`), bytecode 69 |
| JAVA_HOME | `/home/gonzalocar/.jdks/ms-25.0.4.1` |
| Maven | Apache Maven 3.8.7 |
| Docker Engine | 29.9.2 (API 1.56), containerd v2.4.2, runc 1.5.2, Compose v5.5.1 |
| Puerto smoke CI (docker run) | aleatorio, `target/qa2-port.txt` |
| Puerto Compose QA | `34094` (`target/qa2-compose-port.txt`) |
| Volumen Compose QA | `norma-qa2-reaudit_norma-qa2-reaudit-data` (propio, eliminado tras evidencia) |
| Imagen QA | `norma-issue8-qa2:re-audit` |

---

## 3. Exportación limpia y compilación (log fuera de `target/`)

Procedimiento de exportación limpia de `git archive HEAD` (no incorpora archivos sin seguimiento):

```bash
rm -rf target/qa-reexport && mkdir -p target/qa-reexport
git archive HEAD | tar -x -C target/qa-reexport
export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1
export PATH=$JAVA_HOME/bin:$PATH
cd target/qa-reexport
mvn -B clean verify   # exit code 0
```

El log de Maven se guardó en el **padre del export** (`target/qa-reexport-maven.log`), no dentro de `target/` (que `mvn clean` elimina), tal como exige el diseño v2.1.

**Resultado Maven**: `BUILD SUCCESS`, código de salida `0`.  
Log: `issue-8-reconciliation-evidence/qa/qa-reexport-maven.log`.

| Métrica | Valor |
|---|---|
| Pruebas ejecutadas | 254 |
| Fallos | 0 |
| Errores | 0 |
| Omitidas | 0 |
| `DeploymentSmokeTest` | 2/2 (`pingHealthcheckContract`, `deploymentStartsWithoutExternalCredentials`) |
| Tiempo total | ~4 min 28 s |

**Hash JAR (reconstrucción independiente QA)**:  
`6880ac4246f546a81263bb4c570ccb755aec491c036e99b4b54b33a13059626c  target/taller-1.0.0.jar`  
(`issue-8-reconciliation-evidence/qa/qa-reexport-jar-sha256.txt`)

---

## 4. Construcción Docker independiente desde la exportación

```bash
docker build -t norma-issue8-qa2:re-audit -f target/qa-reexport/Dockerfile target/qa-reexport
```

**Exit code**: 0. La etapa `build` ejecutó `mvn -B verify` (254 pruebas verdes dentro de la imagen) y la etapa runtime empaquetó `eclipse-temurin:25-jre-noble` + `libwebp7` + venv `edge-tts==7.3.1` + usuario `norma`.

| Elemento | Valor |
|---|---|
| Imagen QA | `norma-issue8-qa2:re-audit` |
| Image ID / manifest digest | `ffd20b74cc71892d561141219371c78a3116242faf3b0691140fc29bb037b31f` |
| Tamaño | 604 MB |
| Build log | `issue-8-reconciliation-evidence/qa/qa-reexport-docker-build.log` |
| Usuario configurado | `norma` |

La imagen QA es independiente (nombre y digest distintos de cualquier imagen previa) y se construyó desde la exportación limpia del SHA `10f5054`. No se reutilizó una imagen ajena sin verificar.

---

## 5. Correcciones verificadas del diseño v2.1

### 5.1. `DeploymentSmokeTest.java` — aislamiento estricto
Verificado el fuente actualizado: usa `@TempDir static Path tempUploads` y `@DynamicPropertySource` inyectando `norma.uploads` al directorio temporal de JUnit y `groq.api.key=""`. La prueba no depende de variables del host ni altera el almacenamiento local. Resultado: 2/2 pruebas verdes.

### 5.2. Smoke test de CI (`verify.yml`) — parser JSON y ciclo conversacional
El workflow fue corregido para:
- Consumir `GET /api/products?sessionId=ci-session-valid-1` con parser `python3`, validando estrictamente 8 productos, todos los campos obligatorios (`pieceNumber`, `title`, `price`, `imageUrl`, `sold`) y precio base de pieza #2 = 18500.
- Ciclo conversacional completo: propuesta (`AWAITING_CONFIRMATION`), cancelación con `No` (`IDLE`, precio conserva 18500), propuesta y confirmación con `Sí` (`IDLE`, precio mutado a 20000).
- Persistencia de datos mutados tras `docker restart` (pieza #2 = 20000) y tras recreación de contenedor con el MISMO volumen (pieza #2 = 20000).
- Puerto dinámico `-p 0:8080` con re-evaluación tras `docker restart`.
- Limpieza con `trap cleanup EXIT` que vuelca logs ante fallo y elimina sólo contenedores/volúmenes de prueba (sin `docker prune`).

Verificado ejecutando el flujo real contra el contenedor (evidencia `qa2-*`): todos los pasos PASS.

### 5.3. Backup determinista (`docs/operacion.md`)
Verificado el procedimiento corregido: `docker compose stop taller` → `docker compose cp taller:/data <dir-backup>` → `docker compose start taller`, con verificación de contenido (`norma.db` y `uploads/`) y alternativa mediante `docker inspect` del nombre calificado real del volumen (no se asume `norma-data` no calificado).

### 5.4. Documentación de UID/GID
Verificado que la documentación declara correctamente `UID 10001 / GID observado 999 (norma)`, señalando que el `Dockerfile` no fija GID rígido con `-g`. En ejecución real se observó `uid=10001(norma) gid=999(norma)`.

---

## 6. Matriz de verificación en contenedor real y Compose

### 6.1. Smoke CI real (docker run, puerto dinámico, trap cleanup) — exit 0

| Paso | Verificación | Resultado observado | Estado |
|---|---|---|---|
| C1 | Catálogo inicial (parser JSON) | 8 productos, campos obligatorios, pieza #2 = 18500 | PASS |
| C2 | Propuesta de mutación de precio | Estado `AWAITING_CONFIRMATION` | PASS |
| C3 | Cancelación ("No") | Estado `IDLE`, pieza #2 conserva 18500 | PASS |
| C4 | Propuesta + confirmación ("Sí") | Estado `IDLE`, pieza #2 = 20000 | PASS |
| C5 | Persistencia tras `docker restart` | Puerto re-evaluado, pieza #2 = 20000, ping PONG | PASS |
| C6 | Persistencia tras recreación con MISMO volumen | Nuevo contenedor, pieza #2 = 20000 | PASS |
| C7 | Limpieza `trap cleanup EXIT` | Contenedores y volumen removidos, sin `docker prune` | PASS |

Evidencia: `qa2-catalog-init.json`, `qa2-proposal-price.json`, `qa2-cancel-no.json`, `qa2-catalog-cancel.json`, `qa2-proposal2-price.json`, `qa2-confirm-price.json`, `qa2-catalog-confirmed.json`, `qa2-catalog-restart.json`, `qa2-catalog-recreated.json`, `qa-smoke-ci.sh`.

### 6.2. Compose QA real (proyecto/volumen propios) — `State.Health.Status=healthy`

Ejecutado con `docker compose -p norma-qa2-reaudit -f compose.yaml -f target/qa-compose-override.yml up -d` (imagen pre-verificada `norma-issue8-qa2:re-audit`, puerto `34094`, volumen `norma-qa2-reaudit_norma-qa2-reaudit-data`).

| Verificación | Resultado observado | Estado |
|---|---|---|
| `State.Health.Status` (docker inspect) | `healthy` (intento 8 de polling) | PASS |
| Endpoint `GET /api/ping` | HTTP 200, cuerpo `PONG` | PASS |
| Usuario no-root (`docker exec id`) | `uid=10001(norma) gid=999(norma)` | PASS |
| Multipart foto real + texto + "Sí" | `AWAITING_CONFIRMATION` → `IDLE`, `imageUrl=/media/<uuid>.webp` | PASS |
| Serving WebP | HTTP 200, `Content-Type: image/webp` | PASS |
| Persistencia WebP tras `docker restart` | Mismo URL de imagen, HTTP 200 `image/webp` tras restart | PASS |
| `/data` permisos | `norma.db`, `-wal`, `-shm`, `uploads/` propiedad `norma:norma` | PASS |
| Limpieza `down -v` | Contenedor, red y volumen propios removidos | PASS |

Evidencia: `qa2-ping-response.txt`, `qa2-health-status.txt` (direct-run: none; compose: healthy), `qa2-compose-user-id.txt`, `qa2-data-perms.txt`, `qa2-proposal-image.json`, `qa2-confirm-image.json`, `qa2-catalog-a-after.json`, `qa2-media-webp.txt`, `qa2-catalog-a-restart.json`, `qa2-compose-container-id.txt`, `qa2-compose-port.txt`, `qa2-compose-override.yml`.

---

## 7. Re-auditoría de traversal (--path-as-is y encoded)

Se re-auditará con precisión la seguridad contra path traversal. **No se llama 404 a 500**: se clasifica exactamente el status observado, distinguiendo "bloqueado" de "status inválido".

| Prueba | Request | Status observado | Clasificación | Estado |
|---|---|---|---|---|
| Traversal `--path-as-is` | `GET /media/../norma.db` | HTTP 404 | Bloqueado: Tomcat normaliza `../` y el recurso no existe; no se filtra `norma.db` | PASS (bloqueado, 404 no es 500) |
| Traversal encoded | `GET /media/%2e%2e%2fnorma.db` | HTTP 400 | Bloqueado: conector Tomcat rechaza el recorrido de ruta codificado | PASS (bloqueado, 400 no es 500) |
| Archivo inexistente | `GET /media/non-existent.webp` | HTTP 404 | Respuesta genérica 404, sin fuga interna | PASS |

Conclusión de seguridad: el traversal está **bloqueado** en ambas variantes (no se accede a `norma.db` ni se revela ruta interna). Los códigos son 404 y 400 respectivamente, nunca 500; se informan con su valor real. No hay defecto de alcance que reportar.

Evidencia: `qa2-traversal-pasi.txt` (404), `qa2-traversal-enc.txt` (400), `qa2-nonexistent.txt` (404).

---

## 8. Ausencia de claves y límites

- El sistema arranca y opera sin `GROQ_API_KEY` (cadena vacía en `DeploymentSmokeTest` y en el contenedor).
- No hay credenciales quemadas en código, logs ni artefactos de QA.
- No se declara despliegue en servidor público externo ni dominio de producción.

---

## 9. Limpieza de recursos QA

Tras la evidencia se eliminaron exclusivamente recursos propios:
- Contenedor Compose `norma-qa2-reaudit-taller-1` (`docker compose ... down -v`)
- Red `norma-qa2-reaudit_default`
- Volumen `norma-qa2-reaudit_norma-qa2-reaudit-data`
- Los contenedores del smoke CI directo fueron removidos por `trap cleanup EXIT`

No se ejecutó `docker prune`. Recursos ajenos del anfitrión intactos. La imagen `norma-issue8-qa2:re-audit` se conserva como evidencia de trazabilidad de construcción (no es recurso de runtime compartido).

---

## 10. Veredicto

Todos los criterios del diseño v2.1 fueron comprobados contra el SHA `10f5054`:
- Exportación limpia y compilación JDK 25: `BUILD SUCCESS`, exit 0, **254/254 pruebas**, hash JAR registrado.
- Imagen Docker construida desde la exportación limpia (254 pruebas verdes dentro de la imagen), ID/digest registrados.
- `DeploymentSmokeTest` aislado (`@TempDir`, `@DynamicPropertySource`, `groq.api.key=""`).
- Smoke test de CI real: parser JSON de 8 productos/campos, ciclo propuesta/cancel/confirm, persistencia de mutación tras restart y recreación mismo volumen, puerto dinámico, `trap cleanup` propio.
- Compose QA real: `State.Health.Status=healthy`, ping PONG, nonroot 10001/999, multipart foto + texto + sí, WebP `image/webp`, persistencia de medios tras restart, `/data` `norma:norma`, limpieza `down -v`.
- Traversal re-auditado con precisión: bloqueado (404 `--path-as-is`, 400 encoded), sin llamar 404 a 500, sin fuga de `norma.db`.
- Sin claves externas; sin afirmación de despliegue público.

---

## 11. Delta v2.2 vs QA aprobada `10f5054`/`62f4115` (commit `7b4efea`)

**SHA auditado**: `7b4efea1f1861275a1d83f5654ac9ab0c71becd3`  
**Base QA previa**: `10f5054` (informe `62f4115`, QA_APPROVED, preservado íntegro)  
**Adenda de diseño**: `docs/orchestration/issue-8-reconciliation-design-v2.1.md` §4 (v2.2) y `docs/orchestration/issue-8-reconciliation-build.md` §9.

### 11.1. Diff de fuentes y configuración respecto a `10f5054`

`git diff 10f5054 7b4efea` (excluyendo evidencia y `target`) produjo exactamente 3 archivos, todos de documentación/flujo CI:
- `.github/workflows/verify.yml`
- `docs/orchestration/issue-8-reconciliation-build.md`
- `docs/orchestration/issue-8-reconciliation-design-v2.1.md`

**No hubo cambios** en `src/`, `pom.xml`, `Dockerfile`, `compose.yaml`, `.gitignore` ni pruebas Java. El runtime, las dependencias y los 254 tests ya auditados en `10f5054` permanecen al 100% idénticos.

### 11.2. Cambio único en `verify.yml` (v2.2)

Se incrementó `--max-time 5 → 25` **exclusivamente** en las 4 peticiones `POST /api/bot/message` del smoke test de CI (propuesta inicial, cancelación con `No`, propuesta reiterada y confirmación con `Sí`), para absorber el timeout interno de 12 s de `MediaService.speak` (edge-tts) y evitar falsos positivos por timeout cliente en runners con contención.

Se mantuvieron intactos los timeouts acotados de las consultas rápidas:
- `GET /api/ping`: `--max-time 3`
- `GET /api/products`: `--max-time 5`

No se modificaron asserts, puertos, volúmenes ni el cuerpo de las peticiones.

### 11.3. Reejecución del smoke CI corregido v2.2

No se reejecutó la suite de 254 pruebas (sin causa: fuentes Java idénticas a `10f5054`). Se reejecutó **únicamente** el smoke test de contenedor corregido contra la imagen QA independiente ya construida desde `10f5054`:

| Elemento | Valor |
|---|---|
| Imagen QA | `norma-issue8-qa2:re-audit` |
| Image ID / manifest digest | `ffd20b74cc71892d561141219371c78a3116242faf3b0691140fc29bb037b31f` |
| Origen | construida desde `git archive HEAD` de `10f5054` (fuentes idénticas a `7b4efea` en runtime) |
| Puerto | dinámico (`-p 0:8080`, re-evaluado tras restart) |
| Volumen | propio `norma-qa2-vol-re-audit-v22-<id>` (eliminado tras evidencia) |
| Script | `target/qa-smoke-ci-v22.sh` (sintaxis Bash verificada con `bash -n`) |
| Log/exitcode | `issue-8-reconciliation-evidence/qa/v2.2/qa-v22-summary.txt` (`SCRIPT_EXIT=0`) |

Resultado: `SCRIPT_EXIT=0`. Todos los criterios del smoke CI v2.1 se conservaron:
- Catálogo inicial: 8 productos, campos obligatorios, pieza #2 = 18500.
- Propuesta → `AWAITING_CONFIRMATION`; cancelación `No` → `IDLE`, pieza #2 conserva 18500.
- Propuesta + confirmación `Sí` → `IDLE`, pieza #2 = 20000.
- Persistencia mutada tras `docker restart` (pieza #2 = 20000, ping PONG).
- Persistencia tras recreación con **mismo volumen** (pieza #2 = 20000).
- Usuario no-root `uid=10001(norma) gid=999(norma)`; `/data` propiedad `norma:norma`.
- Traversal bloqueado (preciso): `--path-as-is` → HTTP 404, encoded `%2e%2e%2f` → HTTP 400 (no se llama 404 a 500, sin fuga de `norma.db`).
- WebP servido con `Content-Type: image/webp`.
- Limpieza propia de contenedor y volumen (`trap cleanup EXIT`), sin `docker prune`.

### 11.4. Trazabilidad de imagen

La imagen `norma-issue8-qa2:re-audit` (`ffd20b74...`) se construyó en la auditoría previa sobre `10f5054` desde `git archive HEAD`. Como `7b4efea` no altera `src/`, `pom.xml`, `Dockerfile` ni `compose.yaml`, la imagen es ejecutablemente equivalente y trazable a la entrega ahora auditada. No se reconstruyó innecesariamente.

### 11.5. Límites declarados

- No se confunde la ejecución local de CI con GitHub Actions ya ejecutado.
- No se declara despliegue en servidor público ni dominio de producción.
- No se modificó producción, tests, Dockerfile ni configuración global del anfitrión.
- No se usaron rutas `..` para evidencia fuera del checkout; todas las rutas de evidencia son absolutas bajo el worktree.

---

## 12. Veredicto final (delta `7b4efea`)

El delta de `7b4efea` frente a la QA aprobada `10f5054`/`62f4115` se limita al ajuste de timeout `--max-time 25` en 4 peticiones POST conversacionales de CI (v2.2), con fuentes/runtime/tests/idénticas y smoke CI corregido ejecutado con éxito (exit 0) sobre imagen QA independiente trazable.

**QA_APPROVED** sobre `7b4efea`, con correspondencia íntegra a la suite Docker y los criterios de la QA previa. El informe y la evidencia v2.2 quedan en:
- `docs/orchestration/issue-8-reconciliation-qa.md`
- `issue-8-reconciliation-evidence/qa/v2.2/`

Este dictamen habilita la Fase 4 de Codex (aprobación arquitectónica final). No implica merge, push a main ni cierre de issue por QA. Los informes previos (`62f4115` sobre `10f5054` y `b921027` sobre `0348d12`) permanecen inalterados como evidencia histórica.
