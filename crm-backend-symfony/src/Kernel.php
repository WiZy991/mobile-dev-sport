<?php

namespace App;

use Symfony\Bundle\FrameworkBundle\Kernel\MicroKernelTrait;
use Symfony\Component\HttpKernel\Kernel as BaseKernel;

class Kernel extends BaseKernel
{
    use MicroKernelTrait;

    public function boot(): void
    {
        // Как раньше: UTC в PHP/БД (naive DATETIME). Смена на Владивосток ломала «сейчас в зале».
        $timezone = $_ENV['APP_TIMEZONE'] ?? $_SERVER['APP_TIMEZONE'] ?? 'UTC';
        if (\is_string($timezone) && $timezone !== '' && @timezone_open($timezone) !== false) {
            date_default_timezone_set($timezone);
        } else {
            date_default_timezone_set('UTC');
        }

        parent::boot();
    }
}
