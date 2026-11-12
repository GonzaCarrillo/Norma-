# 🏺 El Taller de Norma — Especificación de Arquitectura y Producto

> **Síntesis del Problema:** El desafío no radica en la complejidad algorítmica ni en la concurrencia masiva, sino en **eliminar por completo la fricción cognitiva y visual** para una operadora de 70 años (ex profesora de plástica, sin computadora, sin correo electrónico, con vista cansada y usuaria exclusiva de WhatsApp) y para sus compradores del mismo rango etario.

---

## 1. Traducción de Restricciones a Decisiones de Ingeniería

| Restricción del Enunciado | Decisión Arquitectónica y de Diseño |
| :--- | :--- |
| **"No tiene compu, no tiene mail"** | Cero autenticación tradicional (sin usuario, contraseña ni Magic Links). Administración 100% operada desde WhatsApp y simulada en navegador sin login. |
| **"Solo tiene WhatsApp (y costó que lo use)"** | El backoffice no es una página web: es un contacto de WhatsApp que recibe fotos y audios, y responde con audios de voz. |
| **"No ve bien" (Norma y sus compradores)** | **En la Web:** Accesibilidad extrema (WCAG AAA), contraste $> 7:1$, tipografía base mínima de `22px`, selector de letra gigante y lectura en voz alta nativa. **En WhatsApp:** El bot le responde a Norma mediante notas de voz (TTS rioplatense) para que no tenga que leer textos largos. |
| **"Una sola página: grilla con imagen, título y precio. Sin carrito, sin link a WhatsApp, nada"** | Vidriera pública estrictamente **Read-Only**. Cero botones de compra, cero pasarelas de pago y cero redirecciones externas. |
| **"Agregar, sacar si se queda sin piezas, cambiar foto o cambiar precio"** | Soporte completo para las 4 operaciones del ciclo de vida del catálogo mediante comandos de voz naturales e identificadores cortos de estantería (`Pieza #1`, `Pieza #2`). |
| **"Profesora de plástica / Creatividad humana sobre IA"** | Estética artesanal inspirada en un cuaderno de taller de cerámica (papel cálido, terracota, bordes orgánicos). La tecnología y el procesamiento de voz operan de forma invisible sin alterar su arte con IA generativa. |

---

## 2. Arquitectura del Sistema (Doble Cara + Sandbox de Evaluación)

El sistema se divide en dos interfaces conectadas en tiempo real (vía Server-Sent Events / WebSockets) a un motor central agnóstico al canal (`BotEngineService`):

### A. La Vidriera Pública (Para los Vecinos y Compradores)
1. **Fidelidad Estricta al Boceto:** Una única página con una grilla limpia donde cada tarjeta muestra únicamente:
   * **Etiqueta de Estantería:** Número corto y claro (`#1`, `#2`, `#3`) estilo feria artesanal.
   * **Fotografía de la Pieza:** Proporción uniforme (`4:3` o `1:1`) con bordes suaves.
   * **Título de la Obra:** Tipografía cálida pero de máxima legibilidad.
   * **Precio en Formato Argentino:** Sin decimales confusos ni símbolos extranjeros (ej. `$ 18.500`).
2. **Asistencia Visual y Auditiva (Sin romper el boceto):**
   * **Barra Superior de Ayuda Visual:** Dos botones grandes y claros: `[ 🔍 Letra más grande ]` y `[ 🔊 Escuchar catálogo ]` (al tocar una pieza, el navegador lee en voz alta: *"Pieza número 3: Jarrón de gres esmaltado, dieciocho mil quinientos pesos"*).
   * **Zoom deUn Toque:** Al tocar una foto, se amplía a pantalla completa con un único botón gigante inferior: `[ VOLVER A LAS PIEZAS ]`.
   * **Cartel de Taller:** Un encabezado cálido como nota escrita a mano: *"Catálogo del Taller de Norma — Si te gusta alguna pieza, anotá el numerito y escribime a mi WhatsApp de siempre"*.

---

### B. El Backoffice Invisible (Chatbot de WhatsApp con Devolución por Voz para Norma)
Norma administra todo conversando con un asistente en WhatsApp que se comporta como un sobrino paciente:

1. **Entrada Multimodal Desacoplada:** Soporta que Norma mande una foto con texto, una foto primero y un audio 20 segundos después, o un audio directo para modificar algo que ya está publicado.
2. **Verificación por Voz (Human-in-the-Loop):** Antes de aplicar cualquier cambio en la web pública, el bot genera un audio con voz cálida y acento argentino pidiéndole confirmación:
   * *"Hola Norma, recibí la foto. Entendí que es el Cuenco Rústico a quince mil pesos. ¿Lo subo así a la página?"*
3. **Las 4 Operaciones Requeridas por la Consigna:**
   * **Alta (`CREATE`):** Foto + Audio con nombre y precio $\rightarrow$ Confirmación $\rightarrow$ Publicación con asignación automática de número (`#N`).
   * **Baja / Sin Stock (`DELETE` / `SOLD`):** Audio diciendo *"Ya vendí la pieza 3"* o *"Sacá la maceta azul"* $\rightarrow$ Confirmación $\rightarrow$ Retiro de la grilla (u opción de cartelito *"Vendida"*).
   * **Cambio de Precio (`UPDATE_PRICE`):** Audio diciendo *"A la número 2 subila a veinte mil pesos"* $\rightarrow$ Confirmación $\rightarrow$ Actualización instantánea.
   * **Cambio de Foto (`UPDATE_IMAGE`):** Foto nueva + Audio diciendo *"Esta foto es para cambiar la de la pieza 4"* $\rightarrow$ Confirmación $\rightarrow$ Reemplazo de imagen manteniendo título y precio.

---

## 3. Las 5 Mejoras Diferenciadoras (Factor Ganador)

### 1. Numerito de Estantería (`#1`, `#2`, `#3`)
Elimina el problema de ambigüedad cuando hay varias piezas con nombres parecidos (*"Maceta chica"*, *"Maceta de barro"*). Tanto Norma por voz como sus clientes al encargarle por mensaje pueden referirse simplemente a *"la número 4"*.

### 2. Comando de Pánico: *"¡Uy, me equivoqué!"* (Rollback de 1 Paso)
El sistema guarda un snapshot (`previous_state`) de la última acción ejecutada. Si Norma borra una pieza o cambia un precio por error y manda un audio diciendo *"Uy no, me equivoqué, volvé atrás"*, el bot restaura el estado previo al instante y le responde por audio: *"Tranquila Norma, ya dejé todo como estaba antes"*.

### 3. Revelado Automático de Taller (Optimización No-IA)
Cada fotografía subida pasa por un pipeline determinista ultrarrápido que:
* Recorta y centra la imagen para no deformar la grilla.
* Corrige sutilmente la exposición/calidez (típico de fotos sacadas bajo luz de interior).
* Comprime la imagen a formato `WebP` liviano ($< 80\text{ KB}$) garantizando que el catálogo cargue en menos de $400\text{ ms}$.

### 4. Simulador de WhatsApp Embebido (Fricción Cero para el Juez)
Para que el evaluador del challenge no tenga que pedir credenciales por DM ni agendar números en su teléfono personal:
* La web incluye un botón flotante claramente separado del diseño: `[ 📱 Modo Evaluación: Abrir WhatsApp de Norma ]`.
* Al abrirlo, despliega un teléfono interactivo en la misma pantalla junto al catálogo.
* El juez puede usar el micrófono real de su dispositivo **o tocar 4 botones de "Audios Pre-grabados de Norma"** (Alta de pieza, Cambio de precio, Cambio de foto, Pieza vendida) para ver cómo el bot procesa el audio, responde con voz y actualiza el catálogo en vivo sin esfuerzo.

### 5. Aislamiento de Sesión y Botón de Restauración Inmediata
Como el link será público en un hilo de Twitter/X, los cambios hechos desde el simulador pueden aislarse por sesión o contar con un botón de1 clic en la barra de evaluación (`[ 🔄 Restaurar catálogo original de Norma ]`) para evitar que terceros dejen la página vacía antes de que entre el juez.

---

## 4. Máquina de Estados Finita (FSM) del Chatbot

```text
[IDLE (Esperando mensaje de Norma)]
 │
 ├── 1. Entra FOTO sola ──────────► Guarda imagen temporal en sesión
 │                                  Estado -> [AWAITING_DETAILS]
 │                                  Audio Bot: "Qué linda pieza Norma. Mandame un audio diciéndome cómo se llama y el precio, o decime de qué pieza es la foto nueva."
 │
 ├── 2. Entra AUDIO solo ─────────► Transcribe voz y extrae intención:
 │     ├─ Intención: UNDO ("Me equivoqué") ──► Restaura snapshot previo -> [IDLE]
 │     │                                       Audio Bot: "Listo Norma, ya volví todo como estaba antes."
 │     └─ Intención: UPDATE_PRICE / SOLD ────► Busca pieza por #Número o similitud de nombre
 │                                             Estado -> [AWAITING_CONFIRMATION]
 │                                             Audio Bot: "¿Confirmás cambiar el precio de la Pieza #2 a $15.000?"
 │
 └── 3. Entra FOTO + AUDIO ───────► Transcribe voz y detecta si es pieza nueva (CREATE) o reemplazo de foto (UPDATE_IMAGE)
                                    Estado -> [AWAITING_CONFIRMATION]
                                    Audio Bot: "Recibido. ¿Publico el Jarrón de Gres a $18.000?"

[AWAITING_DETAILS (Tiene foto temporal, falta contexto)]
 │
 └── Entra AUDIO o TEXTO ─────────► Extrae nombre + precio (CREATE) o #Pieza destino (UPDATE_IMAGE)
                                    Estado -> [AWAITING_CONFIRMATION]
                                    Audio Bot: "Perfecto. ¿Confirmás subir la Taza Panzona a $12.000?"

[AWAITING_CONFIRMATION (Esperando el Sí o No de Norma)]
 │
 ├── Respuesta Afirmativa ("Sí", "Dale", "Subilo", "Está bien")
 │     └── Guarda snapshot previo -> Impacta en Base de Datos -> Emite evento en tiempo real a la Web -> [IDLE]
 │         Audio Bot: "¡Listo Norma! Ya quedó actualizado en la página."
 │
 └── Respuesta Negativa o Corrección ("No", "Pará", "No, dije dieciocho mil")
       └── Si incluye corrección, actualiza el borrador y vuelve a preguntar; si es cancelación, limpia sesión -> [IDLE]
           Audio Bot: "Menos mal que me avisaste. Cancelé el cambio, mandame de nuevo tranqui."
```

---

## 5. Checklist de Calidad Anti "AI Quirks"

Para asegurar puntaje máximo en la evaluación funcional y de performance:

- [ ] **Cero Spanglish en la interfaz:** Ningún texto dirá *"Submit"*, *"Loading"*, *"Upload"*, *"Stock"*, *"Admin"* ni *"Reset"*. Todo el vocabulario es cercano y argentino (*"Subiendo foto..."*, *"Pieza disponible"*, *"Vendida"*).
- [ ] **Formato numérico local:** Todos los precios usan punto para los miles y sin centavos (`$ 15.000`).
- [ ] **Tolerancia a números hablados:** El parser convierte sin fallar expresiones como *"quince mil"*, *"15 lucas"* o *"dieciocho quinientos"* a enteros (`15000`, `18500`).
- [ ] **Cold Start = 0 ms:** El servidor y la vista pública deben responder instantáneamente en la primera visita del juez, evitando servicios gratuitos que invernan tras 15 minutos de inactividad.
- [ ] **Grilla Inquebrantable:** Las tarjetas mantienen exactamente la misma altura y alineación sin importar si el título tiene 10 o 60 caracteres, o si la foto original era vertical u horizontal.

---

## 6. Estructura del Hilo de Entrega (Twitter / X)

1. **Tweet 1 (Gancho + Link directo + Video de 20s):** Presentación del problema real (adaptar la tecnología a los 70 años de Norma y no al revés). Video corto mostrando el catálogo artesanal a la izquierda y el WhatsApp de Norma a la derecha actualizando una pieza por voz en vivo.
2. **Tweet 2 (Empatía con la Profesora de Plástica):** Explicación del diseño visual (estética de taller, alto contraste, lector en voz alta para compradoras que no ven bien y fidelidad 100% al boceto sin carritos ni links).
3. **Tweet 3 (El Backoffice por Audio y Prevención de Errores):** Cómo funciona la Máquina de Estados, las respuestas con voz argentina para que Norma no tenga que leer letra chica, el numerito de estantería (`#1`, `#2`) y el comando *"¡Uy, me equivoqué!"*.
4. **Tweet 4 (Ingeniería Invisible y Performance):** Detalles del pipeline de optimización de imágenes a WebP y el modo Sandbox para probar todo en un clic sin logins.