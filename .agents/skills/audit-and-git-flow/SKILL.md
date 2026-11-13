---
name: audit-and-git-flow
description: Auditar como Orca una entrega de Antigravity contra el diseño aprobado de Codex, verificar evidencia y commits atómicos en el worktree de Norma y cerrar una issue únicamente tras conformidad técnica.
---

# Auditar y gestionar Git en Norma

## Identificar el worktree antes de escribir

Leer `AGENTS.md`, incluida su regla de entorno. Ejecutar desde la raíz del workspace:

```bash
pwd -P
git rev-parse --show-toplevel
git branch --show-current
git worktree list --porcelain
printf 'ORCA_WORKTREE_PATH=%s\nORCA_WORKSPACE_NAME=%s\n' "${ORCA_WORKTREE_PATH:-}" "${ORCA_WORKSPACE_NAME:-}"
git status --short
git diff --stat
git diff --cached --stat
```

Si ORCA_WORKTREE_PATH está definido, resolver su ruta real y contrastar con pwd/raíz Git; una discrepancia detiene las escrituras hasta seleccionar el workspace correcto mediante Orca. Si está vacío, no inventar un path: usar `orca worktree current --json` mediante el ejecutable de Orca confirmado por la skill orca-cli y contrastar con Git. ORCA_WORKSPACE_NAME es un identificador del workspace; contrastar su configuración, no asumir que siempre es idéntico al nombre de rama.

No salir del directorio de trabajo para modificar archivos, hacer checkout a main ni mezclar ramas. Si la sesión ya está en main, registrar la situación y resolver el workspace/rama asignada por Orca antes de nuevos commits; no hacer un cambio de rama improvisado. Nunca `git reset --hard`, `git clean` o stash global para obtener un estado limpio. Archivos previos sin seguimiento no pertenecen automáticamente a la entrega.

## Auditoría de entrega

1. Leer diseño/aprobación y auditar el hash del builder, no un target mutable: `git show --stat HASH`, `git diff HASH^ HASH --` y `git diff HASH^ HASH --check`. Comparar la lista y los hunks con el scope exacto. Un archivo autorizado no permite cambios ajenos dentro de él (por ejemplo MediaConfig para facilitar mocks).
2. Verificar reglas relevantes: vidriera solo lectura; confirmación de mutaciones; undo único; sesión/DEFAULT aislados; contador no retrocede; JSON sin coerción; una transacción para snapshot/apply/limpieza. No introducir nuevos requisitos de idioma libre, arquitectura o SLA durante la revisión.
3. Contrastar contratos reales de `Api`, `Catalog`, `BotModel` y `BotSessionCodec` con diseño. Para datos/migración revisar constraints y triggers efectivos, no solo nombres. Para transacciones comprobar uso del proxy, no anotaciones en objetos creados con new.
4. Comprobar evidencia de `testing-and-qa`: log completo clean verify, exit code, JDK25/Maven efectivos, hash del commit/JAR, XML con basedir exportado y totales exactos. Local y exportación pueden diferir por archivos ajenos: nunca contarlos como si fueran parte del commit. No aceptar un JAR anterior a las clases corregidas ni reporte que mezcla hashes.
5. Reejecutar únicamente ante una brecha, fallo, contradicción o regresión relevante. Un total de pruebas verdes no demuestra todos los criterios. Para defectos reproducibles solicitar regresión conductual y corrección acotada a Antigravity. El diseño vuelve a Codex si la solución exige cambiar contratos.
6. Pedir a Codex revisión de conformidad y conservar auditoría versionada en `docs/orchestration/issue-N-audit.md`: PASS/FAIL por criterio, hash, evidencias, límites y hallazgos priorizados con reproducción. No escribir el arreglo de producción como Orca. Las correcciones se reasignan a Antigravity con el formato de `AGENTS.md` y gates exigidos.

## Commits, limpieza y cierre

- Antigravity commitea cambios autorizados en la rama actual antes de su entrega. Usar `git add -- archivo1 archivo2` con nombres reales del scope; revisar `git diff --cached` y `git diff --cached --check`; evitar `git add .`.
- Un commit debe describir un cambio revisable y referenciar `(#N)`; separar implementación y reporte posterior cuando el reporte depende del hash/mediciones. No usar `Closes #N` antes de auditoría PASS para evitar cierre automático al integrar.
- Verificar `git status --porcelain=v1` al final. Vacío significa worktree limpio; si contiene cambios preexistentes, documentarlos y contrastar diff/hashes iniciales/finales. No afirmar limpieza total ni borrar ajeno. Todos los cambios propios de la entrega deben estar commiteados; no incorporar documentos ajenos para satisfacer esa regla.
- No hacer push, merge o despliegue por inferir permiso de esta skill: ejecutar solo lo autorizado en la tarea/sesión. La rama puede contener commits locales todavía no publicados; declararlo para trazabilidad.
- Cuando Orca y Codex acepten todos los criterios y exista autorización de cierre, usar `gh issue close N --comment` con resumen concreto de hash/evidencia/límites. Para textos extensos usar archivo de cuerpo con los comandos gh que admiten `--body-file`; no interpolar texto como shell. Confirmar con `gh issue view N --json state,url`.
- Si el usuario autorizó resolver el backlog continuo, tomar automáticamente la siguiente dependencia tras el cierre; no solicitar permiso entre fases. Para agentes supervisados usar las skills orchestration/orca-cli y los mensajes dispatch, respetando sus recibos `next`; no sustituirlos por subagentes ni dirigir workers por terminal. Su disponibilidad no autoriza nuevos mensajes externos fuera de la tarea.
