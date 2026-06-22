<?php

namespace App\Twig;

use App\Entity\StaffUser;
use App\Service\Admin\OnboardingQuestCatalog;
use Symfony\Bundle\SecurityBundle\Security;
use Twig\Extension\AbstractExtension;
use Twig\TwigFunction;

final class OnboardingTwigExtension extends AbstractExtension
{
    public function __construct(
        private readonly OnboardingQuestCatalog $catalog,
        private readonly Security $security,
    ) {
    }

    public function getFunctions(): array
    {
        return [
            new TwigFunction('dz_onboarding_quest_json', $this->questJson(...), ['is_safe' => ['html']]),
            new TwigFunction('dz_onboarding_user_id', $this->userId(...)),
        ];
    }

    private function questJson(): string
    {
        return json_encode(
            $this->catalog->export(),
            JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP,
        );
    }

    private function userId(): string
    {
        $user = $this->security->getUser();

        return $user instanceof StaffUser ? (string) $user->getId() : 'guest';
    }
}
