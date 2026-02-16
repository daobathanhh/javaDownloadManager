#!/usr/bin/env bash
# Manual gRPC flow: CreateAccount -> CreateSession -> GetAccount (with Bearer token).
# Usage: ./scripts/grpc-test.sh [host:port]
# Example: ./scripts/grpc-test.sh localhost:9090
# Requires: grpcurl, jq. Start the app first (e.g. ./gradlew bootRun).
set -e
HOST="${1:-localhost:9090}"
PROTO="src/main/proto/jdm.proto"
IMPORT="src/main/proto"

echo "=== CreateAccount ==="
CREATE=$(grpcurl -plaintext -proto "$PROTO" -import-path "$IMPORT" \
  -d '{"account_name":"testuser","password":"secret123","email":"test@example.com"}' \
  "$HOST" jdm.v1.AccountService/CreateAccount)
echo "$CREATE"

echo ""
echo "=== CreateSession (login) ==="
SESSION=$(grpcurl -plaintext -proto "$PROTO" -import-path "$IMPORT" \
  -d '{"account_name":"testuser","password":"secret123"}' \
  "$HOST" jdm.v1.SessionService/CreateSession)
echo "$SESSION"

TOKEN=$(echo "$SESSION" | jq -r '.accessToken')
if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "No access token in response. Check CreateSession output above."
  exit 1
fi
echo "Access token (first 50 chars): ${TOKEN:0:50}..."

ACCOUNT_ID=$(echo "$CREATE" | jq -r '.account.id')
echo ""
echo "=== GetAccount (with auth) for account_id=$ACCOUNT_ID ==="
grpcurl -plaintext -proto "$PROTO" -import-path "$IMPORT" \
  -H "authorization: Bearer $TOKEN" \
  -d "{\"account_id\":$ACCOUNT_ID}" \
  "$HOST" jdm.v1.AccountService/GetAccount

echo ""
echo "Done."
