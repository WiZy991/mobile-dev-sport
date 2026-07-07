<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Organization;
use App\Entity\StaffUser;
use App\Service\Admin\AdminMenuBuilder;
use App\Service\Tenant\OrganizationProvisioner;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/admin/platform')]
#[IsGranted('ROLE_SUPER_ADMIN')]
final class AdminPlatformController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly AdminMenuBuilder $adminMenuBuilder,
        private readonly OrganizationProvisioner $provisioner,
        private readonly string $defaultOrganizationSlug = 'demo',
    ) {
    }

    #[Route('/organizations', name: 'admin_platform_organizations', methods: ['GET'])]
    public function organizations(): Response
    {
        $qb = $this->em->getRepository(Organization::class)->createQueryBuilder('o')
            ->orderBy('o.id', 'DESC');
        if ($this->defaultOrganizationSlug !== '') {
            $qb->andWhere('o.slug <> :defaultSlug')
                ->setParameter('defaultSlug', $this->defaultOrganizationSlug);
        }
        $organizations = $qb->getQuery()->getResult();

        return $this->render('admin/platform/organizations.html.twig', [
            'menu' => $this->menu(),
            'current' => 'platform',
            'organizations' => $organizations,
        ]);
    }

    #[Route('/organizations/{id}/toggle-active', name: 'admin_platform_organization_toggle_active', methods: ['POST'])]
    public function toggleOrganizationActive(int $id): Response
    {
        $org = $this->em->getRepository(Organization::class)->find($id);
        if (!$org instanceof Organization) {
            throw $this->createNotFoundException('Организация не найдена.');
        }
        if ($org->getSlug() === $this->defaultOrganizationSlug) {
            $this->addFlash('warning', 'Основную CRM нельзя отключать.');
            return $this->redirectToRoute('admin_platform_organizations');
        }

        $org->setIsActive(!$org->isActive());
        $this->em->flush();

        $this->addFlash('success', $org->isActive()
            ? 'Организация включена.'
            : 'Организация отключена. Вход в CRM для её сотрудников заблокирован.');

        return $this->redirectToRoute('admin_platform_organizations');
    }

    #[Route('/organizations/{id}/delete', name: 'admin_platform_organization_delete', methods: ['POST'])]
    public function deleteOrganization(int $id): Response
    {
        $org = $this->em->getRepository(Organization::class)->find($id);
        if (!$org instanceof Organization) {
            throw $this->createNotFoundException('Организация не найдена.');
        }
        if ($org->getSlug() === $this->defaultOrganizationSlug) {
            $this->addFlash('warning', 'Основную CRM нельзя удалить.');
            return $this->redirectToRoute('admin_platform_organizations');
        }

        try {
            $this->em->remove($org);
            $this->em->flush();
        } catch (\Throwable) {
            $this->addFlash('danger', 'Не удалось удалить организацию: есть связанные данные или ограничения БД.');
            return $this->redirectToRoute('admin_platform_organizations');
        }

        $this->addFlash('success', 'Организация удалена.');

        return $this->redirectToRoute('admin_platform_organizations');
    }

    #[Route('/organizations/new', name: 'admin_platform_organization_new', methods: ['GET', 'POST'])]
    public function newOrganization(Request $request): Response
    {
        if ($request->isMethod('POST')) {
            return $this->processCreate($request);
        }

        $name = trim((string) $request->query->get('name', ''));
        $slug = $name !== '' ? OrganizationProvisioner::slugFromName($name) : '';

        return $this->render('admin/platform/organization_form.html.twig', [
            'menu' => $this->menu(),
            'current' => 'platform',
            'name' => $name,
            'slug' => $slug,
            'orgEmail' => '',
            'orgPhone' => '',
            'adminEmail' => '',
            'adminName' => 'Администратор',
            'demoDays' => 14,
            'tariff' => 'start',
            'subscriptionStartsAt' => (new \DateTimeImmutable('today'))->format('Y-m-d'),
            'subscriptionEndsAt' => (new \DateTimeImmutable('today'))->modify('+30 days')->format('Y-m-d'),
        ]);
    }

    private function processCreate(Request $request): Response
    {
        $name = trim((string) $request->request->get('name', ''));
        $slug = trim((string) $request->request->get('slug', ''));
        $orgEmail = mb_strtolower(trim((string) $request->request->get('org_email', '')));
        $orgPhone = trim((string) $request->request->get('org_phone', ''));
        $adminEmail = mb_strtolower(trim((string) $request->request->get('admin_email', '')));
        $adminPassword = (string) $request->request->get('admin_password', '');
        $adminName = trim((string) $request->request->get('admin_name', 'Администратор'));
        $demoDays = max(1, (int) $request->request->get('demo_days', 14));
        $tariff = trim((string) $request->request->get('tariff', 'start'));
        $subscriptionStartsRaw = trim((string) $request->request->get('subscription_starts_at', ''));
        $subscriptionEndsRaw = trim((string) $request->request->get('subscription_ends_at', ''));

        if ($name === '' || $slug === '' || $adminEmail === '' || $adminPassword === '') {
            $this->addFlash('danger', 'Заполните название, slug, email и пароль администратора.');

            return $this->redirectToRoute('admin_platform_organization_new');
        }
        if (!filter_var($adminEmail, FILTER_VALIDATE_EMAIL)) {
            $this->addFlash('danger', 'Email администратора указан в неверном формате.');

            return $this->redirectToRoute('admin_platform_organization_new');
        }
        if ($orgEmail !== '' && !filter_var($orgEmail, FILTER_VALIDATE_EMAIL)) {
            $this->addFlash('danger', 'Email организации указан в неверном формате.');

            return $this->redirectToRoute('admin_platform_organization_new');
        }
        if (!in_array($tariff, ['demo', 'start', 'business', 'network'], true)) {
            $this->addFlash('danger', 'Выберите тариф из списка.');

            return $this->redirectToRoute('admin_platform_organization_new');
        }

        $subscriptionStartsAt = \DateTimeImmutable::createFromFormat('Y-m-d', $subscriptionStartsRaw) ?: null;
        $subscriptionEndsAt = \DateTimeImmutable::createFromFormat('Y-m-d', $subscriptionEndsRaw) ?: null;
        if (!$subscriptionStartsAt || !$subscriptionEndsAt) {
            $this->addFlash('danger', 'Укажите корректные даты начала и окончания подписки.');

            return $this->redirectToRoute('admin_platform_organization_new');
        }
        if ($subscriptionEndsAt <= $subscriptionStartsAt) {
            $this->addFlash('danger', 'Дата окончания подписки должна быть позже даты начала.');

            return $this->redirectToRoute('admin_platform_organization_new');
        }

        try {
            $this->provisioner->provision(
                $name,
                $slug,
                $adminEmail,
                $adminPassword,
                $adminName,
                $orgEmail !== '' ? $orgEmail : null,
                $orgPhone !== '' ? $orgPhone : null,
                $demoDays,
                $tariff,
                $subscriptionStartsAt,
                $subscriptionEndsAt,
            );
        } catch (\InvalidArgumentException $e) {
            $this->addFlash('danger', $e->getMessage());

            return $this->redirectToRoute('admin_platform_organization_new');
        }

        $this->addFlash('success', 'Организация создана. Передайте клиенту URL /admin, email и пароль.');

        return $this->redirectToRoute('admin_platform_organizations');
    }

    /** @return array<string, string> */
    private function menu(): array
    {
        return $this->adminMenuBuilder->buildFor($this->getUser());
    }
}
