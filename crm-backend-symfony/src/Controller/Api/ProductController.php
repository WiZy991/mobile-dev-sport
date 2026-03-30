<?php

namespace App\Controller\Api;

use App\Entity\Product;
use App\Entity\Sale;
use App\Service\CurrentUserResolver;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/products')]
class ProductController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
    ) {}

    #[Route('/{id}/purchase', name: 'api_products_purchase', methods: ['POST'])]
    public function purchase(string $id, Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $productId = str_starts_with($id, 'product-') ? (int) substr($id, 8) : (int) $id;
        /** @var Product|null $product */
        $product = $this->em->getRepository(Product::class)->find($productId);

        if (!$product || !$product->isActive()) {
            return $this->json(['error' => 'Product not found'], 404);
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $quantity = max(1, (int) ($data['quantity'] ?? 1));
        $paymentMethod = (string) ($data['payment_method'] ?? $data['paymentMethod'] ?? 'card');
        if (!in_array($paymentMethod, ['cash', 'card', 'bonus'], true)) {
            $paymentMethod = 'card';
        }

        $price = $product->getPrice();
        $total = $price * $quantity;

        $sale = (new Sale())
            ->setUser($user)
            ->setClientName($user->getName())
            ->setProductName($product->getName())
            ->setQuantity($quantity)
            ->setPrice($price)
            ->setTotal($total)
            ->setPaymentMethod($paymentMethod);

        $this->em->persist($sale);
        $this->em->flush();

        return $this->json([
            'success' => true,
            'sale_id' => $sale->getId(),
            'product' => $product->getName(),
            'quantity' => $quantity,
            'total' => $total,
        ], 201);
    }

    #[Route('', name: 'api_products_list', methods: ['GET'])]
    public function list(): JsonResponse
    {
        $products = $this->em->getRepository(Product::class)->findBy(
            ['isActive' => true],
            ['name' => 'ASC']
        );

        $data = array_map(static function (Product $p) {
            return [
                'id' => 'product-' . $p->getId(),
                'name' => $p->getName(),
                'description' => $p->getDescription(),
                'price' => $p->getPrice(),
                'category' => $p->getCategory(),
            ];
        }, $products);

        return $this->json($data);
    }
}
