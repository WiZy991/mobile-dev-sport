<?php

namespace App\Entity;

use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'lead_notes')]
class LeadNote
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Lead::class, inversedBy: 'notes')]
    #[ORM\JoinColumn(nullable: false)]
    private Lead $lead;

    #[ORM\Column(type: 'text')]
    private string $text;

    #[ORM\Column(type: 'datetime_immutable')]
    private \DateTimeImmutable $createdAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
    }

    public function getId(): ?int { return $this->id; }

    public function getLead(): Lead { return $this->lead; }
    public function setLead(Lead $lead): self { $this->lead = $lead; return $this; }

    public function getText(): string { return $this->text; }
    public function setText(string $text): self { $this->text = $text; return $this; }

    public function getCreatedAt(): \DateTimeImmutable { return $this->createdAt; }
}
