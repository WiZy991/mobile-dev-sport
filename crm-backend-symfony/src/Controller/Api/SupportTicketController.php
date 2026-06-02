<?php

namespace App\Controller\Api;

use App\Entity\SupportTicket;
use App\Service\CurrentUserResolver;
use App\Service\Support\SupportTicketStaffNotifier;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/support')]
class SupportTicketController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
        private readonly SupportTicketStaffNotifier $staffNotifier,
    ) {}

    #[Route('/tickets', name: 'api_support_ticket_create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        $data = json_decode($request->getContent(), true) ?? [];

        $subject = mb_substr(trim((string) ($data['subject'] ?? '')), 0, 200);
        $message = trim((string) ($data['message'] ?? ''));
        $category = (string) ($data['category'] ?? SupportTicket::CATEGORY_OTHER);
        $contactEmail = trim((string) ($data['contact_email'] ?? ''));

        if ($subject === '') {
            return $this->json(['error' => 'subject is required'], 400);
        }
        if (mb_strlen($message) < 5) {
            return $this->json(['error' => 'message must be at least 5 characters'], 400);
        }
        if (mb_strlen($message) > 8000) {
            return $this->json(['error' => 'message is too long'], 400);
        }
        if (!in_array($category, SupportTicket::allowedCategories(), true)) {
            $category = SupportTicket::CATEGORY_OTHER;
        }

        if ($user === null) {
            if ($contactEmail === '' || !filter_var($contactEmail, FILTER_VALIDATE_EMAIL)) {
                return $this->json(['error' => 'Valid contact_email is required when user is not identified'], 400);
            }
        } else {
            if ($contactEmail !== '' && !filter_var($contactEmail, FILTER_VALIDATE_EMAIL)) {
                return $this->json(['error' => 'Invalid contact_email'], 400);
            }
            if ($contactEmail === '') {
                $contactEmail = $user->getEmail();
            }
        }

        $ticket = (new SupportTicket())
            ->setUser($user)
            ->setSubject($subject)
            ->setMessage($message)
            ->setCategory($category)
            ->setContactEmail($contactEmail !== '' ? $contactEmail : null);

        $this->em->persist($ticket);
        $this->em->flush();

        $this->staffNotifier->notifyNewTicket($ticket);

        return $this->json([
            'success' => true,
            'id' => (string) $ticket->getId(),
        ], 201);
    }
}
