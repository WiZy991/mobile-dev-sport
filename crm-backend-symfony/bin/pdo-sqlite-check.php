<?php

declare(strict_types=1);

if (!\in_array('sqlite', PDO::getAvailableDrivers(), true)) {
    fwrite(STDERR, "PDO has no 'sqlite' driver. Enable extension=pdo_sqlite in php.ini next to php.exe, extension_dir must point to the ext folder.\n");
    exit(1);
}

try {
    new PDO('sqlite::memory:');
} catch (Throwable $e) {
    fwrite(STDERR, $e->getMessage() . "\n");
    exit(1);
}
