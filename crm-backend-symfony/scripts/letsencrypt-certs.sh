#!/usr/bin/env bash
# Выпускает доверенный TLS-сертификат Let's Encrypt для worldcashfit.ru.
# Требования: DNS домена → IP сервера, nginx слушает :80, certbot на хосте.
#
# Использование:
#   CERTBOT_EMAIL=you@example.com bash scripts/letsencrypt-certs.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

DOMAIN="${LE_DOMAIN:-worldcashfit.ru}"
EMAIL="${CERTBOT_EMAIL:-}"
WEBROOT="${ROOT}/docker/nginx/certbot-webroot"
CERT_DIR="${ROOT}/docker/nginx/certs"
export COMPOSE_FILE="compose.yaml:compose.https.yaml"

if [[ -z "${EMAIL}" ]]; then
  echo "ERROR: укажите email для Let's Encrypt:"
  echo "  CERTBOT_EMAIL=you@example.com bash scripts/letsencrypt-certs.sh"
  exit 1
fi

if ! command -v certbot >/dev/null 2>&1; then
  echo "Installing certbot..."
  apt-get update
  apt-get install -y certbot
fi

mkdir -p "${WEBROOT}" "${CERT_DIR}"

echo "Checking nginx is up (port 80 must serve ACME challenge)..."
docker compose up -d nginx

certbot certonly --webroot \
  -w "${WEBROOT}" \
  -d "${DOMAIN}" \
  -d "www.${DOMAIN}" \
  --email "${EMAIL}" \
  --agree-tos \
  --non-interactive \
  --keep-until-expiring

LE_LIVE="/etc/letsencrypt/live/${DOMAIN}"
if [[ ! -f "${LE_LIVE}/fullchain.pem" || ! -f "${LE_LIVE}/privkey.pem" ]]; then
  echo "ERROR: certbot did not create certs in ${LE_LIVE}"
  exit 1
fi

cp "${LE_LIVE}/fullchain.pem" "${CERT_DIR}/fullchain.pem"
cp "${LE_LIVE}/privkey.pem" "${CERT_DIR}/privkey.pem"
chmod 644 "${CERT_DIR}/fullchain.pem"
chmod 600 "${CERT_DIR}/privkey.pem"

echo "Reloading nginx..."
docker compose exec nginx nginx -s reload

echo "OK: trusted Let's Encrypt certificate installed for ${DOMAIN}"
echo "Renewal: certbot renew (add to cron, then copy certs and reload nginx)"
