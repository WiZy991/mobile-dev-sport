<?php

namespace App\Controller\Admin;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Security\Http\Authentication\AuthenticationUtils;

final class AdminSecurityController extends AbstractController
{
    public function login(AuthenticationUtils $authUtils): Response
    {
        return $this->render('admin/login.html.twig', [
            'last_username' => $authUtils->getLastUsername(),
            'error' => $authUtils->getLastAuthenticationError(),
        ]);
    }

    public function loginCheck(): never
    {
        throw new \LogicException('Должно перехватываться фаерволом входа.');
    }

    public function logout(): never
    {
        throw new \LogicException('Должно перехватываться фаерволом выхода.');
    }
}
