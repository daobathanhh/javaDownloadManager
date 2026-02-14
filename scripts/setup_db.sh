#!/bin/bash

set -euo pipefail

if [ ! -f .env ]; then
  echo ".env file not found"
  exit 1
fi

set -a
source .env
set +a

echo "Setting up MySQL database..."

sudo mysql <<EOF
CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS '${DB_USER}'@'${DB_HOST}'
  IDENTIFIED BY '${DB_PASSWORD}';

GRANT ALL PRIVILEGES
ON \`${DB_NAME}\`.* TO '${DB_USER}'@'${DB_HOST}';

FLUSH PRIVILEGES;
EOF

echo "Done!"
