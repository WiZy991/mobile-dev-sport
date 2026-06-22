<?php

declare(strict_types=1);

namespace App\Service\Lead;

use App\Entity\Lead;
use App\Entity\LeadNote;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

final class LeadIngestionService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    /**
     * Создаёт лид или дополняет открытый лид с тем же телефоном.
     */
    public function ingest(
        string $name,
        string $phone,
        ?string $email = null,
        string $source = LeadSource::SITE,
        ?string $comment = null,
        ?User $linkedUser = null,
    ): Lead {
        $name = trim($name);
        $phoneNorm = self::normalizePhone($phone);
        if ($name === '' || $phoneNorm === '') {
            throw new \InvalidArgumentException('Имя и телефон обязательны');
        }
        if (!LeadSource::isValid($source)) {
            $source = LeadSource::OTHER;
        }

        $existing = $this->findOpenByPhone($phoneNorm);
        if ($existing instanceof Lead) {
            return $this->enrichExisting($existing, $name, $email, $source, $comment, $linkedUser);
        }

        $lead = (new Lead())
            ->setName($name)
            ->setPhone(self::formatPhoneDisplay($phoneNorm))
            ->setEmail($email !== null && $email !== '' ? $email : null)
            ->setSource($source)
            ->setComment($comment)
            ->setStatus('new');

        if ($linkedUser !== null) {
            $lead->setConvertedUser($linkedUser);
        }

        $this->em->persist($lead);

        return $lead;
    }

    public function attachUserIfOpenLead(string $phone, User $user, string $noteText): void
    {
        $existing = $this->findOpenByPhone(self::normalizePhone($phone));
        if (!$existing instanceof Lead) {
            return;
        }
        if ($existing->getConvertedUser() === null) {
            $existing->setConvertedUser($user);
        }
        if ($noteText !== '') {
            $this->em->persist((new LeadNote())->setLead($existing)->setText($noteText));
        }
    }

    public static function normalizePhone(string $phone): string
    {
        $digits = preg_replace('/\D+/', '', $phone) ?? '';
        if (strlen($digits) === 11 && str_starts_with($digits, '8')) {
            $digits = '7' . substr($digits, 1);
        }
        if (strlen($digits) === 10) {
            $digits = '7' . $digits;
        }

        return $digits;
    }

    private static function formatPhoneDisplay(string $digits): string
    {
        if (strlen($digits) === 11 && str_starts_with($digits, '7')) {
            return sprintf(
                '+7 (%s) %s-%s-%s',
                substr($digits, 1, 3),
                substr($digits, 4, 3),
                substr($digits, 7, 2),
                substr($digits, 9, 2),
            );
        }

        return $digits;
    }

    private function findOpenByPhone(string $phoneNorm): ?Lead
    {
        if ($phoneNorm === '') {
            return null;
        }

        $leads = $this->em->createQueryBuilder()
            ->select('l')
            ->from(Lead::class, 'l')
            ->where('l.status IN (:statuses)')
            ->setParameter('statuses', ['new', 'trial_scheduled', 'trial_visited'])
            ->orderBy('l.id', 'DESC')
            ->getQuery()
            ->getResult();

        foreach ($leads as $lead) {
            if (self::normalizePhone($lead->getPhone()) === $phoneNorm) {
                return $lead;
            }
        }

        return null;
    }

    private function enrichExisting(
        Lead $lead,
        string $name,
        ?string $email,
        string $source,
        ?string $comment,
        ?User $linkedUser,
    ): Lead {
        if ($lead->getName() === '' || $lead->getName() === 'Гость') {
            $lead->setName($name);
        }
        if (($lead->getEmail() === null || $lead->getEmail() === '') && $email !== null && $email !== '') {
            $lead->setEmail($email);
        }
        if (($lead->getSource() === null || $lead->getSource() === '') && $source !== LeadSource::OTHER) {
            $lead->setSource($source);
        }
        if ($comment !== null && $comment !== '') {
            $prefix = $lead->getComment() ? $lead->getComment() . "\n" : '';
            $lead->setComment($prefix . $comment);
            $this->em->persist((new LeadNote())->setLead($lead)->setText($comment));
        }
        if ($linkedUser !== null && $lead->getConvertedUser() === null) {
            $lead->setConvertedUser($linkedUser);
        }

        return $lead;
    }
}
