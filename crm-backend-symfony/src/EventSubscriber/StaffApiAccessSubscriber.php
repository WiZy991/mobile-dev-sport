<?php

declare(strict_types=1);

namespace App\EventSubscriber;

use App\Service\CurrentStaffUserResolver;
use App\Service\Staff\StaffOnboardingService;
use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\HttpKernel\KernelEvents;

/**
 * Блокирует рабочие staff API для тренеров без одобрения / без аренды.
 */
final class StaffApiAccessSubscriber implements EventSubscriberInterface
{
    public function __construct(
        private readonly CurrentStaffUserResolver $staffResolver,
        private readonly StaffOnboardingService $onboarding,
    ) {
    }

    public static function getSubscribedEvents(): array
    {
        return [KernelEvents::REQUEST => ['onKernelRequest', 4]];
    }

    public function onKernelRequest(RequestEvent $event): void
    {
        if (!$event->isMainRequest()) {
            return;
        }

        $request = $event->getRequest();
        $path = $request->getPathInfo();
        if (!str_starts_with($path, '/api/v1/staff/')) {
            return;
        }

        if ($this->isExempt($path)) {
            return;
        }

        $user = $this->staffResolver->resolve($request);
        if ($user === null) {
            return; // контроллер сам вернёт 401
        }

        if ($this->onboarding->canAccessWorkApis($user)) {
            return;
        }

        $gate = $this->onboarding->resolveGate($user);
        $code = match ($gate) {
            StaffOnboardingService::GATE_PENDING => 'pending_approval',
            StaffOnboardingService::GATE_REJECTED => 'registration_rejected',
            StaffOnboardingService::GATE_NEEDS_PAYMENT => 'rental_required',
            default => 'access_restricted',
        };

        $event->setResponse(new JsonResponse([
            'error' => 'Доступ к рабочим разделам ограничен',
            'code' => $code,
            'onboarding' => $this->onboarding->serialize($user),
        ], 403));
    }

    private function isExempt(string $path): bool
    {
        $exemptPrefixes = [
            '/api/v1/staff/auth/',
            '/api/v1/staff/onboarding',
            '/api/v1/staff/rental/',
            '/api/v1/staff/config',
            '/api/v1/staff/push-token',
            '/api/v1/staff/notifications',
        ];
        foreach ($exemptPrefixes as $prefix) {
            if (str_starts_with($path, $prefix) || $path === rtrim($prefix, '/')) {
                return true;
            }
        }

        return false;
    }
}
