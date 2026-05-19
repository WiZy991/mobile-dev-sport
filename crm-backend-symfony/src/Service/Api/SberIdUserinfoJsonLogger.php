<?php

declare(strict_types=1);

namespace App\Service\Api;

/**
 * Пишет в лог сырой JSON ответа GET …/userinfo (как в WorldFitness/instruction.txt, шаг 3).
 */
final class SberIdUserinfoJsonLogger
{
    private const LOG_FILE = 'sber-userinfo.log';

    public function __construct(
        private readonly string $projectDir,
    ) {
    }

    /**
     * @param array<string, mixed> $userinfo Тело ответа userinfo без обёрток (sub, family_name, identification, …).
     */
    public function log(array $userinfo): void
    {
        if ($userinfo === []) {
            return;
        }

        $dir = $this->projectDir . '/var/log';
        if (!is_dir($dir) && !@mkdir($dir, 0775, true) && !is_dir($dir)) {
            return;
        }

        $line = json_encode(
            $userinfo,
            JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_INVALID_UTF8_SUBSTITUTE | JSON_THROW_ON_ERROR,
        ) . "\n---\n";

        @file_put_contents($dir . '/' . self::LOG_FILE, $line, FILE_APPEND | LOCK_EX);
    }
}
