<?php

namespace App\Entity;

use App\Repository\StaffUserRepository;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Security\Core\User\PasswordAuthenticatedUserInterface;
use Symfony\Component\Security\Core\User\UserInterface;

#[ORM\Entity(repositoryClass: StaffUserRepository::class)]
#[ORM\Table(name: 'staff_users')]
class StaffUser implements UserInterface, PasswordAuthenticatedUserInterface
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 180, unique: true)]
    private string $email;

    #[ORM\Column(type: 'string')]
    private string $password;

    #[ORM\Column(type: 'string', length: 100)]
    private string $name = '';

    /** @var list<string> */
    #[ORM\Column(type: 'json')]
    private array $roles = [];

    #[ORM\Column(type: 'boolean')]
    private bool $isActive = true;

    #[ORM\Column(type: 'datetime_immutable')]
    private \DateTimeImmutable $createdAt;

    /** Одноразовый refresh для staff мобильного клиента. */
    #[ORM\Column(name: 'api_refresh_token', type: 'string', length: 64, nullable: true)]
    private ?string $apiRefreshToken = null;

    /** Короткоживущий access-токен для staff API. */
    #[ORM\Column(name: 'api_access_token', type: 'string', length: 64, nullable: true)]
    private ?string $apiAccessToken = null;

    #[ORM\Column(name: 'api_access_token_expires_at', type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $apiAccessTokenExpiresAt = null;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
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

    public function getUserIdentifier(): string
    {
        return $this->email;
    }

    /** @return list<string> */
    public function getRoles(): array
    {
        $roles = $this->roles;
        $roles[] = 'ROLE_STAFF';
        return array_values(array_unique($roles));
    }

    /** @param list<string> $roles */
    public function setRoles(array $roles): self
    {
        $this->roles = $roles;
        return $this;
    }

    public function getPassword(): string
    {
        return $this->password;
    }

    public function setPassword(string $password): self
    {
        $this->password = $password;
        return $this;
    }

    public function eraseCredentials(): void
    {
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

    public function isActive(): bool
    {
        return $this->isActive;
    }

    public function setIsActive(bool $isActive): self
    {
        $this->isActive = $isActive;
        return $this;
    }

    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
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
}
