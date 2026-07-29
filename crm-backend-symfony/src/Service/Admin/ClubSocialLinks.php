<?php

declare(strict_types=1);

namespace App\Service\Admin;

/**
 * Каталог соцсетей клуба для CRM и API приложения.
 *
 * @phpstan-type SocialLink array{type: string, label: string, url: string, color: string}
 */
final class ClubSocialLinks
{
    /** @var array<string, array{label: string, color: string, placeholder: string}> */
    public const CATALOG = [
        'website' => ['label' => 'Сайт', 'color' => '#64748B', 'placeholder' => 'https://…'],
        'vk' => ['label' => 'ВКонтакте', 'color' => '#4C75A3', 'placeholder' => 'https://vk.com/…'],
        'telegram' => ['label' => 'Telegram', 'color' => '#0088CC', 'placeholder' => 'https://t.me/…'],
        'whatsapp' => ['label' => 'WhatsApp', 'color' => '#25D366', 'placeholder' => 'https://wa.me/79…'],
        'youtube' => ['label' => 'YouTube', 'color' => '#FF0000', 'placeholder' => 'https://youtube.com/…'],
        'instagram' => ['label' => 'Instagram', 'color' => '#E4405F', 'placeholder' => 'https://instagram.com/…'],
        'ok' => ['label' => 'Одноклассники', 'color' => '#EE8208', 'placeholder' => 'https://ok.ru/…'],
        'max' => ['label' => 'MAX', 'color' => '#1A73E8', 'placeholder' => 'https://max.ru/…'],
        'dzen' => ['label' => 'Дзен', 'color' => '#000000', 'placeholder' => 'https://dzen.ru/…'],
        'rutube' => ['label' => 'Rutube', 'color' => '#1A1A1A', 'placeholder' => 'https://rutube.ru/…'],
        'tiktok' => ['label' => 'TikTok', 'color' => '#010101', 'placeholder' => 'https://tiktok.com/@…'],
        'other' => ['label' => 'Другое', 'color' => '#6B7280', 'placeholder' => 'https://…'],
    ];

    /**
     * @return list<SocialLink>
     */
    public static function normalizeFromRequest(array $types, array $urls): array
    {
        $out = [];
        $n = max(\count($types), \count($urls));
        for ($i = 0; $i < $n; ++$i) {
            $type = strtolower(trim((string) ($types[$i] ?? '')));
            $url = trim((string) ($urls[$i] ?? ''));
            if ($url === '') {
                continue;
            }
            if (!isset(self::CATALOG[$type])) {
                $type = 'other';
            }
            if (!preg_match('#^https?://#i', $url)) {
                $url = 'https://' . $url;
            }
            $meta = self::CATALOG[$type];
            $out[] = [
                'type' => $type,
                'label' => $meta['label'],
                'url' => $url,
                'color' => $meta['color'],
            ];
        }

        return $out;
    }

    /**
     * @return list<SocialLink>
     */
    public static function decode(?string $json): array
    {
        if ($json === null || trim($json) === '') {
            return [];
        }
        $decoded = json_decode($json, true);
        if (!\is_array($decoded)) {
            return [];
        }

        $out = [];
        foreach ($decoded as $row) {
            if (!\is_array($row)) {
                continue;
            }
            $type = strtolower(trim((string) ($row['type'] ?? 'other')));
            $url = trim((string) ($row['url'] ?? ''));
            if ($url === '') {
                continue;
            }
            if (!isset(self::CATALOG[$type])) {
                $type = 'other';
            }
            $meta = self::CATALOG[$type];
            $out[] = [
                'type' => $type,
                'label' => (string) ($row['label'] ?? $meta['label']),
                'url' => $url,
                'color' => (string) ($row['color'] ?? $meta['color']),
            ];
        }

        return $out;
    }

    /**
     * Миграция старых полей social_vk / social_telegram / contact_website → список.
     *
     * @return list<SocialLink>
     */
    public static function fromLegacy(?string $website, ?string $vk, ?string $telegram): array
    {
        $types = [];
        $urls = [];
        if ($website !== null && trim($website) !== '') {
            $types[] = 'website';
            $urls[] = trim($website);
        }
        if ($vk !== null && trim($vk) !== '') {
            $types[] = 'vk';
            $urls[] = trim($vk);
        }
        if ($telegram !== null && trim($telegram) !== '') {
            $types[] = 'telegram';
            $urls[] = trim($telegram);
        }

        return self::normalizeFromRequest($types, $urls);
    }

    /**
     * @param list<SocialLink> $links
     */
    public static function encode(array $links): ?string
    {
        if ($links === []) {
            return null;
        }

        return json_encode(array_values($links), \JSON_UNESCAPED_UNICODE | \JSON_UNESCAPED_SLASHES) ?: null;
    }

    /**
     * @param list<SocialLink> $links
     */
    public static function firstUrlByType(array $links, string $type): ?string
    {
        foreach ($links as $link) {
            if (($link['type'] ?? '') === $type && ($link['url'] ?? '') !== '') {
                return $link['url'];
            }
        }

        return null;
    }
}
