#!/usr/bin/env bash
# Clones the dependent modules listed in jenkinsExtraModules.txt (comma separated git URLs)
# into <core-dir>/modules, using the first existing branch of the candidate chain.
# Port of the extra modules checkout + moduleBranchUtils from the Jenkins pipelines.
#
# Usage: clone-extra-modules.sh <core-dir> <extra-modules-file> <candidate-branch>...
set -euo pipefail

CORE_DIR="${1:?core dir is required}"
EXTRA_MODULES_FILE="${2:?extra modules file is required}"
shift 2

if [[ ! -f "$EXTRA_MODULES_FILE" ]]; then
  echo "No $(basename "$EXTRA_MODULES_FILE") found, skipping extra modules."
  exit 0
fi

# Drop empty and duplicated candidates, keeping the order
candidates=()
for branch in "$@"; do
  [[ -z "$branch" ]] && continue
  [[ " ${candidates[*]} " == *" ${branch} "* ]] && continue
  candidates+=("$branch")
done

IFS=',' read -ra modules < <(tr -d '[:space:]' < "$EXTRA_MODULES_FILE")
for module_url in "${modules[@]}"; do
  [[ -z "$module_url" ]] && continue
  # git@github.com:org/repo.git -> https://github.com/org/repo.git
  if [[ "$module_url" =~ ^git@([^:]+):(.+)$ ]]; then
    module_url="https://${BASH_REMATCH[1]}/${BASH_REMATCH[2]}"
  fi
  module_name="$(basename "$module_url" .git)"

  selected=""
  for branch in "${candidates[@]}"; do
    if git ls-remote --exit-code --heads "$module_url" "$branch" >/dev/null 2>&1; then
      selected="$branch"
      break
    fi
  done

  if [[ -n "$selected" ]]; then
    git clone --depth 1 --branch "$selected" "$module_url" "${CORE_DIR}/modules/${module_name}"
  else
    git clone --depth 1 "$module_url" "${CORE_DIR}/modules/${module_name}"
    selected="$(git -C "${CORE_DIR}/modules/${module_name}" branch --show-current)"
  fi
  echo "Module ${module_name}: ${selected}"
  echo "- ${module_name}: \`${selected}\`" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
done
