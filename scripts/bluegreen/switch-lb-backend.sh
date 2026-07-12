#!/usr/bin/env bash
#
# Blue-green traffic switchover at the Load Balancer level:
#   1. Register the green instance in the LB backend set.
#   2. Wait until the green backend is healthy (status OK).
#   3. Remove the blue backend so all traffic flows to green.
#
# Backends in an OCI LB are addressed as "<ip>:<port>". The port is taken from
# an existing backend in the set (falls back to the provided/default port).
#
# Usage:
#   switch-lb-backend.sh <lb-ocid> <backend-set> <green-instance-ocid> <blue-instance-ocid> [port]
#
# Requires: oci CLI (authenticated) and jq.
#
set -euo pipefail

LB="${1:?load balancer OCID required}"
BSET="${2:?backend set name required}"
GREEN_OCID="${3:?green instance OCID required}"
BLUE_OCID="${4:?blue instance OCID required}"
DEFAULT_PORT="${5:-8080}"

log() { printf '[switch-lb] %s\n' "$*" >&2; }

private_ip() {
  oci compute instance list-vnics --instance-id "$1" | jq -r '.data[0]."private-ip"'
}

GREEN_IP="$(private_ip "$GREEN_OCID")"
BLUE_IP="$(private_ip "$BLUE_OCID")"

# Reuse the port of an existing backend if the set already has one.
PORT="$(oci lb backend-set get --load-balancer-id "$LB" --backend-set-name "$BSET" \
  --query 'data.backends[0].port' --raw-output 2>/dev/null || true)"
if [ -z "$PORT" ] || [ "$PORT" = "null" ]; then
  PORT="$DEFAULT_PORT"
fi

log "backend-set=${BSET} port=${PORT}"
log "green ip=${GREEN_IP}  blue ip=${BLUE_IP}"

log "Registering green backend ${GREEN_IP}:${PORT} ..."
oci lb backend create \
  --load-balancer-id "$LB" \
  --backend-set-name "$BSET" \
  --ip-address "$GREEN_IP" \
  --port "$PORT" \
  --wait-for-state SUCCEEDED >/dev/null 2>&1 || log "green backend may already exist; continuing"

# Diagnostics: show how the backend set health-checks its members, so a green
# that is up on localhost but CRITICAL at the LB is easy to explain (wrong
# port/path, or a security list that blocks the LB from reaching the backend).
log "Backend-set health checker config:"
oci lb backend-set get --load-balancer-id "$LB" --backend-set-name "$BSET" \
  --query 'data."health-checker"' 2>&1 | sed 's/^/[switch-lb]   /' >&2 || true

log "Waiting for the green backend to become healthy (status OK) ..."
health="UNKNOWN"
for i in $(seq 1 30); do
  health="$(oci lb backend-health get \
    --load-balancer-id "$LB" \
    --backend-set-name "$BSET" \
    --backend-name "${GREEN_IP}:${PORT}" \
    --query 'data.status' --raw-output 2>/dev/null || true)"
  log "  attempt ${i}: green backend health = ${health:-<unknown>}"
  if [ "$health" = "OK" ]; then
    break
  fi
  sleep 10
done

if [ "$health" != "OK" ]; then
  log "Green backend never became healthy; NOT removing blue. Aborting switchover."
  log "--- green backend health detail ---"
  oci lb backend-health get --load-balancer-id "$LB" --backend-set-name "$BSET" \
    --backend-name "${GREEN_IP}:${PORT}" 2>&1 | sed 's/^/[switch-lb]   /' >&2 || true
  log "--- blue backend health (for comparison) ---"
  oci lb backend-health get --load-balancer-id "$LB" --backend-set-name "$BSET" \
    --backend-name "${BLUE_IP}:${PORT}" 2>&1 | sed 's/^/[switch-lb]   /' >&2 || true
  exit 1
fi

log "Green is healthy. Removing blue backend ${BLUE_IP}:${PORT} ..."
oci lb backend delete \
  --load-balancer-id "$LB" \
  --backend-set-name "$BSET" \
  --backend-name "${BLUE_IP}:${PORT}" \
  --force \
  --wait-for-state SUCCEEDED >/dev/null 2>&1 || log "blue backend not found or already removed; continuing"

log "Traffic switched: green is now the only backend in ${BSET}."
