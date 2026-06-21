#!/usr/bin/env sh
set -eu

ENV_FILE="${1:-.env.local}"

if [ ! -f "$ENV_FILE" ]; then
  return 0 2>/dev/null || exit 0
fi

echo "Loading local environment from $ENV_FILE"
while IFS= read -r raw_line || [ -n "$raw_line" ]; do
  line=$(printf '%s' "$raw_line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
  case "$line" in
    ""|\#*) continue ;;
  esac

  name=${line%%=*}
  value=${line#*=}
  name=$(printf '%s' "$name" | sed '1s/^\xef\xbb\xbf//;s/^[[:space:]]*//;s/[[:space:]]*$//')
  value=$(printf '%s' "$value" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//;s/^"//;s/"$//;s/^'\''//;s/'\''$//')

  if [ "$name" = "spring.ai.openai.api-key" ]; then
    name="OPENAI_API_KEY"
  fi

  export "$name=$value"
done < "$ENV_FILE"
