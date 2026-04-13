<?php

declare(strict_types=1);

if (!\in_array('mysql', PDO::getAvailableDrivers(), true)) {
    fwrite(STDERR, "PDO has no 'mysql' driver. Enable extension=php_pdo_mysql.dll in php.ini next to php.exe; extension_dir must point to ext.\n");
    exit(1);
}
