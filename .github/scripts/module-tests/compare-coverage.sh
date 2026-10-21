#!/usr/bin/env bash
# Fails when the Sonar coverage of the analysed branch is lower than the base branch.
# Replaces the "Compare Coverage Results" stage (sonarUtils.getCoverageWithRetry) of the Jenkins pipelines:
# instead of polling analyses by commit, it waits for the Compute Engine task of this scan.
#
# Usage: compare-coverage.sh <report-task-file> <branch> <base-branch>
# Env: SONAR_HOST_URL, SONAR_TOKEN
set -euo pipefail

REPORT_TASK="${1:?report-task.txt path is required}"
BRANCH="${2:?branch is required}"
BASE_BRANCH="${3:?base branch is required}"
SONAR_HOST_URL="${SONAR_HOST_URL%/}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-900}"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"

sonar_get() {
  curl -sf -u "${SONAR_TOKEN}:" "$@"
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

coverage_of() {
  sonar_get --get "${SONAR_HOST_URL}/api/measures/component" \
    --data-urlencode "component=${project_key}" \
    --data-urlencode "branch=$1" \
    --data-urlencode "metricKeys=coverage" | jq -r '.component.measures[0].value // empty'
}

current="$(coverage_of "$BRANCH")"
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
  exit 1
fi
echo "✅ \`${BRANCH}\`: ${current}% ≥ \`${BASE_BRANCH}\`: ${base}%" >> "$SUMMARY"
