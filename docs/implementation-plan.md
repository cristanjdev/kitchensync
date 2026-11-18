# KitchenSync 360 implementation plan

Prepared 16 November 2026

## Objective and budget

Build a working Android presentation prototype that demonstrates the manuscript workflow: enter an order, coordinate readiness across Grill, Fryer, and Pastry, notify a waiter through a paired Wear OS watch, acknowledge the notification, and confirm pickup.

The PHP 3,500 budget is the development fee. The client supplies Android phones or tablets, a compatible Wear OS watch, internet access, and the cloud account. Device purchases, paid cloud usage, and store publication are separate expenses. This plan is a controlled presentation prototype; continuous restaurant operation is outside acceptance.

The central controller pictured in the manuscript becomes a dashboard mode in the Android app. Kitchen displays become station modes in the same app. The watch uses an actionable notification mirrored from the waiter phone. A separately installed watch app with custom screens is outside this version.

## Fixed deliverables

- One installable Android APK with Dashboard, Station, and Waiter modes.
- Three preset stations: Grill, Fryer, and Pastry.
- One demonstration restaurant and one assigned waiter.
- A small preset menu with each item mapped to one station.
- Manual order entry with table number, menu items, and quantities.
- Kitchen acceptance and item readiness controls.
- Server-controlled complete-order readiness.
- A ready notification on the waiter phone and paired watch.
- A watch Acknowledge action that writes back through the phone.
- A separate Picked Up action in the waiter phone app.
- A dashboard indicator for a ready order unacknowledged after 30 seconds.
- Supabase database, authentication, access policies, and realtime setup.
- Source code, setup instructions, demo accounts, and a presentation script.
- One minor revision round and seven calendar days of fixes for failures against the agreed acceptance checks.

The acknowledgment timeout is a demonstration default, configurable in code. It creates a visible indicator; automatic reassignment and repeated escalation are excluded.

## Devices and demonstration setup

Preferred full demonstration: one dashboard Android device, three station devices, one waiter Android phone, and a Wear OS watch paired to the waiter phone. The same APK runs on every phone or tablet.

A reduced-device demonstration can use one phone or tablet switching between Dashboard and Station views, plus a separate waiter phone paired to the watch. Explain that station switching represents the three physical stations. The first version does not include a separate desktop web dashboard; a laptop can display an Android emulator or a mirrored Android screen if needed.

Keep the waiter app open and its phone awake during the demonstration. All Android clients need internet access to Supabase; they do not have to share the same Wi-Fi. The watch must remain paired and connected to the waiter phone, with notification sharing enabled. Verify the watch settings allow notifications while the phone is being used.

## Technology

- Android: Kotlin and Jetpack Compose, using standard Material components.
- Backend: Supabase Free for a small demonstration dataset, within its current quotas.
- Authentication: preset email/password demo accounts; no registration or password-reset UI.
- Data updates: initial snapshot, realtime subscription, and a fresh snapshot after reconnection.
- Notifications: Android notification channel, runtime permission where required, and notification actions bridged to Wear OS.
- Distribution: signed APK installed directly on the agreed presentation devices.

Use a single app module and a small shared repository for data access. Avoid introducing a separate custom server, web frontend, or watch app. Pin dependency versions and include the lockfiles/version configuration used for delivery.

Supabase's Kotlin client is community-maintained. Verify its current authentication and realtime APIs during implementation. Convex is a viable alternative with an Android client, but select one backend before building; switching later is extra work.

## Screen behavior

### Dashboard

Show order cards grouped as Preparing, Ready, Acknowledged, and Picked Up. Each card shows order number, table number, and Grill/Fryer/Pastry progress. A station with no assigned items is labeled Not required and does not block completion.

Create orders from the preset menu. A quantity greater than one means the station's Ready action confirms the entire line quantity. Display that quantity prominently. Allow cancellation before pickup. Lock item editing after submission; correct an incorrect order by canceling it and creating a new one.

Show a red textual warning when an order has been ready for at least 30 seconds without acknowledgment. Use timestamps from the backend and calculate elapsed time consistently; no scheduled cloud task is needed for this visible indicator.

### Station

Choose Grill, Fryer, or Pastry. Show only that station's items, including order number, table number, item name, quantity, and state.

Use Pending -> Preparing -> Ready. Accept Order changes that station's pending items to Preparing. Mark Ready completes an individual item line. Do not allow reversing a Ready item in the first version. Canceled orders disappear from active work.

### Waiter

Show complete orders with table number, item summary, and current state. Provide Acknowledge followed by Picked Up. Acknowledging means the waiter received the message; pickup means the food was collected.

Show connection status. Display pending or failed writes honestly; do not show an acknowledgment or pickup as saved until the backend confirms it.

### Watch notification

Display KitchenSync 360, order number, table number, and Order ready. Include one Acknowledge action. Use the same order-specific notification identity for updates, so retries do not create duplicate cards.

The action is handled by the phone and submitted to Supabase. Update the notification to Acknowledged after the backend accepts the request. On a temporary failure, keep a retry path and do not falsely confirm success. Pickup remains a phone-app action to keep the watch interaction small.

## Data and access

Keep three main datasets:

1. Profiles: authenticated user identity, restaurant identity, and a fixed demo role.
2. Orders: identifier, display number, restaurant, table, assigned waiter, state, and creation/readiness/acknowledgment/pickup timestamps.
3. Order items: order, menu label, quantity, station, and preparation state.

The small menu and station list can be app configuration rather than extra management tables.

Provision a controller account, a kitchen account, and a waiter account. The shared kitchen account can select any of the three stations for the demo; this is a deliberate simplification, not individual employee permissions. Roles are configured by the backend administrator and cannot be promoted through a client role selector.

Enable row-level security on exposed tables and restrict access to the demonstration restaurant and the permitted role. Permit the waiter to acknowledge and collect only assigned orders. Keep state transitions behind narrowly scoped backend operations; clients must not be able to bypass transition checks by directly writing order status. The APK uses the publishable key and user session, never a privileged service key.

## State transitions and consistency

Order flow: Preparing -> Ready -> Acknowledged -> Picked Up. Cancellation is allowed before pickup and terminates further actions.

An order becomes Ready only when it contains at least one item and all its required item lines are Ready. The backend performs the item update and completion check atomically, serializing concurrent updates for the same order. Set ready_at once. This prevents the final two stations finishing simultaneously from leaving the order stuck or causing duplicate ready transitions.

Acknowledgment and pickup operations validate the current state and authenticated role. Repeating an already accepted action returns the existing result rather than generating another transition. Reject actions for canceled or incompatible order states.

Clients refetch on startup and reconnection instead of depending solely on events received while connected. The waiter app reconciles active ready orders against saved notification identities. A canceled or collected order removes its notification; an acknowledged order must not produce a fresh ready alert after reconnecting.

## Build sequence and effort target

These are estimates for a developer familiar with Android and Supabase. Allow roughly 18-24 focused hours, normally spread over 4-6 working days. At PHP 3,500 that is approximately PHP 146-194 per hour before expenses. If this is commercially unacceptable, reduce scope or revise the fee before promising delivery.

### Phase 1: Prove the phone/watch interaction

Timebox: 1-2 hours once the actual devices are available.

Create a small Android notification with an Acknowledge action. Verify display, vibration settings, and action delivery on the client's paired phone/watch. Verify the notification works while the phone app is open. Check permissions and the device's notification-sharing settings.

Exit condition: tapping Acknowledge on the watch demonstrably reaches the phone. If this fails within the timebox, resolve the device requirement or revise the watch scope before building around an unverified assumption. Do not silently replace the promised interaction with a mock animation.

### Phase 2: Backend and authentication

Target: 3-4 hours.

Create the client-owned Supabase project, demo accounts, datasets, access policies, state operations, and realtime configuration. Seed a few orders. Test that invalid transitions and unauthorized updates are rejected.

Exit condition: simulated updates from three stations produce exactly one ready order; repeated acknowledgment is safe.

### Phase 3: Android screens

Target: 6-8 hours.

Implement the preset-account sign-in flow, Dashboard, Station, and Waiter screens. Use green/dark styling inspired by the illustration, large controls, and clear status text. Add initial loading, reconnect handling, and create/cancel actions.

Exit condition: the complete order workflow runs across two Android devices using cloud data.

### Phase 4: Notifications and timeout indicator

Target: 3-4 hours.

Connect ready-order updates to Android notifications. Implement watch acknowledgment write-back, notification deduplication, pickup cleanup, and the 30-second dashboard warning.

Exit condition: kitchen update -> watch alert -> watch acknowledgment -> dashboard confirmation works with a real cloud order.

### Phase 5: Verification and handover

Target: 5-6 hours.

Run the acceptance scenarios below, fix failures, install the delivery APK on the agreed devices, document setup and cloud ownership, and rehearse the presentation. Record a successful walkthrough as a presentation backup.

## Acceptance scenarios

1. Create a table order containing one Grill, one Fryer, and one Pastry item; each station sees the correct line and quantity.
2. Completing one or two stations does not notify the waiter.
3. Completing the last item makes the order Ready and produces a watch notification. Target an end-to-end visible update within five seconds on the tested demo connection; measure this during rehearsal.
4. A one-station order becomes Ready without waiting for unused stations.
5. Watch acknowledgment updates the backend and dashboard; it does not mark pickup.
6. Pickup removes the order from the active waiter queue and clears its alert.
7. An unacknowledged ready order shows the timeout warning after 30 seconds; acknowledgment clears it.
8. Repeated taps, reconnects, and duplicate events do not create duplicate orders or state transitions.
9. Concurrent completion of the last two station items still produces the correct Ready state.
10. Cancellation suppresses further ready alerts and rejects stale watch actions.
11. Restarting the app preserves cloud order state. Reconnecting recovers missed changes and shows current state.
12. A denied notification permission produces a setup message rather than a misleading successful-alert indicator.
13. The waiter cannot perform kitchen or controller writes; demo users cannot access another restaurant's records.
14. Complete three simultaneous orders without mixing their table numbers, notifications, or acknowledgments.

## Presentation boundaries

The included notification path depends on the waiter app being active. Supabase realtime is not a replacement for operating-system push delivery. Notifications while the app is closed or suspended require additional work, normally a backend push path through Firebase Cloud Messaging; that is excluded here.

Also excluded: a standalone Wear OS app, background always-on tracking, offline synchronization, POS integration, payment processing, inventory, reports, multi-restaurant administration, multiple-waiter assignment, menu management, order modification workflows, automatic cooking-time scheduling, custom hardware, and Play Store publication.

The prototype demonstrates order readiness coordination. It does not claim to synchronize actual cooking durations or guarantee restaurant-wide service improvements.

## Presentation checklist

- Verify the actual phone/watch pairing, notification permissions, vibration, and phone-use notification settings.
- Charge devices and keep the waiter phone awake in the app.
- Check the Supabase project is active the day before and again before the pitch; free projects can pause after low activity over seven days.
- Confirm internet access and rehearse using the presentation connection or a tested hotspot.
- Prepare clean sample data and a three-item demonstration order.
- Rehearse both immediate acknowledgment and the 30-second timeout scenario.
- Keep the recorded successful walkthrough available if the venue's connection fails; identify it as a recording when used.

## Reference documentation

- Wear OS notification bridging and actions: https://developer.android.com/training/wearables/notifications
- Supabase Kotlin introduction: https://supabase.com/docs/reference/kotlin/introduction
- Supabase pricing: https://supabase.com/pricing
- Supabase free-project pausing: https://supabase.com/docs/guides/platform/free-project-pausing
- Separate push notification integration: https://supabase.com/docs/guides/functions/examples/push-notifications
- Convex Android alternative: https://docs.convex.dev/client/android/overview

## Suggested client scope statement

For PHP 3,500, delivery includes a KitchenSync 360 Android presentation prototype with a central dashboard, three preset kitchen stations, one waiter, Supabase cloud synchronization, and ready-order notifications with acknowledgment on one agreed compatible Wear OS phone/watch pair. Watch interaction uses notifications from the paired Android phone. The waiter app remains active during the demonstration. Delivery includes an APK, source code, setup notes, one demonstration, one minor revision round, and seven calendar days of fixes for agreed-scope defects. The client supplies devices, internet, and cloud-account ownership. A standalone watch application, background push delivery, integrations, and additional features require a separate agreement.
