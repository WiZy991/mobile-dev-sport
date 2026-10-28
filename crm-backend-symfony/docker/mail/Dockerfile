FROM debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        postfix \
        opendkim \
        opendkim-tools \
        ca-certificates \
        openssl \
    && rm -rf /var/lib/apt/lists/*

COPY docker/mail/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 25

ENTRYPOINT ["/entrypoint.sh"]
