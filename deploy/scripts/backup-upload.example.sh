#!/usr/bin/env bash
# Example upload hook for backup-postgres.sh: copies the dump to Cloudflare R2
# with rclone (plan §2.6). Install it once on the VPS:
#
#   sudo apt-get install -y rclone
#   sudo install -o root -g tspodcast -m 750 \
#     /opt/tspodcast/scripts/backup-upload.example.sh /etc/tspodcast/backup-upload.sh
#
# Credentials come from /etc/tspodcast/backend.env (R2_ACCOUNT_ID,
# R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_BUCKET) and are handed to rclone
# through its environment, never on the command line. Retention on R2 is set
# with a bucket lifecycle rule (e.g. delete postgres/ objects after 60 days).
#
# Usage (by backup-postgres.sh): backup-upload.sh <dump file>
set -euo pipefail

FILE="${1:?usage: backup-upload.sh <dump file>}"
ENV_FILE="${TSP_ETC_DIR:-/etc/tspodcast}/backend.env"

get() { grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2-; }

R2_ACCOUNT_ID="$(get R2_ACCOUNT_ID)"
R2_BUCKET="$(get R2_BUCKET)"
for v in R2_ACCOUNT_ID R2_BUCKET; do
  [[ -n "${!v}" ]] || { echo "$v is empty in $ENV_FILE" >&2; exit 1; }
done

export RCLONE_CONFIG_R2_TYPE=s3
export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
export RCLONE_CONFIG_R2_ENDPOINT="https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com"
RCLONE_CONFIG_R2_ACCESS_KEY_ID="$(get R2_ACCESS_KEY_ID)"
RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$(get R2_SECRET_ACCESS_KEY)"
export RCLONE_CONFIG_R2_ACCESS_KEY_ID RCLONE_CONFIG_R2_SECRET_ACCESS_KEY
export RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true

rclone copyto "$FILE" "r2:${R2_BUCKET}/postgres/$(basename "$FILE")"
echo "uploaded $(basename "$FILE") to r2:${R2_BUCKET}/postgres/"
