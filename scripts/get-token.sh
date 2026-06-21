#!/usr/bin/env bash
set -euo pipefail

TENANT=""
CLIENT_SECRET_NAME=""
SECRET_SECRET_NAME=""
SCOPE_NAME=""

usage() {
  echo "Usage: $0 --tenant TENANT-ID --client CLIENT-SECRET-NAME --secret SECRET-SECRET-NAME SCOPE-NAME"
  echo "  SCOPE-NAME  scope name only (e.g. actuator.read) — URI is built automatically"
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tenant) TENANT="$2";             shift 2 ;;
    --client) CLIENT_SECRET_NAME="$2"; shift 2 ;;
    --secret) SECRET_SECRET_NAME="$2"; shift 2 ;;
    -*)       echo "Unknown option: $1"; usage ;;
    *)        SCOPE_NAME="$1";         shift ;;
  esac
done

[[ -z "$TENANT"             ]] && { echo "error: --tenant is required"; usage; }
[[ -z "$CLIENT_SECRET_NAME" ]] && { echo "error: --client is required"; usage; }
[[ -z "$SECRET_SECRET_NAME" ]] && { echo "error: --secret is required"; usage; }
[[ -z "$SCOPE_NAME"         ]] && { echo "error: scope name is required"; usage; }

KV=kv-coinservice
CLIENT_ID=$(az keyvault secret show --vault-name "$KV" --name "$CLIENT_SECRET_NAME" --query value -o tsv)
CLIENT_SECRET=$(az keyvault secret show --vault-name "$KV" --name "$SECRET_SECRET_NAME" --query value -o tsv)

SCOPE="api://${CLIENT_ID}/${SCOPE_NAME}"

curl -s -X POST \
  "https://login.microsoftonline.com/${TENANT}/oauth2/v2.0/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" \
  --data-urlencode "scope=${SCOPE}" \
  | jq -r '.access_token'
