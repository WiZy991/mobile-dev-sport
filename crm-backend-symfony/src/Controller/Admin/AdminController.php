<?php

namespace App\Controller\Admin;

use App\Entity\User;
use App\Entity\Product;
use App\Entity\Lead;
use App\Entity\Task;
use App\Entity\Sale;
use App\Entity\ClientNote;
use App\Entity\Document;
use App\Entity\PromoCode;
use App\Entity\SubscriptionPlan;
use App\Entity\Subscription;
use App\Entity\Training;
use App\Entity\Trainer;
use App\Entity\Booking;
use App\Entity\AccessLog;
use App\Entity\ClubSetting;
use App\Entity\Tag;
use App\Entity\Club;
use App\Entity\LeadNote;
use App\Entity\Expense;
use App\Entity\Promotion;
use App\Entity\StaffUser;
use App\Entity\SupportTicket;
use App\Service\Admin\AdminMenuBuilder;
use App\Service\Admin\ClientImportService;
use App\Service\Admin\SubscriptionPlanCatalog;
use App\Service\Lead\LeadIngestionService;
use App\Service\Lead\LeadSource;
use App\Service\Api\SubscriptionFreezePolicy;
use App\Service\Api\SubscriptionFreezeService;
use App\Service\Security\PassportAccessPolicy;
use App\Service\Integration\PercoWebClient;
use App\Service\Reports\OccupancyService;
use App\Service\Reports\VisitPeriodResolver;
use App\Service\Reports\VisitReportService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\File\UploadedFile;
use Symfony\Component\HttpFoundation\StreamedResponse;
use Symfony\Component\Routing\Annotation\Route;
use PhpOffice\PhpSpreadsheet\Spreadsheet;
use PhpOffice\PhpSpreadsheet\Writer\Xlsx;

#[Route('/admin')]
class AdminController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly AdminMenuBuilder $adminMenuBuilder,
        private readonly PercoWebClient $percoWebClient,
        private readonly ClientImportService $clientImportService,
        private readonly OccupancyService $occupancy,
        private readonly VisitPeriodResolver $visitPeriodResolver,
        private readonly VisitReportService $visitReport,
        private readonly PassportAccessPolicy $passportAccess,
        private readonly SubscriptionFreezePolicy $freezePolicy,
        private readonly SubscriptionFreezeService $freezeService,
        private readonly SubscriptionPlanCatalog $planCatalog,
        private readonly LeadIngestionService $leadIngestion,
    ) {}

    private function buildMenu(): array
    {
        return $this->adminMenuBuilder->buildFor($this->getUser());
    }

    private function redirectToSchedulePreservingDate(Request $request): Response
    {
        $url = $this->generateUrl('admin_section', ['section' => 'schedule']);
        $d = $request->request->get('redirect_date');
        if ($d !== null && $d !== '') {
            $url .= '?date=' . rawurlencode((string) $d);
        }

        return $this->redirect($url);
    }

    private function redirectToBookingsPreservingDate(Request $request): Response
    {
        $url = $this->generateUrl('admin_section', ['section' => 'bookings']);
        $d = $request->request->get('redirect_date');
        if ($d !== null && $d !== '') {
            $url .= '?date=' . rawurlencode((string) $d);
        }

        return $this->redirect($url);
    }

    #[Route('/search', name: 'admin_search', methods: ['GET'])]
    public function search(Request $request): Response
    {
        $q = trim((string) $request->query->get('q', ''));
        $menu = $this->buildMenu();
        $results = ['clients' => [], 'leads' => [], 'tasks' => []];

        if (mb_strlen($q) >= 2) {
            $qLike = '%' . $q . '%';
            $results['clients'] = $this->em->createQueryBuilder()
                ->select('u')
                ->from(User::class, 'u')
                ->where('u.name LIKE :q OR u.phone LIKE :q OR u.email LIKE :q')
                ->setParameter('q', $qLike)
                ->orderBy('u.name', 'ASC')
                ->setMaxResults(15)
                ->getQuery()->getResult();

            $results['leads'] = $this->em->createQueryBuilder()
                ->select('l')
                ->from(Lead::class, 'l')
                ->where('l.name LIKE :q OR l.phone LIKE :q OR l.email LIKE :q')
                ->setParameter('q', $qLike)
                ->orderBy('l.id', 'DESC')
                ->setMaxResults(15)
                ->getQuery()->getResult();

            $results['tasks'] = $this->em->createQueryBuilder()
                ->select('t')
                ->from(Task::class, 't')
                ->leftJoin('t.client', 'c')
                ->where('t.title LIKE :q OR c.name LIKE :q')
                ->setParameter('q', $qLike)
                ->orderBy('t.id', 'DESC')
                ->setMaxResults(15)
                ->getQuery()->getResult();
        }

        return $this->render('admin/search.html.twig', [
            'menu' => $menu,
            'current' => null,
            'q' => $q,
            'results' => $results,
        ]);
    }

    #[Route('', name: 'admin_dashboard', methods: ['GET'])]
    public function dashboard(): Response
    {
        $clientsCount = $this->em->getRepository(User::class)->count([]);
        $leadsCount = $this->em->getRepository(Lead::class)->count(['status' => 'new']);
        $leadsCount += $this->em->getRepository(Lead::class)->count(['status' => 'trial_scheduled']);
        $leadsCount += $this->em->getRepository(Lead::class)->count(['status' => 'trial_visited']);

        $todayStart = new \DateTimeImmutable('today');
        $todayEnd = $todayStart->modify('+1 day');

        // Тренировки сегодня по полю startAt
        $trainingsToday = $this->em->getRepository(Training::class)->count([
            // count по диапазону в Doctrine не так просто через count(), поэтому сделаем через DQL ниже при необходимости
        ]);

        $qbTrainings = $this->em->createQueryBuilder()
            ->select('COUNT(t.id)')
            ->from(Training::class, 't')
            ->where('t.startAt >= :start')
            ->andWhere('t.startAt < :end')
            ->setParameter('start', $todayStart)
            ->setParameter('end', $todayEnd);
        $trainingsToday = (int) $qbTrainings->getQuery()->getSingleScalarResult();

        // Выручка за день по продажам
        $qbRevenue = $this->em->createQueryBuilder()
            ->select('COALESCE(SUM(s.total), 0)')
            ->from(Sale::class, 's')
            ->where('s.createdAt >= :start')
            ->andWhere('s.createdAt < :end')
            ->setParameter('start', $todayStart)
            ->setParameter('end', $todayEnd);
        $revenueToday = (float) $qbRevenue->getQuery()->getSingleScalarResult();

        $visitsToday = (int) $this->em->createQueryBuilder()
            ->select('COUNT(a.id)')
            ->from(AccessLog::class, 'a')
            ->where('a.result = :result')
            ->andWhere('a.createdAt >= :start')
            ->andWhere('a.createdAt < :end')
            ->setParameter('result', 'granted')
            ->setParameter('start', $todayStart)
            ->setParameter('end', $todayEnd)
            ->getQuery()->getSingleScalarResult();

        $monthStart = $todayStart->modify('first day of this month');
        $monthEnd = $monthStart->modify('last day of this month')->modify('+1 day');
        $revenueMonth = (float) $this->em->createQueryBuilder()
            ->select('COALESCE(SUM(s.total), 0)')
            ->from(Sale::class, 's')
            ->where('s.createdAt >= :start')->andWhere('s.createdAt < :end')
            ->setParameter('start', $monthStart)->setParameter('end', $monthEnd)
            ->getQuery()->getSingleScalarResult();

        $funnelLeads = [
            'new' => $this->em->getRepository(Lead::class)->count(['status' => 'new']),
            'converted' => $this->em->getRepository(Lead::class)->count(['status' => 'converted']),
        ];

        // Открытые и в работе задачи
        $tasks = $this->em->createQueryBuilder()
            ->select('t')
            ->from(Task::class, 't')
            ->where('t.status IN (:statuses)')
            ->setParameter('statuses', ['open', 'in_progress'])
            ->orderBy('t.dueAt', 'ASC')
            ->addOrderBy('t.id', 'DESC')
            ->setMaxResults(10)
            ->getQuery()->getResult();

        // Ближайшие тренировки (сегодня + завтра)
        $tomorrowEnd = $todayStart->modify('+2 days');
        $upcomingTrainings = $this->em->createQueryBuilder()
            ->select('t')
            ->from(Training::class, 't')
            ->where('t.startAt >= :start')
            ->andWhere('t.startAt < :end')
            ->setParameter('start', $todayStart)
            ->setParameter('end', $tomorrowEnd)
            ->orderBy('t.startAt', 'ASC')
            ->setMaxResults(10)
            ->getQuery()->getResult();

        $users = $this->em->getRepository(User::class)->findBy([], ['name' => 'ASC']);
        $products = $this->em->getRepository(Product::class)->findBy(['isActive' => true], ['name' => 'ASC']);

        return $this->render('admin/dashboard.html.twig', [
            'menu' => $this->buildMenu(),
            'current' => 'dashboard',
            'stats' => [
                'clients' => $clientsCount,
                'leads' => $leadsCount,
                'trainingsToday' => $trainingsToday,
                'visitsToday' => $visitsToday,
                'currentlyInside' => $this->occupancy->countCurrentlyInside(),
                'revenueToday' => $revenueToday,
                'revenueMonth' => $revenueMonth,
            ],
            'funnelLeads' => $funnelLeads,
            'tasks' => $tasks,
            'upcomingTrainings' => $upcomingTrainings,
            'users' => $users,
            'products' => $products,
        ]);
    }

    #[Route('/clients/new', name: 'admin_client_new', methods: ['GET', 'POST'])]
    public function clientNew(Request $request): Response
    {
        $menu = $this->buildMenu();

        if ($request->isMethod('POST')) {
            $email = trim((string) $request->request->get('email', ''));
            $existing = $this->em->getRepository(User::class)->findOneBy(['email' => $email]);
            if ($existing) {
                $this->addFlash('danger', 'Клиент с таким email уже существует.');
                $allTags = $this->em->getRepository(Tag::class)->findBy([], ['name' => 'ASC']);

                $allClubs = $this->em->getRepository(Club::class)->findBy([], ['name' => 'ASC']);

                return $this->render('admin/client_new.html.twig', [
                    'menu' => $menu,
                    'current' => 'clients',
                    'allTags' => $allTags,
                    'allClubs' => $allClubs,
                    'formData' => $request->request->all(),
                ]);
            }

            $user = $this->createOrUpdateUserFromRequest(new User(), $request);
            $this->em->persist($user);
            $this->em->flush();

            $this->addFlash('success', 'Клиент создан.' . (!$user->hasRequiredDataForSubscription() ? ' Заполните паспортные данные для выдачи абонемента.' : ''));

            return $this->redirectToRoute('admin_client_show', ['id' => $user->getId()]);
        }

        $allTags = $this->em->getRepository(Tag::class)->findBy([], ['name' => 'ASC']);
        $allClubs = $this->em->getRepository(Club::class)->findBy([], ['name' => 'ASC']);
        return $this->render('admin/client_new.html.twig', [
            'menu' => $menu,
            'current' => 'clients',
            'allTags' => $allTags,
            'allClubs' => $allClubs,
        ]);
    }

    #[Route('/clients/import', name: 'admin_clients_import', priority: 10, methods: ['GET', 'POST'])]
    public function clientsImport(Request $request): Response
    {
        $menu = $this->buildMenu();

        if ($request->isMethod('POST')) {
            $file = $request->files->get('import_file');
            if (!$file instanceof UploadedFile || !$file->isValid()) {
                $this->addFlash('danger', 'Выберите файл .csv, .xlsx или .xls.');

                return $this->render('admin/client_import.html.twig', [
                    'menu' => $menu,
                    'current' => 'clients',
                ]);
            }

            $updateExisting = $request->request->getBoolean('update_existing');

            try {
                $result = $this->clientImportService->import($file, $updateExisting);
            } catch (\InvalidArgumentException $e) {
                $this->addFlash('danger', $e->getMessage());

                return $this->render('admin/client_import.html.twig', [
                    'menu' => $menu,
                    'current' => 'clients',
                ]);
            } catch (\Throwable $e) {
                $this->addFlash('danger', 'Ошибка чтения файла: ' . $e->getMessage());

                return $this->render('admin/client_import.html.twig', [
                    'menu' => $menu,
                    'current' => 'clients',
                ]);
            }

            $msg = sprintf(
                'Импорт: клиенты — создано %d, обновлено %d; абонементы — создано %d, обновлено %d; записи на тренировки — создано %d, обновлено %d; пропусков строк: %d.',
                $result->clientsCreated,
                $result->clientsUpdated,
                $result->subscriptionsCreated,
                $result->subscriptionsUpdated,
                $result->bookingsCreated,
                $result->bookingsUpdated,
                $result->skipped
            );
            $this->addFlash('success', $msg);
            foreach (array_slice($result->errors, 0, 25) as $err) {
                $this->addFlash('warning', $err);
            }
            if (count($result->errors) > 25) {
                $this->addFlash('warning', '… и ещё ' . (count($result->errors) - 25) . ' сообщений.');
            }

            return $this->redirectToRoute('admin_section', ['section' => 'clients']);
        }

        return $this->render('admin/client_import.html.twig', [
            'menu' => $menu,
            'current' => 'clients',
        ]);
    }

    private function createOrUpdateUserFromRequest(User $user, Request $request): User
    {
        $user->setName((string) $request->request->get('name'))
            ->setPhone((string) $request->request->get('phone'))
            ->setEmail((string) $request->request->get('email'))
            ->setBonusPoints(max(0, (int) $request->request->get('bonus_points', 0)))
            ->setGender($request->request->get('gender') ?: null);

        $dob = $request->request->get('date_of_birth');
        $user->setDateOfBirth($dob ? new \DateTimeImmutable($dob) : null);
        $user->setPlaceOfBirth($request->request->get('place_of_birth') ?: null);

        $user->setPassportSeries($request->request->get('passport_series') ?: null);
        $user->setPassportNumber($request->request->get('passport_number') ?: null);
        $user->setPassportIssuedBy($request->request->get('passport_issued_by') ?: null);
        $pid = $request->request->get('passport_issue_date');
        $user->setPassportIssueDate($pid ? new \DateTimeImmutable($pid) : null);
        $user->setPassportDepartmentCode($request->request->get('passport_department_code') ?: null);
        $user->setRegistrationAddress($request->request->get('registration_address') ?: null);
        $user->setEmergencyContact($request->request->get('emergency_contact') ?: null);

        // Теги (если таблицы есть)
        try {
            $tagIdsRaw = $request->request->get('tag_ids');
            $tagIds = is_array($tagIdsRaw) ? $tagIdsRaw : ($tagIdsRaw ? [$tagIdsRaw] : []);
            $tagIds = array_filter(array_map('intval', $tagIds));
            $user->clearTags();
            if (!empty($tagIds)) {
                foreach ($this->em->getRepository(Tag::class)->findBy(['id' => $tagIds]) as $tag) {
                    $user->addTag($tag);
                }
            }
        } catch (\Throwable $e) {
            // Таблицы тегов могут отсутствовать — пропускаем
        }

        $clubIdRaw = $request->request->get('club_id');
        $clubId = ($clubIdRaw !== null && $clubIdRaw !== '') ? (int) $clubIdRaw : 0;
        if ($clubId > 0) {
            $club = $this->em->getRepository(Club::class)->find($clubId);
            $user->setClub($club instanceof Club ? $club : null);
        } else {
            $user->setClub(null);
        }

        return $user;
    }

    /**
     * @return User[]
     */
    private function buildFilteredClientsList(Request $request): array
    {
        $search = trim((string) $request->query->get('q', ''));
        $tagId = $request->query->get('tag_id') ? (int) $request->query->get('tag_id') : null;
        $clubId = $request->query->get('club_id') ? (int) $request->query->get('club_id') : null;

        if ($tagId) {
            $tag = $this->em->getRepository(Tag::class)->find($tagId);
            $clients = $tag ? $tag->getUsers()->toArray() : [];
        } else {
            $clients = $this->em->getRepository(User::class)->findBy([], ['name' => 'ASC']);
        }
        if ($clubId !== null && $clubId > 0) {
            $clients = array_values(array_filter($clients, fn (User $u) => $u->getClub()?->getId() === $clubId));
        }
        if ($search !== '') {
            $searchLower = mb_strtolower($search);
            $clients = array_filter($clients, fn (User $u) =>
                str_contains(mb_strtolower($u->getName()), $searchLower)
                || str_contains((string) $u->getEmail(), $search)
                || str_contains((string) $u->getPhone(), $search)
            );
        }

        return array_values($clients);
    }

    private function clientTagNamesJoined(User $u): string
    {
        $names = [];
        foreach ($u->getTags() as $t) {
            $names[] = $t->getName();
        }

        return $names !== [] ? implode(', ', $names) : '—';
    }

    #[Route('/clients/{id}', name: 'admin_client_show', methods: ['GET'])]
    public function showClient(int $id): Response
    {
        $menu = $this->buildMenu();

        $client = $this->em->getRepository(User::class)->find($id);
        if (!$client) {
            throw $this->createNotFoundException();
        }

        $subscriptions = $this->em->getRepository(Subscription::class)->findBy(
            ['user' => $client],
            ['id' => 'DESC']
        );
        $bookings = $this->em->getRepository(Booking::class)->findBy(
            ['user' => $client],
            ['id' => 'DESC']
        );
        $sales = $this->em->getRepository(Sale::class)->findBy(
            ['user' => $client],
            ['id' => 'DESC']
        );
        $clientTasks = $this->em->getRepository(Task::class)->findBy(
            ['client' => $client],
            ['dueAt' => 'ASC', 'id' => 'DESC'],
            20
        );
        $clientNotes = $this->em->getRepository(ClientNote::class)->findBy(
            ['client' => $client],
            ['createdAt' => 'DESC'],
            50
        );

        $lastAccess = $this->em->createQueryBuilder()
            ->select('a')
            ->from(AccessLog::class, 'a')
            ->where('a.user = :user')->andWhere('a.result = :result')
            ->setParameter('user', $client)->setParameter('result', 'granted')
            ->orderBy('a.createdAt', 'DESC')
            ->setMaxResults(1)
            ->getQuery()->getOneOrNullResult();

        $pushTokenCount = (int) $this->em->createQueryBuilder()
            ->select('COUNT(p.id)')
            ->from(\App\Entity\PushToken::class, 'p')
            ->where('p.user = :user')
            ->setParameter('user', $client)
            ->getQuery()->getSingleScalarResult();

        $today = new \DateTimeImmutable('today');
        $activeSubsCount = 0;
        foreach ($subscriptions as $s) {
            if ($s->isEffectiveActiveOn($today)) {
                ++$activeSubsCount;
            }
        }

        // Лента активности — объединённая хронология
        $activities = [];
        foreach ($subscriptions as $s) {
            $activities[] = ['type' => 'subscription', 'entity' => $s, 'date' => $s->getStartDate()->setTime(12, 0)];
        }
        foreach ($sales as $s) {
            $activities[] = ['type' => 'sale', 'entity' => $s, 'date' => $s->getCreatedAt()];
        }
        foreach ($bookings as $b) {
            $activities[] = ['type' => 'booking', 'entity' => $b, 'date' => $b->getBookedAt()];
        }
        foreach ($clientTasks as $t) {
            $activities[] = ['type' => 'task', 'entity' => $t, 'date' => $t->getDueAt() ?? $t->getCreatedAt()];
        }
        foreach ($clientNotes as $n) {
            $activities[] = ['type' => 'note', 'entity' => $n, 'date' => $n->getCreatedAt()];
        }
        $accessLogs = $this->em->createQueryBuilder()
            ->select('a')
            ->from(AccessLog::class, 'a')
            ->where('a.user = :user')->andWhere('a.result = :result')
            ->setParameter('user', $client)->setParameter('result', 'granted')
            ->orderBy('a.createdAt', 'DESC')
            ->setMaxResults(20)
            ->getQuery()->getResult();
        foreach ($accessLogs as $a) {
            $activities[] = ['type' => 'visit', 'entity' => $a, 'date' => $a->getCreatedAt()];
        }
        usort($activities, fn ($a, $b) => $b['date'] <=> $a['date']);
        $activities = array_slice($activities, 0, 30);

        $allTags = $this->em->getRepository(Tag::class)->findBy([], ['name' => 'ASC']);
        $allClubs = $this->em->getRepository(Club::class)->findBy([], ['name' => 'ASC']);
        return $this->render('admin/client_show.html.twig', [
            'menu' => $menu,
            'current' => 'clients',
            'client' => $client,
            'can_view_passport' => $this->passportAccess->canViewPassportDetails($this->getUser()),
            'subscriptions' => $subscriptions,
            'bookings' => $bookings,
            'sales' => $sales,
            'clientTasks' => $clientTasks,
            'clientNotes' => $clientNotes,
            'lastAccess' => $lastAccess,
            'pushTokenCount' => $pushTokenCount,
            'activeSubsCount' => $activeSubsCount,
            'activities' => $activities,
            'allTags' => $allTags,
            'allClubs' => $allClubs,
            'isCurrentlyInside' => $this->occupancy->isUserCurrentlyInside($client),
        ]);
    }

    #[Route('/clients/{id}/notes', name: 'admin_note_new', methods: ['POST'])]
    public function addClientNote(int $id, Request $request): Response
    {
        $client = $this->em->getRepository(User::class)->find($id);
        if (!$client) {
            throw $this->createNotFoundException();
        }
        $text = trim((string) $request->request->get('text'));
        if ($text !== '') {
            $note = (new ClientNote())->setClient($client)->setText($text);
            $this->em->persist($note);
            $this->em->flush();
        }
        return $this->redirectToRoute('admin_client_show', ['id' => $id]);
    }

    #[Route('/tags/new', name: 'admin_tag_new', methods: ['POST'])]
    public function createTag(Request $request): Response
    {
        $name = trim((string) $request->request->get('name'));
        if ($name !== '') {
            $tag = (new Tag())->setName($name)->setColor($request->request->get('color') ?: '#6c757d');
            $this->em->persist($tag);
            $this->em->flush();
            $this->addFlash('success', 'Тег «' . $name . '» создан.');
        }
        return $this->redirectToRoute('admin_section', ['section' => 'tags']);
    }

    #[Route('/expenses/new', name: 'admin_expense_new', methods: ['POST'])]
    public function createExpense(Request $request): Response
    {
        $desc = trim((string) $request->request->get('description'));
        $amount = (float) $request->request->get('amount', 0);
        $dateRaw = $request->request->get('date');
        $date = $dateRaw ? new \DateTimeImmutable($dateRaw) : new \DateTimeImmutable();
        if ($desc !== '' && $amount > 0) {
            $expense = (new Expense())->setDescription($desc)->setAmount($amount)->setDate($date)
                ->setCategory($request->request->get('category') ?: null);
            $this->em->persist($expense);
            $this->em->flush();
        }
        return $this->redirectToRoute('admin_section', ['section' => 'finance']);
    }

    #[Route('/expenses/{id}/delete', name: 'admin_expense_delete', methods: ['POST'])]
    public function deleteExpense(int $id): Response
    {
        $expense = $this->em->getRepository(Expense::class)->find($id);
        if ($expense) {
            $this->em->remove($expense);
            $this->em->flush();
        }
        return $this->redirectToRoute('admin_section', ['section' => 'finance']);
    }

    #[Route('/tags/{id}/delete', name: 'admin_tag_delete', methods: ['POST'])]
    public function deleteTag(int $id): Response
    {
        $tag = $this->em->getRepository(Tag::class)->find($id);
        if ($tag) {
            $this->em->remove($tag);
            $this->em->flush();
        }
        return $this->redirectToRoute('admin_section', ['section' => 'tags']);
    }

    #[Route('/leads/new', name: 'admin_lead_new', methods: ['POST'])]
    public function createLead(Request $request): Response
    {
        $name = (string) $request->request->get('name');
        $phone = (string) $request->request->get('phone');
        $email = $request->request->get('email') ?: null;
        $comment = $request->request->get('comment') ?: null;
        $source = (string) $request->request->get('source', '');
        if (!LeadSource::isValid($source)) {
            $source = LeadSource::CALL;
        }

        try {
            $this->leadIngestion->ingest($name, $phone, $email, $source, $comment);
            $this->em->flush();
            $this->addFlash('success', 'Лид добавлен.');
        } catch (\InvalidArgumentException $e) {
            $this->addFlash('danger', $e->getMessage());
        }

        return $this->redirectToRoute('admin_section', ['section' => 'leads']);
    }

    #[Route('/leads/{id}/status', name: 'admin_lead_status', methods: ['POST'])]
    public function changeLeadStatus(int $id, Request $request): Response
    {
        /** @var Lead|null $lead */
        $lead = $this->em->getRepository(Lead::class)->find($id);
        if (!$lead) {
            throw $this->createNotFoundException();
        }

        $status = (string) $request->request->get('status', 'new');
        $allowed = ['new', 'trial_scheduled', 'trial_visited', 'converted', 'inactive'];
        if (!in_array($status, $allowed, true)) {
            $status = 'new';
        }

        $lead->setStatus($status);
        $this->em->flush();

        return $this->redirectToRoute('admin_section', ['section' => 'leads']);
    }

    #[Route('/leads/{id}/notes', name: 'admin_lead_note_new', methods: ['POST'])]
    public function addLeadNote(int $id, Request $request): Response
    {
        $lead = $this->em->getRepository(Lead::class)->find($id);
        if (!$lead) {
            throw $this->createNotFoundException();
        }
        $text = trim((string) $request->request->get('text'));
        if ($text !== '') {
            $note = (new LeadNote())->setLead($lead)->setText($text);
            $this->em->persist($note);
            $this->em->flush();
        }
        $sf = $request->request->get('status_filter', '');
        $src = $request->request->get('source_filter', '');
        $params = ['section' => 'leads'];
        if ($sf !== '') $params['status'] = $sf;
        if ($src !== '') $params['source'] = $src;
        return $this->redirectToRoute('admin_section', $params);
    }

    #[Route('/leads/{id}/comment', name: 'admin_lead_comment', methods: ['POST'])]
    public function updateLeadComment(int $id, Request $request): Response
    {
        $lead = $this->em->getRepository(Lead::class)->find($id);
        if ($lead) {
            $lead->setComment((string) $request->request->get('comment', ''));
            $this->em->flush();
        }

        return $this->redirectToRoute('admin_section', ['section' => 'leads']);
    }

    #[Route('/leads/{id}/convert', name: 'admin_lead_convert', methods: ['POST'])]
    public function convertLeadToClient(int $id): Response
    {
        /** @var Lead|null $lead */
        $lead = $this->em->getRepository(Lead::class)->find($id);
        if (!$lead) {
            throw $this->createNotFoundException();
        }

        // Создаём клиента на основе лида
        $user = (new User())
            ->setName($lead->getName())
            ->setPhone($lead->getPhone())
            ->setEmail($lead->getEmail() ?? ('lead+' . $lead->getId() . '@example.local'))
            ->setBonusPoints(0);

        $this->em->persist($user);

        $lead->setStatus('converted')
            ->setConvertedUser($user);

        $this->em->flush();

        $this->addFlash('success', 'Клиент создан. Заполните паспортные данные для выдачи абонемента.');

        return $this->redirectToRoute('admin_client_show', ['id' => $user->getId()]);
    }

    #[Route('/clients/{id}/update', name: 'admin_client_update', methods: ['POST'])]
    public function updateClient(int $id, Request $request): Response
    {
        $user = $this->em->getRepository(User::class)->find($id);
        if (!$user) {
            throw $this->createNotFoundException();
        }

        $this->createOrUpdateUserFromRequest($user, $request);
        $this->em->flush();

        return $this->redirectToRoute('admin_client_show', ['id' => $id]);
    }

    #[Route('/clients/{id}/block', name: 'admin_client_block', methods: ['POST'])]
    public function blockClient(int $id): Response
    {
        $user = $this->em->getRepository(User::class)->find($id);
        if (!$user) {
            throw $this->createNotFoundException();
        }
        $user->setIsBlocked(!$user->isBlocked());
        $this->em->flush();
        $this->addFlash($user->isBlocked() ? 'warning' : 'success', $user->isBlocked() ? 'Клиент заблокирован — доступ в приложение запрещён.' : 'Доступ в приложение разблокирован.');

        return $this->redirectToRoute('admin_client_show', ['id' => $id]);
    }

    #[Route('/subscriptions/{id}/freeze', name: 'admin_subscription_freeze', methods: ['POST'])]
    public function freezeSubscription(int $id, Request $request): Response
    {
        $subscription = $this->em->getRepository(Subscription::class)->find($id);
        if (!$subscription) {
            $this->addFlash('danger', 'Абонемент не найден');

            return $this->redirectToRoute('admin_section', ['section' => 'subscriptions']);
        }

        $days = (int) $request->request->get('days', 0);
        $error = $this->freezeService->freeze($subscription, $days);
        if ($error !== null) {
            $this->addFlash('danger', $error);
        } else {
            $this->em->flush();
            $this->addFlash('success', 'Абонемент заморожен на ' . $days . ' дн.');
        }

        return $this->redirectAfterSubscriptionFreezeAction($subscription, $request);
    }

    #[Route('/subscriptions/{id}/unfreeze', name: 'admin_subscription_unfreeze', methods: ['POST'])]
    public function unfreezeSubscription(int $id, Request $request): Response
    {
        $subscription = $this->em->getRepository(Subscription::class)->find($id);
        if (!$subscription) {
            $this->addFlash('danger', 'Абонемент не найден');

            return $this->redirectToRoute('admin_section', ['section' => 'subscriptions']);
        }

        $error = $this->freezeService->unfreeze($subscription);
        if ($error !== null) {
            $this->addFlash('danger', $error);
        } else {
            $this->em->flush();
            $this->addFlash('success', 'Абонемент разморожен.');
        }

        return $this->redirectAfterSubscriptionFreezeAction($subscription, $request);
    }

    private function redirectAfterSubscriptionFreezeAction(Subscription $subscription, Request $request): Response
    {
        if ($request->request->get('return_to') === 'subscriptions') {
            $redirect = $this->redirectToRoute('admin_section', ['section' => 'subscriptions']);
            $statusFilter = $request->request->get('return_status', '');
            if ($statusFilter !== '') {
                $redirect->setTargetUrl($redirect->getTargetUrl() . '?status=' . urlencode((string) $statusFilter));
            }

            return $redirect;
        }

        return $this->redirectToRoute('admin_client_show', ['id' => $subscription->getUser()->getId()]);
    }

    #[Route('/bookings/{id}/cancel', name: 'admin_booking_cancel', methods: ['POST'])]
    public function cancelBooking(int $id, Request $request): Response
    {
        $booking = $this->em->getRepository(Booking::class)->find($id);
        if ($booking && in_array($booking->getStatus(), ['confirmed', 'waiting'], true)) {
            if ($booking->getStatus() === 'confirmed') {
                $training = $booking->getTraining();
                $training->setCurrentParticipants(max(0, $training->getCurrentParticipants() - 1));
                $this->em->persist($training);
            }
            $booking->setStatus('cancelled');
            $this->em->flush();
        }

        return $this->redirectToBookingsPreservingDate($request);
    }

    #[Route('/tasks/new', name: 'admin_task_new', methods: ['POST'])]
    public function createTask(Request $request): Response
    {
        $title = (string) $request->request->get('title');
        $clientName = $request->request->get('client_name') ?: null;
        $clientId = $request->request->get('client_id') ? (int) $request->request->get('client_id') : null;
        $type = $request->request->get('type', 'task');
        $status = 'open';

        $dueDate = $request->request->get('due_date');
        $dueTime = $request->request->get('due_time');
        $dueAt = null;
        if ($dueDate) {
            $dateTimeString = $dueDate . ($dueTime ? ' ' . $dueTime : ' 00:00');
            $dueAt = new \DateTimeImmutable($dateTimeString);
        }

        $task = (new Task())
            ->setTitle($title)
            ->setClientName($clientName)
            ->setType($type)
            ->setStatus($status)
            ->setDueAt($dueAt);

        if ($clientId) {
            $user = $this->em->getRepository(User::class)->find($clientId);
            if ($user) {
                $task->setClient($user)->setClientName($user->getName());
            }
        }

        $this->em->persist($task);
        $this->em->flush();

        $returnToClient = $request->request->get('return_to_client') ? (int) $request->request->get('return_to_client') : null;
        if ($returnToClient) {
            return $this->redirectToRoute('admin_client_show', ['id' => $returnToClient]);
        }
        return $this->redirectToRoute('admin_section', ['section' => 'tasks']);
    }

    #[Route('/tasks/{id}/complete', name: 'admin_task_complete', methods: ['POST'])]
    public function completeTask(int $id): Response
    {
        $task = $this->em->getRepository(Task::class)->find($id);
        if ($task) {
            $task->setStatus('done');
            $this->em->flush();
        }

        return $this->redirectToRoute('admin_section', ['section' => 'tasks']);
    }

    #[Route('/tasks/{id}/start', name: 'admin_task_start', methods: ['POST'])]
    public function startTask(int $id): Response
    {
        $task = $this->em->getRepository(Task::class)->find($id);
        if ($task && $task->getStatus() === 'open') {
            $task->setStatus('in_progress');
            $this->em->flush();
        }
        return $this->redirectToRoute('admin_section', ['section' => 'tasks']);
    }

    #[Route('/tasks/{id}/update', name: 'admin_task_update', methods: ['POST'])]
    public function updateTask(int $id, Request $request): Response
    {
        $task = $this->em->getRepository(Task::class)->find($id);
        if (!$task) {
            throw $this->createNotFoundException();
        }

        $task->setTitle((string) $request->request->get('title'));
        $task->setType($request->request->get('type', 'task'));
        $status = $request->request->get('status', $task->getStatus());
        if (in_array($status, ['open', 'in_progress', 'done'], true)) {
            $task->setStatus($status);
        }

        $clientId = $request->request->get('client_id') ? (int) $request->request->get('client_id') : null;
        $clientName = trim((string) $request->request->get('client_name'));
        if ($clientId) {
            $user = $this->em->getRepository(User::class)->find($clientId);
            if ($user) {
                $task->setClient($user)->setClientName($user->getName());
            }
        } else {
            $task->setClient(null)->setClientName($clientName ?: null);
        }

        $dueDate = $request->request->get('due_date');
        $dueTime = $request->request->get('due_time');
        $dueAt = null;
        if ($dueDate) {
            $dueAt = new \DateTimeImmutable($dueDate . ($dueTime ? ' ' . $dueTime : ' 00:00'));
        }
        $task->setDueAt($dueAt);

        $this->em->flush();

        $cid = $task->getClient()?->getId();
        $redirect = $this->redirectToRoute('admin_section', ['section' => 'tasks']);
        if ($cid) {
            $redirect->setTargetUrl($redirect->getTargetUrl() . '?client_id=' . $cid);
        }
        return $redirect;
    }

    #[Route('/tasks/{id}/delete', name: 'admin_task_delete', methods: ['POST'])]
    public function deleteTask(int $id): Response
    {
        $task = $this->em->getRepository(Task::class)->find($id);
        if ($task) {
            $this->em->remove($task);
            $this->em->flush();
        }
        return $this->redirectToRoute('admin_section', ['section' => 'tasks']);
    }

    #[Route('/sales/new', name: 'admin_sale_new', methods: ['POST'])]
    public function createSale(Request $request): Response
    {
        $userId = $request->request->get('user_id');
        $clientName = trim((string) $request->request->get('client_name'));
        $productName = (string) $request->request->get('product_name');
        $quantity = max(1, (int) $request->request->get('quantity', 1));
        $price = (float) $request->request->get('price');
        $paymentMethod = $request->request->get('payment_method', 'cash');
        $promoCodeRaw = trim((string) $request->request->get('promo_code'));

        $total = $price * $quantity;
        $discountAmount = 0.0;
        $promo = null;

        if ($promoCodeRaw !== '') {
            $promo = $this->em->getRepository(PromoCode::class)->findOneBy(['code' => strtoupper($promoCodeRaw)]);
            if ($promo && $promo->isValid()) {
                if ($promo->getDiscountPercent() !== null) {
                    $discountAmount = round($total * $promo->getDiscountPercent() / 100, 2);
                } elseif ($promo->getDiscountAmount() !== null) {
                    $discountAmount = min($promo->getDiscountAmount(), $total);
                }
                $total = max(0, $total - $discountAmount);
                $promo->incrementUsedCount();
            } else {
                $promo = null;
            }
        }

        $user = null;
        if ($userId) {
            $user = $this->em->getRepository(User::class)->find((int) $userId);
            if ($user) {
                $clientName = $user->getName();
            }
        }
        if ($clientName === '') {
            return $this->redirectToRoute('admin_section', ['section' => 'sales']);
        }

        $sale = (new Sale())
            ->setClientName($clientName)
            ->setProductName($productName)
            ->setQuantity($quantity)
            ->setPrice($price)
            ->setTotal($total)
            ->setPaymentMethod($paymentMethod)
            ->setDiscountAmount($discountAmount)
            ->setPromoCode($promo);
        if ($user) {
            $sale->setUser($user);
        }

        $this->em->persist($sale);
        $this->em->flush();

        return $this->redirectToRoute('admin_section', ['section' => 'sales']);
    }

    #[Route('/trainings/new', name: 'admin_training_new', methods: ['POST'])]
    public function createTraining(Request $request): Response
    {
        $name = (string) $request->request->get('name');
        $trainerId = $request->request->get('trainer_id');
        $room = $request->request->get('room') ?: null;
        $maxParticipants = (int) $request->request->get('max_participants', 0);

        $startAtRaw = $request->request->get('start_at');
        $endAtRaw = $request->request->get('end_at');

        // HTML datetime-local: Y-m-d\TH:i
        $startAt = $startAtRaw ? new \DateTimeImmutable($startAtRaw) : null;
        $endAt = $endAtRaw ? new \DateTimeImmutable($endAtRaw) : null;

        if (!$startAt || !$endAt) {
            return $this->redirectToSchedulePreservingDate($request);
        }

        $repeatWeeks = min(12, max(1, (int) $request->request->get('repeat_weeks', 1)));
        $trainingType = (string) $request->request->get('training_type', 'group');
        if (!in_array($trainingType, ['group', 'personal', 'extra'], true)) {
            $trainingType = 'group';
        }
        if ($trainingType === 'personal') {
            $maxParticipants = 1;
        }

        $trainer = null;
        if ($trainerId) {
            $trainer = $this->em->getRepository(Trainer::class)->find((int) $trainerId);
        }

        for ($w = 0; $w < $repeatWeeks; $w++) {
            $weekStart = $startAt->modify('+' . $w . ' weeks');
            $weekEnd = $endAt->modify('+' . $w . ' weeks');

            $training = (new Training())
                ->setName($name)
                ->setDescription(match ($trainingType) {
                    'personal' => 'Персональное занятие',
                    'extra' => 'Дополнительная услуга',
                    default => 'Групповое занятие',
                })
                ->setType($trainingType)
                ->setRoom($room)
                ->setStartAt($weekStart)
                ->setEndAt($weekEnd)
                ->setMaxParticipants($maxParticipants)
                ->setCurrentParticipants(0);

            if ($trainer) {
                $training->setTrainer($trainer)->setTrainerName($trainer->getName());
            }

            $this->em->persist($training);
        }
        $this->em->flush();
        $this->addFlash('success', $repeatWeeks > 1 ? "Добавлено $repeatWeeks тренировок." : 'Тренировка добавлена.');

        return $this->redirectToSchedulePreservingDate($request);
    }

    #[Route('/trainings/{id}/update', name: 'admin_training_update', requirements: ['id' => '\d+'], methods: ['POST'])]
    public function updateTraining(int $id, Request $request): Response
    {
        $training = $this->em->getRepository(Training::class)->find($id);
        if (!$training) {
            throw $this->createNotFoundException();
        }

        $name = trim((string) $request->request->get('name'));
        $trainerId = $request->request->get('trainer_id');
        $room = $request->request->get('room') ?: null;
        $maxParticipants = (int) $request->request->get('max_participants', $training->getMaxParticipants());

        $trainingType = (string) $request->request->get('training_type', $training->getType());
        if (!in_array($trainingType, ['group', 'personal', 'extra'], true)) {
            $trainingType = $training->getType();
        }
        if ($trainingType === 'personal') {
            $maxParticipants = 1;
        }

        $startAtRaw = $request->request->get('start_at');
        $endAtRaw = $request->request->get('end_at');
        $startAt = $startAtRaw ? new \DateTimeImmutable((string) $startAtRaw) : $training->getStartAt();
        $endAt = $endAtRaw ? new \DateTimeImmutable((string) $endAtRaw) : $training->getEndAt();

        if ($endAt <= $startAt) {
            $this->addFlash('warning', 'Время окончания должно быть позже начала.');

            return $this->redirectToSchedulePreservingDate($request);
        }

        if ($name !== '') {
            $training->setName($name);
        }
        $prevType = $training->getType();
        $training->setType($trainingType);
        if ($prevType !== $trainingType) {
            $training->setDescription(match ($trainingType) {
                'personal' => 'Персональное занятие',
                'extra' => 'Дополнительная услуга',
                default => 'Групповое занятие',
            });
        }
        $training->setRoom($room);
        $maxParticipants = max(0, $maxParticipants);
        if ($maxParticipants < $training->getCurrentParticipants()) {
            $this->addFlash('warning', 'Лимит мест меньше числа записанных. Увеличьте лимит или отмените записи.');

            return $this->redirectToSchedulePreservingDate($request);
        }
        $training->setMaxParticipants($maxParticipants);
        $training->setStartAt($startAt)->setEndAt($endAt);

        $trainer = null;
        if ($trainerId !== null && $trainerId !== '') {
            $trainer = $this->em->getRepository(Trainer::class)->find((int) $trainerId);
        }
        if ($trainer) {
            $training->setTrainer($trainer)->setTrainerName($trainer->getName());
        } else {
            $training->setTrainer(null)->setTrainerName(null);
        }

        $this->em->flush();
        $this->addFlash('success', 'Тренировка обновлена (перенесена).');

        return $this->redirectToSchedulePreservingDate($request);
    }

    #[Route('/trainers/new', name: 'admin_trainer_new', methods: ['POST'])]
    public function createStaff(Request $request): Response
    {
        $name = (string) $request->request->get('name');
        $specialization = $request->request->get('specialization') ?: null;
        $photoUrl = $request->request->get('photo_url') ?: null;
        $ratingRaw = $request->request->get('rating');
        $rating = $ratingRaw !== null && $ratingRaw !== '' ? (float) $ratingRaw : null;

        $description = $request->request->get('description');
        $description = is_string($description) ? trim($description) : '';
        $description = $description !== '' ? $description : null;

        $trainer = (new Trainer())
            ->setName($name)
            ->setSpecialization($specialization)
            ->setPhotoUrl($photoUrl)
            ->setRating($rating)
            ->setDescription($description);

        $this->em->persist($trainer);
        $this->em->flush();

        return $this->redirectToRoute('admin_section', ['section' => 'trainers']);
    }

    #[Route('/products/{id}/toggle', name: 'admin_product_toggle', methods: ['POST'])]
    public function toggleProduct(int $id): Response
    {
        $product = $this->em->getRepository(Product::class)->find($id);
        if ($product) {
            $product->setIsActive(!$product->isActive());
            $this->em->flush();
        }

        return $this->redirectToRoute('admin_section', ['section' => 'services']);
    }

    #[Route('/products/{id}/stock', name: 'admin_product_stock', methods: ['POST'])]
    public function updateProductStock(int $id, Request $request): Response
    {
        $product = $this->em->getRepository(Product::class)->find($id);
        if ($product) {
            $qty = $request->request->get('quantity');
            $product->setQuantity($qty !== '' && $qty !== null ? (int) $qty : null);
            $this->em->flush();
        }

        return $this->redirectToRoute('admin_section', ['section' => 'warehouse']);
    }

    #[Route('/trainers/{id}/delete', name: 'admin_trainer_delete', methods: ['POST'])]
    public function deleteTrainer(int $id): Response
    {
        $trainer = $this->em->getRepository(Trainer::class)->find($id);
        if ($trainer) {
            $count = $this->em->getRepository(Training::class)->count(['trainer' => $trainer]);
            if ($count === 0) {
                $this->em->remove($trainer);
                $this->em->flush();
            }
        }

        return $this->redirectToRoute('admin_section', ['section' => 'trainers']);
    }

    #[Route('/trainers/{id}/update', name: 'admin_trainer_update', methods: ['POST'])]
    public function updateTrainer(int $id, Request $request): Response
    {
        $trainer = $this->em->getRepository(Trainer::class)->find($id);
        if (!$trainer) {
            throw $this->createNotFoundException();
        }
        $description = $request->request->get('description');
        $description = is_string($description) ? trim($description) : '';
        $description = $description !== '' ? $description : null;

        $trainer->setName((string) $request->request->get('name'))
            ->setSpecialization($request->request->get('specialization') ?: null)
            ->setPhotoUrl($request->request->get('photo_url') ?: null)
            ->setDescription($description);
        $ratingRaw = $request->request->get('rating');
        $trainer->setRating($ratingRaw !== '' && $ratingRaw !== null ? (float) $ratingRaw : null);
        $this->em->flush();
        $this->addFlash('success', 'Тренер обновлён.');

        return $this->redirectToRoute('admin_section', ['section' => 'trainers']);
    }

    #[Route('/trainings/{id}/delete', name: 'admin_training_delete', methods: ['POST'])]
    public function deleteTraining(int $id, Request $request): Response
    {
        $training = $this->em->getRepository(Training::class)->find($id);
        if ($training && $training->getCurrentParticipants() === 0) {
            $this->em->remove($training);
            $this->em->flush();
        }

        return $this->redirectToSchedulePreservingDate($request);
    }

    #[Route('/subscriptions/issue', name: 'admin_subscription_issue', methods: ['POST'])]
    public function issueSubscription(Request $request): Response
    {
        $userId = (int) $request->request->get('user_id');
        $planId = (int) $request->request->get('plan_id');

        if (!$userId || !$planId) {
            return $this->redirectToRoute('admin_section', ['section' => 'subscriptions']);
        }

        $user = $this->em->getRepository(User::class)->find($userId);
        $plan = $this->em->getRepository(SubscriptionPlan::class)->find($planId);

        if (!$user || !$plan) {
            return $this->redirectToRoute('admin_section', ['section' => 'subscriptions']);
        }

        $clubRepo = $this->em->getRepository(Club::class);
        $clubCount = (int) $clubRepo->count([]);
        $clubId = (int) $request->request->get('club_id');
        /** @var Club|null $issueClub */
        $issueClub = $clubId > 0 ? $clubRepo->find($clubId) : null;
        if ($issueClub === null && $clubCount === 1) {
            $issueClub = $clubRepo->findOneBy([]);
        }
        if ($clubCount >= 2 && !$issueClub instanceof Club) {
            $this->addFlash('danger', 'В CRM несколько клубов: при выдаче абонемента обязательно выберите клуб (тот же, что у шлюза с gateway_token).');

            return $this->redirectToRoute('admin_section', ['section' => 'subscriptions']);
        }

        $returnStatus = $request->request->get('return_status', '');
        if (!$user->hasRequiredDataForSubscription()) {
            $this->addFlash('warning', 'Заполните паспортные данные и дату рождения клиента перед выдачей абонемента.');
            return $this->redirectToRoute('admin_client_show', ['id' => $user->getId()]);
        }

        $startDateRaw = $request->request->get('start_date');
        $startDate = $startDateRaw ? new \DateTimeImmutable($startDateRaw) : new \DateTimeImmutable();

        $visitsTotalRaw = $request->request->get('visits_total');
        $freezeDaysTotalRaw = $request->request->get('freeze_days_total');

        $subscription = (new Subscription())
            ->setUser($user)
            ->setPlan($plan)
            ->setStartDate($startDate)
            ->setStatus('active')
            ->setVisitsTotal(
                $visitsTotalRaw !== null && $visitsTotalRaw !== ''
                    ? (int) $visitsTotalRaw
                    : $plan->getVisitsCount()
            )
            ->setVisitsUsed(0)
            ->setFreezeDaysTotal(
                $freezeDaysTotalRaw !== null && $freezeDaysTotalRaw !== ''
                    ? (int) $freezeDaysTotalRaw
                    : $this->freezePolicy->freezeDaysTotalForPlan($plan)
            )
            ->setFreezeDaysUsed(0)
            ->setClub($issueClub);

        if ($plan->getDurationDays() !== null) {
            $subscription->setEndDate(
                $startDate->modify('+' . $plan->getDurationDays() . ' days')
            );
        }

        $this->em->persist($subscription);
        $this->em->flush();

        // Создаём продажу для связи Sale ↔ Subscription
        $sale = (new Sale())
            ->setUser($user)
            ->setClientName($user->getName())
            ->setProductName('Абонемент: ' . $plan->getName())
            ->setQuantity(1)
            ->setPrice($plan->getPrice())
            ->setTotal($plan->getPrice())
            ->setSubscription($subscription);
        $this->em->persist($sale);
        $this->em->flush();

        $this->addFlash('success', 'Абонемент выдан.');
        $redirect = $this->redirectToRoute('admin_section', ['section' => 'subscriptions']);
        if ($returnStatus !== '') {
            $redirect->setTargetUrl($redirect->getTargetUrl() . '?status=' . urlencode($returnStatus));
        }
        return $redirect;
    }

    #[Route('/plans/{id}/update', name: 'admin_plan_update', methods: ['POST'])]
    public function updatePlan(int $id, Request $request): Response
    {
        $plan = $this->em->getRepository(SubscriptionPlan::class)->find($id);
        if (!$plan) {
            throw $this->createNotFoundException();
        }

        $plan->setPrice((float) $request->request->get('price'))
            ->setIsPopular((bool) $request->request->get('is_popular', false));

        $this->em->flush();
        $this->addFlash('success', 'Тарифный план обновлён.');

        return $this->redirectToRoute('admin_section', ['section' => 'subscriptions']);
    }

    #[Route('/plans/{id}/delete', name: 'admin_plan_delete', methods: ['POST'])]
    public function deletePlan(int $id): Response
    {
        $plan = $this->em->getRepository(SubscriptionPlan::class)->find($id);
        if ($plan) {
            $count = $this->em->getRepository(Subscription::class)->count(['plan' => $plan]);
            if ($count === 0) {
                $this->em->remove($plan);
                $this->em->flush();
                $this->addFlash('success', 'Тарифный план удалён.');
            } else {
                $this->addFlash('warning', 'Нельзя удалить план: по нему выданы абонементы.');
            }
        }

        return $this->redirectToRoute('admin_section', ['section' => 'subscriptions']);
    }

    #[Route('/products/{id}/update', name: 'admin_product_update', methods: ['POST'])]
    public function updateProduct(int $id, Request $request): Response
    {
        $product = $this->em->getRepository(Product::class)->find($id);
        if (!$product) {
            throw $this->createNotFoundException();
        }

        $newCategory = (string) $request->request->get('category', 'service');
        $product->setName((string) $request->request->get('name'))
            ->setDescription($request->request->get('description') ?: null)
            ->setPrice((float) $request->request->get('price'))
            ->setCategory($newCategory);

        $qty = $request->request->get('quantity');
        if ($newCategory === 'goods' && $qty !== null && $qty !== '') {
            $product->setQuantity((int) $qty);
        } elseif ($newCategory === 'service') {
            $product->setQuantity(null);
        }

        $this->em->flush();
        $this->addFlash('success', 'Товар/услуга обновлены.');

        return $this->redirectToRoute('admin_section', ['section' => 'services']);
    }

    #[Route('/settings/club', name: 'admin_settings_club', methods: ['GET', 'POST'])]
    public function settingsClub(Request $request): Response
    {
        $menu = $this->buildMenu();

        $getSetting = function (string $key, string $default = ''): string {
            $s = $this->em->getRepository(ClubSetting::class)->find($key);
            return $s?->getSettingValue() ?? $default;
        };

        if ($request->isMethod('POST')) {
            $keys = ['name', 'address', 'phone', 'email', 'working_hours', 'amenities', 'latitude', 'longitude', 'promo_home_title', 'promo_home_subtitle'];
            foreach ($keys as $key) {
                $value = trim((string) ($request->request->get($key) ?? ''));
                $setting = $this->em->getRepository(ClubSetting::class)->find($key);
                if (!$setting) {
                    $setting = new ClubSetting();
                    $setting->setSettingKey($key);
                }
                $setting->setSettingValue($value ?: null);
                $this->em->persist($setting);
            }

            $percoEnabled = $request->request->get('perco_enabled') === '1' ? '1' : '0';
            $this->persistSetting('perco_enabled', $percoEnabled);
            $this->persistSetting('perco_base_url', trim((string) $request->request->get('perco_base_url', '')) ?: null);
            $this->persistSetting('perco_login', trim((string) $request->request->get('perco_login', '')) ?: null);
            $percoPass = (string) $request->request->get('perco_password', '');
            if ($percoPass !== '') {
                $this->persistSetting('perco_password', $percoPass);
            }
            $percoVerify = $request->request->get('perco_verify_ssl') === '1' ? '1' : '0';
            $this->persistSetting('perco_verify_ssl', $percoVerify);
            $this->persistSetting('perco_entry_device_id', trim((string) $request->request->get('perco_entry_device_id', '')) ?: null);
            $this->persistSetting('perco_cmd_number', trim((string) $request->request->get('perco_cmd_number', '')) ?: null);
            $this->persistSetting('perco_cmd_type', trim((string) $request->request->get('perco_cmd_type', '')) ?: null);
            $this->persistSetting('perco_cmd_value', trim((string) $request->request->get('perco_cmd_value', '')) ?: null);
            $this->persistSetting('perco_cmd_param', trim((string) $request->request->get('perco_cmd_param', '')) ?: null);

            $this->em->flush();
            $this->addFlash('success', 'Настройки клуба сохранены.');

            return $this->redirectToRoute('admin_settings_club');
        }

        return $this->render('admin/settings_club.html.twig', [
            'menu' => $menu,
            'current' => 'settings',
            'club' => [
                'name' => $getSetting('name', 'FitnessClub'),
                'address' => $getSetting('address', 'г. Москва, ул. Примерная, д. 1'),
                'phone' => $getSetting('phone', '+7 (495) 123-45-67'),
                'email' => $getSetting('email', 'info@fitnessclub.ru'),
                'working_hours' => $getSetting('working_hours', 'Пн-Пт: 7:00–23:00, Сб-Вс: 9:00–21:00'),
                'amenities' => $getSetting('amenities', 'Тренажёрный зал, Бассейн, Йога'),
                'latitude' => $getSetting('latitude', '55.7558'),
                'longitude' => $getSetting('longitude', '37.6173'),
                'promo_home_title' => $getSetting('promo_home_title', 'СКИДКА 20%!'),
                'promo_home_subtitle' => $getSetting('promo_home_subtitle', 'на все карты 12 и 6 месяцев'),
                'perco_enabled' => $getSetting('perco_enabled', '0'),
                'perco_base_url' => $getSetting('perco_base_url', ''),
                'perco_login' => $getSetting('perco_login', ''),
                'perco_verify_ssl' => $getSetting('perco_verify_ssl', '1'),
                'perco_entry_device_id' => $getSetting('perco_entry_device_id', ''),
                'perco_cmd_number' => $getSetting('perco_cmd_number', ''),
                'perco_cmd_type' => $getSetting('perco_cmd_type', ''),
                'perco_cmd_value' => $getSetting('perco_cmd_value', ''),
                'perco_cmd_param' => $getSetting('perco_cmd_param', ''),
            ],
        ]);
    }

    public function testPercoConnection(): Response
    {
        if (!$this->percoWebClient->isConfigured()) {
            $this->addFlash('warning', 'Заполните и сохраните настройки PERCo-Web, включите интеграцию.');

            return $this->redirectToRoute('admin_settings_club');
        }
        try {
            $this->percoWebClient->testAuthentication();
            $this->addFlash('success', 'PERCo-Web: авторизация успешна (токен получен).');
        } catch (\Throwable $e) {
            $this->addFlash('danger', 'PERCo-Web: ошибка — ' . $e->getMessage());
        }

        return $this->redirectToRoute('admin_settings_club');
    }

    private function persistSetting(string $key, ?string $value): void
    {
        $setting = $this->em->getRepository(ClubSetting::class)->find($key);
        if (!$setting) {
            $setting = new ClubSetting();
            $setting->setSettingKey($key);
        }
        $setting->setSettingValue($value);
        $this->em->persist($setting);
    }

    #[Route('/documents/upload', name: 'admin_document_upload', methods: ['POST'])]
    public function uploadDocument(Request $request): Response
    {
        $file = $request->files->get('file');
        $name = trim((string) $request->request->get('name', ''));
        $category = trim((string) $request->request->get('category', '')) ?: null;

        if (!$file || !$file->isValid() || $name === '') {
            return $this->redirectToRoute('admin_section', ['section' => 'documents']);
        }

        $ext = strtolower($file->getClientOriginalExtension());
        if (!in_array($ext, ['pdf'], true)) {
            $this->addFlash('warning', 'Допустим только формат PDF.');
            return $this->redirectToRoute('admin_section', ['section' => 'documents']);
        }

        $uploadDir = $this->getParameter('kernel.project_dir') . '/var/uploads/documents';
        if (!is_dir($uploadDir)) {
            mkdir($uploadDir, 0755, true);
        }
        $filename = bin2hex(random_bytes(8)) . '_' . preg_replace('/[^a-zA-Z0-9._-]/', '_', $file->getClientOriginalName());
        $file->move($uploadDir, $filename);

        $doc = (new Document())
            ->setName($name)
            ->setFilename($filename)
            ->setCategory($category);
        $this->em->persist($doc);
        $this->em->flush();
        $this->addFlash('success', 'Документ загружен.');

        return $this->redirectToRoute('admin_section', ['section' => 'documents']);
    }

    #[Route('/documents/{id}/delete', name: 'admin_document_delete', methods: ['POST'])]
    public function deleteDocument(int $id): Response
    {
        $doc = $this->em->getRepository(Document::class)->find($id);
        if ($doc) {
            $path = $this->getParameter('kernel.project_dir') . '/var/uploads/documents/' . $doc->getFilename();
            if (file_exists($path)) {
                unlink($path);
            }
            $this->em->remove($doc);
            $this->em->flush();
            $this->addFlash('success', 'Документ удалён.');
        }
        return $this->redirectToRoute('admin_section', ['section' => 'documents']);
    }

    #[Route('/documents/{id}/download', name: 'admin_document_download', methods: ['GET'])]
    public function downloadDocument(int $id): Response
    {
        $doc = $this->em->getRepository(Document::class)->find($id);
        if (!$doc) {
            throw $this->createNotFoundException();
        }
        $path = $this->getParameter('kernel.project_dir') . '/var/uploads/documents/' . $doc->getFilename();
        if (!file_exists($path)) {
            throw $this->createNotFoundException('Файл не найден.');
        }
        return $this->file($path, $doc->getName() . '.pdf');
    }

    #[Route('/promocodes/{id}/delete', name: 'admin_promo_delete', methods: ['POST'])]
    public function deletePromoCode(int $id): Response
    {
        $promo = $this->em->getRepository(PromoCode::class)->find($id);
        if ($promo) {
            $this->em->remove($promo);
            $this->em->flush();
            $this->addFlash('success', 'Промокод удалён.');
        }
        return $this->redirectToRoute('admin_section', ['section' => 'promocodes']);
    }

    #[Route('/promocodes/{id}/toggle', name: 'admin_promo_toggle', methods: ['POST'])]
    public function togglePromoCode(int $id): Response
    {
        $promo = $this->em->getRepository(PromoCode::class)->find($id);
        if ($promo) {
            $promo->setIsActive(!$promo->isActive());
            $this->em->flush();
        }
        return $this->redirectToRoute('admin_section', ['section' => 'promocodes']);
    }

    #[Route('/promocodes/{id}/update', name: 'admin_promo_update', methods: ['POST'])]
    public function updatePromoCode(int $id, Request $request): Response
    {
        $promo = $this->em->getRepository(PromoCode::class)->find($id);
        if (!$promo) {
            throw $this->createNotFoundException();
        }
        $code = strtoupper(trim((string) $request->request->get('code')));
        $discountPercent = $request->request->get('discount_percent') !== '' ? (float) $request->request->get('discount_percent') : null;
        $discountAmount = $request->request->get('discount_amount') !== '' ? (float) $request->request->get('discount_amount') : null;
        if ($code !== '' && ($discountPercent !== null || $discountAmount !== null)) {
            $promo->setCode($code)
                ->setDiscountPercent($discountPercent)
                ->setDiscountAmount($discountAmount)
                ->setValidFrom($request->request->get('valid_from') ? new \DateTimeImmutable($request->request->get('valid_from')) : null)
                ->setValidTo($request->request->get('valid_to') ? new \DateTimeImmutable($request->request->get('valid_to')) : null)
                ->setUsageLimit($request->request->get('usage_limit') !== '' ? (int) $request->request->get('usage_limit') : null);
            $this->em->flush();
            $this->addFlash('success', 'Промокод обновлён.');
        }
        return $this->redirectToRoute('admin_section', ['section' => 'promocodes']);
    }

    #[Route('/promotions/{id}/update', name: 'admin_promotion_update', methods: ['POST'])]
    public function updatePromotion(int $id, Request $request): Response
    {
        $promotion = $this->em->getRepository(Promotion::class)->find($id);
        if (!$promotion) {
            throw $this->createNotFoundException();
        }

        $promotion
            ->setTitle(trim((string) $request->request->get('title', $promotion->getTitle())))
            ->setSubtitle(($request->request->get('subtitle') !== null && trim((string) $request->request->get('subtitle')) !== '') ? trim((string) $request->request->get('subtitle')) : null)
            ->setButtonText(trim((string) $request->request->get('button_text', 'Подробнее')) ?: 'Подробнее')
            ->setActionType((string) $request->request->get('action_type', 'shop'))
            ->setActionValue(($request->request->get('action_value') !== null && trim((string) $request->request->get('action_value')) !== '') ? trim((string) $request->request->get('action_value')) : null)
            ->setBgFrom($this->normalizeHexColor((string) $request->request->get('bg_from', $promotion->getBgFrom())))
            ->setBgTo($this->normalizeHexColor((string) $request->request->get('bg_to', $promotion->getBgTo())))
            ->setSortOrder((int) $request->request->get('sort_order', 100))
            ->setIsActive($request->request->get('is_active') === '1');

        $this->applyPromotionImageUpload($request, $promotion);

        $this->em->flush();
        $this->addFlash('success', 'Акция обновлена.');

        return $this->redirectToRoute('admin_section', ['section' => 'promotions']);
    }

    #[Route('/promotions/{id}/toggle', name: 'admin_promotion_toggle', methods: ['POST'])]
    public function togglePromotion(int $id): Response
    {
        $promotion = $this->em->getRepository(Promotion::class)->find($id);
        if ($promotion) {
            $promotion->setIsActive(!$promotion->isActive());
            $this->em->flush();
        }

        return $this->redirectToRoute('admin_section', ['section' => 'promotions']);
    }

    #[Route('/promotions/{id}/delete', name: 'admin_promotion_delete', methods: ['POST'])]
    public function deletePromotion(int $id): Response
    {
        $promotion = $this->em->getRepository(Promotion::class)->find($id);
        if ($promotion) {
            $this->em->remove($promotion);
            $this->em->flush();
            $this->addFlash('success', 'Акция удалена.');
        }

        return $this->redirectToRoute('admin_section', ['section' => 'promotions']);
    }

    #[Route('/clients/export', name: 'admin_clients_export', methods: ['GET'])]
    public function exportClients(Request $request): Response
    {
        if (!$this->passportAccess->canExportPassportDetails($this->getUser())) {
            throw $this->createAccessDeniedException('Экспорт паспортных данных доступен только администратору.');
        }

        $clients = $this->buildFilteredClientsList($request);
        $format = strtolower((string) $request->query->get('format', 'csv'));
        if ($format === 'xlsx') {
            return $this->exportClientsAsXlsx($clients, $request);
        }

        $response = new StreamedResponse(function () use ($clients) {
            $handle = fopen('php://output', 'w');
            fprintf($handle, chr(0xEF) . chr(0xBB) . chr(0xBF));
            fputcsv($handle, ['ID', 'Имя', 'Email', 'Телефон', 'Клуб', 'Адрес клуба', 'Дата рождения', 'Паспорт', 'Бонусы', 'Теги', 'Создан'], ';');
            foreach ($clients as $u) {
                $passport = ($u->getPassportSeries() && $u->getPassportNumber()) ? $u->getPassportSeries() . ' ' . $u->getPassportNumber() : '—';
                $club = $u->getClub();
                fputcsv($handle, [
                    $u->getId(),
                    $u->getName(),
                    $u->getEmail() ?? '',
                    $u->getPhone() ?? '',
                    $club ? $club->getName() : '—',
                    $club ? $club->getAddress() : '—',
                    $u->getDateOfBirth()?->format('d.m.Y') ?? '—',
                    $passport,
                    $u->getBonusPoints(),
                    $this->clientTagNamesJoined($u),
                    $u->getCreatedAt()->format('d.m.Y H:i'),
                ], ';');
            }
            fclose($handle);
        });
        $response->headers->set('Content-Type', 'text/csv; charset=UTF-8');
        $response->headers->set('Content-Disposition', 'attachment; filename="clients_' . date('Y-m-d_H-i') . '.csv"');
        return $response;
    }

    private function exportClientsAsXlsx(array $clients, Request $request): StreamedResponse
    {
        $clubId = $request->query->get('club_id') ? (int) $request->query->get('club_id') : 0;
        $suffix = '';
        if ($clubId > 0) {
            $c = $this->em->getRepository(Club::class)->find($clubId);
            if ($c instanceof Club) {
                $suffix = '_club' . $clubId;
            }
        }
        $filename = 'clients' . $suffix . '_' . date('Y-m-d_H-i') . '.xlsx';

        $response = new StreamedResponse(function () use ($clients) {
            $spreadsheet = new Spreadsheet();
            $sheet = $spreadsheet->getActiveSheet();
            $sheet->setTitle('Клиенты');
            $headers = [
                'ID', 'ФИО', 'Email', 'Телефон', 'Клуб', 'Адрес клуба', 'Дата рождения', 'Место рождения', 'Пол',
                'Серия паспорта', 'Номер паспорта', 'Кем выдан', 'Дата выдачи', 'Код подразделения',
                'Адрес регистрации', 'Контакт для экстренной связи', 'Бонусы', 'Теги',
                'Верификация паспорта', 'Создан',
            ];
            $sheet->fromArray($headers, null, 'A1');
            $row = 2;
            foreach ($clients as $u) {
                $club = $u->getClub();
                $sheet->fromArray([
                    $u->getId(),
                    $u->getName(),
                    $u->getEmail(),
                    $u->getPhone(),
                    $club ? $club->getName() : '—',
                    $club ? $club->getAddress() : '—',
                    $u->getDateOfBirth()?->format('d.m.Y') ?? '—',
                    $u->getPlaceOfBirth() ?? '—',
                    $u->getGender() ?? '—',
                    $u->getPassportSeries() ?? '—',
                    $u->getPassportNumber() ?? '—',
                    $u->getPassportIssuedBy() ?? '—',
                    $u->getPassportIssueDate()?->format('d.m.Y') ?? '—',
                    $u->getPassportDepartmentCode() ?? '—',
                    $u->getRegistrationAddress() ?? '—',
                    $u->getEmergencyContact() ?? '—',
                    $u->getBonusPoints(),
                    $this->clientTagNamesJoined($u),
                    $u->getPassportVerificationStatus(),
                    $u->getCreatedAt()->format('d.m.Y H:i'),
                ], null, 'A' . $row);
                ++$row;
            }
            foreach (range('A', 'T') as $col) {
                $sheet->getColumnDimension($col)->setAutoSize(true);
            }
            $writer = new Xlsx($spreadsheet);
            $writer->save('php://output');
        });
        $response->headers->set('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
        $response->headers->set('Content-Disposition', 'attachment; filename="' . $filename . '"');

        return $response;
    }

    #[Route('/visits/export', name: 'admin_visits_export', methods: ['GET'])]
    public function exportVisits(Request $request): StreamedResponse
    {
        $period = $this->visitPeriodResolver->resolve($request->query->all());
        $qb = $this->em->createQueryBuilder()
            ->select('a')
            ->from(AccessLog::class, 'a')
            ->leftJoin('a.club', 'c')
            ->addSelect('c')
            ->where('a.result = :result')
            ->andWhere('a.eventType = :eventType')
            ->andWhere('a.createdAt >= :from')
            ->andWhere('a.createdAt < :to')
            ->setParameter('result', 'granted')
            ->setParameter('eventType', 'entry')
            ->setParameter('from', $period->from)
            ->setParameter('to', $period->toExclusive)
            ->orderBy('a.createdAt', 'DESC');
        $visits = $qb->getQuery()->getResult();

        $response = new StreamedResponse(function () use ($visits) {
            $handle = fopen('php://output', 'w');
            fprintf($handle, chr(0xEF) . chr(0xBB) . chr(0xBF));
            fputcsv($handle, ['ID', 'Клиент', 'Телефон', 'Клуб', 'Устройство', 'Дата и время'], ';');
            foreach ($visits as $v) {
                $user = $v->getUser();
                $club = $v->getClub();
                fputcsv($handle, [
                    $v->getId(),
                    $user ? $user->getName() : '—',
                    $user ? $user->getPhone() : '—',
                    $club ? $club->getName() : '—',
                    $v->getDeviceId() ?? '—',
                    $v->getCreatedAt()->format('d.m.Y H:i:s'),
                ], ';');
            }
            fclose($handle);
        });
        $response->headers->set('Content-Type', 'text/csv; charset=UTF-8');
        $response->headers->set('Content-Disposition', 'attachment; filename="visits_' . $period->dateFromYmd() . '_' . $period->dateToYmdInclusive() . '.csv"');

        return $response;
    }

    #[Route('/selfservice/export', name: 'admin_selfservice_export', methods: ['GET'])]
    public function exportSelfservice(Request $request): StreamedResponse
    {
        $dateFrom = $request->query->get('date_from') ? new \DateTimeImmutable($request->query->get('date_from')) : (new \DateTimeImmutable('today'))->modify('-7 days');
        $dateToRaw = $request->query->get('date_to') ?: (new \DateTimeImmutable('today'))->format('Y-m-d');
        $dateTo = (new \DateTimeImmutable($dateToRaw))->modify('+1 day');
        $resultFilter = $request->query->get('result', '');
        $qb = $this->em->createQueryBuilder()
            ->select('a')
            ->from(AccessLog::class, 'a')
            ->where('a.createdAt >= :from')
            ->andWhere('a.createdAt < :to')
            ->setParameter('from', $dateFrom)
            ->setParameter('to', $dateTo)
            ->orderBy('a.createdAt', 'DESC');
        if ($resultFilter === 'granted' || $resultFilter === 'denied') {
            $qb->andWhere('a.result = :result')->setParameter('result', $resultFilter);
        }
        $logs = $qb->getQuery()->getResult();

        $response = new StreamedResponse(function () use ($logs) {
            $handle = fopen('php://output', 'w');
            fprintf($handle, chr(0xEF) . chr(0xBB) . chr(0xBF));
            fputcsv($handle, ['ID', 'Дата', 'Клиент', 'Телефон', 'Устройство', 'Результат', 'Причина'], ';');
            foreach ($logs as $v) {
                $user = $v->getUser();
                fputcsv($handle, [
                    $v->getId(),
                    $v->getCreatedAt()->format('d.m.Y H:i:s'),
                    $user ? $user->getName() : '—',
                    $user ? $user->getPhone() : '—',
                    $v->getDeviceId() ?? '—',
                    $v->getResult() === 'granted' ? 'Разрешён' : 'Отклонён',
                    $v->getReason() ?? '—',
                ], ';');
            }
            fclose($handle);
        });
        $response->headers->set('Content-Type', 'text/csv; charset=UTF-8');
        $response->headers->set('Content-Disposition', 'attachment; filename="selfservice_' . $dateFrom->format('Y-m-d') . '_' . $dateToRaw . '.csv"');

        return $response;
    }

    #[Route('/sales/export', name: 'admin_sales_export', methods: ['GET'])]
    public function exportSales(Request $request): StreamedResponse
    {
        $dateFromRaw = $request->query->get('date_from');
        $dateToRaw = $request->query->get('date_to');
        $dateFrom = $dateFromRaw ? new \DateTimeImmutable($dateFromRaw) : null;
        $dateTo = $dateToRaw ? (new \DateTimeImmutable($dateToRaw))->modify('+1 day') : null;
        $qb = $this->em->createQueryBuilder()
            ->select('s')
            ->from(Sale::class, 's')
            ->orderBy('s.createdAt', 'DESC');
        if ($dateFrom) {
            $qb->andWhere('s.createdAt >= :from')->setParameter('from', $dateFrom);
        }
        if ($dateTo) {
            $qb->andWhere('s.createdAt < :to')->setParameter('to', $dateTo);
        }
        $sales = $qb->getQuery()->getResult();

        $response = new StreamedResponse(function () use ($sales) {
            $handle = fopen('php://output', 'w');
            fprintf($handle, chr(0xEF) . chr(0xBB) . chr(0xBF));
            fputcsv($handle, ['ID', 'Клиент', 'Товар/услуга', 'Кол-во', 'Цена', 'Сумма', 'Оплата', 'Дата'], ';');
            foreach ($sales as $s) {
                $clientName = $s->getUser() ? $s->getUser()->getName() : $s->getClientName();
                fputcsv($handle, [
                    $s->getId(),
                    $clientName,
                    $s->getProductName(),
                    $s->getQuantity(),
                    number_format($s->getPrice(), 2, '.', ''),
                    number_format($s->getTotal(), 2, '.', ''),
                    $s->getPaymentMethod(),
                    $s->getCreatedAt()->format('d.m.Y H:i'),
                ], ';');
            }
            fclose($handle);
        });
        $filename = 'sales';
        if ($dateFromRaw) {
            $filename .= '_' . $dateFromRaw;
        }
        if ($dateToRaw) {
            $filename .= '_' . $dateToRaw;
        }
        $response->headers->set('Content-Type', 'text/csv; charset=UTF-8');
        $response->headers->set('Content-Disposition', 'attachment; filename="' . $filename . '.csv"');

        return $response;
    }

    #[Route('/{section}', name: 'admin_section', methods: ['GET', 'POST'])]
    public function section(string $section, Request $request): Response
    {
        $menu = $this->buildMenu();
        if (!isset($menu[$section])) {
            throw $this->createNotFoundException();
        }

        if ($section === 'onboarding') {
            return $this->render('admin/onboarding.html.twig', [
                'menu' => $menu,
                'current' => $section,
            ]);
        }

        // Специальная страница для клиентов с данными из БД
        if ($section === 'clients') {
            $search = trim((string) $request->query->get('q', ''));
            $tagId = $request->query->get('tag_id') ? (int) $request->query->get('tag_id') : null;
            $clubId = $request->query->get('club_id') ? (int) $request->query->get('club_id') : null;
            $allTags = $this->em->getRepository(Tag::class)->findBy([], ['name' => 'ASC']);
            $allClubs = $this->em->getRepository(Club::class)->findBy([], ['name' => 'ASC']);
            $clients = $this->buildFilteredClientsList($request);

            return $this->render('admin/clients.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'clients' => $clients,
                'search' => $search,
                'allTags' => $allTags,
                'allClubs' => $allClubs,
                'filterTagId' => $tagId,
                'filterClubId' => $clubId,
            ]);
        }

        if ($section === 'subscriptions') {
            $planRepo = $this->em->getRepository(SubscriptionPlan::class);

            if ($request->isMethod('POST')) {
                if ($request->request->get('add_all_plan_templates')) {
                    $added = 0;
                    foreach ($this->planCatalog->all() as $key => $template) {
                        if ($planRepo->findOneBy(['name' => $template['name']]) !== null) {
                            continue;
                        }
                        $plan = new SubscriptionPlan();
                        $this->planCatalog->applyTemplate($plan, $key);
                        $this->em->persist($plan);
                        ++$added;
                    }
                    $this->em->flush();
                    $this->addFlash(
                        $added > 0 ? 'success' : 'info',
                        $added > 0
                            ? 'Добавлено стандартных тарифов: ' . $added . '.'
                            : 'Все стандартные тарифы уже есть в CRM.',
                    );
                } else {
                    $templateKey = (string) $request->request->get('plan_template', '');
                    $template = $this->planCatalog->find($templateKey);
                    if ($template === null) {
                        $this->addFlash('danger', 'Выберите тариф из списка.');
                    } elseif ($planRepo->findOneBy(['name' => $template['name']]) !== null) {
                        $this->addFlash('warning', 'Тариф «' . $template['name'] . '» уже добавлен в CRM.');
                    } else {
                        $plan = new SubscriptionPlan();
                        $this->planCatalog->applyTemplate($plan, $templateKey);
                        $plan->setIsPopular((bool) $request->request->get('is_popular', false));
                        $this->em->persist($plan);
                        $this->em->flush();
                        $this->addFlash('success', 'Тариф «' . $plan->getName() . '» добавлен.');
                    }
                }

                $redirect = $this->redirectToRoute('admin_section', ['section' => 'subscriptions']);
                $sf = $request->query->get('status', '');
                if ($sf !== '') {
                    $redirect->setTargetUrl($redirect->getTargetUrl() . '?status=' . urlencode($sf));
                }

                return $redirect;
            }

            $plans = $planRepo->findBy([], ['id' => 'ASC']);
            $users = $this->em->getRepository(User::class)->findBy([], ['id' => 'ASC']);
            $statusFilter = $request->query->get('status', '');
            $subsQb = $this->em->createQueryBuilder()->select('s')->from(Subscription::class, 's')->orderBy('s.id', 'DESC');
            if ($statusFilter === 'active' || $statusFilter === 'frozen') {
                $subsQb->andWhere('s.status = :status')->setParameter('status', $statusFilter);
            } elseif ($statusFilter === 'expired') {
                $today = new \DateTimeImmutable('today');
                $subsQb->andWhere('s.endDate IS NOT NULL')
                    ->andWhere('s.endDate < :today')
                    ->setParameter('today', $today);
            }
            $subscriptions = $subsQb->getQuery()->getResult();

            return $this->render('admin/subscription_plans.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'plans' => $plans,
                'planTemplates' => $this->planCatalog->all(),
                'availablePlanTemplates' => $this->planCatalog->availableFor($plans),
                'users' => $users,
                'clubs' => $this->em->getRepository(Club::class)->findBy([], ['name' => 'ASC']),
                'subscriptions' => $subscriptions,
                'statusFilter' => $statusFilter,
            ]);
        }

        if ($section === 'services') {
            $categoryFilter = $request->query->get('category', '');
            if ($request->isMethod('POST')) {
                $product = (new Product())
                    ->setName($request->request->get('name'))
                    ->setDescription($request->request->get('description') ?: null)
                    ->setPrice((float) $request->request->get('price'))
                    ->setCategory($request->request->get('category', 'service'));

                $this->em->persist($product);
                $this->em->flush();

                $redirect = $this->redirectToRoute('admin_section', ['section' => 'services']);
                if ($categoryFilter !== '') {
                    $redirect->setTargetUrl($redirect->getTargetUrl() . '?category=' . urlencode($categoryFilter));
                }
                return $redirect;
            }

            $products = $this->em->getRepository(Product::class)->findBy([], ['id' => 'ASC']);
            if (in_array($categoryFilter, ['service', 'goods'], true)) {
                $products = array_values(array_filter($products, fn (Product $p) => $p->getCategory() === $categoryFilter));
            }

            return $this->render('admin/products.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'products' => $products,
                'categoryFilter' => $categoryFilter,
            ]);
        }

        if ($section === 'leads') {
            $statusFilter = $request->query->get('status', '');
            $sourceFilter = $request->query->get('source', '');
            $qb = $this->em->createQueryBuilder()
                ->select('l')
                ->from(Lead::class, 'l')
                ->leftJoin('l.notes', 'ln')
                ->addSelect('ln')
                ->orderBy('l.id', 'DESC');
            if ($statusFilter !== '' && in_array($statusFilter, ['new', 'trial_scheduled', 'trial_visited', 'converted', 'inactive'], true)) {
                $qb->andWhere('l.status = :status')->setParameter('status', $statusFilter);
            }
            if ($sourceFilter !== '' && LeadSource::isValid($sourceFilter)) {
                $qb->andWhere('l.source = :source')->setParameter('source', $sourceFilter);
            }
            $leads = $qb->getQuery()->getResult();

            $sourceStats = [];
            foreach (LeadSource::keys() as $srcKey) {
                $sourceStats[$srcKey] = (int) $this->em->getRepository(Lead::class)->count(['source' => $srcKey]);
            }

            $funnel = [
                'new' => $this->em->getRepository(Lead::class)->count(['status' => 'new']),
                'trial_scheduled' => $this->em->getRepository(Lead::class)->count(['status' => 'trial_scheduled']),
                'trial_visited' => $this->em->getRepository(Lead::class)->count(['status' => 'trial_visited']),
                'converted' => $this->em->getRepository(Lead::class)->count(['status' => 'converted']),
                'inactive' => $this->em->getRepository(Lead::class)->count(['status' => 'inactive']),
            ];

            return $this->render('admin/leads.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'leads' => $leads,
                'statusFilter' => $statusFilter,
                'sourceFilter' => $sourceFilter,
                'funnel' => $funnel,
                'sourceStats' => $sourceStats,
            ]);
        }

        if ($section === 'tasks') {
            $statusFilter = $request->query->get('status', 'open');
            $typeFilter = $request->query->get('type', '');
            $clientId = $request->query->get('client_id') ? (int) $request->query->get('client_id') : null;
            $qb = $this->em->createQueryBuilder()
                ->select('t')
                ->from(Task::class, 't')
                ->orderBy('t.id', 'DESC');
            if ($statusFilter !== 'all' && in_array($statusFilter, ['open', 'in_progress', 'done'], true)) {
                $qb->andWhere('t.status = :status')->setParameter('status', $statusFilter);
            }
            if ($typeFilter !== '' && in_array($typeFilter, ['task', 'call', 'meeting'], true)) {
                $qb->andWhere('t.type = :type')->setParameter('type', $typeFilter);
            }
            $filterClient = null;
            if ($clientId) {
                $filterClient = $this->em->getRepository(User::class)->find($clientId);
                if ($filterClient) {
                    $qb->andWhere('t.client = :filterClient')->setParameter('filterClient', $filterClient);
                }
            }
            $tasks = $qb->getQuery()->getResult();
            usort($tasks, function (Task $a, Task $b) {
                $da = $a->getDueAt();
                $db = $b->getDueAt();
                if ($da === null && $db === null) return $b->getId() <=> $a->getId();
                if ($da === null) return 1;
                if ($db === null) return -1;
                return $da <=> $db ?: ($b->getId() <=> $a->getId());
            });
            $users = $this->em->getRepository(User::class)->findBy([], ['name' => 'ASC']);

            return $this->render('admin/tasks.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'tasks' => $tasks,
                'statusFilter' => $statusFilter,
                'typeFilter' => $typeFilter,
                'clientId' => $clientId,
                'filterClient' => $filterClient,
                'users' => $users,
            ]);
        }

        if ($section === 'sales') {
            $dateFromRaw = $request->query->get('date_from');
            $dateToRaw = $request->query->get('date_to');
            $dateFrom = $dateFromRaw ? new \DateTimeImmutable($dateFromRaw) : null;
            $dateTo = $dateToRaw ? (new \DateTimeImmutable($dateToRaw))->modify('+1 day') : null;
            $qb = $this->em->createQueryBuilder()
                ->select('s')
                ->from(Sale::class, 's')
                ->orderBy('s.createdAt', 'DESC');
            if ($dateFrom) {
                $qb->andWhere('s.createdAt >= :from')->setParameter('from', $dateFrom);
            }
            if ($dateTo) {
                $qb->andWhere('s.createdAt < :to')->setParameter('to', $dateTo);
            }
            $sales = $qb->getQuery()->getResult();
            $users = $this->em->getRepository(User::class)->findBy([], ['name' => 'ASC']);
            $products = $this->em->getRepository(Product::class)->findBy(['isActive' => true], ['name' => 'ASC']);

            return $this->render('admin/sales.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'sales' => $sales,
                'users' => $users,
                'products' => $products,
                'dateFrom' => $dateFromRaw,
                'dateTo' => $dateToRaw,
            ]);
        }

        if ($section === 'bookings') {
            $bookDate = $request->query->get('date') ? new \DateTimeImmutable($request->query->get('date')) : null;
            $qb = $this->em->createQueryBuilder()
                ->select('b')
                ->from(Booking::class, 'b')
                ->join('b.training', 't')
                ->orderBy('t.startAt', 'DESC');
            if ($bookDate) {
                $dayEnd = $bookDate->modify('+1 day');
                $qb->andWhere('t.startAt >= :start')->andWhere('t.startAt < :end')
                    ->setParameter('start', $bookDate)->setParameter('end', $dayEnd);
            }
            $bookings = $qb->getQuery()->getResult();

            return $this->render('admin/bookings.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'bookings' => $bookings,
                'bookDate' => $bookDate?->format('Y-m-d'),
            ]);
        }

        if ($section === 'visits') {
            $period = $this->visitPeriodResolver->resolve($request->query->all());
            $visitStats = $this->visitReport->countByClub($period->from, $period->toExclusive);
            $qb = $this->em->createQueryBuilder()
                ->select('a')
                ->from(AccessLog::class, 'a')
                ->leftJoin('a.club', 'c')
                ->addSelect('c')
                ->where('a.result = :result')
                ->andWhere('a.eventType = :eventType')
                ->andWhere('a.createdAt >= :from')
                ->andWhere('a.createdAt < :to')
                ->setParameter('result', 'granted')
                ->setParameter('eventType', 'entry')
                ->setParameter('from', $period->from)
                ->setParameter('to', $period->toExclusive)
                ->orderBy('a.createdAt', 'DESC');
            $accessLogs = $qb->getQuery()->getResult();

            return $this->render('admin/visits.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'visits' => $accessLogs,
                'visitPeriod' => $period,
                'visitStats' => $visitStats,
                'currentlyInside' => $this->occupancy->countCurrentlyInside(),
                'currentlyInsideList' => $this->occupancy->listCurrentlyInside(null, 100),
            ]);
        }

        if ($section === 'schedule') {
            $scheduleDate = $request->query->get('date') ? new \DateTimeImmutable($request->query->get('date')) : new \DateTimeImmutable('today');
            $dayStart = $scheduleDate;
            $dayEnd = $scheduleDate->modify('+1 day');
            $qb = $this->em->createQueryBuilder()
                ->select('t')
                ->from(Training::class, 't')
                ->where('t.startAt >= :start')
                ->andWhere('t.startAt < :end')
                ->setParameter('start', $dayStart)
                ->setParameter('end', $dayEnd)
                ->orderBy('t.startAt', 'ASC');
            $trainings = $qb->getQuery()->getResult();
            $trainers = $this->em->getRepository(Trainer::class)->findBy([], ['id' => 'ASC']);
            $bookingsByTraining = [];
            foreach ($trainings as $t) {
                $all = $this->em->getRepository(Booking::class)->findBy(
                    ['training' => $t],
                    ['bookedAt' => 'ASC']
                );
                $bookingsByTraining[$t->getId()] = array_values(array_filter($all, fn (Booking $b) => in_array($b->getStatus(), ['confirmed', 'waiting'], true)));
            }

            return $this->render('admin/schedule.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'trainings' => $trainings,
                'trainers' => $trainers,
                'bookingsByTraining' => $bookingsByTraining,
                'scheduleDate' => $scheduleDate->format('Y-m-d'),
            ]);
        }

        if ($section === 'trainers') {
            $trainers = $this->em->getRepository(Trainer::class)->findBy([], ['id' => 'ASC']);

            return $this->render('admin/staff.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'trainers' => $trainers,
            ]);
        }

        if ($section === 'selfservice') {
            $logDateFrom = $request->query->get('date_from') ? new \DateTimeImmutable($request->query->get('date_from')) : (new \DateTimeImmutable('today'))->modify('-3 days');
            $logDateToRaw = $request->query->get('date_to') ?: (new \DateTimeImmutable('today'))->format('Y-m-d');
            $logDateTo = (new \DateTimeImmutable($logDateToRaw))->modify('+1 day');
            $resultFilter = $request->query->get('result', '');
            $qb = $this->em->createQueryBuilder()
                ->select('a')
                ->from(AccessLog::class, 'a')
                ->where('a.createdAt >= :from')
                ->andWhere('a.createdAt < :to')
                ->setParameter('from', $logDateFrom)
                ->setParameter('to', $logDateTo)
                ->orderBy('a.createdAt', 'DESC');
            if ($resultFilter === 'granted' || $resultFilter === 'denied') {
                $qb->andWhere('a.result = :result')->setParameter('result', $resultFilter);
            }
            $logs = $qb->getQuery()->getResult();

            return $this->render('admin/selfservice.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'logs' => $logs,
                'dateFrom' => $logDateFrom->format('Y-m-d'),
                'dateTo' => $logDateToRaw,
                'resultFilter' => $resultFilter,
            ]);
        }

        if ($section === 'analytics') {
            $periodFilter = $request->query->get('period', 'today');
            $todayStart = new \DateTimeImmutable('today');
            $periodEnd = $todayStart->modify('+1 day');
            $periodStart = match ($periodFilter) {
                'week' => $todayStart->modify('-6 days'),
                'month' => $todayStart->modify('first day of this month'),
                default => $todayStart,
            };

            $qb = $this->em->createQueryBuilder();
            $revenuePeriod = (float) (clone $qb)->select('COALESCE(SUM(s.total), 0)')
                ->from(Sale::class, 's')
                ->where('s.createdAt >= :start')->andWhere('s.createdAt < :end')
                ->setParameter('start', $periodStart)->setParameter('end', $periodEnd)
                ->getQuery()->getSingleScalarResult();

            $revenueToday = (float) (clone $qb)->select('COALESCE(SUM(s.total), 0)')
                ->from(Sale::class, 's')
                ->where('s.createdAt >= :start')->andWhere('s.createdAt < :end')
                ->setParameter('start', $todayStart)->setParameter('end', $todayStart->modify('+1 day'))
                ->getQuery()->getSingleScalarResult();

            $weekStart = $todayStart->modify('-6 days');
            $monthStart = $todayStart->modify('first day of this month');
            $revenueWeek = (float) (clone $qb)->select('COALESCE(SUM(s.total), 0)')
                ->from(Sale::class, 's')
                ->where('s.createdAt >= :start')->andWhere('s.createdAt < :end')
                ->setParameter('start', $weekStart)->setParameter('end', $periodEnd)
                ->getQuery()->getSingleScalarResult();

            $revenueMonth = (float) (clone $qb)->select('COALESCE(SUM(s.total), 0)')
                ->from(Sale::class, 's')
                ->where('s.createdAt >= :start')->andWhere('s.createdAt < :end')
                ->setParameter('start', $monthStart)->setParameter('end', $periodEnd)
                ->getQuery()->getSingleScalarResult();

            $visitsPeriod = (int) $this->em->createQueryBuilder()
                ->select('COUNT(a.id)')
                ->from(AccessLog::class, 'a')
                ->where('a.result = :result')
                ->andWhere('a.createdAt >= :start')
                ->andWhere('a.createdAt < :end')
                ->setParameter('result', 'granted')
                ->setParameter('start', $periodStart)
                ->setParameter('end', $periodEnd)
                ->getQuery()->getSingleScalarResult();

            $visitsToday = (int) $this->em->createQueryBuilder()
                ->select('COUNT(a.id)')
                ->from(AccessLog::class, 'a')
                ->where('a.result = :result')
                ->andWhere('a.createdAt >= :start')
                ->andWhere('a.createdAt < :end')
                ->setParameter('result', 'granted')
                ->setParameter('start', $todayStart)
                ->setParameter('end', $todayStart->modify('+1 day'))
                ->getQuery()->getSingleScalarResult();

            $visitsWeek = (int) $this->em->createQueryBuilder()
                ->select('COUNT(a.id)')
                ->from(AccessLog::class, 'a')
                ->where('a.result = :result')
                ->andWhere('a.createdAt >= :start')
                ->andWhere('a.createdAt < :end')
                ->setParameter('result', 'granted')
                ->setParameter('start', $weekStart)
                ->setParameter('end', $periodEnd)
                ->getQuery()->getSingleScalarResult();

            $leadsTotal = (int) $this->em->getRepository(Lead::class)->count([]);
            $leadsConverted = (int) $this->em->getRepository(Lead::class)->count(['status' => 'converted']);
            $conversionRate = $leadsTotal > 0 ? round($leadsConverted / $leadsTotal * 100, 1) : 0;

            $activeSubsCount = (int) $this->em->getRepository(Subscription::class)->count(['status' => 'active']);
            $newSubsToday = (int) $this->em->createQueryBuilder()
                ->select('COUNT(s.id)')
                ->from(Subscription::class, 's')
                ->where('s.startDate >= :start')
                ->andWhere('s.startDate < :end')
                ->setParameter('start', $todayStart)
                ->setParameter('end', $todayStart->modify('+1 day'))
                ->getQuery()->getSingleScalarResult();

            $topProducts = $this->em->createQueryBuilder()
                ->select('s.productName, SUM(s.quantity) as qty, SUM(s.total) as total')
                ->from(Sale::class, 's')
                ->where('s.createdAt >= :start')->andWhere('s.createdAt < :end')
                ->setParameter('start', $periodStart)->setParameter('end', $periodEnd)
                ->groupBy('s.productName')
                ->orderBy('total', 'DESC')
                ->setMaxResults(10)
                ->getQuery()->getResult();

            return $this->render('admin/analytics.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'periodFilter' => $periodFilter,
                'revenuePeriod' => $revenuePeriod,
                'visitsPeriod' => $visitsPeriod,
                'revenueToday' => $revenueToday,
                'revenueWeek' => $revenueWeek,
                'revenueMonth' => $revenueMonth,
                'visitsToday' => $visitsToday,
                'visitsWeek' => $visitsWeek,
                'leadsTotal' => $leadsTotal,
                'leadsConverted' => $leadsConverted,
                'conversionRate' => $conversionRate,
                'activeSubsCount' => $activeSubsCount,
                'newSubsToday' => $newSubsToday,
                'topProducts' => $topProducts,
            ]);
        }

        if ($section === 'cashdesk') {
            $cashDate = $request->query->get('date') ? new \DateTimeImmutable($request->query->get('date')) : new \DateTimeImmutable('today');
            $dayStart = $cashDate;
            $dayEnd = $cashDate->modify('+1 day');
            $qb = $this->em->createQueryBuilder();
            $revenueToday = (float) (clone $qb)->select('COALESCE(SUM(s.total), 0)')
                ->from(Sale::class, 's')
                ->where('s.createdAt >= :start')->andWhere('s.createdAt < :end')
                ->setParameter('start', $dayStart)->setParameter('end', $dayEnd)
                ->getQuery()->getSingleScalarResult();
            $salesToday = $this->em->createQueryBuilder()
                ->select('s')
                ->from(Sale::class, 's')
                ->where('s.createdAt >= :start')->andWhere('s.createdAt < :end')
                ->setParameter('start', $dayStart)->setParameter('end', $dayEnd)
                ->orderBy('s.createdAt', 'DESC')
                ->getQuery()->getResult();
            $breakdownRaw = $this->em->createQueryBuilder()
                ->select('s.paymentMethod, SUM(s.total) as totalSum')
                ->from(Sale::class, 's')
                ->where('s.createdAt >= :start')->andWhere('s.createdAt < :end')
                ->setParameter('start', $dayStart)->setParameter('end', $dayEnd)
                ->groupBy('s.paymentMethod')
                ->getQuery()->getArrayResult();
            $paymentBreakdown = array_map(fn ($r) => ['paymentMethod' => $r['paymentMethod'], 'totalSum' => (float) $r['totalSum']], $breakdownRaw);

            return $this->render('admin/cashdesk.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'revenueToday' => $revenueToday,
                'salesToday' => $salesToday,
                'paymentBreakdown' => $paymentBreakdown,
                'cashDate' => $cashDate->format('Y-m-d'),
            ]);
        }

        if ($section === 'warehouse') {
            $products = $this->em->getRepository(Product::class)->findBy([], ['name' => 'ASC']);
            $lowStockProducts = array_values(array_filter($products, fn (Product $p) => $p->getCategory() === 'goods' && $p->getQuantity() !== null && $p->getQuantity() < 5));

            return $this->render('admin/warehouse.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'products' => $products,
                'lowStockProducts' => $lowStockProducts,
            ]);
        }

        if ($section === 'mobileapps') {
            $clubName = $this->em->getRepository(ClubSetting::class)->find('name')?->getSettingValue();
            $clubAddress = $this->em->getRepository(ClubSetting::class)->find('address')?->getSettingValue();
            $clubConfigured = ($clubName !== null && $clubName !== '') || ($clubAddress !== null && $clubAddress !== '');

            $plansCount = (int) $this->em->getRepository(SubscriptionPlan::class)->count([]);
            $trainersCount = (int) $this->em->getRepository(Trainer::class)->count([]);
            $productsActiveCount = (int) $this->em->getRepository(Product::class)->count(['isActive' => true]);
            $pushTokensCount = (int) $this->em->createQueryBuilder()
                ->select('COUNT(p.id)')
                ->from(\App\Entity\PushToken::class, 'p')
                ->getQuery()->getSingleScalarResult();

            $recentAccessLogs = $this->em->createQueryBuilder()
                ->select('a')
                ->from(AccessLog::class, 'a')
                ->orderBy('a.createdAt', 'DESC')
                ->setMaxResults(15)
                ->getQuery()->getResult();

            return $this->render('admin/mobileapps.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'clubConfigured' => $clubConfigured,
                'plansCount' => $plansCount,
                'trainersCount' => $trainersCount,
                'productsActiveCount' => $productsActiveCount,
                'pushTokensCount' => $pushTokensCount,
                'recentAccessLogs' => $recentAccessLogs,
            ]);
        }

        if ($section === 'app_support') {
            $staff = $this->getUser();
            $canMutate = $staff instanceof StaffUser && $this->adminMenuBuilder->canMutateAdmin($staff);

            if ($request->isMethod('POST') && $canMutate) {
                $ticketId = (int) $request->request->get('ticket_id');
                $status = (string) $request->request->get('status');
                if ($ticketId > 0 && in_array($status, SupportTicket::allowedStatuses(), true)) {
                    $ticket = $this->em->find(SupportTicket::class, $ticketId);
                    if ($ticket instanceof SupportTicket) {
                        $ticket->setStatus($status);
                        $this->em->flush();
                        $this->addFlash('success', 'Статус обновлён.');
                    }
                }

                return $this->redirectToRoute('admin_section', ['section' => 'app_support']);
            }

            $tickets = $this->em->createQueryBuilder()
                ->select('t')
                ->from(SupportTicket::class, 't')
                ->orderBy('t.createdAt', 'DESC')
                ->setMaxResults(200)
                ->getQuery()
                ->getResult();

            return $this->render('admin/app_support.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'tickets' => $tickets,
                'canMutate' => $canMutate,
            ]);
        }

        if ($section === 'messengers') {
            return $this->render('admin/messengers.html.twig', [
                'menu' => $menu,
                'current' => $section,
            ]);
        }

        if ($section === 'calls') {
            return $this->render('admin/calls.html.twig', [
                'menu' => $menu,
                'current' => $section,
            ]);
        }

        if ($section === 'comments') {
            $notes = $this->em->createQueryBuilder()
                ->select('n')
                ->from(ClientNote::class, 'n')
                ->orderBy('n.createdAt', 'DESC')
                ->setMaxResults(50)
                ->getQuery()->getResult();
            $leadsWithComments = $this->em->getRepository(Lead::class)->findBy(
                [],
                ['id' => 'DESC'],
                30
            );
            $leadsWithComments = array_filter($leadsWithComments, fn (Lead $l) => $l->getComment() !== null && $l->getComment() !== '');

            return $this->render('admin/comments.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'notes' => $notes,
                'leadsWithComments' => array_values($leadsWithComments),
            ]);
        }

        if ($section === 'deals') {
            $dateFromRaw = $request->query->get('date_from');
            $dateToRaw = $request->query->get('date_to');
            $qb = $this->em->createQueryBuilder()
                ->select('l')
                ->from(Lead::class, 'l')
                ->where('l.status = :status')
                ->setParameter('status', 'converted')
                ->orderBy('l.createdAt', 'DESC');
            if ($dateFromRaw) {
                $qb->andWhere('l.createdAt >= :from')->setParameter('from', new \DateTimeImmutable($dateFromRaw));
            }
            if ($dateToRaw) {
                $qb->andWhere('l.createdAt < :to')->setParameter('to', (new \DateTimeImmutable($dateToRaw))->modify('+1 day'));
            }
            $deals = $qb->getQuery()->getResult();

            return $this->render('admin/deals.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'deals' => $deals,
                'dateFrom' => $dateFromRaw,
                'dateTo' => $dateToRaw,
            ]);
        }

        if ($section === 'documents') {
            $searchQ = trim((string) $request->query->get('q', ''));
            $categoryFilter = trim((string) $request->query->get('category', ''));
            $qb = $this->em->createQueryBuilder()
                ->select('d')
                ->from(Document::class, 'd')
                ->orderBy('d.createdAt', 'DESC');
            if ($searchQ !== '') {
                $qb->andWhere('d.name LIKE :q')->setParameter('q', '%' . $searchQ . '%');
            }
            if ($categoryFilter !== '') {
                $qb->andWhere('d.category LIKE :cat')->setParameter('cat', '%' . $categoryFilter . '%');
            }
            $documents = $qb->getQuery()->getResult();

            return $this->render('admin/documents.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'documents' => $documents,
                'searchQ' => $searchQ,
                'categoryFilter' => $categoryFilter,
            ]);
        }

        if ($section === 'promocodes') {
            if ($request->isMethod('POST')) {
                $code = strtoupper(trim((string) $request->request->get('code')));
                $discountPercent = $request->request->get('discount_percent') !== '' ? (float) $request->request->get('discount_percent') : null;
                $discountAmount = $request->request->get('discount_amount') !== '' ? (float) $request->request->get('discount_amount') : null;
                $validFromRaw = $request->request->get('valid_from');
                $validToRaw = $request->request->get('valid_to');
                $usageLimit = $request->request->get('usage_limit') !== '' ? (int) $request->request->get('usage_limit') : null;

                if ($code !== '' && ($discountPercent !== null || $discountAmount !== null)) {
                    $promo = (new PromoCode())
                        ->setCode($code)
                        ->setDiscountPercent($discountPercent)
                        ->setDiscountAmount($discountAmount)
                        ->setValidFrom($validFromRaw ? new \DateTimeImmutable($validFromRaw) : null)
                        ->setValidTo($validToRaw ? new \DateTimeImmutable($validToRaw) : null)
                        ->setUsageLimit($usageLimit);
                    $this->em->persist($promo);
                    $this->em->flush();
                    $this->addFlash('success', 'Промокод создан.');
                }
                return $this->redirectToRoute('admin_section', ['section' => 'promocodes']);
            }

            $promoCodes = $this->em->getRepository(PromoCode::class)->findBy([], ['createdAt' => 'DESC']);

            return $this->render('admin/promocodes.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'promoCodes' => $promoCodes,
            ]);
        }

        if ($section === 'promotions') {
            if ($request->isMethod('POST')) {
                $title = trim((string) $request->request->get('title', ''));
                if ($title !== '') {
                    $promotion = (new Promotion())
                        ->setTitle($title)
                        ->setSubtitle(($request->request->get('subtitle') !== null && trim((string) $request->request->get('subtitle')) !== '') ? trim((string) $request->request->get('subtitle')) : null)
                        ->setButtonText(trim((string) $request->request->get('button_text', 'Подробнее')) ?: 'Подробнее')
                        ->setActionType((string) $request->request->get('action_type', 'shop'))
                        ->setActionValue(($request->request->get('action_value') !== null && trim((string) $request->request->get('action_value')) !== '') ? trim((string) $request->request->get('action_value')) : null)
                        ->setBgFrom($this->normalizeHexColor((string) $request->request->get('bg_from', '#F97316')))
                        ->setBgTo($this->normalizeHexColor((string) $request->request->get('bg_to', '#3B82F6')))
                        ->setSortOrder((int) $request->request->get('sort_order', 100))
                        ->setIsActive($request->request->get('is_active') === '1');

                    $this->applyPromotionImageUpload($request, $promotion);
                    $this->em->persist($promotion);
                    $this->em->flush();
                    $this->addFlash('success', 'Акция создана.');
                } else {
                    $this->addFlash('warning', 'Укажите заголовок акции.');
                }

                return $this->redirectToRoute('admin_section', ['section' => 'promotions']);
            }

            $promotions = $this->em->getRepository(Promotion::class)->findBy([], ['sortOrder' => 'ASC', 'id' => 'DESC']);

            return $this->render('admin/promotions.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'promotions' => $promotions,
            ]);
        }

        if ($section === 'tags') {
            $tags = $this->em->getRepository(Tag::class)->findBy([], ['name' => 'ASC']);
            return $this->render('admin/tags.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'tags' => $tags,
            ]);
        }

        if ($section === 'finance') {
            $dateFromRaw = $request->query->get('date_from') ?: (new \DateTimeImmutable('first day of this month'))->format('Y-m-d');
            $dateToRaw = $request->query->get('date_to') ?: (new \DateTimeImmutable('last day of this month'))->format('Y-m-d');
            $dateFrom = new \DateTimeImmutable($dateFromRaw);
            $dateTo = (new \DateTimeImmutable($dateToRaw))->modify('+1 day');

            $income = (float) $this->em->createQueryBuilder()
                ->select('COALESCE(SUM(s.total), 0)')
                ->from(Sale::class, 's')
                ->where('s.createdAt >= :from')->andWhere('s.createdAt < :to')
                ->setParameter('from', $dateFrom)->setParameter('to', $dateTo)
                ->getQuery()->getSingleScalarResult();

            $expenses = $this->em->getRepository(Expense::class)->findBy(
                [],
                ['date' => 'DESC', 'id' => 'DESC'],
                100
            );
            $dateToExclusive = (new \DateTimeImmutable($dateToRaw))->modify('+1 day');
            $expensesInPeriod = array_values(array_filter($expenses, fn (Expense $e) => $e->getDate() >= $dateFrom && $e->getDate() < $dateToExclusive));
            $expenseTotal = array_sum(array_map(fn (Expense $e) => $e->getAmount(), $expensesInPeriod));

            return $this->render('admin/finance.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'dateFrom' => $dateFrom->format('Y-m-d'),
                'dateTo' => $dateToRaw,
                'income' => $income,
                'expenseTotal' => $expenseTotal,
                'profit' => $income - $expenseTotal,
                'expenses' => $expensesInPeriod,
                'allExpenses' => array_slice($expenses, 0, 50),
            ]);
        }

        if ($section === 'settings') {
            return $this->render('admin/settings.html.twig', [
                'menu' => $menu,
                'current' => $section,
            ]);
        }

        return $this->render('admin/section.html.twig', [
            'menu' => $menu,
            'current' => $section,
            'title' => $menu[$section],
        ]);
    }

    private function normalizeHexColor(string $value): string
    {
        $c = strtoupper(trim($value));
        if (!str_starts_with($c, '#')) {
            $c = '#' . $c;
        }
        if (!preg_match('/^#[0-9A-F]{6}$/', $c)) {
            return '#F97316';
        }
        return $c;
    }

    private function applyPromotionImageUpload(Request $request, Promotion $promotion): void
    {
        $file = $request->files->get('image_file');
        if (!$file instanceof UploadedFile) {
            return;
        }
        if (!$file->isValid()) {
            if ($file->getError() !== \UPLOAD_ERR_NO_FILE) {
                $this->addFlash('warning', 'Изображение не принято: ' . $file->getErrorMessage());
            }

            return;
        }
        try {
            $promotion->setImagePath($this->storePromotionImage($file));
        } catch (\Throwable $e) {
            $this->addFlash('warning', $e->getMessage());
        }
    }

    private function storePromotionImage(UploadedFile $file): string
    {
        $allowed = ['jpg', 'jpeg', 'png', 'webp'];
        // Имя файла — без symfony/mime; guessExtension() без пакета symfony/mime даёт предупреждение/сбой.
        $ext = strtolower(pathinfo($file->getClientOriginalName(), \PATHINFO_EXTENSION));
        if (!in_array($ext, $allowed, true)) {
            try {
                $g = strtolower((string) ($file->guessExtension() ?: ''));
                if (in_array($g, $allowed, true)) {
                    $ext = $g;
                }
            } catch (\Throwable) {
                // нет MimeTypes — остаёмся по имени файла
            }
        }
        if (!in_array($ext, $allowed, true)) {
            throw new \RuntimeException('Формат изображения: jpg, png, webp (расширение файла: ' . ($ext !== '' ? $ext : 'не определено') . ').');
        }

        $uploadsDir = $this->getParameter('kernel.project_dir') . '/public/uploads/promotions';
        if (!is_dir($uploadsDir) && !mkdir($uploadsDir, 0775, true) && !is_dir($uploadsDir)) {
            throw new \RuntimeException('Не удалось создать директорию для изображений.');
        }

        $filename = 'promo_' . date('Ymd_His') . '_' . bin2hex(random_bytes(4)) . '.' . $ext;
        $file->move($uploadsDir, $filename);

        return '/uploads/promotions/' . $filename;
    }
}

