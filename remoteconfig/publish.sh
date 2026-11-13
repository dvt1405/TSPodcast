#!/usr/bin/env bash
# Merge remoteconfig/ads-iap.params.json into a project's LIVE Remote Config template and publish it.
#
# A template deploy replaces the whole template, so this never publishes the file alone: it fetches
# the current template, replaces only the conditions and parameters defined in the params file
# (wherever they currently sit, top level or in a group) and keeps every other key as is.
#
#   remoteconfig/publish.sh <firebase-project-id> [--dry-run]
#
# --dry-run writes the merged template to remoteconfig/build/<project>.merged.json and stops.
# Needs: firebase CLI logged in with an account that can edit the project's Remote Config, jq.
set -euo pipefail

usage() { echo "usage: $0 <firebase-project-id> [--dry-run]" >&2; exit 2; }
[[ $# -ge 1 ]] || usage
PROJECT="$1"
DRY_RUN=false
[[ "${2:-}" == "--dry-run" ]] && DRY_RUN=true
[[ $# -le 2 ]] || usage

DIR="$(cd "$(dirname "$0")" && pwd)"
PARAMS="$DIR/ads-iap.params.json"
OUT="$DIR/build"
mkdir -p "$OUT"
CURRENT="$OUT/$PROJECT.current.json"
MERGED="$OUT/$PROJECT.merged.json"

firebase remoteconfig:get --project "$PROJECT" -o "$CURRENT" >/dev/null
"$DIR/merge.sh" "$CURRENT" "$PARAMS" > "$MERGED"

echo "Merged template: $MERGED"
jq -r '
  "conditions: " + ([.conditions[]?.name] | join(", ")),
  "groups: " + ([.parameterGroups // {} | to_entries[] | "\(.key) (\(.value.parameters | length))"] | join(", ")),
  "top-level params: \((.parameters // {}) | length)"
' "$MERGED"

if $DRY_RUN; then
  echo "Dry run: nothing published."
  exit 0
fi

# Deploy through a throwaway firebase.json so the repo's hosting config is not involved.
cat > "$OUT/firebase.json" <<JSON
{ "remoteconfig": { "template": "$PROJECT.merged.json" } }
JSON
firebase deploy --only remoteconfig --project "$PROJECT" --config "$OUT/firebase.json"
