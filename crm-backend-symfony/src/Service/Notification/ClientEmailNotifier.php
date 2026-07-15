<?php

declare(strict_types=1);

namespace App\Service\Notification;

use Psr\Log\LoggerInterface;

/**
 * Отправка email: SMTP (если задан SMTP_HOST) или fallback через mail().
 */
final class ClientEmailNotifier
{
    public function __construct(
        private readonly ?string $mailFrom = null,
        private readonly ?string $mailFromName = null,
        private readonly ?string $smtpHost = null,
        private readonly int $smtpPort = 587,
        private readonly ?string $smtpUser = null,
        private readonly ?string $smtpPassword = null,
        private readonly string $smtpEncryption = 'tls',
        private readonly bool $enabled = true,
        private readonly ?LoggerInterface $logger = null,
    ) {
    }

    public function send(string $to, string $subject, string $body): void
    {
        if (!$this->enabled) {
            return;
        }

        $to = trim($to);
        if ($to === '' || !filter_var($to, FILTER_VALIDATE_EMAIL)) {
            return;
        }

        $from = $this->mailFrom ?: 'noreply@worldcashfit.ru';
        $fromName = $this->mailFromName ?: 'WorldCashFit';

        $host = trim((string) $this->smtpHost);
        if ($host !== '') {
            $this->sendViaSmtp($host, $from, $fromName, $to, $subject, $body);

            return;
        }

        $this->sendViaMailFunction($from, $fromName, $to, $subject, $body);
    }

    private function sendViaMailFunction(
        string $from,
        string $fromName,
        string $to,
        string $subject,
        string $body,
    ): void {
        $headers = implode("\r\n", [
            'MIME-Version: 1.0',
            'Content-Type: text/plain; charset=UTF-8',
            'From: ' . $this->formatAddress($fromName, $from),
        ]);

        $encodedSubject = '=?UTF-8?B?' . base64_encode($subject) . '?=';
        $sent = @mail($to, $encodedSubject, $body, $headers);
        if (!$sent) {
            $this->logger?->warning('Email via mail() failed; configure SMTP_HOST in .env', [
                'to' => $to,
                'subject' => $subject,
            ]);
        }
    }

    private function sendViaSmtp(
        string $host,
        string $from,
        string $fromName,
        string $to,
        string $subject,
        string $body,
    ): void {
        $encryption = strtolower(trim($this->smtpEncryption));
        $remote = $host . ':' . ($this->smtpPort > 0 ? $this->smtpPort : 587);
        $transport = $encryption === 'ssl' ? 'ssl://' . $remote : $remote;

        $socket = @stream_socket_client(
            $transport,
            $errno,
            $errstr,
            10,
            STREAM_CLIENT_CONNECT,
        );
        if (!\is_resource($socket)) {
            $this->logger?->error('SMTP connection failed', [
                'host' => $host,
                'port' => $this->smtpPort,
                'error' => $errstr ?: (string) $errno,
            ]);

            return;
        }

        try {
            $this->smtpExpect($socket, [220]);
            $this->smtpCommand($socket, 'EHLO worldcashfit.ru', [250]);

            if ($encryption === 'tls') {
                $this->smtpCommand($socket, 'STARTTLS', [220]);
                if (!stream_socket_enable_crypto($socket, true, STREAM_CRYPTO_METHOD_TLS_CLIENT)) {
                    throw new \RuntimeException('STARTTLS failed');
                }
                $this->smtpCommand($socket, 'EHLO worldcashfit.ru', [250]);
            }

            $user = trim((string) $this->smtpUser);
            $password = (string) $this->smtpPassword;
            if ($user !== '' && $password !== '') {
                $this->smtpCommand($socket, 'AUTH LOGIN', [334]);
                $this->smtpCommand($socket, base64_encode($user), [334]);
                $this->smtpCommand($socket, base64_encode($password), [235]);
            }

            $this->smtpCommand($socket, 'MAIL FROM:<' . $from . '>', [250]);
            $this->smtpCommand($socket, 'RCPT TO:<' . $to . '>', [250, 251]);
            $this->smtpCommand($socket, 'DATA', [354]);

            $message = implode("\r\n", [
                'From: ' . $this->formatAddress($fromName, $from),
                'To: <' . $to . '>',
                'Subject: =?UTF-8?B?' . base64_encode($subject) . '?=',
                'MIME-Version: 1.0',
                'Content-Type: text/plain; charset=UTF-8',
                'Content-Transfer-Encoding: 8bit',
                '',
                $body,
                '',
            ]);
            fwrite($socket, $message . "\r\n.\r\n");
            $this->smtpExpect($socket, [250]);
            $this->smtpCommand($socket, 'QUIT', [221]);
        } catch (\Throwable $e) {
            $this->logger?->error('SMTP send failed', [
                'to' => $to,
                'subject' => $subject,
                'error' => $e->getMessage(),
            ]);
        } finally {
            fclose($socket);
        }
    }

    /** @param list<int> $codes */
    private function smtpCommand($socket, string $command, array $codes): void
    {
        fwrite($socket, $command . "\r\n");
        $this->smtpExpect($socket, $codes);
    }

    /** @param list<int> $codes */
    private function smtpExpect($socket, array $codes): void
    {
        $response = '';
        while (($line = fgets($socket, 515)) !== false) {
            $response .= $line;
            if (isset($line[3]) && $line[3] === ' ') {
                break;
            }
        }
        $code = (int) substr(trim($response), 0, 3);
        if (!in_array($code, $codes, true)) {
            throw new \RuntimeException('SMTP error: ' . trim($response));
        }
    }

    private function formatAddress(string $name, string $email): string
    {
        $safeName = str_replace(['"', "\r", "\n"], '', $name);

        return '"' . $safeName . '" <' . $email . '>';
    }
}
