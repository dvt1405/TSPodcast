#!/usr/bin/env bash
# Shared helpers for the deploy scripts. Sourced, not executed.
# Paths can be overridden through the environment (mostly for testing).

TSP_APP_DIR="${TSP_APP_DIR:-/opt/tspodcast}"        # copy of the repo's deploy/ folder
TSP_ETC_DIR="${TSP_ETC_DIR:-/etc/tspodcast}"        # env files (root:tspodcast, 640)
TSP_STATE_DIR="${TSP_STATE_DIR:-/var/lib/tspodcast}" # deployed tags, history, locks
TSP_BACKUP_DIR="${TSP_BACKUP_DIR:-/var/backups/tspodcast}"
export TSP_ETC_DIR

TSP_COMPOSE_FILE="$TSP_APP_DIR/docker-compose.yml"
TSP_COMPOSE_ENV="$TSP_ETC_DIR/compose.env"

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >&2; }
warn() { log "WARNING: $*"; }
die() {
  log "ERROR: $*"
  exit 1
}

# env_get FILE KEY: value of KEY in a KEY=value file, without evaluating it as
# shell. Prints nothing when the key or the file is missing.
env_get() {
  local file="$1" key="$2" line
  [[ -r "$file" ]] || return 0
  line="$(grep -E "^[[:space:]]*${key}=" "$file" | tail -n 1 || true)"
  line="${line#*=}"
  line="${line%$'\r'}"
  # Strip one level of matching quotes.
  if [[ "$line" =~ ^\"(.*)\"$ || "$line" =~ ^\'(.*)\'$ ]]; then
    line="${BASH_REMATCH[1]}"
  fi
  printf '%s' "$line"
}

# Image tag currently deployed (empty before the first deploy).
current_web_tag() {
  if [[ -r "$TSP_STATE_DIR/web.tag" ]]; then
    tr -d '[:space:]' <"$TSP_STATE_DIR/web.tag"
  fi
}

previous_web_tag() {
  if [[ -r "$TSP_STATE_DIR/web.tag.previous" ]]; then
    tr -d '[:space:]' <"$TSP_STATE_DIR/web.tag.previous"
  fi
}

valid_tag() { [[ "$1" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]; }

# docker compose with the stack's project, env file and profiles.
# WEB_TAG defaults to the deployed tag so `compose.sh up -d` never jumps to :latest.
compose() {
  [[ -r "$TSP_COMPOSE_FILE" ]] || die "$TSP_COMPOSE_FILE not found (copy the repo's deploy/ folder to $TSP_APP_DIR)"
  [[ -r "$TSP_COMPOSE_ENV" ]] || die "$TSP_COMPOSE_ENV not readable (are you in the tspodcast group? run: id)"
  local profiles
  profiles="${COMPOSE_PROFILES-$(env_get "$TSP_COMPOSE_ENV" COMPOSE_PROFILES)}"
  WEB_TAG="${WEB_TAG:-$(current_web_tag)}" \
    COMPOSE_PROFILES="$profiles" \
    docker compose \
    --project-directory "$TSP_APP_DIR" \
    --file "$TSP_COMPOSE_FILE" \
    --env-file "$TSP_COMPOSE_ENV" \
    "$@"
}

# Warn when an env file is readable by everyone; refuse when it is missing.
check_env_file() {
  local file="$1" mode
  [[ -e "$file" ]] || die "$file is missing (template: $TSP_APP_DIR/env/$(basename "$file").example)"
  [[ -r "$file" ]] || die "$file exists but is not readable by $(id -un) (needs group tspodcast)"
  mode="$(stat -c '%a' "$file" 2>/dev/null || stat -f '%Lp' "$file")"
  if [[ "${mode: -1}" != "0" ]]; then
    warn "$file is readable by other users (mode $mode); fix: sudo chmod 640 $file"
  fi
}

# Plan §1 invariant: no backend container gets a PodcastIndex or Anthropic key.
check_backend_env() {
  local file="$TSP_ETC_DIR/backend.env"
  [[ -e "$file" ]] || return 0
  if grep -Eq '^[[:space:]]*(PODCASTINDEX_[A-Z_]*|ANTHROPIC_API_KEY)=[^[:space:]]' "$file"; then
    die "$file sets PODCASTINDEX_* or ANTHROPIC_API_KEY; backend containers must not hold these keys (plan §1)"
  fi
}

# Exclusive lock so deploy, rollback and restore never overlap.
take_lock() {
  local name="$1"
  mkdir -p "$TSP_STATE_DIR"
  exec 9>"$TSP_STATE_DIR/$name.lock"
  flock -n 9 || die "another $name is running (lock $TSP_STATE_DIR/$name.lock)"
}
