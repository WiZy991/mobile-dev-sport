"""Письма восстановления пароля: Celery забирает задачу из Redis и шлёт через Postfix (mail:25)."""

from __future__ import annotations

import os
import smtplib
from email.header import Header
from email.mime.text import MIMEText
from email.utils import formataddr, formatdate, make_msgid

from celery import Celery

broker = os.environ.get("CELERY_BROKER_URL", "redis://redis:6379/0")
app = Celery("crm_mail", broker=broker)
app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    task_ignore_result=True,
    task_acks_late=True,
    worker_prefetch_multiplier=1,
    broker_connection_retry_on_startup=True,
    timezone="UTC",
    enable_utc=True,
)


def _smtp_send(to: str, subject: str, text: str, mail_from: str, mail_from_name: str) -> None:
    host = os.environ.get("SMTP_HOST", "mail")
    port = int(os.environ.get("SMTP_PORT", "25"))
    msg = MIMEText(text, "plain", "utf-8")
    msg["Subject"] = Header(subject, "utf-8")
    msg["From"] = formataddr((str(Header(mail_from_name, "utf-8")), mail_from))
    msg["To"] = to
    msg["Date"] = formatdate(localtime=False)
    domain = mail_from.rsplit("@", 1)[-1] if "@" in mail_from else "worldcashfit.ru"
    msg["Message-ID"] = make_msgid(domain=domain)
    msg["Auto-Submitted"] = "auto-generated"

    with smtplib.SMTP(host, port, timeout=20) as smtp:
        smtp.sendmail(mail_from, [to], msg.as_string())


@app.task(name="mail.send_password_reset", bind=True, max_retries=5, default_retry_delay=30)
def send_password_reset(
    self,
    to: str,
    subject: str,
    text: str,
    html: str | None = None,
    mail_from: str | None = None,
    mail_from_name: str | None = None,
) -> None:
    sender = mail_from or os.environ.get("MAIL_FROM", "noreply@worldcashfit.ru")
    sender_name = mail_from_name or os.environ.get("MAIL_FROM_NAME", "WorldCashFit")
    try:
        _smtp_send(to, subject, text, sender, sender_name)
    except Exception as exc:
        raise self.retry(exc=exc)
