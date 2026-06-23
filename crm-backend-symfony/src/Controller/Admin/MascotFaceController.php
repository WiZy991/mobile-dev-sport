<?php

namespace App\Controller\Admin;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/admin')]
final class MascotFaceController extends AbstractController
{
    private const FACES = ['neutral', 'happy', 'excited', 'thinking', 'celebrate', 'sad'];

    #[Route('/mascot/{face}.png', name: 'admin_mascot_face', requirements: ['face' => 'neutral|happy|excited|thinking|celebrate|sad'], methods: ['GET'])]
    public function face(string $face): Response
    {
        if (!\in_array($face, self::FACES, true)) {
            throw $this->createNotFoundException();
        }

        $paths = [
            $this->getParameter('kernel.project_dir') . '/assets/mascot/zalka-' . $face . '.png',
            $this->getParameter('kernel.project_dir') . '/public/img/mascot/zalka-' . $face . '.png',
        ];

        $file = null;
        foreach ($paths as $path) {
            if (is_readable($path)) {
                $file = $path;
                break;
            }
        }

        if ($file === null) {
            throw $this->createNotFoundException('Mascot face not found.');
        }

        return new Response(
            (string) file_get_contents($file),
            Response::HTTP_OK,
            [
                'Content-Type' => 'image/png',
                'Cache-Control' => 'public, max-age=604800',
            ],
        );
    }
}
