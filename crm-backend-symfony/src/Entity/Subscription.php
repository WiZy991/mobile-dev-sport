<?php

namespace App\Entity;

use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use App\Entity\Sale;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'subscriptions')]
class Subscription
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: User::class)]
    #[ORM\JoinColumn(nullable: false)]
    private User $user;

    #[ORM\ManyToOne(targetEntity: SubscriptionPlan::class)]
    #[ORM\JoinColumn(nullable: false)]
    private SubscriptionPlan $plan;

    #[ORM\Column(type: 'date_immutable')]
    private \DateTimeImmutable $startDate;

    #[ORM\Column(type: 'date_immutable', nullable: true)]
    private ?\DateTimeImmutable $endDate = null;

    #[ORM\Column(type: 'string', length: 20)]
    private string $status = 'active'; // active, frozen, expired

    #[ORM\Column(type: 'integer', nullable: true)]
    private ?int $visitsTotal = null;

    #[ORM\Column(type: 'integer', nullable: true)]
    private ?int $visitsUsed = null;

    #[ORM\Column(type: 'integer', nullable: true)]
    private ?int $freezeDaysTotal = null;

    #[ORM\Column(type: 'integer', nullable: true)]
    private ?int $freezeDaysUsed = null;

    #[ORM\ManyToOne(targetEntity: PromoCode::class)]
    #[ORM\JoinColumn(nullable: true)]
    private ?PromoCode $promoCode = null;

    /** null = действует в любом клубе (наследие до привязки по франшизе). */
    #[ORM\ManyToOne(targetEntity: Club::class)]
    #[ORM\JoinColumn(name: 'club_id', referencedColumnName: 'id', nullable: true, onDelete: 'SET NULL')]
    private ?Club $club = null;

    /** @var Collection<int, Sale> */
    #[ORM\OneToMany(targetEntity: Sale::class, mappedBy: 'subscription')]
    private Collection $sales;

    public function __construct()
    {
        $this->startDate = new \DateTimeImmutable();
        $this->sales = new ArrayCollection();
    }

    /** @return Collection<int, Sale> */
    public function getSales(): Collection
    {
        return $this->sales;
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getUser(): User
    {
        return $this->user;
    }

    public function setUser(User $user): self
    {
        $this->user = $user;
        return $this;
    }

    public function getPlan(): SubscriptionPlan
    {
        return $this->plan;
    }

    public function setPlan(SubscriptionPlan $plan): self
    {
        $this->plan = $plan;
        return $this;
    }

    public function getStartDate(): \DateTimeImmutable
    {
        return $this->startDate;
    }

    public function setStartDate(\DateTimeImmutable $startDate): self
    {
        $this->startDate = $startDate;
        return $this;
    }

    public function getEndDate(): ?\DateTimeImmutable
    {
        return $this->endDate;
    }

    public function setEndDate(?\DateTimeImmutable $endDate): self
    {
        $this->endDate = $endDate;
        return $this;
    }

    public function getStatus(): string
    {
        return $this->status;
    }

    public function setStatus(string $status): self
    {
        $this->status = $status;
        return $this;
    }

    public function getVisitsTotal(): ?int
    {
        return $this->visitsTotal;
    }

    public function setVisitsTotal(?int $visitsTotal): self
    {
        $this->visitsTotal = $visitsTotal;
        return $this;
    }

    public function getVisitsUsed(): ?int
    {
        return $this->visitsUsed;
    }

    public function setVisitsUsed(?int $visitsUsed): self
    {
        $this->visitsUsed = $visitsUsed;
        return $this;
    }

    public function getFreezeDaysTotal(): ?int
    {
        return $this->freezeDaysTotal;
    }

    public function setFreezeDaysTotal(?int $freezeDaysTotal): self
    {
        $this->freezeDaysTotal = $freezeDaysTotal;
        return $this;
    }

    public function getFreezeDaysUsed(): ?int
    {
        return $this->freezeDaysUsed;
    }

    public function setFreezeDaysUsed(?int $freezeDaysUsed): self
    {
        $this->freezeDaysUsed = $freezeDaysUsed;
        return $this;
    }

    public function getPromoCode(): ?PromoCode { return $this->promoCode; }
    public function setPromoCode(?PromoCode $promo): self { $this->promoCode = $promo; return $this; }

    public function getClub(): ?Club
    {
        return $this->club;
    }

    public function setClub(?Club $club): self
    {
        $this->club = $club;
        return $this;
    }

    /**
     * Допуск на турникете клуба $gateClub: если абонемент без клуба — во всех; иначе только в своём.
     * $gateClub = null (legacy /api/v1/access/entry без контекста клуба) — не фильтруем по клубу.
     */
    public function isValidAtClub(?Club $gateClub): bool
    {
        if ($gateClub === null) {
            return true;
        }
        if ($this->club === null) {
            return true;
        }

        return $this->club->getId() === $gateClub->getId();
    }

    /**
     * Календарный период [startDate, endDate] включительно (endDate null — без верхней границы).
     * Сравнение по строке Y-m-d, чтобы совпадать с логикой API и не зависеть от полуночи в часовом поясе.
     */
    public function coversCalendarDay(\DateTimeImmutable $day): bool
    {
        $d = $day->format('Y-m-d');
        if ($d < $this->startDate->format('Y-m-d')) {
            return false;
        }
        if ($this->endDate !== null && $d > $this->endDate->format('Y-m-d')) {
            return false;
        }

        return true;
    }

    /** Абонемент в БД «active» и сегодня попадает в календарный период (как при проверке на турникете). */
    public function isEffectiveActiveOn(\DateTimeImmutable $day): bool
    {
        return $this->status === 'active' && $this->coversCalendarDay($day);
    }
}

