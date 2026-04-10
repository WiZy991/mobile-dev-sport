<?php

namespace App\Service\Admin;

use Symfony\Component\HttpFoundation\Request;

/**
 * Сопоставляет имя маршрута админки с «разделом» для проверки прав.
 */
final class AdminRouteSectionMapper
{
    /** @var array<string, string> */
    private const ROUTE_SECTION = [
        'admin_dashboard' => 'dashboard',
        'admin_search' => 'dashboard',
        'admin_section' => '_dynamic_section',
        'admin_client_new' => 'clients',
        'admin_client_show' => 'clients',
        'admin_client_update' => 'clients',
        'admin_client_block' => 'clients',
        'admin_note_new' => 'clients',
        'admin_clients_export' => 'clients',
        'admin_clients_import' => 'clients',
        'admin_subscription_freeze' => 'subscriptions',
        'admin_subscription_unfreeze' => 'subscriptions',
        'admin_subscription_issue' => 'subscriptions',
        'admin_plan_update' => 'subscriptions',
        'admin_plan_delete' => 'subscriptions',
        'admin_lead_new' => 'leads',
        'admin_lead_status' => 'leads',
        'admin_lead_convert' => 'leads',
        'admin_lead_comment' => 'leads',
        'admin_lead_note_new' => 'leads',
        'admin_booking_cancel' => 'bookings',
        'admin_task_new' => 'tasks',
        'admin_task_complete' => 'tasks',
        'admin_task_start' => 'tasks',
        'admin_task_update' => 'tasks',
        'admin_task_delete' => 'tasks',
        'admin_sale_new' => 'sales',
        'admin_training_new' => 'schedule',
        'admin_training_update' => 'schedule',
        'admin_training_delete' => 'schedule',
        'admin_trainer_new' => 'trainers',
        'admin_product_toggle' => 'warehouse',
        'admin_product_stock' => 'warehouse',
        'admin_product_update' => 'warehouse',
        'admin_trainer_delete' => 'trainers',
        'admin_trainer_update' => 'trainers',
        'admin_settings_club' => 'settings',
        'admin_perco_test' => 'settings',
        'admin_visits_export' => 'visits',
        'admin_document_upload' => 'documents',
        'admin_document_delete' => 'documents',
        'admin_document_download' => 'documents',
        'admin_promo_delete' => 'promocodes',
        'admin_promo_toggle' => 'promocodes',
        'admin_promo_update' => 'promocodes',
        'admin_promotion_update' => 'promotions',
        'admin_promotion_toggle' => 'promotions',
        'admin_promotion_delete' => 'promotions',
        'admin_selfservice_export' => 'selfservice',
        'admin_tag_new' => 'tags',
        'admin_tag_delete' => 'tags',
        'admin_expense_new' => 'finance',
        'admin_expense_delete' => 'finance',
        'admin_sber_id_start' => 'clients',
        'admin_sber_id_callback' => 'clients',
        'admin_crm_staff_index' => 'crm_staff',
        'admin_crm_staff_new' => 'crm_staff',
        'admin_crm_staff_edit' => 'crm_staff',
    ];

    public function resolveSection(Request $request): ?string
    {
        $route = $request->attributes->get('_route');
        if (!is_string($route)) {
            return null;
        }
        if ($route === 'admin_section') {
            $section = $request->attributes->get('section');
            return is_string($section) ? $section : null;
        }
        return self::ROUTE_SECTION[$route] ?? null;
    }
}
