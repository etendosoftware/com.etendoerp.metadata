#!/usr/bin/env bash
# Code and schema checks run after the environment is built.
# Port of codeSchemaVerification.groovy from the Jenkins pipelines.
#
# Usage: code-schema-verification.sh <module-dir> <stack>
# Env: PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE
set -uo pipefail

MODULE_DIR="${1:?module dir is required}"
STACK="${2:-DEFAULT}"
SQL_DIR="$(dirname "$0")/sql"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"
failed=0

echo "### Code and Schema Verification" >> "$SUMMARY"

if [[ "$STACK" != "BACKPORT" ]]; then
  commons_lang="$(grep -rlF --exclude-dir={.git,pipelines} --include='*.java' 'org.apache.commons.lang.' "$MODULE_DIR")"
  if [[ -n "$commons_lang" ]]; then
    echo "::error::'org.apache.commons.lang' is used in the module. Replace it with 'org.apache.commons.lang3'."
    {
      echo "🚨 \`org.apache.commons.lang\` is used, replace it with \`org.apache.commons.lang3\`:"
      echo '```'
      echo "$commons_lang"
      echo '```'
    } >> "$SUMMARY"
    failed=1
  fi
fi

run_check() {
  local title="$1" sql_file="$2" result
  if ! result="$(psql -v ON_ERROR_STOP=1 -t -A -F '|' -f "$sql_file" | cut -d '|' -f1)"; then
    echo "::error::Could not run ${sql_file}"
    failed=1
    return
  fi
  if [[ -n "$result" ]]; then
    echo "::error::${title}: $(echo "$result" | paste -sd ',' -)"
    printf '🚨 %s:\n```\n%s\n```\n' "$title" "$result" >> "$SUMMARY"
    failed=1
  fi
}

run_check "Duplicate constraints detected in the DB" "${SQL_DIR}/check_duplicate_constraints.sql"
run_check "Orphaned triggers detected in the DB" "${SQL_DIR}/check_orphaned_triggers.sql"

if (( failed )); then
  exit 1
fi
echo "✅ No issues found" >> "$SUMMARY"
