# Especificación de Diseño y Contrato Codex v2.1 — Conciliación Issue #8: Verificación Integral y Despliegue Reproducible (Adenda Correctiva)

**Versión**: 2.1 (Aprobada para Despacho Fase 2 Correctiva / Re-QA)  
**Fecha**: 13 de Noviembre de 2026  
**Autor**: Codex (Software Architect) — Registrado por Antigravity (Software Builder)  
**Issue de Referencia**: [#8: Verificación integral y despliegue reproducible](https://github.com/GonzaCarrillo/Norma-/issues/8)  
**Base**: commit `b921027` (QA Fase 3 sobre `0348d12`)  
**Rama de trabajo**: `GonzaCarrillo/reconcile-8-deployment`  
**Worktree**: `/home/gonzalocar/orca/workspaces/Norma-/reconcile-8-deployment`  

---

## 1. Resumen y Propósito de la Adenda v2.1

Durante la revisión arquitectónica de Fase 4 sobre la primera entrega de la Issue #8 (commit `0348d12`, auditado en `b921027`), Codex identificó observaciones específicas en el flujo de CI, la documentación operativa de backup, la precisión del identificador de grupo (GID) y el aislamiento de propiedades en `DeploymentSmokeTest.java`.

Esta adenda v2.1 formaliza los criterios de corrección aprobados para el despacho, estableciendo el estándar técnico requerido antes de someter los cambios a la nueva auditoría de QA independiente (Fase 3) por OpenCode / Nemotron.

---

## 2. Registro de Hallazgos y Decisiones Técnicas

### 2.1. Validación Estricta de Catálogo en CI mediante Parser JSON (No Grep Arbitrario)
- **Problema identificado**: El paso de smoke test en `.github/workflows/verify.yml` utilizaba un `grep` sobre una cadena arbitraria no perteneciente al catálogo base de Norma.
- **Decisión v2.1**: La verificación en CI debe consumir el endpoint `GET /api/products?sessionId=<id_valido>` utilizando un parser `python3` que valide estrictamente:
  1. La presencia exacta de 8 objetos JSON en el catálogo inicial.
  2. La existencia de todos los campos obligatorios del contrato (`pieceNumber`, `title`, `price`, `imageUrl`, `sold`) en cada producto.
  3. El valor esperado del catálogo base (ej. pieza #2 precio 18500).

### 2.2. Demostración de Persistencia de Datos Mutados en CI
- **Problema identificado**: El reinicio del contenedor en CI solo volvía a consultar el catálogo semilla, sin evidenciar que una mutación real sobreviviera al ciclo de vida del contenedor.
- **Decisión v2.1**: El script de CI debe ejecutar el ciclo conversacional completo:
  1. POST de propuesta de cambio de precio (`A la número 2 subila a veinte mil pesos`) con `sessionId` válido (`ci-session-valid-1`), comprobando estado `AWAITING_CONFIRMATION`.
  2. POST de cancelación (`No`), verificando retorno a estado `IDLE` y preservación del precio original (18500).
  3. POST de propuesta y confirmación explícita (`Sí`), comprobando estado `IDLE` y actualización efectiva a 20000.
  4. Reinicio del contenedor (`docker restart`), comprobando salud `/api/ping` y confirmando que la pieza #2 conserva su precio mutado (20000).
  5. Recreación del contenedor con el MISMO volumen (`docker stop`, `docker rm`, `docker run -v <volumen>:/data`), comprobando salud `/api/ping` y confirmando que la pieza #2 persiste en 20000.

### 2.3. Dinamismo de Puerto Host y Manejo Robusto en CI
- **Problema identificado**: Colisiones potenciales de puertos fijos en entornos concurrentes o de runners compartidos.
- **Decisión v2.1**:
  - Mapeo de puerto efímero `-p 0:8080` asignado por Docker.
  - Resolución dinámica mediante `docker port <cont> 8080`.
  - Re-evaluación obligatoria del puerto tras `docker restart`, ya que el demonio Docker reasigna el mapeo dinámico al reiniciar contenedores con puerto `0`.
  - Mecanismo de limpieza estricta mediante `trap cleanup EXIT` en Bash con `set -euo pipefail`, garantizando la destrucción de contenedores y volúmenes de prueba incluso ante fallos, y volcando `docker logs` para diagnóstico sin ejecutar `docker prune`.

### 2.4. Procedimiento Determinista de Backup sin Nombres Supuestos
- **Problema identificado**: La instrucción de respaldo anterior (`docker run -v norma-data:...`) montaba un volumen no calificado que difiere del volumen real nombrado por Docker Compose (prefijado con el nombre de proyecto, ej. `reconcile-8-deployment_norma-data`), produciendo respaldos vacíos.
- **Decisión v2.1**:
  - Detener escrituras activas para asegurar la consistencia del archivo SQLite y sus ficheros WAL (`norma.db-wal`, `norma.db-shm`):
    `docker compose stop taller`.
  - Copiar los datos directamente desde el contenedor del servicio a un directorio concreto nuevo:
    `docker compose cp taller:/data ./backups/norma-backup-$(date +%Y%m%d%H%M%S)`.
  - Reanudar el contenedor:
    `docker compose start taller`.
  - Verificar explícitamente que el directorio de respaldo contenga `norma.db` y `uploads/` antes de su archivado o utilización.
  - Alternativa: consultar el nombre calificado real mediante `docker inspect $(docker compose ps -q taller) --format '...'` en lugar de asumir nombres fijos.

### 2.5. Corrección de Documentación de UID y GID
- **Problema identificado**: La documentación anterior afirmaba `UID/GID 10001`, cuando en ejecución real el comando `useradd -r -u 10001 norma` sin `-g` asigna el siguiente GID de sistema libre en Ubuntu Noble JRE (observado: `gid=999(norma)`).
- **Decisión v2.1**: Documentar con precisión técnica `UID 10001 / GID observado 999 (norma)`, señalando expresamente que el `Dockerfile` no fija un GID rígido mediante `-g` y evitando promesas falsas de estabilidad de GID entre imágenes base.

### 2.6. Aislamiento Estricto en `DeploymentSmokeTest.java`
- **Problema identificado**: `DeploymentSmokeTest.java` heredaba variables de entorno del host y utilizaba el directorio de uploads local predeterminado.
- **Decisión v2.1**:
  - Anotar con `@TempDir static Path tempUploads` para proporcionar un directorio de subidas efímero bajo el ciclo de vida de JUnit.
  - Registrar `@DynamicPropertySource` que fije explícitamente `norma.uploads` al directorio temporal y `groq.api.key` a cadena vacía `""`, asegurando que la prueba de humo no dependa de credenciales externas ni altere el almacenamiento local.

### 2.7. Preservación de Trazabilidad Histórica
- **Regla obligatoria**: El informe de QA previo de Fase 3 (`docs/orchestration/issue-8-reconciliation-qa.md`) en el commit `b921027` no debe ser modificado, preservando la evidencia y el historial de auditoría del repositorio.

---

## 3. Alcance Cerrado de la Fase 2 Correctiva

- `.github/workflows/verify.yml`
- `docs/operacion.md`
- `README.md`
- `src/test/java/com/norma/taller/DeploymentSmokeTest.java`
- `docs/orchestration/issue-8-reconciliation-design-v2.1.md` (este documento)
- `docs/orchestration/issue-8-reconciliation-build.md`
- `issue-8-reconciliation-evidence/builder/`

---

## 4. Adenda v2.2: Ajuste de Timeout en Peticiones POST Conversacionales en CI

- **Observación arquitectónica de Codex**:
  En el flujo de CI (`.github/workflows/verify.yml`), las peticiones `POST /api/bot/message` utilizaban `--max-time 5`. No obstante, el backend (`MediaService.speak`) dispone de un tiempo de espera de hasta 12 segundos para la invocación asíncrona de `edge-tts`. En entornos de integración continua o corredores con contención de CPU, un límite de 5 segundos es excesivamente restrictivo y vulnerable a falsos positivos por timeout cliente.
- **Decisión de diseño v2.2**:
  - Incrementar el tiempo límite cliente exclusivamente en las cuatro peticiones `POST` a `/api/bot/message` a `--max-time 25`. Este umbral holgado absorbe el tiempo máximo de síntesis de voz (12s), la carga del proceso Python y la respuesta HTTP sobre hilos virtuales de Spring Boot.
  - Mantener los timeouts estrictos y reducidos en las consultas rápidas de salud (`GET /api/ping`, `--max-time 3`) y catálogo (`GET /api/products`, `--max-time 5`).
  - No se modifica código de producción, Dockerfile ni pruebas Java; el runtime permanece 100% idéntico al auditado por OpenCode en el commit `62f4115`.
