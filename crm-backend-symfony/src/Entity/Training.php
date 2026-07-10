<?php

namespace App\Entity;

use App\Entity\Contract\TenantAware;
use App\Entity\Trait\OrganizationOwnedTrait;
use Doctrine\ORM\Mapping as ORM;
use App\Entity\Trainer;

#[ORM\Entity]
#[ORM\Table(name: 'trainings')]
class Training implements TenantAware
{
    use OrganizationOwnedTrait;
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 150)]
    private string $name;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $description = null;

    #[ORM\Column(type: 'string', length: 50)]
    private string $type = 'group'; // group, personal, extra (допуслуги / запись на услугу)

    #[ORM\Column(type: 'string', length: 100, nullable: true)]
    private ?string $trainerName = null;

    #[ORM\ManyToOne(targetEntity: Trainer::class)]
    #[ORM\JoinColumn(nullable: true)]
    private ?Trainer $trainer = null;

    #[ORM\Column(type: 'datetime_immutable')]
    private \DateTimeImmutable $startAt;

    #[ORM\Column(type: 'datetime_immutable')]
    private \DateTimeImmutable $endAt;

    #[ORM\Column(type: 'string', length: 100, nullable: true)]
    private ?string $room = null;

    #[ORM\Column(type: 'integer')]
    private int $maxParticipants = 0;

    #[ORM\Column(type: 'integer')]
    private int $currentParticipants = 0;

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getName(): string
    {
        return $this->name;
    }

    public function setName(string $name): self
    {
        $this->name = $name;
        return $this;
    }

    public function getDescription(): ?string
    {
        return $this->description;
    }

    public function setDescription(?string $description): self
    {
        $this->description = $description;
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

    public function getTrainerName(): ?string
    {
        return $this->trainerName;
    }

    public function setTrainerName(?string $trainerName): self
    {
        $this->trainerName = $trainerName;
        return $this;
    }

    public function getTrainer(): ?Trainer
    {
        return $this->trainer;
    }

    public function setTrainer(?Trainer $trainer): self
    {
        $this->trainer = $trainer;
        return $this;
    }

    public function getStartAt(): \DateTimeImmutable
    {
        return $this->startAt;
    }

    public function setStartAt(\DateTimeImmutable $startAt): self
    {
        $this->startAt = $startAt;
        return $this;
    }

    public function getEndAt(): \DateTimeImmutable
    {
        return $this->endAt;
    }

    public function setEndAt(\DateTimeImmutable $endAt): self
    {
        $this->endAt = $endAt;
        return $this;
    }

    public function getRoom(): ?string
    {
        return $this->room;
    }

    public function setRoom(?string $room): self
    {
        $this->room = $room;
        return $this;
    }

    public function getMaxParticipants(): int
    {
        return $this->maxParticipants;
    }

    public function setMaxParticipants(int $maxParticipants): self
    {
        $this->maxParticipants = $maxParticipants;
        return $this;
    }

    public function getCurrentParticipants(): int
    {
        return $this->currentParticipants;
    }

    public function setCurrentParticipants(int $currentParticipants): self
    {
        $this->currentParticipants = $currentParticipants;
        return $this;
    }
}

