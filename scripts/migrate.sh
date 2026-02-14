#!/bin/bash

set -euo pipefail

if [ ! -f .env ]; then
  echo ".env file not found"
  exit 1
fi

set -a
source .env
set +a

TEMP_CONFIG=$(mktemp)
envsubst < dbconfig.yml > "$TEMP_CONFIG"

~/go/bin/sql-migrate "$@" -config="$TEMP_CONFIG"

rm "$TEMP_CONFIG"
