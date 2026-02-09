<?php

namespace App\Controller\Admin;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/admin')]
class AdminController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {}
    private function getMenu(): array
    {
        return [
            'dashboard' => 'Дашборд',
            'tasks' => 'Задачи',
            'clients' => 'Клиенты',
            'schedule' => 'Расписание',
            'visits' => 'Посещения',
            'sales' => 'Продажи',
            'services' => 'Услуги',
            'leads' => 'Лиды',
            'deals' => 'Сделки',
            'comments' => 'Комментарии',
            'messengers' => 'Мессенджеры',
            'calls' => 'Звонки',
            'selfservice' => 'Самообслуживание',
            'cashdesk' => 'Касса',
            'warehouse' => 'Склад',
            'analytics' => 'Аналитика',
            'mobileapps' => 'Мобильные приложения',
            'staff' => 'Персонал',
            'documents' => 'Документы',
            'settings' => 'Настройки',
        ];
    }

    #[Route('', name: 'admin_dashboard', methods: ['GET'])]
    public function dashboard(): Response
    {
        // Считаем реальные данные, которые уже есть в системе
        $clientsCount = $this->em->getRepository(User::class)->count([]);

        return $this->render('admin/dashboard.html.twig', [
            'menu' => $this->getMenu(),
            'current' => 'dashboard',
            'stats' => [
                'clients' => $clientsCount,
                // Остальные показатели будут заполняться по мере добавления сущностей
                'leads' => 0,
                'trainingsToday' => 0,
                'revenueToday' => 0,
            ],
            'tasks' => [],
        ]);
    }

    #[Route('/{section}', name: 'admin_section', methods: ['GET'])]
    public function section(string $section): Response
    {
        $menu = $this->getMenu();
        if (!isset($menu[$section])) {
            throw $this->createNotFoundException();
        }

        // Специальная страница для клиентов с данными из БД
        if ($section === 'clients') {
            $clients = $this->em->getRepository(User::class)->findBy([], ['id' => 'ASC']);

            return $this->render('admin/clients.html.twig', [
                'menu' => $menu,
                'current' => $section,
                'clients' => $clients,
            ]);
        }

        return $this->render('admin/section.html.twig', [
            'menu' => $menu,
            'current' => $section,
            'title' => $menu[$section],
        ]);
    }
}

