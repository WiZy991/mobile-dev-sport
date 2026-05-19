FROM php:8.4-cli

# ca-certificates + PHP curl (+ бинарь curl для отладки). CA bundle Mozilla — иногда надёжнее только Debian при обращении к oauth.sber.ru.
# Если curl (60) остаётся — на сети VPS часто HTTPS-инспекция; тогда свой корень в /usr/local/share/ca-certificates/*.crt + update-ca-certificates
# или временно SBER_ID_SSL_VERIFY=0 в .env.local (небезопасно).
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
    && update-ca-certificates \
    && curl -fsSL https://curl.se/ca/cacert.pem -o /etc/ssl/certs/mozilla-cacert.pem \
    && chmod 644 /etc/ssl/certs/mozilla-cacert.pem \
    && docker-php-ext-configure gd --with-freetype --with-jpeg \
    && docker-php-ext-install curl gd intl pdo_mysql zip \
    && printf '%s\n' \
        'openssl.cafile=/etc/ssl/certs/mozilla-cacert.pem' \
        'curl.cainfo=/etc/ssl/certs/mozilla-cacert.pem' \
        > /usr/local/etc/php/conf.d/99-ca.ini \
    && rm -rf /var/lib/apt/lists/*

ENV CURL_CA_BUNDLE=/etc/ssl/certs/mozilla-cacert.pem \
    SSL_CERT_FILE=/etc/ssl/certs/mozilla-cacert.pem \
    REQUESTS_CA_BUNDLE=/etc/ssl/certs/mozilla-cacert.pem

COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

WORKDIR /app

COPY composer.json composer.lock symfony.lock ./
RUN composer install --no-interaction --prefer-dist --optimize-autoloader --no-scripts

COPY . .

EXPOSE 8000
