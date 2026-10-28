<?php

declare(strict_types=1);

namespace App\EventSubscriber;

use Symfony\Component\EventDispatcher\Attribute\AsEventListener;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpKernel\Event\ExceptionEvent;
use Symfony\Component\HttpKernel\Exception\HttpExceptionInterface;
use Symfony\Component\HttpKernel\KernelEvents;

/**
 * API никогда не отдаёт HTML/стек PHP клиенту приложения.
 */
#[AsEventListener(event: KernelEvents::EXCEPTION, priority: 16)]
final class ApiClientExceptionSubscriber
{
    public function __invoke(ExceptionEvent $event): void
    {
        if (!$event->isMainRequest()) {
            return;
        }
        $path = $event->getRequest()->getPathInfo();
        if (!str_starts_with($path, '/api/')) {
            return;
        }

        $e = $event->getThrowable();
        $status = $e instanceof HttpExceptionInterface ? $e->getStatusCode() : 500;
        $raw = trim($e->getMessage());
        $message = $this->isClientSafe($raw) ? $raw : $this->humanStatus($status);

        $event->setResponse(new JsonResponse([
            'error' => $message,
            'code' => $status >= 500 ? 'server_error' : 'request_failed',
        ], $status >= 400 ? $status : 500));
    }

    private function isClientSafe(string $text): bool
    {
        if ($text === '' || strlen($text) > 240) {
            return false;
        }
        if (!preg_match('/[А-Яа-яЁё]/u', $text)) {
            return false;
        }
        $lower = mb_strtolower($text);

        return !str_contains($lower, 'exception')
            && !str_contains($lower, 'sqlstate')
            && !str_contains($lower, 'stack')
            && !str_contains($lower, 'vendor/')
            && !str_contains($lower, 'cache:clear');
    }

    private function humanStatus(int $status): string
    {
        return match (true) {
            $status === 404, $status === 405 => 'Сервис временно недоступен. Попробуйте позже.',
            $status === 429 => 'Слишком много попыток. Подождите несколько минут.',
            $status >= 500 => 'Сервис временно недоступен. Попробуйте позже.',
            default => 'Не удалось выполнить запрос. Попробуйте ещё раз.',
        };
    }
}
