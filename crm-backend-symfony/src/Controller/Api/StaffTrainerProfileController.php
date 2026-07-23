<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\StaffUser;
use App\Entity\Trainer;
use App\Service\CurrentStaffUserResolver;
use App\Service\Staff\StaffOnboardingService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\File\UploadedFile;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/staff/trainer-profile')]
final class StaffTrainerProfileController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentStaffUserResolver $staffResolver,
        private readonly StaffOnboardingService $onboarding,
    ) {
    }

    #[Route('', name: 'api_staff_trainer_profile_get', methods: ['GET'])]
    public function get(Request $request): JsonResponse
    {
        $staff = $this->requireTrainerStaff($request);
        if ($staff instanceof JsonResponse) {
            return $staff;
        }

        try {
            $trainer = $this->onboarding->ensureTrainerProfile($staff);
        } catch (\Throwable $e) {
            return $this->json(['error' => $e->getMessage(), 'code' => 'trainer_missing'], 400);
        }

        return $this->json($this->serialize($trainer, $request));
    }

    #[Route('', name: 'api_staff_trainer_profile_update', methods: ['PUT', 'PATCH'])]
    public function update(Request $request): JsonResponse
    {
        $staff = $this->requireTrainerStaff($request);
        if ($staff instanceof JsonResponse) {
            return $staff;
        }

        try {
            $trainer = $this->onboarding->ensureTrainerProfile($staff);
        } catch (\Throwable $e) {
            return $this->json(['error' => $e->getMessage(), 'code' => 'trainer_missing'], 400);
        }

        $data = json_decode($request->getContent(), true) ?? [];

        if (array_key_exists('name', $data)) {
            $name = trim((string) $data['name']);
            if ($name === '') {
                return $this->json(['error' => 'Имя не может быть пустым', 'code' => 'invalid_name'], 400);
            }
            $trainer->setName($name);
            $staff->setName($name);
        }
        if (array_key_exists('specialization', $data)) {
            $spec = trim((string) $data['specialization']);
            $trainer->setSpecialization($spec !== '' ? $spec : null);
        }
        if (array_key_exists('description', $data)) {
            $desc = trim((string) $data['description']);
            $trainer->setDescription($desc !== '' ? $desc : null);
        }
        if (array_key_exists('photo_url', $data)) {
            $url = trim((string) $data['photo_url']);
            $trainer->setPhotoUrl($url !== '' ? $url : null);
        }

        $this->em->flush();

        return $this->json($this->serialize($trainer, $request));
    }

    #[Route('/photo', name: 'api_staff_trainer_profile_photo', methods: ['POST'])]
    public function uploadPhoto(Request $request): JsonResponse
    {
        $staff = $this->requireTrainerStaff($request);
        if ($staff instanceof JsonResponse) {
            return $staff;
        }

        try {
            $trainer = $this->onboarding->ensureTrainerProfile($staff);
        } catch (\Throwable $e) {
            return $this->json(['error' => $e->getMessage(), 'code' => 'trainer_missing'], 400);
        }

        $file = $request->files->get('photo') ?? $request->files->get('file');
        if (!$file instanceof UploadedFile) {
            return $this->json(['error' => 'Прикрепите файл photo', 'code' => 'missing_file'], 400);
        }
        if (!$file->isValid()) {
            return $this->json(['error' => $file->getErrorMessage(), 'code' => 'invalid_file'], 400);
        }

        try {
            $path = $this->storeTrainerPhoto($file);
        } catch (\Throwable $e) {
            return $this->json(['error' => $e->getMessage(), 'code' => 'upload_failed'], 400);
        }

        $trainer->setPhotoUrl($path);
        $this->em->flush();

        return $this->json($this->serialize($trainer, $request));
    }

    /** @return StaffUser|JsonResponse */
    private function requireTrainerStaff(Request $request): StaffUser|JsonResponse
    {
        $staff = $this->staffResolver->resolve($request);
        if (!$staff instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }
        $isPrivileged = \in_array('ROLE_SUPER_ADMIN', $staff->getRoles(), true)
            || \in_array('ROLE_ADMIN', $staff->getRoles(), true)
            || \in_array('ROLE_MANAGER', $staff->getRoles(), true);
        if (!$staff->isTrainerRole() && !$isPrivileged) {
            return $this->json(['error' => 'Доступно только тренерам', 'code' => 'forbidden'], 403);
        }

        return $staff;
    }

    /** @return array<string, mixed> */
    private function serialize(Trainer $trainer, Request $request): array
    {
        return [
            'id' => $trainer->getId() !== null ? 'trainer-' . $trainer->getId() : null,
            'name' => $trainer->getName(),
            'specialization' => $trainer->getSpecialization(),
            'description' => $trainer->getDescription(),
            'rating' => $trainer->getRating() ?? 0.0,
            'photo_url' => self::absolutePublicUrl($request, $trainer->getPhotoUrl()),
            'photo_path' => $trainer->getPhotoUrl(),
        ];
    }

    private function storeTrainerPhoto(UploadedFile $file): string
    {
        $allowed = ['jpg', 'jpeg', 'png', 'webp'];
        $ext = strtolower(pathinfo($file->getClientOriginalName(), \PATHINFO_EXTENSION));
        if (!\in_array($ext, $allowed, true)) {
            try {
                $g = strtolower((string) ($file->guessExtension() ?: ''));
                if (\in_array($g, $allowed, true)) {
                    $ext = $g;
                }
            } catch (\Throwable) {
            }
        }
        if (!\in_array($ext, $allowed, true)) {
            throw new \RuntimeException('Формат фото: jpg, png, webp');
        }

        $uploadsDir = $this->getParameter('kernel.project_dir') . '/public/uploads/trainers';
        if (!is_dir($uploadsDir) && !mkdir($uploadsDir, 0775, true) && !is_dir($uploadsDir)) {
            throw new \RuntimeException('Не удалось создать директорию для фото');
        }

        $filename = 'trainer_' . date('Ymd_His') . '_' . bin2hex(random_bytes(4)) . '.' . $ext;
        $file->move($uploadsDir, $filename);

        return '/uploads/trainers/' . $filename;
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
