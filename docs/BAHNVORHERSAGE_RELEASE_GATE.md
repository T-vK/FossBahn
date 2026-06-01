# Bahn-Vorhersage release gate

## Why previous releases failed

The bahnvorhersage server **requires a walking (`walking: true`) segment between every pair of consecutive rail legs**. Deutsche Bahn often returns two (or more) train legs back-to-back at the same station (e.g. Hamburg → Hannover ICE, Hannover → Berlin ICE) **without** an explicit `WALK` abschnitt.

The server then raises:

`ValueError: Transfer segment missing between two legs`

…which surfaces to the app as **HTTP 500** and heuristic-only ratings.

Earlier client fixes (2-stop trips, unique trip IDs, null stopover fields) were necessary but **did not address this rule**, so Hamburg Hbf → Berlin Hbf kept failing while single-leg or Köln-style fixtures could pass.

## What we do now

1. **`expandLegsForRating`** — Before building FPTF JSON, insert a synthetic walking leg between consecutive non-walking legs.
2. **Walking time fields** — Send `arrival` at the transfer origin and `departure` toward the next train (matches bahnvorhersage `Transfer.from_leg_dict`).
3. **Numeric EVA stop IDs** — Use `@L=…` / EVA digits so Neo4j transfer lookup does not crash on `int(halt_id)`.

## Tests (must pass before release)

| Test | When | What |
|------|------|------|
| `BahnVorhersageLegExpansionTest` | Every CI build | Unit: expansion + JSON contains `walking:true` |
| `BahnVorhersageMobileV2ProbeTest.post_twoRailLegHamburgBerlinShape_returns200` | Every CI build | **Live POST** to bahnvorhersage.de (Hamburg→Berlin shape, 2 rail legs) |
| `BahnVorhersageMobileV2ProbeTest.post_dbVendoMultiLegFixture_returns200` | `RUN_LIVE_API_TESTS=true` | Live POST with real db-vendo fixture |
| `BahnVorhersageHamburgBerlinGateTest` | `RUN_LIVE_API_TESTS=true` | Full pipeline: DB search HH→BE, rate all journeys, assert ML (not heuristic) |

Run locally:

```bash
# Always run (includes Hamburg→Berlin-shaped live probe)
./gradlew :core:api:testDebugUnitTest --tests "de.openbahn.api.BahnVorhersageMobileV2ProbeTest" --rerun-tasks

# Full gate (needs int.bahn.de + bahnvorhersage.de)
RUN_LIVE_API_TESTS=true ./gradlew :core:api:testDebugUnitTest \
  --tests "de.openbahn.api.BahnVorhersageMobileV2ProbeTest" \
  --tests "de.openbahn.api.BahnVorhersageHamburgBerlinGateTest" \
  --rerun-tasks
```

CI: the `live-api` job runs `/.github/scripts/run-live-api-tests.sh`, which includes the Bahn-Vorhersage tests above.

## Device verification

After installing the build, search **Hamburg Hbf → Berlin Hbf** and confirm logcat:

- `POST …/api/mobile/v2/journeys` → **HTTP 200** (not 500 HTML)
- `stopCounts=[2]` per trip
- `estimate=false` and `mlStops > 0` on rated journeys
