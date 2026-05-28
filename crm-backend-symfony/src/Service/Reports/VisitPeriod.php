<?php

declare(strict_types=1);

namespace App\Service\Reports;

/**
 * Диапазон дат для отчёта по посещениям (полуинтервал [from, toExclusive)).
 */
final readonly class VisitPeriod
{
    public const TYPE_MONTH = 'month';
    public const TYPE_QUARTER = 'quarter';
    public const TYPE_CUSTOM = 'custom';

    public function __construct(
        public string $type,
        public \DateTimeImmutable $from,
        public \DateTimeImmutable $toExclusive,
        public string $label,
        /** YYYY-MM для period=month */
        public ?string $month = null,
        public ?int $year = null,
        public ?int $quarter = null,
    ) {
    }

    public function dateFromYmd(): string
    {
        return $this->from->format('Y-m-d');
    }

    /** Последний включительный день периода (для отображения и CSV). */
    public function dateToYmdInclusive(): string
    {
        return $this->toExclusive->modify('-1 day')->format('Y-m-d');
    }

    public function dateFromDisplay(): string
    {
        return $this->from->format('d.m.Y');
    }

    public function dateToDisplay(): string
    {
        return $this->toExclusive->modify('-1 day')->format('d.m.Y');
    }

    /**
     * @return array<string, scalar|null>
     */
    public function queryParams(): array
    {
        $params = ['period' => $this->type];

        return match ($this->type) {
            self::TYPE_MONTH => array_merge($params, [
                'month' => $this->month ?? $this->from->format('Y-m'),
            ]),
            self::TYPE_QUARTER => array_merge($params, [
                'year' => $this->year ?? (int) $this->from->format('Y'),
                'quarter' => $this->quarter ?? (int) ceil((int) $this->from->format('n') / 3),
            ]),
            default => array_merge($params, [
                'date_from' => $this->dateFromYmd(),
                'date_to' => $this->dateToYmdInclusive(),
            ]),
        };
    }
}
