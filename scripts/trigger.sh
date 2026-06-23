#!/usr/bin/env bash
#
# Script Name: trigger.sh
# Description: Manually trigger a coinservice cron job
# Usage: trigger.sh --tenant TENANT_ID --host HOST JOB_NAME
#        trigger.sh --tenant TENANT_ID --host HOST --list
#

set -euo pipefail
IFS=$'\n\t'

SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"
readonly SCRIPT_NAME

SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
readonly SCRIPT_DIR

TENANT=""
HOST=""
JOB_NAME=""
INSECURE=""
LIST=false

usage() {
  cat << EOF
Usage: ${SCRIPT_NAME} [--insecure] --tenant TENANT_ID --host HOST JOB_NAME
       ${SCRIPT_NAME} [--insecure] --tenant TENANT_ID --host HOST --list

Options:
    --tenant TENANT_ID   Azure AD tenant ID
    --host HOST          Host and optional port (e.g. coinservice.example.com or localhost:8443)
    --insecure           Skip TLS certificate verification (for local self-signed certs)
    --list               List available cron jobs instead of triggering one

Arguments:
    JOB_NAME             Name of the cron job to trigger (e.g. createEpoch)

Examples:
    ${SCRIPT_NAME} --insecure --tenant <tenant-id> --host localhost:8443 createEpoch
    ${SCRIPT_NAME} --tenant <tenant-id> --host coinservice.example.com --list
EOF
  exit 1
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --tenant)   TENANT="$2";   shift 2 ;;
      --host)     HOST="$2";     shift 2 ;;
      --insecure) INSECURE="-k"; shift ;;
      --list)     LIST=true;     shift ;;
      -h|--help)  usage ;;
      -*) echo "Unknown option: $1" >&2; usage ;;
      *)  JOB_NAME="$1"; shift ;;
    esac
  done
}

main() {
  parse_args "$@"

  [[ -z "$TENANT" ]] && { echo "error: --tenant is required" >&2; usage; }
  [[ -z "$HOST"   ]] && { echo "error: --host is required" >&2;   usage; }

  if [[ "$LIST" == false && -z "$JOB_NAME" ]]; then
    echo "error: JOB_NAME is required (or use --list)" >&2
    usage
  fi

  local token
  token=$("$SCRIPT_DIR/get-token.sh" \
    --tenant "$TENANT" \
    --client "AZURE-CLIENT-ID" \
    --secret "MY-API-KEY" \
    .default)

  if [[ "$LIST" == true ]]; then
    local response
    response=$(curl --no-progress-meter --fail-with-body ${INSECURE} \
      "https://${HOST}/v1/trigger/jobs" \
      -H "Authorization: Bearer ${token}") || {
      echo "error: curl failed (exit $?)" >&2
      echo "$response" >&2
      exit 1
    }
    echo "$response" | jq '.'
  else
    curl --no-progress-meter --fail-with-body ${INSECURE} \
      -X POST \
      "https://${HOST}/v1/trigger/${JOB_NAME}" \
      -H "Authorization: Bearer ${token}" || {
      echo "error: curl failed (exit $?)" >&2
      exit 1
    }
  fi
}

main "$@"
