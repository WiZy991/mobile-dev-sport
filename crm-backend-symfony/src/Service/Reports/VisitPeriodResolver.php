<?php

declare(strict_types=1);

namespace App\Service\Reports;

use App\Service\ClubTimezone;

/**
 * Разбор периода отчёта: месяц, квартал или произвольные даты.
 * Границы — календарь Владивостока, в UTC для сравнения с access_logs.
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
        $todayLocal = new \DateTimeImmutable('today', ClubTimezone::zone());
        $monthRaw = isset($query['month']) && is_string($query['month']) ? trim($query['month']) : $todayLocal->format('Y-m');
        if (!preg_match('/^\d{4}-\d{2}$/', $monthRaw)) {
            $monthRaw = $todayLocal->format('Y-m');
        }

        $fromLocal = new \DateTimeImmutable($monthRaw . '-01 00:00:00', ClubTimezone::zone());
        $toLocal = $fromLocal->modify('first day of next month');
        $from = $this->toUtc($fromLocal);
        $toExclusive = $this->toUtc($toLocal);
        $label = $this->formatMonthLabel($fromLocal);

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
        $todayLocal = new \DateTimeImmutable('today', ClubTimezone::zone());
        $year = isset($query['year']) && is_numeric($query['year']) ? (int) $query['year'] : (int) $todayLocal->format('Y');
        $quarter = isset($query['quarter']) && is_numeric($query['quarter'])
            ? (int) $query['quarter']
            : (int) ceil((int) $todayLocal->format('n') / 3);

        if ($year < 2000 || $year > 2100) {
            $year = (int) $todayLocal->format('Y');
        }
        if ($quarter < 1 || $quarter > 4) {
            $quarter = (int) ceil((int) $todayLocal->format('n') / 3);
        }

        $startMonth = ($quarter - 1) * 3 + 1;
        $fromLocal = new \DateTimeImmutable(sprintf('%04d-%02d-01 00:00:00', $year, $startMonth), ClubTimezone::zone());
        $toLocal = $fromLocal->modify('+3 months');
        $label = sprintf('%d квартал %d', $quarter, $year);

        return new VisitPeriod(
            type: VisitPeriod::TYPE_QUARTER,
            from: $this->toUtc($fromLocal),
            toExclusive: $this->toUtc($toLocal),
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
        $todayLocal = new \DateTimeImmutable('today', ClubTimezone::zone());
        $fromRaw = isset($query['date_from']) && is_string($query['date_from'])
            ? trim($query['date_from'])
            : $todayLocal->modify('-7 days')->format('Y-m-d');
        $toRaw = isset($query['date_to']) && is_string($query['date_to'])
            ? trim($query['date_to'])
            : $todayLocal->format('Y-m-d');

        try {
            $fromLocal = new \DateTimeImmutable($fromRaw . ' 00:00:00', ClubTimezone::zone());
        } catch (\Exception) {
            $fromLocal = $todayLocal->modify('-7 days');
        }

        try {
            $toInclusiveLocal = new \DateTimeImmutable($toRaw . ' 00:00:00', ClubTimezone::zone());
        } catch (\Exception) {
            $toInclusiveLocal = $todayLocal;
        }

        if ($toInclusiveLocal < $fromLocal) {
            [$fromLocal, $toInclusiveLocal] = [$toInclusiveLocal, $fromLocal];
        }

        $toExclusiveLocal = $toInclusiveLocal->modify('+1 day');
        $label = sprintf(
            '%s — %s',
            $fromLocal->format('d.m.Y'),
            $toInclusiveLocal->format('d.m.Y'),
        );

        return new VisitPeriod(
            type: VisitPeriod::TYPE_CUSTOM,
            from: $this->toUtc($fromLocal),
            toExclusive: $this->toUtc($toExclusiveLocal),
            label: $label,
        );
    }

    private function toUtc(\DateTimeImmutable $local): \DateTimeImmutable
    {
        return $local->setTimezone(new \DateTimeZone('UTC'));
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
