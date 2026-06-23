<?php

namespace App\Service\Admin;

/**
 * Каталог игрового онбординга «Зайка» — единый источник уроков для CRM и будущих мобильных приложений.
 */
final class OnboardingQuestCatalog
{
    public function mascot(): array
    {
        return [
            'name' => 'Зайка',
            'tagline' => 'Твой гид по CRM',
            'description' => 'Зайка в наушниках — проводит по CRM и подсказывает на каждом шаге.',
        ];
    }

    /** @return array{units: list<array<string, mixed>>, totalLessons: int} */
    public function export(): array
    {
        $units = [
            $this->unitStart(),
            $this->unitClients(),
            $this->unitLeads(),
            $this->unitSubscriptions(),
            $this->unitSchedule(),
            $this->unitMobile(),
            $this->unitFinish(),
        ];

        $total = 0;
        foreach ($units as $unit) {
            $total += count($unit['lessons']);
        }

        return [
            'mascot' => $this->mascot(),
            'units' => $units,
            'totalLessons' => $total,
            'heartsMax' => 5,
            'xpPerLesson' => 15,
            'xpBonusPerfect' => 5,
        ];
    }

    /** @return array<string, mixed> */
    private function tour(string $section, string $target, string $text, string $mood = 'happy'): array
    {
        return [
            'type' => 'tour',
            'section' => $section,
            'target' => $target,
            'text' => $text,
            'mood' => $mood,
        ];
    }

    /** @return array<string, mixed> */
    private function tourClick(string $section, string $target, string $text, string $mood = 'excited'): array
    {
        return [
            'type' => 'tour_click',
            'section' => $section,
            'target' => $target,
            'text' => $text,
            'mood' => $mood,
        ];
    }

    /**
     * @param list<string> $options
     *
     * @return array<string, mixed>
     */
    private function quiz(string $section, string $target, string $question, array $options, int $correct, string $explain): array
    {
        return [
            'type' => 'quiz',
            'section' => $section,
            'target' => $target,
            'question' => $question,
            'options' => $options,
            'correct' => $correct,
            'explain' => $explain,
            'mood' => 'thinking',
        ];
    }

    /** @return array<string, mixed> */
    private function unitStart(): array
    {
        return [
            'id' => 'start',
            'title' => 'Старт',
            'icon' => 'bi-flag',
            'color' => '#ff5b2e',
            'lessons' => [
                [
                    'id' => 'welcome',
                    'title' => 'Знакомство',
                    'summary' => 'Старт: подсветка меню CRM',
                    'icon' => '👋',
                    'steps' => [
                        $this->tour(
                            'onboarding',
                            '[data-dz-tour="lesson-map"]',
                            'Привет! Я Зайка. Сейчас пойдём по настоящим разделам CRM — я подсвечу кнопки и поля.',
                            'happy',
                        ),
                        $this->tourClick(
                            'onboarding',
                            '[data-dz-nav="dashboard"]',
                            'Нажми «Дашборд» в меню слева — начнём с главного экрана!',
                            'excited',
                        ),
                    ],
                ],
                [
                    'id' => 'dashboard',
                    'title' => 'Дашборд',
                    'summary' => 'Сводка на сегодня',
                    'icon' => '📊',
                    'steps' => [
                        $this->tour(
                            'dashboard',
                            '[data-dz-tour="dashboard-header"]',
                            'Вот дашборд — он открывается при входе. Здесь сводка на сегодня.',
                            'neutral',
                        ),
                        $this->tour(
                            'dashboard',
                            '[data-dz-tour="dashboard-stats"]',
                            'Эти карточки — цифры смены: клиенты, посещения, записи, лиды.',
                            'happy',
                        ),
                        $this->quiz(
                            'dashboard',
                            '[data-dz-tour="dashboard-header"]',
                            'Где посмотреть сводку на сегодня?',
                            ['В разделе «Дашборд»', 'В настройках клуба', 'В складе'],
                            0,
                            'Дашборд — стартовая точка каждой смены.',
                        ),
                    ],
                ],
                [
                    'id' => 'menu',
                    'title' => 'Меню CRM',
                    'summary' => 'Разделы слева по ролям',
                    'icon' => '🧭',
                    'steps' => [
                        $this->tour(
                            'dashboard',
                            '.sidebar-nav',
                            'Слева — все разделы CRM. У каждой роли свой набор: менеджер, кассир, тренер…',
                            'neutral',
                        ),
                        $this->tourClick(
                            'dashboard',
                            '[data-dz-nav="clients"]',
                            'Нажми «Клиенты» — покажу, как искать человека на ресепшене!',
                            'excited',
                        ),
                    ],
                ],
            ],
        ];
    }

    /** @return array<string, mixed> */
    private function unitClients(): array
    {
        return [
            'id' => 'clients',
            'title' => 'Клиенты',
            'icon' => 'bi-people',
            'color' => '#3b9eff',
            'lessons' => [
                [
                    'id' => 'find-client',
                    'title' => 'Поиск',
                    'summary' => 'Поле поиска клиента',
                    'icon' => '🔍',
                    'steps' => [
                        $this->tour(
                            'clients',
                            '[data-dz-tour="clients-search"]',
                            'Клиент пришёл на ресепшен — ищи по имени, телефону или email в этом поле.',
                            'happy',
                        ),
                        $this->quiz(
                            'clients',
                            '[data-dz-tour="clients-search"]',
                            'Клиент назвал только телефон. Где искать?',
                            ['В поиске CRM по номеру', 'Только в Excel', 'В разделе «Склад»'],
                            0,
                            'Телефон — главный идентификатор для входа в приложение.',
                        ),
                    ],
                ],
                [
                    'id' => 'client-card',
                    'title' => 'Карточка',
                    'summary' => 'Список и профиль клиента',
                    'icon' => '📇',
                    'steps' => [
                        $this->tour(
                            'clients',
                            '[data-dz-tour="clients-table"]',
                            'Нажми на имя или ID — откроется карточка: абонементы, записи, заметки, всё в одном месте.',
                            'neutral',
                        ),
                        $this->quiz(
                            'clients',
                            '[data-dz-tour="clients-table"]',
                            'Где выдать новый абонемент?',
                            ['Из карточки клиента', 'Только из приложения клиента', 'Из раздела «Документы»'],
                            0,
                            'В карточке клиента — вкладка абонементов и кнопка выдачи.',
                        ),
                    ],
                ],
                [
                    'id' => 'new-client',
                    'title' => 'Новый клиент',
                    'summary' => 'Кнопка регистрации',
                    'icon' => '➕',
                    'steps' => [
                        $this->tour(
                            'clients',
                            '[data-dz-tour="clients-new"]',
                            'Первый визит? Жми «+ Новый клиент» — имя и телефон обязательны!',
                            'excited',
                        ),
                        $this->quiz(
                            'clients',
                            '[data-dz-tour="clients-new"]',
                            'Что важнее всего при регистрации?',
                            ['Правильный номер телефона', 'Цвет кроссовок', 'Любимый тренер'],
                            0,
                            'По телефону клиент войдёт в приложение и получит QR для входа.',
                        ),
                    ],
                ],
            ],
        ];
    }

    /** @return array<string, mixed> */
    private function unitLeads(): array
    {
        return [
            'id' => 'leads',
            'title' => 'Лиды',
            'icon' => 'bi-funnel',
            'color' => '#a855f7',
            'lessons' => [
                [
                    'id' => 'leads-intro',
                    'title' => 'Что такое лид',
                    'summary' => 'Раздел заявок',
                    'icon' => '🎯',
                    'steps' => [
                        $this->tourClick(
                            'clients',
                            '[data-dz-nav="leads"]',
                            'Заявки с сайта и приложения попадают в «Лиды». Нажми — откроем раздел!',
                            'excited',
                        ),
                        $this->tour(
                            'leads',
                            '[data-dz-tour="leads-header"]',
                            'Лид — человек, который ещё не купил абонемент, но заинтересовался клубом.',
                            'happy',
                        ),
                        $this->quiz(
                            'leads',
                            '[data-dz-tour="leads-header"]',
                            'Заявка с сайта куда попадает?',
                            ['В раздел «Лиды»', 'В склад', 'В расписание'],
                            0,
                            'Лиды собираются автоматически — проверяй раздел каждую смену.',
                        ),
                    ],
                ],
                [
                    'id' => 'funnel',
                    'title' => 'Воронка',
                    'summary' => 'Этапы сделки',
                    'icon' => '📈',
                    'steps' => [
                        $this->tour(
                            'leads',
                            '[data-dz-tour="leads-funnel"]',
                            'Воронка сверху: Новый → Пробное → Пришёл → Стал клиентом. Переводи лида по этапам.',
                            'neutral',
                        ),
                        $this->quiz(
                            'leads',
                            '[data-dz-tour="leads-funnel"]',
                            'Лид пришёл на пробную тренировку. Какой этап?',
                            ['Пробное / Пришёл', 'Неактивен', 'Склад'],
                            0,
                            'Обновляй статус — так вся команда видит прогресс.',
                        ),
                    ],
                ],
            ],
        ];
    }

    /** @return array<string, mixed> */
    private function unitSubscriptions(): array
    {
        return [
            'id' => 'subscriptions',
            'title' => 'Абонементы',
            'icon' => 'bi-card-checklist',
            'color' => '#14b88a',
            'lessons' => [
                [
                    'id' => 'plans',
                    'title' => 'Тарифы',
                    'summary' => 'Прайс абонементов',
                    'icon' => '💳',
                    'steps' => [
                        $this->tourClick(
                            'leads',
                            '[data-dz-nav="subscriptions"]',
                            'Абонементы — в этом разделе. Нажми «Абонементы» в меню!',
                            'excited',
                        ),
                        $this->tour(
                            'subscriptions',
                            '[data-dz-tour="subscriptions-plans"]',
                            'Здесь тарифные планы: 1, 3, 6, 12 месяцев. Цену можно менять в CRM.',
                            'neutral',
                        ),
                        $this->quiz(
                            'subscriptions',
                            '[data-dz-tour="subscriptions-plans"]',
                            'Где клиент видит тарифы?',
                            ['В мобильном приложении и при выдаче в CRM', 'Только на бумажке', 'В разделе «Звонки»'],
                            0,
                            'Тарифы в CRM = тарифы в приложении.',
                        ),
                    ],
                ],
                [
                    'id' => 'freeze',
                    'title' => 'Заморозка',
                    'summary' => 'Правила заморозки',
                    'icon' => '❄️',
                    'steps' => [
                        $this->tour(
                            'subscriptions',
                            '[data-dz-tour="subscriptions-list"]',
                            'Заморозка — в карточке абонемента. 3 мес. — 14 дн., 6 мес. — 20 дн., 12 мес. — 30 дн.',
                            'neutral',
                        ),
                        $this->quiz(
                            'subscriptions',
                            '[data-dz-tour="subscriptions-list"]',
                            'Абонемент на 12 месяцев — сколько дней заморозки?',
                            ['30 дней', '0 дней', '7 дней'],
                            0,
                            'При заморозке дата окончания сдвигается на срок заморозки.',
                        ),
                    ],
                ],
            ],
        ];
    }

    /** @return array<string, mixed> */
    private function unitSchedule(): array
    {
        return [
            'id' => 'schedule',
            'title' => 'Расписание',
            'icon' => 'bi-calendar3',
            'color' => '#ffcb45',
            'lessons' => [
                [
                    'id' => 'trainings',
                    'title' => 'Занятия',
                    'summary' => 'Календарь тренировок',
                    'icon' => '🏋️',
                    'steps' => [
                        $this->tourClick(
                            'subscriptions',
                            '[data-dz-nav="schedule"]',
                            'Расписание занятий — здесь. Нажми «Расписание»!',
                            'excited',
                        ),
                        $this->tour(
                            'schedule',
                            '[data-dz-tour="schedule-main"]',
                            'Групповые слоты — в общем расписании. Персональные — в индивидуальных тренировках.',
                            'neutral',
                        ),
                        $this->quiz(
                            'schedule',
                            '[data-dz-tour="schedule-main"]',
                            'Клиент хочет персональную тренировку. Какой тип слота?',
                            ['Персональная', 'Групповая', 'Доп. услуга'],
                            0,
                            'Тип слота определяет, где клиент увидит запись в приложении.',
                        ),
                    ],
                ],
                [
                    'id' => 'bookings',
                    'title' => 'Записи',
                    'summary' => 'Кто записан на занятие',
                    'icon' => '📅',
                    'steps' => [
                        $this->tourClick(
                            'schedule',
                            '[data-dz-nav="bookings"]',
                            'Записи на тренировки — отдельный раздел. Нажми!',
                            'excited',
                        ),
                        $this->tour(
                            'bookings',
                            '[data-dz-tour="bookings-main"]',
                            'Здесь видно, кто записан. Отменить можно и ты, и клиент из приложения.',
                            'happy',
                        ),
                        $this->quiz(
                            'bookings',
                            '[data-dz-tour="bookings-main"]',
                            'Где отменить запись клиента?',
                            ['«Записи на тренировки» или карточка клиента', 'Только в кассе', 'Нигде — только клиент'],
                            0,
                            'Администратор и тренер могут управлять записями в CRM.',
                        ),
                    ],
                ],
            ],
        ];
    }

    /** @return array<string, mixed> */
    private function unitMobile(): array
    {
        return [
            'id' => 'mobile',
            'title' => 'Приложение',
            'icon' => 'bi-phone',
            'color' => '#f472b6',
            'lessons' => [
                [
                    'id' => 'qr-entry',
                    'title' => 'Вход по QR',
                    'summary' => 'Пропуск в зал',
                    'icon' => '📱',
                    'steps' => [
                        $this->tourClick(
                            'bookings',
                            '[data-dz-nav="visits"]',
                            'Пропуска и посещения — в разделе «Посещения». Нажми!',
                            'excited',
                        ),
                        $this->tour(
                            'visits',
                            '[data-dz-tour="visits-main"]',
                            'Клиент показывает QR из приложения — турникет проверяет абонемент. Логи входа — здесь.',
                            'neutral',
                        ),
                        $this->quiz(
                            'visits',
                            '[data-dz-tour="visits-main"]',
                            'Вход отклонён. Что проверить первым?',
                            ['Активен ли абонемент', 'Цвет футболки', 'Расписание на завтра'],
                            0,
                            'Смотри карточку клиента и логи в «Самообслуживание».',
                        ),
                    ],
                ],
                [
                    'id' => 'app-support',
                    'title' => 'Поддержка',
                    'summary' => 'Чат из приложения',
                    'icon' => '💬',
                    'steps' => [
                        $this->tourClick(
                            'visits',
                            '[data-dz-nav="app_support"]',
                            'Сообщения из приложения — в «Обращения». Нажми!',
                            'excited',
                        ),
                        $this->tour(
                            'app_support',
                            '[data-dz-tour="app-support-main"]',
                            'Отвечай клиентам и меняй статус: новое → в работе → закрыто.',
                            'neutral',
                        ),
                        $this->quiz(
                            'app_support',
                            '[data-dz-tour="app-support-main"]',
                            'Клиент написал в чат приложения. Куда зайти?',
                            ['Обращения из приложения', 'Склад', 'Промокоды'],
                            0,
                            'Линия поддержки следит за этим разделом.',
                        ),
                    ],
                ],
            ],
        ];
    }

    /** @return array<string, mixed> */
    private function unitFinish(): array
    {
        return [
            'id' => 'finish',
            'title' => 'Финал',
            'icon' => 'bi-trophy',
            'color' => '#ffcb45',
            'lessons' => [
                [
                    'id' => 'cashdesk',
                    'title' => 'Касса',
                    'summary' => 'Продажи за смену',
                    'icon' => '💰',
                    'steps' => [
                        $this->tourClick(
                            'app_support',
                            '[data-dz-nav="sales"]',
                            'Продажи оформляются здесь. Нажми «Продажи»!',
                            'excited',
                        ),
                        $this->tour(
                            'sales',
                            '[data-dz-tour="sales-main"]',
                            'Продажи — покупки абонементов и услуг. Касса и финансы — для отчётов за смену.',
                            'neutral',
                        ),
                    ],
                ],
                [
                    'id' => 'graduate',
                    'title' => 'Итог',
                    'summary' => 'Обучение пройдено',
                    'icon' => '🏆',
                    'steps' => [
                        $this->tourClick(
                            'sales',
                            '[data-dz-nav="onboarding"]',
                            'Финиш! Нажми «Обучение» в меню — поздравлю тебя там!',
                            'celebrate',
                        ),
                        [
                            'type' => 'checkpoint',
                            'section' => 'onboarding',
                            'target' => '[data-dz-tour="lesson-map"]',
                            'text' => 'Ты прошёл все уроки! Справочник — во вкладке «Справочник». Удачной смены!',
                            'mood' => 'celebrate',
                        ],
                    ],
                ],
            ],
        ];
    }
}
