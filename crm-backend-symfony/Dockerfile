FROM php:8.4-cli

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
    REQUESTS_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt

COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

WORKDIR /app

COPY composer.json composer.lock symfony.lock ./
RUN composer install --no-interaction --prefer-dist --optimize-autoloader --no-scripts

COPY . .

EXPOSE 8000
