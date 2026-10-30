#!/usr/bin/env bash
# Smoke test of the TSPodcast web. Read-only GET requests; prints no secrets.
#
# Usage:
#   smoke.sh [--public] [--allow-missing-keys] <base-url>
#
#   smoke.sh http://127.0.0.1:3000           on the VPS, straight to the container
#   smoke.sh --public https://example.com    through Caddy: also checks HSTS,
#                                            http->https and www->apex redirects
#
# Fails when a page does not answer 200, a security header is missing, or the
# site shows the "PodcastIndex keys not configured" notice (web.env incomplete;
# --allow-missing-keys or SMOKE_ALLOW_MISSING_KEYS=1 turns that into a warning).
# A PodcastIndex error page (rate limit, outage) is only a warning.
set -euo pipefail

PUBLIC=0
ALLOW_MISSING_KEYS="${SMOKE_ALLOW_MISSING_KEYS:-0}"
BASE=""

usage() {
  sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --public) PUBLIC=1 ;;
    --allow-missing-keys) ALLOW_MISSING_KEYS=1 ;;
    -h | --help) usage 0 ;;
    -*) echo "unknown option: $1" >&2; usage 2 ;;
    *) BASE="$1" ;;
  esac
  shift
done
[[ -n "$BASE" ]] || usage 2
BASE="${BASE%/}"
command -v curl >/dev/null || { echo "curl is required" >&2; exit 2; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

FAILS=0
WARNS=0
ok() { printf '  ok    %s\n' "$*"; }
bad() { printf '  FAIL  %s\n' "$*"; FAILS=$((FAILS + 1)); }
note() { printf '  warn  %s\n' "$*"; WARNS=$((WARNS + 1)); }

# fetch PATH -> writes $TMP/body and $TMP/headers (lower-cased), echoes status.
fetch() {
  local url="$BASE$1" status
  status="$(curl -sS -o "$TMP/body" -D "$TMP/headers.raw" -w '%{http_code}' \
    --max-time 30 --retry 2 --retry-delay 2 --retry-all-errors \
    -A "TSPodcastSmoke/1.0" "$url" || true)"
  [[ "$status" =~ ^[0-9]{3}$ ]] || status=000
  touch "$TMP/headers.raw"
  tr '[:upper:]' '[:lower:]' <"$TMP/headers.raw" | tr -d '\r' >"$TMP/headers" || true
  echo "$status"
}

has_header() { grep -q "^$1:" "$TMP/headers"; }
header_value() { grep "^$1:" "$TMP/headers" | tail -n 1 | cut -d: -f2- | sed 's/^ *//'; }

expect_page() {
  local path="$1" want="${2:-200}" status
  status="$(fetch "$path")"
  if [[ "$status" == "$want" ]]; then ok "GET $path -> $status"; else bad "GET $path -> $status (want $want)"; fi
}

echo "Smoke test: $BASE"

# 1. Server up (static route, no PodcastIndex call).
expect_page /robots.txt
if grep -qi '^sitemap:' "$TMP/body"; then ok "robots.txt lists a sitemap"; else note "robots.txt has no Sitemap line (SITE_NOINDEX image?)"; fi

# 2. Home page: status, security headers, content.
expect_page /
for h in x-content-type-options referrer-policy x-frame-options permissions-policy; do
  if has_header "$h"; then ok "header $h"; else bad "header $h missing"; fi
done
if has_header x-powered-by; then bad "x-powered-by is exposed"; else ok "no x-powered-by"; fi
if grep -q 'id="setup-title"' "$TMP/body"; then
  if [[ "$ALLOW_MISSING_KEYS" == "1" ]]; then
    note "home shows the setup notice: PODCASTINDEX_* missing in /etc/tspodcast/web.env"
  else
    bad "home shows the setup notice: PODCASTINDEX_* missing in /etc/tspodcast/web.env"
  fi
elif grep -Eq 'Không tải được dữ liệu|Could not load data' "$TMP/body"; then
  note "home shows a load error (PodcastIndex 401/429/outage? check: compose.sh logs web)"
else
  ok "home renders PodcastIndex content"
fi

# 3. Other routes: ISR pages, a server-only route, metadata routes.
expect_page /trending
expect_page /radio
expect_page "/search?q=test"
expect_page /sitemap.xml
if [[ "$(header_value content-type)" == *xml* ]]; then ok "sitemap.xml is XML"; else bad "sitemap.xml content-type: $(header_value content-type)"; fi
expect_page /manifest.webmanifest
expect_page /this-page-does-not-exist-smoke 404

# 4. Through Caddy only.
if [[ "$PUBLIC" == "1" ]]; then
  fetch / >/dev/null
  if has_header strict-transport-security; then ok "header strict-transport-security"; else bad "header strict-transport-security missing"; fi
  if [[ "$(header_value server)" == *addy* ]]; then note "Server header reveals Caddy"; fi

  host="${BASE#*://}"
  host="${host%%/*}"
  loc="$(curl -sS -o /dev/null -w '%{http_code} %{redirect_url}' --max-time 15 "http://$host/" || true)"
  if [[ "$loc" =~ ^30[178]\ https://$host/?$ ]]; then ok "http -> https ($loc)"; else bad "http://$host/ -> $loc (want a redirect to https://$host/)"; fi

  if [[ "$host" != www.* ]]; then
    loc="$(curl -sS -o /dev/null -w '%{http_code} %{redirect_url}' --max-time 15 "https://www.$host/trending" || true)"
    if [[ "$loc" =~ ^30[178]\ https://$host/trending$ ]]; then ok "www -> apex ($loc)"; else bad "https://www.$host/trending -> $loc (want a redirect to https://$host/trending)"; fi
  fi
fi

echo "Result: $FAILS failed, $WARNS warning(s)"
[[ "$FAILS" -eq 0 ]]
