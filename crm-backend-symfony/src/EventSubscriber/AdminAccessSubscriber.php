<?php

namespace App\EventSubscriber;

use App\Entity\StaffUser;
use App\Service\Admin\AdminMenuBuilder;
use App\Service\Admin\AdminRouteSectionMapper;
use Symfony\Bundle\SecurityBundle\Security;
use Symfony\Component\EventDispatcher\Attribute\AsEventListener;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\HttpKernel\KernelEvents;
use Symfony\Component\Security\Core\Exception\AccessDeniedException;

#[AsEventListener(event: KernelEvents::REQUEST, priority: -5)]
final class AdminAccessSubscriber
{
    public function __construct(
        private readonly Security $security,
        private readonly AdminMenuBuilder $menuBuilder,
        private readonly AdminRouteSectionMapper $routeSectionMapper,
    ) {
    }

    public function __invoke(RequestEvent $event): void
    {
        if (!$event->isMainRequest()) {
            return;
        }
        $request = $event->getRequest();
        if (!str_starts_with($request->getPathInfo(), '/admin')) {
            return;
        }

        $route = $request->attributes->get('_route');
        if (in_array($route, ['admin_login', 'admin_login_check'], true)) {
            return;
        }

        $user = $this->security->getUser();
        if (!$user instanceof StaffUser) {
            return;
        }

        if (!$user->isActive()) {
            throw new AccessDeniedException('Учётная запись отключена.');
        }

        $section = $this->routeSectionMapper->resolveSection($request);
        if ($section === null) {
            return;
        }

        if (!$this->menuBuilder->isSectionAllowed($user, $section)) {
            throw new AccessDeniedException('Нет доступа к этому разделу.');
        }

        if (!$this->menuBuilder->canMutateAdmin($user)
            && is_string($route)
            && in_array($route, ['admin_sber_id_start', 'admin_sber_id_callback'], true)) {
            throw new AccessDeniedException('Верификация клиентов недоступна для этой роли.');
        }

        $method = $request->getMethod();
        if (\in_array($method, ['GET', 'HEAD'], true)) {
            return;
        }

        if ($this->menuBuilder->canWriteCurrentAdminOrTrainer($user, is_string($route) ? $route : null, $method)) {
            return;
        }

        throw new AccessDeniedException('Доступ только для просмотра.');
    }
}
