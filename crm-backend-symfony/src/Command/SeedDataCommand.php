<?php

declare(strict_types=1);

namespace App\Command;

use App\Entity\Club;
use App\Entity\ClubSetting;
use App\Entity\Locker;
use App\Entity\Product;
use App\Entity\SubscriptionPlan;
use App\Entity\Trainer;
use App\Entity\Training;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

#[AsCommand(
    name: 'app:seed-data',
    description: 'Заполняет БД тестовыми данными для работы API',
)]
class SeedDataCommand extends Command
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);

        // Проверяем, есть ли уже данные
        $userCount = $this->em->getRepository(User::class)->count([]);
        if ($userCount > 0) {
            $io->note('Данные уже есть в БД. Для перезаписи: doctrine:schema:drop --full --force, миграции, затем app:seed-data.');
            return Command::SUCCESS;
        }

        // 1. Пользователь
        $user = (new User())
            ->setEmail('user@example.com')
            ->setName('Тестовый пользователь')
            ->setPhone('+7 900 123-45-67')
            ->setBonusPoints(100);
        $this->em->persist($user);

        // 2. Тренеры (один тренер — как в типичной CRM; все слоты привязаны к нему)
        $trainers = [
            ['Тренер клуба', 'Персональные и групповые тренировки', 5.0],
        ];
        $trainerEntities = [];
        foreach ($trainers as [$name, $spec, $rating]) {
            $t = (new Trainer())
                ->setName($name)
                ->setSpecialization($spec)
                ->setRating($rating);
            $this->em->persist($t);
            $trainerEntities[] = $t;
        }

        // 3. Тренировки (на ближайшие 7 дней)
        $trainingNames = [
            'Йога для начинающих',
            'Силовая тренировка',
            'Функциональный тренинг',
            'Стретчинг',
            'HIIT',
            'Пилатес',
        ];
        $today = new \DateTimeImmutable('today');
        for ($d = 0; $d < 7; $d++) {
            $date = $today->modify("+{$d} days");
            for ($i = 0; $i < 3; $i++) {
                $start = $date->setTime(9 + $i * 3, 0);
                $end = $start->modify('+1 hour');
                $name = $trainingNames[($d + $i) % count($trainingNames)];
                $trainer = $trainerEntities[$i % count($trainerEntities)];
                $t = (new Training())
                    ->setName($name)
                    ->setDescription('Групповое занятие')
                    ->setType('group')
                    ->setTrainer($trainer)
                    ->setStartAt($start)
                    ->setEndAt($end)
                    ->setRoom('Зал ' . ($i + 1))
                    ->setMaxParticipants(15)
                    ->setCurrentParticipants(0);
                $this->em->persist($t);
            }
        }

        // 3b. Персональные тренировки (экран «Индивидуальная тренировка» в приложении)
        // Окно ~90 дней от «сегодня», иначе при листании месяцев слотов не видно (раньше было только 7 дней).
        $personalHorizonDays = 90;
        $personalNames = ['Йога тренировка', 'Силовая тренировка', 'Пилатес', 'Бокс'];
        for ($d = 0; $d < $personalHorizonDays; $d++) {
            $date = $today->modify("+{$d} days");
            for ($i = 0; $i < 2; $i++) {
                $start = $date->setTime(9 + $i * 2, 0);
                $end = $start->modify('+1 hour');
                $name = $personalNames[$i % count($personalNames)];
                $trainer = $trainerEntities[$i % count($trainerEntities)];
                $t = (new Training())
                    ->setName($name)
                    ->setDescription('Персональное занятие')
                    ->setType('personal')
                    ->setTrainer($trainer)
                    ->setStartAt($start)
                    ->setEndAt($end)
                    ->setRoom('Зал ' . ($i + 1))
                    ->setMaxParticipants(1)
                    ->setCurrentParticipants(0);
                $this->em->persist($t);
            }
        }

        // 3c. Допуслуги (тип extra — запись как на групповое)
        $extraNames = ['Солярий 10 мин', 'Массаж классический'];
        for ($d = 0; $d < 7; $d++) {
            $date = $today->modify("+{$d} days");
            $start = $date->setTime(18, 0);
            $end = $start->modify('+30 minutes');
            $t = (new Training())
                ->setName($extraNames[$d % count($extraNames)])
                ->setDescription('Дополнительная услуга')
                ->setType('extra')
                ->setTrainer($trainerEntities[0])
                ->setStartAt($start)
                ->setEndAt($end)
                ->setRoom('Зона сервиса')
                ->setMaxParticipants(3)
                ->setCurrentParticipants(0);
            $this->em->persist($t);
        }

        // 4. Тарифные планы
        $plans = [
            ['Базовый', 'Безлимитный доступ в зал', 2990, 30, null, 'unlimited', false],
            ['Стандарт', 'Зал + групповые занятия', 4990, 30, null, 'unlimited', true],
            ['Премиум', 'Всё включено + персональные тренировки', 7990, 30, null, 'unlimited', false],
            ['12 посещений', 'Ограниченный абонемент', 1990, null, 12, 'limited', false],
        ];
        foreach ($plans as [$name, $desc, $price, $days, $visits, $type, $popular]) {
            $p = (new SubscriptionPlan())
                ->setName($name)
                ->setDescription($desc)
                ->setPrice($price)
                ->setDurationDays($days)
                ->setVisitsCount($visits)
                ->setType($type)
                ->setIsPopular($popular);
            $this->em->persist($p);
        }

        // 5. Товары/услуги
        $products = [
            ['Полотенце', 'Махровое полотенце', 350, 'goods'],
            ['Бутылка воды', 'Питьевая вода 0.5л', 100, 'goods'],
            ['Персональная тренировка', '1 занятие с тренером', 2500, 'service'],
            ['Массаж', 'Спортивный массаж 60 мин', 3500, 'service'],
        ];
        foreach ($products as [$name, $desc, $price, $cat]) {
            $p = (new Product())
                ->setName($name)
                ->setDescription($desc)
                ->setPrice($price)
                ->setCategory($cat)
                ->setIsActive(true);
            $this->em->persist($p);
        }

        // 6. Клуб
        $club = (new Club())
            ->setName('FitnessClub Центр')
            ->setAddress('г. Москва, ул. Примерная, д. 1')
            ->setPhone('+7 (495) 123-45-67')
            ->setEmail('info@fitnessclub.ru')
            ->setWorkingHours('Пн-Пт: 7:00–23:00, Сб-Вс: 9:00–21:00')
            ->setLatitude(55.7558)
            ->setLongitude(37.6173)
            ->setAmenitiesJson(json_encode(['Тренажёрный зал', 'Бассейн', 'Йога', 'Групповые занятия']))
            ->setMaxCapacity(100);
        $this->em->persist($club);

        // 7. Настройки клуба (ClubSetting)
        $settings = [
            ['name', 'FitnessClub'],
            ['address', 'г. Москва, ул. Примерная, д. 1'],
            ['phone', '+7 (495) 123-45-67'],
            ['email', 'info@fitnessclub.ru'],
            ['working_hours', 'Пн-Пт: 7:00–23:00, Сб-Вс: 9:00–21:00'],
            ['amenities', 'Тренажёрный зал, Бассейн, Йога, Групповые занятия'],
            ['latitude', '55.7558'],
            ['longitude', '37.6173'],
            ['gym_max_capacity', '100'],
            ['promo_home_title', 'СКИДКА 20%!'],
            ['promo_home_subtitle', 'на все карты 12 и 6 месяцев'],
        ];
        foreach ($settings as [$key, $value]) {
            $s = (new ClubSetting())
                ->setSettingKey($key)
                ->setSettingValue($value);
            $this->em->persist($s);
        }

        // 8. Шкафчики
        for ($n = 1; $n <= 20; $n++) {
            $l = (new Locker())
                ->setNumber((string) $n)
                ->setStatus(Locker::STATUS_AVAILABLE);
            $this->em->persist($l);
        }

        $this->em->flush();

        $io->success('Тестовые данные успешно добавлены!');
        $io->listing([
            '1 пользователь (user@example.com)',
            count($trainers) . ' тренер(ов)',
            'групповые + персональные + extra на горизонте из сида',
            '4 тарифных плана',
            '4 товара/услуги',
            '1 клуб',
            '20 шкафчиков',
        ]);

        return Command::SUCCESS;
    }
}
