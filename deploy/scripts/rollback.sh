#!/usr/bin/env bash
# Go back to an earlier web version.
#
# Usage:
#   rollback.sh            redeploy the tag that ran before the current one
#   rollback.sh <tag>      redeploy a specific tag (see --list)
#   rollback.sh --list     show the deploy history (newest last)
#
# Uses deploy.sh, so the same healthcheck and smoke test apply. The images of
# the last three deploys are still on the server; older tags are pulled from
# GHCR. Running it twice in a row swaps back to where you started.
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
# shellcheck source=deploy/scripts/lib.sh
source "$SCRIPT_DIR/lib.sh"

case "${1:-}" in
  -h | --help)
    sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
  --list)
    printf 'current:  %s\nprevious: %s\n\n' "$(current_web_tag)" "$(previous_web_tag)"
    if [[ -r "$TSP_STATE_DIR/deploy-history.log" ]]; then
      tail -n 20 "$TSP_STATE_DIR/deploy-history.log"
    fi
    exit 0
    ;;
esac

TARGET="${1:-$(previous_web_tag)}"
[[ -n "$TARGET" ]] || die "no previous tag recorded in $TSP_STATE_DIR/web.tag.previous; pass a tag (rollback.sh --list)"
valid_tag "$TARGET" || die "invalid image tag: $TARGET"
[[ "$TARGET" != "$(current_web_tag)" ]] || die "$TARGET is already running"

log "rolling back web from $(current_web_tag) to $TARGET"
# No automatic roll-forward: if the old version is broken too, stop and look.
exec "$SCRIPT_DIR/deploy.sh" --no-rollback "$TARGET"
