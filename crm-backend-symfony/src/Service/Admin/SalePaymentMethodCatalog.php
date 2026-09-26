<?php

declare(strict_types=1);

namespace App\Service\Admin;

/**
 * Способы оплаты / типы выдачи в продажах (Sale.paymentMethod).
 *
 * Ручная выдача абонемента раньше молча писала «cash» — из‑за этого
 * сертификаты и компенсации выглядели как наличная выручка.
 */
final class SalePaymentMethodCatalog
{
    public const CASH = 'cash';
    public const CARD = 'card';
    public const BONUS = 'bonus';
    public const ALFA_SBP = 'alfa_sbp';
    public const ALFA_ACQUIRING = 'alfa_acquiring';
    public const APP_ACQUIRING_STUB = 'app_acquiring_stub';

    /** Выдача по сертификату (сумма номинала тарифа — для учёта, не «нал»). */
    public const CERTIFICATE = 'certificate';

    /**
     * Клиент уже оплатил (сбой эквайринга / продажа мимо CRM) —
     * абонемент выдан вручную, но это не бесплатная раздача.
     */
    public const PAID_OFFLINE = 'paid_offline';

    /** Бартер (блогер и т.п.) — сумма 0. */
    public const BARTER = 'barter';

    /** Служебный доступ (персонал) — сумма 0. */
    public const SERVICE = 'service';

    /** @var array<string, string> */
    public const LABELS = [
        self::CASH => 'Наличные',
        self::CARD => 'Карта',
        self::BONUS => 'Бонусы',
        self::ALFA_SBP => 'СБП (Альфа)',
        self::ALFA_ACQUIRING => 'Эквайринг (Альфа)',
        self::APP_ACQUIRING_STUB => 'Приложение (тест)',
        self::CERTIFICATE => 'Сертификат',
        self::PAID_OFFLINE => 'Уже оплачено (вне CRM)',
        self::BARTER => 'Бартер',
        self::SERVICE => 'Служебный',
    ];

    /**
     * Варианты при ручной выдаче абонемента (обязательный выбор).
     *
     * @return array<string, string>
     */
    public static function issueOptions(): array
    {
        return [
            self::CERTIFICATE => self::LABELS[self::CERTIFICATE],
            self::PAID_OFFLINE => self::LABELS[self::PAID_OFFLINE],
            self::BARTER => self::LABELS[self::BARTER],
            self::SERVICE => self::LABELS[self::SERVICE],
            self::CASH => self::LABELS[self::CASH],
            self::CARD => self::LABELS[self::CARD],
            self::BONUS => self::LABELS[self::BONUS],
        ];
    }

    /**
     * Варианты в модалке «Новая продажа».
     *
     * @return array<string, string>
     */
    public static function saleFormOptions(): array
    {
        return self::issueOptions();
    }

    public static function label(string $code): string
    {
        return self::LABELS[$code] ?? $code;
    }

    public static function isZeroSum(string $code): bool
    {
        return \in_array($code, [self::BARTER, self::SERVICE], true);
    }

    /** Учитывать в «Выручке» кассы (бартер/служебный/бонусы — нет). */
    public static function countsAsCashRevenue(string $code): bool
    {
        return !\in_array($code, [self::BARTER, self::SERVICE, self::BONUS], true);
    }

    public static function productPrefix(string $code): string
    {
        return match ($code) {
            self::CERTIFICATE => 'Сертификат',
            self::PAID_OFFLINE => 'Абонемент (уже оплачено)',
            self::BARTER => 'Бартер',
            self::SERVICE => 'Служебный',
            default => 'Абонемент',
        };
    }

    public static function isKnown(string $code): bool
    {
        return isset(self::LABELS[$code]);
    }
}
