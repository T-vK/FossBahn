# Passenger rights system

Privacy-first evaluation of EU/EVO-style entitlements and Germany-specific Deutschlandticket rules. **No automated filing** — the app produces drafts and notifications; the user confirms before sending email.

## Architecture layers

| Layer | Module | Responsibility |
|-------|--------|----------------|
| Data | `core:rights` models, `TripEventStream` | `PlannedTrip`, `RouteGraph`, `DelayEvent`, ledger |
| Inference | `JourneyRightsAdapter` | Map `Journey` → event stream |
| Legal logic | `rules/*`, `PassengerRightsEngine` | Deterministic standard rules + exception heuristics |
| User approval | `ClaimDraftBuilder`, `ClaimsScreen`, email intent | Draft only; no API claim submission |

## Decision states

- `NORMAL_DELAY` — below compensation thresholds
- `COMPENSATION_ELIGIBLE` / `HIGH_COMPENSATION_ELIGIBLE` — standard paths (EU % or D-Ticket €)
- `LAST_CONNECTION_RISK` — last connection + severe disruption
- `NO_PUBLIC_TRANSPORT_AVAILABLE` — no alternative in window
- `TAXI_REIMBURSEMENT_POSSIBLE` — **conditional**, not guaranteed
- `FERNVERKEHR_FALLBACK_POSSIBLE` — ICE/IC escalation **conditional**

Exception outcomes always use `LegalDisclaimers` and `requiresUserConfirmation = true`.

## Deutschlandticket

- ≥ 60 min at destination: €1.50 per incident
- ≥ 120 min: €2.50
- Monthly cap: 25 % of €49 ≈ **€12.25** (`MonthlyLedger` in DataStore per month)

## Testing with simulated delays (debug builds)

In **Settings → Simulate delay (debug)** choose a preset:

| Preset | Effect |
|--------|--------|
| Off | Live data only |
| D-Ticket 60 min | +65 min arrival, D-Ticket rules |
| D-Ticket 120 min | +125 min arrival, D-Ticket rules |
| Long-distance 60 min | +65 min, EU % rules |
| Last connection / taxi | +75 min, last connection, no ÖPNV alternative |

Open any **connection detail**: the card shows the simulated delay and the passenger-rights banner when thresholds are met. Tracked journeys use the same simulation during background refresh.

Implementation: `PassengerRightsSimulationConfig` + `Journey.applyPassengerRightsSimulation()` in `core:model`.

## Extension points

- Set `isLastConnectionOfDay` / `hasPublicAlternativeInWindow` on `PassengerRightsRepository.evaluate()` when route replanning exists
- Add operator-specific email targets in `ClaimDraftBuilder`
- Live API tests: reuse delay fixtures from `core/api/src/test/resources`
