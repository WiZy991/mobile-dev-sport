<?php

namespace App\Entity;

use App\Entity\Contract\TenantAware;
use App\Entity\Trait\OrganizationOwnedTrait;
use Doctrine\ORM\Mapping as ORM;

/**
 * Тревога доступа: например, проход вдвоём по одному QR (tailgating), зафиксированный камерой.
 *
 * Дверь уже открыта СКУД по одному QR; камера IVS считает пересечения линии входа. Если прошло
 * больше людей, чем разрешено (>= порога), агент клуба шлёт сюда событие, а CRM уведомляет
 * клиента и персонал (админ/менеджер).
 */
#[ORM\Entity]
#[ORM\Table(name: 'access_alarms')]
#[ORM\Index(name: 'idx_access_alarms_created_at', columns: ['created_at'])]
#[ORM\Index(name: 'idx_access_alarms_club_created', columns: ['club_id', 'created_at'])]
class AccessAlarm implements TenantAware
{
    use OrganizationOwnedTrait;

    public const TYPE_TAILGATING = 'tailgating';
    public const TYPE_GROUP_ENTRY = 'group_entry';

    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Club::class)]
    #[ORM\JoinColumn(name: 'club_id', referencedColumnName: 'id', nullable: true, onDelete: 'SET NULL')]
    private ?Club $club = null;

    #[ORM\ManyToOne(targetEntity: User::class)]
    #[ORM\JoinColumn(nullable: true, onDelete: 'SET NULL')]
    private ?User $user = null;

    /** Связанный лог входа (по тому же QR), если найден. */
    #[ORM\ManyToOne(targetEntity: AccessLog::class)]
    #[ORM\JoinColumn(name: 'access_log_id', referencedColumnName: 'id', nullable: true, onDelete: 'SET NULL')]
    private ?AccessLog $accessLog = null;

    #[ORM\Column(type: 'string', length: 30)]
    private string $type = self::TYPE_TAILGATING;

    #[ORM\Column(type: 'string', length: 100, nullable: true)]
    private ?string $deviceId = null;

    #[ORM\Column(type: 'integer')]
    private int $peopleCount = 2;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $rawData = null;

    /** @var array<string, mixed>|null */
    #[ORM\Column(type: 'json', nullable: true)]
    private ?array $details = null;

    #[ORM\Column(type: 'datetime_immutable')]
    private \DateTimeImmutable $createdAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getClub(): ?Club
    {
        return $this->club;
    }

    public function setClub(?Club $club): self
    {
        $this->club = $club;
        return $this;
    }

    public function getUser(): ?User
    {
        return $this->user;
    }

    public function setUser(?User $user): self
    {
        $this->user = $user;
        return $this;
    }

    public function getAccessLog(): ?AccessLog
    {
        return $this->accessLog;
    }

    public function setAccessLog(?AccessLog $accessLog): self
    {
        $this->accessLog = $accessLog;
        return $this;
    }

    public function getType(): string
    {
        return $this->type;
    }

    public function setType(string $type): self
    {
        $this->type = $type;
        return $this;
    }

    public function getDeviceId(): ?string
    {
        return $this->deviceId;
    }

    public function setDeviceId(?string $deviceId): self
    {
        $this->deviceId = $deviceId;
        return $this;
    }

    public function getPeopleCount(): int
    {
        return $this->peopleCount;
    }

    public function setPeopleCount(int $peopleCount): self
    {
        $this->peopleCount = $peopleCount;
        return $this;
    }

    public function getRawData(): ?string
    {
        return $this->rawData;
    }

    public function setRawData(?string $rawData): self
    {
        $this->rawData = $rawData;
        return $this;
    }

    /** @return array<string, mixed>|null */
    public function getDetails(): ?array
    {
        return $this->details;
    }

    /** @param array<string, mixed>|null $details */
    public function setDetails(?array $details): self
    {
        $this->details = $details;
        return $this;
    }

    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
    }
}
