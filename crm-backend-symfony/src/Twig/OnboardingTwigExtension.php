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

    /** @var array<string, list<string>> */
    private const MASCOT_HEAD_POOLS = [
        'neutral' => [
            '/img/mascot/heads/zalka-head-01.png',
            '/img/mascot/heads/zalka-head-05.png',
            '/img/mascot/heads/zalka-head-09.png',
            '/img/mascot/heads/zalka-head-13.png',
        ],
        'happy' => [
            '/img/mascot/heads/zalka-head-02.png',
            '/img/mascot/heads/zalka-head-06.png',
            '/img/mascot/heads/zalka-head-10.png',
            '/img/mascot/heads/zalka-head-14.png',
        ],
        'excited' => [
            '/img/mascot/heads/zalka-head-03.png',
            '/img/mascot/heads/zalka-head-07.png',
            '/img/mascot/heads/zalka-head-11.png',
            '/img/mascot/heads/zalka-head-15.png',
        ],
        'thinking' => [
            '/img/mascot/heads/zalka-head-04.png',
            '/img/mascot/heads/zalka-head-08.png',
            '/img/mascot/heads/zalka-head-12.png',
            '/img/mascot/heads/zalka-head-16.png',
        ],
        'sad' => [
            '/img/mascot/heads/zalka-head-13.png',
            '/img/mascot/heads/zalka-head-14.png',
            '/img/mascot/heads/zalka-head-15.png',
            '/img/mascot/heads/zalka-head-16.png',
        ],
        'celebrate' => [
            '/img/mascot/heads/zalka-head-03.png',
            '/img/mascot/heads/zalka-head-07.png',
            '/img/mascot/heads/zalka-head-11.png',
            '/img/mascot/heads/zalka-head-15.png',
        ],
    ];

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

        $pool = self::MASCOT_HEAD_POOLS[$face] ?? self::MASCOT_HEAD_POOLS['neutral'];

        return $pool[0];
    }

    private function mascotFacesJson(): string
    {
        return json_encode(self::MASCOT_HEAD_POOLS, JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP);
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
