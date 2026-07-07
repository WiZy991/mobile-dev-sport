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
    ) {
    }

    #[Route('/organizations', name: 'admin_platform_organizations', methods: ['GET'])]
    public function organizations(): Response
    {
        $organizations = $this->em->getRepository(Organization::class)->findBy([], ['id' => 'DESC']);

        return $this->render('admin/platform/organizations.html.twig', [
            'menu' => $this->menu(),
            'current' => 'platform',
            'organizations' => $organizations,
        ]);
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
            'tariff' => 'demo',
        ]);
    }

    private function processCreate(Request $request): Response
    {
        $name = trim((string) $request->request->get('name', ''));
        $slug = trim((string) $request->request->get('slug', ''));
        $orgEmail = trim((string) $request->request->get('org_email', ''));
        $orgPhone = trim((string) $request->request->get('org_phone', ''));
        $adminEmail = trim((string) $request->request->get('admin_email', ''));
        $adminPassword = (string) $request->request->get('admin_password', '');
        $adminName = trim((string) $request->request->get('admin_name', 'Администратор'));
        $demoDays = max(1, (int) $request->request->get('demo_days', 14));
        $tariff = trim((string) $request->request->get('tariff', 'demo'));

        if ($name === '' || $slug === '' || $adminEmail === '' || $adminPassword === '') {
            $this->addFlash('danger', 'Заполните название, slug, email и пароль администратора.');

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
