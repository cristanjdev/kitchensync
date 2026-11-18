-- KitchenSync 360 - Demo Accounts & Seed Data
-- Run in Supabase SQL Editor after migrations/20261116000000_kitchensync360_schema.sql
-- and supabase/upgrade-order-safety.sql. WARNING: deletes and replaces demo restaurant orders.
--
-- The demo password is NOT stored in this repo. Put this line first (same run), with your own password:
--   select set_config('ks360.demo_password', 'choose-a-strong-password', false);

do $$
declare
    v_controller_id uuid := '11111111-1111-1111-1111-111111111111';
    v_kitchen_id    uuid := '22222222-2222-2222-2222-222222222222';
    v_waiter_id     uuid := '33333333-3333-3333-3333-333333333333';
    v_other_id      uuid := '44444444-4444-4444-4444-444444444444';
    v_order_25      uuid := 'aaaa0025-0000-0000-0000-000000000025';
    v_order_26      uuid := 'aaaa0026-0000-0000-0000-000000000026';
    v_order_27      uuid := 'aaaa0027-0000-0000-0000-000000000027';
    v_password      text := nullif(current_setting('ks360.demo_password', true), '');
begin
    if v_password is null then
        raise exception 'Set the demo password first: select set_config(''ks360.demo_password'', ''...'', false);';
    end if;

    -- 1. Create or update demo Auth accounts (password from ks360.demo_password)
    insert into auth.users (
        instance_id, id, aud, role, email, encrypted_password,
        email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
        confirmation_token, recovery_token, email_change_token_new, email_change
    ) values
    (
        '00000000-0000-0000-0000-000000000000',
        v_controller_id,
        'authenticated',
        'authenticated',
        'controller@kitchensync360.com',
        crypt(v_password, gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}',
        '{"display_name":"Central Controller"}',
        now(),
        now(),
        '', '', '', ''
    ),
    (
        '00000000-0000-0000-0000-000000000000',
        v_kitchen_id,
        'authenticated',
        'authenticated',
        'kitchen@kitchensync360.com',
        crypt(v_password, gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}',
        '{"display_name":"Kitchen Station Staff"}',
        now(),
        now(),
        '', '', '', ''
    ),
    (
        '00000000-0000-0000-0000-000000000000',
        v_waiter_id,
        'authenticated',
        'authenticated',
        'waiter@kitchensync360.com',
        crypt(v_password, gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}',
        '{"display_name":"Assigned Waiter"}',
        now(),
        now(),
        '', '', '', ''
    ),
    (
        '00000000-0000-0000-0000-000000000000',
        v_other_id,
        'authenticated',
        'authenticated',
        'other@anotherbistro.com',
        crypt(v_password, gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}',
        '{"display_name":"Other Restaurant User"}',
        now(),
        now(),
        '', '', '', ''
    )
    on conflict (id) do nothing;

    -- Supabase Auth cannot scan NULL token columns ("Database error querying schema" at login).
    -- Repair accounts created by an earlier run of this script.
    update auth.users
    set confirmation_token     = coalesce(confirmation_token, ''),
        recovery_token         = coalesce(recovery_token, ''),
        email_change_token_new = coalesce(email_change_token_new, ''),
        email_change           = coalesce(email_change, '')
    where id in (v_controller_id, v_kitchen_id, v_waiter_id, v_other_id);

    -- Email identities so password sign-in resolves the provider like dashboard-created users.
    insert into auth.identities (
        provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at
    )
    select
        u.id::text,
        u.id,
        jsonb_build_object('sub', u.id::text, 'email', u.email, 'email_verified', true),
        'email',
        now(), now(), now()
    from auth.users u
    where u.id in (v_controller_id, v_kitchen_id, v_waiter_id, v_other_id)
      and not exists (
          select 1 from auth.identities i
          where i.user_id = u.id and i.provider = 'email'
      );

    -- 2. Create corresponding profiles
    insert into public.profiles (id, email, restaurant_id, role, display_name)
    values
        (v_controller_id, 'controller@kitchensync360.com', 'rest_demo_001', 'controller', 'Central Controller'),
        (v_kitchen_id,    'kitchen@kitchensync360.com',    'rest_demo_001', 'kitchen',    'Kitchen Station Staff'),
        (v_waiter_id,     'waiter@kitchensync360.com',     'rest_demo_001', 'waiter',     'Assigned Waiter'),
        (v_other_id,      'other@anotherbistro.com',       'rest_other_999', 'controller', 'Other Restaurant')
    on conflict (id) do update
    set email = excluded.email,
        restaurant_id = excluded.restaurant_id,
        role = excluded.role,
        display_name = excluded.display_name;

    -- 3. Seed sample orders for 'rest_demo_001'
    delete from public.orders where restaurant_id = 'rest_demo_001';

    -- Order #25: Table 12 (Preparing across Grill, Fryer, Pastry)
    insert into public.orders (
        id, order_number, restaurant_id, table_number, assigned_waiter_id, status, created_at, updated_at
    ) values (
        v_order_25, 25, 'rest_demo_001', 12, v_waiter_id, 'preparing', now() - interval '4 minutes', now()
    );

    insert into public.order_items (
        order_id, restaurant_id, menu_item_id, menu_label, quantity, station, status
    ) values
        (v_order_25, 'rest_demo_001', 'grill_chicken', 'Grilled Chicken', 1, 'grill', 'preparing'),
        (v_order_25, 'rest_demo_001', 'fryer_fries', 'French Fries', 2, 'fryer', 'ready'),
        (v_order_25, 'rest_demo_001', 'pastry_choco_cake', 'Chocolate Cake', 1, 'pastry', 'pending');

    -- Order #26: Table 8 (Ready across Grill, Fryer, Pastry)
    insert into public.orders (
        id, order_number, restaurant_id, table_number, assigned_waiter_id, status, created_at, ready_at, updated_at
    ) values (
        v_order_26, 26, 'rest_demo_001', 8, v_waiter_id, 'ready', now() - interval '6 minutes', now() - interval '10 seconds', now()
    );

    insert into public.order_items (
        order_id, restaurant_id, menu_item_id, menu_label, quantity, station, status, ready_at
    ) values
        (v_order_26, 'rest_demo_001', 'grill_salmon', 'Pan-Seared Salmon', 1, 'grill', 'ready', now() - interval '25 seconds'),
        (v_order_26, 'rest_demo_001', 'fryer_croquettes', 'Potato Croquettes', 1, 'fryer', 'ready', now() - interval '18 seconds'),
        (v_order_26, 'rest_demo_001', 'pastry_cheesecake', 'New York Cheesecake', 1, 'pastry', 'ready', now() - interval '10 seconds');

    -- Order #27: Table 15 (Acknowledged, Grill + Fryer only, Pastry Not required)
    insert into public.orders (
        id, order_number, restaurant_id, table_number, assigned_waiter_id, status, created_at, ready_at, acknowledged_at, updated_at
    ) values (
        v_order_27, 27, 'rest_demo_001', 15, v_waiter_id, 'acknowledged',
        now() - interval '9 minutes', now() - interval '2 minutes', now() - interval '1 minute', now()
    );

    insert into public.order_items (
        order_id, restaurant_id, menu_item_id, menu_label, quantity, station, status, ready_at
    ) values
        (v_order_27, 'rest_demo_001', 'grill_burger', 'Beef Burger', 2, 'grill', 'ready', now() - interval '3 minutes'),
        (v_order_27, 'rest_demo_001', 'fryer_onion_rings', 'Onion Rings', 2, 'fryer', 'ready', now() - interval '2 minutes');

    perform setval('public.order_display_number_seq', 28, true);
end;
$$;
