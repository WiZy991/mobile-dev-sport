<?php

namespace App\Controller\Api;

use App\Entity\Trainer;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/trainers')]
class TrainerController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {}

    #[Route('', name: 'api_trainers_list', methods: ['GET'])]
    public function list(Request $request): JsonResponse
    {
        $trainers = $this->em->getRepository(Trainer::class)->findAll();

        $data = array_map(function (Trainer $t) use ($request) {
            return $this->serialize($t, $request);
        }, $trainers);

        return $this->json($data);
    }

    #[Route('/{id}', name: 'api_trainers_show', methods: ['GET'])]
    public function show(string $id, Request $request): JsonResponse
    {
        $numericId = str_starts_with($id, 'trainer-') ? (int) substr($id, 8) : (int) $id;

        /** @var Trainer|null $trainer */
        $trainer = $this->em->getRepository(Trainer::class)->find($numericId);

        if (!$trainer) {
            return $this->json(['error' => 'Not found'], 404);
        }

        return $this->json($this->serialize($trainer, $request));
    }

    /** @return array<string, mixed> */
    private function serialize(Trainer $trainer, Request $request): array
    {
        return [
            'id' => 'trainer-' . $trainer->getId(),
            'name' => $trainer->getName(),
            'photo_url' => self::absolutePublicUrl($request, $trainer->getPhotoUrl()),
            'specialization' => $trainer->getSpecialization(),
            'phone' => $trainer->getPhone(),
            'rating' => $trainer->getRating() ?? 0.0,
            'description' => $trainer->getDescription(),
        ];
    }

    private static function absolutePublicUrl(Request $request, ?string $path): ?string
    {
        if ($path === null || trim($path) === '') {
            return null;
        }
        $path = trim($path);
        if (str_starts_with($path, 'http://') || str_starts_with($path, 'https://')) {
            return $path;
        }
        $base = $request->getSchemeAndHttpHost();

        return str_starts_with($path, '/') ? $base . $path : $base . '/' . $path;
    }
}
