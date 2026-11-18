// Rehearsal helper: plays the controller and kitchen roles from a terminal, so one phone
// (waiter + watch) and the web dashboard can be tested without extra devices.
//
//   npm run demo -- create [table]            create a 3-station order (default table 12)
//   npm run demo -- create-grill [table]      create a Grill-only order (2x Beef Burger)
//   npm run demo -- ready <order#> <station>  accept + mark ready the station's lines (grill|fryer|pastry|all)
//   npm run demo -- status [order#]           show active orders, or one order
//   npm run demo -- cancel <order#>           cancel an order
import { readFileSync } from 'node:fs';
import { createClient } from '@supabase/supabase-js';

const env = Object.fromEntries(
  readFileSync(new URL('../.env.local', import.meta.url), 'utf8')
    .split(/\r?\n/)
    .filter((l) => l.includes('=') && !l.trim().startsWith('#'))
    .map((l) => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]),
);
const PASSWORD = process.env.KS360_DEMO_PASSWORD ?? env.VITE_DEMO_PASSWORD;
if (!PASSWORD) {
  console.error('Set VITE_DEMO_PASSWORD in web-dashboard/.env.local (or KS360_DEMO_PASSWORD in the environment).');
  process.exit(1);
}

async function as(role) {
  const c = createClient(env.VITE_SUPABASE_URL, env.VITE_SUPABASE_PUBLISHABLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { error } = await c.auth.signInWithPassword({ email: `${role}@kitchensync360.demo`, password: PASSWORD });
  if (error) throw new Error(`${role} sign-in failed: ${error.message}`);
  return c;
}

const must = ({ data, error }, what) => {
  if (error) throw new Error(`${what}: ${error.message}`);
  return data;
};

async function findOrder(c, number) {
  const rows = must(
    await c.from('orders').select('*, order_items(*)').eq('order_number', Number(number)).limit(1),
    'lookup',
  );
  if (!rows.length) throw new Error(`Order #${number} not found`);
  return rows[0];
}

function show(o) {
  const pad = (n) => String(n).padStart(3, '0');
  const lines = o.order_items
    .map((i) => `      ${i.quantity}x ${i.menu_label} [${i.station}] ${i.status}`)
    .join('\n');
  console.log(`  #${pad(o.order_number)}  table ${o.table_number}  ${o.status.toUpperCase()}\n${lines}`);
}

const [cmd, a, b] = process.argv.slice(2);
try {
  if (cmd === 'create' || cmd === 'create-grill') {
    const controller = await as('controller');
    const items =
      cmd === 'create'
        ? [
            { menu_item_id: 'grill_chicken', menu_label: 'Grilled Chicken', quantity: 1, station: 'grill' },
            { menu_item_id: 'fryer_fries', menu_label: 'French Fries', quantity: 2, station: 'fryer' },
            { menu_item_id: 'pastry_choco_cake', menu_label: 'Chocolate Cake', quantity: 1, station: 'pastry' },
          ]
        : [{ menu_item_id: 'grill_burger', menu_label: 'Beef Burger', quantity: 2, station: 'grill' }];
    const order = must(
      await controller.rpc('create_order', {
        p_table_number: Number(a ?? 12),
        p_items: items,
        p_request_id: crypto.randomUUID(),
      }),
      'create_order',
    );
    console.log('Created:');
    show(await findOrder(controller, order.order_number));
  } else if (cmd === 'ready') {
    if (!a || !b) throw new Error('Usage: ready <order#> <grill|fryer|pastry|all>');
    const kitchen = await as('kitchen');
    const order = await findOrder(kitchen, a);
    const stations = b === 'all' ? ['grill', 'fryer', 'pastry'] : [b];
    for (const station of stations) {
      const lines = order.order_items.filter((i) => i.station === station);
      if (!lines.length) {
        console.log(`  ${station}: not required`);
        continue;
      }
      must(await kitchen.rpc('accept_station_order', { p_order_id: order.id, p_station: station }), 'accept');
      for (const line of lines) must(await kitchen.rpc('mark_item_ready', { p_item_id: line.id }), 'mark_item_ready');
      console.log(`  ${station}: accepted and marked ready`);
    }
    show(await findOrder(kitchen, a));
  } else if (cmd === 'cancel') {
    const controller = await as('controller');
    const order = await findOrder(controller, a);
    must(await controller.rpc('cancel_order', { p_order_id: order.id }), 'cancel_order');
    show(await findOrder(controller, a));
  } else if (cmd === 'status') {
    const c = await as('controller');
    if (a) show(await findOrder(c, a));
    else {
      const rows = must(
        await c
          .from('orders')
          .select('*, order_items(*)')
          .in('status', ['preparing', 'ready', 'acknowledged'])
          .order('order_number'),
        'status',
      );
      if (!rows.length) console.log('  No active orders.');
      rows.forEach(show);
    }
  } else {
    console.log(readFileSync(new URL(import.meta.url), 'utf8').split('\n').slice(0, 9).join('\n'));
  }
} catch (error) {
  console.error(`Error: ${error.message}`);
  process.exit(1);
}
