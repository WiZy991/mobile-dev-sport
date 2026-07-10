<?php

namespace App\Entity;

use App\Entity\Contract\TenantAware;
use App\Entity\Trait\OrganizationOwnedTrait;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'users')]
#[ORM\UniqueConstraint(name: 'uniq_user_org_email', columns: ['organization_id', 'email'])]
class User implements TenantAware
{
    use OrganizationOwnedTrait;
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 180)]
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

    /** Опросник при регистрации в приложении: ключ варианта «Откуда узнали о нас». */
    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $referralSource = null;

    /** Свой вариант, если выбрано «Другое». */
    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $referralSourceOther = null;

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

    /** Хеш пароля для входа в мобильное приложение (email + password). */
    #[ORM\Column(name: 'password_hash', type: 'string', length: 255, nullable: true)]
    private ?string $passwordHash = null;

    /** Одноразовый refresh для мобильного клиента (Bearer в POST /auth/refresh). */
    #[ORM\Column(name: 'api_refresh_token', type: 'string', length: 64, nullable: true)]
    private ?string $apiRefreshToken = null;

    /** Короткоживущий access для мобильного API (Bearer на /api/v1/*). */
    #[ORM\Column(name: 'api_access_token', type: 'string', length: 64, nullable: true)]
    private ?string $apiAccessToken = null;

    #[ORM\Column(name: 'api_access_token_expires_at', type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $apiAccessTokenExpiresAt = null;

    /** none | pending | verified | rejected — соответствие паспорта (Сбер ID / др.) */
    #[ORM\Column(type: 'string', length: 20)]
    private string $passportVerificationStatus = 'none';

    #[ORM\Column(type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $passportVerifiedAt = null;

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $passportVerificationProvider = null;

    #[ORM\Column(type: 'string', length: 128, nullable: true)]
    private ?string $passportVerificationSubject = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $passportVerificationAuditJson = null;

    /** Стабильный идентификатор Сбер ID (sub из id_token). */
    #[ORM\Column(name: 'sber_id', type: 'string', length: 128, nullable: true, unique: true)]
    private ?string $sberId = null;

    /** Стабильный идентификатор Sign in with Apple (sub из identity token). */
    #[ORM\Column(name: 'apple_id', type: 'string', length: 128, nullable: true, unique: true)]
    private ?string $appleId = null;

    /** Верифицирован ли профиль через Сбер ID (упрощённый флаг для CRM/API). */
    #[ORM\Column(name: 'is_verified', type: 'boolean')]
    private bool $verified = false;

    #[ORM\Column(type: 'datetime_immutable')]
    private \DateTimeImmutable $createdAt;

    #[ORM\ManyToMany(targetEntity: Tag::class, inversedBy: 'users')]
    #[ORM\JoinTable(name: 'user_tags')]
    private Collection $tags;

    /** Предпочитаемый / основной клуб (фильтр и выгрузка в CRM). */
    #[ORM\ManyToOne(targetEntity: Club::class)]
    #[ORM\JoinColumn(name: 'club_id', referencedColumnName: 'id', nullable: true, onDelete: 'SET NULL')]
    private ?Club $club = null;

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

    public function getClub(): ?Club
    {
        return $this->club;
    }

    public function setClub(?Club $club): self
    {
        $this->club = $club;
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

    public function getReferralSource(): ?string
    {
        return $this->referralSource;
    }

    public function setReferralSource(?string $referralSource): self
    {
        $this->referralSource = $referralSource;
        return $this;
    }

    public function getReferralSourceOther(): ?string
    {
        return $this->referralSourceOther;
    }

    public function setReferralSourceOther(?string $referralSourceOther): self
    {
        $this->referralSourceOther = $referralSourceOther;
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

    public function getPasswordHash(): ?string
    {
        return $this->passwordHash;
    }

    public function setPasswordHash(?string $passwordHash): self
    {
        $this->passwordHash = $passwordHash;

        return $this;
    }

    public function getApiRefreshToken(): ?string
    {
        return $this->apiRefreshToken;
    }

    public function setApiRefreshToken(?string $apiRefreshToken): self
    {
        $this->apiRefreshToken = $apiRefreshToken;

        return $this;
    }

    public function getPassportVerificationStatus(): string
    {
        return $this->passportVerificationStatus;
    }

    public function setPassportVerificationStatus(string $passportVerificationStatus): self
    {
        $this->passportVerificationStatus = $passportVerificationStatus;
        return $this;
    }

    public function getPassportVerifiedAt(): ?\DateTimeImmutable
    {
        return $this->passportVerifiedAt;
    }

    public function setPassportVerifiedAt(?\DateTimeImmutable $passportVerifiedAt): self
    {
        $this->passportVerifiedAt = $passportVerifiedAt;
        return $this;
    }

    public function getPassportVerificationProvider(): ?string
    {
        return $this->passportVerificationProvider;
    }

    public function setPassportVerificationProvider(?string $passportVerificationProvider): self
    {
        $this->passportVerificationProvider = $passportVerificationProvider;
        return $this;
    }

    public function getPassportVerificationSubject(): ?string
    {
        return $this->passportVerificationSubject;
    }

    public function setPassportVerificationSubject(?string $passportVerificationSubject): self
    {
        $this->passportVerificationSubject = $passportVerificationSubject;
        return $this;
    }

    public function getPassportVerificationAuditJson(): ?string
    {
        return $this->passportVerificationAuditJson;
    }

    public function setPassportVerificationAuditJson(?string $passportVerificationAuditJson): self
    {
        $this->passportVerificationAuditJson = $passportVerificationAuditJson;
        return $this;
    }

    public function getSberId(): ?string
    {
        return $this->sberId;
    }

    public function setSberId(?string $sberId): self
    {
        $this->sberId = $sberId;
        return $this;
    }

    public function getAppleId(): ?string
    {
        return $this->appleId;
    }

    public function setAppleId(?string $appleId): self
    {
        $this->appleId = $appleId;
        return $this;
    }

    public function isVerified(): bool
    {
        return $this->verified;
    }

    public function setVerified(bool $verified): self
    {
        $this->verified = $verified;
        return $this;
    }

    public function getApiAccessToken(): ?string
    {
        return $this->apiAccessToken;
    }

    public function setApiAccessToken(?string $apiAccessToken): self
    {
        $this->apiAccessToken = $apiAccessToken;

        return $this;
    }

    public function getApiAccessTokenExpiresAt(): ?\DateTimeImmutable
    {
        return $this->apiAccessTokenExpiresAt;
    }

    public function setApiAccessTokenExpiresAt(?\DateTimeImmutable $apiAccessTokenExpiresAt): self
    {
        $this->apiAccessTokenExpiresAt = $apiAccessTokenExpiresAt;

        return $this;
    }

    public function isPassportLockedFromClientEdit(): bool
    {
        return $this->passportVerificationProvider === 'sber_id'
            && $this->passportVerificationStatus === 'verified';
    }

    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
    }
}

