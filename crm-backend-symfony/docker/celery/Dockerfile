FROM python:3.12-slim

WORKDIR /app

COPY docker/celery/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY docker/celery/worker.py /app/worker.py

RUN useradd --system --uid 1000 --create-home celery \
    && chown -R celery:celery /app

ENV PYTHONUNBUFFERED=1 \
    TZ=UTC

USER celery

CMD ["celery", "-A", "worker", "worker", "--loglevel=info", "--concurrency=2"]
