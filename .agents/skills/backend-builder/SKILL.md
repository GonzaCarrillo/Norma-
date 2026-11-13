---
name: backend-builder
description: Implementar una issue backend aprobada de El Taller de Norma como Antigravity, respetando los contratos Spring MVC, la FSM tipada, JdbcClient y SQLite v2 del repositorio.
---

# Construir el backend de Norma

1. Leer `AGENTS.md`, el diseño versionado y la aprobación de Orca en `docs/orchestration/`. Confirmar worktree/rama y registrar diff previo antes de escribir. Trabajar solo en la lista autorizada; conservar archivos ajenos sin reset, stash ni adición global. Esperar cada gate exigido por la tarea.
2. Mantener Java 25 sin preview y Spring Boot 3.5.16 del `pom.xml`. Usar constructor injection y el paquete plano `com.norma.taller`; no crear capas controller/service/repository nuevas ni introducir Lombok, JPA, Spring Security o framework frontend sin diseño aprobado. Dependencias existentes: Web, JDBC, sqlite-jdbc 3.49.1.0, Thumbnailator 0.4.20, webp-imageio 0.1.6 y starter-test.
3. Separar responsabilidades actuales:
   - Borde HTTP/SSE en `Api.java`; DTOs públicos son records existentes, no entidades JPA.
   - Reglas conversacionales y transacciones en `BotEngine`; tipos en `BotModel`, gramática en `IntentParser`, serialización en `BotSessionCodec`.
   - SQL parametrizado mediante `JdbcClient` en `Catalog`; bootstrap/compatibilidad en `CatalogDatabaseInitializer`.
   - Proveedores y archivos en `MediaService`, sin red ni procesos dentro de transacciones de catálogo.
4. Preservar los contratos de error de `ApiErrors`: IllegalArgumentException a 400 con `message` español, multipart demasiado grande a 413 y error inesperado a 500 genérico con log servidor. No devolver stack traces, JSON de snapshots o secretos al usuario. No ocultar fallos SQL como cancelación o éxito.
5. Mantener SQLite real y sus invariantes: `spring.sql.init.mode=never`; inicializador anterior a Catalog; esquema v2 y semilla en recursos SQL; WAL, FK activas, busy_timeout=5000, synchronous=NORMAL y pool de una conexión. No borrar DB para arrancar, reparar semilla silenciosamente o rebajar next_number. Reset/restore no reciclan números; Catalog devuelve vendidos internamente y Api los filtra de la vidriera.
6. Para mutaciones usar los beans proxied con `@Transactional` y propagación REQUIRED actual. Snapshot, apply y limpieza del borrador forman una unidad; restore, consumo de snapshot y limpieza también. No usar REQUIRES_NEW o locks para compensar un diseño que depende de una única conexión. Dejar escapar errores inesperados para rollback.
7. Mantener el JSON del codec sin coerción ni truncamiento: comprobar rangos antes de convertir enteros Jackson, rechazar duplicados/residuos/versiones desconocidas y cruces de sesión; preservar valores históricos íntegros al restaurar. El parser consume importes/referencias completos; ni nombres duplicados ni varios montos se resuelven con findFirst o el último fragmento.
8. Configuración real: PORT, NORMA_DB, NORMA_UPLOADS y GROQ_API_KEY en `application.properties`; no cargar `.env` mágicamente. `PORT` y `NORMA_DB` son aliases de placeholders; `SERVER_PORT` y `SPRING_DATASOURCE_URL` son propiedades nativas que prevalecen sobre archivos externos. El sandbox no tiene autenticación administrativa: el token de sesión aísla catálogos, no acredita identidad.
9. Si se toca imagen, conservar fotografía humana: la implementación actual centra 4:3, usa WebP y exige menos de 80000 bytes. Verificar el diseño de medios vigente antes de cambiar límites, formatos o dependencias. Para voz, respetar los timeouts de RestClient y ProcessBuilder existentes; cambios de proveedor requieren diseño.
10. Ejecutar las pruebas pertinentes y después la suite completa conforme a `testing-and-qa`. Entregar comandos/resultados reales, hash y diff, archivos, riesgos y pendientes; hacer commits atómicos en la rama actual con referencia a la issue. Si el arreglo exige modificar contrato/esquema/scope, informar a Orca antes de hacerlo. No cerrar, publicar ni integrar sin la autorización correspondiente.
