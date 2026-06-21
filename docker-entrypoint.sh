#!/usr/bin/env sh
set -eu

if [ -f /run/policy-secrets/openai_api_key ]; then
  OPENAI_API_KEY=$(cat /run/policy-secrets/openai_api_key)
  export OPENAI_API_KEY
fi

if [ -f /run/policy-secrets/llm_model ]; then
  LLM_MODEL=$(cat /run/policy-secrets/llm_model)
  export LLM_MODEL
fi

exec java -jar /app/policy-intelligence-api.jar
