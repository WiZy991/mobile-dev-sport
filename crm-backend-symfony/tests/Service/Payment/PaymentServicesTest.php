<?php

declare(strict_types=1);

namespace App\Tests\Service\Payment;

use App\Entity\ClientGroup;
use App\Entity\Payment;
use App\Entity\PromoCode;
use App\Entity\Subscription;
use App\Entity\SubscriptionPlan;
use App\Entity\User;
use App\Service\Api\SubscriptionFreezePolicy;
use App\Service\Payment\AlfaOrderStatus;
use App\Service\Notification\ClientNotificationScheduler;
use App\Service\Payment\PaymentStatusSyncService;
use App\Service\Payment\SubscriptionPurchaseQuoteService;
use Doctrine\ORM\EntityManagerInterface;
use Doctrine\ORM\EntityRepository;
use PHPUnit\Framework\TestCase;

final class SubscriptionPurchaseQuoteServiceTest extends TestCase
{
    public function testQuoteAppliesPercentDiscount(): void
    {
        $plan = (new SubscriptionPlan())
            ->setName('Test')
            ->setPrice(1000.0)
            ->setType('unlimited');

        $promo = (new PromoCode())
            ->setCode('SAVE10')
            ->setDiscountPercent(10.0)
            ->setIsActive(true);

        $em = $this->createMock(EntityManagerInterface::class);
        $repo = $this->createMock(EntityRepository::class);
        $repo->method('findOneBy')->willReturn($promo);
        $em->method('getRepository')->willReturn($repo);

        $service = new SubscriptionPurchaseQuoteService($em);
        $quote = $service->quote($plan, 'save10');

        self::assertSame(900.0, $quote->finalPrice);
        self::assertSame(100.0, $quote->discountAmount);
        self::assertSame(90000, $quote->amountKopecks);
    }

    public function testQuoteIgnoresInvalidPromo(): void
    {
        $plan = (new SubscriptionPlan())
            ->setName('Test')
            ->setPrice(500.0);

        $em = $this->createMock(EntityManagerInterface::class);
        $repo = $this->createMock(EntityRepository::class);
        $repo->method('findOneBy')->willReturn(null);
        $em->method('getRepository')->willReturn($repo);

        $service = new SubscriptionPurchaseQuoteService($em);
        $quote = $service->quote($plan, 'INVALID');

        self::assertSame(500.0, $quote->finalPrice);
        self::assertNull($quote->promo);
    }

    public function testQuoteAppliesClientGroupDiscount(): void
    {
        $plan = (new SubscriptionPlan())
            ->setName('Test')
            ->setPrice(1000.0)
            ->setType('unlimited');

        $group = (new ClientGroup())
            ->setName('VIP')
            ->setDiscountPercent(20.0);

        $user = (new User())
            ->setEmail('vip@test.ru')
            ->setPhone('+79990000001')
            ->setName('VIP')
            ->setClientGroup($group);

        $em = $this->createMock(EntityManagerInterface::class);
        $service = new SubscriptionPurchaseQuoteService($em);
        $quote = $service->quote($plan, '', false, $user);

        self::assertSame(1000.0, $quote->originalPrice);
        self::assertSame(800.0, $quote->finalPrice);
        self::assertSame(200.0, $quote->discountAmount);
        self::assertSame(20.0, $quote->groupDiscountPercent);
    }

    public function testQuoteStacksGroupThenPromoPercent(): void
    {
        $plan = (new SubscriptionPlan())
            ->setName('Test')
            ->setPrice(1000.0)
            ->setType('unlimited');

        $group = (new ClientGroup())
            ->setName('VIP')
            ->setDiscountPercent(20.0);

        $user = (new User())
            ->setEmail('vip@test.ru')
            ->setPhone('+79990000001')
            ->setName('VIP')
            ->setClientGroup($group);

        $promo = (new PromoCode())
            ->setCode('EXTRA10')
            ->setDiscountPercent(10.0)
            ->setIsActive(true);

        $em = $this->createMock(EntityManagerInterface::class);
        $repo = $this->createMock(EntityRepository::class);
        $repo->method('findOneBy')->willReturn($promo);
        $em->method('getRepository')->willReturn($repo);

        $service = new SubscriptionPurchaseQuoteService($em);
        $quote = $service->quote($plan, 'extra10', false, $user);

        // 1000 − 20% = 800; 800 − 10% = 720
        self::assertSame(720.0, $quote->finalPrice);
        self::assertSame(280.0, $quote->discountAmount);
    }
}

final class PaymentFulfillmentServiceTest extends TestCase
{
    public function testFulfillIsIdempotentWhenAlreadyPaid(): void
    {
        $user = (new User())->setEmail('u@test.ru')->setPhone('+79990000000')->setName('User');
        $plan = (new SubscriptionPlan())->setName('Plan')->setPrice(100.0)->setDurationDays(30)->setType('unlimited');
        $existingSub = (new Subscription())->setUser($user)->setPlan($plan)->setStatus('active');

        $payment = (new Payment())
            ->setUser($user)
            ->setSubscriptionPlan($plan)
            ->setAmountKopecks(10000)
            ->setStatus(Payment::STATUS_PAID)
            ->setSubscription($existingSub);

        $em = $this->createMock(EntityManagerInterface::class);
        $em->expects(self::never())->method('persist');

        $scheduler = $this->createMock(ClientNotificationScheduler::class);
        $scheduler->expects(self::never())->method('scheduleSubscriptionExpiryReminders');

        $service = new PaymentFulfillmentService($em, new SubscriptionFreezePolicy(), $scheduler);
        $sub = $service->fulfill($payment, 'CARD');

        self::assertSame($existingSub, $sub);
    }
}

final class PaymentStatusSyncServiceTest extends TestCase
{
    public function testSyncMarksFailedOnAmountMismatch(): void
    {
        $user = (new User())->setEmail('u@test.ru')->setPhone('+79990000000')->setName('User');
        $plan = (new SubscriptionPlan())->setName('Plan')->setPrice(100.0)->setType('unlimited');
        $payment = (new Payment())
            ->setUser($user)
            ->setSubscriptionPlan($plan)
            ->setAmountKopecks(10000)
            ->setStatus(Payment::STATUS_PENDING)
            ->setAlfaOrderId('order-123');

        $alfaClient = $this->createMock(\App\Service\Payment\AlfaAcquiringClient::class);
        $alfaClient->method('getOrderStatusExtended')->willReturn(new AlfaOrderStatus(
            orderStatus: 2,
            paymentState: 'DEPOSITED',
            amountKopecks: 5000,
            paymentWay: 'CARD',
        ));

        $em = $this->createMock(EntityManagerInterface::class);
        $em->expects(self::once())->method('flush');

        $fulfillment = $this->createMock(PaymentFulfillmentService::class);
        $fulfillment->expects(self::never())->method('fulfill');

        $service = new PaymentStatusSyncService($em, $alfaClient, $fulfillment);
        $result = $service->syncFromGateway($payment);

        self::assertSame(Payment::STATUS_FAILED, $result->getStatus());
        self::assertStringContainsString('Amount mismatch', (string) $result->getFailureReason());
    }
}
