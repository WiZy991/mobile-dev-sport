<?php

namespace App\Entity;

use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'leads')]
class Lead
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 100)]
    private string $name;

    #[ORM\Column(type: 'string', length: 32)]
    private string $phone;

    #[ORM\Column(type: 'string', length: 150, nullable: true)]
    private ?string $email = null;

    #[ORM\Column(type: 'string', length: 50)]
    private string $status = 'new'; // new, trial_scheduled, trial_visited, converted, inactive

    #[ORM\ManyToOne(targetEntity: User::class)]
    #[ORM\JoinColumn(nullable: true)]
    private ?User $convertedUser = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $comment = null;

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $source = null; // site, instagram, vk, telegram, referral, guest_pass, support, call, walk_in, other

    #[ORM\Column(type: 'datetime_immutable')]
    private \DateTimeImmutable $createdAt;

    /** @var Collection<int, LeadNote> */
    #[ORM\OneToMany(targetEntity: LeadNote::class, mappedBy: 'lead', cascade: ['persist', 'remove'], orphanRemoval: true)]
    #[ORM\OrderBy(['createdAt' => 'DESC'])]
    private Collection $notes;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
        $this->notes = new ArrayCollection();
    }

    /** @return Collection<int, LeadNote> */
    public function getNotes(): Collection
    {
        return $this->notes;
    }

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

    public function getPhone(): string
    {
        return $this->phone;
    }

    public function setPhone(string $phone): self
    {
        $this->phone = $phone;
        return $this;
    }

    public function getEmail(): ?string
    {
        return $this->email;
    }

    public function setEmail(?string $email): self
    {
        $this->email = $email;
        return $this;
    }

    public function getConvertedUser(): ?User
    {
        return $this->convertedUser;
    }

    public function setConvertedUser(?User $user): self
    {
        $this->convertedUser = $user;
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

    public function getComment(): ?string
    {
        return $this->comment;
    }

    public function setComment(?string $comment): self
    {
        $this->comment = $comment;
        return $this;
    }

    public function getSource(): ?string { return $this->source; }
    public function setSource(?string $source): self { $this->source = $source; return $this; }

    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
    }
}

