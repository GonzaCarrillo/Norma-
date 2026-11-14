# Reporte de Construcción — Modelado de Dominio: Glosario Ubicuo v1

**Fecha**: 14 de Noviembre de 2026  
**Rama**: `GonzaCarrillo/domain-glossary`  
**Tarea / Dispatch**: `task_d4116ecf9ed8` / `ctx_8e576bc6addc`  
**Rol**: Antigravity (Software Builder / Implementer)  
**Artefactos construidos**:
- `GLOSSARY.md` (raíz del repositorio)
- `docs/orchestration/domain-glossary-build.md` (este reporte de trazabilidad)

---

## 1. Resumen de la Tarea

Se implementó el archivo raíz `GLOSSARY.md` siguiendo la especificación aprobada para el modelado de dominio documental v1 de **El Taller de Norma**. El documento plasma el lenguaje ubicuo del dominio artesanal y de la administración conversacional de la vidriera digital en español rioplatense, sin trasladar detalles de implementación técnica, frameworks ni estructuras de datos al glosario.

No se alteró código fuente de producción, ni suites de pruebas, ni archivos de configuración (`pom.xml`, `.properties`), ni documentación institucional (`README.md`, `AGENTS.md`, `docs/decisiones.md`). No se generaron nuevos ADRs ni cambios a las reglas operativas del proyecto.

---

## 2. Trazabilidad con Fuentes de Implementación y Especificaciones

El glosario refleja fielmente el comportamiento y las invariantes codificadas en el monolito, abstrayendo la terminología hacia el lenguaje de negocio:

| Concepto de Dominio en `GLOSSARY.md` | Fuente de Código / Evidencia | Contrato o Comportamiento Subyacente |
|---|---|---|
| **Pieza** | `Catalog.Product`, `BotModel.Action` | Unidad artesanal individual en catálogo con atributos esenciales: número, título, precio, foto y estado vendido. |
| **Número de pieza** | `Catalog.Product.pieceNumber`, `Catalog.Session.nextNumber` | Entero correlativo único por sesión, referenciado en la conversación para comandos y modificaciones. |
| **Catálogo** | `Catalog.all(sessionId)` | Colección completa de piezas de la sesión (disponibles y vendidas ordenadas por `piece_number`). |
| **Vidriera** | `Catalog.Product.sold == false` (filtro de vista) | Exhibición exclusiva de piezas disponibles para clientes y visitantes. |
| **Pieza disponible vs Vendida** | `Catalog.Product.sold` (0 ó 1), `BotModel.Sell` | El registro de venta oculta la pieza de la vidriera pública; el producto permanece en la base relacional sin borrado físico. |
| **Sesión de evaluación** | `Catalog.ensure(id)`, `Catalog.valid(id)` | Sandbox aislado mediante identificador alfanumérico sin login ni autenticación con contraseña. |
| **Catálogo inicial** | Semilla canónica `DEFAULT` en `seed.sql`, `Catalog.ensure` | Plantilla inmutable de 8 piezas de muestra clonadas en transacciones aisladas. |
| **Borrador** | `BotModel.Draft`, `Catalog.Session.pending` | Estructura en memoria o serializada que acumula datos parciales durante el diálogo sin aplicar mutaciones. |
| **Propuesta pendiente de confirmación** | `BotModel.State.AWAITING_CONFIRMATION`, `BotModel.Action` | Acción completa y validada que aguarda la confirmación afirmativa del usuario para ejecutarse. |
| **Confirmación** | `BotModel.Control.Confirm`, `BotEngine.process` | Señal explícita de aceptación que dispara `Catalog.apply(..., action)` de manera atómica. |
| **Cancelación** | `BotModel.Control.Cancel`, `BotEngine.process` | Descarte del borrador y retorno a estado base sin alterar el catálogo ni borrar snapshots previos. |
| **Deshacer** | `BotModel.Control.Undo`, `Catalog.Session.snapshot`, `Catalog.restore` | Reversión en un único paso del último cambio confirmado a partir del snapshot serializado. |
| **Restauración del catálogo** | `Catalog.reset(id)` | Reposición de las 8 piezas iniciales, limpieza de borradores y snapshots, preservando el high-water mark numérico. |
| **Alta de pieza** | `BotModel.Create`, `Catalog.apply` (`CREATE`) | Incorporación de pieza requiriendo obligatoriamente foto válida, título (1..100 caracteres) y precio entero positivo. |
| **Cambio de precio** | `BotModel.ChangePrice`, `Catalog.apply` (`PRICE`) | Valor monetario en pesos enteros positivos sin centavos ni valores negativos o desbordantes. |
| **Cambio de fotografía** | `BotModel.ChangeImage`, `Catalog.apply` (`IMAGE`) | Actualización de la imagen preservando identidad de la pieza y número asociado. |
| **Registro de venta** | `BotModel.Sell`, `Catalog.apply` (`SOLD`) | Marcado de pieza como vendida sin pasarela de cobro, pedido mercantil ni eliminación. |
| **Asistente** | `BotEngine`, `IntentParser` | Interlocutor que asiste, interpreta y propone cambios, sin autorizar mutaciones de manera autónoma. |
| **Ejemplo de demostración** | `docs/operacion.md` (§ "Prueba del jurado") | Escenarios predefinidos para evaluación rápida sin grabación de voz real ni STT vivo de Norma. |

---

## 3. Análisis de Casos de Borde del Dominio

### 3.1. Vender → Deshacer (`vender → deshacer`)
- **Escenario**: La artesana indica que vendió una pieza (ej. pieza #3) y confirma la propuesta. Posteriormente advierte un error y solicita deshacer el cambio ("me equivoqué" / "deshacer").
- **Comportamiento del dominio**:
  1. Al confirmarse el registro de venta, la pieza #3 pasa a estado vendida y la vidriera deja de mostrarla inmediatamente.
  2. Inmediatamente antes de aplicar la venta, el sistema toma un resguardo completo del estado previo del catálogo en la sesión.
  3. Cuando Norma solicita deshacer, el asistente restaura el catálogo al estado resguardado: la pieza #3 vuelve a estar disponible y visible en la vidriera, conservando exactamente su mismo número de pieza, su fotografía, su título y su precio.
  4. El resguardo de deshacer se consume al ejecutarse. Si la artesana solicita inmediatamente un segundo deshacer consecutivo, no existe un historial anterior adicional; el asistente le responde con amabilidad que ya no hay cambios previos para volver atrás.

### 3.2. Proponer → Cancelar (`proponer → cancelar`)
- **Escenario**: Durante el diálogo, la artesana comienza a preparar un cambio (por ejemplo, actualizando el precio de una pieza o cargando los datos de una nueva pieza) y, ya sea con datos parciales o con la propuesta pendiente de confirmación, expresa su rechazo ("no", "cancelar", "dejalo así").
- **Comportamiento del dominio**:
  1. El asistente descarta íntegramente el borrador en preparación y regresa a la conversación base.
  2. El catálogo permanece inalterado: ninguna pieza es creada ni modificada.
  3. La cancelación del borrador no toca ni revierte los cambios que hubieran sido confirmados con anterioridad en el catálogo.
  4. Si con anterioridad existía un cambio confirmado respaldado para deshacer, la cancelación del borrador en curso no destruye ese resguardo, permitiendo aún deshacer el cambio confirmado previo si correspondiera.

### 3.3. Restauración del catálogo → Alta nueva con número no reciclado (`reset → alta nueva`)
- **Escenario**: Una sesión fue utilizada para crear piezas adicionales (por ejemplo, las piezas #9 y #10). Luego, el evaluador o artesana decide restaurar el catálogo inicial mediante la opción de restauración ("Restaurar catálogo original" / `reset`), y con posterioridad solicita el alta de una nueva pieza artesanal.
- **Comportamiento del dominio**:
  1. La restauración del catálogo repone exactamente las ocho piezas de muestra originales (piezas #1 a #8) y elimina las piezas creadas durante la sesión, además de limpiar borradores pendientes y puntos de deshacer.
  2. No obstante, el contador de numeración histórica respeta la política de marca de agua máxima (*high-water mark*): conserva el valor máximo entre el contador histórico anterior y la numeración inicial (`MAX(contador_anterior, 9)`).
  3. Cuando Norma realiza un alta de pieza tras la restauración, la nueva pieza no recicla el número #9 ni el #10: se le asigna el número #11.
  4. De este modo se garantiza que las piezas creadas conserven referencias numéricas históricas únicas e irrepetibles dentro de la sesión, evitando confusiones o solapamientos con registros pasados.

### 3.4. Dos pestañas del navegador no constituyen dos sesiones (`dos tabs ≠ dos sesiones`)
- **Escenario**: Un usuario evaluador abre simultáneamente dos pestañas o ventanas distintas en su navegador web apuntando a la misma URL de evaluación con el mismo identificador de sesión.
- **Comportamiento del dominio**:
  1. Ambas pestañas interactúan sobre el mismo espacio de sesión en el servidor. Comparten exactamente el mismo catálogo de piezas, la misma vidriera, el mismo borrador en curso y el mismo historial de deshacer.
  2. Un cambio iniciado o confirmado en una pestaña se refleja en el estado compartido de la sesión.
  3. La apertura de una nueva pestaña de navegación no crea de forma automática un catálogo nuevo ni una conversación aislada.
  4. Para llevar a cabo evaluaciones paralelas independientes sin cruzamiento de datos, el evaluador debe generar o navegar utilizando un identificador de sesión diferente para cada entorno.

---

## 4. Verificación de Cumplimiento de Restricciones

- **Ausencia de tecnicismos en `GLOSSARY.md`**: El glosario no contiene referencias a SQL, HTTP, endpoints REST, estados FSM (`IDLE`, `AWAITING_CONFIRMATION`), tipos de datos de programación (`record`, `enum`, `String`, `int`), configuraciones de infraestructura ni terminología cloud.
- **Formato estricto**: Se respetó el formato de encabezado de contexto breve (1-2 frases), sección `## Language`, entradas `**Término**: definición (1-2 frases)` y cláusula `_Avoid_: sinónimos confusos a evitar`.
- **Registro de decisiones (ADR)**: No se crearon nuevos ADRs ni se modificó `docs/decisiones.md`. Las decisiones D5, D8, D9 y D10 se mantienen vigentes como marco de referencia.
- **Alcance acotado**: Se limitó estrictamente a la creación de `GLOSSARY.md` y `docs/orchestration/domain-glossary-build.md`. No se tocaron archivos de código fuente, pruebas ni configuración.
