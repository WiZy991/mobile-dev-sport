<?php

namespace App\Controller\Api;

use App\Entity\Locker;
use App\Entity\LockerBooking;
use App\Service\CurrentUserResolver;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/lockers')]
class LockerController extends AbstractController
{
    private const BOOKING_DURATION_HOURS = 4;

    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
    ) {}

    #[Route('', name: 'api_lockers_list', methods: ['GET'])]
    public function list(): JsonResponse
    {
        $lockers = $this->em->getRepository(Locker::class)->findBy(
            [],
            ['number' => 'ASC']
        );

        $data = array_map(self::serializeLocker(...), $lockers);
        return $this->json($data);
    }

    #[Route('/my-booking', name: 'api_lockers_my_booking', methods: ['GET'])]
    public function myBooking(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $booking = $this->em->getRepository(LockerBooking::class)->findOneBy(
            ['user' => $user],
            ['id' => 'DESC']
        );

        if (!$booking || !$booking->isActive()) {
            return $this->json(null);
        }

        return $this->json(self::serializeBooking($booking));
    }

    #[Route('/{id}/book', name: 'api_lockers_book', methods: ['POST'])]
    public function book(string $id, Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $lockerId = str_starts_with($id, 'locker-') ? (int) substr($id, 7) : (int) $id;
        /** @var Locker|null $locker */
        $locker = $this->em->getRepository(Locker::class)->find($lockerId);

        if (!$locker) {
            return $this->json(['error' => 'Locker not found'], 404);
        }

        if (!$locker->isAvailable()) {
            return $this->json(['error' => 'Locker is not available'], 409);
        }

        // Check if user has active booking
        $existing = $this->em->getRepository(LockerBooking::class)->findOneBy(
            ['user' => $user],
            ['id' => 'DESC']
        );
        if ($existing && $existing->isActive()) {
            return $this->json(['error' => 'You already have an active locker booking'], 409);
        }

        $now = new \DateTimeImmutable();
        $endsAt = $now->modify('+' . self::BOOKING_DURATION_HOURS . ' hours');

        $booking = (new LockerBooking())
            ->setLocker($locker)
            ->setUser($user)
            ->setStartedAt($now)
            ->setEndsAt($endsAt);

        $locker->setStatus(Locker::STATUS_OCCUPIED);

        $this->em->persist($booking);
        $this->em->persist($locker);
        $this->em->flush();

        return $this->json(self::serializeBooking($booking), 201);
    }

    #[Route('/release', name: 'api_lockers_release', methods: ['POST'])]
    public function release(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $booking = $this->em->getRepository(LockerBooking::class)->findOneBy(
            ['user' => $user],
            ['id' => 'DESC']
        );

        if (!$booking || !$booking->isActive()) {
            return $this->json(['error' => 'No active locker booking'], 404);
        }

        $locker = $booking->getLocker();
        $locker->setStatus(Locker::STATUS_AVAILABLE);
        $booking->setReleasedAt(new \DateTimeImmutable());

        $this->em->persist($locker);
        $this->em->persist($booking);
        $this->em->flush();

        return $this->json(['success' => true]);
    }

    private static function serializeLocker(Locker $l): array
    {
        return [
            'id' => 'locker-' . $l->getId(),
            'number' => $l->getNumber(),
            'status' => $l->getStatus(),
        ];
    }

    private static function serializeBooking(LockerBooking $b): array
    {
        $l = $b->getLocker();
        return [
            'id' => 'locker-booking-' . $b->getId(),
            'locker' => self::serializeLocker($l),
            'started_at' => $b->getStartedAt()->format('Y-m-d\TH:i:s'),
            'ends_at' => $b->getEndsAt()->format('Y-m-d\TH:i:s'),
            'qr_token' => $b->getQrToken(),
            'qr_code_data' => 'FITNESSCLUB:LOCKER:' . $l->getId() . ':' . $b->getQrToken(),
        ];
    }
}
