<?php

namespace App\Service\Api;

use App\Entity\AccessLog;
use App\Entity\Booking;
use App\Entity\ClientNote;
use App\Entity\Document;
use App\Entity\Expense;
use App\Entity\Lead;
use App\Entity\Product;
use App\Entity\PromoCode;
use App\Entity\Promotion;
use App\Entity\Sale;
use App\Entity\StaffUser;
use App\Entity\Subscription;
use App\Entity\SupportTicket;
use App\Entity\Tag;
use App\Entity\Task;
use App\Entity\Trainer;
use App\Entity\Training;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Данные разделов CRM для мобильной админки staff-app.
 */
final class StaffAdminSectionData
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    /** @return list<array{key: string, value: int|float}> */
    public function cards(string $section): array
    {
        return match ($section) {
            'dashboard' => [
                ['key' => 'all_clients', 'value' => $this->em->getRepository(User::class)->count([])],
                ['key' => 'all_bookings', 'value' => $this->em->getRepository(Booking::class)->count([])],
                ['key' => 'tasks_open', 'value' => $this->em->getRepository(Task::class)->count(['status' => 'open'])],
                ['key' => 'support_new', 'value' => $this->em->getRepository(SupportTicket::class)->count(['status' => SupportTicket::STATUS_NEW])],
            ],
            'clients' => [
                ['key' => 'all_clients', 'value' => $this->em->getRepository(User::class)->count([])],
            ],
            'bookings' => [
                ['key' => 'all_bookings', 'value' => $this->em->getRepository(Booking::class)->count([])],
            ],
            'subscriptions' => [
                ['key' => 'all_subscriptions', 'value' => $this->em->getRepository(Subscription::class)->count([])],
            ],
            'tasks' => [
                ['key' => 'tasks_open', 'value' => $this->em->getRepository(Task::class)->count(['status' => 'open'])],
                ['key' => 'tasks_done', 'value' => $this->em->getRepository(Task::class)->count(['status' => 'done'])],
            ],
            'leads' => [
                ['key' => 'leads_new', 'value' => $this->em->getRepository(Lead::class)->count(['status' => 'new'])],
                ['key' => 'leads_converted', 'value' => $this->em->getRepository(Lead::class)->count(['status' => 'converted'])],
            ],
            'deals' => [
                ['key' => 'deals_total', 'value' => $this->em->getRepository(Lead::class)->count(['status' => 'converted'])],
            ],
            'sales' => [
                ['key' => 'sales_total', 'value' => $this->em->getRepository(Sale::class)->count([])],
            ],
            'visits' => [
                ['key' => 'visits_today', 'value' => $this->countAccessLogsToday()],
            ],
            'trainers' => [
                ['key' => 'trainers_total', 'value' => $this->em->getRepository(Trainer::class)->count([])],
            ],
            'documents' => [
                ['key' => 'documents_total', 'value' => $this->em->getRepository(Document::class)->count([])],
            ],
            'crm_staff' => [
                ['key' => 'staff_total', 'value' => $this->em->getRepository(StaffUser::class)->count([])],
            ],
            'app_support' => [
                ['key' => 'support_new', 'value' => $this->em->getRepository(SupportTicket::class)->count(['status' => SupportTicket::STATUS_NEW])],
            ],
            'finance' => $this->financeCards(),
            'services' => [
                ['key' => 'products_total', 'value' => $this->em->getRepository(Product::class)->count(['isActive' => true])],
            ],
            'promocodes' => [
                ['key' => 'promocodes_total', 'value' => $this->em->getRepository(PromoCode::class)->count([])],
            ],
            'promotions' => [
                ['key' => 'promotions_total', 'value' => $this->em->getRepository(Promotion::class)->count([])],
            ],
            'tags' => [
                ['key' => 'tags_total', 'value' => $this->em->getRepository(Tag::class)->count([])],
            ],
            'comments' => [
                ['key' => 'comments_total', 'value' => $this->em->getRepository(ClientNote::class)->count([])],
            ],
            'schedule' => [
                ['key' => 'schedule_today', 'value' => $this->countTrainingsInDays(0)],
                ['key' => 'schedule_week', 'value' => $this->countTrainingsThisWeek()],
            ],
            default => [
                ['key' => 'items_total', 'value' => 0],
            ],
        };
    }

    /** @return list<array{title: string, subtitle: string, meta: string, id?: int, refType?: string}> */
    public function items(string $section): array
    {
        return match ($section) {
            'dashboard' => array_slice(array_merge(
                $this->items('tasks'),
                $this->items('leads'),
                $this->items('bookings'),
            ), 0, 20),
            'clients' => array_map(fn (User $client) => $this->row(
                $client->getName(),
                (string) ($client->getPhone() ?? ''),
                $client->getEmail(),
                $client->getId(),
                'client',
            ), $this->em->getRepository(User::class)->findBy([], ['name' => 'ASC'], 50)),
            'bookings' => array_map(function (Booking $booking) {
                $training = $booking->getTraining();

                return $this->row(
                    $booking->getClientName(),
                    $training->getName(),
                    $training->getStartAt()->format('d.m.Y H:i') . ' · ' . $booking->getStatus(),
                    $booking->getUser()?->getId(),
                    $booking->getUser() !== null ? 'client' : null,
                );
            }, $this->em->createQueryBuilder()
                ->select('b')
                ->from(Booking::class, 'b')
                ->join('b.training', 't')
                ->where('b.status != :cancelled')
                ->setParameter('cancelled', 'cancelled')
                ->orderBy('t.startAt', 'DESC')
                ->setMaxResults(50)
                ->getQuery()
                ->getResult()),
            'tasks' => array_map(fn (Task $task) => $this->row(
                $task->getTitle(),
                (string) ($task->getClientName() ?? 'Без клиента'),
                ($task->getDueAt()?->format('d.m.Y') ?? 'Без срока') . ' · ' . $task->getStatus(),
                $task->getId(),
                'task',
            ), $this->em->getRepository(Task::class)->findBy([], ['id' => 'DESC'], 50)),
            'subscriptions' => array_map(fn (Subscription $sub) => $this->row(
                $sub->getUser()?->getName() ?? 'Клиент',
                $sub->getPlan()?->getName() ?? 'Абонемент',
                $sub->getStatus(),
                $sub->getUser()?->getId(),
                $sub->getUser() !== null ? 'client' : null,
            ), $this->em->getRepository(Subscription::class)->findBy([], ['id' => 'DESC'], 40)),
            'leads' => array_map(fn (Lead $lead) => $this->row(
                $lead->getName(),
                $lead->getPhone(),
                $this->leadStatusLabel($lead->getStatus()) . ' · ' . $lead->getCreatedAt()->format('d.m.Y'),
                $lead->getId(),
                'lead',
            ), $this->em->getRepository(Lead::class)->findBy([], ['id' => 'DESC'], 50)),
            'deals' => array_map(fn (Lead $lead) => $this->row(
                $lead->getName(),
                $lead->getPhone(),
                $lead->getCreatedAt()->format('d.m.Y'),
                $lead->getConvertedUser()?->getId(),
                $lead->getConvertedUser() !== null ? 'client' : 'lead',
            ), $this->em->getRepository(Lead::class)->findBy(['status' => 'converted'], ['id' => 'DESC'], 40)),
            'sales' => array_map(fn (Sale $sale) => $this->row(
                $sale->getClientName(),
                $sale->getProductName(),
                number_format($sale->getTotal(), 0, '.', ' ') . ' ₽ · ' . $sale->getCreatedAt()->format('d.m.Y H:i'),
                $sale->getUser()?->getId(),
                $sale->getUser() !== null ? 'client' : null,
            ), $this->em->getRepository(Sale::class)->findBy([], ['createdAt' => 'DESC'], 40)),
            'visits' => array_map(function (AccessLog $log) {
                $user = $log->getUser();

                return $this->row(
                    $user?->getName() ?? 'Гость',
                    $log->getEventType() === 'exit' ? 'Выход' : 'Вход',
                    $log->getCreatedAt()->format('d.m.Y H:i') . ' · ' . ($log->getResult() === 'granted' ? 'разрешено' : 'отказ'),
                    $user?->getId(),
                    $user !== null ? 'client' : null,
                );
            }, $this->em->getRepository(AccessLog::class)->findBy([], ['createdAt' => 'DESC'], 40)),
            'schedule' => array_map(function (Training $training) {
                return $this->row(
                    $training->getName(),
                    $training->getTrainerName() ?? ($training->getTrainer()?->getName() ?? 'Тренер'),
                    $training->getStartAt()->format('d.m.Y H:i') . ' · ' . $training->getRoom(),
                    $training->getId(),
                    'training',
                );
            }, $this->em->createQueryBuilder()
                ->select('t')
                ->from(Training::class, 't')
                ->where('t.startAt >= :from')
                ->setParameter('from', new \DateTimeImmutable('today'))
                ->orderBy('t.startAt', 'ASC')
                ->setMaxResults(40)
                ->getQuery()
                ->getResult()),
            'trainers' => array_map(fn (Trainer $trainer) => $this->row(
                $trainer->getName(),
                (string) ($trainer->getSpecialization() ?? ''),
                $trainer->getRating() !== null ? 'Рейтинг: ' . $trainer->getRating() : '',
                $trainer->getId(),
                'trainer',
            ), $this->em->getRepository(Trainer::class)->findBy([], ['name' => 'ASC'], 40)),
            'documents' => array_map(fn (Document $doc) => $this->row(
                $doc->getName(),
                (string) ($doc->getCategory() ?? 'Без категории'),
                $doc->getCreatedAt()->format('d.m.Y'),
                $doc->getId(),
                'document',
            ), $this->em->getRepository(Document::class)->findBy([], ['createdAt' => 'DESC'], 40)),
            'crm_staff' => array_map(fn (StaffUser $staff) => $this->row(
                $staff->getName(),
                $staff->getEmail(),
                implode(', ', $staff->getRoles()),
                $staff->getId(),
                'staff',
            ), $this->em->getRepository(StaffUser::class)->findBy(['isActive' => true], ['name' => 'ASC'], 40)),
            'app_support' => array_map(fn (SupportTicket $ticket) => $this->row(
                $ticket->getSubject(),
                $ticket->getUser()?->getName() ?? $ticket->getContactEmail() ?? 'Клиент',
                $this->ticketStatusLabel($ticket->getStatus()) . ' · ' . $ticket->getCreatedAt()->format('d.m.Y H:i'),
                $ticket->getId(),
                'ticket',
            ), $this->em->getRepository(SupportTicket::class)->findBy([], ['createdAt' => 'DESC'], 40)),
            'services' => array_map(fn (Product $product) => $this->row(
                $product->getName(),
                (string) ($product->getCategory() ?? ''),
                number_format($product->getPrice(), 0, '.', ' ') . ' ₽',
                $product->getId(),
                'product',
            ), $this->em->getRepository(Product::class)->findBy(['isActive' => true], ['name' => 'ASC'], 40)),
            'finance' => array_map(fn (Expense $expense) => $this->row(
                $expense->getDescription(),
                (string) ($expense->getCategory() ?? 'Расход'),
                number_format($expense->getAmount(), 0, '.', ' ') . ' ₽ · ' . $expense->getDate()->format('d.m.Y'),
                $expense->getId(),
                'expense',
            ), $this->em->getRepository(Expense::class)->findBy([], ['date' => 'DESC'], 40)),
            'promocodes' => array_map(function (PromoCode $promo) {
                $discount = $promo->getDiscountPercent() !== null
                    ? '−' . $promo->getDiscountPercent() . '%'
                    : ($promo->getDiscountAmount() !== null ? '−' . $promo->getDiscountAmount() . ' ₽' : 'Скидка');

                return $this->row(
                    $promo->getCode(),
                    $discount,
                    'Использовано: ' . $promo->getUsedCount(),
                    $promo->getId(),
                    'promocode',
                );
            }, $this->em->getRepository(PromoCode::class)->findBy([], ['createdAt' => 'DESC'], 40)),
            'promotions' => array_map(fn (Promotion $promo) => $this->row(
                $promo->getTitle(),
                (string) ($promo->getSubtitle() ?? ''),
                $promo->isActive() ? 'Активна' : 'Неактивна',
                $promo->getId(),
                'promotion',
            ), $this->em->getRepository(Promotion::class)->findBy([], ['sortOrder' => 'ASC'], 40)),
            'tags' => array_map(fn (Tag $tag) => $this->row(
                $tag->getName(),
                (string) ($tag->getColor() ?? ''),
                'Тег',
                $tag->getId(),
                'tag',
            ), $this->em->getRepository(Tag::class)->findBy([], ['name' => 'ASC'], 40)),
            'comments' => array_map(function (ClientNote $note) {
                $client = $note->getClient();

                return $this->row(
                    $client?->getName() ?? 'Клиент',
                    mb_substr($note->getText(), 0, 120),
                    $note->getCreatedAt()->format('d.m.Y H:i'),
                    $client?->getId(),
                    $client !== null ? 'client' : null,
                );
            }, $this->em->getRepository(ClientNote::class)->findBy([], ['createdAt' => 'DESC'], 40)),
            'analytics', 'settings', 'franchise', 'messengers', 'calls', 'selfservice', 'cashdesk', 'warehouse', 'mobileapps' => [
                $this->row(
                    'Раздел «' . $section . '»',
                    'Просмотр и редактирование в полной веб-CRM',
                    'На телефоне доступен список ключевых данных в соседних разделах',
                ),
            ],
            default => [],
        };
    }

    /** @return array{title: string, subtitle: string, meta: string, id?: int, refType?: string} */
    private function row(string $title, string $subtitle, string $meta, ?int $id = null, ?string $refType = null): array
    {
        $row = [
            'title' => $title,
            'subtitle' => $subtitle,
            'meta' => $meta,
        ];
        if ($id !== null) {
            $row['id'] = $id;
        }
        if ($refType !== null) {
            $row['refType'] = $refType;
        }

        return $row;
    }

    /** @return list<array{key: string, value: float|int}> */
    private function financeCards(): array
    {
        $monthStart = new \DateTimeImmutable('first day of this month');
        $monthEnd = $monthStart->modify('+1 month');
        $income = (float) $this->em->createQueryBuilder()
            ->select('COALESCE(SUM(s.total), 0)')
            ->from(Sale::class, 's')
            ->where('s.createdAt >= :from')
            ->andWhere('s.createdAt < :to')
            ->setParameter('from', $monthStart)
            ->setParameter('to', $monthEnd)
            ->getQuery()
            ->getSingleScalarResult();

        return [
            ['key' => 'finance_income_month', 'value' => (int) round($income)],
            ['key' => 'finance_expense_month', 'value' => (int) round($this->sumExpenses($monthStart, $monthEnd))],
        ];
    }

    private function sumExpenses(\DateTimeImmutable $from, \DateTimeImmutable $to): float
    {
        $expenses = $this->em->getRepository(Expense::class)->findBy([], ['date' => 'DESC'], 200);
        $sum = 0.0;
        foreach ($expenses as $expense) {
            if ($expense->getDate() >= $from && $expense->getDate() < $to) {
                $sum += $expense->getAmount();
            }
        }

        return $sum;
    }

    private function countAccessLogsToday(): int
    {
        $start = new \DateTimeImmutable('today');
        $end = $start->modify('+1 day');

        return (int) $this->em->createQueryBuilder()
            ->select('COUNT(a.id)')
            ->from(AccessLog::class, 'a')
            ->where('a.createdAt >= :start')
            ->andWhere('a.createdAt < :end')
            ->setParameter('start', $start)
            ->setParameter('end', $end)
            ->getQuery()
            ->getSingleScalarResult();
    }

    private function countTrainingsInDays(int $offsetDays): int
    {
        $start = (new \DateTimeImmutable('today'))->modify('+' . $offsetDays . ' day');
        $end = $start->modify('+1 day');

        return (int) $this->em->createQueryBuilder()
            ->select('COUNT(t.id)')
            ->from(Training::class, 't')
            ->where('t.startAt >= :start')
            ->andWhere('t.startAt < :end')
            ->setParameter('start', $start)
            ->setParameter('end', $end)
            ->getQuery()
            ->getSingleScalarResult();
    }

    private function countTrainingsThisWeek(): int
    {
        $monday = new \DateTimeImmutable('monday this week');
        $nextMonday = $monday->modify('+1 week');

        return (int) $this->em->createQueryBuilder()
            ->select('COUNT(t.id)')
            ->from(Training::class, 't')
            ->where('t.startAt >= :start')
            ->andWhere('t.startAt < :end')
            ->setParameter('start', $monday)
            ->setParameter('end', $nextMonday)
            ->getQuery()
            ->getSingleScalarResult();
    }

    private function leadStatusLabel(string $status): string
    {
        return match ($status) {
            'new' => 'Новый',
            'trial_scheduled' => 'Пробное назначено',
            'trial_visited' => 'Пробное состоялось',
            'converted' => 'Конвертирован',
            'inactive' => 'Неактивен',
            default => $status,
        };
    }

    private function ticketStatusLabel(string $status): string
    {
        return match ($status) {
            SupportTicket::STATUS_NEW => 'Новое',
            SupportTicket::STATUS_IN_PROGRESS => 'В работе',
            SupportTicket::STATUS_DONE => 'Закрыто',
            default => $status,
        };
    }
}
