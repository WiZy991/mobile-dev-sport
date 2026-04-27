<?php

namespace App\Entity;

use Doctrine\ORM\Mapping as ORM;

/**
 * Команда от CRM к ПК-шлюзу клуба.
 * Шлюз long-poll'ит /api/v1/gateway/commands и подтверждает выполнение через /commands/{id}/ack.
 */
#[ORM\Entity]
#[ORM\Table(name: 'gateway_commands')]
#[ORM\Index(name: 'IDX_gateway_commands_club_status', columns: ['club_id', 'status'])]
#[ORM\Index(name: 'IDX_gateway_commands_created', columns: ['created_at'])]
class GatewayCommand
{
    public const STATUS_PENDING = 'pending';
    public const STATUS_DELIVERED = 'delivered';
    public const STATUS_DONE = 'done';
    public const STATUS_FAILED = 'failed';
    public const STATUS_EXPIRED = 'expired';

    /** Открыть дверь (вызвать device/command в локальном PERCo). */
    public const KIND_OPEN_DOOR = 'open_door';
    /** Передать сырой вызов в PERCo API (METHOD + PATH + JSON-тело). */
    public const KIND_PERCO_CALL = 'perco_call';
    /** Простая проверка связности. */
    public const KIND_PING = 'ping';

    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Club::class)]
    #[ORM\JoinColumn(name: 'club_id', referencedColumnName: 'id', nullable: false, onDelete: 'CASCADE')]
    private Club $club;

    #[ORM\Column(type: 'string', length: 40)]
    private string $kind = self::KIND_PING;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $payloadJson = null;

    #[ORM\Column(type: 'string', length: 20)]
    private string $status = self::STATUS_PENDING;

    #[ORM\Column(type: 'datetime')]
    private \DateTimeInterface $createdAt;

    #[ORM\Column(type: 'datetime', nullable: true)]
    private ?\DateTimeInterface $deliveredAt = null;

    #[ORM\Column(type: 'datetime', nullable: true)]
    private ?\DateTimeInterface $doneAt = null;

    #[ORM\Column(type: 'datetime', nullable: true)]
    private ?\DateTimeInterface $expiresAt = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $resultJson = null;

    #[ORM\Column(type: 'string', length: 150, nullable: true)]
    private ?string $issuedBy = null;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
    }

    public function getId(): ?int { return $this->id; }

    public function getClub(): Club { return $this->club; }
    public function setClub(Club $club): self { $this->club = $club; return $this; }

    public function getKind(): string { return $this->kind; }
    public function setKind(string $kind): self { $this->kind = $kind; return $this; }

    public function getPayloadJson(): ?string { return $this->payloadJson; }
    public function setPayloadJson(?string $v): self { $this->payloadJson = $v; return $this; }

    /** @return array<string, mixed> */
    public function getPayload(): array
    {
        if (!$this->payloadJson) return [];
        $v = json_decode($this->payloadJson, true);
        return is_array($v) ? $v : [];
    }

    /** @param array<string, mixed> $payload */
    public function setPayload(array $payload): self
    {
        $this->payloadJson = $payload ? json_encode($payload, JSON_UNESCAPED_UNICODE) : null;
        return $this;
    }

    public function getStatus(): string { return $this->status; }
    public function setStatus(string $status): self { $this->status = $status; return $this; }

    public function getCreatedAt(): \DateTimeInterface { return $this->createdAt; }
    public function setCreatedAt(\DateTimeInterface $v): self { $this->createdAt = $v; return $this; }

    public function getDeliveredAt(): ?\DateTimeInterface { return $this->deliveredAt; }
    public function setDeliveredAt(?\DateTimeInterface $v): self { $this->deliveredAt = $v; return $this; }

    public function getDoneAt(): ?\DateTimeInterface { return $this->doneAt; }
    public function setDoneAt(?\DateTimeInterface $v): self { $this->doneAt = $v; return $this; }

    public function getExpiresAt(): ?\DateTimeInterface { return $this->expiresAt; }
    public function setExpiresAt(?\DateTimeInterface $v): self { $this->expiresAt = $v; return $this; }

    public function getResultJson(): ?string { return $this->resultJson; }
    public function setResultJson(?string $v): self { $this->resultJson = $v; return $this; }

    /** @param array<string, mixed> $result */
    public function setResult(array $result): self
    {
        $this->resultJson = $result ? json_encode($result, JSON_UNESCAPED_UNICODE) : null;
        return $this;
    }

    public function getIssuedBy(): ?string { return $this->issuedBy; }
    public function setIssuedBy(?string $v): self { $this->issuedBy = $v; return $this; }
}
