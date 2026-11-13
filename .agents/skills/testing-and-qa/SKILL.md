---
name: testing-and-qa
description: Escribir y ejecutar pruebas de El Taller de Norma como Antigravity u Orca usando JUnit 5, Spring Boot Test, MockMvc y SQLite real; producir evidencia de compilación y regresión atribuible al commit.
---

# Verificar Norma

Leer `AGENTS.md` y el diseño aprobado. Ejecutar dentro del worktree confirmado; guardar artefactos bajo su `target/` ignorado. No ejecutar dos Maven sobre el mismo directorio. Usar los nombres de suites del código, sin fijar un conteo histórico como objetivo.

## Elegir la prueba por comportamiento

- `IntentParserTest`: JUnit Jupiter, `@ParameterizedTest`, CsvSource/ValueSource; cubrir gramática y rechazos, no regex espejo. Mantener puntuación de montos y referencias completas, ambiguos y títulos.
- `BotSessionCodecTest`: ObjectMapper/Jackson real; overflow, coerción, duplicados, trailing tokens, legacy, sesión cruzada y roundtrip íntegro.
- `BotEngineTest` y `FlowTest`: motor Spring proxied; transiciones y cuatro operaciones, no mutación sin sí, undo/no-op, snapshot, aislamiento y contador.
- `CatalogRepositoryTest`: ApplicationContextRunner con auto-config JDBC/transaction y archivo en `@TempDir`; comparar estado completo. `CatalogMigrationTest`: fixtures `src/test/resources/db/legacy-v0.sql`, schema/seed/guardas y fallos del migrador real.
- `CatalogPersistenceTest` y `BotPersistenceTest`: archivo SQLite, cierre de contexto/pool y reapertura. `new Catalog` o `new BotEngine` no activa @Transactional. Memoria no demuestra WAL ni persistencia.
- `BotApiTest`: `@SpringBootTest`, `@AutoConfigureMockMvc`, MockMultipartFile y medios simulados. El stub debe tener `MediaService.root` temporal válido; no cambiar producción para tolerar un mock incompleto. Verificar foto+texto/audio, estados, 400 y fallos sin red/Groq/edge-tts.
- Atomicidad: inyectar un fallo SQL dentro del camino real con trigger de fixture; comprobar causa exacta y estado íntegro desde una nueva transacción. Un fallo posterior a process en una transacción exterior no sustituye esta prueba. Concurrencia con barreras, timeouts y cierre del executor en finally.

## Comandos reales

Requiere JDK 25 y Maven >=3.6.3. El entorno observado tiene JDK en `/home/gonzalocar/.jdks/ms-25.0.4.1`; es una ruta local, reemplazarla por la instalación real en otro equipo. Confirmar antes de construir:

```bash
export JAVA_HOME=/home/gonzalocar/.jdks/ms-25.0.4.1
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
mvn -B compile
mvn -B -Dtest=IntentParserTest,BotSessionCodecTest test
mvn -B -Dtest=CatalogRepositoryTest,CatalogMigrationTest,CatalogPersistenceTest,FlowTest,BotEngineTest,BotPersistenceTest,BotApiTest test
mvn -B clean verify
```

Elegir solo suites afectadas para la iteración; `clean verify` final ejecuta todas. No usar skipTests, exclusions ni editar suites previas para aparentar éxito. Ante fallo entregar comando, causa y diagnóstico; no prometer que los comandos pasarán en cualquier entorno.

El JAR esperado es `target/taller-1.0.0.jar`. Smoke de base: `java --enable-native-access=ALL-UNNAMED -jar target/taller-1.0.0.jar` y `curl --fail http://localhost:8080/api/ping` devuelve PONG. Para pruebas de arranque usar DB/uploads aislados bajo target y puerto libre, detener solo el proceso propio y capturar el status real de wait; no usar `wait ... || true` para afirmar exit0.

## Evidencia limpia del commit

Con cambios autorizados ya commiteados, crear una exportación distinta dentro del worktree. Este bloque se ejecuta en Bash, desde su raíz:

```bash
set -o pipefail
norma_qa_root="$(git rev-parse --show-toplevel)"
mkdir -p "$norma_qa_root/target"
norma_qa_run="$(mktemp -d "$norma_qa_root/target/qa.XXXXXX")"
git rev-parse HEAD > "$norma_qa_run/commit.txt"
git archive HEAD -o "$norma_qa_run/source.tar"
mkdir "$norma_qa_run/export"
tar -xf "$norma_qa_run/source.tar" -C "$norma_qa_run/export"
(
  cd "$norma_qa_run/export" || exit
  java -version
  mvn -version
  mvn -B clean verify
) > "$norma_qa_run/verify.log" 2>&1
norma_qa_status=$?
printf 'EXIT_CODE=%s\n' "$norma_qa_status" >> "$norma_qa_run/verify.log"
printf 'Evidencia: %s\n' "$norma_qa_run"
```

Si el status no es cero, no aceptar el build ni asumir JAR válido. Si pasa, registrar `sha256sum` del log y del JAR exportado, XML `export/target/surefire-reports/TEST-*.xml` y sus conteos tests/failures/errors/skipped/basedir. Comparar fuentes exportadas con el hash registrado; no atribuirle archivos sin seguimiento del workspace. Una actualización posterior solo del reporte debe declarar la diferencia documental. No volcar propiedades sensibles de XML ni credenciales en informes.

CI `.github/workflows/verify.yml` ejecuta Maven verify con Temurin25 y Docker build. Docker vuelve a verificar en su etapa Maven; probar contenedor cuando el cambio lo exige, no confundir receta con despliegue público exitoso.
