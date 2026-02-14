#!/usr/bin/env bash

set -euo pipefail

#
# Generate Java classes and gRPC stubs from jdm.proto using Buf.
# Resolves buf/validate (Protovalidate) via buf.yaml deps; no manual protoc -I needed.
#
# Requirements on PATH:
#   - buf (Buf CLI, https://buf.build/docs/installation)
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"

echo "==> Generating Java + gRPC code (Buf + Protovalidate)"

if ! command -v buf >/dev/null 2>&1; then
  echo "Error: buf not found on PATH. Install the Buf CLI: https://buf.build/docs/installation" >&2
  exit 1
fi

cd "${PROJECT_ROOT}"

echo "Updating Buf dependencies (e.g. buf.build/bufbuild/protovalidate)..."
buf dep update

echo "Running buf generate..."
buf generate

echo "Done! Java code is under src/main/java (package com.java_download_manager.jdm.proto)."
