<?php

namespace App\Controller\Api;

use App\Entity\User;
use App\Service\Admin\SberIdOAuthService;
use App\Service\Api\SberMobileAuthService;
use App\Service\CurrentUserResolver;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/auth/sber')]
class SberAuthController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
        private readonly SberMobileAuthService $mobileAuth,
        private readonly SberIdOAuthService $sberId,
    ) {}

    /** Ссылка на авторизацию Сбер ID для текущего пользователя приложения. */
    #[Route('/authorize-url', name: 'api_auth_sber_authorize_url', methods: ['GET'])]
    public function authorizeUrl(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }
        if (!$this->mobileAuth->isReady()) {
            return $this->json([
                'error' => 'sber_not_configured',
                'message' => 'Сбер ID для мобильного приложения не настроен на сервере (SBER_ID_*, SBER_ID_MOBILE_REDIRECT_URI).',
            ], 503);
        }
        try {
            $url = $this->mobileAuth->buildAuthorizeUrlForUser($user);
        } catch (\Throwable $e) {
            return $this->json(['error' => 'sber_url_failed', 'message' => $e->getMessage()], 500);
        }

        return $this->json(['authorize_url' => $url]);
    }

    /**
     * Redirect URI мобильного потока (зарегистрируйте тот же URL в кабинете Сбер ID).
     * После успеха пользователь может вернуться в приложение и снова нажать «Купить».
     */
    #[Route('/callback', name: 'api_auth_sber_callback', methods: ['GET'])]
    public function callback(Request $request): Response
    {
        $state = (string) $request->query->get('state', '');
        $userId = $this->mobileAuth->parseUserIdFromState($state);
        if ($userId === null) {
            return new Response(
                $this->htmlResult(false, 'Неверный или устаревший запрос. Откройте приложение и начните верификацию снова.'),
                400
            );
        }

        /** @var User|null $user */
        $user = $this->em->getRepository(User::class)->find($userId);
        if (!$user) {
            return new Response($this->htmlResult(false, 'Пользователь не найден.'), 404);
        }

        if ($request->query->get('error')) {
            return new Response(
                $this->htmlResult(false, 'Сбер ID: ' . (string) $request->query->get('error_description', $request->query->get('error'))),
                400
            );
        }

        $code = $request->query->get('code');
        if (!is_string($code) || $code === '') {
            return new Response($this->htmlResult(false, 'Нет кода авторизации.'), 400);
        }

        $redirect = $this->mobileAuth->getMobileRedirectUri();
        try {
            $tokens = $this->sberId->exchangeAuthorizationCode($code, $redirect);
        } catch (\Throwable $e) {
            return new Response($this->htmlResult(false, 'Не удалось завершить вход: ' . htmlspecialchars($e->getMessage(), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8')), 502);
        }

        $idToken = $tokens['id_token'] ?? null;
        if (!is_string($idToken) || $idToken === '') {
            return new Response($this->htmlResult(false, 'Ответ Сбер ID без id_token.'), 502);
        }

        $claims = $this->sberId->decodeIdTokenPayload($idToken);
        $sub = isset($claims['sub']) && is_string($claims['sub']) ? $claims['sub'] : null;

        $user
            ->setPassportVerificationStatus('verified')
            ->setPassportVerifiedAt(new \DateTimeImmutable())
            ->setPassportVerificationProvider('sber_id')
            ->setPassportVerificationSubject($sub)
            ->setPassportVerificationAuditJson((string) json_encode($claims, JSON_UNESCAPED_UNICODE));

        $this->em->flush();

        return new Response($this->htmlResult(true, 'Верификация успешна. Вернитесь в приложение и нажмите «Купить» ещё раз — затем откроется оплата (когда будет подключён эквайринг).'));
    }

    private function htmlResult(bool $ok, string $message): string
    {
        $title = $ok ? 'Готово' : 'Ошибка';
        $safe = htmlspecialchars($message, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');

        return <<<HTML
<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>{$title}</title>
<style>body{font-family:sans-serif;padding:24px;max-width:480px;margin:auto;line-height:1.5}</style></head>
<body><h1>{$title}</h1><p>{$safe}</p></body></html>
HTML;
    }
}
