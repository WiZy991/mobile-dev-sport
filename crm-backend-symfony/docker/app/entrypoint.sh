#!/bin/sh
set -eu

cd /app

# Каталоги загрузок (том с хоста может быть пустым / root-owned).
mkdir -p /app/public/uploads/clubs /app/public/uploads/promotions /app/public/uploads/trainers /app/var/cache /app/var/log
chown -R www-data:www-data /app/public/uploads /app/var || true

php bin/console cache:clear --no-interaction || true
php bin/console doctrine:database:create --if-not-exists --no-interaction || true
php bin/console doctrine:migrations:migrate --no-interaction || true
chown -R www-data:www-data /app/var || true

# Worker-контейнер переопределяет CMD — тогда entrypoint всё равно запускает миграции, затем CMD.
if [ "$#" -gt 0 ]; then
  exec "$@"
fi

exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
