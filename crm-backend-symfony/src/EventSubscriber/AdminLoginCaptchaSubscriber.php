<?php

declare(strict_types=1);

namespace App\EventSubscriber;

use App\Service\Auth\AuthAbuseGuard;
use Symfony\Component\EventDispatcher\Attribute\AsEventListener;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\HttpKernel\KernelEvents;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;

/**
 * Капча на входе в CRM только после частых попыток с одного IP.
 */
#[AsEventListener(event: KernelEvents::REQUEST, priority: 9)]
final class AdminLoginCaptchaSubscriber
{
    public function __construct(
        private readonly AuthAbuseGuard $abuse,
        private readonly UrlGeneratorInterface $urls,
    ) {
    }

    public function __invoke(RequestEvent $event): void
    {
        if (!$event->isMainRequest()) {
            return;
        }
        $request = $event->getRequest();
        if ($request->getPathInfo() !== '/admin/login_check' || !$request->isMethod('POST')) {
            return;
        }

        $error = $this->abuse->enforceWeb(
            $request,
            'staff_login',
            trim((string) $request->request->get('captcha_id', '')) ?: null,
            trim((string) $request->request->get('captcha_answer', '')) ?: null,
        );
        if ($error === null) {
            return;
        }

        $request->getSession()->getFlashBag()->add('danger', $error);
        $challenge = $this->abuse->challengeIfNeeded($request, 'staff_login');
        if ($challenge !== null) {
            $request->getSession()->set('admin_login_captcha', $challenge);
        }
        $event->setResponse(new RedirectResponse($this->urls->generate('admin_login')));
    }
}
