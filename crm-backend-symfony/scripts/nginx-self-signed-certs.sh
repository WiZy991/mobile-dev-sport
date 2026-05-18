#!/usr/bin/env bash
set -euo pipefail
# Самоподписанный сертификат для docker/nginx (браузеры будут ругаться — только для проверки).
# В продакшене замените файлы на Let's Encrypt (certbot) и при необходимости поправьте compose.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CERT_DIR="${ROOT}/docker/nginx/certs"
mkdir -p "${CERT_DIR}"
openssl req -x509 -nodes -days 825 -newkey rsa:2048 \
  -keyout "${CERT_DIR}/privkey.pem" \
  -out "${CERT_DIR}/fullchain.pem" \
  -subj "/CN=worldcashfit.ru"
echo "OK: ${CERT_DIR}/fullchain.pem и privkey.pem"
