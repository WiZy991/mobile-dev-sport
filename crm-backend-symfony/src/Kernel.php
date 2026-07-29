<?php

namespace App;

use Symfony\Bundle\FrameworkBundle\Kernel\MicroKernelTrait;
use Symfony\Component\HttpKernel\Kernel as BaseKernel;

class Kernel extends BaseKernel
{
    use MicroKernelTrait;

    public function boot(): void
    {
        // Клуб / CRM — Владивосток (UTC+10). Без явной TZ Docker/PHP часто в UTC.
        $timezone = $_ENV['APP_TIMEZONE'] ?? $_SERVER['APP_TIMEZONE'] ?? 'Asia/Vladivostok';
        if (\is_string($timezone) && $timezone !== '' && @timezone_open($timezone) !== false) {
            date_default_timezone_set($timezone);
        } else {
            date_default_timezone_set('Asia/Vladivostok');
        }

        parent::boot();
    }
}
