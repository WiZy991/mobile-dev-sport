<?php

declare(strict_types=1);

namespace App\Entity;

use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'staff_club_rentals')]
#[ORM\UniqueConstraint(name: 'uniq_staff_club_rental', columns: ['staff_user_id', 'club_id'])]
#[ORM\Index(name: 'idx_staff_club_rental_staff', columns: ['staff_user_id'])]
#[ORM\Index(name: 'idx_staff_club_rental_club', columns: ['club_id'])]
class StaffClubRental
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: StaffUser::class)]
    #[ORM\JoinColumn(name: 'staff_user_id', referencedColumnName: 'id', nullable: false, onDelete: 'CASCADE')]
    private StaffUser $staffUser;

    #[ORM\ManyToOne(targetEntity: Club::class)]
    #[ORM\JoinColumn(name: 'club_id', referencedColumnName: 'id', nullable: false, onDelete: 'CASCADE')]
    private Club $club;

    #[ORM\Column(name: 'paid_until', type: 'datetime_immutable')]
    private \DateTimeImmutable $paidUntil;

    #[ORM\Column(name: 'updated_at', type: 'datetime_immutable')]
    private \DateTimeImmutable $updatedAt;

    public function __construct(StaffUser $staffUser, Club $club, \DateTimeImmutable $paidUntil)
    {
        $this->staffUser = $staffUser;
        $this->club = $club;
        $this->paidUntil = $paidUntil;
        $this->updatedAt = new \DateTimeImmutable();
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getStaffUser(): StaffUser
    {
        return $this->staffUser;
    }

    public function getClub(): Club
    {
        return $this->club;
    }

    public function getPaidUntil(): \DateTimeImmutable
    {
        return $this->paidUntil;
    }

    public function setPaidUntil(\DateTimeImmutable $paidUntil): self
    {
        $this->paidUntil = $paidUntil;
        $this->updatedAt = new \DateTimeImmutable();

        return $this;
    }

    public function getUpdatedAt(): \DateTimeImmutable
    {
        return $this->updatedAt;
    }

    public function isValid(?\DateTimeImmutable $now = null): bool
    {
        $tz = \App\Service\ClubTimezone::zone();
        $nowLocal = ($now ?? \App\Service\ClubTimezone::now())->setTimezone($tz);
        $untilEndOfDay = new \DateTimeImmutable(
            $this->paidUntil->format('Y-m-d') . ' 23:59:59',
            $tz,
        );

        return $untilEndOfDay >= $nowLocal;
    }
}
