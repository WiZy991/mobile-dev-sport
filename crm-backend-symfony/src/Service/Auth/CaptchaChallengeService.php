<?php

declare(strict_types=1);

namespace App\Service\Auth;

use Psr\Cache\CacheItemPoolInterface;

/**
 * Простая арифметика 1–9: «Сколько будет 3 + 5?»
 * Ответ живёт в кэше 5 минут.
 */
final class CaptchaChallengeService
{
    private const TTL = 300;

    public function __construct(
        private readonly CacheItemPoolInterface $cache,
    ) {
    }

    /** @return array{id: string, question: string} */
    public function issue(): array
    {
        $a = random_int(1, 9);
        $b = random_int(1, 9);
        $id = bin2hex(random_bytes(16));
        $item = $this->cache->getItem($this->key($id));
        $item->set((string) ($a + $b));
        $item->expiresAfter(self::TTL);
        $this->cache->save($item);

        return [
            'id' => $id,
            'question' => sprintf('Сколько будет %d + %d?', $a, $b),
        ];
    }

    public function verify(?string $id, ?string $answer): bool
    {
        $id = trim((string) $id);
        $answer = trim((string) $answer);
        if ($id === '' || $answer === '' || !preg_match('/^\d{1,2}$/', $answer)) {
            return false;
        }
        $item = $this->cache->getItem($this->key($id));
        if (!$item->isHit()) {
            return false;
        }
        $ok = hash_equals((string) $item->get(), (string) ((int) $answer));
        $this->cache->deleteItem($this->key($id));

        return $ok;
    }

    private function key(string $id): string
    {
        return 'auth.captcha.' . $id;
    }
}
