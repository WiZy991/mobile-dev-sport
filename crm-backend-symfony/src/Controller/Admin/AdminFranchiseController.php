<?php

namespace App\Controller\Admin;

use App\Entity\AccessAlarm;
use App\Entity\Club;
use App\Entity\GatewayCommand;
use App\Service\Admin\AdminMenuBuilder;
use App\Service\Admin\ClubDeletionService;
use App\Service\Reports\OccupancyService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Управление франшизой клубов: per-club настройки PERCo, токен ПК-шлюза,
 * статус «онлайн», постановка команд (например, открыть дверь).
 *
 * Маршруты объявлены в config/routes/admin.yaml выше catch-all admin_section,
 * поэтому атрибутные #[Route] здесь не используются (иначе будет дубликат имени).
 */
class AdminFranchiseController extends AbstractController
{
    /** Считаем шлюз «онлайн», если heartbeat был не позже этого числа секунд назад. */
    private const ONLINE_THRESHOLD_SECONDS = 90;

    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly AdminMenuBuilder $adminMenuBuilder,
        private readonly OccupancyService $occupancy,
        private readonly ClubDeletionService $clubDeletion,
    ) {
    }

    public function list(): Response
    {
        $clubs = $this->em->getRepository(Club::class)->findBy([], ['name' => 'ASC']);
        $now = new \DateTimeImmutable();

        $rows = [];
        foreach ($clubs as $club) {
            $rows[] = [
                'club' => $club,
                'online' => $this->isOnline($club, $now),
                'last_seen' => $club->getGatewayLastSeenAt(),
                'has_token' => (bool) $club->getGatewayToken(),
                'has_perco' => (bool) ($club->getPercoBaseUrl() && $club->getPercoLogin() && $club->getPercoPassword()),
                'pending' => $this->countPendingCommands($club),
                'inside' => $this->occupancy->countCurrentlyInside($club),
            ];
        }

        return $this->render('admin/franchise/list.html.twig', [
            'menu' => $this->adminMenuBuilder->buildFor($this->getUser()),
            'current' => 'franchise',
            'rows' => $rows,
        ]);
    }

    public function edit(int $id): Response
    {
        $club = $this->findClubOrFail($id);

        $recentCommands = $this->em->getRepository(GatewayCommand::class)
            ->createQueryBuilder('c')
            ->andWhere('c.club = :club')
            ->setParameter('club', $club)
            ->orderBy('c.id', 'DESC')
            ->setMaxResults(20)
            ->getQuery()
            ->getResult();

        $recentAlarms = $this->em->getRepository(AccessAlarm::class)
            ->createQueryBuilder('a')
            ->andWhere('a.club = :club')
            ->setParameter('club', $club)
            ->orderBy('a.id', 'DESC')
            ->setMaxResults(20)
            ->getQuery()
            ->getResult();

        return $this->render('admin/franchise/edit.html.twig', [
            'menu' => $this->adminMenuBuilder->buildFor($this->getUser()),
            'current' => 'franchise',
            'club' => $club,
            'online' => $this->isOnline($club, new \DateTimeImmutable()),
            'recent_commands' => $recentCommands,
            'recent_alarms' => $recentAlarms,
            'inside' => $this->occupancy->countCurrentlyInside($club),
            'inside_list' => $this->occupancy->listCurrentlyInside($club, 100),
        ]);
    }

    public function save(int $id, Request $request): Response
    {
        $club = $this->findClubOrFail($id);

        $name = trim((string) $request->request->get('name', ''));
        if ($name !== '') {
            $club->setName($name);
        }
        $address = trim((string) $request->request->get('address', ''));
        if ($address !== '') {
            $club->setAddress($address);
        }

        $club->setPercoBaseUrl($this->trimOrNull($request->request->get('perco_base_url')));
        $club->setPercoLogin($this->trimOrNull($request->request->get('perco_login')));
        $newPassword = (string) $request->request->get('perco_password', '');
        if ($newPassword !== '') {
            $club->setPercoPassword($newPassword);
        }
        $deviceId = trim((string) $request->request->get('perco_entry_device_id', ''));
        $club->setPercoEntryDeviceId($deviceId === '' ? null : (int) $deviceId);
        $club->setPercoVerifySsl($request->request->get('perco_verify_ssl') === '1');

        $this->em->flush();
        $this->addFlash('success', 'Настройки клуба сохранены.');

        return $this->redirectToRoute('admin_franchise_edit', ['id' => $club->getId()]);
    }

    public function regenerateToken(int $id, Request $request): Response
    {
        $club = $this->findClubOrFail($id);
        if (!$request->isMethod('POST')) {
            $this->addFlash(
                'warning',
                'Смена токена выполняется только кнопкой «Сгенерировать токен» на странице клуба (открытие этого адреса в строке браузера не меняет токен).',
            );

            return $this->redirectToRoute('admin_franchise_edit', ['id' => $club->getId()]);
        }

        $club->setGatewayToken($this->generateToken());
        $this->em->flush();

        $this->addFlash('success', 'Сгенерирован новый токен шлюза. Скопируйте и сохраните в config.ini шлюза в клубе — старый токен больше не работает.');

        return $this->redirectToRoute('admin_franchise_edit', ['id' => $club->getId()]);
    }

    public function openDoor(int $id, Request $request): Response
    {
        $club = $this->findClubOrFail($id);

        if (!$club->getGatewayToken()) {
            $this->addFlash('warning', 'Сначала сгенерируйте токен шлюза для этого клуба.');

            return $this->redirectToRoute('admin_franchise_edit', ['id' => $club->getId()]);
        }
        if (!$club->getPercoEntryDeviceId()) {
            $this->addFlash('warning', 'Не задан ID устройства входа PERCo для этого клуба.');

            return $this->redirectToRoute('admin_franchise_edit', ['id' => $club->getId()]);
        }

        $cmd = new GatewayCommand();
        $cmd->setClub($club)
            ->setKind(GatewayCommand::KIND_OPEN_DOOR)
            ->setStatus(GatewayCommand::STATUS_PENDING)
            ->setExpiresAt((new \DateTimeImmutable())->modify('+2 minutes'))
            ->setIssuedBy((string) ($this->getUser()?->getUserIdentifier() ?? 'admin'))
            ->setPayload([
                'device_id' => $club->getPercoEntryDeviceId(),
                'cmd_number' => 1,
                'cmd_type' => 1,
                'cmd_value' => 1,
                'cmd_param' => 0,
                'comment' => trim((string) $request->request->get('comment', '')),
            ]);

        $this->em->persist($cmd);
        $this->em->flush();

        $this->addFlash('success', 'Команда «Открыть дверь» поставлена в очередь #' . $cmd->getId() . '. Шлюз клуба заберёт её при ближайшем опросе (≤ 30 сек).');

        return $this->redirectToRoute('admin_franchise_edit', ['id' => $club->getId()]);
    }

    public function clearGateway(int $id, Request $request): Response
    {
        $club = $this->findClubOrFail($id);
        if (!$request->isMethod('POST')) {
            return $this->redirectToRoute('admin_franchise_edit', ['id' => $club->getId()]);
        }

        $this->clubDeletion->clearGateway($club);
        $this->addFlash('success', 'Шлюз сброшен: токен и heartbeat удалены, очередь команд очищена.');

        return $this->redirectToRoute($request->request->get('redirect') === 'list'
            ? 'admin_franchise_list'
            : 'admin_franchise_edit',
            ['id' => $club->getId()],
        );
    }

    public function deleteClub(int $id, Request $request): Response
    {
        $club = $this->findClubOrFail($id);
        if (!$request->isMethod('POST')) {
            return $this->redirectToRoute('admin_franchise_list');
        }

        $deps = $this->clubDeletion->describeDependencies($club);
        $name = $club->getName();

        try {
            $this->clubDeletion->deleteClub($club);
        } catch (\RuntimeException $e) {
            $this->addFlash('danger', $e->getMessage());

            return $this->redirectToRoute('admin_franchise_list');
        }

        $this->addFlash(
            'success',
            sprintf(
                'Клуб «%s» удалён. Отвязано клиентов: %d, абонементов: %d (активных: %d).',
                $name,
                $deps['users'],
                $deps['subscriptions'],
                $deps['active_subscriptions'],
            ),
        );

        return $this->redirectToRoute('admin_franchise_list');
    }

    private function findClubOrFail(int $id): Club
    {
        $club = $this->em->getRepository(Club::class)->find($id);
        if (!$club instanceof Club) {
            throw $this->createNotFoundException('Клуб не найден');
        }

        return $club;
    }

    private function isOnline(Club $club, \DateTimeImmutable $now): bool
    {
        $seen = $club->getGatewayLastSeenAt();
        if ($seen === null) {
            return false;
        }

        return ($now->getTimestamp() - $seen->getTimestamp()) <= self::ONLINE_THRESHOLD_SECONDS;
    }

    private function countPendingCommands(Club $club): int
    {
        return (int) $this->em->getRepository(GatewayCommand::class)
            ->createQueryBuilder('c')
            ->select('COUNT(c.id)')
            ->andWhere('c.club = :club')
            ->andWhere('c.status = :s')
            ->setParameter('club', $club)
            ->setParameter('s', GatewayCommand::STATUS_PENDING)
            ->getQuery()
            ->getSingleScalarResult();
    }

    private function generateToken(): string
    {
        return bin2hex(random_bytes(24));
    }

    private function trimOrNull(mixed $v): ?string
    {
        $s = trim((string) ($v ?? ''));

        return $s === '' ? null : $s;
    }
}
