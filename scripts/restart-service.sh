#!/usr/bin/env sh
set -eu

SERVICE="${1:-}"
if [ -z "$SERVICE" ]; then
  echo "Usage: sh scripts/restart-service.sh <postgres|ml-service|api|ui>"
  exit 1
fi

case "$SERVICE" in
  postgres|ml-service|api|ui)
    ;;
  *)
    echo "Unknown service: $SERVICE"
    echo "Allowed services: postgres, ml-service, api, ui"
    exit 1
    ;;
esac

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
cd "$PROJECT_ROOT"

docker compose restart "$SERVICE"
