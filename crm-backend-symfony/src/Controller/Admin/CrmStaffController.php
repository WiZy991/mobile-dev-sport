<?php

namespace App\Controller\Admin;

use App\Entity\StaffUser;
use App\Service\Admin\AdminMenuBuilder;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/admin/crm-staff')]
final class CrmStaffController extends AbstractController
{
    private const SELECTABLE_ROLES = [
        'ROLE_SUPER_ADMIN' => 'Суперадмин',
        'ROLE_ADMIN' => 'Админ',
        'ROLE_MANAGER' => 'Менеджер',
        'ROLE_FINANCE' => 'Финансы',
        'ROLE_TRAINER' => 'Тренер',
        'ROLE_VIEWER' => 'Только просмотр',
    ];

    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly UserPasswordHasherInterface $passwordHasher,
        private readonly AdminMenuBuilder $adminMenuBuilder,
    ) {
    }

    #[Route('', name: 'admin_crm_staff_index', methods: ['GET'])]
    public function index(): Response
    {
        $users = $this->em->getRepository(StaffUser::class)->findBy([], ['id' => 'ASC']);

        return $this->render('admin/crm_staff_index.html.twig', [
            'menu' => $this->menu(),
            'current' => 'crm_staff',
            'staffUsers' => $users,
        ]);
    }

    #[Route('/new', name: 'admin_crm_staff_new', methods: ['GET', 'POST'])]
    public function new(Request $request): Response
    {
        if ($request->isMethod('POST')) {
            return $this->processCreate($request);
        }

        return $this->render('admin/crm_staff_form.html.twig', [
            'menu' => $this->menu(),
            'current' => 'crm_staff',
            'staffUser' => null,
            'selectableRoles' => self::SELECTABLE_ROLES,
            'checkedRoles' => ['ROLE_VIEWER'],
        ]);
    }

    #[Route('/{id<\d+>}/edit', name: 'admin_crm_staff_edit', methods: ['GET', 'POST'])]
    public function edit(int $id, Request $request): Response
    {
        $staff = $this->em->getRepository(StaffUser::class)->find($id);
        if (!$staff) {
            throw $this->createNotFoundException();
        }

        if ($request->isMethod('POST')) {
            return $this->processUpdate($staff, $request);
        }

        $stored = array_values(array_filter(
            $staff->getRoles(),
            static fn (string $r) => $r !== 'ROLE_STAFF' && array_key_exists($r, self::SELECTABLE_ROLES)
        ));

        return $this->render('admin/crm_staff_form.html.twig', [
            'menu' => $this->menu(),
            'current' => 'crm_staff',
            'staffUser' => $staff,
            'selectableRoles' => self::SELECTABLE_ROLES,
            'checkedRoles' => $stored !== [] ? $stored : ['ROLE_VIEWER'],
        ]);
    }

    private function processCreate(Request $request): Response
    {
        $email = trim((string) $request->request->get('email', ''));
        $name = trim((string) $request->request->get('name', ''));
        $password = (string) $request->request->get('password', '');
        $roles = $this->normalizeRolesFromRequest($request);

        if ($email === '' || $name === '' || $password === '') {
            $this->addFlash('danger', 'Укажите email, имя и пароль.');
            return $this->redirectToRoute('admin_crm_staff_new');
        }

        if ($this->em->getRepository(StaffUser::class)->findOneBy(['email' => $email])) {
            $this->addFlash('danger', 'Пользователь с таким email уже есть.');
            return $this->redirectToRoute('admin_crm_staff_new');
        }

        $u = (new StaffUser())
            ->setEmail($email)
            ->setName($name)
            ->setRoles($roles)
            ->setIsActive($request->request->get('is_active') === '1');

        $u->setPassword($this->passwordHasher->hashPassword($u, $password));
        $this->em->persist($u);
        $this->em->flush();
        $this->addFlash('success', 'Учётная запись создана.');

        return $this->redirectToRoute('admin_crm_staff_index');
    }

    private function processUpdate(StaffUser $staff, Request $request): Response
    {
        $email = trim((string) $request->request->get('email', ''));
        $name = trim((string) $request->request->get('name', ''));
        $password = (string) $request->request->get('password', '');
        $roles = $this->normalizeRolesFromRequest($request);

        if ($email === '' || $name === '') {
            $this->addFlash('danger', 'Укажите email и имя.');
            return $this->redirectToRoute('admin_crm_staff_edit', ['id' => $staff->getId()]);
        }

        $other = $this->em->getRepository(StaffUser::class)->findOneBy(['email' => $email]);
        if ($other && $other->getId() !== $staff->getId()) {
            $this->addFlash('danger', 'Этот email занят другой учётной записью.');
            return $this->redirectToRoute('admin_crm_staff_edit', ['id' => $staff->getId()]);
        }

        $staff->setEmail($email)->setName($name)->setRoles($roles)
            ->setIsActive($request->request->get('is_active') === '1');

        if ($password !== '') {
            $staff->setPassword($this->passwordHasher->hashPassword($staff, $password));
        }

        $this->em->flush();
        $this->addFlash('success', 'Сохранено.');

        return $this->redirectToRoute('admin_crm_staff_index');
    }

    /** @param list<string|int|float|bool> $raw */
    private function normalizeRoles(array $raw): array
    {
        $out = [];
        foreach ($raw as $r) {
            $s = is_string($r) ? $r : (is_scalar($r) ? (string) $r : '');
            if ($s === '' || !array_key_exists($s, self::SELECTABLE_ROLES)) {
                continue;
            }
            $out[] = $s;
        }

        return array_values(array_unique($out !== [] ? $out : ['ROLE_VIEWER']));
    }

    private function normalizeRolesFromRequest(Request $request): array
    {
        $bag = $request->request->all();
        $raw = $bag['roles'] ?? [];

        return $this->normalizeRoles(is_array($raw) ? $raw : []);
    }

    /** @return array<string, string> */
    private function menu(): array
    {
        return $this->adminMenuBuilder->buildFor($this->getUser());
    }
}
