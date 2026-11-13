# Diseño de issue #8 — Verificación integral y despliegue reproducible

Versión **1.0**, 2026-11-13. Autor: **OpenCode / Nemotron — Software Architect interino (Modo Contingencia)**.
Issue: [#8](https://github.com/GonzaCarrillo/Norma-/issues/8).
Base inspeccionada: `572d291` (rama `GonzaCarrillo/issue-8-deploy`, basada en `main` con #4, #5, #6, #7 mergeados).
Rama: `GonzaCarrillo/issue-8-deploy`; worktree: `/home/gonzalocar/orca/workspaces/Norma-/issue-8-deploy`.

---

## 1. Verificación de cumplimiento con código existente

La issue #8 depende de #7 (orden anterior del backlog), ya verificada. Al inspeccionar el código actual en `main`, la verificación integral y el despliegue reproducible ya están **implementados**:

| Artefacto | Ruta | Contenido para #8 |
|---|---|---|
| CI pipeline | `.github/workflows/verify.yml` | push/PR, Java 25 (temurin), `mvn -B verify`, `docker build -t norma:verificada .` |
| Pruebas de API y reglas | `src/test/java/com/norma/taller/*` | 17 suites: ApiAudioTest, BotApiTest, BotEngineTest, BotPersistenceTest, BotSessionCodecTest, CatalogMigrationTest, CatalogPersistenceTest, CatalogRepositoryTest, DemoMediaTest, FlowTest, IntentParserTest, MediaHttpTest, MediaServiceTest, PhotoProcessorTest, StorefrontContractTest, StorefrontSseTest |
| Docker con volumen | `Dockerfile` + `compose.yaml` | Multi-etapa (build Maven + JRE 25 con edge-tts), `VOLUME /data`, `EXPOSE 8080`, usuario no-root, healthcheck `/api/ping`; compose con volumen `norma-data`, puerto 8080, `GROQ_API_KEY` opcional |
| Guía operativa | `README.md` + `docs/operacion.md` | Compilación (`mvn verify`), ejecución (`java -jar`), configuración externa (PORT, NORMA_DB, NORMA_UPLOADS, GROQ_API_KEY), precedencia de propiedades, contenedor (`docker compose up`), prueba del jurado, límites y riesgos |

---

## 2. Criterios de aceptación #8 vs implementación actual

| Criterio #8 | Estado en código actual (main) | Evidencia |
|---|---|---|
| Tests y empaquetado pasan | Implementado | CI `verify.yml` ejecuta `mvn -B verify`; 17 suites, 192 tests (tras #5), 0 fallos |
| Receta de arranque | Implementado | `README.md` §Compilación y ejecución: `mvn -B verify`, `java --enable-native-access=ALL-UNNAMED -jar target/taller-1.0.0.jar`, `http://localhost:8080` |
| Límites y configuración documentados | Implementado | `README.md` §Configuración externa (tabla de propiedades, precedencia, aliases); `docs/operacion.md` (límites de medios, persistencia, migración, concurrencia, riesgos) |
| CI | Implementado | `.github/workflows/verify.yml`: push/PR, setup-java 25, `mvn -B verify`, `docker build` |
| Docker con volumen | Implementado | `Dockerfile` `VOLUME /data`, `compose.yaml` volumen `norma-data:/data`, persistencia fuera del contenedor |
| Persistencia fuera del contenedor | Implementado | `Dockerfile` ENV `NORMA_DB=/data/norma.db NORMA_UPLOADS=/data/uploads`; `compose.yaml` volumen nombrado `norma-data` |
| No afirmar despliegue público sin evidencia | Respetado | README/docs declaran sandbox sin credenciales, sin SLA, sin URL pública garantizada |

---

## 3. Conclusión de diseño #8

**Issue #8 ya está resuelta por la implementación existente en `main`**. No hay archivos nuevos que crear ni código de producción que modificar. Los criterios de aceptación de #8 se verifican contra el código existente:

- Tests y empaquetado pasan (CI verde) ✓
- Receta de arranque documentada (README) ✓
- Límites y configuración documentados (README + docs/operacion.md) ✓
- CI con Java 25 y build Docker ✓
- Docker con volumen para persistencia fuera del contenedor ✓
- Sin afirmaciones de despliegue público sin evidencia ✓

**No se requiere Fase 2 (construcción) ni Fase 3 (QA de código nuevo) para #8.**

Todas las issues de la cola pendiente (#4, #5, #6, #7, #8) están ahora resueltas:
- #4 Medios: mergeado a `main` (QA_APPROVED + Arquitectura PASS)
- #5 Voz/STT/TTS: mergeado a `main` (QA_APPROVED + Arquitectura PASS)
- #6 Vidriera SSE: mergeado a `main` (QA_APPROVED + Arquitectura PASS, V1-zoom200 resuelto)
- #7 Simulador: resuelto por #6 (criterios ya cumplidos)
- #8 Despliegue reproducible: resuelto por código existente (CI, Docker, pruebas, guía operativa)

Commit autorizado para este documento únicamente en rama `GonzaCarrillo/issue-8-deploy` (no `main`).
