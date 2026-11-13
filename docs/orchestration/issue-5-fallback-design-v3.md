# Especificación Técnica y Diseño Arquitectónico v3.0 — Componente de Fallback de Intenciones (H1)

**Issue**: [#5 — Soporte de voz argentina y transcripción con fallbacks](https://github.com/GonzaCarrillo/Norma-/issues/5)  
**Versión**: 3.0 (Codex v3 Aprobado para Despacho)  
**Fecha**: 13 de Noviembre de 2026  
**Autor**: Codex (Software Architect) / Registrado por Antigravity (Software Builder)  
**Rama**: `GonzaCarrillo/reconcile-5-voice`  
**Estado**: Aprobado para construcción aislada en Fase 2; cableado a `Api.java` diferido a Gate de Integración post-SSE #6.

---

## 1. Contexto, Motivación y Aislamiento

Durante la conciliación de la Issue #5, se completaron y auditaron exitosamente los requerimientos de audio y transcripción (H2 y H3 bajo commit `06f86f7`). El requerimiento H1 (fallback de intenciones asistido por LLM) fue aislado arquitectónicamente para evitar colisiones concurrentes con el desarrollo del endpoint SSE en la Issue #6.

Este diseño establece el contrato definitivo para el componente `IntentFallback.java` y su suite de pruebas deterministas `IntentFallbackTest.java`, cumpliendo rigurosamente los siguientes principios:
1. **Aislamiento absoluto**: No se modifica `Api.java`, `BotEngine.java`, `IntentParser.java`, `Catalog.java`, esquemas SQL, POM, propiedades ni assets de frontend.
2. **El modelo solo propone**: El LLM (`llama-3.3-70b-versatile`) actúa exclusivamente como un normalizador de texto cuando la gramática estricta no reconoce la intención. La FSM y el validador determinista retienen toda la autoridad sobre la transición de estados, validación y confirmación.
3. **Inmunidad contra inyección de control**: Los comandos de control conversacional (`CONFIRM`, `CANCEL`, `UNDO`) quedan estrictamente prohibidos en el fallback; jamás se convierten en comandos de catálogo.
4. **Verificación estricta de evidencia**: El modelo no puede inventar títulos, precios, números de pieza ni URLs de imagen. Todo dato aceptado debe ser una subcadena literal verificable del texto de entrada.

---

## 2. Contrato de la API y Ciclo de Vida del Componente

### 2.1 Definición de Clase y Dependencias

```java
package com.norma.taller;

@Component
public class IntentFallback {
    public Optional<String> normalize(String original, String image, List<Catalog.Product> products);
}
```

### 2.2 Constructores y Configuración

El componente expone dos constructores:
1. **Constructor de Producción (Spring IoC)**:
   ```java
   @Autowired
   public IntentFallback(
           @Value("${groq.api.key:}") String key,
           IntentParser parser,
           ObjectMapper json
   )
   ```
   Configura un `RestClient` apuntando a `https://api.groq.com/openai/v1` con `JdkClientHttpRequestFactory`, `connectTimeout = Duration.ofSeconds(3)` y `readTimeout = Duration.ofSeconds(8)`. No implementa reintentos automáticos ni bloqueos prolongados.
2. **Constructor Inyectable para Pruebas (Test Fixture)**:
   ```java
   public IntentFallback(
           String key,
           IntentParser parser,
           ObjectMapper json,
           RestClient client
   )
   ```
   Permite sustituir el `RestClient` por un mock o fixture HTTP local sin dependencias de red ni claves reales.

### 2.3 Políticas de Seguridad y Red

- **Clave ausente o en blanco**: Si `groq.api.key` es vacía o nula, `normalize` retorna inmediatamente `Optional.empty()` sin llamadas de red ni errores.
- **Fallas de comunicación**: Cualquier excepción HTTP (4xx, 5xx), timeout de conexión o lectura, interrupción o parseo corrupto retorna inmediatamente `Optional.empty()`.
- **Privacidad y Auditoría**: Queda terminantemente prohibido loguear la clave de API (`groq.api.key`), el texto íntegro de mensajes del usuario o secretos en bitácoras (`logger`).
- **Contexto Transaccional**: No debe ejecutarse jamás dentro de un bloque `@Transactional` activo de base de datos.

---

## 3. Contrato Estricto de Salida del LLM

### 3.1 Parámetros de la Invocación a Groq

- **Endpoint**: `POST /chat/completions`
- **Modelo**: `llama-3.3-70b-versatile`
- **Temperatura**: `0` (determinismo máximo)
- **Response Format**: `{"type": "json_object"}`
- **System Prompt**: Instruye al modelo a extraer únicamente intenciones de catálogo argentino con evidencia literal, o retornar campos vacíos si no es explícito.

### 3.2 Esquema JSON Estricto y Reglas de Deserialización

La respuesta del modelo debe ser un objeto JSON plano con exactamente 4 claves:

```json
{
  "operation": "CREATE | PRICE | IMAGE | SOLD",
  "targetEvidence": "subcadena literal o vacio",
  "priceEvidence": "subcadena literal o vacio",
  "titleEvidence": "subcadena literal o vacio"
}
```

Reglas estrictas de parsing (Jackson):
1. **Detección de duplicados**: `JsonParser.Feature.STRICT_DUPLICATE_DETECTION` activado. Cualquier clave duplicada causa el descarte total (`Optional.empty()`).
2. **Sin propiedades desconocidas**: La presencia de cualquier campo adicional a los 4 permitidos invalida la respuesta.
3. **Sin coerción de tipos**: Todos los 4 valores deben ser strings primitivos (`isTextual()`). No se admiten números, booleanos, arrays, objetos anidados ni literales nulos.
4. **Sin contenido residual**: No se permiten tokens o caracteres adicionales tras el cierre del objeto JSON.

---

## 4. Validación de Evidencia y Reconstrucción Canónica

Para que una propuesta del modelo sea aceptada, debe cumplir todas y cada una de las siguientes validaciones locales:

### 4.1 Subcadenas Literales (Anti-Alucinación)
- Toda evidencia no vacía (`targetEvidence`, `priceEvidence`, `titleEvidence`) debe existir como subcadena literal exacta en el mensaje `original` (`original.contains(evidence)`).
- Longitud acotada: `titleEvidence.length() <= 100`.

### 4.2 Matriz de Campos por Operación
Los campos que no corresponden a la operación declarada deben ser cadenas vacías (`""`):

| Operación | `targetEvidence` | `priceEvidence` | `titleEvidence` | URL de Imagen |
|---|---|---|---|---|
| `CREATE` | Vacío (`""`) | Requerido (`original.contains`) | Requerido (`original.contains`) | Proviene del parámetro `image` (nunca del LLM) |
| `PRICE` | Requerido (`original.contains`) | Requerido (`original.contains`) | Vacío (`""`) | No aplica |
| `IMAGE` | Requerido (`original.contains`) | Vacío (`""`) | Vacío (`""`) | Proviene del parámetro `image` (nunca del LLM) |
| `SOLD` | Requerido (`original.contains`) | Vacío (`""`) | Vacío (`""`) | No aplica |

### 4.3 Validación de Destino (`targetEvidence`)
- Debe contener una referencia explícita a una pieza mediante el patrón `(?:(?:la\\s+)?(?:pieza|n[uú]mero|numero)\\s*[:#]?\\s*|#\\s*)(\\d+|[a-záéíóúñ]+)`.
- El número extraído debe corresponder a una pieza existente en `products` que esté **activa** (`!product.sold()`).
- Si la pieza está vendida, no existe, o existen múltiples referencias contradictorias en el texto de entrada, se rechaza inmediatamente (`Optional.empty()`).

### 4.4 Validación de Precio (`priceEvidence`)
- Debe ser convertible a un entero positivo mediante `parser.price(priceEvidence) > 0`.
- Si el precio no es válido o resulta menor o igual a cero, se rechaza.

### 4.5 Inmunidad de Controles
- Si el mensaje `original` corresponde a un control conversacional (`CONFIRM`, `CANCEL`, `UNDO`), verificado por `parser.extractControl(original, image)`, el componente no invoca al LLM y retorna `Optional.empty()`.

### 4.6 Reconstrucción Canónica Local y Validación con `IntentParser`

Una vez verificados los fragmentos literales, el componente no ejecuta acciones por sí mismo. En su lugar, construye una sentencia canónica en español coloquial estandarizado:
- `CREATE`: `"Publicá " + titleEvidence.trim() + " a " + priceVal + " pesos"`
- `PRICE`: `"A la número " + targetNum + " subila a " + priceVal + " pesos"`
- `IMAGE`: `"Cambiá la foto de la pieza " + targetNum`
- `SOLD`: `"Ya vendí la pieza " + targetNum`

Dicha sentencia se envía a `parser.interpret(canonical, image, products, null)`.
**Criterio de Aceptación**: Se acepta la normalización únicamente si `parser.interpret` retorna una instancia de `BotModel.Complete` cuya acción interna coincide de forma exacta con los campos validados (`operation`, `targetNum`, `priceVal`, `title`, `image`).
Cualquier otro resultado (`NeedsDetails`, `Ambiguous`, `Invalid`, `Unknown`) provoca el descarte inmediato (`Optional.empty()`).

---

## 5. Arquitectura de Integración Futura (Post-Issue #6)

En el futuro gate de integración, cuando la rama con SSE #6 se encuentre consolidada en `main` o estabilizada, `Api.java` o el controlador orquestador invocará `IntentFallback` bajo las siguientes condiciones exclusivas:

```text
Condiciones para activar IntentFallback:
  1. Estado de la sesión es IDLE.
  2. No hay borrador pendiente (pendingDraft == null).
  3. parser.extractControl(text, photo) es BotModel.None.
  4. parser.interpret(text, photo, products, null) retorna BotModel.Unknown.
```

Si el resultado del parser determinista es `Complete`, `NeedsDetails`, `Ambiguous` o `Invalid`, o si existe una señal de control (`Confirm`, `Cancel`, `Undo`, `Correction`), **no se consulta al LLM**. La invocación previa se ejecuta fuera de cualquier transacción `@Transactional`.

---

## 6. Plan de Pruebas Deterministas (`IntentFallbackTest.java`)

La suite de pruebas debe ser 100% determinista, aislada y sin consumo de cuota ni claves reales:
1. **Casos Exitosos de Normalización**:
   - `CREATE`: texto con frase desordenada ("me gustaria agregar mate de algarrobo por quince mil pesos"), imagen válida y catálogo activo -> devuelve comando canónico `"Publicá mate de algarrobo a 15000 pesos"`.
   - `PRICE`: frase coloquial ("a la pieza 2 ponela en 20000"), pieza 2 activa -> devuelve comando canónico `"A la número 2 subila a 20000 pesos"`.
   - `IMAGE`: frase con foto adjunta ("actualizame la foto de la pieza 3"), imagen válida -> devuelve comando canónico `"Cambiá la foto de la pieza 3"`.
   - `SOLD`: frase coloquial ("anotame que la 4 se vendio"), pieza 4 activa -> devuelve comando canónico `"Ya vendí la pieza 4"`.
2. **Defensas Anti-Alucinación**:
   - Modelo inventa título no presente en el texto original -> `Optional.empty()`.
   - Modelo inventa precio o número no presente en el texto original -> `Optional.empty()`.
   - Modelo inventa URL de imagen en el payload -> descartado (se usa exclusivamente el parámetro `image`).
3. **Validación de Destino y Catálogo**:
   - Referencia a pieza inexistente en catálogo -> `Optional.empty()`.
   - Referencia a pieza ya vendida (`sold == true`) -> `Optional.empty()`.
   - Mensaje con múltiples referencias ambiguas a piezas -> `Optional.empty()`.
4. **Validación de Sintaxis JSON y Estructura**:
   - Claves duplicadas en JSON -> `Optional.empty()`.
   - Propiedades desconocidas en JSON -> `Optional.empty()`.
   - Coerción de tipos (número en vez de string en `priceEvidence` u `operation`) -> `Optional.empty()`.
   - Caracteres trailing tras el JSON -> `Optional.empty()`.
5. **Inmunidad de Controles Conversacionales**:
   - Entrada `"si"`, `"confirmo"`, `"dale"` -> `Optional.empty()`.
   - Entrada `"no"`, `"cancela"` -> `Optional.empty()`.
   - Entrada `"deshacer"`, `"me equivoque"` -> `Optional.empty()`.
6. **Resiliencia ante Fallos de Infraestructura**:
   - Clave `groq.api.key` nula o vacía -> `Optional.empty()`, cero llamadas HTTP.
   - Respuestas HTTP 401, 429, 500, o timeout de lectura -> `Optional.empty()`.
