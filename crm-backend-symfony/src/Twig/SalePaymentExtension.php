<?php

declare(strict_types=1);

namespace App\Twig;

use App\Service\Admin\SalePaymentMethodCatalog;
use Twig\Extension\AbstractExtension;
use Twig\TwigFunction;

final class SalePaymentExtension extends AbstractExtension
{
    public function getFunctions(): array
    {
        return [
            new TwigFunction('sale_payment_label', [SalePaymentMethodCatalog::class, 'label']),
            new TwigFunction('sale_payment_issue_options', [SalePaymentMethodCatalog::class, 'issueOptions']),
            new TwigFunction('sale_payment_form_options', [SalePaymentMethodCatalog::class, 'saleFormOptions']),
        ];
    }
}
