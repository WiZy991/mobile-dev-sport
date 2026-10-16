#!/usr/bin/env bash
# Продлевает сертификат Let's Encrypt и ставит авто-установку в nginx при будущих продлениях.
#
# Использование (на сервере, от root):
#   bash scripts/letsencrypt-renew.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

DOMAIN="${LE_DOMAIN:-worldcashfit.ru}"
export COMPOSE_FILE="compose.yaml:compose.https.yaml"
HOOK="/etc/letsencrypt/renewal-hooks/deploy/crm-nginx.sh"

if ! command -v certbot >/dev/null 2>&1; then
  apt-get update
  apt-get install -y certbot
fi

mkdir -p "${ROOT}/docker/nginx/certbot-webroot"
docker compose up -d nginx

# Certbot сам не знает про docker/nginx/certs — без хука nginx остаётся со старым файлом.
mkdir -p "$(dirname "${HOOK}")"
cat > "${HOOK}" <<EOF
#!/usr/bin/env bash
LE_DOMAIN="${DOMAIN}" bash "${ROOT}/scripts/letsencrypt-install-cert.sh"
EOF
chmod 755 "${HOOK}"

certbot renew --cert-name "${DOMAIN}" --non-interactive || {
  echo "certbot renew не сработал — выпускаем заново через webroot"
  certbot certonly --webroot \
    -w "${ROOT}/docker/nginx/certbot-webroot" \
    --cert-name "${DOMAIN}" \
    -d "${DOMAIN}" -d "www.${DOMAIN}" \
    --non-interactive --agree-tos --register-unsafely-without-email \
    --force-renewal
}

LE_DOMAIN="${DOMAIN}" bash "${ROOT}/scripts/letsencrypt-install-cert.sh"

if command -v systemctl >/dev/null 2>&1 && systemctl list-timers 2>/dev/null | grep -q certbot; then
  echo "Авто-продление: systemd certbot.timer активен"
elif ! crontab -l 2>/dev/null | grep -q 'certbot renew'; then
  (crontab -l 2>/dev/null; echo "17 3 * * * certbot renew --quiet") | crontab -
  echo "Авто-продление: добавлен cron (ежедневно 03:17)"
fi
