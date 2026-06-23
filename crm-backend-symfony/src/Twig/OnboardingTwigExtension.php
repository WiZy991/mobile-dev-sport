<?php

namespace App\Twig;

use App\Entity\StaffUser;
use App\Service\Admin\OnboardingQuestCatalog;
use Symfony\Bundle\SecurityBundle\Security;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;
use Twig\Extension\AbstractExtension;
use Twig\TwigFunction;

final class OnboardingTwigExtension extends AbstractExtension
{
    private const MASCOT_FACES = ['neutral', 'happy', 'excited', 'thinking', 'celebrate', 'sad'];

    public function __construct(
        private readonly OnboardingQuestCatalog $catalog,
        private readonly Security $security,
        private readonly UrlGeneratorInterface $urlGenerator,
    ) {
    }

    public function getFunctions(): array
    {
        return [
            new TwigFunction('dz_onboarding_quest_json', $this->questJson(...), ['is_safe' => ['html']]),
            new TwigFunction('dz_onboarding_user_id', $this->userId(...)),
            new TwigFunction('dz_mascot_face_url', $this->mascotFaceUrl(...)),
            new TwigFunction('dz_mascot_faces_json', $this->mascotFacesJson(...), ['is_safe' => ['html']]),
        ];
    }

    public function mascotFaceUrl(string $face = 'neutral'): string
    {
        if (!\in_array($face, self::MASCOT_FACES, true)) {
            $face = 'neutral';
        }

        return $this->urlGenerator->generate('admin_mascot_face', ['face' => $face]);
    }

    private function mascotFacesJson(): string
    {
        $map = [];
        foreach (self::MASCOT_FACES as $face) {
            $map[$face] = $this->mascotFaceUrl($face);
        }

        return json_encode($map, JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP);
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
