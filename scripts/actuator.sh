#!/usr/bin/env bash
#
# Script Name: actuator.sh
# Description: Hit a coinservice actuator endpoint using a client-credentials token
# Usage: actuator.sh --tenant TENANT_ID --host HOST ENDPOINT
#

set -euo pipefail
IFS=$'\n\t'

SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"
readonly SCRIPT_NAME

SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
readonly SCRIPT_DIR

TENANT=""
HOST=""
ENDPOINT=""
INSECURE=""

usage() {
  cat << EOF
Usage: ${SCRIPT_NAME} [--insecure] --tenant TENANT_ID --host HOST ENDPOINT

Options:
    --tenant TENANT_ID   Azure AD tenant ID
    --host HOST          Host and optional port (e.g. coinservice.example.com or localhost:8443)
    --insecure           Skip TLS certificate verification (for local self-signed certs)

Arguments:
    ENDPOINT             Actuator path (e.g. health, info, metrics/jvm.memory.used)

Examples:
    ${SCRIPT_NAME} --insecure --tenant <tenant-id> --host localhost:8443 info
    ${SCRIPT_NAME} --tenant <tenant-id> --host coinservice.example.com loggers/ROOT
EOF
  exit 1
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --tenant) TENANT="$2"; shift 2 ;;
      --host)     HOST="$2";     shift 2 ;;
      --insecure) INSECURE="-k"; shift ;;
      -h|--help)  usage ;;
      -*) echo "Unknown option: $1" >&2; usage ;;
      *)  ENDPOINT="$1"; shift ;;
    esac
  done
}

main() {
  parse_args "$@"

  [[ -z "$TENANT"   ]] && { echo "error: --tenant is required" >&2; usage; }
  [[ -z "$HOST"     ]] && { echo "error: --host is required" >&2;   usage; }
  [[ -z "$ENDPOINT" ]] && { echo "error: ENDPOINT is required" >&2; usage; }

  local token
  token=$("$SCRIPT_DIR/get-token.sh" \
    --tenant "$TENANT" \
    --client "AZURE-CLIENT-ID" \
    --secret "MY-API-KEY" \
    .default)

  local response
  response=$(curl --no-progress-meter --fail-with-body ${INSECURE} \
    "https://${HOST}/actuator/${ENDPOINT}" \
    -H "Authorization: Bearer ${token}") || {
    echo "error: curl failed (exit $?)" >&2
    echo "$response" >&2
    exit 1
  }

  echo "$response" | jq '.'
}

main "$@"
