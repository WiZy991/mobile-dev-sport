<?php

declare(strict_types=1);

namespace App\Controller\Api;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

/** Тексты правовых документов для мобильного приложения (без вёрстки сайта). */
#[Route('/api/v1/legal')]
final class LegalApiController extends AbstractController
{
    /** @var array<string, string> slug → имя .txt в templates/legal/content/ */
    private const TEXT_FILES = [
        'license_agreement' => 'offer.txt',
        'privacy' => 'privacy-policy.txt',
        'client-agreement' => 'client-agreement.txt',
        'trainer-agreement' => 'trainer-agreement.txt',
        'personal-data-consent' => 'personal-data-consent.txt',
    ];

    private const TITLES = [
        'license_agreement' => 'Договор-оферта',
        'privacy' => 'Политика конфиденциальности',
        'client-agreement' => 'Договор с клиентом',
        'trainer-agreement' => 'Договор с тренером',
        'personal-data-consent' => 'Согласие на обработку персональных данных',
        'requisites' => 'Реквизиты',
    ];

    #[Route('/{slug}', name: 'api_legal_document', methods: ['GET'])]
    public function document(string $slug): JsonResponse
    {
        if ($slug === 'requisites') {
            return $this->json([
                'title' => self::TITLES['requisites'],
                'fields' => [
                    ['label' => 'Полное наименование', 'value' => 'Индивидуальный предприниматель Мацкова Александра Сергеевна'],
                    ['label' => 'Юридический адрес', 'value' => '692768, Приморский край, Надеждинский район, п. Зима южная, ул. Терентьева, д. 1/2'],
                    ['label' => 'Почтовый адрес', 'value' => '690014, г. Владивосток, Приморский край, ул. Толстого, д. 32а, офис 308'],
                    ['label' => 'ИНН', 'value' => '254009880989'],
                    ['label' => 'ОГРНИП', 'value' => '323253600018625'],
                    ['label' => 'Расчётный счёт', 'value' => '40802810520020005616'],
                    ['label' => 'Банк', 'value' => 'АО «Альфа-Банк», г. Москва'],
                    ['label' => 'БИК', 'value' => '040813770'],
                    ['label' => 'Корреспондентский счёт', 'value' => '30101810900000000770'],
                    ['label' => 'Телефон', 'value' => '+7 (902) 483-42-69'],
                    ['label' => 'Электронная почта', 'value' => 'avto24vl@mail.ru'],
                ],
            ]);
        }

        if (!isset(self::TEXT_FILES[$slug], self::TITLES[$slug])) {
            return $this->json(['error' => 'Документ не найден'], Response::HTTP_NOT_FOUND);
        }

        $path = $this->getParameter('kernel.project_dir')
            . '/templates/legal/content/'
            . self::TEXT_FILES[$slug];

        if (!is_readable($path)) {
            return $this->json(['error' => 'Текст документа недоступен'], Response::HTTP_NOT_FOUND);
        }

        $body = trim((string) file_get_contents($path));
        if ($body === '') {
            return $this->json(['error' => 'Текст документа пуст'], Response::HTTP_NOT_FOUND);
        }

        return $this->json([
            'title' => self::TITLES[$slug],
            'body' => $body,
        ]);
    }
}
