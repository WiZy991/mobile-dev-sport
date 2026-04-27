<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

class LandingController extends AbstractController
{
    #[Route('/', name: 'landing_page', methods: ['GET'])]
    public function index(): Response
    {
        return $this->render('landing/index.html.twig', [
            'admin_url' => '/admin',
        ]);
    }
}
