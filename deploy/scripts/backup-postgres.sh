#!/usr/bin/env bash
# Nightly dump of the backend Postgres (profile "backend").
#
# Usage:
#   backup-postgres.sh             dump, verify, rotate, upload (if a hook exists)
#   backup-postgres.sh --no-upload dump, verify, rotate only
#
# Output: /var/backups/tspodcast/postgres/daily/tspodcast-<UTC time>.dump
#   pg_dump custom format (-Fc, compressed), restorable with restore-postgres.sh.
# Rotation: KEEP_DAILY newest dumps (default 14); the first dump of each ISO
#   week is also hard-linked into weekly/, which keeps KEEP_WEEKLY (default 8).
# Off-site copy (plan §2.6: R2): if /etc/tspodcast/backup-upload.sh exists and
#   is executable, it is called with the dump path (example:
#   deploy/scripts/backup-upload.example.sh). A failing hook fails the backup.
# Monitoring: BACKUP_PING_URL in compose.env (healthchecks.io) gets a ping on
#   success and <url>/fail on failure.
#
# Run by tspodcast-backup.timer (deploy/systemd) as the deploy user.
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
# shellcheck source=deploy/scripts/lib.sh
source "$SCRIPT_DIR/lib.sh"

UPLOAD=1
case "${1:-}" in
  -h | --help) sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  --no-upload) UPLOAD=0 ;;
  "") ;;
  *) die "unknown option: $1 (see --help)" ;;
esac

KEEP_DAILY="${KEEP_DAILY:-14}"
KEEP_WEEKLY="${KEEP_WEEKLY:-8}"
DEST="$TSP_BACKUP_DIR/postgres"
HOOK="$TSP_ETC_DIR/backup-upload.sh"
PING_URL="$(env_get "$TSP_COMPOSE_ENV" BACKUP_PING_URL)"

ping_hc() {
  [[ -n "$PING_URL" ]] || return 0
  curl -fsS -m 10 --retry 3 -o /dev/null "$PING_URL$1" || warn "could not ping healthchecks"
}

TMP_FILE=""
on_exit() {
  local rc=$?
  [[ -n "$TMP_FILE" && -e "$TMP_FILE" ]] && rm -f "$TMP_FILE"
  if [[ $rc -ne 0 ]]; then
    ping_hc /fail
  fi
}
trap on_exit EXIT

take_lock backup
umask 077
mkdir -p "$DEST/daily" "$DEST/weekly"

cid="$(COMPOSE_PROFILES=backend compose ps -q postgres 2>/dev/null || true)"
[[ -n "$cid" ]] || die "postgres is not running (COMPOSE_PROFILES=backend in compose.env? compose.sh ps)"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
FILE="$DEST/daily/tspodcast-$stamp.dump"
TMP_FILE="$FILE.partial"

log "dumping to $FILE"
# Credentials stay inside the container: pg_dump runs there over the local socket.
# shellcheck disable=SC2016 # expanded inside the container
COMPOSE_PROFILES=backend compose exec -T postgres \
  sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --compress=6 --no-owner' \
  >"$TMP_FILE"

[[ -s "$TMP_FILE" ]] || die "dump is empty"
# A readable table of contents means the archive is complete.
COMPOSE_PROFILES=backend compose exec -T postgres pg_restore --list <"$TMP_FILE" >/dev/null ||
  die "pg_restore cannot read the dump; keeping the previous backups untouched"
mv "$TMP_FILE" "$FILE"
TMP_FILE=""
log "dump ok: $(du -h "$FILE" | cut -f1)"

# Weekly copy: first successful dump of the ISO week.
week="$(date -u +%G-W%V)"
if ! compgen -G "$DEST/weekly/tspodcast-$week-*.dump" >/dev/null; then
  ln "$FILE" "$DEST/weekly/tspodcast-$week-$stamp.dump"
  log "weekly copy for $week"
fi

# Rotation (names sort by time).
prune() {
  local dir="$1" keep="$2"
  find "$dir" -maxdepth 1 -name 'tspodcast-*.dump' -type f | sort -r | tail -n +"$((keep + 1))" |
    while read -r old; do
      rm -f -- "$old"
      log "rotated out $(basename "$old")"
    done
}
prune "$DEST/daily" "$KEEP_DAILY"
prune "$DEST/weekly" "$KEEP_WEEKLY"

if [[ "$UPLOAD" == "1" && -x "$HOOK" ]]; then
  log "uploading with $HOOK"
  "$HOOK" "$FILE"
elif [[ "$UPLOAD" == "1" ]]; then
  warn "no executable $HOOK: backup stays on this server only (plan §2.6 wants an off-site copy on R2)"
fi

ping_hc ""
log "backup finished"
