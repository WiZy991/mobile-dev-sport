FROM python:3.12-slim

WORKDIR /app

COPY docker/celery/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY docker/celery/worker.py /app/worker.py

ENV PYTHONUNBUFFERED=1 \
    TZ=UTC

CMD ["celery", "-A", "worker", "worker", "--loglevel=info", "--concurrency=2"]
