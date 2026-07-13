#!/usr/bin/env bash
# Acorta una URL usando una instancia de Linker.
#
# Uso:
#   LINKER_HOST=http://<host>:8080 ./scripts/shorten-url.sh https://example.com
#
# Imprime la URL corta en stdout. Requiere curl y jq.

set -euo pipefail

LINKER_HOST="${LINKER_HOST:-http://localhost:8080}"

if [[ $# -ne 1 ]]; then
  echo "Uso: LINKER_HOST=http://<host>:8080 $0 <url-a-acortar>" >&2
  exit 1
fi

URL="$1"

RESPONSE="$(curl -fsS -X POST "$LINKER_HOST/link" \
  -H "Content-Type: application/json" \
  -d "{\"url\":\"$URL\"}")"

SHORT_URL="$(printf '%s' "$RESPONSE" | jq -r '.shortUrl // empty')"

if [[ -z "$SHORT_URL" ]]; then
  echo "Linker no devolvió shortUrl. Respuesta: $RESPONSE" >&2
  exit 1
fi

echo "$SHORT_URL"
