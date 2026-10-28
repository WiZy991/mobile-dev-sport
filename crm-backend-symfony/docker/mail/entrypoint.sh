#!/bin/sh
set -eu

DOMAIN="${MAIL_DOMAIN:-worldcashfit.ru}"
HOSTNAME="${MAIL_HOSTNAME:-mail.${DOMAIN}}"
SELECTOR="${DKIM_SELECTOR:-default}"
KEYS_DIR="/etc/opendkim/keys/${DOMAIN}"
OUT_DIR="${DKIM_OUT_DIR:-/out}"

mkdir -p "$KEYS_DIR" /etc/opendkim "$OUT_DIR" /var/spool/postfix /var/lib/postfix

if [ ! -f "${KEYS_DIR}/${SELECTOR}.private" ]; then
    opendkim-genkey -D "$KEYS_DIR" -d "$DOMAIN" -s "$SELECTOR"
fi
chown -R opendkim:opendkim /etc/opendkim/keys
chmod 600 "${KEYS_DIR}/${SELECTOR}.private"

# DNS-запись для копирования в панель домена.
if [ -f "${KEYS_DIR}/${SELECTOR}.txt" ]; then
    DKIM_VALUE=$(grep -o '"[^"]*"' "${KEYS_DIR}/${SELECTOR}.txt" | tr -d '"' | tr -d '\n' | tr -s ' ')
    {
        echo "# Добавьте в DNS домена ${DOMAIN}:"
        echo "# 1) SPF  TXT  @  =  v=spf1 ip4:ВАШ_IP_СЕРВЕРА mx -all"
        echo "# 2) DKIM TXT  ${SELECTOR}._domainkey  ="
        echo "${DKIM_VALUE}"
        echo "# 3) DMARC TXT  _dmarc  =  v=DMARC1; p=quarantine; rua=mailto:noreply@${DOMAIN}"
        echo "# PTR (обратная DNS) сервера должен указывать на ${HOSTNAME}"
    } > "${OUT_DIR}/dkim-dns.txt"
    echo "==== DKIM / DNS ===="
    cat "${OUT_DIR}/dkim-dns.txt"
    echo "===================="
fi

cat > /etc/opendkim.conf <<EOF
Syslog                  no
LogWhy                  yes
Canonicalization        relaxed/simple
Mode                    s
SubDomains              yes
AutoRestart             yes
Background              yes
DNSTimeout              5
SignatureAlgorithm      rsa-sha256
Socket                  inet:8891@127.0.0.1
PidFile                 /run/opendkim/opendkim.pid
KeyTable                /etc/opendkim/KeyTable
SigningTable            refile:/etc/opendkim/SigningTable
InternalHosts           /etc/opendkim/TrustedHosts
ExternalIgnoreList      /etc/opendkim/TrustedHosts
UserID                  opendkim
UMask                   007
EOF

echo "${SELECTOR}._domainkey.${DOMAIN} ${DOMAIN}:${SELECTOR}:${KEYS_DIR}/${SELECTOR}.private" > /etc/opendkim/KeyTable
echo "*@${DOMAIN} ${SELECTOR}._domainkey.${DOMAIN}" > /etc/opendkim/SigningTable
cat > /etc/opendkim/TrustedHosts <<EOF
127.0.0.1
localhost
::1
mail
${HOSTNAME}
172.16.0.0/12
10.0.0.0/8
192.168.0.0/16
EOF

mkdir -p /run/opendkim
chown opendkim:opendkim /run/opendkim

postconf -e "myhostname = ${HOSTNAME}"
postconf -e "mydomain = ${DOMAIN}"
postconf -e "myorigin = \$mydomain"
postconf -e "inet_interfaces = all"
postconf -e "inet_protocols = ipv4"
postconf -e "mydestination ="
postconf -e "local_transport = error:local delivery disabled"
postconf -e "mynetworks = 127.0.0.0/8 [::1]/128 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16"
postconf -e "smtpd_recipient_restrictions = permit_mynetworks, reject"
postconf -e "smtpd_relay_restrictions = permit_mynetworks, reject"
postconf -e "smtpd_sender_restrictions = permit_mynetworks, reject"
postconf -e "smtpd_client_restrictions = permit_mynetworks, reject"
postconf -e "relayhost ="
postconf -e "smtp_tls_security_level = may"
postconf -e "smtpd_tls_security_level = none"
postconf -e "smtpd_banner = \$myhostname ESMTP"
postconf -e "maillog_file = /dev/stdout"
postconf -e "compatibility_level = 3.6"
postconf -e "milter_default_action = accept"
postconf -e "milter_protocol = 6"
postconf -e "smtpd_milters = inet:127.0.0.1:8891"
postconf -e "non_smtpd_milters = inet:127.0.0.1:8891"
postconf -e "mailbox_size_limit = 0"
postconf -e "recipient_delimiter = +"
postconf -e "alias_maps ="
postconf -e "alias_database ="

# Без chroot — иначе milter и логи в контейнере ломаются.
postconf -F '*/*/chroot = n' || true

opendkim -x /etc/opendkim.conf
sleep 1
exec /usr/sbin/postfix start-fg
