FROM php:8.4-fpm

# Сертификаты oauth.sber.ru выдаются «Russian Trusted Sub CA» (Минцифры). В международных и старых бандлах этих корней нет —
# добавляем официальные PEM с gu-st.ru, затем единый /etc/ssl/certs/ca-certificates.crt для curl, PHP openssl и Symfony.
# См. также: developers.sber.ru/help/certificates
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

# Несколько воркеров: иначе long-poll шлюза / тяжёлый CRM блокируют ВСЕ mobile API (как на php -S).
RUN printf '%s\n' \
        '[www]' \
        'pm = dynamic' \
        'pm.max_children = 20' \
        'pm.start_servers = 4' \
        'pm.min_spare_servers = 2' \
        'pm.max_spare_servers = 8' \
        'pm.max_requests = 500' \
        'request_terminate_timeout = 60' \
        'clear_env = no' \
    > /usr/local/etc/php-fpm.d/zz-crm.conf

COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

WORKDIR /app

COPY composer.json composer.lock symfony.lock ./
RUN composer install --no-interaction --prefer-dist --optimize-autoloader --no-scripts --no-dev

COPY . .
# classmap после COPY: иначе новые контроллеры могут не попасть в optimized autoload
RUN composer dump-autoload --no-interaction --optimize --no-dev \
    && chown -R www-data:www-data /app/var || true

EXPOSE 9000
