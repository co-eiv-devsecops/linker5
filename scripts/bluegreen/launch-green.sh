#!/usr/bin/env bash
#
# Launch a "green" Linker VM cloned from the current (blue) instance's placement.
# It derives compartment, availability domain, shape, shape-config, subnet and
# image from the blue instance, so blue-green needs no hardcoded OCIDs.
#
# Usage:
#   launch-green.sh <blue-instance-ocid> <ssh-public-key-file> <cloud-init-file> [display-name]
#
# Prints the new (green) instance OCID on stdout (all logs go to stderr).
# Requires: oci CLI (authenticated) and jq.
#
set -euo pipefail

BLUE_OCID="${1:?blue instance OCID required}"
SSH_PUBKEY_FILE="${2:?ssh public key file required}"
CLOUD_INIT_FILE="${3:?cloud-init file required}"
GREEN_NAME="${4:-linker5-green}"

log() { printf '[launch-green] %s\n' "$*" >&2; }

log "Reading placement from blue instance: ${BLUE_OCID}"
BLUE_JSON="$(oci compute instance get --instance-id "${BLUE_OCID}")"

COMPARTMENT_ID="$(printf '%s' "${BLUE_JSON}" | jq -r '.data."compartment-id"')"
AVAILABILITY_DOMAIN="$(printf '%s' "${BLUE_JSON}" | jq -r '.data."availability-domain"')"
SHAPE="$(printf '%s' "${BLUE_JSON}" | jq -r '.data.shape')"
IMAGE_ID="$(printf '%s' "${BLUE_JSON}" | jq -r '.data."image-id" // .data."source-details"."image-id" // empty')"
OCPUS="$(printf '%s' "${BLUE_JSON}" | jq -r '.data."shape-config".ocpus // empty')"
MEMORY_GBS="$(printf '%s' "${BLUE_JSON}" | jq -r '.data."shape-config"."memory-in-gbs" // empty')"

BLUE_VNIC="$(oci compute instance list-vnics --instance-id "${BLUE_OCID}")"
SUBNET_ID="$(printf '%s' "${BLUE_VNIC}" | jq -r '.data[0]."subnet-id"')"
# Copy the blue VNIC's NSGs so the Load Balancer health checker can reach green
# on the app port (same network security as blue; otherwise green is CRITICAL
# with "Connection failed" even though the app is up).
NSG_IDS="$(printf '%s' "${BLUE_VNIC}" | jq -c '.data[0]."nsg-ids" // []')"

if [ -z "${IMAGE_ID}" ] || [ "${IMAGE_ID}" = "null" ]; then
  IMAGE_ID="${IMAGE_OCID:?could not derive image OCID from blue; set IMAGE_OCID to override}"
fi

log "compartment=${COMPARTMENT_ID}"
log "ad=${AVAILABILITY_DOMAIN} shape=${SHAPE} subnet=${SUBNET_ID}"
log "nsg-ids=${NSG_IDS}"

launch_args=(
  --compartment-id "${COMPARTMENT_ID}"
  --availability-domain "${AVAILABILITY_DOMAIN}"
  --shape "${SHAPE}"
  --subnet-id "${SUBNET_ID}"
  --image-id "${IMAGE_ID}"
  --display-name "${GREEN_NAME}"
  --assign-public-ip false
  --ssh-authorized-keys-file "${SSH_PUBKEY_FILE}"
  --user-data-file "${CLOUD_INIT_FILE}"
  --wait-for-state RUNNING
  # Enable the Bastion plugin so the oci-bastion-deploy action can reach green
  # through the OCI Bastion managed-SSH session (same as the blue instance).
  --agent-config '{"areAllPluginsDisabled": false, "isManagementDisabled": false, "isMonitoringDisabled": false, "pluginsConfig": [{"name": "Bastion", "desiredState": "ENABLED"}]}'
)

# Flexible shapes require an explicit shape-config.
if printf '%s' "${SHAPE}" | grep -qi 'Flex'; then
  launch_args+=(--shape-config "{\"ocpus\": ${OCPUS:-1}, \"memoryInGBs\": ${MEMORY_GBS:-12}}")
fi

# Attach the same NSGs as blue so the LB can health-check green on the app port.
if [ -n "${NSG_IDS}" ] && [ "${NSG_IDS}" != "[]" ]; then
  launch_args+=(--nsg-ids "${NSG_IDS}")
fi

log "Launching green instance '${GREEN_NAME}'..."
GREEN_JSON="$(oci compute instance launch "${launch_args[@]}")"
GREEN_OCID="$(printf '%s' "${GREEN_JSON}" | jq -r '.data.id')"

if [ -z "${GREEN_OCID}" ] || [ "${GREEN_OCID}" = "null" ]; then
  log "Failed to obtain green instance OCID"
  exit 1
fi

log "Green instance is RUNNING: ${GREEN_OCID}"
printf '%s\n' "${GREEN_OCID}"
