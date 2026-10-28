#!/bin/sh
set -eu

DOMAIN="${MAIL_DOMAIN:-worldcashfit.ru}"
HOSTNAME="${MAIL_HOSTNAME:-mail.${DOMAIN}}"
SELECTOR="${DKIM_SELECTOR:-default}"
KEYS_DIR="/etc/opendkim/keys/${DOMAIN}"
OUT_DIR="${DKIM_OUT_DIR:-/out}"

mkdir -p "$KEYS_DIR" /etc/opendkim "$OUT_DIR" /var/spool/postfix /var/lib/postfix /run/opendkim /var/log

if [ ! -f "${KEYS_DIR}/${SELECTOR}.private" ]; then
    opendkim-genkey -D "$KEYS_DIR" -d "$DOMAIN" -s "$SELECTOR"
fi
chown -R opendkim:opendkim /etc/opendkim /etc/opendkim/keys /run/opendkim
chmod 600 "${KEYS_DIR}/${SELECTOR}.private"
chmod 644 "${KEYS_DIR}/${SELECTOR}.txt" 2>/dev/null || true

# DNS-запись для копирования в панель домена (BeGet). MX не трогать.
if [ -f "${KEYS_DIR}/${SELECTOR}.txt" ]; then
    DKIM_VALUE=$(grep -o '"[^"]*"' "${KEYS_DIR}/${SELECTOR}.txt" | tr -d '"' | tr -d '\n' | tr -s ' ')
    {
        echo "# DNS worldcashfit.ru (панель BeGet). MX mx1/mx2.beget.com НЕ менять."
        echo "# Публичный IPv4 этого VPS: curl -4 ifconfig.me"
        echo "#"
        echo "# 1) SPF  TXT  @   заменить redirect=beget.com на:"
        echo "#    v=spf1 include:beget.com ip4:IPv4_VPS -all"
        echo "# 2) A    mail     = IPv4_VPS"
        echo "# 3) DKIM TXT  ${SELECTOR}._domainkey  ="
        echo "${DKIM_VALUE}"
        echo "# 4) DMARC TXT  _dmarc  ="
        echo "#    v=DMARC1; p=none; rua=mailto:noreply@${DOMAIN}"
        echo "# 5) PTR у хостера VPS: IPv4_VPS -> ${HOSTNAME}"
    } > "${OUT_DIR}/dkim-dns.txt"
    echo "==== DKIM / DNS ===="
    cat "${OUT_DIR}/dkim-dns.txt"
    echo "===================="
fi

cat > /etc/opendkim.conf <<EOF
Syslog                  yes
SyslogSuccess           yes
LogWhy                  yes
Canonicalization        relaxed/relaxed
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
OversignHeaders         From
AlwaysAddARHeader       yes
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
chown opendkim:opendkim /etc/opendkim/KeyTable /etc/opendkim/SigningTable /etc/opendkim/TrustedHosts
chmod 644 /etc/opendkim/KeyTable /etc/opendkim/SigningTable /etc/opendkim/TrustedHosts

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
postconf -e "smtp_helo_name = ${HOSTNAME}"
postconf -e "smtp_tls_security_level = may"
postconf -e "smtp_tls_loglevel = 1"
postconf -e "smtpd_tls_security_level = none"
postconf -e "smtpd_banner = \$myhostname ESMTP"
postconf -e "maillog_file = /dev/stdout"
postconf -e "compatibility_level = 3.6"
# Без подписи DKIM письмо не отправляем (иначе Mail.ru сразу режет как спам).
postconf -e "milter_default_action = tempfail"
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
ready=0
i=0
while [ "$i" -lt 20 ]; do
    if nc -z 127.0.0.1 8891 2>/dev/null; then
        ready=1
        break
    fi
    i=$((i + 1))
    sleep 0.3
done
if [ "$ready" != 1 ]; then
    echo "OpenDKIM did not start on 127.0.0.1:8891" >&2
    opendkim -n -x /etc/opendkim.conf || true
    exit 1
fi
echo "OpenDKIM listening on 127.0.0.1:8891"

exec /usr/sbin/postfix start-fg
