#!/usr/bin/env bash
# Копирует сертификат Let's Encrypt в docker/nginx/certs и перезагружает nginx.
# Вызывается certbot'ом после каждого продления (deploy hook) и из letsencrypt-renew.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

DOMAIN="${LE_DOMAIN:-worldcashfit.ru}"
LE_LIVE="/etc/letsencrypt/live/${DOMAIN}"
CERT_DIR="${ROOT}/docker/nginx/certs"
export COMPOSE_FILE="compose.yaml:compose.https.yaml"

if [[ ! -f "${LE_LIVE}/fullchain.pem" || ! -f "${LE_LIVE}/privkey.pem" ]]; then
  echo "ERROR: нет сертификата в ${LE_LIVE}"
  exit 1
fi

mkdir -p "${CERT_DIR}"
cp -L "${LE_LIVE}/fullchain.pem" "${CERT_DIR}/fullchain.pem"
cp -L "${LE_LIVE}/privkey.pem" "${CERT_DIR}/privkey.pem"
chmod 644 "${CERT_DIR}/fullchain.pem"
chmod 600 "${CERT_DIR}/privkey.pem"

docker compose exec -T nginx nginx -s reload
echo "OK: сертификат ${DOMAIN} установлен в nginx, действует до:"
openssl x509 -enddate -noout -in "${CERT_DIR}/fullchain.pem"
