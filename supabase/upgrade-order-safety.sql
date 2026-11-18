-- Apply AFTER the original schema migration; safe to re-run.
-- No demo data is deleted. The updated app requires this upgrade.
begin;
alter table public.orders add column if not exists request_id uuid;
alter table public.orders add column if not exists created_by uuid references public.profiles(id);
alter table public.orders add column if not exists request_items jsonb;
create unique index if not exists orders_creation_request on public.orders(created_by, request_id);
drop function if exists public.create_order(integer, jsonb);
create or replace function public.create_order(
    p_table_number integer,
    p_items jsonb,
    p_request_id uuid
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

    if p_request_id is null then
        raise exception 'A request identity is required';
    end if;
    -- Serialize retries of this request, including simultaneous HTTP calls.
    perform pg_advisory_xact_lock(hashtextextended(v_profile.id::text || p_request_id::text, 0));
    select * into v_order from public.orders
    where created_by = v_profile.id and request_id = p_request_id;
    if found then
        if v_order.table_number <> p_table_number or v_order.request_items <> p_items then
            raise exception 'Request identity was already used for a different order';
        end if;
        return v_order;
    end if;

    -- Assign to the demonstration waiter in the same restaurant
    select id into v_waiter_id
    from public.profiles
    where restaurant_id = v_profile.restaurant_id
      and role = 'waiter'
    order by created_at asc
    limit 1;

    if v_waiter_id is null then
        raise exception 'Configure a waiter before creating orders';
    end if;
    insert into public.orders (
        request_id, created_by, request_items,
        restaurant_id,
        table_number,
        assigned_waiter_id,
        status,
        created_at,
        updated_at
    ) values (
        p_request_id, v_profile.id, p_items,
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

    if v_item.status = 'pending' then
        raise exception 'Accept the station order before marking items ready';
    end if;

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

    if v_order.assigned_waiter_id is distinct from v_profile.id then
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

    if v_order.assigned_waiter_id is distinct from v_profile.id then
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


grant select on public.profiles, public.orders, public.order_items to authenticated;
revoke all on function public.create_order(integer, jsonb, uuid) from public, anon;
grant execute on function public.create_order(integer, jsonb, uuid) to authenticated;
revoke all on function public.accept_station_order(uuid, text) from public, anon;
grant execute on function public.accept_station_order(uuid, text) to authenticated;
revoke all on function public.mark_item_ready(uuid) from public, anon;
grant execute on function public.mark_item_ready(uuid) to authenticated;
revoke all on function public.acknowledge_order(uuid) from public, anon;
grant execute on function public.acknowledge_order(uuid) to authenticated;
revoke all on function public.mark_order_picked_up(uuid) from public, anon;
grant execute on function public.mark_order_picked_up(uuid) to authenticated;
revoke all on function public.cancel_order(uuid) from public, anon;
grant execute on function public.cancel_order(uuid) to authenticated;
commit;
