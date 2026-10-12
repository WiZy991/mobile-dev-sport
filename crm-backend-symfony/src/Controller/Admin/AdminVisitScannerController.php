<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Controller\Api\GatewayController;
use App\Entity\Club;
use App\Service\Admin\AdminMenuBuilder;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpKernel\HttpKernelInterface;

/**
 * Ручной сканер на ресепшене (Mertech 2310 P2D и любой USB-сканер в режиме клавиатуры):
 * сканер «печатает» QR из приложения в поле страницы + Enter → вход / выход.
 *
 * Логика прохода не дублируется: запрос уходит в тот же {@see GatewayController}
 * (абонемент, списание посещения, тренеры, гостевые), что и у турникета, с токеном шлюза клуба.
 * Дверь при этом не открывается — только фиксация в журнале.
 *
 * Маршруты — в config/routes/admin.yaml (до catch-all admin_section).
 */
final class AdminVisitScannerController extends AbstractController
{
    public const DEVICE_ID = 'crm-scanner';

    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly AdminMenuBuilder $adminMenuBuilder,
    ) {
    }

    public function page(): Response
    {
        return $this->render('admin/visits_scanner.html.twig', [
            'menu' => $this->adminMenuBuilder->buildFor($this->getUser()),
            'current' => 'visits',
            'clubs' => $this->em->getRepository(Club::class)->findBy([], ['name' => 'ASC']),
        ]);
    }

    public function scan(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true);
        $data = \is_array($data) ? $data : [];
        $qr = trim((string) ($data['qr'] ?? ''));
        $mode = (string) ($data['mode'] ?? 'auto');
        $clubId = (int) ($data['club_id'] ?? 0);

        if ($qr === '') {
            return $this->json(['access_granted' => false, 'reason' => 'empty', 'message' => 'Пустой код'], 400);
        }
        $club = $clubId > 0 ? $this->em->getRepository(Club::class)->find($clubId) : null;
        if (!$club instanceof Club) {
            return $this->json(['access_granted' => false, 'reason' => 'club_required', 'message' => 'Выберите клуб'], 400);
        }

        $token = $this->ensureGatewayToken($club);
        $isExit = $mode === 'exit';
        $payload = [
            'qr' => mb_substr($qr, 0, 255),
            'device_id' => self::DEVICE_ID,
        ];
        if (!$isExit) {
            // auto: повторный скан того, кто уже в зале, = выход; entry: повторный скан = отказ.
            $payload['allow_exit_toggle'] = $mode !== 'entry';
        }

        $sub = Request::create(
            $isExit ? '/api/v1/gateway/access/exit' : '/api/v1/gateway/access/entry',
            'POST',
            server: [
                'HTTP_AUTHORIZATION' => 'Bearer ' . $token,
                'CONTENT_TYPE' => 'application/json',
            ],
            content: json_encode($payload, \JSON_UNESCAPED_UNICODE),
        );
        $sub->attributes->set('_controller', GatewayController::class . '::' . ($isExit ? 'exit' : 'entry'));

        /** @var HttpKernelInterface $kernel */
        $kernel = $this->container->get('http_kernel');
        $response = $kernel->handle($sub, HttpKernelInterface::SUB_REQUEST, false);

        $body = json_decode((string) $response->getContent(), true);
        $body = \is_array($body) ? $body : [];
        // Шлюзу нужен open_device для PERCo; ресепшену — нет.
        unset($body['open_device'], $body['delta_ms'], $body['server_now_ms'], $body['qr_timestamp_ms'], $body['qr_time_segment']);
        if (($body['access_granted'] ?? false) !== true && empty($body['message'])) {
            $body['message'] = match ($body['reason'] ?? '') {
                'invalid_format' => 'Код не распознан. Проверьте раскладку (EN) и что сканируется QR из приложения',
                'user_not_found' => 'Клиент по коду не найден',
                'guest_pass_invalid' => 'Гостевой пропуск недействителен',
                default => 'Отказ: ' . ($body['reason'] ?? 'неизвестная ошибка'),
            };
        }

        return $this->json($body, $response->getStatusCode());
    }

    /** У клуба без ПК-шлюза токена может не быть — генерируем, существующий не трогаем. */
    private function ensureGatewayToken(Club $club): string
    {
        $token = (string) $club->getGatewayToken();
        if ($token !== '') {
            return $token;
        }
        $token = bin2hex(random_bytes(24));
        $club->setGatewayToken($token);
        $this->em->flush();

        return $token;
    }
}
