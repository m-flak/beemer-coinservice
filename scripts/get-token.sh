#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

TENANT=""
CLIENT_SECRET_NAME=""
SECRET_SECRET_NAME=""
SCOPE_NAME=""

usage() {
  cat << EOF
Usage: $(basename "${BASH_SOURCE[0]}") --tenant TENANT-ID --client CLIENT-SECRET-NAME --secret SECRET-SECRET-NAME SCOPE-NAME
  SCOPE-NAME  scope name only (e.g. .default) — URI is built automatically
EOF
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tenant) TENANT="$2";             shift 2 ;;
    --client) CLIENT_SECRET_NAME="$2"; shift 2 ;;
    --secret) SECRET_SECRET_NAME="$2"; shift 2 ;;
    -h|--help) usage ;;
    -*)       echo "Unknown option: $1" >&2; usage ;;
    *)        SCOPE_NAME="$1";         shift ;;
  esac
done

[[ -z "$TENANT"             ]] && { echo "error: --tenant is required" >&2;     usage; }
[[ -z "$CLIENT_SECRET_NAME" ]] && { echo "error: --client is required" >&2;     usage; }
[[ -z "$SECRET_SECRET_NAME" ]] && { echo "error: --secret is required" >&2;     usage; }
[[ -z "$SCOPE_NAME"         ]] && { echo "error: scope name is required" >&2;   usage; }

# Returns the exp claim (Unix seconds) from a JWT, or empty string on failure.
jwt_exp() {
  python3 -c "
import sys, base64, json
payload = sys.argv[1].split('.')[1]
payload += '=' * (4 - len(payload) % 4)
print(json.loads(base64.urlsafe_b64decode(payload))['exp'])
" "$1" 2>/dev/null || true
}

cache_file="${HOME}/.cache/coinservice/token_${TENANT}"

if [[ -f "$cache_file" ]]; then
  cached_token=$(cat "$cache_file")
  exp=$(jwt_exp "$cached_token")
  now=$(date +%s)
  # Treat the token as expired 60 seconds early to avoid edge cases.
  if [[ -n "$exp" && "$exp" -gt $((now + 60)) ]]; then
    echo "$cached_token"
    exit 0
  fi
fi

KV=kv-coinservice
CLIENT_ID=$(az keyvault secret show --vault-name "$KV" --name "$CLIENT_SECRET_NAME" --query value -o tsv)
CLIENT_SECRET=$(az keyvault secret show --vault-name "$KV" --name "$SECRET_SECRET_NAME" --query value -o tsv)

token=$(curl -s -X POST \
  "https://login.microsoftonline.com/${TENANT}/oauth2/v2.0/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" \
  --data-urlencode "scope=api://${CLIENT_ID}/${SCOPE_NAME}" \
  | jq -r '.access_token')

[[ -z "$token" || "$token" == "null" ]] && { echo "error: failed to obtain access token" >&2; exit 1; }

mkdir -p "$(dirname "$cache_file")"
echo "$token" > "$cache_file"
chmod 600 "$cache_file"

echo "$token"
