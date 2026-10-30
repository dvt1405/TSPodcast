#!/bin/sh
# Entrypoint of the web image (web/Dockerfile).
#
# Next.js writes ISR pages next to the compiled server (.next/server), not into
# .next/cache, so that folder must be writable while the rest of the container
# is read-only. deploy/docker-compose.yml mounts a scratch volume there; this
# script empties it and copies in the server of *this* image, so a new deploy
# never runs with pages or code left over from the previous image.
set -eu

SEED=/app/.next/server.seed
TARGET=/app/.next/server

if [ ! -d "$SEED" ]; then
  echo "docker-entrypoint: $SEED is missing, the image is broken" >&2
  exit 1
fi
if [ ! -w "$TARGET" ]; then
  echo "docker-entrypoint: $TARGET is not writable (mount a volume or tmpfs there)" >&2
  exit 1
fi

find "$TARGET" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
cp -R "$SEED"/. "$TARGET"/

exec "$@"
