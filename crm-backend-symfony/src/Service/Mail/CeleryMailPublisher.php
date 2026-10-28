<?php

declare(strict_types=1);

namespace App\Service\Mail;

use Psr\Log\LoggerInterface;

/**
 * Кладёт письмо в очередь Celery (Redis LPUSH, JSON protocol v2).
 * Воркер python/celery забирает задачу и шлёт через контейнер mail/Postfix.
 */
final class CeleryMailPublisher
{
    public const TASK_PASSWORD_RESET = 'mail.send_password_reset';

    public function __construct(
        private readonly string $redisHost = 'redis',
        private readonly int $redisPort = 6379,
        private readonly string $queue = 'celery',
        private readonly ?string $mailFrom = null,
        private readonly ?string $mailFromName = null,
        private readonly bool $enabled = true,
        private readonly ?LoggerInterface $logger = null,
    ) {
    }

    public function enqueuePasswordReset(string $to, string $subject, string $text, ?string $html = null): void
    {
        $this->enqueue(self::TASK_PASSWORD_RESET, [
            'to' => $to,
            'subject' => $subject,
            'text' => $text,
            'html' => $html,
            'mail_from' => $this->mailFrom ?: 'noreply@worldcashfit.ru',
            'mail_from_name' => $this->mailFromName ?: 'WorldCashFit',
        ]);
    }

    /**
     * @param array<string, mixed> $kwargs
     */
    public function enqueue(string $task, array $kwargs): void
    {
        if (!$this->enabled) {
            return;
        }

        $id = $this->uuid();
        $payload = [
            [],
            $kwargs,
            [
                'callbacks' => null,
                'errbacks' => null,
                'chain' => null,
                'chord' => null,
            ],
        ];
        $bodyJson = json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
        $message = [
            'body' => base64_encode($bodyJson),
            'content-encoding' => 'utf-8',
            'content-type' => 'application/json',
            'headers' => [
                'lang' => 'py',
                'task' => $task,
                'id' => $id,
                'root_id' => $id,
                'parent_id' => null,
                'group' => null,
                'meth' => null,
                'shadow' => null,
                'eta' => null,
                'expires' => null,
                'retries' => 0,
                'timelimit' => [null, null],
                'argsrepr' => '()',
                'kwargsrepr' => json_encode($kwargs, JSON_UNESCAPED_UNICODE),
                'origin' => 'php@crm',
                'ignore_result' => true,
            ],
            'properties' => [
                'correlation_id' => $id,
                'reply_to' => $this->uuid(),
                'delivery_mode' => 2,
                'delivery_info' => [
                    'exchange' => '',
                    'routing_key' => $this->queue,
                ],
                'priority' => 0,
                'body_encoding' => 'base64',
                'delivery_tag' => $this->uuid(),
            ],
        ];
        $json = json_encode($message, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
        $this->lpush($this->queue, $json);
        $this->logger?->info('Mail queued for Celery', [
            'task' => $task,
            'queue' => $this->queue,
            'id' => $id,
        ]);
    }

    private function lpush(string $key, string $value): void
    {
        $remote = sprintf('tcp://%s:%d', $this->redisHost, $this->redisPort);
        $fp = @stream_socket_client($remote, $errno, $errstr, 2.0);
        if (!\is_resource($fp)) {
            throw new \RuntimeException('Redis недоступен: ' . ($errstr !== '' ? $errstr : (string) $errno));
        }
        stream_set_timeout($fp, 2);
        try {
            $cmd = '*3' . "\r\n"
                . '$5' . "\r\nLPUSH\r\n"
                . '$' . strlen($key) . "\r\n" . $key . "\r\n"
                . '$' . strlen($value) . "\r\n" . $value . "\r\n";
            fwrite($fp, $cmd);
            $resp = fgets($fp);
            if ($resp === false || ($resp[0] !== ':' && $resp[0] !== '+')) {
                throw new \RuntimeException('Redis LPUSH: ' . trim((string) $resp));
            }
        } finally {
            fclose($fp);
        }
    }

    private function uuid(): string
    {
        $b = random_bytes(16);
        $b[6] = chr((ord($b[6]) & 0x0f) | 0x40);
        $b[8] = chr((ord($b[8]) & 0x3f) | 0x80);
        $hex = bin2hex($b);

        return sprintf(
            '%s-%s-%s-%s-%s',
            substr($hex, 0, 8),
            substr($hex, 8, 4),
            substr($hex, 12, 4),
            substr($hex, 16, 4),
            substr($hex, 20, 12),
        );
    }
}
