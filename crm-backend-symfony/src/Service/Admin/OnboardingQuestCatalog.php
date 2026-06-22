<?php

namespace App\Service\Admin;

/**
 * Каталог игрового онбординга «Залька» — единый источник уроков для CRM и будущих мобильных приложений.
 */
final class OnboardingQuestCatalog
{
    public function mascot(): array
    {
        return [
            'name' => 'Залька',
            'tagline' => 'Твой тренер по CRM',
            'description' => 'Помощник по обучению работе в CRM клуба «Доброзал».',
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
                    'summary' => 'Как устроено это обучение',
                    'icon' => '👋',
                    'steps' => [
                        [
                            'type' => 'story',
                            'mood' => 'happy',
                            'text' => 'Привет! Я Залька. Это обучение по CRM: 16 коротких уроков. В каждом — объяснение раздела и пара вопросов. Нажимай «Продолжить».',
                        ],
                        [
                            'type' => 'story',
                            'mood' => 'excited',
                            'text' => 'Темы: клиенты, лиды, абонементы, расписание, приложение, касса. После урока откроется следующий. Поехали!',
                        ],
                    ],
                ],
                [
                    'id' => 'dashboard',
                    'title' => 'Дашборд',
                    'summary' => 'Сводка на сегодня при входе в CRM',
                    'icon' => '📊',
                    'steps' => [
                        [
                            'type' => 'info',
                            'mood' => 'neutral',
                            'title' => 'Главный экран',
                            'text' => 'Дашборд открывается при входе в CRM. Там сводка на сегодня: посещения, записи, лиды и обращения из приложения.',
                        ],
                        [
                            'type' => 'quiz',
                            'mood' => 'thinking',
                            'question' => 'Где посмотреть сводку на сегодня?',
                            'options' => ['В разделе «Дашборд»', 'В настройках клуба', 'В складе'],
                            'correct' => 0,
                            'explain' => 'Дашборд — твоя стартовая точка каждой смены.',
                        ],
                    ],
                ],
                [
                    'id' => 'menu',
                    'title' => 'Меню CRM',
                    'summary' => 'Разделы слева и права по ролям',
                    'icon' => '🧭',
                    'steps' => [
                        [
                            'type' => 'info',
                            'mood' => 'neutral',
                            'title' => 'Боковое меню',
                            'text' => 'Слева — все разделы CRM. Ты видишь только то, что разрешено твоей роли: менеджер, кассир, тренер и т.д.',
                        ],
                        [
                            'type' => 'quiz',
                            'mood' => 'thinking',
                            'question' => 'Почему у коллег может быть другое меню?',
                            'options' => ['Разные роли — разный доступ', 'CRM сломалось', 'Меню обновляется раз в месяц'],
                            'correct' => 0,
                            'explain' => 'Администратор назначает роль — от неё зависит список разделов.',
                        ],
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
                    'summary' => 'Найти клиента по имени или телефону',
                    'icon' => '🔍',
                    'steps' => [
                        [
                            'type' => 'story',
                            'mood' => 'happy',
                            'text' => 'Клиент пришёл на ресепшен — найдём его за пару секунд!',
                        ],
                        [
                            'type' => 'info',
                            'mood' => 'neutral',
                            'title' => 'Как искать',
                            'text' => 'Поле поиска вверху слева или раздел «Клиенты». Ищи по имени, телефону или email.',
                        ],
                        [
                            'type' => 'quiz',
                            'mood' => 'thinking',
                            'question' => 'Клиент назвал только телефон. Где искать?',
                            'options' => ['Поиск в CRM по номеру', 'Только в Excel', 'В разделе «Склад»'],
                            'correct' => 0,
                            'explain' => 'Телефон — главный идентификатор для входа в приложение.',
                        ],
                    ],
                ],
                [
                    'id' => 'client-card',
                    'title' => 'Карточка',
                    'summary' => 'Абонементы, записи и заметки клиента',
                    'icon' => '📇',
                    'steps' => [
                        [
                            'type' => 'info',
                            'mood' => 'neutral',
                            'title' => 'Карточка клиента',
                            'text' => 'Внутри: контакты, абонементы, записи, посещения, заметки и теги. Всё в одном месте.',
                        ],
                        [
                            'type' => 'quiz',
                            'mood' => 'thinking',
                            'question' => 'Где выдать новый абонемент?',
                            'options' => ['Из карточки клиента', 'Только из приложения клиента', 'Из раздела «Документы»'],
                            'correct' => 0,
                            'explain' => 'В карточке клиента — вкладка абонементов и кнопка выдачи.',
                        ],
                    ],
                ],
                [
                    'id' => 'new-client',
                    'title' => 'Новый клиент',
                    'summary' => 'Регистрация на ресепшене',
                    'icon' => '➕',
                    'steps' => [
                        [
                            'type' => 'story',
                            'mood' => 'excited',
                            'text' => 'Первый визит? Создай клиента кнопкой «Новый клиент» — укажи имя и телефон обязательно!',
                        ],
                        [
                            'type' => 'quiz',
                            'mood' => 'thinking',
                            'question' => 'Что важнее всего при регистрации?',
                            'options' => ['Правильный номер телефона', 'Цвет кроссовок', 'Любимый тренер'],
                            'correct' => 0,
                            'explain' => 'По телефону клиент войдёт в приложение и получит QR для входа.',
                        ],
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
                    'summary' => 'Заявки с сайта и приложения',
                    'icon' => '🎯',
                    'steps' => [
                        [
                            'type' => 'story',
                            'mood' => 'happy',
                            'text' => 'Лид — человек, который ещё не купил абонемент, но заинтересовался клубом.',
                        ],
                        [
                            'type' => 'info',
                            'mood' => 'neutral',
                            'title' => 'Откуда приходят',
                            'text' => 'С сайта, соцсетей, гостевого пропуска в приложении, звонка или вручную с ресепшена.',
                        ],
                        [
                            'type' => 'quiz',
                            'mood' => 'thinking',
                            'question' => 'Заявка с сайта куда попадает?',
                            'options' => ['В раздел «Лиды»', 'В склад', 'В расписание'],
                            'correct' => 0,
                            'explain' => 'Лиды собираются автоматически — проверяй раздел каждую смену.',
                        ],
                    ],
                ],
                [
                    'id' => 'funnel',
                    'title' => 'Воронка',
                    'summary' => 'Этапы от заявки до клиента',
                    'icon' => '📈',
                    'steps' => [
                        [
                            'type' => 'info',
                            'mood' => 'neutral',
                            'title' => 'Этапы воронки',
                            'text' => 'Новый → Пробное → Пришёл → Стал клиентом. Переводи лида по этапам по мере работы.',
                        ],
                        [
                            'type' => 'quiz',
                            'mood' => 'thinking',
                            'question' => 'Лид пришёл на пробную тренировку. Какой этап?',
                            'options' => ['Пробное / Пришёл', 'Неактивен', 'Склад'],
                            'correct' => 0,
                            'explain' => 'Обновляй статус — так вся команда видит прогресс.',
                        ],
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
                    'summary' => 'Прайс-лист абонементов',
                    'icon' => '💳',
                    'steps' => [
                        [
                            'type' => 'info',
                            'mood' => 'neutral',
                            'title' => 'Тарифные планы',
                            'text' => 'Стандартные тарифы (1, 3, 6, 12 мес.) добавляются из каталога. Цену можно менять в CRM.',
                        ],
                        [
                            'type' => 'quiz',
                            'mood' => 'thinking',
                            'question' => 'Где клиент видит тарифы?',
                            'options' => ['В мобильном приложении и при выдаче в CRM', 'Только на бумажке', 'В разделе «Звонки»'],
                            'correct' => 0,
                            'explain' => 'Тарифы в CRM = тарифы в приложении.',
                        ],
                    ],
                ],
                [
                    'id' => 'freeze',
                    'title' => 'Заморозка',
                    'summary' => 'Сколько дней можно заморозить',
                    'icon' => '❄️',
                    'steps' => [
                        [
                            'type' => 'info',
                            'mood' => 'neutral',
                            'title' => 'Заморозка абонемента',
                            'text' => 'Клиент может заморозить в приложении или попросить на ресепшене. Дни заморозки зависят от срока: 3 мес. — 14 дн., 6 мес. — 20 дн., 12 мес. — 30 дн.',
                        ],
                        [
                            'type' => 'quiz',
                            'mood' => 'thinking',
                            'question' => 'Абонемент на 12 месяцев — сколько дней заморозки?',
                            'options' => ['30 дней', '0 дней', '7 дней'],
                            'correct' => 0,
                            'explain' => 'При заморозке дата окончания сдвигается на срок заморозки.',
                        ],
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
                    'summary' => 'Групповые и персональные слоты',
                    'icon' => '🏋️',
                    'steps' => [
                        [
                            'type' => 'info',
                            'mood' => 'neutral',
                            'title' => 'Создание занятия',
                            'text' => 'Групповые — в общем расписании приложения. Персональные — в разделе индивидуальных тренировок.',
                        ],
                        [
                            'type' => 'quiz',
                            'mood' => 'thinking',
                            'question' => 'Клиент хочет персональную тренировку. Какой тип слота?',
                            'options' => ['Персональная', 'Групповая', 'Доп. услуга'],
                            'correct' => 0,
                            'explain' => 'Тип слота определяет, где клиент увидит запись в приложении.',
                        ],
                    ],
                ],
                [
                    'id' => 'bookings',
                    'title' => 'Записи',
                    'summary' => 'Кто записан на тренировку',
                    'icon' => '📅',
                    'steps' => [
                        [
                            'type' => 'story',
                            'mood' => 'happy',
                            'text' => 'Записи смотри в разделе «Записи на тренировки» или в карточке клиента. Отменить можно и ты, и клиент из приложения.',
                        ],
                        [
                            'type' => 'quiz',
                            'mood' => 'thinking',
                            'question' => 'Где отменить запись клиента?',
                            'options' => ['«Записи на тренировки» или карточка клиента', 'Только в кассе', 'Нигде — только клиент'],
                            'correct' => 0,
                            'explain' => 'Администратор и тренер могут управлять записями в CRM.',
                        ],
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
                    'summary' => 'Пропуск клиента в зал',
                    'icon' => '📱',
                    'steps' => [
                        [
                            'type' => 'info',
                            'mood' => 'neutral',
                            'title' => 'QR-код',
                            'text' => 'Клиент открывает QR в приложении «Доброзал». Турникет сканирует код и проверяет абонемент.',
                        ],
                        [
                            'type' => 'quiz',
                            'mood' => 'thinking',
                            'question' => 'Вход отклонён. Что проверить первым?',
                            'options' => ['Активен ли абонемент', 'Цвет футболки', 'Расписание на завтра'],
                            'correct' => 0,
                            'explain' => 'Смотри карточку клиента и логи в «Самообслуживание».',
                        ],
                    ],
                ],
                [
                    'id' => 'app-support',
                    'title' => 'Поддержка',
                    'summary' => 'Сообщения из приложения',
                    'icon' => '💬',
                    'steps' => [
                        [
                            'type' => 'info',
                            'mood' => 'neutral',
                            'title' => 'Обращения',
                            'text' => 'Сообщения из приложения — в разделе «Обращения из приложения». Отвечай и меняй статус: новое → в работе → закрыто.',
                        ],
                        [
                            'type' => 'quiz',
                            'mood' => 'thinking',
                            'question' => 'Клиент написал в чат приложения. Куда зайти?',
                            'options' => ['Обращения из приложения', 'Склад', 'Промокоды'],
                            'correct' => 0,
                            'explain' => 'Линия поддержки следит за этим разделом.',
                        ],
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
                    'summary' => 'Продажи и операции за смену',
                    'icon' => '💰',
                    'steps' => [
                        [
                            'type' => 'info',
                            'mood' => 'neutral',
                            'title' => 'Продажи и касса',
                            'text' => 'Продажи — оформление покупок. Касса — операции за смену. Раздел «Финансы» — для отчётов (роль «Финансы»).',
                        ],
                    ],
                ],
                [
                    'id' => 'graduate',
                    'title' => 'Итог',
                    'summary' => 'Завершение обучения',
                    'icon' => '🏆',
                    'steps' => [
                        [
                            'type' => 'story',
                            'mood' => 'celebrate',
                            'text' => 'Ты прошёл весь путь! Теперь ты знаешь CRM «Доброзал» от А до Я. Я горжусь тобой!',
                        ],
                        [
                            'type' => 'checkpoint',
                            'mood' => 'celebrate',
                            'title' => 'Обучение пройдено!',
                            'text' => 'Справочник с подробностями — во вкладке «Справочник». Удачной смены!',
                        ],
                    ],
                ],
            ],
        ];
    }
}
