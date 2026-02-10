<?php

namespace App\Controller\Admin;

use App\Entity\User;
use App\Entity\Product;
use App\Entity\Lead;
use App\Entity\Task;
use App\Entity\Sale;
use App\Entity\SubscriptionPlan;
use App\Entity\Subscription;
use App\Entity\Training;
use App\Entity\Trainer;
use App\Entity\Booking;
use App\Entity\AccessLog;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Component\HttpFoundation\Request;

#[Route('/admin')]
class AdminController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {}
    private function getMenu(): array
    {
        return [
            'dashboard' => 'Дашборд',
            'tasks' => 'Задачи',
            'clients' => 'Клиенты',
            'schedule' => 'Расписание',
            'subscriptions' => 'Абонементы',
            'visits' => 'Посещения',
            'sales' => 'Продажи',
            'services' => 'Услуги',
            'leads' => 'Лиды',
            'deals' => 'Сделки',
            'comments' => 'Комментарии',
            'messengers' => 'Мессенджеры',
            'calls' => 'Звонки',
            'selfservice' => 'Самообслуживание',
            'cashdesk' => 'Касса',
            'warehouse' => 'Склад',
            'analytics' => 'Аналитика',
            'mobileapps' => 'Мобильные приложения',
            'staff' => 'Персонал',
            'documents' => 'Документы',
            'settings' => 'Настройки',
        ];
    }

    #[Route('', name: 'admin_dashboard', methods: ['GET'])]
    public function dashboard(): Response
    {
        $clientsCount = $this->em->getRepository(User::class)->count([]);
        $leadsCount = $this->em->getRepository(Lead::class)->count([]);

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

        // Открытые задачи
        $tasks = $this->em->getRepository(Task::class)->findBy(['status' => 'open'], ['id' => 'DESC'], 10);

        return $this->render('admin/dashboard.html.twig', [
            'menu' => $this->getMenu(),
            'current' => 'dashboard',
            'stats' => [
                'clients' => $clientsCount,
                'leads' => $leadsCount,
                'trainingsToday' => $trainingsToday,
                'revenueToday' => $revenueToday,
            ],
            'tasks' => $tasks,
        ]);
    }

    #[Route('/clients/new', name: 'admin_client_new', methods: ['POST'])]
    public function createClient(Request $request): Response
    {
        $name = (string) $request->request->get('name');
        $phone = (string) $request->request->get('phone');
        $email = (string) $request->request->get('email');
        $bonusPoints = (int) $request->request->get('bonus_points', 0);

        $user = (new User())
            ->setName($name)
            ->setPhone($phone)
            ->setEmail($email)
            ->setBonusPoints($bonusPoints);

        $this->em->persist($user);
        $this->em->flush();

        return $this->redirectToRoute('admin_section', ['section' => 'clients']);
    }

    #[Route('/clients/{id}', name: 'admin_client_show', methods: ['GET'])]
    public function showClient(int $id): Response
    {
        $menu = $this->getMenu();

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

        return $this->render('admin/client_show.html.twig', [
            'menu' => $menu,
            'current' => 'clients',
            'client' => $client,
            'subscriptions' => $subscriptions,
            'bookings' => $bookings,
        ]);
    }

    #[Route('/leads/new', name: 'admin_lead_new', methods: ['POST'])]
    public function createLead(Request $request): Response
    {
        $name = (string) $request->request->get('name');
        $phone = (string) $request->request->get('phone');
        $email = $request->request->get('email') ?: null;
        $comment = $request->request->get('comment') ?: null;

        $lead = (new Lead())
            ->setName($name)
            ->setPhone($phone)
            ->setEmail($email)
            ->setComment($comment);

        $this->em->persist($lead);
        $this->em->flush();

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

        // Обновляем статус лида
        $lead->setStatus('converted');

        $this->em->flush();

        return $this->redirectToRoute('admin_client_show', ['id' => $user->getId()]);
    }

    #[Route('/tasks/new', name: 'admin_task_new', methods: ['POST'])]
    public function createTask(Request $request): Response
    {
        $title = (string) $request->request->get('title');
        $clientName = $request->request->get('client_name') ?: null;
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

        $this->em->persist($task);
        $this->em->flush();

        return $this->redirectToRoute('admin_section', ['section' => 'tasks']);
    }

    #[Route('/sales/new', name: 'admin_sale_new', methods: ['POST'])]
    public function createSale(Request $request): Response
    {
        $clientName = (string) $request->request->get('client_name');
        $productName = (string) $request->request->get('product_name');
        $quantity = max(1, (int) $request->request->get('quantity', 1));
        $price = (float) $request->request->get('price');
        $paymentMethod = $request->request->get('payment_method', 'cash');

        $total = $price * $quantity;

        $sale = (new Sale())
            ->setClientName($clientName)
            ->setProductName($productName)
            ->setQuantity($quantity)
            ->setPrice($price)
            ->setTotal($total)
            ->setPaymentMethod($paymentMethod);

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
            return $this->redirectToRoute('admin_section', ['section' => 'schedule']);
        }

        $training = (new Training())
            ->setName($name)
            ->setRoom($room)
            ->setStartAt($startAt)
            ->setEndAt($endAt)
            ->setMaxParticipants($maxParticipants)
            ->setCurrentParticipants(0);

        if ($trainerId) {
            $trainer = $this->em->getRepository(Trainer::class)->find((int) $trainerId);
            if ($trainer) {
                $training
                    ->setTrainer($trainer)
                    ->setTrainerName($trainer->getName());
            }
        }

        $this->em->persist($training);
        $this->em->flush();

        return $this->redirectToRoute('admin_section', ['section' => 'schedule']);
    }

    #[Route('/staff/new', name: 'admin_staff_new', methods: ['POST'])]
    public function createStaff(Request $request): Response
    {
        $name = (string) $request->request->get('name');
        $specialization = $request->request->get('specialization') ?: null;
        $photoUrl = $request->request->get('photo_url') ?: null;
        $ratingRaw = $request->request->get('rating');
        $rating = $ratingRaw !== null && $ratingRaw !== '' ? (float) $ratingRaw : null;

        $trainer = (new Trainer())
            ->setName($name)
            ->setSpecialization($specialization)
            ->setPhotoUrl($photoUrl)
            ->setRating($rating);

        $this->em->persist($trainer);
        $this->em->flush();

        return $this->redirectToRoute('admin_section', ['section' => 'staff']);
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
                    : 14
            )
            ->setFreezeDaysUsed(0);

        if ($plan->getDurationDays() !== null) {
            $subscription->setEndDate(
                $startDate->modify('+' . $plan->getDurationDays() . ' days')
            );
        }

        $this->em->persist($subscription);
        $this->em->flush();

        return $this->redirectToRoute('admin_section', ['section' => 'subscriptions']);
    }

    #[Route('/{section}', name: 'admin_section', methods: ['GET', 'POST'])]
    public function section(string $section, Request $request): Response
    {
        $menu = $this->getMenu();
        if (!isset($menu[$section])) {
            throw $this->createNotFoundException();
        }

        // Специальная страница для клиентов с данными из БД
        if ($section === 'clients') {
            $clients = $this->em->getRepository(User::class)->findBy([], ['id' => 'ASC']);

            return $this->render('admin/clients.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'clients' => $clients,
            ]);
        }

        if ($section === 'subscriptions') {
            // создание нового тарифного плана через форму
            if ($request->isMethod('POST')) {
                $plan = new SubscriptionPlan();
                $plan
                    ->setName($request->request->get('name'))
                    ->setPrice((float) $request->request->get('price'))
                    ->setDurationDays($request->request->get('duration_days') !== '' ? (int) $request->request->get('duration_days') : null)
                    ->setVisitsCount($request->request->get('visits_count') !== '' ? (int) $request->request->get('visits_count') : null)
                    ->setType($request->request->get('type', 'unlimited'));

                $this->em->persist($plan);
                $this->em->flush();

                return $this->redirectToRoute('admin_section', ['section' => 'subscriptions']);
            }

            $plans = $this->em->getRepository(SubscriptionPlan::class)->findBy([], ['id' => 'ASC']);
            $users = $this->em->getRepository(User::class)->findBy([], ['id' => 'ASC']);
            $subscriptions = $this->em->getRepository(Subscription::class)->findBy([], ['id' => 'DESC']);

            return $this->render('admin/subscription_plans.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'plans' => $plans,
                'users' => $users,
                'subscriptions' => $subscriptions,
            ]);
        }

        if ($section === 'services') {
            if ($request->isMethod('POST')) {
                $product = (new Product())
                    ->setName($request->request->get('name'))
                    ->setDescription($request->request->get('description') ?: null)
                    ->setPrice((float) $request->request->get('price'))
                    ->setCategory($request->request->get('category', 'service'));

                $this->em->persist($product);
                $this->em->flush();

                return $this->redirectToRoute('admin_section', ['section' => 'services']);
            }

            $products = $this->em->getRepository(Product::class)->findBy([], ['id' => 'ASC']);

            return $this->render('admin/products.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'products' => $products,
            ]);
        }

        if ($section === 'leads') {
            $leads = $this->em->getRepository(Lead::class)->findBy([], ['id' => 'DESC']);

            return $this->render('admin/leads.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'leads' => $leads,
            ]);
        }

        if ($section === 'tasks') {
            $tasks = $this->em->getRepository(Task::class)->findBy([], ['id' => 'DESC']);

            return $this->render('admin/tasks.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'tasks' => $tasks,
            ]);
        }

        if ($section === 'sales') {
            $sales = $this->em->getRepository(Sale::class)->findBy([], ['id' => 'DESC']);

            return $this->render('admin/sales.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'sales' => $sales,
            ]);
        }

        if ($section === 'visits') {
            $visits = $this->em->getRepository(Booking::class)->findBy([], ['id' => 'DESC']);

            return $this->render('admin/visits.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'visits' => $visits,
            ]);
        }

        if ($section === 'schedule') {
            $trainings = $this->em->getRepository(Training::class)->findBy([], ['startAt' => 'ASC']);
            $trainers = $this->em->getRepository(Trainer::class)->findBy([], ['id' => 'ASC']);

            return $this->render('admin/schedule.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'trainings' => $trainings,
                'trainers' => $trainers,
            ]);
        }

        if ($section === 'staff') {
            $trainers = $this->em->getRepository(Trainer::class)->findBy([], ['id' => 'ASC']);

            return $this->render('admin/staff.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'trainers' => $trainers,
            ]);
        }

        if ($section === 'selfservice') {
            $logs = $this->em->getRepository(AccessLog::class)->findBy([], ['id' => 'DESC']);

            return $this->render('admin/selfservice.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'logs' => $logs,
            ]);
        }

        return $this->render('admin/section.html.twig', [
            'menu' => $menu,
            'current' => $section,
            'title' => $menu[$section],
        ]);
    }
}

