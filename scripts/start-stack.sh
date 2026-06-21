#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
cd "$PROJECT_ROOT"

. "$SCRIPT_DIR/load-env.sh" ".env.local"

mkdir -p .secrets
if [ "${OPENAI_API_KEY:-}" ]; then
  printf '%s' "$OPENAI_API_KEY" > .secrets/openai_api_key
fi
if [ "${LLM_MODEL:-}" ]; then
  printf '%s' "$LLM_MODEL" > .secrets/llm_model
fi

echo "Building and starting Policy Intelligence stack..."
echo "1/4 Starting PostgreSQL and ML service..."
docker compose up -d --build --wait postgres ml-service

echo "2/4 Starting API..."
docker compose up -d --build --wait api

echo "3/4 Starting UI..."
docker compose up -d --build ui

echo "4/4 Current container status:"
docker compose ps

echo ""
echo "Policy Intelligence UI:  http://localhost:4200"
echo "Policy Intelligence API: http://localhost:8080"
echo "ML service health:       http://localhost:8090/health"
