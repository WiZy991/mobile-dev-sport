<?php

namespace App\Twig;

use App\Entity\StaffUser;
use App\Service\Admin\AdminMenuBuilder;
use Symfony\Bundle\SecurityBundle\Security;
use Twig\Extension\AbstractExtension;
use Twig\TwigFunction;

final class AdminSecurityExtension extends AbstractExtension
{
    public function __construct(
        private readonly Security $security,
        private readonly AdminMenuBuilder $adminMenuBuilder,
    ) {
    }

    public function getFunctions(): array
    {
        return [
            new TwigFunction('admin_can_mutate', $this->adminCanMutate(...)),
            new TwigFunction('admin_can_coach', $this->adminCanCoach(...)),
        ];
    }

    public function adminCanMutate(): bool
    {
        $u = $this->security->getUser();

        return $u instanceof StaffUser && $this->adminMenuBuilder->canMutateAdmin($u);
    }

    /** Тренер: расписание, записи, карточка тренера — или полный доступ админ/менеджер. */
    public function adminCanCoach(): bool
    {
        $u = $this->security->getUser();

        return $u instanceof StaffUser
            && ($this->adminMenuBuilder->canMutateAdmin($u) || $this->adminMenuBuilder->hasTrainerRole($u));
    }
}
