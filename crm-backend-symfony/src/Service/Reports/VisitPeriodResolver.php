<?php

declare(strict_types=1);

namespace App\Service\Reports;

/**
 * Разбор периода отчёта: месяц, квартал или произвольные даты.
 */
final class VisitPeriodResolver
{
    /**
     * @param array<string, mixed> $query GET-параметры запроса
     */
    public function resolve(array $query): VisitPeriod
    {
        $type = isset($query['period']) && is_string($query['period']) ? $query['period'] : VisitPeriod::TYPE_MONTH;

        return match ($type) {
            VisitPeriod::TYPE_QUARTER => $this->resolveQuarter($query),
            VisitPeriod::TYPE_CUSTOM => $this->resolveCustom($query),
            default => $this->resolveMonth($query),
        };
    }

    /**
     * @param array<string, mixed> $query
     */
    private function resolveMonth(array $query): VisitPeriod
    {
        $today = new \DateTimeImmutable('today');
        $monthRaw = isset($query['month']) && is_string($query['month']) ? trim($query['month']) : $today->format('Y-m');
        if (!preg_match('/^\d{4}-\d{2}$/', $monthRaw)) {
            $monthRaw = $today->format('Y-m');
        }

        $from = new \DateTimeImmutable($monthRaw . '-01');
        $toExclusive = $from->modify('first day of next month');
        $label = $this->formatMonthLabel($from);

        return new VisitPeriod(
            type: VisitPeriod::TYPE_MONTH,
            from: $from,
            toExclusive: $toExclusive,
            label: $label,
            month: $monthRaw,
        );
    }

    /**
     * @param array<string, mixed> $query
     */
    private function resolveQuarter(array $query): VisitPeriod
    {
        $today = new \DateTimeImmutable('today');
        $year = isset($query['year']) && is_numeric($query['year']) ? (int) $query['year'] : (int) $today->format('Y');
        $quarter = isset($query['quarter']) && is_numeric($query['quarter']) ? (int) $query['quarter'] : (int) ceil((int) $today->format('n') / 3);

        if ($year < 2000 || $year > 2100) {
            $year = (int) $today->format('Y');
        }
        if ($quarter < 1 || $quarter > 4) {
            $quarter = (int) ceil((int) $today->format('n') / 3);
        }

        $startMonth = ($quarter - 1) * 3 + 1;
        $from = new \DateTimeImmutable(sprintf('%04d-%02d-01', $year, $startMonth));
        $toExclusive = $from->modify('+3 months');
        $label = sprintf('%d квартал %d', $quarter, $year);

        return new VisitPeriod(
            type: VisitPeriod::TYPE_QUARTER,
            from: $from,
            toExclusive: $toExclusive,
            label: $label,
            year: $year,
            quarter: $quarter,
        );
    }

    /**
     * @param array<string, mixed> $query
     */
    private function resolveCustom(array $query): VisitPeriod
    {
        $today = new \DateTimeImmutable('today');
        $fromRaw = isset($query['date_from']) && is_string($query['date_from']) ? trim($query['date_from']) : $today->modify('-7 days')->format('Y-m-d');
        $toRaw = isset($query['date_to']) && is_string($query['date_to']) ? trim($query['date_to']) : $today->format('Y-m-d');

        try {
            $from = new \DateTimeImmutable($fromRaw);
        } catch (\Exception) {
            $from = $today->modify('-7 days');
        }

        try {
            $toInclusive = new \DateTimeImmutable($toRaw);
        } catch (\Exception) {
            $toInclusive = $today;
        }

        if ($toInclusive < $from) {
            [$from, $toInclusive] = [$toInclusive, $from];
        }

        $toExclusive = $toInclusive->modify('+1 day');
        $label = sprintf(
            '%s — %s',
            $from->format('d.m.Y'),
            $toInclusive->format('d.m.Y'),
        );

        return new VisitPeriod(
            type: VisitPeriod::TYPE_CUSTOM,
            from: $from,
            toExclusive: $toExclusive,
            label: $label,
        );
    }

    private function formatMonthLabel(\DateTimeImmutable $from): string
    {
        static $months = [
            1 => 'январь', 2 => 'февраль', 3 => 'март', 4 => 'апрель',
            5 => 'май', 6 => 'июнь', 7 => 'июль', 8 => 'август',
            9 => 'сентябрь', 10 => 'октябрь', 11 => 'ноябрь', 12 => 'декабрь',
        ];
        $monthNum = (int) $from->format('n');

        return ($months[$monthNum] ?? $from->format('F')) . ' ' . $from->format('Y');
    }
}
