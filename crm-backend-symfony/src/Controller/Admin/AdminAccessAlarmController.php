<?php

namespace App\Controller\Admin;

use App\Entity\AccessAlarm;
use App\Entity\StaffUser;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;

/**
 * JSON для колокола тревог в админке: опрос новых access_alarms и desktop-уведомлений.
 */
final class AdminAccessAlarmController extends AbstractController
{
  private const LIST_LIMIT = 30;

  public function __construct(
    private readonly EntityManagerInterface $em,
  ) {
  }

  public function poll(Request $request): JsonResponse
  {
    $user = $this->getUser();
    if (!$user instanceof StaffUser || !$this->canViewAccessAlarms($user)) {
      return new JsonResponse([
        'enabled' => false,
        'latest_id' => 0,
        'unread_count' => 0,
        'alarms' => [],
        'new_alarms' => [],
      ]);
    }

    $sinceId = max(0, (int) $request->query->get('since_id', 0));
    $ackId = max(0, (int) $request->query->get('ack_id', 0));

    $recent = $this->fetchAlarms(null);
    $latestId = 0;
    foreach ($recent as $alarm) {
      $id = $alarm->getId();
      if ($id !== null && $id > $latestId) {
        $latestId = $id;
      }
    }

    $newAlarms = $sinceId > 0 ? $this->fetchAlarms($sinceId) : [];
    $unreadCount = $this->countUnread($ackId);

    return new JsonResponse([
      'enabled' => true,
      'latest_id' => $latestId,
      'unread_count' => $unreadCount,
      'alarms' => array_map(fn (AccessAlarm $a) => $this->serializeAlarm($a), $recent),
      'new_alarms' => array_map(fn (AccessAlarm $a) => $this->serializeAlarm($a), $newAlarms),
    ]);
  }

  /** @return list<AccessAlarm> */
  private function fetchAlarms(?int $sinceId): array
  {
    $qb = $this->em->getRepository(AccessAlarm::class)
      ->createQueryBuilder('a')
      ->leftJoin('a.club', 'c')->addSelect('c')
      ->leftJoin('a.user', 'u')->addSelect('u')
      ->orderBy('a.id', 'DESC')
      ->setMaxResults(self::LIST_LIMIT);

    if ($sinceId !== null && $sinceId > 0) {
      $qb->andWhere('a.id > :since')->setParameter('since', $sinceId)->orderBy('a.id', 'ASC');
    }

    /** @var list<AccessAlarm> */
    return $qb->getQuery()->getResult();
  }

  private function countUnread(int $ackId): int
  {
    $qb = $this->em->getRepository(AccessAlarm::class)
      ->createQueryBuilder('a')
      ->select('COUNT(a.id)');

    if ($ackId > 0) {
      $qb->andWhere('a.id > :ack')->setParameter('ack', $ackId);
    }

    return (int) $qb->getQuery()->getSingleScalarResult();
  }

  /** @return array<string, mixed> */
  private function serializeAlarm(AccessAlarm $alarm): array
  {
    $club = $alarm->getClub();
    $client = $alarm->getUser();
    $clubName = $club?->getName() ?? 'клуб';
    $clientName = $client?->getName();
    $count = $alarm->getPeopleCount();

    if ($alarm->getType() === AccessAlarm::TYPE_GROUP_ENTRY) {
      $title = 'Вход группой без прохода по QR';
      $body = sprintf('%s: зафиксирован вход %d чел. без прохода по QR.', $clubName, $count);
    } else {
      $title = 'Проход вдвоём по одному QR';
      $who = $clientName ? (' (' . $clientName . ')') : '';
      $body = sprintf('%s: по одному QR прошло %d чел.%s', $clubName, $count, $who);
    }

    $clubId = $club?->getId();

    return [
      'id' => $alarm->getId(),
      'type' => $alarm->getType(),
      'title' => $title,
      'body' => $body,
      'club_id' => $clubId,
      'club_name' => $clubName,
      'people_count' => $count,
      'client_name' => $clientName,
      'client_id' => $client?->getId(),
      'created_at' => $alarm->getCreatedAt()->format(\DateTimeInterface::ATOM),
      'club_url' => $clubId !== null
        ? $this->generateUrl('admin_franchise_edit', ['id' => $clubId], UrlGeneratorInterface::ABSOLUTE_PATH)
        : $this->generateUrl('admin_franchise_list', [], UrlGeneratorInterface::ABSOLUTE_PATH),
      'client_url' => $client?->getId() !== null
        ? $this->generateUrl('admin_client_show', ['id' => $client->getId()], UrlGeneratorInterface::ABSOLUTE_PATH)
        : null,
    ];
  }

  private function canViewAccessAlarms(StaffUser $staff): bool
  {
    $roles = $staff->getRoles();
    if (array_intersect($roles, ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_MANAGER']) !== []) {
      return true;
    }
    if (\in_array('ROLE_CLUB_ALL', $roles, true)) {
      return true;
    }
    foreach ($roles as $role) {
      if (str_starts_with($role, 'ROLE_CLUB_')) {
        return true;
      }
    }

    return false;
  }
}
