<?php

declare(strict_types=1);

namespace App\Service\Admin;

/**
 * Опциональные модули CRM — включаются в настройках клуба.
 * Базовые разделы (клиенты, расписание, абонементы…) всегда доступны.
 */
final class ClubModuleRegistry
{
    /** @var array<string, string> ключ => подпись */
    public const OPTIONAL = [
        'access' => 'СКУД / QR-доступ',
        'leads' => 'Лиды и воронка',
        'deals' => 'Сделки',
        'tasks' => 'Задачи',
        'trainers' => 'Тренеры',
        'analytics' => 'Аналитика',
        'gamification' => 'Геймификация',
        'promotions' => 'Акции и промокоды',
        'cashdesk' => 'Касса',
        'warehouse' => 'Склад',
        'selfservice' => 'Самообслуживание',
        'messengers' => 'Мессенджеры',
        'calls' => 'Звонки',
        'franchise' => 'Франшиза',
    ];

    /** Раздел меню → модуль (null = всегда включён). */
    private const SECTION_MODULE = [
        'visits' => 'access',
        'leads' => 'leads',
        'deals' => 'deals',
        'tasks' => 'tasks',
        'trainers' => 'trainers',
        'analytics' => 'analytics',
        'promocodes' => 'promotions',
        'promotions' => 'promotions',
        'cashdesk' => 'cashdesk',
        'warehouse' => 'warehouse',
        'selfservice' => 'selfservice',
        'messengers' => 'messengers',
        'calls' => 'calls',
        'franchise' => 'franchise',
        'mobileapps' => 'access',
    ];

    public function __construct(
        private readonly ClubSettingsStore $settings,
    ) {
    }

    /** @return list<string> */
    public function enabledKeys(): array
    {
        $raw = $this->settings->get('enabled_modules');
        if ($raw === null || $raw === '') {
            return $this->defaultEnabledKeys();
        }

        $decoded = json_decode($raw, true);
        if (!\is_array($decoded)) {
            return $this->defaultEnabledKeys();
        }

        return array_values(array_intersect(array_keys(self::OPTIONAL), $decoded));
    }

    /** @return list<string> */
    public function defaultEnabledKeys(): array
    {
        return array_keys(self::OPTIONAL);
    }

    public function isEnabled(string $moduleKey): bool
    {
        return \in_array($moduleKey, $this->enabledKeys(), true);
    }

    public function isSectionEnabled(string $sectionKey): bool
    {
        $module = self::SECTION_MODULE[$sectionKey] ?? null;
        if ($module === null) {
            return true;
        }

        return $this->isEnabled($module);
    }

    /** @param list<string> $keys */
    public function saveEnabledKeys(array $keys): void
    {
        $filtered = array_values(array_intersect(array_keys(self::OPTIONAL), $keys));
        $this->settings->set('enabled_modules', json_encode($filtered, JSON_UNESCAPED_UNICODE));
        $this->settings->set('perco_enabled', \in_array('access', $filtered, true) ? '1' : '0');
    }
}
