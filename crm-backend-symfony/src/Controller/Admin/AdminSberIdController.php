<?php

namespace App\Controller\Admin;

use App\Entity\User;
use App\Service\Admin\SberIdOAuthService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;

#[Route('/admin')]
final class AdminSberIdController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly SberIdOAuthService $sberId,
        private readonly string $sberRedirectUri,
    ) {
    }

    #[Route('/clients/{id}/verify/sber-id', name: 'admin_sber_id_start', methods: ['GET'])]
    public function start(int $id, Request $request): Response
    {
        if (!$this->sberId->isConfigured() || trim($this->sberRedirectUri) === '') {
            $this->addFlash('warning', 'Сбер ID не настроен: задайте SBER_ID_CLIENT_ID, SBER_ID_CLIENT_SECRET и SBER_ID_REDIRECT_URI в окружении.');
            return $this->redirectToRoute('admin_client_show', ['id' => $id]);
        }

        $client = $this->em->getRepository(User::class)->find($id);
        if (!$client) {
            throw $this->createNotFoundException();
        }

        $session = $request->getSession();
        $state = bin2hex(random_bytes(16));
        $nonce = bin2hex(random_bytes(16));
        $session->set('sber_id_oauth', [
            'state' => $state,
            'nonce' => $nonce,
            'client_id' => $id,
        ]);

        $redirect = $this->buildCallbackAbsoluteUri($request);
        $url = $this->sberId->buildAuthorizeUrl($redirect, $state, $nonce);

        return $this->redirect($url);
    }

    #[Route('/oauth/sber-id/callback', name: 'admin_sber_id_callback', methods: ['GET'])]
    public function callback(Request $request): Response
    {
        $session = $request->getSession();
        $saved = $session->get('sber_id_oauth');
        $session->remove('sber_id_oauth');

        $state = (string) $request->query->get('state', '');
        if (!is_array($saved) || $state === '' || !hash_equals((string) ($saved['state'] ?? ''), $state)) {
            $this->addFlash('danger', 'Сбер ID: неверный state сессии.');
            return $this->redirectToRoute('admin_dashboard');
        }

        $error = $request->query->get('error');
        if ($error) {
            $this->addFlash('danger', 'Сбер ID: ' . $error);
            return $this->redirectToRoute('admin_client_show', ['id' => (int) ($saved['client_id'] ?? 0)]);
        }

        $code = $request->query->get('code');
        if (!is_string($code) || $code === '') {
            $this->addFlash('danger', 'Сбер ID: нет кода авторизации.');
            return $this->redirectToRoute('admin_client_show', ['id' => (int) ($saved['client_id'] ?? 0)]);
        }

        $clientUserId = (int) ($saved['client_id'] ?? 0);
        $client = $this->em->getRepository(User::class)->find($clientUserId);
        if (!$client) {
            throw $this->createNotFoundException();
        }

        $redirect = $this->buildCallbackAbsoluteUri($request);

        try {
            $tokens = $this->sberId->exchangeAuthorizationCode($code, $redirect);
        } catch (\Throwable $e) {
            $this->addFlash('danger', 'Сбер ID: не удалось обменять код — ' . $e->getMessage());
            return $this->redirectToRoute('admin_client_show', ['id' => $clientUserId]);
        }

        $idToken = $tokens['id_token'] ?? null;
        if (!is_string($idToken) || $idToken === '') {
            $this->addFlash('danger', 'Сбер ID: ответ без id_token.');
            return $this->redirectToRoute('admin_client_show', ['id' => $clientUserId]);
        }

        $claims = $this->sberId->decodeIdTokenPayload($idToken);
        $sub = isset($claims['sub']) && is_string($claims['sub']) ? $claims['sub'] : null;

        $client
            ->setVerified(true)
            ->setSberId($sub)
            ->setPassportVerificationStatus('verified')
            ->setPassportVerifiedAt(new \DateTimeImmutable())
            ->setPassportVerificationProvider('sber_id')
            ->setPassportVerificationSubject($sub)
            ->setPassportVerificationAuditJson((string) json_encode($claims, JSON_UNESCAPED_UNICODE));

        $this->em->flush();
        $this->addFlash('success', 'Клиент отмечен как верифицированный через Сбер ID.');

        return $this->redirectToRoute('admin_client_show', ['id' => $clientUserId]);
    }

    private function buildCallbackAbsoluteUri(Request $request): string
    {
        if (trim($this->sberRedirectUri) !== '') {
            return trim($this->sberRedirectUri);
        }

        return $this->generateUrl('admin_sber_id_callback', [], UrlGeneratorInterface::ABSOLUTE_URL);
    }
}
