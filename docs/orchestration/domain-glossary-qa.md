# Informe de QA Documental — Glosario de Dominio (commit 6a21cca)

**SHA base auditada:** `6a21cca3e67dd3408d433f2c0e6da31602351e8b`  
**Rama:** `GonzaCarrillo/domain-glossary`  
**Fecha:** 14 de Noviembre de 2026  
**Rol:** OpenCode / Nemotron — QA & Test Auditor (Fase 3)  
**Archivos bajo auditoría (solo lectura):** `GLOSSARY.md`, `docs/orchestration/domain-glossary-build.md`  
**Único archivo de escritura:** `docs/orchestration/domain-glossary-qa.md` (este informe)  
**Árbol de trabajo:** limpio (`git status --short` sin cambios pendientes)

---

## 1. Alcance y alcance excluido

La auditoría es **documental y de consistencia**: contrasto cada entrada de `GLOSSARY.md` y cada fila del reporte de build contra el modelo real de código (`BotModel`, `Catalog`, `BotEngine`, `schema.sql`, `seed.sql`). No se modifica `GLOSSARY.md`, el reporte de build, código de producción, configuración, `.idea`, `main`, ni se abre issue, push o merge.

Por alcance de la issue, **no se repiten las 254 pruebas ni se ejecuta Docker/Maven**. El entorno JDK observado local es 21 (no 25), por lo cual la verificación de comportamiento queda restringida a la lectura estática del código y al diff documental, sin suite de ejecución.

---

## 2. Evidencia de código consultada (modelo real)

- `src/main/java/com/norma/taller/BotModel.java`: `State` (IDLE, AWAITING_DETAILS, AWAITING_CONFIRMATION), `Operation` (CREATE, PRICE, IMAGE, SOLD), `Action` (Create, ChangePrice, ChangeImage, Sell), `Draft` (borrador parcial), `Control` (Confirm, Cancel, Undo, Correction, None), `ParseResult` (Complete/NeedsDetails/Ambiguous/Invalid/Unknown).
- `src/main/java/com/norma/taller/Catalog.java`: `Product(sessionId, pieceNumber, ..., sold)`, `Session(id, state, pending, tempImage, snapshot, nextNumber)`, `all(id)` (devuelve disponibles y vendidas), `apply(id, intent)` (CREATE requiere imagen/título/precio; PRICE/IMAGE/SELL no borran; SOLD pone `sold=1`), `reset(id)` (clona DEFAULT de 8 piezas, limpia `pending`/`temp_image`/`snapshot`, estado IDLE, conserva `next_number = max(s.nextNumber(), 9)`), `restore(id, products)` (usa snapshot, conserva high-water mark `max(s.nextNumber(), maxRestored+1, 9)`), `valid(id)` (alfanumérico 8-80, sin login ni contraseña).
- `src/main/resources/db/seed.sql`: plantilla `DEFAULT` de 8 piezas (`piece_number` 1 a 8) y `next_number = 9`.
- `src/main/resources/db/migrate-v0-v2.sql`: `piece_number` entre 1 y 2147483646, `price` entero entre 1 y 2147483647, `sold` en (0,1), `UNIQUE(session_id, piece_number)`.

---

## 3. Matriz de criterios auditados

| # | Criterio (dominio) | Evidencia en GLOSSARY.md | Evidencia en modelo real | Resultado |
|---|---|---|---|---|
| C1 | Contexto único y definiciones de negocio en 1-2 frases | Cada entrada tiene encabezado de contexto breve y definición de 1-2 frases | N/A (estilo documental) | PASS |
| C2 | Clausula `_Avoid_:` solo sinónimos confusos, sin tecnología | Todas las entradas tienen `_Avoid_:` con términos de negocio | N/A | PASS |
| C3 | Ausencia de clases/SQL/HTTP/SDK/tecnología en el glosario | No hay referencias a SQL, HTTP, endpoints, `record`, `enum`, `String`, `int`, infraestructura ni cloud | N/A | PASS |
| C4 | Catálogo incluye vendidas; vidriera solo disponibles | "Catálogo ... abarcando tanto las piezas disponibles como las ya vendidas"; "Vidriera ... exclusivamente las piezas actualmente disponibles ... No exhibe piezas vendidas" | `Catalog.all(id)` devuelve todo; vidriera = filtro `sold == false` | PASS |
| C5 | Número único por sesión, no reciclado tras reset/undo | "Referencia numérica entera y estable ... dentro de una sesión"; "Restauración del catálogo ... preservando la numeración histórica ... ni reinicio del contador a cero" | `UNIQUE(session_id, piece_number)`; `reset` conserva `max(next_number, 9)`; `restore` conserva high-water mark | PASS |
| C6 | Sesión ≠ usuario autenticado / pestaña | "No equivale a una cuenta de usuario autenticada con clave ni a una pestaña particular del navegador web" | `valid(id)` alfanumérico 8-80, sin login/password; dos pestañas comparten el mismo id de sesión | PASS |
| C7 | Borrador incompleto vs propuesta completa | "Borrador ... puede reunir datos parciales o completos pero aún no impacta"; "Propuesta pendiente de confirmación ... reúne todos los datos requeridos y válidos ... a la espera de la aprobación explícita" | `BotModel.Draft` parcial vs `BotModel.Action` completa y válida (`AWAITING_CONFIRMATION`) | PASS |
| C8 | Confirmación explícita | "Consentimiento explícito expresado por la artesana ... No se produce por guardado tácito ni por inferencias automáticas" | `Control.Confirm` dispara `Catalog.apply` atómicamente tras snapshot | PASS |
| C9 | Cancelación no revierte publicado | "Dejando el catálogo exactamente en el estado ... No revierte modificaciones ya asentadas con anterioridad" | `Control.Cancel` vuelve a IDLE limpiando `pending`/`temp_image` sin tocar `snapshot` ni cambios confirmados | PASS |
| C10 | Deshacer un paso | "Revierte ... por única vez el último cambio confirmado ... No brinda un historial acumulativo infinito" | `Control.Undo` consume el snapshot (lo pone a null), un solo paso, sin historial acumulativo | PASS |
| C11 | Reset: 8 piezas, limpia pending/snapshot, no recicla números | "Restablece las ocho piezas ... limpia borradores e historial de reversión, preservando la numeración histórica" | `reset` clona DEFAULT (8), `pending=NULL`, `temp_image=NULL`, `snapshot=NULL`, `state=IDLE`, `next_number=max(next_number,9)` | PASS |
| C12 | Imagen requerida en alta | "Alta de pieza ... requiriendo obligatoriamente fotografía, título descriptivo y precio. No admite cargas incompletas" | `Create` exige `imageUrl` no vacía; `Catalog.apply` CREATE exige `image` no vacía | PASS |
| C13 | Vendida no eliminada | "Pieza vendida ... permaneciendo resguardada en el catálogo histórico ... No constituye la eliminación definitiva"; "Registro de venta ... ni eliminación del registro" | `SELL` pone `sold=1`, no borra la fila | PASS |
| C14 | Ejemplos no son fotos/autoría Norma / STT real | "Ejemplo de demostración ... sin constituir una grabación auténtica de la voz de Norma ni una transcripción en vivo. No es un canal de comunicación directo ni un mensaje real de clientes" | Escenarios predefinidos (`docs/operacion.md`), sin audio/STT real | PASS |

---

## 4. Casos de borde revisados (sección 3 del reporte de build)

| Caso | Descripción | Consistencia con el código | Resultado |
|---|---|---|---|
| B1 | Vender → Deshacer | Venta confirma (`sold=1`, snapshot tomado antes); deshacer restaura el snapshot y la pieza vuelve disponible con mismo número/foto/título/precio; el resguardo se consume (segundo deshacer sin historial previo) | Coincide con `executeConfirmation` (snapshot + apply) y `handleUndo` (restore + snapshot null) | PASS |
| B2 | Proponer → Cancelar | Cancel descarta borrador, catálogo inalterado, no toca cambios confirmados previos, conserva snapshot para posible deshacer | `Control.Cancel` en DETAILS/CONFIRMATION vuelve a IDLE sin alterar productos ni snapshot | PASS |
| B3 | Reset → Alta nueva (número no reciclado) | Tras reset de piezas #9/#10, alta nueva recibe #11 (no #9 ni #10) por high-water mark | `reset` → `next_number = max(next_number, 9)`; CREATE usa `next_number` y lo incrementa | PASS |
| B4 | Dos pestañas ≠ dos sesiones | Mismo id de sesión comparte catálogo, vidriera, borrador e historial; no se crea catálogo automático por pestaña | Una fila de sesión por id; `ensure` con `ON CONFLICT DO NOTHING` | PASS |

---

## 5. Consistencia documental / diffcheck

- `GLOSSARY.md` y `docs/orchestration/domain-glossary-build.md` usan la misma terminología ubicua (pieza, número de pieza, catálogo, vidriera, disponible/vendida, sesión, catálogo inicial, borrador, propuesta pendiente, confirmación, cancelación, deshacer, restauración, alta, cambio de precio, cambio de fotografía, registro de venta, asistente, ejemplo de demostración).
- Cada fila del reporte de build (sección 2) tiene correspondencia 1:1 con una entrada del glosario y con una clase/método real del modelo.
- Los casos de borde de la sección 3 del reporte mapean exactamente a los controles del FSM (`Confirm`, `Cancel`, `Undo`) y a `Catalog.reset`/`restore`.
- No se detectan contradicciones entre el glosario, el reporte de build y el código fuente leído.
- No se inventan nuevas reglas, renombres ni ADRs; no se exige ADR (donde procede las decisiones D5, D8, D9, D10 se mantienen).

---

## 6. Limitaciones

- Auditoría documental sobre código leído estáticamente; no se ejecutó `mvn -B clean verify` ni la suite de 254 pruebas (fuera del alcance de esta issue y sin Docker).
- El JDK local observado es 21, no el 25 referenciado en la skill de QA; no afecta la validez del contraste documental.
- No se verificó frontend (navegador/zoom/DPR) ni SSE por no corresponder al alcance documental.

---

## 7. Dictamen

**QA_APPROVED**

`GLOSSARY.md` (commit `6a21cca`) es consistente con el modelo real de dominio (`BotModel`/`Catalog`/`BotEngine`, `schema.sql`, `seed.sql`): respeta el formato de contexto breve (1-2 frases), definición de negocio y `_Avoid_:` solo sinónimos confusos; no contiene clases/SQL/HTTP/SDK/tecnología; y modela correctamente catálogo con vendidas vs vidriera solo disponibles, número único por sesión no reciclado tras reset/undo, sesión distinta de usuario autenticado/pestaña, borrador incompleto vs propuesta completa con confirmación explícita, cancelación que no revierte publicado, deshacer de un paso, reset que limpia pending/snapshot sin reciclar números, imagen obligatoria en alta, vendida no eliminada y ejemplos sin audio/STT real. Los cuatro casos de borde (vender→deshacer, proponer→cancelar, reset→alta, dos pestañas) están correctamente documentados y son consistentes con el código.

No se modifica `GLOSSARY.md`, el reporte de build, código, configuración ni `.idea`; el único archivo escrito es este informe.
