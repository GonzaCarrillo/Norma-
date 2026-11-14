# Glosario de Dominio — El Taller de Norma

Este documento define el lenguaje ubicuo para la administración conversacional del catálogo artesanal de El Taller de Norma dentro del entorno de evaluación. Describe los conceptos del dominio y la interacción de la artesana con su vidriera digital, manteniendo una terminología consistente y despojada de tecnicismos.

## Language

- **Pieza**: Unidad artesanal representada e identificada dentro del catálogo de una sesión de evaluación. No afirma autoría personal exclusiva, existencia de stock físico real ni unicidad en su fabricación.
  - _Avoid_: Producto comercial genérico, artículo seriado, mercadería masiva.

- **Número de pieza**: Referencia numérica entera y estable asignada a una pieza dentro de una sesión para identificarla y nombrarla en la conversación sin ambigüedad. No constituye un identificador global entre evaluadores ni se corresponde con la posición visual transitoria en la pantalla.
  - _Avoid_: Identificador global, posición visible en pantalla, orden de lista.

- **Catálogo**: Colección integral de las piezas artesanales registradas en una sesión de evaluación, abarcando tanto las piezas disponibles como las vendidas. No representa el inventario físico total del taller ni un depósito comercial central.
  - _Avoid_: Inventario físico del taller, depósito central, tienda externa.

- **Vidriera**: Vista pública que expone ante los clientes exclusivamente las piezas actualmente disponibles para la venta en la sesión. No exhibe piezas vendidas ni funciona como un panel de control o ajustes.
  - _Avoid_: Catálogo completo, carrito de compras, panel de control.

- **Pieza disponible**: Pieza artesanal activa que se exhibe en la vidriera pública y puede ser ofrecida, modificada en sus detalles o registrada como vendida. No implica una existencia de stock ilimitado ni reposición automática.
  - _Avoid_: Mercadería con stock ilimitado, producto masivo.

- **Pieza vendida**: Pieza artesanal marcada como vendida que permanece en el catálogo actual de la sesión pero deja de exhibirse en la vidriera. No constituye la eliminación definitiva de la pieza, aunque puede ser sustituida o revertida ante una restauración del catálogo o una acción de deshacer.
  - _Avoid_: Pieza eliminada, pieza borrada, baja de inventario.

- **Sesión de evaluación**: Entorno de trabajo aislado e independiente asignado a cada evaluador, que contiene una conversación propia y una copia autónoma del catálogo. No equivale a una cuenta de usuario autenticada con clave ni a una pestaña particular del navegador web.
  - _Avoid_: Cuenta de usuario autenticada, perfil de cliente, pestaña del navegador.

- **Catálogo inicial**: Plantilla canónica de ocho piezas de muestra utilizada para sembrar o restablecer el catálogo de una sesión de evaluación. No corresponde a la totalidad de la producción real de Norma ni a un catálogo maestro de fabricación definitiva.
  - _Avoid_: Catálogo de producción real de Norma, inventario maestro del taller.

- **Borrador**: Propuesta de modificación o alta en proceso de preparación durante el diálogo, que puede reunir datos parciales o completos pero aún no impacta en el catálogo ni en la vidriera. No constituye un cambio publicado ni una operación en firme.
  - _Avoid_: Cambio aplicado, orden de compra, carrito.

- **Propuesta pendiente de confirmación**: Borrador que ya reúne todos los datos requeridos y válidos para llevar a cabo una operación, encontrándose a la espera de la aprobación explícita de Norma. No representa un cambio ya aplicado ni una ejecución automática.
  - _Avoid_: Cambio ya aplicado, orden confirmada, confirmación automática.

- **Confirmación**: Consentimiento explícito expresado por la artesana que autoriza la aplicación confirmada de una propuesta preparada sobre el catálogo de la sesión. No se produce por guardado tácito ni por inferencias automáticas del asistente, manteniéndose disponible la posibilidad de deshacer el cambio.
  - _Avoid_: Guardado automático, confirmación implícita, cobro.

- **Cancelación**: Decisión explícita de desestimar el borrador o la propuesta en preparación, dejando el catálogo exactamente en el estado en que se encontraba sin alterar cambios confirmados previamente. No revierte modificaciones ya asentadas con anterioridad en el catálogo.
  - _Avoid_: Deshacer cambio confirmado, eliminar pieza, borrar sesión.

- **Deshacer**: Acción que revierte de manera inmediata y por única vez el último cambio confirmado sobre el catálogo, regresándolo al estado previo a dicha confirmación. No brinda un historial acumulativo infinito ni un recorrido por múltiples estados pasados.
  - _Avoid_: Cancelación de borrador, historial ilimitado de cambios, papelera de reciclaje.

- **Restauración del catálogo**: Acción que restablece las ocho piezas del catálogo inicial en la sesión y limpia borradores e historial de reversión, preservando la numeración histórica para no repetir números de pieza ya emitidos. No es un formateo general del sistema ni un reinicio de la numeración a cero.
  - _Avoid_: Reseteo general del sistema, reinicio de numeración a cero.

- **Alta de pieza**: Incorporación de una nueva pieza artesanal al catálogo y a la vidriera de la sesión, requiriendo obligatoriamente fotografía, título descriptivo y precio. No admite cargas incompletas ni publicaciones sin los datos obligatorios.
  - _Avoid_: Publicación sin foto o precio, importación masiva.

- **Cambio de precio**: Modificación del valor monetario de una pieza existente, fijado obligatoriamente en un número entero y positivo expresado en pesos argentinos. No contempla centavos, divisas extranjeras ni actualizaciones automáticas de margen.
  - _Avoid_: Cotización en moneda extranjera, precios con centavos o decimales.

- **Cambio de fotografía**: Actualización de la imagen que ilustra una pieza existente, conservando intactos su identidad, su número de pieza, su título y su precio. No genera una nueva pieza ni sustituye la pieza por otra.
  - _Avoid_: Creación de nueva pieza, sustitución de pieza, reasignación de número.

- **Registro de venta**: Asentamiento formal de que una pieza existente fue comercializada para que la vidriera deje de mostrarla públicamente, manteniéndola en el catálogo actual de la sesión. No involucra procesamiento de cobros ni la eliminación de la pieza del catálogo.
  - _Avoid_: Cobro de dinero, pasarela de pago, eliminación de pieza, orden de compra.

- **Asistente**: Interlocutor conversacional que interactúa con Norma, interpreta sus intenciones y redacta propuestas de cambio claras para su revisión, sin poder ejecutar modificaciones por propia decisión. No actúa como un agente decisor autónomo ni como un administrador del sistema.
  - _Avoid_: Operador autónomo, administrador del sistema, autorizador de cambios.

- **Ejemplo de demostración**: Mensaje o escenario predefinido disponible en la interfaz para ensayar y evaluar las interacciones del taller, sin constituir una grabación auténtica de la voz de Norma ni una transcripción en vivo. No es un canal de comunicación directo ni un mensaje real de clientes.
  - _Avoid_: Audio real de Norma, transcripción de voz en vivo, mensaje de cliente real.
