# Glosario de Dominio — El Taller de Norma

Este documento define el lenguaje ubicuo para la administración conversacional del catálogo artesanal de El Taller de Norma dentro del entorno de evaluación. Describe los conceptos del oficio y la interacción cotidiana de la artesana con su vidriera digital, manteniendo una terminología consistente y despojada de tecnicismos.

## Language

- **Pieza**: Unidad artesanal única creada manualmente por Norma e individualizada dentro del catálogo de la sesión de evaluación. No representa un artículo industrializado ni un producto seriado de comercio masivo.
  - _Avoid_: Producto genérico, artículo serial, ítem industrial, mercadería, SKU.

- **Número de pieza**: Referencia numérica entera y estable asignada a una pieza dentro de una sesión para identificarla y nombrarla en la conversación sin ambigüedad. No constituye un identificador global entre evaluadores ni se corresponde con la posición visual transitoria en la pantalla.
  - _Avoid_: Identificador global, ID de base de datos, código de barras, posición visible, índice de cuadrícula.

- **Catálogo**: Colección integral de las piezas artesanales registradas en una sesión de evaluación, abarcando tanto las piezas que se encuentran disponibles como las que ya fueron vendidas. No representa el inventario físico total del taller ni un depósito comercial central.
  - _Avoid_: Inventario físico general, stock central, depósito, base de datos externa, tienda en línea.

- **Vidriera**: Vista pública que expone ante los clientes exclusivamente las piezas actualmente disponibles para la venta en la sesión. No exhibe piezas vendidas ni funciona como un panel de control o ajustes.
  - _Avoid_: Catálogo completo, escaparate físico, carrito de compras, panel de administración.

- **Pieza disponible**: Pieza artesanal activa que se exhibe en la vidriera pública y puede ser ofrecida, modificada en sus detalles o registrada como vendida. No implica una existencia de stock ilimitado ni reposición automática.
  - _Avoid_: Producto en stock ilimitado, artículo mayorista, ítem genérico en inventario.

- **Pieza vendida**: Pieza artesanal que ya fue comercializada y que la vidriera oculta automáticamente, permaneciendo resguardada en el catálogo histórico de la sesión. No constituye la eliminación definitiva de la pieza ni el borrado de sus registros históricos.
  - _Avoid_: Pieza eliminada, pieza borrada, producto sin stock reponible, baja definitiva.

- **Sesión de evaluación**: Entorno de trabajo aislado e independiente asignado a cada evaluador, que contiene una conversación propia y una copia autónoma del catálogo. No equivale a una cuenta de usuario autenticada con clave ni a una pestaña particular del navegador web.
  - _Avoid_: Cuenta de usuario, perfil autenticado, pestaña de navegador, credencial de acceso.

- **Catálogo inicial**: Plantilla canónica de ocho piezas de muestra utilizada para sembrar o restablecer el catálogo de una sesión de evaluación. No corresponde a la totalidad de la producción real de Norma ni a un catálogo maestro de fabricación definitiva.
  - _Avoid_: Catálogo de producción real, inventario maestro del taller, catálogo definitivo de fábrica.

- **Borrador**: Propuesta de modificación o alta en proceso de preparación durante el diálogo, que puede reunir datos parciales o completos pero aún no impacta en el catálogo ni en la vidriera. No constituye un cambio publicado ni una operación en firme.
  - _Avoid_: Cambio publicado, transacción definitiva, orden abierta, carrito de compras.

- **Propuesta pendiente de confirmación**: Borrador que ya reúne todos los datos requeridos y válidos para llevar a cabo una operación, encontrándose a la espera de la aprobación explícita de Norma. No representa un cambio ya aplicado ni una ejecución automática.
  - _Avoid_: Operación ejecutada, cambio automático, orden procesada, confirmación asumida.

- **Confirmación**: Consentimiento explícito expresado por la artesana que autoriza formalmente la aplicación definitiva de una propuesta preparada sobre el catálogo de la sesión. No se produce por guardado tácito ni por inferencias automáticas del asistente.
  - _Avoid_: Auto-guardado, confirmación implícita, checkout, cobro.

- **Cancelación**: Decisión explícita de desestimar el borrador o la propuesta en preparación, dejando el catálogo exactamente en el estado en que se encontraba sin alterar cambios confirmados previamente. No revierte modificaciones ya asentadas con anterioridad en el catálogo.
  - _Avoid_: Deshacer cambio publicado, marcha atrás de catálogo, eliminación de pieza, borrado de sesión.

- **Deshacer**: Acción que revierte de manera inmediata y por única vez el último cambio confirmado sobre el catálogo, regresándolo al estado previo a dicha confirmación. No brinda un historial acumulativo infinito ni un recorrido por múltiples estados pasados.
  - _Avoid_: Cancelación de borrador, historial ilimitado, árbol de versiones múltiples, papelera de reciclaje.

- **Restauración del catálogo**: Acción que restablece las ocho piezas del catálogo inicial en la sesión y limpia borradores e historial de reversión, preservando la numeración histórica para no repetir números de pieza ya emitidos. No es un formateo general del sistema ni un reinicio de la numeración a cero.
  - _Avoid_: Reseteo de fábrica, formateo de base de datos, purga de sistema, reinicio del contador a cero.

- **Alta de pieza**: Incorporación de una nueva pieza artesanal al catálogo y a la vidriera de la sesión, requiriendo obligatoriamente fotografía, título descriptivo y precio. No admite cargas incompletas ni importaciones masivas de productos.
  - _Avoid_: Carga incompleta, importación por lote, duplicación de catálogo.

- **Cambio de precio**: Modificación del valor monetario de una pieza existente, fijado obligatoriamente en un número entero y positivo expresado en pesos argentinos. No contempla centavos, divisas extranjeras ni actualizaciones automáticas de margen.
  - _Avoid_: Cotización en dólares, centavos o decimales, ajuste automático de precios.

- **Cambio de fotografía**: Actualización de la imagen que ilustra una pieza existente, conservando intacta su identidad, su número de pieza y su información asociada. No crea una nueva pieza ni altera el historial del objeto artesanal.
  - _Avoid_: Creación de nueva pieza, reasignación de pieza, reemplazo de ítem en catálogo externo.

- **Registro de venta**: Asentamiento formal de que una pieza fue comercializada para que la vidriera la retire de la vista pública, manteniéndola archivada en el catálogo de la sesión. No involucra procesamiento de pagos, cobros bancarios ni eliminación del registro.
  - _Avoid_: Pasarela de pago, cobro de dinero, orden de despacho, baja definitiva de pieza, factura comercial.

- **Asistente**: Interlocutor conversacional que interactúa con Norma, interpreta sus intenciones y redacta propuestas de cambio claras para su revisión, sin poder ejecutar modificaciones por propia decisión. No actúa como un agente decisor autónomo ni como un administrador del sistema.
  - _Avoid_: Bot autónomo ejecutor, administrador del sistema, cajero automático, operador independiente.

- **Ejemplo de demostración**: Mensaje o escenario predefinido disponible en la interfaz para ensayar y evaluar las interacciones del taller, sin constituir una grabación auténtica de la voz de Norma ni una transcripción en vivo. No es un canal de comunicación directo ni un mensaje real de clientes.
  - _Avoid_: Audio auténtico de Norma, dictado en vivo por voz, mensaje de WhatsApp en directo, canal de clientes real.
