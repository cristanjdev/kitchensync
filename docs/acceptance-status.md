# Implementation and acceptance status

Reviewed 16 November 2026; updated 17 November 2026. Implementation is present; full acceptance is pending.

## Changes from the review

- Order submissions have an atomic in-flight guard and a persisted request identity. The backend serializes duplicate requests and returns the existing order, rejecting reuse for a different payload.
- Cloud sessions persist refresh tokens and expiry, serialize token rotation across requests, and send the updated token to realtime.
- Realtime connection callbacks wait for the join reply and handle subscription errors.
- Pending station lines require acceptance before readiness in cloud SQL, local demo logic, and station controls.
- Waiter acknowledgment and pickup require an exact assigned-waiter match in the upgrade script.
- Setup instructions include the required `supabase/upgrade-order-safety.sql` upgrade. The original schema migration is preserved.

## Fixes from the 17 November review

- Backend timestamps (`+00:00` offsets from PostgREST) are parsed with `OffsetDateTime`. `Instant.parse` rejects them on Android 8-13, which kept the 30-second warning at 0 seconds in cloud mode.
- Saving Setup with a blank key keeps the existing publishable key instead of silently switching to the on-device engine.
- Waiter notifications use the signed-in waiter's ID, so accounts created in the Supabase dashboard receive alerts. Local single-device rehearsal still acknowledges as the demo waiter.
- Overlapping snapshot requests (poll, realtime, post-write) apply only the newest result, preventing brief state rollbacks.
- Every screen header shows the active backend and sync state; on-device mode is labelled "this device only, not synced".
- The timeout reads `UNACKNOWLEDGED_TIMEOUT_SECONDS` from `app/build.gradle.kts`.
- `seed.sql` sets the auth token columns to empty strings and creates `auth.identities` rows, so seeded accounts can sign in with a password. Re-running it repairs accounts from earlier runs.
- `CloudSessionTest` uses a `java.net` stub server, so `testDebugUnitTest` compiles under Gradle.

## Verification

On 17 November 2026, `gradlew --offline testDebugUnitTest assembleDebug assembleRelease` succeeded. Six JUnit tests passed: four model tests (including backend timestamp parsing) and two HTTP session tests (concurrent rotation; rejected refresh without sending an expired write). The APKs in `app/build/outputs/apk/` were rebuilt from this source. The release configuration uses debug signing for direct prototype installation.

No connected Supabase tools or runnable local database were available. SQL execution, permissions, concurrency, the upgrade, and seeded-account sign-in (including the `auth.identities` insert) require verification on the client project. No phone/watch acceptance results or backup recording were found.

## Acceptance scenarios

| Plan scenario | Status and required evidence |
| --- | --- |
| 1. Three-station order and quantities | Implemented; run across actual cloud clients. |
| 2. Partial completion sends no ready alert | Implemented; verify on phone/watch. |
| 3. Last completion alerts within five seconds | Measure during real device rehearsal. |
| 4. Unused stations do not block readiness | Model summary tested; verify cloud completion. |
| 5. Watch acknowledgment updates cloud/dashboard | Actual paired watch required. |
| 6. Pickup clears active queue and notification | Implemented; verify device cleanup. |
| 7. Thirty-second warning | Model boundary and backend timestamp format tested; verify on an Android 13-or-older device as well as a newer one. |
| 8. Repeated taps and reconnects | Retry protection added; verify duplicate requests and uncertain-response recovery against SQL. |
| 9. Concurrent final station completion | Parent-row locking present; execute concurrent database requests. |
| 10. Cancellation and stale watch actions | Implemented; verify with real notification action. |
| 11. Restart and reconnection | Session refresh tests pass; verify process restart and missed-event recovery. |
| 12. Notification permission denied | Setup messaging present; verify Android permission flow. |
| 13. Role and restaurant isolation | SQL policies/role checks present; execute negative access tests. |
| 14. Three simultaneous orders | Run multi-device rehearsal and verify table/notification identities. |

## Handover still required

Apply the original schema and safety upgrade to the client-owned project; seed demo accounts only when replacement of demo orders is intended, then sign in with all three accounts. Install the rebuilt APK, confirm each device header shows "Supabase Cloud", run and record all acceptance outcomes, rehearse the actual paired watch, measure alert latency, and record a successful walkthrough. Agree the revision round and seven-day defect-fix period with the client.
