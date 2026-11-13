# Aprobación Arquitectónica — Issue #4: Fotografías y medios de demostración (Acting Software Architect — Modo Contingencia)

**Rol:** OpenCode / Nemotron — Software Architect interino (Codex PAUSADO/STANDBY, AGENTS.override.md vigente)
**Worktree / rama:** `GonzaCarrillo/issue-4-media` — `/home/gonzalocar/orca/workspaces/Norma-/issue-4-media`
**SHA auditado (builder Antigravity):** `830d709` (HEAD de la rama; commit final `4efe03a` revalidado por QA)
**Diseño de referencia:** `docs/orchestration/issue-4-design.md` v1.0 (base `e2b6f524ec8a09b2f12b0b12237b3920addcc01e`), aprobado por Orca
**Registro de aprobación builder:** `docs/orchestration/issue-4-approval.md` (Gates B1, B2, B3)
**Informe QA (OpenCode):** `docs/orchestration/issue-4-qa-revalidacion.md` sobre commit `4efe03a` — **QA_APPROVED**
**Fecha:** 2026-11-13

---

## 1. Veredicto arquitectónico

**CONFORMIDAD: PASS.** El commit final `830d709` de la rama `GonzaCarrillo/issue-4-media` es conforme al diseño aprobado v1.0, respeta el scope autorizado (§8 del diseño), no introduce deuda técnica y no colisiona con ningún archivo, entidad, migración o configuración global de issue #6 ni de otras issues activas.

La implementación de Antigravity cumple todos los requisitos del diseño:

| Requisito del diseño | Estado en la implementación |
|---|---|
| `PhotoProcessor` package-private, sin framework/SQL/red | Implementado (703 líneas), costuras `WebPEncoder`/`FileMover` inyectables |
| Delegación `MediaService.image` a `PhotoProcessor` | `MediaService` mantiene constructor/root/transcribe/speak intactos |
| Allowlist JPEG/PNG8/WebP estáticos; rechazo HEIC/animados/CMYK/PNG16 | Implementado en `analyzeContainer`/`isFormatAllowed`/`parse*` |
| Límites cámara: 15 MiB, eje ≤16384, producto ≤24 MP (long) | Implementados antes de `read(0)` |
| EXIF IFD0 orientaciones 1–8, `metadata-extractor` 2.19.0 | Implementado con `ExifReader`, rechazo de metadatos corruptos/excesivos |
| Recorte 4:3 `k=min(floor(Wo/4),floor(Ho/3))`, origen centrado | Implementado en `transformAndReveal`, tabla EXIF 1–8 respetada |
| Revelado determinista: RGB8 sobre #FFFFFF, corrección sutil exacta | Implementado: `R'=clamp(round(R*1.03*1.01))`, `G'=clamp(round(G*1.03))`, `B'=clamp(round(B*1.03*0.99))` |
| WebP lossy, 8 calidades [80..10]/100f, éxito 0<bytes<80000 | Implementado en `DefaultWebPEncoder` + `encodeAndPublish` |
| Slot único `Semaphore(1)`, 500 por ocupación sin cola | Implementado en `process` |
| Staging `.processing/`, `ATOMIC_MOVE`, UUID sin nombre de cliente | Implementado; `AtomicMoveNotSupportedException` → 500 sin fallback |
| Serving `/media/**`: 200+MIME, 404 staging/traversal/symlinks | `MediaConfig` PathResourceResolver + `ApiErrors` 404 limitado a `/media/**` |
| `docs/medios.md` matriz de procedencia por recurso | Completada (8 WebP + 4 MP3, hash, fuente, licencia, transforms) |
| Regresión #1–#3 intacta | 130 pruebas previas verdes, `clean verify` completo |

---

## 2. Alcance revisado y frontera respetada

Archivos modificados en la rama (diff vs `main`):

- `docs/decisiones.md` — decisiones #4 + corrección estado histórico #2/#3 (ADR preservados)
- `docs/medios.md` — matriz de procedencia individual por recurso (§7)
- `docs/operacion.md` — contrato de imagen, staging, atomic move, limpieza limitada
- `docs/orchestration/issue-4-approval.md` — registro de gates Orca
- `docs/orchestration/issue-4-build-report.md` — entorno, comandos, resultados, evidencias
- `docs/orchestration/issue-4-design.md` — diseño de referencia (v1.0)
- `docs/orchestration/issue-4-qa.md` — informe QA previo
- `docs/orchestration/issue-4-qa-revalidacion.md` — revalidación QA + revisión arquitectónica
- `pom.xml` — únicamente `metadata-extractor:2.19.0`
- `src/main/java/com/norma/taller/Api.java` — sólo `MediaConfig` resolver privado/MIME + `ApiErrors` 404 `/media`; endpoints intactos
- `src/main/java/com/norma/taller/MediaService.java` — delegación a `PhotoProcessor`, root seguro
- `src/main/java/com/norma/taller/PhotoProcessor.java` (nuevo) — pipeline completo
- `src/test/java/com/norma/taller/DemoMediaTest.java` (nuevo) — 8 WebP + 4 MP3
- `src/test/java/com/norma/taller/MediaHttpTest.java` (nuevo) — HTTP real multipart/serving
- `src/test/java/com/norma/taller/PhotoProcessorTest.java` (nuevo) — geometría, validación, EXIF, revelado, compresión, concurrencia

**Frontera respetada:** No se modificó `BotEngine`, `Catalog`, `Product`/`Session`/`Reply`, `BotModel`, `IntentParser`, `BotSessionCodec`, SQLite v2, `schema.sql`, `seed.sql`, `migrate-v0-v2.sql`, `CatalogDatabaseInitializer`, `application.properties` (límites 15/20MB conservados), assets binarios existentes, `index.html`/`style.css`/`app.js`, ni pruebas existentes. No se introdujeron autenticación, colas, nuevos servicios, ImageMagick ni IA.

---

## 3. Conformidad contra matriz de aceptación §9

Todos los criterios V1, V2, L1, L2, O1, G1, C1, W1, W2, F1, F2, H1, A1, A2, D1, D-PROCEDENCIA, R1 verificados como **PASS** por QA independiente sobre `4efe03a` (175/175 pruebas, exit code 0, JDK 25 documentado, `BUILD SUCCESS`).

Observación no bloqueante registrada: atribución de fotógrafo para pieza-2 y pieza-6 no corroborable por QA (únicamente URLs de CDN de entrega). Recomendación: de no poder aportarse evidencia de verificación, cambiar a «No identificado fehacientemente» conforme a §7. No afecta QA_APPROVED ni la conformidad arquitectónica.

---

## 4. Riesgos residuales declarados (no ocultados)

- Cap de 24 MP no equivale a techo total de memoria RSS del sistema.
- Disponibilidad de `ATOMIC_MOVE` depende del volumen de `NORMA_UPLOADS`; sin él, 500 documentado sin fallback a copy público.
- Procedencia original de 4 fotos fuente aún no revalidada criptográficamente (parejas 1/5, 2/6, 3/7, 4/8 son inferencia visual).
- Retención de archivos finales sin referencia tras fallo del motor es riesgo operativo documentado, no resuelto por #4 (excluido por #3).

---

## 5. Decisión y siguientes pasos

- Conformidad arquitectónica de `830d709` contra diseño v1.0: **PASS** (Acting Software Architect, Modo Contingencia).
- QA independiente: **QA_APPROVED** sobre `4efe03a`.
- La issue #4 está lista para integración por Orca (merge a `main` y cierre), ya que ambas aprobaciones (QA + Arquitectura) están satisfechas sobre la versión final entregada.
- No se declara cierre ni se mezcla rama desde esta aprobación; corresponde a Orca.

Commit autorizado para este informe únicamente en rama `GonzaCarrillo/issue-4-media` (no `main`).
