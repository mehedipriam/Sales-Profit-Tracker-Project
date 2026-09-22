#!/usr/bin/env bash
# One-time bootstrap for Phase 6a TLS: issues the first Let's Encrypt certificate.
# Run this ON THE SERVER (Linux, with Docker and this repo checked out), not from a dev machine -
# Let's Encrypt has to reach DOMAIN over the internet on port 80 to validate it.
#
# Re-running this script re-issues the certificate (--force-renewal), so it costs a Let's Encrypt
# rate-limit hit each time - don't put it on a cron. Routine renewal is handled by the `certbot`
# service in docker-compose.prod.yml, which is already running after this script finishes.
set -euo pipefail
cd "$(dirname "$0")/.."

set -a; . ./.env; set +a
: "${DOMAIN:?set DOMAIN in .env}"
: "${CERTBOT_EMAIL:?set CERTBOT_EMAIL in .env}"

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
DATA_PATH="./certbot/conf"
RSA_KEY_SIZE=4096

echo "### Rendering nginx/prod.conf for $DOMAIN ..."
sed "s/__DOMAIN__/$DOMAIN/g" nginx/prod.conf.template > nginx/prod.conf

if [ ! -e "$DATA_PATH/options-ssl-nginx.conf" ] || [ ! -e "$DATA_PATH/ssl-dhparams.pem" ]; then
  echo "### Downloading certbot's recommended TLS parameters ..."
  mkdir -p "$DATA_PATH"
  curl -s https://raw.githubusercontent.com/certbot/certbot/master/certbot-nginx/certbot_nginx/_internal/tls_configs/options-ssl-nginx.conf > "$DATA_PATH/options-ssl-nginx.conf"
  curl -s https://raw.githubusercontent.com/certbot/certbot/master/certbot-nginx/certbot_nginx/_internal/tls_configs/ssl-dhparams.pem > "$DATA_PATH/ssl-dhparams.pem"
fi

# nginx's ssl_certificate directives point at real file paths even before a real cert exists, so a
# dummy self-signed one goes there first just so nginx can start at all.
echo "### Creating a dummy certificate so nginx can start ..."
mkdir -p "$DATA_PATH/live/$DOMAIN"
$COMPOSE run --rm --entrypoint "\
  openssl req -x509 -nodes -newkey rsa:$RSA_KEY_SIZE -days 1 \
    -keyout '/etc/letsencrypt/live/$DOMAIN/privkey.pem' \
    -out '/etc/letsencrypt/live/$DOMAIN/fullchain.pem' \
    -subj '/CN=$DOMAIN'" certbot

echo "### Starting nginx (serving the dummy certificate for now) ..."
$COMPOSE up -d --force-recreate frontend

echo "### Deleting the dummy certificate ..."
$COMPOSE run --rm --entrypoint "\
  rm -rf /etc/letsencrypt/live/$DOMAIN /etc/letsencrypt/archive/$DOMAIN /etc/letsencrypt/renewal/$DOMAIN.conf" certbot

echo "### Requesting the real certificate for $DOMAIN ..."
STAGING_ARG=""
# Let's Encrypt's real rate limits are easy to hit while testing; set CERTBOT_STAGING=1 in .env
# to get a (browser-untrusted) staging cert first and confirm the whole flow works.
[ "${CERTBOT_STAGING:-0}" != "0" ] && STAGING_ARG="--staging"

$COMPOSE run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    $STAGING_ARG \
    --email $CERTBOT_EMAIL \
    -d $DOMAIN \
    --rsa-key-size $RSA_KEY_SIZE \
    --agree-tos \
    --force-renewal" certbot

echo "### Reloading nginx with the real certificate ..."
$COMPOSE exec frontend nginx -s reload

echo "### Done. https://$DOMAIN is live."
