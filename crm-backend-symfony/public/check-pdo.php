<?php

declare(strict_types=1);

// PDO в этом же процессе, что обрабатывает HTTP (cli-server). См. .\dev-server.ps1 — CRM_WINGET_DEVCHECK=1.
if (getenv('CRM_WINGET_DEVCHECK') !== '1') {
    http_response_code(404);
    exit;
}

header('Content-Type: text/plain; charset=utf-8');
echo 'SAPI: ' . PHP_SAPI . "\n";
echo 'php.ini: ' . (php_ini_loaded_file() ?: '(none)') . "\n";
echo 'extension_dir: ' . (ini_get('extension_dir') ?: '') . "\n";
echo 'pdo_sqlite: ' . (extension_loaded('pdo_sqlite') ? 'yes' : 'no') . "\n";
$drivers = extension_loaded('pdo') ? PDO::getAvailableDrivers() : [];
echo 'PDO drivers: ' . json_encode($drivers, JSON_UNESCAPED_UNICODE) . "\n";
