#!/usr/bin/env bash
# Phase 8a: restores a backup produced by backup-db.sh, streamed straight from object storage into MySQL
# (aws s3 cp - ... | gunzip | mysql, no temp file ever hits disk). DESTRUCTIVE - overwrites the live
# database with the backup's contents. Run this as part of the quarterly restore drill (see RUNBOOK.md),
# or for real during an incident.
# Usage: ./scripts/restore-db.sh <backup-filename>   (as it appears in the bucket, e.g. from `aws s3 ls`)
set -euo pipefail
cd "$(dirname "$0")/.."

set -a; . ./.env; set +a
: "${BACKUP_S3_BUCKET:?set BACKUP_S3_BUCKET in .env}"
: "${BACKUP_S3_ENDPOINT:?set BACKUP_S3_ENDPOINT in .env}"
: "${BACKUP_S3_ACCESS_KEY:?set BACKUP_S3_ACCESS_KEY in .env}"
: "${BACKUP_S3_SECRET_KEY:?set BACKUP_S3_SECRET_KEY in .env}"
: "${MYSQL_DATABASE:?set MYSQL_DATABASE in .env}"
: "${MYSQL_ROOT_PASSWORD:?set MYSQL_ROOT_PASSWORD in .env}"

FILE="${1:?Usage: restore-db.sh <backup-filename>}"

echo "### This OVERWRITES the live '$MYSQL_DATABASE' database with the contents of $FILE."
read -r -p "Type the database name ($MYSQL_DATABASE) to confirm: " CONFIRM
[ "$CONFIRM" = "$MYSQL_DATABASE" ] || { echo "Aborted - no changes made."; exit 1; }

echo "### Downloading and restoring $FILE ..."
docker run --rm \
  -e AWS_ACCESS_KEY_ID="$BACKUP_S3_ACCESS_KEY" \
  -e AWS_SECRET_ACCESS_KEY="$BACKUP_S3_SECRET_KEY" \
  amazon/aws-cli s3 cp "s3://$BACKUP_S3_BUCKET/$FILE" - --endpoint-url "$BACKUP_S3_ENDPOINT" \
  | gunzip \
  | docker compose exec -T mysql sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" \"$MYSQL_DATABASE\""

echo "### Done. Restart the backend so it reconnects cleanly:"
echo "###   docker compose -f docker-compose.yml -f docker-compose.prod.yml restart backend backend2"
