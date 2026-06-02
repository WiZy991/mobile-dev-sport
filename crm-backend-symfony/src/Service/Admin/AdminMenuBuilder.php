<?php

namespace App\Service\Admin;

use App\Entity\StaffUser;
use Symfony\Component\Security\Core\User\UserInterface;

/**
 * Меню и доступ к разделам по ролям персонала.
 */
final class AdminMenuBuilder
{
    /** @var array<string, string> ключ section => подпись */
    private const FULL_MENU = [
        'dashboard' => 'Дашборд',
        'tasks' => 'Задачи',
        'clients' => 'Клиенты',
        'schedule' => 'Расписание',
        'bookings' => 'Записи на тренировки',
        'subscriptions' => 'Абонементы',
        'visits' => 'Посещения',
        'sales' => 'Продажи',
        'services' => 'Услуги',
        'leads' => 'Лиды',
        'deals' => 'Сделки',
        'comments' => 'Комментарии',
        'messengers' => 'Мессенджеры',
        'calls' => 'Звонки',
        'selfservice' => 'Самообслуживание',
        'cashdesk' => 'Касса',
        'warehouse' => 'Склад',
        'analytics' => 'Аналитика',
        'finance' => 'Финансы',
        'mobileapps' => 'Мобильные приложения',
        'app_support' => 'Обращения из приложения',
        'trainers' => 'Тренеры',
        'crm_staff' => 'Персонал CRM',
        'franchise' => 'Франшиза',
        'documents' => 'Документы',
        'promocodes' => 'Промокоды',
        'promotions' => 'Акции',
        'tags' => 'Теги',
        'settings' => 'Настройки',
    ];

    /**
     * null = все разделы из FULL_MENU.
     * @var array<string, list<string>|null>
     */
    private const ROLE_TO_SECTIONS = [
        'ROLE_SUPER_ADMIN' => null,
        'ROLE_ADMIN' => null,
        /** Операционный блок: лиды, записи, документы, тренеры зала и т.д. */
        'ROLE_MANAGER' => [
            'dashboard', 'tasks', 'clients', 'schedule', 'bookings', 'subscriptions', 'visits',
            'leads', 'deals', 'comments', 'services', 'mobileapps', 'app_support', 'documents', 'tags', 'settings', 'selfservice', 'promotions',
            'trainers', 'analytics',
        ],
        /** Касса, склад, отчёты по деньгам */
        'ROLE_FINANCE' => [
            'dashboard', 'sales', 'cashdesk', 'warehouse', 'finance', 'analytics',
            'subscriptions', 'clients', 'promocodes', 'promotions', 'visits', 'services', 'app_support',
        ],
        /** Тренер зала: расписание (создание/перенос/удаление), записи, абонементы, клиенты, своя карточка в «Тренеры». */
        'ROLE_TRAINER' => [
            'dashboard', 'schedule', 'bookings', 'subscriptions', 'clients', 'visits', 'trainers',
        ],
        /** Широкий просмотр без изменений (POST блокируется подписчиком). */
        'ROLE_VIEWER' => [
            'dashboard', 'clients', 'schedule', 'bookings', 'subscriptions', 'visits', 'analytics', 'app_support',
        ],
        /** Линия поддержки: обращения и клиенты. */
        'ROLE_SUPPORT' => [
            'dashboard', 'app_support', 'clients', 'comments',
        ],
    ];

    private const MUTATING_ROLES = [
        'ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_FINANCE',
    ];

    /** POST/PUT разрешённые тренеру (остальная CRM — только у {@see canMutateAdmin()}). */
    private const TRAINER_WRITE_ROUTES = [
        'admin_training_new',
        'admin_training_update',
        'admin_training_delete',
        'admin_booking_cancel',
        'admin_trainer_update',
    ];

    /** @return list<string> */
    public function supportedRoles(): array
    {
        return array_values(array_keys(self::ROLE_TO_SECTIONS));
    }

    /** Не только GET: создание клиентов, продаж, правки и т.д. */
    public function canMutateAdmin(StaffUser $user): bool
    {
        return (bool) array_intersect($user->getRoles(), self::MUTATING_ROLES);
    }

    public function hasTrainerRole(StaffUser $user): bool
    {
        return in_array('ROLE_TRAINER', $user->getRoles(), true);
    }

    public function hasSupportRole(StaffUser $user): bool
    {
        return in_array('ROLE_SUPPORT', $user->getRoles(), true);
    }

    public function canUpdateSupportTicket(StaffUser $user): bool
    {
        return $this->canMutateAdmin($user) || $this->hasSupportRole($user);
    }

    public function isTrainerWriteRoute(?string $route, string $method): bool
    {
        if ($route === null || $method !== 'POST') {
            return false;
        }
        return in_array($route, self::TRAINER_WRITE_ROUTES, true);
    }

    /** Полные права на запись или тренер с разрешённым маршрутом. */
    public function canWriteCurrentAdminOrTrainer(StaffUser $user, ?string $route, string $method): bool
    {
        if ($this->canMutateAdmin($user)) {
            return true;
        }
        if (!$this->hasTrainerRole($user)) {
            return false;
        }
        return $this->isTrainerWriteRoute($route, $method);
    }

    /** @return array<string, string> */
    public function buildFor(?UserInterface $user): array
    {
        if (!$user instanceof StaffUser) {
            return [];
        }
        if (!$user->isActive()) {
            return [];
        }

        $allowed = $this->allowedSections($user);
        if ($allowed === []) {
            return [];
        }

        $menu = [];
        foreach (self::FULL_MENU as $key => $label) {
            if (!in_array($key, $allowed, true)) {
                continue;
            }
            if ($key === 'crm_staff' && !array_intersect($user->getRoles(), ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN'])) {
                continue;
            }
            if ($key === 'franchise' && !array_intersect($user->getRoles(), ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN'])) {
                continue;
            }
            $menu[$key] = $label;
        }

        return $menu;
    }

    /** @return list<string> */
    public function allowedSections(StaffUser $user): array
    {
        $union = [];
        foreach ($user->getRoles() as $role) {
            if ($role === 'ROLE_STAFF') {
                continue;
            }
            if (!array_key_exists($role, self::ROLE_TO_SECTIONS)) {
                continue;
            }
            $sections = self::ROLE_TO_SECTIONS[$role];
            if ($sections === null) {
                return array_keys(self::FULL_MENU);
            }
            $union = array_values(array_unique(array_merge($union, $sections)));
        }

        if ($union === []) {
            return self::ROLE_TO_SECTIONS['ROLE_VIEWER'];
        }

        return $union;
    }

    public function isSectionAllowed(?UserInterface $user, string $section): bool
    {
        if (!$user instanceof StaffUser || !$user->isActive()) {
            return false;
        }
        if (!array_key_exists($section, self::FULL_MENU)) {
            return false;
        }
        $roles = $user->getRoles();
        if ($section === 'crm_staff' && !array_intersect($roles, ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN'])) {
            return false;
        }
        if ($section === 'franchise' && !array_intersect($roles, ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN'])) {
            return false;
        }
        if (array_intersect($roles, ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN'])) {
            return true;
        }
        $allowed = $this->allowedSections($user);
        if ($allowed === []) {
            return false;
        }
        return in_array($section, $allowed, true);
    }

    /** @param list<string> $adminSections @return list<string> */
    public function buildMobileAppSections(array $adminSections): array
    {
        $map = [
            'dashboard' => 'dashboard',
            'tasks' => 'tasks',
            'clients' => 'clients',
            'schedule' => 'schedule',
            'bookings' => 'bookings',
            'subscriptions' => 'subscriptions',
            'visits' => 'visits',
            'analytics' => 'analytics',
            'finance' => 'finance',
            'app_support' => 'app_support',
        ];

        $result = ['home', 'profile'];
        foreach ($adminSections as $section) {
            if (isset($map[$section])) {
                $result[] = $map[$section];
            }
        }
        if ($adminSections !== []) {
            $result[] = 'admin';
        }

        return array_values(array_unique($result));
    }
}
