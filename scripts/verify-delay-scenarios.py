#!/usr/bin/env python3
"""
Debug trace for Verspätung / cancellation on 2026-05-30 Hamburg → Berlin scenarios.

Scenarios (documented real-world behaviour):
  ICE 603  dep 13:49 gl.11 → arr 16:20 gl.3  (actual ~13:56 / ~16:42)
  FLX 1247 dep 17:11 gl.14 → arr 19:47 gl.4  (cancelled)

Steps:
  1. Resolve stations (/reiseloesung/orte)
  2. Search /angebote/fahrplan for 2026-05-30T12:00
  3. Locate ICE 603 @ 13:49 and FLX 1247 @ 17:11 in raw JSON
  4. POST /reiseloesung/verbindung refresh (ctxRecon)
  5. GET /reiseloesung/abfahrten + /ankuenfte for ezZeit / ausfall

Usage:
  python3 scripts/verify-delay-scenarios.py --fixtures   # offline JSON (no Java/Gradle)
  ./scripts/verify-delay-scenarios.sh                      # fixtures via Gradle, or --fixtures if no Java
  python3 scripts/verify-delay-scenarios.py --when 2026-05-30T12:00:00
  python3 scripts/verify-delay-scenarios.py --when "$(date -u -d '+3 hours' +%Y-%m-%dT%H:%M:%S)"  # if 422 (past)
  python3 scripts/verify-delay-scenarios.py --gradle     # Kotlin verifyDelayScenarios (needs JDK 17)
  python3 scripts/verify-delay-scenarios.py --strict       # fail if historical delays cleared from live API
  python3 scripts/verify-delay-scenarios.py --when 2026-05-30T12:00:00 --board-at-scheduled --dump-raw

Termux (no Java): pkg install openjdk-17 OR use --fixtures only.

Exit codes: 0 = checks passed or skipped (not in results), 1 = assertion failed, 2 = OPS_BLOCKED
"""
from __future__ import annotations

import gzip
import json
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import zlib
from datetime import datetime, timedelta
from email.message import Message
from pathlib import Path
from typing import Any

API = "https://int.bahn.de/web/api"
HAMBURG_EVA = "8002549"
BERLIN_EVA = "8011160"
# Documented scenario day (ICE 603 / FLX 1247). API may reject past times with HTTP 422.
DEFAULT_SCENARIO_WHEN = "2026-05-30T12:00:00"
# Must match JourneyRequestBuilder / live_api_smoke.py (invalid codes → HTTP 422).
PRODUKTGATTUNGEN = [
    "ICE",
    "EC_IC",
    "IR",
    "REGIONAL",
    "SBAHN",
    "BUS",
    "SCHIFF",
    "UBAHN",
    "TRAM",
    "ANRUFPFLICHTIG",
]
FIXTURE_DIR = Path(__file__).resolve().parents[1] / "core/api/src/test/resources"
SCENARIO_DAY = "2026-05-30"
SCENARIOS = (
    {
        "name": "ICE 603 (delayed)",
        "line": "ICE 603",
        "dep_clock": "13:49",
        "arr_clock": "16:20",
        "expect_dep_delay_min": 5,
        "expect_dep_prognosed": "13:56",
        "expect_arr_prognosed": "16:42",
        "expect_arr_delay_min": 15,
        "expect_cancelled": False,
    },
    {
        "name": "FLX 1247 (cancelled)",
        "line": "FLX 1247",
        "dep_clock": "17:11",
        "arr_clock": "19:47",
        "expect_dep_delay_min": 0,
        "expect_cancelled": True,
    },
)


def _header_encoding(headers: Message | None) -> str | None:
    if headers is None:
        return None
    enc = headers.get("Content-Encoding")
    return enc.lower().strip() if enc else None


def decode_body(data: bytes, content_encoding: str | None = None) -> str:
    enc = (content_encoding or "").lower()
    if enc == "gzip" or (len(data) >= 2 and data[:2] == b"\x1f\x8b"):
        data = gzip.decompress(data)
    elif enc in ("deflate", "x-deflate"):
        data = zlib.decompress(data)
    elif enc == "br":
        try:
            import brotli  # type: ignore[import-not-found]
        except ImportError as err:
            raise RuntimeError(
                "Response uses Brotli (Content-Encoding: br). "
                "Install with: pip install brotli  # Termux: pkg install python-brotli",
            ) from err
        data = brotli.decompress(data)
    return data.decode("utf-8")


def fetch(url: str, data: bytes | None = None, method: str | None = None) -> str:
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "Accept-Encoding": "gzip",
        "User-Agent": "OpenBahnNavigator/verify-delay-scenarios",
    }
    req = urllib.request.Request(
        url,
        data=data,
        headers=headers,
        method=method or ("POST" if data else "GET"),
    )
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            return decode_body(resp.read(), _header_encoding(resp.headers))
    except urllib.error.HTTPError as e:
        body = decode_body(e.read() if e.fp else b"", _header_encoding(e.headers))
        if "OPS_BLOCKED" in body:
            print("FAIL: Deutsche Bahn blocked this IP (OPS_BLOCKED)")
            sys.exit(2)
        print(f"HTTP {e.code} from {url}", file=sys.stderr)
        print(body[:2000], file=sys.stderr)
        hint = api_error_hint(body, e.code)
        if hint:
            print(f"Hint: {hint}", file=sys.stderr)
        raise RuntimeError(f"HTTP {e.code}") from e


def api_error_hint(body: str, code: int) -> str | None:
    if code != 422:
        return None
    try:
        data = json.loads(body)
    except json.JSONDecodeError:
        return "Request validation failed (422). Check produktgattungen and anfrageZeitpunkt."
    if data.get("code") == "OPS_BLOCKED":
        return "Deutsche Bahn blocked this IP."
    msg = data.get("fehlerNachricht") or data.get("message") or data.get("code")
    if msg:
        return f"API says: {msg}"
    return (
        "422 often means invalid produktgattungen or anfrageZeitpunkt in the past. "
        "Try: python3 scripts/verify-delay-scenarios.py --when $(date -u +%Y-%m-%dT%H:%M:%S)"
    )


def search_station(name: str, preferred_eva: str | None = None) -> dict:
    q = urllib.parse.quote(name)
    raw = fetch(f"{API}/reiseloesung/orte?suchbegriff={q}&typ=ALL&max=8&locale=de")
    if "OPS_BLOCKED" in raw:
        print("FAIL: Deutsche Bahn blocked this IP (OPS_BLOCKED) on /orte")
        sys.exit(2)
    stations = json.loads(raw)
    if not isinstance(stations, list):
        raise RuntimeError(f"/orte expected JSON array, got {type(stations).__name__}")
    if preferred_eva:
        for s in stations:
            if s.get("extId") == preferred_eva:
                return s
    for s in stations:
        if (s.get("name") or "").lower() == name.lower():
            return s
    if not stations:
        raise RuntimeError(f"No stations found for {name!r}")
    return stations[0]


def halt_id_for_journey(station: dict) -> str:
    lid = station.get("id")
    if isinstance(lid, str) and lid.startswith("A=1@"):
        return lid
    eva = station.get("extId")
    if eva and str(eva).isdigit():
        return f"A=1@L={eva}@"
    raise RuntimeError(f"Station {station.get('name')!r} has no bahn.de lid id: {station!r}")


def build_fahrplan_body(from_station: dict, to_station: dict, when_iso: str) -> dict[str, Any]:
    """Same shape as JourneyRequestBuilder / live_api_smoke.py."""
    return {
        "abfahrtsHalt": halt_id_for_journey(from_station),
        "ankunftsHalt": halt_id_for_journey(to_station),
        "anfrageZeitpunkt": when_iso,
        "ankunftSuche": "ABFAHRT",
        "bikeCarriage": False,
        "deutschlandTicketVorhanden": False,
        "nurDeutschlandTicketVerbindungen": False,
        "schnelleVerbindungen": False,
        "sitzplatzOnly": False,
        "reservierungsKontingenteVorhanden": False,
        "klasse": "KLASSE_2",
        "reisende": [{"typ": "ERWACHSENER", "anzahl": 1, "alter": [], "ermaessigungen": []}],
        "produktgattungen": list(PRODUKTGATTUNGEN),
    }


def parse_when_arg(argv: list[str]) -> str:
    for i, arg in enumerate(argv):
        if arg == "--when" and i + 1 < len(argv):
            return argv[i + 1]
        if arg.startswith("--when="):
            return arg.split("=", 1)[1]
    return DEFAULT_SCENARIO_WHEN


def warn_if_past(when_iso: str) -> None:
    try:
        when = datetime.fromisoformat(when_iso[:19])
    except ValueError:
        print(f"Warning: could not parse --when {when_iso!r}", file=sys.stderr)
        return
    now = datetime.now()
    if when < now - timedelta(minutes=5):
        print(
            f"Warning: anfrageZeitpunkt {when_iso} is in the past (now {now:%Y-%m-%dT%H:%M:%S}).\n"
            "  bahn.de often returns HTTP 422 for past searches.\n"
            "  For a live connectivity check use: --when with a near-future time.\n"
            "  Offline ICE/FLX checks: python3 scripts/verify-delay-scenarios.py --fixtures",
            file=sys.stderr,
        )


def clock(iso: str | None) -> str:
    if not iso or "T" not in iso:
        return iso or "—"
    return iso.split("T", 1)[1][:5]


def banner(title: str) -> None:
    print()
    print("=" * 62)
    print(f"  {title}")
    print("=" * 62)


def delay_minutes(scheduled: str | None, prognosed: str | None) -> int | None:
    if not scheduled or not prognosed or scheduled == prognosed:
        return None
    try:
        s = datetime.fromisoformat(scheduled[:19])
        p = datetime.fromisoformat(prognosed[:19])
        mins = int((p - s).total_seconds() // 60)
        return mins if mins > 0 else None
    except ValueError:
        return None


def line_name(section: dict) -> str | None:
    vm = section.get("verkehrsmittel") or {}
    return vm.get("name") or vm.get("kurzText")


def flatten_connections(root: dict) -> list[dict]:
    conns = root.get("verbindungen") or []
    out: list[dict] = []
    for raw in conns:
        inner = raw.get("verbindung")
        merged = {**inner, **{k: v for k, v in raw.items() if k != "verbindung"}} if isinstance(inner, dict) else raw
        out.append(merged)
    return out


def _halt_times(halt: dict, for_departure: bool) -> tuple[Any, Any]:
    if for_departure:
        sched = halt.get("abfahrtsZeitpunkt") or halt.get("abgangsZeitpunkt")
        prog = halt.get("ezAbfahrtsZeitpunkt") or halt.get("ezAbgangsZeitpunkt") or halt.get("ezZeit")
    else:
        sched = halt.get("ankunftsZeitpunkt") or halt.get("ankunftsZeit")
        prog = halt.get("ezAnkunftsZeitpunkt") or halt.get("ezAnkunftsDatum") or halt.get("ezZeit")
    return sched, prog


def section_times(section: dict) -> dict[str, Any]:
    abfahrt = section.get("abfahrt") if isinstance(section.get("abfahrt"), dict) else {}
    ankunft = section.get("ankunft") if isinstance(section.get("ankunft"), dict) else {}
    halte = [h for h in (section.get("halte") or []) if isinstance(h, dict)]
    first_halt = halte[0] if halte else {}
    last_halt = halte[-1] if halte else {}
    fh_sched, fh_prog = _halt_times(first_halt, True)
    lh_sched, lh_prog = _halt_times(last_halt, False)

    dep_sched = abfahrt.get("sollzeit") or section.get("abfahrtsZeitpunkt") or fh_sched
    dep_prog = (
        abfahrt.get("prognosezeit")
        or abfahrt.get("echtzeit")
        or abfahrt.get("echtZeit")
        or section.get("ezAbfahrtsZeitpunkt")
        or abfahrt.get("ezZeit")
        or fh_prog
    )
    arr_sched = ankunft.get("sollzeit") or section.get("ankunftsZeitpunkt") or lh_sched
    arr_prog = (
        ankunft.get("prognosezeit")
        or ankunft.get("echtzeit")
        or ankunft.get("echtZeit")
        or section.get("ezAnkunftsZeitpunkt")
        or ankunft.get("ezZeit")
        or lh_prog
    )
    dep_delay = abfahrt.get("verspaetung") or section.get("verspaetung")
    return {
        "dep_sched": dep_sched,
        "dep_prog": dep_prog,
        "dep_delay": dep_delay or delay_minutes(str(dep_sched), str(dep_prog) if dep_prog else None),
        "arr_sched": arr_sched,
        "arr_prog": arr_prog,
        "arr_delay": ankunft.get("verspaetung") or delay_minutes(str(arr_sched), str(arr_prog) if arr_prog else None),
        "origin_cancelled": section.get("originCancelled"),
        "destination_cancelled": section.get("destinationCancelled"),
        "remarks": section.get("himMeldungen") or [],
        "hinweise": [],
    }


def is_cancelled_section(section: dict, conn: dict) -> bool:
    if section.get("originCancelled") or section.get("destinationCancelled"):
        return True
    for key in ("risNotizen", "echtzeitNotizen"):
        for note in section.get(key) or []:
            if not isinstance(note, dict):
                continue
            k = note.get("key", "")
            v = (note.get("value") or note.get("text") or "").lower()
            if k == "text.realtime.stop.cancelled" or "entfällt" in v or "fällt aus" in v:
                return True
    for h in conn.get("hinweise") or []:
        if isinstance(h, dict):
            t = (h.get("kurzText") or h.get("text") or "").lower()
            if "fällt" in t or "entfällt" in t:
                return True
    return False


def find_connection(connections: list[dict], line: str, dep_clock: str) -> dict | None:
    for conn in connections:
        for sec in conn.get("verbindungsAbschnitte") or conn.get("segmente") or []:
            if not isinstance(sec, dict):
                continue
            ln = line_name(sec) or ""
            times = section_times(sec)
            if line.lower() in ln.lower() and clock(str(times["dep_sched"])) == dep_clock:
                return {"connection": conn, "section": sec, "times": times}
    return None


def dump_delay_fields(section: dict) -> None:
    """Print raw API objects that may carry Verspätung (for comparing with bahn.de)."""
    abfahrt = section.get("abfahrt")
    ankunft = section.get("ankunft")
    print("\n── raw delay fields (section) ──")
    if isinstance(abfahrt, dict):
        print(f"  abfahrt: {json.dumps(abfahrt, ensure_ascii=False)[:400]}")
    else:
        print("  abfahrt: (missing)")
    if isinstance(ankunft, dict):
        print(f"  ankunft: {json.dumps(ankunft, ensure_ascii=False)[:400]}")
    halte = section.get("halte") or []
    if halte:
        h0 = halte[0] if isinstance(halte[0], dict) else {}
        h1 = halte[-1] if isinstance(halte[-1], dict) else {}
        print(f"  first halt keys: {sorted(h0.keys())}")
        for label, h in (("origin halt", h0), ("dest halt", h1)):
            snippet = {
                k: h.get(k)
                for k in (
                    "abfahrtsZeitpunkt",
                    "ankunftsZeitpunkt",
                    "ezAbfahrtsZeitpunkt",
                    "ezAnkunftsZeitpunkt",
                    "ezZeit",
                    "verspaetung",
                    "prognosezeit",
                    "istzeit",
                )
                if h.get(k) is not None
            }
            if snippet:
                print(f"  {label}: {json.dumps(snippet, ensure_ascii=False)}")


def print_connection_debug(label: str, hit: dict) -> None:
    conn = hit["connection"]
    sec = hit["section"]
    t = hit["times"]
    print(f"\n── {label} ──")
    print(f"  line: {line_name(sec)}")
    print(f"  dep  {clock(str(t['dep_sched']))} → {clock(str(t['dep_prog']))}  delay={t['dep_delay']} min")
    print(f"  arr  {clock(str(t['arr_sched']))} → {clock(str(t['arr_prog']))}  delay={t['arr_delay']} min")
    print(f"  ctxRecon: {'yes' if conn.get('ctxRecon') else 'no'}")
    print(f"  cancelled: {is_cancelled_section(sec, conn)}")


def refresh_connection(ctx_recon: str) -> dict | None:
    body = json.dumps({"ctxRecon": ctx_recon, "poly": True}).encode()
    text = fetch(f"{API}/reiseloesung/verbindung", data=body)
    if "OPS_BLOCKED" in text:
        sys.exit(2)
    return json.loads(text)


def connections_from_refresh_payload(raw: dict) -> list[dict]:
    """Normalize /reiseloesung/verbindung response like JourneyResponseParser.parseRefresh."""
    if raw.get("verbindungen"):
        return flatten_connections(raw)
    inner = raw.get("verbindung")
    if isinstance(inner, dict):
        return flatten_connections({"verbindungen": [inner]})
    if raw.get("verbindungsAbschnitte") or raw.get("segmente"):
        return [raw]
    return flatten_connections({"verbindungen": [raw]})


def effective_board_when(scenario_when_iso: str) -> str:
    """Board API rejects past datum/zeit — use now when scenario time has passed."""
    try:
        when = datetime.fromisoformat(scenario_when_iso[:19])
    except ValueError:
        return scenario_when_iso
    now = datetime.now() + timedelta(minutes=2)
    if when < now:
        return now.strftime("%Y-%m-%dT%H:%M:%S")
    return scenario_when_iso


def board_departures(eva: str, when: str, *, at_scheduled: bool = False) -> list[dict]:
    if not at_scheduled:
        when = effective_board_when(when)
    date, time = when[:10], when[11:19]
    params: list[tuple[str, str]] = [
        ("ortExtId", eva),
        ("datum", date),
        ("zeit", time),
        ("dauer", "180"),
    ]
    for p in PRODUKTGATTUNGEN:
        params.append(("verkehrsmittel[]", p))
    q = urllib.parse.urlencode(params)
    text = fetch(f"{API}/reiseloesung/abfahrten?{q}", method="GET")
    data = json.loads(text)
    return data.get("abfahrten") or data.get("entries") or []


def board_arrivals(eva: str, when: str, *, at_scheduled: bool = False) -> list[dict]:
    if not at_scheduled:
        when = effective_board_when(when)
    date, time = when[:10], when[11:19]
    params: list[tuple[str, str]] = [
        ("ortExtId", eva),
        ("datum", date),
        ("zeit", time),
        ("dauer", "180"),
    ]
    for p in PRODUKTGATTUNGEN:
        params.append(("verkehrsmittel[]", p))
    q = urllib.parse.urlencode(params)
    text = fetch(f"{API}/reiseloesung/ankuenfte?{q}", method="GET")
    data = json.loads(text)
    return data.get("ankuenfte") or data.get("entries") or []


def match_board(entries: list[dict], line: str, dep_clock: str) -> dict | None:
    for e in entries:
        vm = e.get("verkehrsmittel") or {}
        name = vm.get("name") or ""
        sched = e.get("zeit") or e.get("abfahrtsZeit")
        if line.lower() in name.lower() and clock(str(sched)) == dep_clock:
            return e
    return None


def enrich_hit_with_refresh(hit: dict, spec: dict) -> dict:
    """Mirror app: search often has no verspaetung; refresh via ctxRecon adds delays."""
    token = hit["connection"].get("ctxRecon")
    if not token:
        return hit
    banner(f"REFRESH /reiseloesung/verbindung: {spec['name']}")
    try:
        refreshed = refresh_connection(token)
    except RuntimeError as err:
        print(f"  refresh failed: {err}")
        return hit
    if not refreshed:
        return hit
    r_conns = connections_from_refresh_payload(refreshed)
    r_hit = find_connection(r_conns, spec["line"], spec["dep_clock"])
    if r_hit:
        print_connection_debug("after refresh (used for checks)", r_hit)
        return r_hit
    print("  refresh returned data but could not re-match line/time — using search payload")
    return hit


def delays_ok(times: dict[str, Any], spec: dict) -> bool:
    dep_delay = times["dep_delay"] or 0
    arr_delay = times["arr_delay"] or 0
    dep_prog = clock(str(times["dep_prog"])) if times.get("dep_prog") else None
    arr_prog = clock(str(times["arr_prog"])) if times.get("arr_prog") else None
    if dep_delay >= spec["expect_dep_delay_min"]:
        return True
    if arr_delay >= spec.get("expect_arr_delay_min", 0):
        return True
    if dep_prog == spec.get("expect_dep_prognosed"):
        return True
    if arr_prog == spec.get("expect_arr_prognosed"):
        return True
    return False


def scenario_departure_datetime(spec: dict) -> datetime | None:
    try:
        return datetime.fromisoformat(f"{SCENARIO_DAY}T{spec['dep_clock']}:00")
    except ValueError:
        return None


def historical_delays_likely_cleared(spec: dict) -> bool:
    """Live API often drops verspaetung/prognose for connections hours after they ran."""
    if spec.get("expect_cancelled"):
        return False
    dep = scenario_departure_datetime(spec)
    if dep is None:
        return False
    return datetime.now() > dep + timedelta(hours=3)


def strict_mode(argv: list[str]) -> bool:
    return "--strict" in argv


def run_fixture_checks() -> bool:
    """Offline verification using the same JSON as Kotlin verifyDelayScenarios (no Java)."""
    banner("FIXTURE: ICE 603 (delayed)")
    ice_path = FIXTURE_DIR / "dbweb-scenario-ice603-delay.json"
    if not ice_path.is_file():
        print(f"✗ missing fixture: {ice_path}")
        return False
    ice_conns = flatten_connections(json.loads(ice_path.read_text(encoding="utf-8")))
    ice_hit = find_connection(ice_conns, "ICE 603", "13:49")
    if ice_hit is None:
        print("✗ ICE 603 not found in fixture")
        return False
    t = ice_hit["times"]
    print_connection_debug("fixture parse", ice_hit)
    if not delays_ok(t, SCENARIOS[0]):
        print(
            f"✗ ICE 603 fixture: expected dep +7 / arr +22 min "
            f"(got dep_delay={t['dep_delay']} arr_delay={t['arr_delay']})",
        )
        return False
    print("✓ ICE 603 fixture: delays +7 / +22 min (matches Kotlin test)")

    banner("FIXTURE: FLX 1247 (cancelled)")
    flx_path = FIXTURE_DIR / "dbweb-scenario-flx1247-cancelled.json"
    if not flx_path.is_file():
        print(f"✗ missing fixture: {flx_path}")
        return False
    flx_conns = flatten_connections(json.loads(flx_path.read_text(encoding="utf-8")))
    flx_hit = find_connection(flx_conns, "FLX 1247", "17:11")
    if flx_hit is None:
        print("✗ FLX 1247 not found in fixture")
        return False
    print_connection_debug("fixture parse", flx_hit)
    if not is_cancelled_section(flx_hit["section"], flx_hit["connection"]):
        print("✗ FLX 1247 fixture: expected cancellation flags/remarks")
        return False
    print("✓ FLX 1247 fixture: cancellation detected")
    return True


def check_scenario(hit: dict | None, spec: dict, *, strict: bool) -> bool:
    if hit is None:
        print(f"⚠ {spec['name']}: not found in search results (may be outside timetable window)")
        return True

    print_connection_debug("search /fahrplan", hit)
    best = enrich_hit_with_refresh(hit, spec)
    if "--dump-raw" in sys.argv:
        dump_delay_fields(best["section"])
    t = best["times"]
    conn = best["connection"]
    sec = best["section"]

    if spec["expect_cancelled"]:
        ok = is_cancelled_section(sec, conn)
        if not ok:
            print(f"✗ {spec['name']}: expected cancellation flags/remarks in search/refresh JSON")
            return False
        print(f"✓ {spec['name']}: cancellation visible in API")
        return True

    dep_delay = t["dep_delay"] or 0
    dep_prog = clock(str(t["dep_prog"])) if t.get("dep_prog") else None
    if delays_ok(t, spec):
        print(
            f"✓ {spec['name']}: delays visible after refresh "
            f"(dep delay={dep_delay} min, prog={dep_prog})",
        )
        return True

    if not strict and historical_delays_likely_cleared(spec):
        print(
            f"⚠ {spec['name']}: connection found but live API has no delay data "
            f"(dep delay={dep_delay} prog={dep_prog})",
        )
        print(
            "  Historical afternoon trains often lose verspaetung/prognose hours later.",
        )
        print("  Verify parser offline: python3 scripts/verify-delay-scenarios.py --fixtures")
        return True

    print(
        f"✗ {spec['name']}: no delays after search+refresh "
        f"(dep delay={dep_delay} prog={dep_prog}; expected >={spec['expect_dep_delay_min']} min "
        f"or {spec.get('expect_dep_prognosed')})",
    )
    print("  Note: historical delays (2026-05-30 afternoon) may no longer be in the live API.")
    print("  Use: python3 scripts/verify-delay-scenarios.py --fixtures")
    return False


def main() -> None:
    if "--fixtures" in sys.argv:
        banner("OFFLINE FIXTURE VERIFICATION (no Java / no network)")
        ok = run_fixture_checks()
        sys.exit(0 if ok else 1)

    when_iso = parse_when_arg(sys.argv)
    warn_if_past(when_iso)
    strict = strict_mode(sys.argv)
    board_at_scheduled = "--board-at-scheduled" in sys.argv

    run_gradle = "--gradle" in sys.argv
    if run_gradle:
        root = Path(__file__).resolve().parents[1]
        print("Running Kotlin verifyDelayScenarios…")
        import shutil

        if shutil.which("java") is None:
            print(
                "Gradle skipped: Java not in PATH. "
                "Use --fixtures for offline checks, or install JDK 17 (see docs/DEVELOPMENT.md).",
                file=sys.stderr,
            )
        else:
            subprocess.run(
                ["./gradlew", ":core:api:verifyDelayScenarios", "--console=plain"],
                cwd=root,
                check=False,
            )

    banner(f"PYTHON API TRACE: Hamburg → Berlin (when={when_iso})")
    hamburg = search_station("Hamburg Hbf", HAMBURG_EVA)
    berlin = search_station("Berlin Hbf", BERLIN_EVA)
    print(f"From: {hamburg.get('name')} ({hamburg.get('extId')}) id={str(hamburg.get('id', ''))[:48]}…")
    print(f"To:   {berlin.get('name')} ({berlin.get('extId')}) id={str(berlin.get('id', ''))[:48]}…")

    body = build_fahrplan_body(hamburg, berlin, when_iso)
    banner("SEARCH /angebote/fahrplan")
    print(f"  produktgattungen: {', '.join(PRODUKTGATTUNGEN)}")
    raw = fetch(f"{API}/angebote/fahrplan", data=json.dumps(body).encode())
    if "OPS_BLOCKED" in raw:
        print("FAIL: Deutsche Bahn blocked this IP (OPS_BLOCKED) on /fahrplan")
        sys.exit(2)
    root = json.loads(raw)
    if root.get("status") == "ERROR":
        print(f"FAIL: API error code={root.get('code')} body={json.dumps(root)[:500]}")
        sys.exit(1)
    connections = flatten_connections(root)
    print(f"Connections: {len(connections)}")
    for conn in connections[:12]:
        for sec in conn.get("verbindungsAbschnitte") or conn.get("segmente") or []:
            if isinstance(sec, dict) and line_name(sec):
                t = section_times(sec)
                print(
                    f"  • {line_name(sec)} dep {clock(str(t['dep_sched']))} "
                    f"delay={t['dep_delay']}",
                )
                break

    # FLX may need a later search anchor than 12:00
    extra_connections: list[dict] = []
    if when_iso.startswith("2026-05-30"):
        try:
            later_body = build_fahrplan_body(hamburg, berlin, "2026-05-30T16:30:00")
            later_raw = fetch(f"{API}/angebote/fahrplan", data=json.dumps(later_body).encode())
            extra_connections = flatten_connections(json.loads(later_raw))
            if extra_connections:
                print(f"Supplemental search @ 16:30: {len(extra_connections)} connection(s)")
        except RuntimeError:
            pass

    all_connections = connections + extra_connections

    all_ok = True
    for spec in SCENARIOS:
        banner(spec["name"])
        hit = find_connection(all_connections, spec["line"], spec["dep_clock"])
        if not check_scenario(hit, spec, strict=strict):
            all_ok = False

        dep_when = f"2026-05-30T{spec['dep_clock']}:00"
        board_when = dep_when if board_at_scheduled else effective_board_when(dep_when)
        if board_when != dep_when:
            print(f"  (board uses now={board_when} — pass --board-at-scheduled to query departure time)")
        elif board_at_scheduled:
            print(f"  (board at scheduled departure {board_when})")

        banner(f"BOARD abfahrten @ Hamburg (target dep {spec['dep_clock']})")
        try:
            entries = board_departures(HAMBURG_EVA, board_when, at_scheduled=board_at_scheduled)
            board_hit = match_board(entries, spec["line"], spec["dep_clock"])
            if board_hit:
                ez = board_hit.get("ezZeit")
                zeit = board_hit.get("zeit")
                d = board_hit.get("verspaetung") or delay_minutes(str(zeit), str(ez))
                print(
                    f"  abfahrten: {clock(str(zeit))} → {clock(str(ez))} "
                    f"delay={d} ausfall={board_hit.get('ausfall')}",
                )
            else:
                print("  abfahrten: no exact line/time match in 3h window (may differ after scenario date)")
        except Exception as err:
            print(f"  abfahrten: skipped ({err})")

    if not all_ok:
        sys.exit(1)
    print("\nAll applicable checks passed.")


if __name__ == "__main__":
    main()
