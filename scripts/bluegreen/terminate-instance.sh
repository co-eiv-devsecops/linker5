#!/usr/bin/env bash
#
# Terminate an instance by OCID and wait until it is fully TERMINATED.
# Used by blue-green to retire the old (blue) instance on success, or to roll
# back the new (green) instance on failure.
#
# Usage:
#   terminate-instance.sh <instance-ocid>
#
# Requires: oci CLI (authenticated).
#
set -euo pipefail

INSTANCE_OCID="${1:?instance OCID required}"

echo "[terminate] Terminating instance ${INSTANCE_OCID} (boot volume included)..." >&2
oci compute instance terminate \
  --instance-id "${INSTANCE_OCID}" \
  --preserve-boot-volume false \
  --force \
  --wait-for-state TERMINATED

echo "[terminate] Instance ${INSTANCE_OCID} terminated." >&2
