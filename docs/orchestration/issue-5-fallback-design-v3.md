# Especificación Técnica y Diseño Arquitectónico v3.1 — Componente e Integración de Fallback de Intenciones (H1)

**Issue**: [#5 — Soporte de voz argentina y transcripción con fallbacks](https://github.com/GonzaCarrillo/Norma-/issues/5)  
**Versión**: 3.1 (Codex v3.1 Aprobado para Despacho — Gate de Integración en Api)  
**Fecha**: 13 de Noviembre de 2026  
**Autor**: Codex (Software Architect) / Registrado por Antigravity (Software Builder)  
**Rama**: `GonzaCarrillo/reconcile-5-voice`  
**Estado**: Componente e Integración en `Api.java` implementados bajo contrato estricto; base unificada con `origin/main` (SSE #6 cerrado en `8cdacbe`).

---

## 1. Contexto, Motivación y Base Unificada

Tras la consolidación y cierre de la Issue #6 en `origin/main` (commit `8cdacbe`), se habilitó el gate de integración para conectar el componente `IntentFallback.java` (desarrollado y probado aisladamente en `c920ed3`) en `Api.java`.

La integración se realiza mediante merge limpio de `origin/main` en la rama `GonzaCarrillo/reconcile-5-voice`, preservando la atomicidad de las publicaciones SSE (`catalog-update`) y garantizando:
1. **Aislamiento transaccional**: La llamada al proveedor de red Groq (`IntentFallback.normalize`) ocurre estrictamente fuera de cualquier bloque `@Transactional` y sin retención de bloqueos de base de datos SQLite.
2. **Autoridad de la FSM**: El LLM solo propone texto canónico normalizado; la FSM (`BotEngine`) sigue requiriendo confirmación explícita (`AWAITING_CONFIRMATION`) antes de mutar el catálogo.
3. **Inmunidad de controles**: Controles conversacionales (`CONFIRM`, `CANCEL`, `UNDO`) y borradores pendientes jamás activan el modelo.
4. **Degradación transparente**: Ante cualquier fallo de red, clave ausente o JSON no conforme, se conserva el texto original intacto y `BotEngine` responde de forma nativa.

---

## 2. Contrato de Integración en `Api.java`

### 2.1 Inyección de Dependencias y Constructores

`Api.java` declara explícitamente las dependencias de interpretación y fallback:

```java
@RestController
public class Api {
    private final Catalog catalog;
    private final BotEngine bot;
    private final MediaService media;
    private final IntentParser parser;
    private final IntentFallback fallback;

    @Autowired
    public Api(Catalog c, BotEngine b, MediaService m, IntentParser parser, IntentFallback fallback) {
        this.catalog = c;
        this.bot = b;
        this.media = m;
        this.parser = parser;
        this.fallback = fallback;
    }

    // Adaptador de 3 argumentos para compatibilidad regresiva en pruebas unitarias aisladas sin red
    public Api(Catalog c, BotEngine b, MediaService m) {
        this(c, b, m, new IntentParser(), null);
    }
}
```

### 2.2 Flujo de Procesamiento en `POST /api/bot/message`

El endpoint ejecuta el siguiente pipeline secuencial:
1. Validaciones perimetrales: validación de formato de `sessionId`, rechazo de texto y audio simultáneos (HTTP 400), límite de longitud de texto <= 1000 caracteres.
2. Procesamiento de medios:
   - Si viene audio: transcripción mediante `media.transcribe(audio)` (HTTP 400 en caso de fallo STT).
   - Si viene imagen: persistencia y normalización WebP mediante `media.image(image)`.
3. **Condición de activación de `IntentFallback`**:
   - Se asegura la sesión: `catalog.ensure(sessionId)`.
   - Se consulta el estado actual de la sesión: `Catalog.Session sess = catalog.session(sessionId)`.
   - Se consulta el catálogo actual: `List<Catalog.Product> products = catalog.all(sessionId)`.
   - **Evaluación cuádruple**:
     1. `fallback != null`
     2. `"IDLE".equals(sess.state())` (estado base sin conversación pendiente).
     3. `sess.pending() == null` (sin borrador a la espera de confirmación o detalles).
     4. `parser.extractControl(text, photo) instanceof BotModel.None` (el mensaje no es señal de control).
     5. `parser.interpret(text, photo, products, null) instanceof BotModel.Unknown` (la gramática estricta determinista no reconoció el comando).
4. **Normalización y Reemplazo**:
   - Si se cumplen las condiciones, se invoca `fallback.normalize(text, photo, products)`.
   - Si retorna `Optional.of(canonicalCommand)`, la variable `text` se sustituye por dicho comando canónico antes de invocar a `bot.process(...)`.
   - Si retorna `Optional.empty()`, se preserva el `text` original.
5. Procesamiento conversacional y publicación:
   - Invocación a `bot.process(sessionId, text, photo)`.
   - Publicación SSE atómica a todos los clientes conectados a la sesión: `publish(sessionId)`.
   - Retorno del objeto inmutable `BotEngine.Reply(result.replyText(), media.speak(result.replyText()), result.state())`.

---

## 3. Matriz de Comportamiento de Activación

| Estado Sesión | Borrador Pendiente | Control (`extractControl`) | Interpretación (`interpret`) | ¿Consulta al LLM (`normalize`)? | Resultado ante Fallback |
|---|---|---|---|---|---|
| `IDLE` | `null` | `None` | `Unknown` | **SÍ** | Si propone válido -> FSM pasa a `AWAITING_CONFIRMATION`. Si falla -> bot responde "No entendí". |
| `IDLE` | `null` | `None` | `Complete` | **NO** | Gramática determinista resuelve directo a `AWAITING_CONFIRMATION`. |
| `IDLE` | `null` | `None` | `NeedsDetails` | **NO** | Gramática determinista pide el dato faltante (FSM pasa a `AWAITING_DETAILS`). |
| `IDLE` | `null` | `None` | `Ambiguous` | **NO** | Gramática determinista pide aclarar el número de pieza. |
| `IDLE` | `null` | `None` | `Invalid` | **NO** | Gramática determinista devuelve mensaje de error descriptivo. |
| `AWAITING_DETAILS` | `draft != null` | Cualquiera | Cualquiera | **NO** | El usuario está completando un dato pendiente; el motor resuelve. |
| `AWAITING_CONFIRMATION` | `draft != null` | `Confirm` ("si") | No aplica | **NO** | Se procesa la confirmación y se muta el catálogo a `IDLE`. |
| `AWAITING_CONFIRMATION` | `draft != null` | `Cancel` ("no") | No aplica | **NO** | Se cancela la acción y se vuelve a `IDLE`. |
| Cualquiera | Cualquiera | `Undo` | No aplica | **NO** | Deshacer conversacional sin intervención de red. |

---

## 4. Garantías de Seguridad y Concurrencia

1. **Aislamiento Transaccional de Red**:
   - `IntentFallback.normalize` se ejecuta fuera de cualquier contexto transaccional (`TransactionSynchronizationManager.isActualTransactionActive() == false`).
   - `bot.process(...)` abre su propia transacción de corta duración únicamente para la mutación SQLite en `Catalog`.
2. **Inmunidad de Inyección en URLs de Imagen**:
   - La URL de la imagen siempre proviene del pipeline de carga local gestionado por `MediaService` / `PhotoProcessor`, nunca del contenido devuelto por el modelo.
3. **No-mutación sin confirmación explícita**:
   - La normalización exitosa de un comando no ejecuta la acción en base de datos; únicamente coloca a la FSM en `AWAITING_CONFIRMATION`. Solo un mensaje posterior `"si"` muta el catálogo.
4. **Aislamiento entre Sesiones A/B**:
   - Las sesiones concurrentes no comparten estado, emisores SSE ni borradores.

---

## 5. Estrategia de Pruebas de Integración (`IntentFallbackApiTest.java`)

Se implementan pruebas HTTP de integración con `MockMvc` verificando:
1. **Flujo completo de normalización con confirmación**:
   - Envío de comando coloquial no reconocido por la gramática estándar con foto adjunta.
   - Fallback propone comando canónico `Publicá ... a ... pesos`.
   - Verificación de que la FSM responde pidiendo confirmación (`AWAITING_CONFIRMATION`) y el catálogo aún **no** se modificó.
   - Verificación de que `TransactionSynchronizationManager.isActualTransactionActive()` fue `false` durante la llamada al proveedor.
   - Envío posterior de `"si"`: confirma la acción, muta el catálogo y retorna a `IDLE` sin una segunda llamada al proveedor.
2. **No invocación al LLM en comandos deterministas o de control**:
   - Comandos directos (`Publicá ...`), controles (`si`, `no`, `deshacer`) y estados pendientes no invocan a `IntentFallback`.
3. **Degradación elegante ante fallos**:
   - Simulación de error HTTP 500, timeout o JSON corrupto del LLM: el endpoint responde 200 con el mensaje nativo de desconocimiento del bot ("No entendí qué querés hacer"), sin exponer fallas técnicas.
4. **Sesiones A/B concurrentes**:
   - La normalización en la sesión A no interfiere con la sesión B.
