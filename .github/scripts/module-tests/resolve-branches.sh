#!/usr/bin/env bash
# Resolves which etendo_core branch and stack a module branch is tested against.
# Port of branchUtils.selectCoreBranch + moduleBranchContext from the Jenkins pipelines.
#
# Usage: resolve-branches.sh <commit-branch> <module-default-branch>
# Optional env: JIRA_ACCESS_TOKEN (base64 "email:token"), JIRA_BASE_URL, CORE_REPO_URL, CURRENT_YEAR
# Writes key=value pairs to $GITHUB_OUTPUT (or stdout when not running in Actions).
set -euo pipefail

COMMIT_BRANCH="${1:?commit branch is required}"
MODULE_DEFAULT_BRANCH="${2:-main}"
# API gateway URL (cloudId of etendoproject.atlassian.net): works with both classic and scoped API tokens
JIRA_BASE_URL="${JIRA_BASE_URL:-https://api.atlassian.com/ex/jira/b79eca5e-7328-40d8-a6cf-be75e6f159b2}"
CORE_REPO_URL="${CORE_REPO_URL:-https://github.com/etendosoftware/etendo_core.git}"
CURRENT_YEAR="${CURRENT_YEAR:-$(date +%y)}"

core_branch="main"
stack="DEFAULT"
epic_branch=""
backport_branch=""
prerelease_branch=""

year=""
if [[ "$COMMIT_BRANCH" =~ -Y([0-9]{2}) ]]; then
  year="${BASH_REMATCH[1]}"
fi

# Feature branches: look up the parent epic in Jira to test against epic/<KEY> when it exists
if [[ "$COMMIT_BRANCH" == feature/* ]]; then
  task="${COMMIT_BRANCH#feature/}"
  task="${task%%-Y*}"
  if [[ -n "${JIRA_ACCESS_TOKEN:-}" ]]; then
    epic_key="$(curl -sf -H "Authorization: Basic ${JIRA_ACCESS_TOKEN}" -H "Accept: application/json" \
      "${JIRA_BASE_URL}/rest/api/3/issue/${task}" | jq -r '.fields.parent.key // ""')" || epic_key=""
    if [[ -n "$epic_key" ]]; then
      epic_branch="epic/${epic_key}${year:+-Y${year}}"
      echo "Epic of ${task}: ${epic_key} -> ${epic_branch}" >&2
    fi
  else
    echo "::warning::JIRA_ACCESS_TOKEN not set, skipping epic branch lookup for ${task}" >&2
  fi
fi

if [[ "$COMMIT_BRANCH" == develop* ]] || { [[ "$COMMIT_BRANCH" == feature* || "$COMMIT_BRANCH" == epic* ]] && [[ -z "$year" ]]; }; then
  core_branch="develop"
elif [[ -n "$year" ]]; then
  if (( 10#$year > 10#$CURRENT_YEAR )); then
    prerelease_branch="prerelease/${year}.1"
    core_branch="$prerelease_branch"
    stack="PRERELEASE"
  else
    backport_branch="release/${year}.4"
    core_branch="$backport_branch"
    stack="BACKPORT"
  fi
elif [[ "$COMMIT_BRANCH" == release* ]]; then
  backport_branch="$COMMIT_BRANCH"
  core_branch="$backport_branch"
  stack="BACKPORT"
  if [[ "$COMMIT_BRANCH" =~ ^release/([0-9]{2})\. ]]; then
    prerelease_branch="prerelease/${BASH_REMATCH[1]}.1"
  fi
elif [[ "$COMMIT_BRANCH" == prerelease* ]]; then
  prerelease_branch="$COMMIT_BRANCH"
  core_branch="$prerelease_branch"
  stack="PRERELEASE"
fi

branch_exists() {
  [[ -n "$1" ]] && git ls-remote --exit-code --heads "$CORE_REPO_URL" "$1" >/dev/null 2>&1
}

if ! branch_exists "$core_branch"; then
  echo "::error::Core branch ${core_branch} does not exist in ${CORE_REPO_URL}" >&2
  exit 1
fi

# Same precedence as Jenkins: commit branch > epic branch > resolved core branch
core_checkout="$core_branch"
if branch_exists "$epic_branch"; then
  core_checkout="$epic_branch"
fi
if branch_exists "$COMMIT_BRANCH"; then
  core_checkout="$COMMIT_BRANCH"
fi

# Branch whose Sonar coverage is the baseline; empty means the comparison is skipped
coverage_base="$MODULE_DEFAULT_BRANCH"
if [[ "$COMMIT_BRANCH" == "$MODULE_DEFAULT_BRANCH" || "$COMMIT_BRANCH" == release/* ]]; then
  coverage_base=""
elif [[ -n "$year" && -n "$backport_branch" ]]; then
  coverage_base="$backport_branch"
fi

{
  echo "core-branch=${core_branch}"
  echo "core-checkout=${core_checkout}"
  echo "stack=${stack}"
  echo "epic-branch=${epic_branch}"
  echo "backport-branch=${backport_branch}"
  echo "prerelease-branch=${prerelease_branch}"
  echo "coverage-base=${coverage_base}"
} | tee -a "${GITHUB_OUTPUT:-/dev/null}"
