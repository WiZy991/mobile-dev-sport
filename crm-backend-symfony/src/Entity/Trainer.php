<?php

namespace App\Entity;

use App\Entity\Contract\TenantAware;
use App\Entity\Trait\OrganizationOwnedTrait;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'trainers')]
class Trainer implements TenantAware
{
    use OrganizationOwnedTrait;

    public const STATUS_PUBLISHED = 'published';
    public const STATUS_MODERATION = 'moderation';
    public const STATUS_HIDDEN = 'hidden';

    /** @var list<string> */
    public const PUBLICATION_STATUSES = [
        self::STATUS_PUBLISHED,
        self::STATUS_MODERATION,
        self::STATUS_HIDDEN,
    ];

    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 150)]
    private string $name;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $specialization = null;

    #[ORM\Column(type: 'float', nullable: true)]
    private ?float $rating = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $photoUrl = null;

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $phone = null;

    /** Email для входа в staffapp (совпадение при «Регистрация»). */
    #[ORM\Column(type: 'string', length: 180, nullable: true)]
    private ?string $email = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $description = null;

    /**
     * Видимость в клиентском приложении:
     * published — в списке «Наша команда»;
     * moderation — скрыт (ожидает модерации);
     * hidden — скрыт вручную.
     */
    /** Новые карточки по умолчанию на модерации; миграция оставляет существующих published. */
    #[ORM\Column(name: 'publication_status', type: 'string', length: 20, options: ['default' => self::STATUS_MODERATION])]
    private string $publicationStatus = self::STATUS_MODERATION;

    /**
     * Прайс для клиентского приложения: JSON-массив
     * [{"name":"Персональная тренировка","price_from":2500}, ...]
     */
    #[ORM\Column(name: 'services_json', type: 'text', nullable: true)]
    private ?string $servicesJson = null;

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

    public function getSpecialization(): ?string
    {
        return $this->specialization;
    }

    public function setSpecialization(?string $specialization): self
    {
        $this->specialization = $specialization;
        return $this;
    }

    public function getRating(): ?float
    {
        return $this->rating;
    }

    public function setRating(?float $rating): self
    {
        $this->rating = $rating;
        return $this;
    }

    public function getPhotoUrl(): ?string
    {
        return $this->photoUrl;
    }

    public function setPhotoUrl(?string $photoUrl): self
    {
        $this->photoUrl = $photoUrl;
        return $this;
    }

    public function getPhone(): ?string
    {
        return $this->phone;
    }

    public function setPhone(?string $phone): self
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
        $email = $email !== null ? trim($email) : null;
        $this->email = ($email === null || $email === '') ? null : mb_strtolower($email);

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

    public function getPublicationStatus(): string
    {
        return $this->publicationStatus;
    }

    public function setPublicationStatus(string $publicationStatus): self
    {
        $status = strtolower(trim($publicationStatus));
        if (!\in_array($status, self::PUBLICATION_STATUSES, true)) {
            $status = self::STATUS_HIDDEN;
        }
        $this->publicationStatus = $status;

        return $this;
    }

    public function isPublishedInApp(): bool
    {
        return $this->publicationStatus === self::STATUS_PUBLISHED;
    }

    public static function normalizePublicationStatus(mixed $raw, string $default = self::STATUS_MODERATION): string
    {
        if (!\is_string($raw)) {
            return $default;
        }
        $status = strtolower(trim($raw));

        return \in_array($status, self::PUBLICATION_STATUSES, true) ? $status : $default;
    }

    public function getPublicationStatusLabel(): string
    {
        return match ($this->publicationStatus) {
            self::STATUS_PUBLISHED => 'Опубликован',
            self::STATUS_HIDDEN => 'Скрыт',
            default => 'На модерации',
        };
    }

    /** @return list<array{name: string, price_from: int}> */
    public function getServices(): array
    {
        if ($this->servicesJson === null || trim($this->servicesJson) === '') {
            return [];
        }
        $decoded = json_decode($this->servicesJson, true);
        if (!\is_array($decoded)) {
            return [];
        }
        $out = [];
        foreach ($decoded as $row) {
            if (!\is_array($row)) {
                continue;
            }
            $name = trim((string) ($row['name'] ?? ''));
            if ($name === '') {
                continue;
            }
            $price = (int) ($row['price_from'] ?? $row['priceFrom'] ?? 0);
            $out[] = ['name' => $name, 'price_from' => max(0, $price)];
        }

        return $out;
    }

    /**
     * @param list<mixed>|null $services
     * @return list<string> ошибки валидации (пусто = ок)
     */
    public function setServicesFromInput(?array $services): array
    {
        if ($services === null) {
            $this->servicesJson = null;

            return [];
        }
        if (\count($services) > 20) {
            return ['Не больше 20 услуг'];
        }
        $normalized = [];
        foreach ($services as $row) {
            if (!\is_array($row)) {
                return ['Некорректный формат услуги'];
            }
            $name = trim((string) ($row['name'] ?? ''));
            if ($name === '') {
                return ['Укажите название услуги'];
            }
            if (mb_strlen($name) > 120) {
                return ['Название услуги слишком длинное'];
            }
            $priceRaw = $row['price_from'] ?? $row['priceFrom'] ?? 0;
            if (!is_numeric($priceRaw) || (int) $priceRaw < 0) {
                return ['Цена «от» должна быть числом ≥ 0'];
            }
            $normalized[] = [
                'name' => $name,
                'price_from' => (int) $priceRaw,
            ];
        }
        $this->servicesJson = $normalized === [] ? null : json_encode($normalized, \JSON_UNESCAPED_UNICODE);

        return [];
    }

    public function getServicesJson(): ?string
    {
        return $this->servicesJson;
    }
}
