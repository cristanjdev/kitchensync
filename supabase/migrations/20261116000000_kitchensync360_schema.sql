-- KitchenSync 360 - Supabase Schema, RLS Policies, Atomic RPCs, and Realtime Setup
-- Matches docs/implementation-plan.md specifications

create extension if not exists "pgcrypto";

-- ============================================================================
-- 1. TABLES
-- ============================================================================

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    email text not null,
    restaurant_id text not null default 'rest_demo_001',
    role text not null check (role in ('controller', 'kitchen', 'waiter')),
    display_name text not null,
    created_at timestamptz not null default now()
);

create sequence if not exists public.order_display_number_seq start with 25 increment by 1;

create table if not exists public.orders (
    id uuid primary key default gen_random_uuid(),
    order_number integer not null default nextval('public.order_display_number_seq'),
    restaurant_id text not null default 'rest_demo_001',
    table_number integer not null check (table_number > 0 and table_number <= 999),
    assigned_waiter_id uuid references public.profiles(id),
    status text not null default 'preparing' check (status in ('preparing', 'ready', 'acknowledged', 'picked_up', 'canceled')),
    created_at timestamptz not null default now(),
    ready_at timestamptz,
    acknowledged_at timestamptz,
    picked_up_at timestamptz,
    canceled_at timestamptz,
    updated_at timestamptz not null default now()
);

create index if not exists idx_orders_restaurant_status on public.orders(restaurant_id, status);
create index if not exists idx_orders_created_at on public.orders(created_at desc);

create table if not exists public.order_items (
    id uuid primary key default gen_random_uuid(),
    order_id uuid not null references public.orders(id) on delete cascade,
    restaurant_id text not null default 'rest_demo_001',
    menu_item_id text not null,
    menu_label text not null,
    quantity integer not null check (quantity > 0 and quantity <= 99),
    station text not null check (station in ('grill', 'fryer', 'pastry')),
    status text not null default 'pending' check (status in ('pending', 'preparing', 'ready')),
    created_at timestamptz not null default now(),
    ready_at timestamptz,
    updated_at timestamptz not null default now()
);

create index if not exists idx_order_items_order_id on public.order_items(order_id);
create index if not exists idx_order_items_restaurant_station on public.order_items(restaurant_id, station, status);

-- ============================================================================
-- 2. ROW LEVEL SECURITY (RLS)
-- ============================================================================

alter table public.profiles enable row level security;
alter table public.orders enable row level security;
alter table public.order_items enable row level security;

-- Helper function to retrieve caller's restaurant_id
create or replace function public.current_user_restaurant_id()
returns text
language sql
stable
security definer
set search_path = public
as $$
    select restaurant_id from public.profiles where id = auth.uid();
$$;

-- Helper function to retrieve caller's role
create or replace function public.current_user_role()
returns text
language sql
stable
security definer
set search_path = public
as $$
    select role from public.profiles where id = auth.uid();
$$;

drop policy if exists "Users can view profiles in their restaurant" on public.profiles;
create policy "Users can view profiles in their restaurant"
    on public.profiles
    for select
    to authenticated
    using (restaurant_id = public.current_user_restaurant_id());

drop policy if exists "Users can view orders in their restaurant" on public.orders;
create policy "Users can view orders in their restaurant"
    on public.orders
    for select
    to authenticated
    using (restaurant_id = public.current_user_restaurant_id());

drop policy if exists "Users can view order items in their restaurant" on public.order_items;
create policy "Users can view order items in their restaurant"
    on public.order_items
    for select
    to authenticated
    using (restaurant_id = public.current_user_restaurant_id());

-- Note: Direct INSERT/UPDATE/DELETE policies are intentionally omitted.
-- Clients must invoke the narrowly scoped SECURITY DEFINER RPC functions below,
-- preventing direct status tampering or cross-role writes.

revoke insert, update, delete on public.profiles from anon, authenticated;
revoke insert, update, delete on public.orders from anon, authenticated;
revoke insert, update, delete on public.order_items from anon, authenticated;

-- ============================================================================
-- 3. NARROWLY SCOPED ATOMIC BACKEND OPERATIONS (RPCs)
-- ============================================================================

-- 3.1 Create Order (Controller only)
-- p_items JSONB format: [{"menu_item_id": "grill_chicken", "menu_label": "Grilled Chicken", "quantity": 1, "station": "grill"}, ...]
create or replace function public.create_order(
    p_table_number integer,
    p_items jsonb
)
returns public.orders
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile public.profiles;
    v_waiter_id uuid;
    v_order public.orders;
    v_item jsonb;
    v_item_count integer;
begin
    select * into v_profile from public.profiles where id = auth.uid();
    if not found then
        raise exception 'Unauthorized: profile not found for current user';
    end if;

    if v_profile.role <> 'controller' then
        raise exception 'Forbidden: only controller role can create orders (current role: %)', v_profile.role;
    end if;

    if p_table_number is null or p_table_number <= 0 then
        raise exception 'Invalid table number: must be greater than 0';
    end if;

    if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
        raise exception 'Invalid order: must contain at least one menu item';
    end if;

    -- Assign to the demonstration waiter in the same restaurant
    select id into v_waiter_id
    from public.profiles
    where restaurant_id = v_profile.restaurant_id
      and role = 'waiter'
    order by created_at asc
    limit 1;

    insert into public.orders (
        restaurant_id,
        table_number,
        assigned_waiter_id,
        status,
        created_at,
        updated_at
    ) values (
        v_profile.restaurant_id,
        p_table_number,
        v_waiter_id,
        'preparing',
        now(),
        now()
    )
    returning * into v_order;

    v_item_count := 0;
    for v_item in select * from jsonb_array_elements(p_items)
    loop
        if coalesce((v_item->>'quantity')::integer, 0) <= 0 then
            raise exception 'Invalid item quantity: must be greater than 0';
        end if;

        if coalesce(v_item->>'station', '') not in ('grill', 'fryer', 'pastry') then
            raise exception 'Invalid station: %', coalesce(v_item->>'station', '');
        end if;

        insert into public.order_items (
            order_id,
            restaurant_id,
            menu_item_id,
            menu_label,
            quantity,
            station,
            status,
            created_at,
            updated_at
        ) values (
            v_order.id,
            v_profile.restaurant_id,
            coalesce(v_item->>'menu_item_id', 'custom'),
            coalesce(v_item->>'menu_label', 'Menu Item'),
            (v_item->>'quantity')::integer,
            v_item->>'station',
            'pending',
            now(),
            now()
        );
        v_item_count := v_item_count + 1;
    end loop;

    if v_item_count = 0 then
        raise exception 'Order must contain at least one valid item';
    end if;

    return v_order;
end;
$$;

-- 3.2 Accept Station Order (Kitchen only)
-- Transitions all 'pending' items for (p_order_id, p_station) to 'preparing'. Idempotent.
create or replace function public.accept_station_order(
    p_order_id uuid,
    p_station text
)
returns setof public.order_items
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile public.profiles;
    v_order public.orders;
begin
    select * into v_profile from public.profiles where id = auth.uid();
    if not found then
        raise exception 'Unauthorized: profile not found';
    end if;

    if v_profile.role <> 'kitchen' then
        raise exception 'Forbidden: only kitchen role can accept station items';
    end if;

    if p_station not in ('grill', 'fryer', 'pastry') then
        raise exception 'Invalid station: %', p_station;
    end if;

    -- Lock the parent order row to serialize concurrent mutations
    select * into v_order
    from public.orders
    where id = p_order_id
      and restaurant_id = v_profile.restaurant_id
    for update;

    if not found then
        raise exception 'Order not found';
    end if;

    if v_order.status = 'canceled' then
        raise exception 'Cannot accept items for a canceled order';
    end if;

    if v_order.status <> 'preparing' then
        -- Already ready/acknowledged/picked_up; return current station items idempotently
        return query
            select * from public.order_items
            where order_id = p_order_id and station = p_station
            order by created_at asc;
        return;
    end if;

    update public.order_items
    set status = 'preparing',
        updated_at = now()
    where order_id = p_order_id
      and restaurant_id = v_profile.restaurant_id
      and station = p_station
      and status = 'pending';

    update public.orders
    set updated_at = now()
    where id = p_order_id;

    return query
        select * from public.order_items
        where order_id = p_order_id and station = p_station
        order by created_at asc;
end;
$$;

-- 3.3 Mark Item Ready (Kitchen only)
-- Atomically marks a single item line 'ready' and checks whether ALL items of the order are now 'ready'.
-- Serializes concurrent station completions via FOR UPDATE on public.orders.
create or replace function public.mark_item_ready(
    p_item_id uuid
)
returns public.orders
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile public.profiles;
    v_item public.order_items;
    v_order public.orders;
    v_total_items integer;
    v_unready_items integer;
begin
    select * into v_profile from public.profiles where id = auth.uid();
    if not found then
        raise exception 'Unauthorized: profile not found';
    end if;

    if v_profile.role <> 'kitchen' then
        raise exception 'Forbidden: only kitchen role can mark items ready';
    end if;

    select * into v_item
    from public.order_items
    where id = p_item_id
      and restaurant_id = v_profile.restaurant_id;

    if not found then
        raise exception 'Order item not found';
    end if;

    -- Lock the parent order FIRST to serialize concurrent station completions on the same order
    select * into v_order
    from public.orders
    where id = v_item.order_id
      and restaurant_id = v_profile.restaurant_id
    for update;

    if not found then
        raise exception 'Parent order not found';
    end if;

    if v_order.status = 'canceled' then
        raise exception 'Cannot mark item ready on a canceled order';
    end if;

    -- Re-read item under order lock
    select * into v_item
    from public.order_items
    where id = p_item_id
    for update;

    -- Idempotent update: if not yet ready, transition to ready
    if v_item.status <> 'ready' then
        update public.order_items
        set status = 'ready',
            ready_at = coalesce(ready_at, now()),
            updated_at = now()
        where id = p_item_id;
    end if;

    -- Check if the order has at least one item and all items are ready
    select count(*), count(*) filter (where status <> 'ready')
    into v_total_items, v_unready_items
    from public.order_items
    where order_id = v_order.id;

    if v_total_items > 0 and v_unready_items = 0 and v_order.status = 'preparing' then
        update public.orders
        set status = 'ready',
            ready_at = coalesce(ready_at, now()),
            updated_at = now()
        where id = v_order.id
        returning * into v_order;
    else
        update public.orders
        set updated_at = now()
        where id = v_order.id
        returning * into v_order;
    end if;

    return v_order;
end;
$$;

-- 3.4 Acknowledge Order (Waiter only, assigned orders only)
-- Idempotent when already 'acknowledged'. Rejects 'preparing', 'picked_up', or 'canceled'.
create or replace function public.acknowledge_order(
    p_order_id uuid
)
returns public.orders
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile public.profiles;
    v_order public.orders;
begin
    select * into v_profile from public.profiles where id = auth.uid();
    if not found then
        raise exception 'Unauthorized: profile not found';
    end if;

    if v_profile.role <> 'waiter' then
        raise exception 'Forbidden: only waiter role can acknowledge orders';
    end if;

    select * into v_order
    from public.orders
    where id = p_order_id
      and restaurant_id = v_profile.restaurant_id
    for update;

    if not found then
        raise exception 'Order not found';
    end if;

    if v_order.assigned_waiter_id is not null and v_order.assigned_waiter_id <> v_profile.id then
        raise exception 'Forbidden: order is assigned to another waiter';
    end if;

    if v_order.status = 'canceled' then
        raise exception 'Cannot acknowledge a canceled order';
    end if;

    -- Idempotent: repeating an already accepted acknowledgment returns the existing result
    if v_order.status = 'acknowledged' then
        return v_order;
    end if;

    if v_order.status <> 'ready' then
        raise exception 'Incompatible order state for acknowledgment: %', v_order.status;
    end if;

    update public.orders
    set status = 'acknowledged',
        acknowledged_at = coalesce(acknowledged_at, now()),
        updated_at = now()
    where id = p_order_id
    returning * into v_order;

    return v_order;
end;
$$;

-- 3.5 Mark Order Picked Up (Waiter only, assigned orders only)
-- Requires order to be 'acknowledged'. Idempotent if already 'picked_up'.
create or replace function public.mark_order_picked_up(
    p_order_id uuid
)
returns public.orders
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile public.profiles;
    v_order public.orders;
begin
    select * into v_profile from public.profiles where id = auth.uid();
    if not found then
        raise exception 'Unauthorized: profile not found';
    end if;

    if v_profile.role <> 'waiter' then
        raise exception 'Forbidden: only waiter role can confirm order pickup';
    end if;

    select * into v_order
    from public.orders
    where id = p_order_id
      and restaurant_id = v_profile.restaurant_id
    for update;

    if not found then
        raise exception 'Order not found';
    end if;

    if v_order.assigned_waiter_id is not null and v_order.assigned_waiter_id <> v_profile.id then
        raise exception 'Forbidden: order is assigned to another waiter';
    end if;

    if v_order.status = 'canceled' then
        raise exception 'Cannot pick up a canceled order';
    end if;

    -- Idempotent: repeating pickup returns the existing result
    if v_order.status = 'picked_up' then
        return v_order;
    end if;

    if v_order.status <> 'acknowledged' then
        raise exception 'Order must be acknowledged before pickup (current state: %)', v_order.status;
    end if;

    update public.orders
    set status = 'picked_up',
        picked_up_at = coalesce(picked_up_at, now()),
        updated_at = now()
    where id = p_order_id
    returning * into v_order;

    return v_order;
end;
$$;

-- 3.6 Cancel Order (Controller only, allowed before pickup)
-- Idempotent if already 'canceled'. Rejects if 'picked_up'.
create or replace function public.cancel_order(
    p_order_id uuid
)
returns public.orders
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile public.profiles;
    v_order public.orders;
begin
    select * into v_profile from public.profiles where id = auth.uid();
    if not found then
        raise exception 'Unauthorized: profile not found';
    end if;

    if v_profile.role <> 'controller' then
        raise exception 'Forbidden: only controller role can cancel orders';
    end if;

    select * into v_order
    from public.orders
    where id = p_order_id
      and restaurant_id = v_profile.restaurant_id
    for update;

    if not found then
        raise exception 'Order not found';
    end if;

    if v_order.status = 'canceled' then
        return v_order;
    end if;

    if v_order.status = 'picked_up' then
        raise exception 'Cannot cancel an order that has already been picked up';
    end if;

    update public.orders
    set status = 'canceled',
        canceled_at = coalesce(canceled_at, now()),
        updated_at = now()
    where id = p_order_id
    returning * into v_order;

    return v_order;
end;
$$;

grant execute on function public.create_order(integer, jsonb) to authenticated;
grant execute on function public.accept_station_order(uuid, text) to authenticated;
grant execute on function public.mark_item_ready(uuid) to authenticated;
grant execute on function public.acknowledge_order(uuid) to authenticated;
grant execute on function public.mark_order_picked_up(uuid) to authenticated;
grant execute on function public.cancel_order(uuid) to authenticated;

-- ============================================================================
-- 4. REALTIME CONFIGURATION
-- ============================================================================

alter table public.orders replica identity full;
alter table public.order_items replica identity full;

do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'orders'
    ) then
        alter publication supabase_realtime add table public.orders;
    end if;

    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'order_items'
    ) then
        alter publication supabase_realtime add table public.order_items;
    end if;
end;
$$;
