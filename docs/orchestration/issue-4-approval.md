# Aprobación de diseño — Issue #4

Orca aprueba issue-4-design.md v1.0 (commit e2b6f524ec8a09b2f12b0b12237b3920addcc01e).

Decisiones G0 aprobadas explícitamente por Orca:
- Formatos de entrada: allowlist estricta JPEG/JPG, PNG8 y WebP estáticos (animaciones y contenedores políglotas o no conformes rechazados).
- Límites de cámara: tamaño 1 a 15728640 bytes (15 MiB) inclusive, cada eje <= 16384 px, producto en long <= 24000000 px (24 MP).
- Metadata y dependencia: incorporación única de `com.drewnoakes:metadata-extractor:2.19.0`. Extracción estricta de bloque EXIF IFD0 (orientaciones 1 a 8) hasta 262144 bytes; rechazo 400 ante metadata corrupta, contradictoria o excesiva.
- Geometría y revelado: recorte centrado 4:3 determinista con factor `k = min(floor(Wo/4), floor(Ho/3))`, k=0 rechazado 400. Redimensionado uniforme a 800x600 sobre blanco #FFFFFF. Corrección sutil única sobre canales sRGB8: R'=clamp(round(R*1.03*1.01)), G'=clamp(round(G*1.03)), B'=clamp(round(B*1.03*0.99)).
- Codificación: WebP con pérdida explícito del provider instalado, exactamente hasta 8 intentos con calidades [80, 70, 60, 50, 40, 30, 20, 10]/100f sobre el mismo raster revelado. Éxito si 0 < bytes < 80000. Si ningún candidato cumple < 80000, error 400 específico sin reducir resolución.
- Concurrencia de aplicación: slot único activo de procesamiento fotográfico vía Semaphore.tryAcquire, fallo 500 por ocupación sin cola de rasters.
- Almacenamiento y serving: staging privado en `root/.processing/` con nombres generados por servidor, publicación mediante `ATOMIC_MOVE` a `root/<UUID>.webp`, bloqueo en MVC de acceso o traversal a `.processing` y 404 específico para medios faltantes o denegados sin exponer detalles privados.
- Recursos y procedencia: ocho recursos derivados de cuatro fotografías humanas existentes sujeto a trazabilidad honesta en docs/medios.md sin generación artificial ni atribución ficticia.
- Limpieza limitada: borrado en finally únicamente de temporales de la llamada en curso; errores posteriores al retorno de URL o fallos de disco preservan datos y emiten advertencia operativa sin romper el contrato del bot ni alterar Session/Product.

Fases autorizadas: B1 (validación y transformación), B2 (compresión, almacenamiento y serving), B3 (recursos, integración y evidencia). Gate de Orca requerido antes de cada transición de fase.
