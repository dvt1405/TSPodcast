#!/usr/bin/env bash
# Run one backend worker job in a throw-away container (profile "backend").
#
# Usage:
#   run-worker-job.sh <job> [args...]      e.g. run-worker-job.sh daily-crawl
#
# The plan (ke-hoach-backend-quickwin-cuoc-lon.md §2.6) schedules jobs with
# pg-boss cron INSIDE the long-running worker, so this is not needed for normal
# operation. It is the host-side alternative, used by tspodcast-crawl.timer, if
# the worker ends up without its own scheduler, and for running a job by hand.
#
# TODO(BE-M0): replace WORKER_JOB_CMD with the real CLI of backend/ (for
# example "node dist/cli.js") and the job names it accepts.
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
# shellcheck source=deploy/scripts/lib.sh
source "$SCRIPT_DIR/lib.sh"

if [[ $# -eq 0 || "$1" == "-h" || "$1" == "--help" ]]; then
  sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
  [[ $# -eq 0 ]] && exit 2 || exit 0
fi

WORKER_JOB_CMD="${WORKER_JOB_CMD:-node dist/cli.js}" # TODO(BE-M0)
JOB="$1"
shift
[[ "$JOB" =~ ^[a-z0-9][a-z0-9-]{0,63}$ ]] || die "invalid job name: $JOB"

take_lock "job-$JOB"
export COMPOSE_PROFILES=backend
log "running worker job $JOB"
# shellcheck disable=SC2086 # WORKER_JOB_CMD is a command line on purpose
compose run --rm --no-deps -T worker $WORKER_JOB_CMD "$JOB" "$@"
log "job $JOB finished"
