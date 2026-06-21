#!/usr/bin/env sh
set -eu

PORT="${1:-8080}"

if command -v lsof >/dev/null 2>&1; then
  pids=$(lsof -ti tcp:"$PORT" 2>/dev/null || true)
elif command -v fuser >/dev/null 2>&1; then
  pids=$(fuser "$PORT"/tcp 2>/dev/null || true)
elif command -v netstat >/dev/null 2>&1; then
  pids=$(netstat -ano 2>/dev/null | awk -v port=":$PORT" '$0 ~ port && $0 ~ /LISTEN/ {print $NF}' | sort -u)
else
  echo "No supported port inspection tool found; skipping port cleanup for $PORT."
  exit 0
fi

if [ -z "${pids:-}" ]; then
  echo "Port $PORT is free."
  exit 0
fi

for pid in $pids; do
  if [ "$pid" = "0" ] || [ "$pid" = "-" ]; then
    continue
  fi

  command_line=""
  if command -v ps >/dev/null 2>&1; then
    command_line=$(ps -p "$pid" -o args= 2>/dev/null || true)
  fi

  case "$command_line" in
    *PolicyIntelligenceApplication*|*gradle*bootRun*|*policy-intelligence-api.jar*)
      echo "Stopping stale Policy Intelligence API process $pid on port $PORT."
      kill "$pid" 2>/dev/null || taskkill //PID "$pid" //F >/dev/null 2>&1 || true
      ;;
    *)
      echo "Port $PORT is used by process $pid, but it does not look like this API. Leaving it alone."
      ;;
  esac
done
