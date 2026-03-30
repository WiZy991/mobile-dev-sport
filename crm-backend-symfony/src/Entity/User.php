<?php

namespace App\Entity;

use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'users')]
class User
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 180, unique: true)]
    private string $email;

    #[ORM\Column(type: 'string', length: 32)]
    private string $phone;

    #[ORM\Column(type: 'string', length: 100)]
    private string $name;

    #[ORM\Column(type: 'date_immutable', nullable: true)]
    private ?\DateTimeImmutable $dateOfBirth = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $placeOfBirth = null;

    #[ORM\Column(type: 'string', length: 10, nullable: true)]
    private ?string $passportSeries = null;

    #[ORM\Column(type: 'string', length: 10, nullable: true)]
    private ?string $passportNumber = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $passportIssuedBy = null;

    #[ORM\Column(type: 'date_immutable', nullable: true)]
    private ?\DateTimeImmutable $passportIssueDate = null;

    #[ORM\Column(type: 'string', length: 10, nullable: true)]
    private ?string $passportDepartmentCode = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $registrationAddress = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $emergencyContact = null;

    #[ORM\Column(type: 'string', length: 20, nullable: true)]
    private ?string $gender = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $avatarUrl = null;

    #[ORM\Column(type: 'integer')]
    private int $bonusPoints = 0;

    #[ORM\Column(type: 'boolean')]
    private bool $isBlocked = false;

    #[ORM\Column(type: 'datetime_immutable')]
    private \DateTimeImmutable $createdAt;

    #[ORM\ManyToMany(targetEntity: Tag::class, inversedBy: 'users')]
    #[ORM\JoinTable(name: 'user_tags')]
    private Collection $tags;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
        $this->tags = new ArrayCollection();
    }

    /** @return Collection<int, Tag> */
    public function getTags(): Collection
    {
        return $this->tags;
    }

    public function addTag(Tag $tag): self
    {
        if (!$this->tags->contains($tag)) {
            $this->tags->add($tag);
        }
        return $this;
    }

    public function removeTag(Tag $tag): self
    {
        $this->tags->removeElement($tag);
        return $this;
    }

    public function clearTags(): self
    {
        $this->tags->clear();
        return $this;
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getEmail(): string
    {
        return $this->email;
    }

    public function setEmail(string $email): self
    {
        $this->email = $email;
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

    public function getName(): string
    {
        return $this->name;
    }

    public function setName(string $name): self
    {
        $this->name = $name;
        return $this;
    }

    public function getDateOfBirth(): ?\DateTimeImmutable
    {
        return $this->dateOfBirth;
    }

    public function setDateOfBirth(?\DateTimeImmutable $dateOfBirth): self
    {
        $this->dateOfBirth = $dateOfBirth;
        return $this;
    }

    public function getPlaceOfBirth(): ?string
    {
        return $this->placeOfBirth;
    }

    public function setPlaceOfBirth(?string $placeOfBirth): self
    {
        $this->placeOfBirth = $placeOfBirth;
        return $this;
    }

    public function getPassportSeries(): ?string
    {
        return $this->passportSeries;
    }

    public function setPassportSeries(?string $passportSeries): self
    {
        $this->passportSeries = $passportSeries;
        return $this;
    }

    public function getPassportNumber(): ?string
    {
        return $this->passportNumber;
    }

    public function setPassportNumber(?string $passportNumber): self
    {
        $this->passportNumber = $passportNumber;
        return $this;
    }

    public function getPassportIssuedBy(): ?string
    {
        return $this->passportIssuedBy;
    }

    public function setPassportIssuedBy(?string $passportIssuedBy): self
    {
        $this->passportIssuedBy = $passportIssuedBy;
        return $this;
    }

    public function getPassportIssueDate(): ?\DateTimeImmutable
    {
        return $this->passportIssueDate;
    }

    public function setPassportIssueDate(?\DateTimeImmutable $passportIssueDate): self
    {
        $this->passportIssueDate = $passportIssueDate;
        return $this;
    }

    public function getPassportDepartmentCode(): ?string
    {
        return $this->passportDepartmentCode;
    }

    public function setPassportDepartmentCode(?string $passportDepartmentCode): self
    {
        $this->passportDepartmentCode = $passportDepartmentCode;
        return $this;
    }

    public function getRegistrationAddress(): ?string
    {
        return $this->registrationAddress;
    }

    public function setRegistrationAddress(?string $registrationAddress): self
    {
        $this->registrationAddress = $registrationAddress;
        return $this;
    }

    public function getEmergencyContact(): ?string
    {
        return $this->emergencyContact;
    }

    public function setEmergencyContact(?string $emergencyContact): self
    {
        $this->emergencyContact = $emergencyContact;
        return $this;
    }

    public function getGender(): ?string
    {
        return $this->gender;
    }

    public function setGender(?string $gender): self
    {
        $this->gender = $gender;
        return $this;
    }

    public function hasRequiredDataForSubscription(): bool
    {
        return $this->passportSeries !== null && $this->passportSeries !== ''
            && $this->passportNumber !== null && $this->passportNumber !== ''
            && $this->dateOfBirth !== null;
    }

    public function getAvatarUrl(): ?string
    {
        return $this->avatarUrl;
    }

    public function setAvatarUrl(?string $avatarUrl): self
    {
        $this->avatarUrl = $avatarUrl;
        return $this;
    }

    public function getBonusPoints(): int
    {
        return $this->bonusPoints;
    }

    public function setBonusPoints(int $bonusPoints): self
    {
        $this->bonusPoints = $bonusPoints;
        return $this;
    }

    public function isBlocked(): bool
    {
        return $this->isBlocked;
    }

    public function setIsBlocked(bool $isBlocked): self
    {
        $this->isBlocked = $isBlocked;
        return $this;
    }

    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
    }
}

