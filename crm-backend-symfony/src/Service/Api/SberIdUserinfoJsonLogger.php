<?php

declare(strict_types=1);

namespace App\Service\Api;

/**
 * Пишет в лог ответ GET …/userinfo без блока identification (паспорт маскируется).
 */
final class SberIdUserinfoJsonLogger
{
    private const LOG_FILE = 'sber-userinfo.log';

    public function __construct(
        private readonly string $projectDir,
        private readonly bool $enabled,
    ) {
    }

    /**
     * @param array<string, mixed> $userinfo
     */
    public function log(array $userinfo): void
    {
        if (!$this->enabled || $userinfo === []) {
            return;
        }

        $dir = $this->projectDir . '/var/log';
        if (!is_dir($dir) && !@mkdir($dir, 0775, true) && !is_dir($dir)) {
            return;
        }

        $safe = $this->redact($userinfo);

        $line = json_encode(
            $safe,
            JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_INVALID_UTF8_SUBSTITUTE | JSON_THROW_ON_ERROR,
        ) . "\n---\n";

        @file_put_contents($dir . '/' . self::LOG_FILE, $line, FILE_APPEND | LOCK_EX);
    }

    /**
     * @param array<string, mixed> $userinfo
     *
     * @return array<string, mixed>
     */
    private function redact(array $userinfo): array
    {
        foreach (['identification', 'priority_doc', 'maindoc', 'previous_identification'] as $key) {
            if (array_key_exists($key, $userinfo)) {
                $userinfo[$key] = '[REDACTED]';
            }
        }

        return $userinfo;
    }
}
