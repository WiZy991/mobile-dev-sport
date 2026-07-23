<?php

namespace App\Controller;

use App\Entity\Payment;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

class PaymentRedirectController extends AbstractController
{
    public function __construct(
        private readonly string $appBridgeUri,
        private readonly string $staffAppBridgeUri,
        private readonly EntityManagerInterface $em,
    ) {}

    #[Route('/payment/success', name: 'payment_success_redirect', methods: ['GET'])]
    public function success(Request $request): Response
    {
        return $this->bridgeResponse($request, 'success');
    }

    #[Route('/payment/fail', name: 'payment_fail_redirect', methods: ['GET'])]
    public function fail(Request $request): Response
    {
        return $this->bridgeResponse($request, 'fail');
    }

    private function bridgeResponse(Request $request, string $result): Response
    {
        $paymentId = (int) $request->query->get('payment_id', 0);
        $orderId = (string) $request->query->get('orderId', $request->query->get('mdOrder', ''));

        $query = http_build_query(array_filter([
            'payment_id' => $paymentId > 0 ? $paymentId : null,
            'order_id' => $orderId !== '' ? $orderId : null,
            'result' => $result,
        ]));

        $bridgeBase = $this->resolveBridgeUri($paymentId, $orderId);
        $deepLink = $bridgeBase . ($query !== '' ? '?' . $query : '');

        $html = <<<HTML
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Возврат в приложение</title>
    <style>
        body { font-family: system-ui, sans-serif; text-align: center; padding: 2rem; }
        a { color: #e85d04; font-size: 1.1rem; }
    </style>
    <script>
        window.location.replace({$this->jsonEscape($deepLink)});
    </script>
</head>
<body>
    <p>Перенаправление в приложение…</p>
    <p><a href="{$this->htmlEscape($deepLink)}">Открыть приложение</a></p>
</body>
</html>
HTML;

        return new Response($html, Response::HTTP_OK, ['Content-Type' => 'text/html; charset=UTF-8']);
    }

    private function resolveBridgeUri(int $paymentId, string $orderId): string
    {
        $payment = null;
        if ($paymentId > 0) {
            $payment = $this->em->getRepository(Payment::class)->find($paymentId);
        }
        if ($payment === null && $orderId !== '') {
            $payment = $this->em->getRepository(Payment::class)->findOneBy(['alfaOrderId' => $orderId])
                ?? $this->em->getRepository(Payment::class)->findOneBy(['orderNumber' => $orderId]);
        }

        if ($payment instanceof Payment && $payment->getType() === Payment::TYPE_TRAINER_RENTAL) {
            return $this->staffAppBridgeUri !== '' ? $this->staffAppBridgeUri : 'staffapp://payment/callback';
        }

        return $this->appBridgeUri;
    }

    private function jsonEscape(string $value): string
    {
        return json_encode($value, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    }

    private function htmlEscape(string $value): string
    {
        return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    }
}
