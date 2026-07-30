FROM php:8.4-fpm

# Сертификаты oauth.sber.ru выдаются «Russian Trusted Sub CA» (Минцифры).
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        libcurl4-openssl-dev \
        libfreetype6-dev \
        libicu-dev \
        libjpeg62-turbo-dev \
        libpng-dev \
        libzip-dev \
        nginx \
        supervisor \
        unzip \
    && curl -fsSL https://gu-st.ru/content/lending/russian_trusted_root_ca_pem.crt \
        -o /usr/local/share/ca-certificates/russian-trusted-root.crt \
    && curl -fsSL https://gu-st.ru/content/lending/russian_trusted_sub_ca_pem.crt \
        -o /usr/local/share/ca-certificates/russian-trusted-sub.crt \
    && update-ca-certificates \
    && docker-php-ext-configure gd --with-freetype --with-jpeg \
    && docker-php-ext-install curl gd intl pdo_mysql zip \
    && printf '%s\n' \
        'openssl.cafile=/etc/ssl/certs/ca-certificates.crt' \
        'curl.cainfo=/etc/ssl/certs/ca-certificates.crt' \
        > /usr/local/etc/php/conf.d/99-ca.ini \
    && rm -rf /var/lib/apt/lists/*

ENV CURL_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt \
    SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt \
    REQUESTS_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt \
    TZ=UTC \
    APP_TIMEZONE=UTC

RUN printf '%s\n' 'date.timezone=UTC' > /usr/local/etc/php/conf.d/99-timezone.ini

# FPM только localhost — HTTP отдаёт nginx в ЭТОМ же контейнере на :8000 (как раньше для TLS-nginx).
RUN printf '%s\n' \
        '[www]' \
        'listen = 127.0.0.1:9000' \
        'pm = dynamic' \
        'pm.max_children = 20' \
        'pm.start_servers = 4' \
        'pm.min_spare_servers = 2' \
        'pm.max_spare_servers = 8' \
        'pm.max_requests = 500' \
        'request_terminate_timeout = 60' \
        'clear_env = no' \
        'catch_workers_output = yes' \
        'decorate_workers_output = no' \
    > /usr/local/etc/php-fpm.d/zz-crm.conf \
    && sed -i 's/^listen = .*/listen = 127.0.0.1:9000/' /usr/local/etc/php-fpm.d/www.conf

COPY docker/app/nginx-app.conf /etc/nginx/sites-available/default
RUN rm -f /etc/nginx/sites-enabled/default \
    && ln -s /etc/nginx/sites-available/default /etc/nginx/sites-enabled/default \
    && rm -f /etc/nginx/conf.d/default.conf \
    && printf '%s\n' \
        '[supervisord]' \
        'nodaemon=true' \
        'user=root' \
        '' \
        '[program:php-fpm]' \
        'command=/usr/local/sbin/php-fpm -F' \
        'autostart=true' \
        'autorestart=true' \
        'priority=10' \
        'stdout_logfile=/dev/stdout' \
        'stdout_logfile_maxbytes=0' \
        'stderr_logfile=/dev/stderr' \
        'stderr_logfile_maxbytes=0' \
        '' \
        '[program:nginx]' \
        'command=/usr/sbin/nginx -g "daemon off;"' \
        'autostart=true' \
        'autorestart=true' \
        'priority=20' \
        'stdout_logfile=/dev/stdout' \
        'stdout_logfile_maxbytes=0' \
        'stderr_logfile=/dev/stderr' \
        'stderr_logfile_maxbytes=0' \
    > /etc/supervisor/conf.d/crm.conf

COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

WORKDIR /app

COPY composer.json composer.lock symfony.lock ./
RUN composer install --no-interaction --prefer-dist --optimize-autoloader --no-scripts --no-dev

COPY . .
RUN composer dump-autoload --no-interaction --optimize --no-dev \
    && chown -R www-data:www-data /app/var || true \
    && chmod +x /app/docker/app/entrypoint.sh

EXPOSE 8000

ENTRYPOINT ["/app/docker/app/entrypoint.sh"]
