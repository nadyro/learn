-- LOCAL DEVELOPMENT DATA ONLY.
-- This folder is added to Flyway's locations by the "local" profile exclusively (see application-local.yml).
-- It must never run in staging or production. Safe to re-run: existing rows are left untouched.

insert into products (id, sku, name, description, price_amount, price_currency, status, created_at, updated_at, version)
values ('0192f0a0-0000-7000-8000-000000000001', 'TENT-ALPINE-2P', 'Alpine 2-person tent',
        'Four-season dome tent, 2.4 kg, aluminium poles.', 349.00, 'EUR', 'ACTIVE', now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000002', 'TENT-TRAIL-1P', 'Trail ultralight 1-person tent',
        'Single-wall trekking-pole tent, 780 g.', 279.00, 'EUR', 'ACTIVE', now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000003', 'PACK-SUMMIT-45', 'Summit 45 L backpack',
        'Alpine pack with removable lid and ice axe loops.', 189.90, 'EUR', 'ACTIVE', now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000004', 'PACK-DAY-22', 'Daypack 22 L',
        'Lightweight daypack with hydration sleeve.', 79.90, 'EUR', 'ACTIVE', now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000005', 'BAG-DOWN-M5', 'Down sleeping bag -5°C',
        '800 fill power responsibly sourced down.', 299.00, 'EUR', 'ACTIVE', now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000006', 'MAT-INFL-R4', 'Insulated sleeping mat R4.2',
        'Inflatable mat, 7.5 cm thick.', 139.00, 'EUR', 'ACTIVE', now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000007', 'LAMP-HEAD-400', 'Headlamp 400 lm',
        'Rechargeable headlamp with red light mode.', 49.90, 'EUR', 'ACTIVE', now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000008', 'STOVE-GAS-MINI', 'Mini gas stove',
        'Folding canister stove, 3000 W.', 39.90, 'EUR', 'ACTIVE', now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000009', 'JACKET-SHELL-M', 'Storm shell jacket (M)',
        '3-layer waterproof breathable shell.', 249.00, 'EUR', 'ACTIVE', now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000010', 'POLES-CARBON', 'Carbon trekking poles (pair)',
        'Foldable carbon poles, 2 x 230 g.', 119.00, 'EUR', 'ACTIVE', now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000011', 'BOTTLE-STEEL-1L', 'Insulated bottle 1 L',
        'Keeps drinks hot 12 h, cold 24 h.', 34.90, 'EUR', 'ACTIVE', now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000012', 'TENT-CLASSIC-4P', 'Classic family tent 4P',
        'Discontinued model, kept for order history.', 199.00, 'EUR', 'ARCHIVED', now(), now(), 0)
on conflict (id) do nothing;

insert into inventory_items (product_id, on_hand, reserved, created_at, updated_at, version)
values ('0192f0a0-0000-7000-8000-000000000001', 25, 0, now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000002', 10, 0, now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000003', 40, 0, now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000004', 120, 0, now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000005', 15, 0, now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000006', 60, 0, now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000007', 200, 0, now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000008', 80, 0, now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000009', 3, 0, now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000010', 0, 0, now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000011', 150, 0, now(), now(), 0),
       ('0192f0a0-0000-7000-8000-000000000012', 0, 0, now(), now(), 0)
on conflict (product_id) do nothing;
