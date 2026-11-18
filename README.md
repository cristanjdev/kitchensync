# KitchenSync 360

Android presentation prototype with a central controller dashboard, three preset kitchen stations (`Grill`, `Fryer`, `Pastry`), Supabase cloud synchronization, and Wear OS watch notification acknowledgment.

Development budget: PHP 3,500. Devices and internet are client-provided.

## Project Documents & Deliverables

- [Web dashboard (monitor-size, deploys to Vercel)](web-dashboard/README.md): an addition beyond the original plan, which excluded a web frontend. Agree its scope with the client.
- [Implementation plan](docs/implementation-plan.md)
- [Setup, Cloud Ownership & Wear OS Guide](docs/setup-and-cloud-guide.md)
- [Live Presentation Script & Walkthrough](docs/presentation-script.md)
- [Supabase Schema, RLS & Atomic RPC Migration](supabase/migrations/20261116000000_kitchensync360_schema.sql)
- [Supabase Demo Accounts & Seed Data](supabase/seed.sql)
- [Original manuscript](references/manuscript%20pitching%2001.docx)
- [Product illustration](references/product-overview.png)
- [Workflow illustration](references/workflow-reference.png)

## Verification status

See [acceptance status](docs/acceptance-status.md) for checks passed and outstanding cloud/device verification. Run the schema migration, then `supabase/upgrade-order-safety.sql`, then (for a fresh project) `supabase/seed.sql`. Each screen header shows whether the app is on Supabase Cloud or the single-device demo engine.

## Preset Demo Accounts

| Role | Email | Password | Mode |
| :--- | :--- | :--- | :--- |
| **Controller** | `controller@kitchensync360.demo` | *(demo password)* | Central Dashboard |
| **Kitchen** | `kitchen@kitchensync360.demo` | *(demo password)* | Grill, Fryer, and Pastry Stations |
| **Waiter** | `waiter@kitchensync360.demo` | *(demo password)* | Waiter Queue & Wear OS Watch Bridge |

## Building & Installing the APK

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease
```

Signed APKs ready for direct device installation:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`
