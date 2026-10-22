#!/usr/bin/env bash
# Fails when the Sonar coverage of the analysed branch is lower than the base branch.
# Replaces the "Compare Coverage Results" stage (sonarUtils.getCoverageWithRetry) of the Jenkins pipelines:
# instead of polling analyses by commit, it waits for the Compute Engine task of this scan.
#
# Usage: compare-coverage.sh <report-task-file> <branch> <base-branch>
# Env: SONAR_HOST_URL, SONAR_TOKEN, SONAR_READ_TOKEN (optional, needs Browse permission to read measures)
set -euo pipefail

REPORT_TASK="${1:?report-task.txt path is required}"
BRANCH="${2:?branch is required}"
BASE_BRANCH="${3:?base branch is required}"
SONAR_HOST_URL="${SONAR_HOST_URL%/}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-900}"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"
FORBIDDEN=3

sonar_get() {
  local body http_code token="${SONAR_API_TOKEN:-$SONAR_TOKEN}"
  body="$(curl -s -w '\n%{http_code}' -u "${token}:" "$@")"
  http_code="${body##*$'\n'}"
  body="${body%$'\n'*}"
  if [[ "$http_code" == 401 || "$http_code" == 403 ]] && [[ -n "${SKIP_ON_FORBIDDEN:-}" ]]; then
    return "$FORBIDDEN"
  fi
  if [[ "$http_code" != 2* ]]; then
    echo "::error::Sonar API call failed with HTTP ${http_code}: $* ${body}" >&2
    return 1
  fi
  echo "$body"
}

project_key="$(grep '^projectKey=' "$REPORT_TASK" | cut -d= -f2-)"
ce_task_url="$(grep '^ceTaskUrl=' "$REPORT_TASK" | cut -d= -f2-)"

echo "Waiting for Sonar to process the analysis (${ce_task_url})"
deadline=$((SECONDS + TIMEOUT_SECONDS))
while :; do
  status="$(sonar_get "$ce_task_url" | jq -r '.task.status')"
  case "$status" in
    SUCCESS) break ;;
    FAILED|CANCELED)
      echo "::error::Sonar analysis task ended with status ${status}"
      exit 1 ;;
  esac
  if (( SECONDS > deadline )); then
    echo "::error::Timed out waiting for the Sonar analysis task (last status: ${status})"
    exit 1
  fi
  sleep 15
done

# Analysis tokens can follow the CE task but cannot read measures: use the read token when available,
# otherwise only warn so a missing permission does not fail the build
SONAR_API_TOKEN="${SONAR_READ_TOKEN:-$SONAR_TOKEN}"
[[ -z "${SONAR_READ_TOKEN:-}" ]] && SKIP_ON_FORBIDDEN=1

coverage_of() {
  sonar_get --get "${SONAR_HOST_URL}/api/measures/component" \
    --data-urlencode "component=${project_key}" \
    --data-urlencode "branch=$1" \
    --data-urlencode "metricKeys=coverage" | jq -r '.component.measures[0].value // empty'
}

current="$(coverage_of "$BRANCH")" || {
  rc=$?
  if (( rc == FORBIDDEN )); then
    echo "::warning::The Sonar token cannot read measures. Set the SONAR_READ_TOKEN secret (Browse permission). Skipping coverage comparison."
    echo "⚠️ Coverage comparison skipped: set \`SONAR_READ_TOKEN\` with Browse permission" >> "$SUMMARY"
    exit 0
  fi
  exit "$rc"
}
base="$(coverage_of "$BASE_BRANCH" || true)"
echo "Coverage ${BRANCH}: ${current:-n/a}% | ${BASE_BRANCH}: ${base:-n/a}%"

echo "### Coverage" >> "$SUMMARY"
if [[ -z "$current" ]]; then
  echo "::error::Coverage for ${BRANCH} not found in Sonar"
  exit 1
fi
if [[ -z "$base" ]]; then
  echo "::warning::No coverage found for base branch ${BASE_BRANCH}, skipping comparison"
  echo "\`${BRANCH}\`: ${current}% (no baseline for \`${BASE_BRANCH}\`)" >> "$SUMMARY"
  exit 0
fi

if awk -v c="$current" -v b="$base" 'BEGIN { exit !(c < b) }'; then
  echo "::error::Coverage (${current}%) is lower than ${BASE_BRANCH} branch (${base}%)"
  echo "🚨 \`${BRANCH}\`: ${current}% < \`${BASE_BRANCH}\`: ${base}%" >> "$SUMMARY"
  echo "decreased=true" >> "${GITHUB_OUTPUT:-/dev/null}"
  exit 1
fi
echo "✅ \`${BRANCH}\`: ${current}% ≥ \`${BASE_BRANCH}\`: ${base}%" >> "$SUMMARY"
