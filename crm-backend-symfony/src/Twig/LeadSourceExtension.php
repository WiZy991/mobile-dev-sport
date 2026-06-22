<?php

declare(strict_types=1);

namespace App\Twig;

use App\Service\Lead\LeadSource;
use Twig\Extension\AbstractExtension;
use Twig\TwigFunction;

final class LeadSourceExtension extends AbstractExtension
{
    public function getFunctions(): array
    {
        return [
            new TwigFunction('lead_source_label', [LeadSource::class, 'label']),
            new TwigFunction('lead_source_badge', [LeadSource::class, 'badgeClass']),
            new TwigFunction('lead_sources', [LeadSource::class, 'labels']),
        ];
    }
}
