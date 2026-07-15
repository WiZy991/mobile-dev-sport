<?php

declare(strict_types=1);

namespace App\EventSubscriber;

use App\Service\Notification\ScheduledNotificationProcessor;
use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\HttpKernel\KernelEvents;
use Symfony\Contracts\Cache\CacheInterface;
use Symfony\Contracts\Cache\ItemInterface;

/**
 * Периодически (не чаще раза в 30 с) обрабатывает отложенные уведомления
 * на фоне обычных HTTP-запросов к API и админке — без отдельного cron.
 */
final class ScheduledNotificationRequestSubscriber implements EventSubscriberInterface
{
    private const THROTTLE_SECONDS = 30;
    private const CACHE_KEY = 'scheduled_notifications_last_process';

    public function __construct(
        private readonly ScheduledNotificationProcessor $processor,
        private readonly CacheInterface $cache,
    ) {
    }

    public static function getSubscribedEvents(): array
    {
        return [
            KernelEvents::REQUEST => ['onKernelRequest', -50],
        ];
    }

    public function onKernelRequest(RequestEvent $event): void
    {
        if (!$event->isMainRequest()) {
            return;
        }

        $path = $event->getRequest()->getPathInfo();
        if (!str_starts_with($path, '/api/v1/') && !str_starts_with($path, '/admin')) {
            return;
        }

        $this->cache->get(self::CACHE_KEY, function (ItemInterface $item) {
            $item->expiresAfter(self::THROTTLE_SECONDS);
            $this->processor->processDue();

            return time();
        });
    }
}
