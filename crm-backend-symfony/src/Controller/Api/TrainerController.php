<?php

namespace App\Controller\Api;

use App\Entity\Trainer;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/trainers')]
class TrainerController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {}

    #[Route('', name: 'api_trainers_list', methods: ['GET'])]
    public function list(): JsonResponse
    {
        $trainers = $this->em->getRepository(Trainer::class)->findAll();

        $data = array_map(static function (Trainer $t) {
            return [
                'id' => 'trainer-' . $t->getId(),
                'name' => $t->getName(),
                'photo_url' => $t->getPhotoUrl(),
                'specialization' => $t->getSpecialization(),
                'rating' => $t->getRating(),
                'description' => $t->getDescription(),
            ];
        }, $trainers);

        return $this->json($data);
    }

    #[Route('/{id}', name: 'api_trainers_show', methods: ['GET'])]
    public function show(string $id): JsonResponse
    {
        $numericId = str_starts_with($id, 'trainer-') ? (int) substr($id, 8) : (int) $id;

        /** @var Trainer|null $trainer */
        $trainer = $this->em->getRepository(Trainer::class)->find($numericId);

        if (!$trainer) {
            return $this->json(['error' => 'Not found'], 404);
        }

        return $this->json([
            'id' => 'trainer-' . $trainer->getId(),
            'name' => $trainer->getName(),
            'photo_url' => $trainer->getPhotoUrl(),
            'specialization' => $trainer->getSpecialization(),
            'rating' => $trainer->getRating(),
            'description' => $trainer->getDescription(),
        ]);
    }
}

