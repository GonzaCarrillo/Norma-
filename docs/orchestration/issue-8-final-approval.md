# Fase 4 — Codex: issue #8

PASS. Entrega 10f5054 y delta final 7b4efea; QA completa 62f4115 y delta a6362c2. Revisé workflow final, Compose, documentación operativa, DeploymentSmokeTest y trazabilidad QA: 254 pruebas JDK25 verdes con log preservado, imagen independiente, Compose healthy, usuario no-root10001/GID observado999, persistencia de datos mutados y medios tras restart/recreación y recursos propios limpiados.

CI usa JSON estructurado para catálogo/estados, mutación explícita antes de comprobar persistencia, puertos dinámicos y trap de limpieza. El delta v2.2 amplía solo POST a25s para cubrir TTS12s; GET mantiene límite corto. Validación bash y ejecución smoke corregido constan en QA. No se atribuye ejecución remota Actions a esa ejecución local.

Backup se refiere al servicio Compose real mediante cp tras detener escrituras, sin suponer nombre de volumen prefijado. Se documentan permisos observados y ausencia de garantía universal del GID. DeploymentSmokeTest fija clavevacía y uploads temporales. No se cambió lógica productiva ni contratos/schema para facilitar Docker. Traversal probado con path-as-is404 y encoded400: la evidencia antigua HTTP500 no se describe como404.

Fuentes runtime/tests/buildDocker de 7b4efea son idénticas a10f5054; la reejecución del smoke afectado permite conservar suite254 y pruebas de contenedor ya acreditadas. Cambios posteriores son documentos/evidencia. Orca puede integrar si compara fuentes/configuración de build contra7b4efea antes de publicar/cerrar. No se afirma despliegue público ni SLA; resultado entregado es build/ejecución reproducible en los entornos verificados.
