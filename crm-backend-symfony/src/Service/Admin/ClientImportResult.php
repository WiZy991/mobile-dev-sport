<?php

namespace App\Service\Admin;

/** Результат импорта из CSV/XLSX: клиенты, абонементы, записи на тренировки. */
final class ClientImportResult
{
    /**
     * @param list<string> $errors Сообщения по строкам
     */
    public function __construct(
        public readonly int $clientsCreated,
        public readonly int $clientsUpdated,
        public readonly int $subscriptionsCreated,
        public readonly int $subscriptionsUpdated,
        public readonly int $bookingsCreated,
        public readonly int $bookingsUpdated,
        public readonly int $skipped,
        public readonly array $errors,
    ) {
    }
}
