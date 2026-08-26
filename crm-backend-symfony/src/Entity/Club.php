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

    /** Цена аренды для тренера (руб / 30 дней). null = зал не в каталоге аренды. */
    #[ORM\Column(name: 'trainer_rental_amount_rub', type: 'integer', nullable: true)]
    private ?int $trainerRentalAmountRub = null;

    /** Показывать клуб в мобильном приложении (регистрация / список залов). */
    #[ORM\Column(name: 'show_in_app', type: 'boolean', options: ['default' => false])]
    private bool $showInApp = false;

    /** Относительный путь к фото карточки регистрации (`/uploads/clubs/...`). */
    #[ORM\Column(name: 'registration_image_path', type: 'string', length: 255, nullable: true)]
    private ?string $registrationImagePath = null;

    public const ENTRY_QR_ASCII = 'ascii';
    public const ENTRY_QR_WIEGAND = 'wiegand';

    /** Формат QR входа в приложении: ascii (FITNESSCLUB:ENTRY) | wiegand (7 цифр). */
    #[ORM\Column(name: 'entry_qr_format', type: 'string', length: 16, options: ['default' => 'ascii'])]
    private string $entryQrFormat = self::ENTRY_QR_ASCII;

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

    public function getTrainerRentalAmountRub(): ?int
    {
        return $this->trainerRentalAmountRub;
    }

    public function setTrainerRentalAmountRub(?int $v): self
    {
        $this->trainerRentalAmountRub = $v !== null && $v > 0 ? $v : null;

        return $this;
    }

    public function isShowInApp(): bool
    {
        return $this->showInApp;
    }

    public function setShowInApp(bool $v): self
    {
        $this->showInApp = $v;

        return $this;
    }

    public function getRegistrationImagePath(): ?string
    {
        return $this->registrationImagePath;
    }

    public function setRegistrationImagePath(?string $v): self
    {
        $this->registrationImagePath = $v !== null && trim($v) !== '' ? trim($v) : null;

        return $this;
    }

    public function getEntryQrFormat(): string
    {
        return $this->entryQrFormat;
    }

    public function setEntryQrFormat(string $v): self
    {
        $v = strtolower(trim($v));
        if ($v !== self::ENTRY_QR_WIEGAND) {
            $v = self::ENTRY_QR_ASCII;
        }
        $this->entryQrFormat = $v;

        return $this;
    }

    public function usesWiegandEntryQr(): bool
    {
        return $this->entryQrFormat === self::ENTRY_QR_WIEGAND;
    }
}
