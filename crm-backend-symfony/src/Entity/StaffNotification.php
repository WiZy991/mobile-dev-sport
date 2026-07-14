<?php

namespace App\Entity;

use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'staff_notifications')]
class StaffNotification
{
    public const TYPE_SUPPORT_TICKET = 'support_ticket';
    public const TYPE_ACCESS_ALARM = 'access_alarm';
    public const TYPE_LEAD = 'lead';
    public const TYPE_BOOKING = 'booking';
    public const TYPE_SALE = 'sale';
    public const TYPE_PAYMENT = 'payment';
    public const TYPE_SUBSCRIPTION = 'subscription';
    public const TYPE_FEEDBACK = 'feedback';
    public const TYPE_CLIENT = 'client';
    public const TYPE_GUEST_PASS = 'guest_pass';
    public const TYPE_TASK = 'task';
    public const TYPE_SYSTEM = 'system';

    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: StaffUser::class)]
    #[ORM\JoinColumn(nullable: false, onDelete: 'CASCADE')]
    private StaffUser $staffUser;

    #[ORM\Column(type: 'string', length: 50)]
    private string $type = self::TYPE_SUPPORT_TICKET;

    #[ORM\Column(type: 'string', length: 150)]
    private string $title = '';

    #[ORM\Column(type: 'text')]
    private string $body = '';

    #[ORM\Column(type: 'string', length: 100, nullable: true)]
    private ?string $referenceId = null;

    #[ORM\Column(type: 'datetime_immutable')]
    private \DateTimeImmutable $createdAt;

    #[ORM\Column(type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $readAt = null;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getStaffUser(): StaffUser
    {
        return $this->staffUser;
    }

    public function setStaffUser(StaffUser $staffUser): self
    {
        $this->staffUser = $staffUser;

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

    public function getTitle(): string
    {
        return $this->title;
    }

    public function setTitle(string $title): self
    {
        $this->title = $title;

        return $this;
    }

    public function getBody(): string
    {
        return $this->body;
    }

    public function setBody(string $body): self
    {
        $this->body = $body;

        return $this;
    }

    public function getReferenceId(): ?string
    {
        return $this->referenceId;
    }

    public function setReferenceId(?string $referenceId): self
    {
        $this->referenceId = $referenceId;

        return $this;
    }

    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
    }

    public function getReadAt(): ?\DateTimeImmutable
    {
        return $this->readAt;
    }

    public function setReadAt(?\DateTimeImmutable $readAt): self
    {
        $this->readAt = $readAt;

        return $this;
    }

    public function isRead(): bool
    {
        return $this->readAt !== null;
    }
}
