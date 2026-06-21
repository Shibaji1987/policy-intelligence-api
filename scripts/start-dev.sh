#!/usr/bin/env sh
set -eu

PORT="${PORT:-8080}"
NO_DOCKER="${NO_DOCKER:-false}"

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
cd "$PROJECT_ROOT"

. "$SCRIPT_DIR/load-env.sh" ".env.local"

if [ "$NO_DOCKER" != "true" ]; then
  echo "Starting PGVector and ML service..."
  docker compose up -d --wait postgres ml-service
fi

"$SCRIPT_DIR/free-application-port.sh" "$PORT"

export SERVER_PORT="$PORT"
echo "Starting Policy Intelligence API on http://localhost:$PORT"
./gradlew bootRun --no-daemon
