#!/usr/bin/env bash
# Phase 8a: dumps the database and streams it straight to S3-compatible object storage (mysqldump | gzip |
# aws s3 cp - ..., no temp file ever hits disk). Meant to run ON THE SERVER (Linux, this repo checked out,
# the stack already up), via cron - see RUNBOOK.md for the schedule and for the restore drill this is only
# half of ("a backup you've never restored from is not a backup").
#
# Needs an S3-compatible bucket already created - AWS S3, Backblaze B2, DigitalOcean Spaces, MinIO, or
# anything else the aws CLI can reach via --endpoint-url - with credentials in .env. Uses the amazon/aws-cli
# Docker image rather than requiring awscli installed on the host, consistent with the rest of this repo.
set -euo pipefail
cd "$(dirname "$0")/.."

set -a; . ./.env; set +a
: "${BACKUP_S3_BUCKET:?set BACKUP_S3_BUCKET in .env}"
: "${BACKUP_S3_ENDPOINT:?set BACKUP_S3_ENDPOINT in .env}"
: "${BACKUP_S3_ACCESS_KEY:?set BACKUP_S3_ACCESS_KEY in .env}"
: "${BACKUP_S3_SECRET_KEY:?set BACKUP_S3_SECRET_KEY in .env}"
: "${MYSQL_DATABASE:?set MYSQL_DATABASE in .env}"
: "${MYSQL_ROOT_PASSWORD:?set MYSQL_ROOT_PASSWORD in .env}"

FILE="backup-${MYSQL_DATABASE}-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"

echo "### Dumping $MYSQL_DATABASE and streaming it to s3://$BACKUP_S3_BUCKET/$FILE ..."
docker compose exec -T mysql sh -c "mysqldump -uroot -p\"\$MYSQL_ROOT_PASSWORD\" --single-transaction --routines --triggers \"$MYSQL_DATABASE\"" \
  | gzip \
  | docker run --rm -i \
      -e AWS_ACCESS_KEY_ID="$BACKUP_S3_ACCESS_KEY" \
      -e AWS_SECRET_ACCESS_KEY="$BACKUP_S3_SECRET_KEY" \
      amazon/aws-cli s3 cp - "s3://$BACKUP_S3_BUCKET/$FILE" --endpoint-url "$BACKUP_S3_ENDPOINT"

echo "### Done: $FILE"
# Pruning old backups: set a lifecycle/expiration rule on the bucket itself (every S3-compatible provider
# supports this) rather than scripting deletion here - a bug in a delete script is a much worse day than a
# slightly bigger storage bill. See RUNBOOK.md.
