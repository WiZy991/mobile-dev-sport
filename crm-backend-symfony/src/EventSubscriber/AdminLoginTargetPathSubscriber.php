<?php

declare(strict_types=1);

namespace App\EventSubscriber;

use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;
use Symfony\Component\Security\Http\Event\LoginSuccessEvent;
use Symfony\Component\Security\Http\Util\TargetPathTrait;

/**
 * После логина не возвращаем на AJAX/API URL (например /admin/api/notifications/poll),
 * иначе в браузере открывается сырой JSON вместо админки.
 */
final class AdminLoginTargetPathSubscriber implements EventSubscriberInterface
{
    use TargetPathTrait;

    public function __construct(
        private readonly UrlGeneratorInterface $urlGenerator,
    ) {
    }

    public static function getSubscribedEvents(): array
    {
        return [LoginSuccessEvent::class => 'onLoginSuccess'];
    }

    public function onLoginSuccess(LoginSuccessEvent $event): void
    {
        if ($event->getFirewallName() !== 'admin') {
            return;
        }

        $request = $event->getRequest();
        $session = $request->hasSession() ? $request->getSession() : null;
        if ($session === null) {
            return;
        }

        $targetPath = $this->getTargetPath($session, 'admin');
        if ($targetPath === null || !$this->isNonHtmlAdminTarget($targetPath)) {
            return;
        }

        $this->removeTargetPath($session, 'admin');
        $event->setResponse(new RedirectResponse($this->urlGenerator->generate('admin_dashboard')));
    }

    private function isNonHtmlAdminTarget(string $targetPath): bool
    {
        $path = parse_url($targetPath, PHP_URL_PATH);
        if (!\is_string($path) || $path === '') {
            $path = $targetPath;
        }

        if (str_starts_with($path, '/admin/api/')) {
            return true;
        }

        // На всякий случай: любой poll/json endpoint под /admin
        if (str_contains($path, '/notifications/poll')) {
            return true;
        }

        return false;
    }
}
