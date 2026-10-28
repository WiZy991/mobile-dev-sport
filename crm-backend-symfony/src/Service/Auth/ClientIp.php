<?php

declare(strict_types=1);

namespace App\Service\Auth;

use Symfony\Component\HttpFoundation\Request;

final class ClientIp
{
    public static function from(Request $request): string
    {
        $xff = trim((string) $request->headers->get('X-Forwarded-For', ''));
        if ($xff !== '') {
            $first = trim(explode(',', $xff)[0]);
            if ($first !== '' && filter_var($first, FILTER_VALIDATE_IP)) {
                return $first;
            }
        }
        $ip = $request->getClientIp();

        return is_string($ip) && $ip !== '' ? $ip : '0.0.0.0';
    }
}
