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
  python3 scripts/verify-delay-scenarios.py
  python3 scripts/verify-delay-scenarios.py --when 2026-05-30T12:00:00
  python3 scripts/verify-delay-scenarios.py --when "$(date -u -d '+3 hours' +%Y-%m-%dT%H:%M:%S)"  # if 422 (past)
  python3 scripts/verify-delay-scenarios.py --gradle   # also run Kotlin verifyDelayScenarios

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
SCENARIOS = (
    {
        "name": "ICE 603 (delayed)",
        "line": "ICE 603",
        "dep_clock": "13:49",
        "arr_clock": "16:20",
        "expect_dep_delay_min": 5,
        "expect_dep_prognosed": "13:56",
        "expect_arr_prognosed": "16:42",
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
            "  For ICE 603 / FLX 1247 logic use: ./scripts/verify-delay-scenarios.sh (fixtures).",
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


def section_times(section: dict) -> dict[str, Any]:
    abfahrt = section.get("abfahrt") if isinstance(section.get("abfahrt"), dict) else {}
    ankunft = section.get("ankunft") if isinstance(section.get("ankunft"), dict) else {}
    dep_sched = (
        abfahrt.get("sollzeit")
        or section.get("abfahrtsZeitpunkt")
        or (section.get("halte") or [{}])[0].get("abfahrtsZeitpunkt")
    )
    dep_prog = (
        abfahrt.get("prognosezeit")
        or section.get("ezAbfahrtsZeitpunkt")
        or (section.get("halte") or [{}])[0].get("ezAbfahrtsZeitpunkt")
    )
    arr_sched = (
        ankunft.get("sollzeit")
        or section.get("ankunftsZeitpunkt")
        or (section.get("halte") or [{}])[-1].get("ankunftsZeitpunkt")
    )
    arr_prog = (
        ankunft.get("prognosezeit")
        or section.get("ezAnkunftsZeitpunkt")
        or (section.get("halte") or [{}])[-1].get("ezAnkunftsZeitpunkt")
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


def board_departures(eva: str, when: str) -> list[dict]:
    date, time = when[:10], when[11:19]
    params: list[tuple[str, str]] = [
        ("ortExtId", eva),
        ("datum", date),
        ("zeit", time),
        ("dauer", "180"),
    ]
    for p in ("ICE", "IC", "EC", "IR", "RE", "RB", "S", "BUS"):
        params.append(("verkehrsmittel[]", p))
    q = urllib.parse.urlencode(params)
    text = fetch(f"{API}/reiseloesung/abfahrten?{q}", method="GET")
    data = json.loads(text)
    return data.get("abfahrten") or data.get("entries") or []


def match_board(entries: list[dict], line: str, dep_clock: str) -> dict | None:
    for e in entries:
        vm = e.get("verkehrsmittel") or {}
        name = vm.get("name") or ""
        sched = e.get("zeit") or e.get("abfahrtsZeit")
        if line.lower() in name.lower() and clock(str(sched)) == dep_clock:
            return e
    return None


def check_scenario(hit: dict | None, spec: dict) -> bool:
    if hit is None:
        print(f"⚠ {spec['name']}: not found in search results (may be outside timetable window)")
        return True
    print_connection_debug(spec["name"], hit)
    t = hit["times"]
    conn = hit["connection"]
    sec = hit["section"]

    if spec["expect_cancelled"]:
        ok = is_cancelled_section(sec, conn)
        if not ok:
            print(f"✗ {spec['name']}: expected cancellation flags/remarks")
            return False
        print(f"✓ {spec['name']}: cancellation visible in API")
        return True

    dep_delay = t["dep_delay"] or 0
    dep_prog = clock(str(t["dep_prog"]))
    if dep_delay < spec["expect_dep_delay_min"] and dep_prog != spec.get("expect_dep_prognosed"):
        print(
            f"✗ {spec['name']}: expected dep delay >={spec['expect_dep_delay_min']} min "
            f"or prognosed {spec.get('expect_dep_prognosed')}, got delay={dep_delay} prog={dep_prog}",
        )
        return False
    print(f"✓ {spec['name']}: delay visible in search JSON (dep delay={dep_delay} min)")

    token = conn.get("ctxRecon")
    if token:
        banner(f"REFRESH: {spec['name']}")
        refreshed = refresh_connection(token)
        if refreshed:
            r_conns = flatten_connections(refreshed if isinstance(refreshed, dict) else {})
            r_hit = find_connection(r_conns, spec["line"], spec["dep_clock"])
            if r_hit:
                print_connection_debug("after /verbindung", r_hit)
            else:
                print("  (refresh returned data but could not re-match section)")
    return True


def main() -> None:
    when_iso = parse_when_arg(sys.argv)
    warn_if_past(when_iso)

    run_gradle = "--gradle" in sys.argv
    if run_gradle:
        root = Path(__file__).resolve().parents[1]
        print("Running Kotlin verifyDelayScenarios…")
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

    all_ok = True
    for spec in SCENARIOS:
        banner(spec["name"])
        hit = find_connection(connections, spec["line"], spec["dep_clock"])
        if not check_scenario(hit, spec):
            all_ok = False

        banner(f"BOARD abfahrten @ Hamburg ({spec['dep_clock']})")
        dep_when = f"2026-05-30T{spec['dep_clock']}:00"
        try:
            entries = board_departures(HAMBURG_EVA, dep_when)
            board_hit = match_board(entries, spec["line"], spec["dep_clock"])
            if board_hit:
                ez = board_hit.get("ezZeit")
                zeit = board_hit.get("zeit")
                d = board_hit.get("verspaetung") or delay_minutes(str(zeit), str(ez))
                print(f"  board: {clock(str(zeit))} → {clock(str(ez))} delay={d} ausfall={board_hit.get('ausfall')}")
            else:
                print("  board: no matching entry in 3h window")
        except Exception as err:
            print(f"  board: skipped ({err})")

    if not all_ok:
        sys.exit(1)
    print("\nAll applicable checks passed.")


if __name__ == "__main__":
    main()
