#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
CORPUS_DIR="$PROJECT_ROOT/docs/evaluation/confusing-corpus"

if command -v curl.exe >/dev/null 2>&1; then
  CURL="curl.exe"
else
  CURL="curl"
fi

upload() {
  title="$1"
  file="$2"
  echo "Uploading $title"
  "$CURL" -fsS -X POST "$API_BASE_URL/api/v1/documents" \
    -F "title=$title" \
    -F "file=@$file;type=text/markdown" \
    -F "tenantId=default" \
    -F "department=Security" \
    -F "region=Global" \
    -F "documentType=Policy" \
    -F "classification=Restricted" \
    -F "strategy=SLIDING_WINDOW" \
    -F "chunkSize=900" \
    -F "overlap=120"
  echo ""
}

upload "Contractor Production Customer Data Access Standard" "$CORPUS_DIR/contractor-production-customer-data.md"
upload "Employee Production Customer Data Access Standard" "$CORPUS_DIR/employee-production-customer-data.md"
upload "Contractor Sandbox Customer Data Handling Standard" "$CORPUS_DIR/contractor-sandbox-customer-data.md"
upload "Vendor Analytics Customer Data Sharing Standard" "$CORPUS_DIR/vendor-analytics-customer-data.md"
upload "Employee Break Glass Production Access Standard" "$CORPUS_DIR/break-glass-production-access.md"

echo "Confusing corpus loaded. Run:"
echo "$CURL -X POST $API_BASE_URL/api/v1/evaluations/run-golden-questions"
