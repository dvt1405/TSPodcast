#!/usr/bin/env bash
# docker compose for the TSPodcast stack, with the right project folder, env
# file, profiles and the currently deployed web tag.
#
#   compose.sh ps
#   compose.sh logs -f --tail=200 web
#   compose.sh exec caddy caddy reload --config /etc/caddy/Caddyfile
#   compose.sh --profile backend up -d postgres
#
# Never run `compose.sh config` in shared logs: it prints the env files' values.
set -euo pipefail

# shellcheck source=deploy/scripts/lib.sh
source "$(dirname "$(readlink -f "$0")")/lib.sh"

if [[ $# -eq 0 || "$1" == "-h" || "$1" == "--help" ]]; then
  sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
fi

compose "$@"
