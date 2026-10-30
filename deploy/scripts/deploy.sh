#!/usr/bin/env bash
# Deploy one version of the TSPodcast web on the VPS.
#
# Usage:
#   deploy.sh <tag>                  pull WEB_IMAGE:<tag> from GHCR and run it
#   deploy.sh --build <path/to/web>  build the image on this server instead
#                                    (fallback when CI is down; needs ~2 GB RAM)
# Options:
#   --timeout <sec>   how long to wait for the healthcheck (default 180)
#   --no-rollback     on failure, leave the new version running
#   --skip-smoke      only wait for the container healthcheck
#
# Steps: pull (or build) -> docker compose up -d -> wait for "healthy" ->
# smoke test on 127.0.0.1:3000 -> record the tag. On failure it goes back to
# the tag that was running before. The previous tag is kept in
# /var/lib/tspodcast/web.tag.previous for rollback.sh; images of the last
# three deploys stay on disk so a rollback needs no registry.
#
# Runs as the deploy user (groups docker + tspodcast); CI calls it over SSH.
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
# shellcheck source=deploy/scripts/lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

TAG=""
BUILD_DIR=""
TIMEOUT=180
ROLLBACK=1
SMOKE=1
KEEP_IMAGES=3

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build)
      [[ $# -ge 2 ]] || usage 2
      BUILD_DIR="$2"
      shift
      ;;
    --timeout)
      [[ $# -ge 2 && "$2" =~ ^[0-9]+$ ]] || usage 2
      TIMEOUT="$2"
      shift
      ;;
    --no-rollback) ROLLBACK=0 ;;
    --skip-smoke) SMOKE=0 ;;
    -h | --help) usage 0 ;;
    -*) echo "unknown option: $1" >&2; usage 2 ;;
    *) TAG="$1" ;;
  esac
  shift
done

if [[ -n "$BUILD_DIR" ]]; then
  [[ -z "$TAG" ]] || die "give either a tag or --build, not both"
  [[ -f "$BUILD_DIR/Dockerfile" && -f "$BUILD_DIR/package-lock.json" ]] ||
    die "$BUILD_DIR does not look like the repo's web/ folder (no Dockerfile/package-lock.json)"
  BUILD_DIR="$(readlink -f "$BUILD_DIR")"
  rev="$(git -C "$BUILD_DIR" rev-parse --short=12 HEAD 2>/dev/null || true)"
  TAG="local-${rev:-$(date +%Y%m%d%H%M%S)}"
fi
[[ -n "$TAG" ]] || usage 2
valid_tag "$TAG" || die "invalid image tag: $TAG"

take_lock deploy

check_env_file "$TSP_COMPOSE_ENV"
check_env_file "$TSP_ETC_DIR/web.env"
WEB_IMAGE="$(env_get "$TSP_COMPOSE_ENV" WEB_IMAGE)"
[[ -n "$WEB_IMAGE" ]] || die "WEB_IMAGE is empty in $TSP_COMPOSE_ENV"
[[ -n "$(env_get "$TSP_COMPOSE_ENV" DOMAIN)" ]] || die "DOMAIN is empty in $TSP_COMPOSE_ENV"
profiles="${COMPOSE_PROFILES-$(env_get "$TSP_COMPOSE_ENV" COMPOSE_PROFILES)}"
if [[ ",$profiles," == *",backend,"* ]]; then
  check_env_file "$TSP_ETC_DIR/backend.env"
  check_backend_env
fi

CURRENT="$(current_web_tag)"
log "deploying $WEB_IMAGE:$TAG (running now: ${CURRENT:-nothing})"

# --- 1. get the image -----------------------------------------------------
if [[ -n "$BUILD_DIR" ]]; then
  log "building on this server from $BUILD_DIR (this takes a few minutes)"
  WEB_TAG="$TAG" WEB_BUILD_CONTEXT="$BUILD_DIR" compose build --pull web
elif docker image inspect "$WEB_IMAGE:$TAG" >/dev/null 2>&1; then
  log "image already on this server, not pulling"
else
  # Plain docker pull: compose would silently skip a failed pull for a service
  # that also has a build section.
  docker pull --quiet "$WEB_IMAGE:$TAG" >/dev/null ||
    die "pull of $WEB_IMAGE:$TAG failed (tag pushed? 'docker login ghcr.io' done as $(id -un)?)"
fi
docker image inspect "$WEB_IMAGE:$TAG" >/dev/null 2>&1 || die "image $WEB_IMAGE:$TAG is not on this server"

# --- 2. start it ------------------------------------------------------------
wait_healthy() {
  local deadline=$((SECONDS + TIMEOUT)) cid status
  while ((SECONDS < deadline)); do
    cid="$(compose ps -q web 2>/dev/null || true)"
    if [[ -n "$cid" ]]; then
      status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || echo missing)"
      case "$status" in
        healthy) return 0 ;;
        unhealthy | exited | dead) log "web container is $status"; return 1 ;;
      esac
    fi
    sleep 3
  done
  log "web did not become healthy within ${TIMEOUT}s"
  return 1
}

verify() {
  wait_healthy || return 1
  if [[ "$SMOKE" == "1" ]]; then
    "$SCRIPT_DIR/healthcheck/smoke.sh" http://127.0.0.1:3000 || return 1
  fi
}

start() {
  WEB_TAG="$1" compose up -d --remove-orphans
}

FAILED=0
start "$TAG" || FAILED=1
if [[ "$FAILED" == "0" ]]; then
  verify || FAILED=1
fi

if [[ "$FAILED" == "1" ]]; then
  log "deploy of $TAG FAILED; last log lines of web:"
  compose logs --no-color --tail=60 web >&2 || true
  if [[ "$ROLLBACK" == "1" && -n "$CURRENT" && "$CURRENT" != "$TAG" ]]; then
    log "rolling back to $CURRENT"
    if start "$CURRENT" && wait_healthy; then
      log "rolled back to $CURRENT"
    else
      log "ROLLBACK FAILED too: check 'compose.sh ps' and 'compose.sh logs web' now"
    fi
  fi
  printf '%s\t%s\t%s\tfailed\n' "$(date -Is)" "$TAG" "$WEB_IMAGE" >>"$TSP_STATE_DIR/deploy-history.log"
  exit 1
fi

# --- 3. pick up Caddyfile changes (the folder is bind-mounted) ---------------
CADDY_OK=1
if ! compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile >/dev/null 2>&1; then
  CADDY_OK=0
fi

# --- 4. record ----------------------------------------------------------------
if [[ -n "$CURRENT" && "$CURRENT" != "$TAG" ]]; then
  printf '%s\n' "$CURRENT" >"$TSP_STATE_DIR/web.tag.previous"
fi
printf '%s\n' "$TAG" >"$TSP_STATE_DIR/web.tag"
printf '%s\t%s\t%s\tok\n' "$(date -Is)" "$TAG" "$WEB_IMAGE" >>"$TSP_STATE_DIR/deploy-history.log"

# --- 5. keep the images of the last deploys, drop older ones --------------------
mapfile -t KEEP < <(
  {
    echo "$TAG"
    previous_web_tag
    echo
    awk -F'\t' '$4 == "ok" { print $2 }' "$TSP_STATE_DIR/deploy-history.log" | tac
  } | awk 'NF && !seen[$0]++' | head -n "$KEEP_IMAGES"
)
while read -r tag; do
  [[ -z "$tag" || "$tag" == "<none>" ]] && continue
  keep=0
  for k in "${KEEP[@]}"; do [[ "$k" == "$tag" ]] && keep=1; done
  if [[ "$keep" == "0" ]]; then
    docker image rm "$WEB_IMAGE:$tag" >/dev/null 2>&1 && log "removed old image $WEB_IMAGE:$tag" || true
  fi
done < <(docker image ls "$WEB_IMAGE" --format '{{.Tag}}')
docker image prune -f >/dev/null 2>&1 || true

log "deployed $WEB_IMAGE:$TAG (previous: $(previous_web_tag || true); kept images: ${KEEP[*]})"
if [[ "$CADDY_OK" == "0" ]]; then
  die "web is live, but 'caddy reload' failed: Caddy still runs the old Caddyfile. Check: compose.sh exec caddy caddy validate --config /etc/caddy/Caddyfile"
fi
