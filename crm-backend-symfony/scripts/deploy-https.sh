#!/usr/bin/env bash
# Поднимает CRM с HTTPS (nginx + app + db).
# Освобождает порты 80/443, если их занимает nginx/apache на хосте или «зависший» контейнер.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

COMPOSE=(docker compose -f compose.yaml -f compose.https.yaml)
HTTP_PORT="${NGINX_HTTP_PORT:-80}"
HTTPS_PORT="${NGINX_HTTPS_PORT:-443}"

port_in_use() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -tlnH "sport = :${port}" 2>/dev/null | grep -q .
    return
  fi
  if command -v lsof >/dev/null 2>&1; then
    lsof -iTCP:"${port}" -sTCP:LISTEN -t >/dev/null 2>&1
    return
  fi
  return 1
}

stop_host_web_servers() {
  for svc in nginx apache2 httpd caddy; do
    if command -v systemctl >/dev/null 2>&1 && systemctl is-active --quiet "${svc}" 2>/dev/null; then
      echo "Stopping host service: ${svc}"
      systemctl stop "${svc}"
    fi
  done
}

stop_docker_on_port() {
  local port="$1"
  local ids
  ids="$(docker ps -q --filter "publish=${port}" 2>/dev/null || true)"
  if [[ -n "${ids}" ]]; then
    echo "Stopping Docker containers on port ${port}: ${ids}"
    docker stop ${ids}
  fi
}

ensure_port_free() {
  local port="$1"
  if ! port_in_use "${port}"; then
    return 0
  fi

  echo "Port ${port} is in use, trying to free it..."
  stop_host_web_servers
  stop_docker_on_port "${port}"

  if port_in_use "${port}"; then
    echo "ERROR: port ${port} is still in use. Check manually, e.g.:"
    echo "  ss -tlnp | grep ':${port} '"
    echo "  docker ps --format 'table {{.Names}}\t{{.Ports}}'"
    exit 1
  fi
}

if [[ ! -f docker/nginx/certs/fullchain.pem || ! -f docker/nginx/certs/privkey.pem ]]; then
  echo "TLS certs not found, generating self-signed..."
  bash scripts/nginx-self-signed-certs.sh
fi

ensure_port_free "${HTTP_PORT}"
ensure_port_free "${HTTPS_PORT}"

"${COMPOSE[@]}" down --remove-orphans || true
"${COMPOSE[@]}" up -d --build

echo
echo "Stack is up:"
"${COMPOSE[@]}" ps
