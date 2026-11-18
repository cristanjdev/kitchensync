# KitchenSync 360 — Setup, Cloud Ownership, and Wear OS Guide

## 1. Deliverables Overview

- **Android Application (`:app`)**: Single installable APK containing **Dashboard (Controller)**, **Station (Grill, Fryer, Pastry)**, and **Waiter** modes.
- **Supabase Backend SQL**:
  - Schema, RLS policies, atomic RPC functions, and Realtime configuration: [`supabase/migrations/20261116000000_kitchensync360_schema.sql`](../supabase/migrations/20261116000000_kitchensync360_schema.sql)
  - Demo accounts and sample orders seed script: [`supabase/seed.sql`](../supabase/seed.sql)
- **Presentation Script & Acceptance Checklist**: [`docs/presentation-script.md`](presentation-script.md)

---

## 2. Preset Demo Accounts

All accounts belong to demonstration restaurant `rest_demo_001` and share one demo password, chosen when you run `supabase/seed.sql`. It is not stored in this repo.

| Role | Email | Password | Assigned Mode |
| :--- | :--- | :--- | :--- |
| **Controller** | `controller@kitchensync360.demo` | *(demo password)* | Central Controller Dashboard |
| **Kitchen** | `kitchen@kitchensync360.demo` | *(demo password)* | Station Display (`Grill`, `Fryer`, `Pastry`) |
| **Waiter** | `waiter@kitchensync360.demo` | *(demo password)* | Waiter Queue & Wear OS Watch Notification Bridge |

> An additional isolated account (`other@anotherbistro.demo`, restaurant `rest_other_999`) is included in `supabase/seed.sql` to verify Row-Level Security (Acceptance Scenario #13).

---

## 3. Supabase Project Setup (Client Cloud Account)

1. Sign in to [https://supabase.com](https://supabase.com) and create a new **Free** project.
2. Open **SQL Editor** in the Supabase dashboard:
   - Paste and run [`supabase/migrations/20261116000000_kitchensync360_schema.sql`](../supabase/migrations/20261116000000_kitchensync360_schema.sql).
   - Paste and run [`supabase/upgrade-order-safety.sql`](../supabase/upgrade-order-safety.sql) to enable safe retries and strict station transitions.
   - Paste and run [`supabase/seed.sql`](../supabase/seed.sql).
3. Open **Project Settings -> API**:
   - Copy the **Project URL** (`https://<project-ref>.supabase.co`).
   - Copy the **anon / publishable** key (`eyJ...` or `sb_publishable_...`).
   - **Never** put the `service_role` secret key into the Android app.
4. Configure the Android app in either of two ways:
   - **Runtime (No rebuild required)**: Open the app, tap **Setup**, select **Supabase Cloud (Multi-Device Realtime)**, enter the Project URL and Publishable Key, and tap **Save Settings**.
   - **Build-time**: Set `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` in `local.properties` before running `./gradlew assembleDebug` or `./gradlew assembleRelease`.

> **Free Project Pausing Reminder**: Supabase Free projects can pause after 7 days of inactivity. Check the project dashboard the day before the pitch and again 1 hour before presenting.

---

## 4. Wear OS Phone & Watch Setup (Phase 1 Verification)

1. Pair the Wear OS watch with the Waiter Android phone using the watch's companion app (Pixel Watch, Galaxy Wearable, or Wear OS by Google).
2. In the companion app's **Notifications** settings:
   - Enable notifications for **KitchenSync 360**.
   - Enable **"Show notifications on watch while using phone"** (critical because the Waiter phone stays awake and open in the app during the pitch).
3. On the Waiter phone:
   - Launch **KitchenSync 360** and grant the **Allow Notifications** runtime permission when prompted.
   - Sign in as **Assigned Waiter** (`waiter@kitchensync360.demo`).
   - Tap **Test Watch** (or open **Setup -> Send Phase 1 Test Watch Alert**).
   - Confirm the watch vibrates and displays `KitchenSync 360 • Order #000` with `Table 1 • Order ready`.
   - Tap **Acknowledge** on the watch and verify the phone banner updates to `Verified: Watch Acknowledge received by phone`.

---

## 5. Installing the Signed APK

Build the signed APK with Gradle:

```powershell
.\gradlew.bat assembleDebug assembleRelease
```

Generated installable APK locations:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

Install onto connected presentation devices via ADB:

```powershell
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Updating an existing backend

Run `supabase/upgrade-order-safety.sql` in SQL Editor before installing the updated APK. It preserves existing orders. Do not re-run `seed.sql` on existing presentation data: it deletes and replaces the demo restaurant orders.

Order submissions use a persisted request ID. Retrying the same payload after an uncertain failure returns the original order. A successful response clears that identity so a later intentional identical order can be created.

The release build currently uses the Android debug signing key for direct prototype installation. Keep the same signing key for updates on the presentation devices.
