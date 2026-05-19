FROM php:8.4-cli

# ca-certificates + curl: исходящие HTTPS (oauth.sber.ru и т.д.) без «self-signed certificate in certificate chain»
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        git \
        libcurl4-openssl-dev \
        libfreetype6-dev \
        libicu-dev \
        libjpeg62-turbo-dev \
        libpng-dev \
        libzip-dev \
        unzip \
    && update-ca-certificates \
    && docker-php-ext-configure gd --with-freetype --with-jpeg \
    && docker-php-ext-install curl gd intl pdo_mysql zip \
    && printf '%s\n' \
        'openssl.cafile=/etc/ssl/certs/ca-certificates.crt' \
        'curl.cainfo=/etc/ssl/certs/ca-certificates.crt' \
        > /usr/local/etc/php/conf.d/99-ca.ini \
    && rm -rf /var/lib/apt/lists/*

COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

WORKDIR /app

COPY composer.json composer.lock symfony.lock ./
RUN composer install --no-interaction --prefer-dist --optimize-autoloader --no-scripts

COPY . .

EXPOSE 8000
