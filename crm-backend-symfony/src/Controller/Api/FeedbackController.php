<?php

namespace App\Controller\Api;

use App\Entity\Feedback;
use App\Service\CurrentUserResolver;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/feedback')]
class FeedbackController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
    ) {}

    #[Route('', name: 'api_feedback_submit', methods: ['POST'])]
    public function submit(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);

        $data = json_decode($request->getContent(), true) ?? [];
        $rating = max(1, min(5, (int) ($data['rating'] ?? 0)));
        $comment = trim((string) ($data['comment'] ?? ''));
        $type = (string) ($data['type'] ?? 'general');
        if (!in_array($type, [Feedback::TYPE_GENERAL, Feedback::TYPE_TRAINING], true)) {
            $type = Feedback::TYPE_GENERAL;
        }
        $referenceId = !empty($data['reference_id']) ? (string) $data['reference_id'] : null;

        if ($rating < 1 || $rating > 5) {
            return $this->json(['error' => 'Rating must be 1-5'], 400);
        }

        $feedback = (new Feedback())
            ->setUser($user)
            ->setType($type)
            ->setRating($rating)
            ->setComment($comment !== '' ? $comment : null)
            ->setReferenceId($referenceId);

        $this->em->persist($feedback);
        $this->em->flush();

        return $this->json([
            'success' => true,
            'id' => 'feedback-' . $feedback->getId(),
        ], 201);
    }
}
