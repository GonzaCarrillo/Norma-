# Decisiones y riesgos
- Spring Boot 3.5 admite Java 25; cumple 3.4+ sin fijar la versión ilustrativa 3.4.3.
- La sesión se crea una sola vez y persiste aunque quede sin piezas. DEFAULT es inmutable.
- SQLite serializa escrituras; snapshot, producto y estado conversacional cambian en una transacción.
- La confirmación consume el borrador; repetir “sí” no repite una operación.
- Una referencia ambigua nunca elige una pieza al azar.
- Sandbox público: conocer el identificador equivale a poseer la sesión. No es un sistema de administración de producción autenticado.
- Los objetivos de 0 ms de cold start y tiempos universales no se pueden garantizar: requieren instancia siempre encendida y medición.
- Thumbnailator no codifica WebP por sí solo: se agrega proveedor ImageIO WebP.
- El despliegue público requiere un destino y credenciales disponibles; Docker no equivale a publicación.
