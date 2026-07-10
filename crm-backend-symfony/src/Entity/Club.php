<?php

namespace App\Entity;

use App\Entity\Contract\TenantAware;
use App\Entity\Trait\OrganizationOwnedTrait;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'clubs')]
class Club implements TenantAware
{
    use OrganizationOwnedTrait;
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 150)]
    private string $name;

    #[ORM\Column(type: 'string', length: 255)]
    private string $address;

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $phone = null;

    #[ORM\Column(type: 'string', length: 100, nullable: true)]
    private ?string $email = null;

    #[ORM\Column(type: 'string', length: 100, nullable: true)]
    private ?string $workingHours = null;

    #[ORM\Column(type: 'float', nullable: true)]
    private ?float $latitude = null;

    #[ORM\Column(type: 'float', nullable: true)]
    private ?float $longitude = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $amenitiesJson = null;

    #[ORM\Column(type: 'integer', nullable: true)]
    private ?int $maxCapacity = null;

    /** Уникальный токен ПК-шлюза в клубе для авторизации в /api/v1/gateway/* */
    #[ORM\Column(type: 'string', length: 64, nullable: true, unique: true)]
    private ?string $gatewayToken = null;

    /** Время последнего heartbeat от шлюза. */
    #[ORM\Column(type: 'datetime', nullable: true)]
    private ?\DateTimeInterface $gatewayLastSeenAt = null;

    /** Локальный URL PERCo-Web в LAN клуба (используется только шлюзом). */
    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $percoBaseUrl = null;

    #[ORM\Column(type: 'string', length: 100, nullable: true)]
    private ?string $percoLogin = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $percoPassword = null;

    /** ID исполнительного устройства (турникета) в PERCo для команды открытия. */
    #[ORM\Column(type: 'integer', nullable: true)]
    private ?int $percoEntryDeviceId = null;

    #[ORM\Column(type: 'boolean', options: ['default' => true])]
    private bool $percoVerifySsl = true;

    public function getId(): ?int { return $this->id; }

    public function getName(): string { return $this->name; }
    public function setName(string $name): self { $this->name = $name; return $this; }

    public function getAddress(): string { return $this->address; }
    public function setAddress(string $address): self { $this->address = $address; return $this; }

    public function getPhone(): ?string { return $this->phone; }
    public function setPhone(?string $phone): self { $this->phone = $phone; return $this; }

    public function getEmail(): ?string { return $this->email; }
    public function setEmail(?string $email): self { $this->email = $email; return $this; }

    public function getWorkingHours(): ?string { return $this->workingHours; }
    public function setWorkingHours(?string $v): self { $this->workingHours = $v; return $this; }

    public function getLatitude(): ?float { return $this->latitude; }
    public function setLatitude(?float $v): self { $this->latitude = $v; return $this; }

    public function getLongitude(): ?float { return $this->longitude; }
    public function setLongitude(?float $v): self { $this->longitude = $v; return $this; }

    public function getAmenitiesJson(): ?string { return $this->amenitiesJson; }
    public function setAmenitiesJson(?string $v): self { $this->amenitiesJson = $v; return $this; }

    /** @return string[] */
    public function getAmenities(): array {
        if (!$this->amenitiesJson) return [];
        $a = json_decode($this->amenitiesJson, true);
        return is_array($a) ? $a : array_filter(array_map('trim', explode(',', $this->amenitiesJson)));
    }

    public function getMaxCapacity(): ?int { return $this->maxCapacity; }
    public function setMaxCapacity(?int $v): self { $this->maxCapacity = $v; return $this; }

    public function getGatewayToken(): ?string { return $this->gatewayToken; }
    public function setGatewayToken(?string $v): self { $this->gatewayToken = $v; return $this; }

    public function getGatewayLastSeenAt(): ?\DateTimeInterface { return $this->gatewayLastSeenAt; }
    public function setGatewayLastSeenAt(?\DateTimeInterface $v): self { $this->gatewayLastSeenAt = $v; return $this; }

    public function getPercoBaseUrl(): ?string { return $this->percoBaseUrl; }
    public function setPercoBaseUrl(?string $v): self { $this->percoBaseUrl = $v; return $this; }

    public function getPercoLogin(): ?string { return $this->percoLogin; }
    public function setPercoLogin(?string $v): self { $this->percoLogin = $v; return $this; }

    public function getPercoPassword(): ?string { return $this->percoPassword; }
    public function setPercoPassword(?string $v): self { $this->percoPassword = $v; return $this; }

    public function getPercoEntryDeviceId(): ?int { return $this->percoEntryDeviceId; }
    public function setPercoEntryDeviceId(?int $v): self { $this->percoEntryDeviceId = $v; return $this; }

    public function isPercoVerifySsl(): bool { return $this->percoVerifySsl; }
    public function setPercoVerifySsl(bool $v): self { $this->percoVerifySsl = $v; return $this; }
}
