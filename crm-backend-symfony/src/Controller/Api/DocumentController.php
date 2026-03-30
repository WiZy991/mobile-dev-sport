<?php

namespace App\Controller\Api;

use App\Entity\Document;
use App\Service\CurrentUserResolver;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\BinaryFileResponse;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\ResponseHeaderBag;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/documents')]
class DocumentController extends AbstractController
{
    private const ALLOWED_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png'];
    private const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
    ) {}

    #[Route('', name: 'api_documents_list', methods: ['GET'])]
    public function list(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);

        $repo = $this->em->getRepository(Document::class);
        $qb = $repo->createQueryBuilder('d')
            ->orderBy('d.createdAt', 'DESC');
        if ($user) {
            $qb->where('d.user IS NULL OR d.user = :user')->setParameter('user', $user);
        } else {
            $qb->where('d.user IS NULL');
        }
        $docs = $qb->getQuery()->getResult();

        $list = array_map(function (Document $d) use ($user) {
            return [
                'id' => (string) $d->getId(),
                'name' => $d->getName(),
                'category' => $d->getCategory(),
                'created_at' => $d->getCreatedAt()->format('Y-m-d\TH:i:s'),
                'is_mine' => $user && $d->getUser() && $d->getUser()->getId() === $user->getId(),
            ];
        }, $docs);

        return $this->json($list);
    }

    #[Route('', name: 'api_documents_upload', methods: ['POST'])]
    public function upload(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $file = $request->files->get('file');
        $name = trim((string) ($request->request->get('name') ?? ''));
        $category = trim((string) ($request->request->get('category') ?? '')) ?: null;

        if (!$file || !$file->isValid()) {
            return $this->json(['error' => 'File is required and must be valid'], 400);
        }

        if ($file->getSize() > self::MAX_FILE_SIZE) {
            return $this->json(['error' => 'File too large. Max 10 MB'], 400);
        }

        $ext = strtolower($file->getClientOriginalExtension());
        if (!in_array($ext, self::ALLOWED_EXTENSIONS, true)) {
            return $this->json(['error' => 'Allowed formats: PDF, JPG, PNG'], 400);
        }

        $uploadDir = $this->getParameter('kernel.project_dir') . '/var/uploads/documents';
        if (!is_dir($uploadDir)) {
            mkdir($uploadDir, 0755, true);
        }

        $filename = bin2hex(random_bytes(8)) . '_' . preg_replace('/[^a-zA-Z0-9._-]/', '_', $file->getClientOriginalName());
        $file->move($uploadDir, $filename);

        $displayName = $name !== '' ? $name : $file->getClientOriginalName();

        $doc = (new Document())
            ->setName($displayName)
            ->setFilename($filename)
            ->setCategory($category)
            ->setUser($user);

        $this->em->persist($doc);
        $this->em->flush();

        return $this->json([
            'id' => (string) $doc->getId(),
            'name' => $doc->getName(),
            'category' => $doc->getCategory(),
            'created_at' => $doc->getCreatedAt()->format('Y-m-d\TH:i:s'),
            'is_mine' => true,
        ], 201);
    }

    #[Route('/{id}/download', name: 'api_documents_download', methods: ['GET'])]
    public function download(int $id, Request $request): Response
    {
        $user = $this->userResolver->resolve($request);
        $doc = $this->em->getRepository(Document::class)->find($id);
        if (!$doc) {
            return $this->json(['error' => 'Document not found'], 404);
        }

        // Only owner or club doc (user_id null)
        if ($doc->getUser() !== null && (!$user || $doc->getUser()->getId() !== $user->getId())) {
            return $this->json(['error' => 'Access denied'], 403);
        }

        $path = $this->getParameter('kernel.project_dir') . '/var/uploads/documents/' . $doc->getFilename();
        if (!file_exists($path)) {
            return $this->json(['error' => 'File not found'], 404);
        }

        $ext = pathinfo($doc->getFilename(), PATHINFO_EXTENSION);
        $response = new BinaryFileResponse($path);
        $response->setContentDisposition(
            ResponseHeaderBag::DISPOSITION_ATTACHMENT,
            $doc->getName() . '.' . ($ext ?: 'pdf')
        );

        return $response;
    }
}
