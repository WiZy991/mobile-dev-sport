<?php

declare(strict_types=1);

namespace App\EventSubscriber;

use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\HttpKernel\KernelEvents;

/**
 * Раньше на каждом /api/v1 и /admin запросе синхронно слал FCM/SMTP (до 40 шт.),
 * из‑за cache-lock параллельные запросы home/profile ждали 30–40+ секунд.
 *
 * Отправка только через cron/worker: `app:process-scheduled-notifications`
 * (см. сервис notifications_worker в compose.yaml).
 */
final class ScheduledNotificationRequestSubscriber implements EventSubscriberInterface
{
    public static function getSubscribedEvents(): array
    {
        return [
            // Оставляем подписку пустой по смыслу — класс не трогает request path.
            // (Можно удалить сервис позже; пока no-op, чтобы не ломать DI/документацию.)
            KernelEvents::REQUEST => ['onKernelRequest', -50],
        ];
    }

    public function onKernelRequest(RequestEvent $event): void
    {
        // no-op: не блокируем API отправкой пушей/писем
    }
}
