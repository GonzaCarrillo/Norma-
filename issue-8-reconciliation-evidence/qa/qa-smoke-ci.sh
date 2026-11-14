#!/usr/bin/env bash
# Smoke test CI corregido (design v2.1 / verify.yml) - ejecucion real sobre imagen QA
set -euo pipefail

CHECKOUT="/home/gonzalocar/orca/workspaces/Norma-/reconcile-8-deployment"
EVID="$CHECKOUT/issue-8-reconciliation-evidence/qa"
IMG="norma-issue8-qa2:re-audit"
SMOKE_ID="re-audit-$RANDOM"
CONT_NAME="norma-qa2-smoke-${SMOKE_ID}"
RECREATED_NAME="norma-qa2-recreated-${SMOKE_ID}"
VOL_NAME="norma-qa2-vol-${SMOKE_ID}"

mkdir -p "$EVID"

cleanup() {
  local exit_code=$?
  echo "Ejecutando limpieza CI (codigo de salida: $exit_code)..."
  if [ "$exit_code" -ne 0 ]; then
    echo "--- Logs contenedor principal ($CONT_NAME) ---"
    docker logs "$CONT_NAME" 2>/dev/null || true
    echo "--- Logs contenedor recreado ($RECREATED_NAME) ---"
    docker logs "$RECREATED_NAME" 2>/dev/null || true
  fi
  docker stop "$CONT_NAME" 2>/dev/null || true
  docker rm "$CONT_NAME" 2>/dev/null || true
  docker stop "$RECREATED_NAME" 2>/dev/null || true
  docker rm "$RECREATED_NAME" 2>/dev/null || true
  docker volume rm "$VOL_NAME" 2>/dev/null || true
}
trap cleanup EXIT

docker volume create "$VOL_NAME" >/dev/null
echo "VOL_NAME=$VOL_NAME" > "$EVID/qa2-vol-name.txt"

docker run -d \
  --name "$CONT_NAME" \
  -p 0:8080 \
  -v "$VOL_NAME:/data" \
  "$IMG" > "$EVID/qa2-container-id.txt"

HOST_PORT=$(docker port "$CONT_NAME" 8080 | head -n 1 | sed 's/.*://')
echo "HOST_PORT=$HOST_PORT" > "$EVID/qa2-port.txt"
echo "Contenedor iniciado en puerto host dinamico: $HOST_PORT"

# Health via docker inspect State.Health.Status (no solo curl)
for i in $(seq 1 40); do
  HEALTH=$(docker inspect "$CONT_NAME" --format='{{.State.Health.Status}}' 2>/dev/null || echo "none")
  echo "intento $i: State.Health.Status=$HEALTH"
  if [ "$HEALTH" = "healthy" ]; then
    echo "Contenedor reportado healthy por Docker healthcheck en intento $i"
    break
  fi
  sleep 2
done
echo "HEALTH_FINAL=$HEALTH" > "$EVID/qa2-health-status.txt"

# curl healthping
curl -sS -i "http://127.0.0.1:$HOST_PORT/api/ping" > "$EVID/qa2-ping-response.txt" 2>&1
echo "=== ping ==="; cat "$EVID/qa2-ping-response.txt"

SESS_ID="ci-session-valid-1"

# 1. Catalogo inicial via parser python3
curl -sS -f --max-time 5 "http://127.0.0.1:$HOST_PORT/api/products?sessionId=$SESS_ID" > "$EVID/qa2-catalog-init.json"
python3 - <<'PY' "$EVID/qa2-catalog-init.json"
import json, sys
with open(sys.argv[1]) as f:
    items = json.load(f)
assert len(items) == 8, f"esperados 8, encontrados {len(items)}"
for p in items:
    for field in ["pieceNumber","title","price","imageUrl","sold"]:
        assert field in p, f"campo {field} ausente en {p}"
p2 = next(p for p in items if p["pieceNumber"] == 2)
assert p2["price"] == 18500, f"precio pieza2 esperado 18500, {p2['price']}"
print("Catalogo inicial OK: 8 productos, campos validos, pieza2=18500")
PY

# 2. Propuesta de mutacion de precio
curl -sS -f --max-time 5 -X POST \
  -F "sessionId=$SESS_ID" \
  -F "text=A la numero 2 subila a veinte mil pesos" \
  "http://127.0.0.1:$HOST_PORT/api/bot/message" > "$EVID/qa2-proposal-price.json"
python3 - <<'PY' "$EVID/qa2-proposal-price.json"
import json, sys
with open(sys.argv[1]) as f:
    d = json.load(f)
assert d.get("state") == "AWAITING_CONFIRMATION", f"estado esperado AWAITING_CONFIRMATION, {d.get('state')}"
print("Propuesta OK: AWAITING_CONFIRMATION")
PY

# 3. Cancelar ("No") y verificar no-mutacion
curl -sS -f --max-time 5 -X POST \
  -F "sessionId=$SESS_ID" \
  -F "text=No" \
  "http://127.0.0.1:$HOST_PORT/api/bot/message" > "$EVID/qa2-cancel-no.json"
python3 - <<'PY' "$EVID/qa2-cancel-no.json"
import json, sys
with open(sys.argv[1]) as f:
    d = json.load(f)
assert d.get("state") == "IDLE", f"estado tras cancel IDLE, {d.get('state')}"
print("Cancelacion OK: IDLE")
PY
curl -sS -f --max-time 5 "http://127.0.0.1:$HOST_PORT/api/products?sessionId=$SESS_ID" > "$EVID/qa2-catalog-cancel.json"
python3 - <<'PY' "$EVID/qa2-catalog-cancel.json"
import json, sys
with open(sys.argv[1]) as f:
    items = json.load(f)
p2 = next(p for p in items if p["pieceNumber"] == 2)
assert p2["price"] == 18500, f"precio pieza2 tras cancel esperado 18500, {p2['price']}"
print("Pieza2 conserva 18500 tras cancelacion")
PY

# 4. Proponer y confirmar ("Si")
curl -sS -f --max-time 5 -X POST \
  -F "sessionId=$SESS_ID" \
  -F "text=A la numero 2 subila a veinte mil pesos" \
  "http://127.0.0.1:$HOST_PORT/api/bot/message" > "$EVID/qa2-proposal2-price.json"
curl -sS -f --max-time 5 -X POST \
  -F "sessionId=$SESS_ID" \
  -F "text=Si" \
  "http://127.0.0.1:$HOST_PORT/api/bot/message" > "$EVID/qa2-confirm-price.json"
python3 - <<'PY' "$EVID/qa2-confirm-price.json"
import json, sys
with open(sys.argv[1]) as f:
    d = json.load(f)
assert d.get("state") == "IDLE", f"estado tras confirm IDLE, {d.get('state')}"
print("Confirmacion OK: IDLE")
PY
curl -sS -f --max-time 5 "http://127.0.0.1:$HOST_PORT/api/products?sessionId=$SESS_ID" > "$EVID/qa2-catalog-confirmed.json"
python3 - <<'PY' "$EVID/qa2-catalog-confirmed.json"
import json, sys
with open(sys.argv[1]) as f:
    items = json.load(f)
p2 = next(p for p in items if p["pieceNumber"] == 2)
assert p2["price"] == 20000, f"precio mutado esperado 20000, {p2['price']}"
print("Pieza2 mutada a 20000 confirmada")
PY

# 5. Reinicio contenedor + re-evaluar puerto dinamico
docker restart "$CONT_NAME" > "$EVID/qa2-restart.txt"
HOST_PORT=$(docker port "$CONT_NAME" 8080 | head -n 1 | sed 's/.*://')
echo "HOST_PORT_RESTART=$HOST_PORT" > "$EVID/qa2-port-restart.txt"
echo "Puerto dinamico tras restart: $HOST_PORT"
for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "http://127.0.0.1:$HOST_PORT/api/ping" 2>/dev/null || echo "000")
  body=$(curl -s --max-time 3 "http://127.0.0.1:$HOST_PORT/api/ping" 2>/dev/null || echo "")
  if [ "$code" = "200" ] && [ "$body" = "PONG" ]; then
    echo "Reiniciado listo en intento $i"
    break
  fi
  sleep 2
done
curl -sS -i "http://127.0.0.1:$HOST_PORT/api/ping" > "$EVID/qa2-ping-post-restart.txt" 2>&1
curl -sS -f --max-time 5 "http://127.0.0.1:$HOST_PORT/api/products?sessionId=$SESS_ID" > "$EVID/qa2-catalog-restart.json"
python3 - <<'PY' "$EVID/qa2-catalog-restart.json"
import json, sys
with open(sys.argv[1]) as f:
    items = json.load(f)
p2 = next(p for p in items if p["pieceNumber"] == 2)
assert p2["price"] == 20000, f"persistencia post-restart fallida: esperado 20000, {p2['price']}"
print("Persistencia post-restart OK: pieza2=20000")
PY

# 6. Recreacion con MISMO volumen
docker stop "$CONT_NAME" > "$EVID/qa2-stop.txt"
docker rm "$CONT_NAME" > "$EVID/qa2-rm.txt"
docker run -d \
  --name "$RECREATED_NAME" \
  -p 0:8080 \
  -v "$VOL_NAME:/data" \
  "$IMG" > "$EVID/qa2-container2-id.txt"
RECREATED_PORT=$(docker port "$RECREATED_NAME" 8080 | head -n 1 | sed 's/.*://')
echo "RECREATED_PORT=$RECREATED_PORT" > "$EVID/qa2-port2.txt"
for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "http://127.0.0.1:$RECREATED_PORT/api/ping" 2>/dev/null || echo "000")
  body=$(curl -s --max-time 3 "http://127.0.0.1:$RECREATED_PORT/api/ping" 2>/dev/null || echo "")
  if [ "$code" = "200" ] && [ "$body" = "PONG" ]; then
    echo "Recreado listo en intento $i"
    break
  fi
  sleep 2
done
curl -sS -i "http://127.0.0.1:$RECREATED_PORT/api/ping" > "$EVID/qa2-ping-post-recreate.txt" 2>&1
curl -sS -f --max-time 5 "http://127.0.0.1:$RECREATED_PORT/api/products?sessionId=$SESS_ID" > "$EVID/qa2-catalog-recreated.json"
python3 - <<'PY' "$EVID/qa2-catalog-recreated.json"
import json, sys
with open(sys.argv[1]) as f:
    items = json.load(f)
p2 = next(p for p in items if p["pieceNumber"] == 2)
assert p2["price"] == 20000, f"persistencia post-recreacion fallida: esperado 20000, {p2['price']}"
print("Persistencia post-recreacion MISMO volumen OK: pieza2=20000")
PY

# nonroot + permisos /data
docker exec "$RECREATED_NAME" id > "$EVID/qa2-user-id.txt" 2>&1
docker exec "$RECREATED_NAME" ls -la /data > "$EVID/qa2-data-perms.txt" 2>&1
echo "=== nonroot ==="; cat "$EVID/qa2-user-id.txt"
echo "=== /data perms ==="; cat "$EVID/qa2-data-perms.txt"

# Traversal re-auditoria --path-as-is y encoded
curl -sS -i --path-as-is "http://127.0.0.1:$RECREATED_PORT/media/../norma.db" > "$EVID/qa2-traversal-path-as-is.txt" 2>&1
echo "=== traversal --path-as-is ==="; head -4 "$EVID/qa2-traversal-path-as-is.txt"
curl -sS -i "http://127.0.0.1:$RECREATED_PORT/media/%2e%2e%2fnorma.db" > "$EVID/qa2-traversal-encoded.txt" 2>&1
echo "=== traversal encoded ==="; head -4 "$EVID/qa2-traversal-encoded.txt"

echo "SMOKE CI RE-AUDIT COMPLETADO CON EXITO"
