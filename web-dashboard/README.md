# KitchenSync 360 — Web Dashboard

Monitor-size central dashboard for KitchenSync 360. It uses the same Supabase project, accounts, RLS policies and role-checked RPCs as the Android app. No backend changes are needed.

- Four live columns: Preparing, Ready, Acknowledged, Picked Up (last 12)
- Grill / Fryer / Pastry progress per order, with "Not required" for unused stations
- Red warning for ready orders unacknowledged for 30 seconds (`src/menu.ts`)
- Controller account: create orders (retry-safe) and cancel before pickup
- Kitchen or waiter accounts: view only
- Full-screen button for a wall monitor or TV

## Live updates (no refresh needed)

- Supabase Realtime subscription on `orders` and `order_items`; every change triggers a fresh, consistent snapshot. A burst from one action (for example, 1 order plus 3 items) is merged into one fetch.
- After reconnecting, coming back online, or the tab becoming visible again, the app takes a fresh snapshot so missed changes are recovered.
- Safety-net polling runs every 3 s while realtime is down and every 20 s while it is live.
- The header shows **Live / Reconnecting / Offline** and seconds since the last sync. If a sync fails, the last confirmed data stays on screen with a warning.

The Android app and the watch follow the same backend. The phone receives realtime events (plus a 3 s safety poll), and the waiter phone updates, replaces or removes the watch notification whenever its snapshot changes.

## Local development

```powershell
cd web-dashboard
copy .env.example .env.local   # then fill in the URL and publishable key
npm install
npm run dev                     # http://localhost:5173
npm run build                   # type-check + production build into dist/
```

## Backend check

After applying the schema, `upgrade-order-safety.sql` and `seed.sql`:

```powershell
npm run check:realtime
```

This signs in as all demo roles, subscribes to realtime like the dashboard, and runs a full order through it, printing PASS/FAIL and realtime delays. Checks include a three-station order, a concurrent final completion, acknowledge and pickup, a one-station order, cancellation with a stale acknowledgment, and role/restaurant isolation. It leaves test orders on table 99 as picked up or canceled.

## Deploy to Vercel

1. Import the repository in Vercel and set **Root Directory** to `web-dashboard`. The Vite preset is detected: build command `npm run build`, output `dist`.
2. Add environment variables (Production and Preview):
   - `VITE_SUPABASE_URL` = `https://<project-ref>.supabase.co`
   - `VITE_SUPABASE_PUBLISHABLE_KEY` = `sb_publishable_...`
3. Deploy, open the URL on the monitor's browser, sign in as `controller@kitchensync360.com`, and press **Full screen**.

Or use the CLI from this folder: `npx vercel` (preview) / `npx vercel --prod`.

Never use the `service_role` / secret key here. These values are bundled into public JavaScript; the publishable key is designed for that, and access is enforced by RLS and RPC role checks.
