<?php

declare(strict_types=1);

namespace App\Entity;

use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'phone_otp_challenges')]
#[ORM\Index(name: 'idx_phone_otp_phone_created', columns: ['phone', 'created_at'])]
#[ORM\UniqueConstraint(name: 'uniq_phone_otp_deeplink', columns: ['deeplink_token'])]
#[ORM\UniqueConstraint(name: 'uniq_phone_otp_reg_ticket', columns: ['registration_ticket'])]
class PhoneOtpChallenge
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 32)]
    private string $phone;

    #[ORM\Column(type: 'string', length: 20)]
    private string $channel;

    #[ORM\Column(name: 'code_hash', type: 'string', length: 255)]
    private string $codeHash;

    #[ORM\Column(name: 'expires_at', type: 'datetime_immutable')]
    private \DateTimeImmutable $expiresAt;

    #[ORM\Column(type: 'integer')]
    private int $attempts = 0;

    #[ORM\Column(name: 'sent_at', type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $sentAt = null;

    /** Временно для Max: код до доставки в чат, затем обнуляется. */
    #[ORM\Column(name: 'delivery_code', type: 'string', length: 8, nullable: true)]
    private ?string $deliveryCode = null;

    #[ORM\Column(name: 'deeplink_token', type: 'string', length: 64, nullable: true)]
    private ?string $deeplinkToken = null;

    #[ORM\Column(name: 'registration_ticket', type: 'string', length: 64, nullable: true)]
    private ?string $registrationTicket = null;

    #[ORM\Column(name: 'registration_ticket_expires_at', type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $registrationTicketExpiresAt = null;

    #[ORM\Column(name: 'consumed_at', type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $consumedAt = null;

    #[ORM\Column(name: 'created_at', type: 'datetime_immutable')]
    private \DateTimeImmutable $createdAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getPhone(): string
    {
        return $this->phone;
    }

    public function setPhone(string $phone): self
    {
        $this->phone = $phone;

        return $this;
    }

    public function getChannel(): string
    {
        return $this->channel;
    }

    public function setChannel(string $channel): self
    {
        $this->channel = $channel;

        return $this;
    }

    public function getCodeHash(): string
    {
        return $this->codeHash;
    }

    public function setCodeHash(string $codeHash): self
    {
        $this->codeHash = $codeHash;

        return $this;
    }

    public function getExpiresAt(): \DateTimeImmutable
    {
        return $this->expiresAt;
    }

    public function setExpiresAt(\DateTimeImmutable $expiresAt): self
    {
        $this->expiresAt = $expiresAt;

        return $this;
    }

    public function getAttempts(): int
    {
        return $this->attempts;
    }

    public function incrementAttempts(): self
    {
        ++$this->attempts;

        return $this;
    }

    public function resetAttempts(): self
    {
        $this->attempts = 0;

        return $this;
    }

    public function getSentAt(): ?\DateTimeImmutable
    {
        return $this->sentAt;
    }

    public function setSentAt(?\DateTimeImmutable $sentAt): self
    {
        $this->sentAt = $sentAt;

        return $this;
    }

    public function getDeliveryCode(): ?string
    {
        return $this->deliveryCode;
    }

    public function setDeliveryCode(?string $deliveryCode): self
    {
        $this->deliveryCode = $deliveryCode;

        return $this;
    }

    public function getDeeplinkToken(): ?string
    {
        return $this->deeplinkToken;
    }

    public function setDeeplinkToken(?string $deeplinkToken): self
    {
        $this->deeplinkToken = $deeplinkToken;

        return $this;
    }

    public function getRegistrationTicket(): ?string
    {
        return $this->registrationTicket;
    }

    public function setRegistrationTicket(?string $registrationTicket): self
    {
        $this->registrationTicket = $registrationTicket;

        return $this;
    }

    public function getRegistrationTicketExpiresAt(): ?\DateTimeImmutable
    {
        return $this->registrationTicketExpiresAt;
    }

    public function setRegistrationTicketExpiresAt(?\DateTimeImmutable $registrationTicketExpiresAt): self
    {
        $this->registrationTicketExpiresAt = $registrationTicketExpiresAt;

        return $this;
    }

    public function getConsumedAt(): ?\DateTimeImmutable
    {
        return $this->consumedAt;
    }

    public function setConsumedAt(?\DateTimeImmutable $consumedAt): self
    {
        $this->consumedAt = $consumedAt;

        return $this;
    }

    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
    }

    public function isExpired(): bool
    {
        return $this->expiresAt <= new \DateTimeImmutable();
    }

    public function isConsumed(): bool
    {
        return $this->consumedAt !== null;
    }
}
