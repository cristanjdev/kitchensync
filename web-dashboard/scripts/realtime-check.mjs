// End-to-end backend + realtime check for KitchenSync 360.
// Usage (from web-dashboard/):  npm run check:realtime
// Requires schema, upgrade-order-safety.sql and seed.sql applied, and .env.local filled in.
// Creates test orders on table 99 (left as picked up / canceled so they don't clutter active work).
import { readFileSync } from 'node:fs';
import { createClient } from '@supabase/supabase-js';

const env = Object.fromEntries(
  readFileSync(new URL('../.env.local', import.meta.url), 'utf8')
    .split(/\r?\n/)
    .filter((line) => line.includes('=') && !line.trim().startsWith('#'))
    .map((line) => [line.slice(0, line.indexOf('=')).trim(), line.slice(line.indexOf('=') + 1).trim()]),
);
const URL_ = env.VITE_SUPABASE_URL;
const KEY = env.VITE_SUPABASE_PUBLISHABLE_KEY;
const PASSWORD = process.env.KS360_DEMO_PASSWORD ?? env.VITE_DEMO_PASSWORD;
if (!PASSWORD) {
  console.error('Set VITE_DEMO_PASSWORD in web-dashboard/.env.local (or KS360_DEMO_PASSWORD in the environment).');
  process.exit(1);
}
const TEST_TABLE = 99;

let failures = 0;
const ok = (msg) => console.log(`  PASS  ${msg}`);
const fail = (msg) => {
  failures++;
  console.log(`  FAIL  ${msg}`);
};
const check = (cond, msg) => (cond ? ok(msg) : fail(msg));

function client() {
  return createClient(URL_, KEY, { auth: { persistSession: false, autoRefreshToken: false } });
}

async function signIn(email) {
  const c = client();
  const { error } = await c.auth.signInWithPassword({ email, password: PASSWORD });
  if (error) throw new Error(`Sign-in failed for ${email}: ${error.message}`);
  return c;
}

const rpc = async (c, fn, args) => {
  const { data, error } = await c.rpc(fn, args);
  if (error) throw new Error(`${fn}: ${error.message}`);
  return data;
};

const getOrder = async (c, id) => {
  const { data, error } = await c.from('orders').select('*, order_items(*)').eq('id', id).single();
  if (error) throw new Error(error.message);
  return data;
};

async function main() {
  console.log(`Project: ${URL_}`);
  console.log('\n1. Demo account sign-in');
  const controller = await signIn('controller@kitchensync360.demo');
  const kitchen = await signIn('kitchen@kitchensync360.demo');
  const waiter = await signIn('waiter@kitchensync360.demo');
  ok('controller, kitchen and waiter signed in');

  console.log('\n2. Realtime subscription (as the dashboard)');
  const events = []; // { at, table, status, id }
  const waiters = [];
  const channel = controller
    .channel('ks360-realtime-check')
    .on('postgres_changes', { event: '*', schema: 'public', table: 'orders' }, (p) => {
      const evt = { at: Date.now(), table: 'orders', status: p.new?.status, id: p.new?.id };
      events.push(evt);
      for (const w of [...waiters]) if (w.match(evt)) { waiters.splice(waiters.indexOf(w), 1); w.resolve(evt); }
    })
    .on('postgres_changes', { event: '*', schema: 'public', table: 'order_items' }, (p) => {
      events.push({ at: Date.now(), table: 'order_items', status: p.new?.status, id: p.new?.order_id });
    });
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Realtime did not subscribe within 15s')), 15_000);
    channel.subscribe((state, err) => {
      if (state === 'SUBSCRIBED') { clearTimeout(timer); resolve(); }
      if (state === 'CHANNEL_ERROR' || state === 'TIMED_OUT') { clearTimeout(timer); reject(new Error(`Realtime ${state}: ${err?.message ?? ''}`)); }
    });
  });
  ok('subscribed to orders and order_items changes');
  // Give the server a moment to attach the postgres_changes listeners.
  await new Promise((r) => setTimeout(r, 1500));

  const waitForOrderEvent = (id, status, timeoutMs = 10_000) =>
    new Promise((resolve) => {
      const existing = events.find((e) => e.table === 'orders' && e.id === id && e.status === status);
      if (existing) return resolve(existing);
      const w = { match: (e) => e.table === 'orders' && e.id === id && e.status === status, resolve };
      waiters.push(w);
      setTimeout(() => { const i = waiters.indexOf(w); if (i >= 0) { waiters.splice(i, 1); resolve(null); } }, timeoutMs);
    });

  console.log('\n3. Three-station order (scenarios 1, 2, 3, 8, 9)');
  const requestId = crypto.randomUUID();
  const items = [
    { menu_item_id: 'grill_chicken', menu_label: 'Grilled Chicken', quantity: 1, station: 'grill' },
    { menu_item_id: 'fryer_fries', menu_label: 'French Fries', quantity: 2, station: 'fryer' },
    { menu_item_id: 'pastry_choco_cake', menu_label: 'Chocolate Cake', quantity: 1, station: 'pastry' },
  ];
  const createdAt = Date.now();
  const created = await rpc(controller, 'create_order', { p_table_number: TEST_TABLE, p_items: items, p_request_id: requestId });
  const retried = await rpc(controller, 'create_order', { p_table_number: TEST_TABLE, p_items: items, p_request_id: requestId });
  check(retried.id === created.id, 'retrying the same request returns the same order (no duplicate)');
  const insertEvt = await waitForOrderEvent(created.id, 'preparing');
  check(!!insertEvt, `dashboard received the new order via realtime${insertEvt ? ` in ${insertEvt.at - createdAt} ms` : ''}`);

  let order = await getOrder(kitchen, created.id);
  const byStation = Object.fromEntries(order.order_items.map((i) => [i.station, i]));
  check(order.order_items.length === 3 && byStation.fryer?.quantity === 2, 'each station has its line with the right quantity');

  for (const station of ['grill', 'fryer', 'pastry']) await rpc(kitchen, 'accept_station_order', { p_order_id: created.id, p_station: station });
  await rpc(kitchen, 'mark_item_ready', { p_item_id: byStation.grill.id });
  order = await getOrder(controller, created.id);
  check(order.status === 'preparing', 'one station ready: order still preparing (no waiter alert)');

  const readyStart = Date.now();
  await Promise.all([
    rpc(kitchen, 'mark_item_ready', { p_item_id: byStation.fryer.id }),
    rpc(kitchen, 'mark_item_ready', { p_item_id: byStation.pastry.id }),
  ]);
  order = await getOrder(controller, created.id);
  check(order.status === 'ready' && !!order.ready_at, 'concurrent final two stations: order is ready, ready_at set once');
  const readyEvt = await waitForOrderEvent(created.id, 'ready');
  check(!!readyEvt, `ready status pushed via realtime${readyEvt ? ` in ${readyEvt.at - readyStart} ms` : ''}`);
  check(order.order_items.every((i) => i.status === 'ready'), 'all three lines are ready (no line lost to the race)');
  const replay = await rpc(kitchen, 'mark_item_ready', { p_item_id: byStation.pastry.id });
  check(replay.status === 'ready' && replay.ready_at === order.ready_at, 'repeating Mark Ready keeps the original ready_at (no second transition)');

  console.log('\n4. Acknowledge and pickup (scenarios 5, 6, 8)');
  const ackStart = Date.now();
  const ack1 = await rpc(waiter, 'acknowledge_order', { p_order_id: created.id });
  const ack2 = await rpc(waiter, 'acknowledge_order', { p_order_id: created.id });
  check(ack1.status === 'acknowledged' && ack2.acknowledged_at === ack1.acknowledged_at, 'repeated acknowledgment is idempotent');
  const ackEvt = await waitForOrderEvent(created.id, 'acknowledged');
  check(!!ackEvt, `acknowledgment pushed to dashboard${ackEvt ? ` in ${ackEvt.at - ackStart} ms` : ''}`);
  check(ack1.picked_up_at === null, 'acknowledgment does not mark pickup');
  const picked = await rpc(waiter, 'mark_order_picked_up', { p_order_id: created.id });
  check(picked.status === 'picked_up', 'pickup saved');
  check(!!(await waitForOrderEvent(created.id, 'picked_up')), 'pickup pushed to dashboard');

  console.log('\n5. One-station order (scenario 4)');
  const single = await rpc(controller, 'create_order', {
    p_table_number: TEST_TABLE,
    p_items: [{ menu_item_id: 'grill_burger', menu_label: 'Beef Burger', quantity: 2, station: 'grill' }],
    p_request_id: crypto.randomUUID(),
  });
  await rpc(kitchen, 'accept_station_order', { p_order_id: single.id, p_station: 'grill' });
  const singleItems = (await getOrder(kitchen, single.id)).order_items;
  const singleReady = await rpc(kitchen, 'mark_item_ready', { p_item_id: singleItems[0].id });
  check(singleReady.status === 'ready', 'grill-only order becomes ready without unused stations');

  console.log('\n6. Cancellation and stale actions (scenario 10)');
  await rpc(controller, 'cancel_order', { p_order_id: single.id });
  check(!!(await waitForOrderEvent(single.id, 'canceled')), 'cancellation pushed to dashboard');
  const staleAck = await waiter.rpc('acknowledge_order', { p_order_id: single.id });
  check(!!staleAck.error, `stale acknowledgment rejected (${staleAck.error?.message ?? 'accepted!'})`);

  console.log('\n7. Role and restaurant isolation (scenario 13)');
  const waiterCreate = await waiter.rpc('create_order', { p_table_number: TEST_TABLE, p_items: items, p_request_id: crypto.randomUUID() });
  check(!!waiterCreate.error, 'waiter cannot create orders');
  const kitchenAck = await kitchen.rpc('acknowledge_order', { p_order_id: created.id });
  check(!!kitchenAck.error, 'kitchen cannot acknowledge');
  const directWrite = await controller.from('orders').update({ status: 'ready' }).eq('id', created.id).select();
  check(!!directWrite.error || (directWrite.data ?? []).length === 0, 'direct status writes are blocked');
  try {
    const other = await signIn('other@anotherbistro.demo');
    const { data } = await other.from('orders').select('id');
    check((data ?? []).length === 0, 'other restaurant sees none of the demo orders');
  } catch (e) {
    fail(`other-restaurant account: ${e.message}`);
  }

  await controller.removeChannel(channel);
  console.log(`\n${failures === 0 ? 'ALL CHECKS PASSED' : `${failures} CHECK(S) FAILED`}`);
  process.exit(failures === 0 ? 0 : 1);
}

main().catch((error) => {
  console.error(`\nABORTED: ${error.message}`);
  process.exit(1);
});
