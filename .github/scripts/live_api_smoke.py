#!/usr/bin/env python3
"""
Smoke-test Hamburg Hbf → Berlin Hbf on int.bahn.de (no Gradle).

Steps (same flow as the app):
  1. GET  /reiseloesung/orte     — resolve departure & destination stations
  2. POST /angebote/fahrplan     — journey search (route planning)
  3. Count connections the app can show (parseable legs), not only raw API rows

Leg parsing mirrors JourneyResponseParser.kt — update both when the API shape changes.
"""
from __future__ import annotations

import gzip
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import zlib
from datetime import datetime, timedelta, timezone
from email.message import Message
from typing import Any

API = "https://int.bahn.de/web/api"
HAMBURG_EVA = "8002549"
BERLIN_EVA = "8011160"
PRODUCTS = [
    "ICE", "EC_IC", "IR", "REGIONAL", "SBAHN", "BUS", "SCHIFF", "UBAHN", "TRAM", "ANRUFPFLICHTIG",
]
HALT_ID_NAME = re.compile(r"@O=([^@]+)@")


def _header_encoding(headers: Message | None) -> str | None:
    if headers is None:
        return None
    enc = headers.get("Content-Encoding")
    return enc.lower().strip() if enc else None


def decode_response_body(data: bytes, content_encoding: str | None = None) -> str:
    """Decompress gzip/deflate bodies (common from int.bahn.de; Termux urllib may not)."""
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
                "Install with: pkg install python-brotli  # or: pip install brotli",
            ) from err
        data = brotli.decompress(data)
    return data.decode("utf-8")


def fetch(url: str, data: bytes | None = None) -> str:
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "Accept-Encoding": "gzip",
    }
    req = urllib.request.Request(url, data=data, headers=headers, method="POST" if data else "GET")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read()
            return decode_response_body(raw, _header_encoding(resp.headers))
    except urllib.error.HTTPError as e:
        raw = e.read() if e.fp else b""
        try:
            body = decode_response_body(raw, _header_encoding(e.headers))
        except Exception:
            body = raw.decode("utf-8", errors="replace")
        if "OPS_BLOCKED" in body:
            print(f"FAIL: Deutsche Bahn blocked this IP (OPS_BLOCKED) HTTP {e.code}")
            sys.exit(2)
        print(f"HTTP {e.code}: {body[:500]}", file=sys.stderr)
        raise


def search_station(name: str) -> dict:
    q = urllib.parse.quote(name)
    raw = fetch(f"{API}/reiseloesung/orte?suchbegriff={q}&typ=ALL&max=5&locale=de")
    if "OPS_BLOCKED" in raw:
        print("FAIL: Deutsche Bahn blocked this IP (OPS_BLOCKED) on /orte")
        sys.exit(2)
    stations = json.loads(raw)
    for s in stations:
        if s.get("extId") == (HAMBURG_EVA if "Hamburg" in name else BERLIN_EVA):
            return s
    for s in stations:
        if s.get("name", "").lower() == name.lower():
            return s
    if not stations:
        print(f"FAIL: no stations for {name!r}")
        sys.exit(1)
    return stations[0]


def extract_connection_elements(root: dict) -> tuple[list[dict], str]:
    top = root.get("verbindungen") or []
    if top:
        return [merge_connection(el) for el in top], "verbindungen"
    for key in ("intervalle", "tagesbestPreisIntervalle"):
        from_intervals: list[dict] = []
        for interval in root.get(key) or []:
            for el in interval.get("verbindungen") or []:
                from_intervals.append(merge_connection(el))
        if from_intervals:
            return from_intervals, key
    return [], "none"


def merge_connection(raw: dict) -> dict:
    inner = raw.get("verbindung")
    if not isinstance(inner, dict):
        return raw
    merged = dict(inner)
    for key, value in raw.items():
        if key == "verbindung":
            continue
        if key in ("verbindungsAbschnitte", "segmente", "halte"):
            incoming = value if isinstance(value, list) else []
            existing = merged.get(key) if isinstance(merged.get(key), list) else []
            if incoming:
                merged[key] = incoming
            elif existing:
                merged[key] = existing
            else:
                merged[key] = value
        else:
            merged[key] = value
    return merged


def flatten_abschnitte(connection: dict) -> list[dict]:
    top = abschnitte_array(connection)
    if not top:
        return []
    return [a for el in top for a in expand_abschnitt(el)]


def abschnitte_array(connection: dict) -> list | None:
    for key in ("verbindungsAbschnitte", "segmente"):
        arr = connection.get(key)
        if isinstance(arr, list) and arr:
            return arr
    for key in ("verbindungsAbschnitte", "segmente"):
        arr = connection.get(key)
        if isinstance(arr, list):
            return arr
    return None


def expand_abschnitt(element: Any) -> list[dict]:
    if not isinstance(element, dict):
        return []
    unwrapped = element.get("verbindungsAbschnitt") or element.get("abschnitt") or element
    if not isinstance(unwrapped, dict):
        return []
    nested = unwrapped.get("verbindungsAbschnitte") or unwrapped.get("segmente")
    if isinstance(nested, list) and nested:
        return [a for el in nested for a in expand_abschnitt(el)]
    return [unwrapped]


def text_field(obj: dict | None, key: str) -> str | None:
    if not obj:
        return None
    el = obj.get(key)
    if isinstance(el, str):
        return el
    if isinstance(el, (int, float)) and key.lower().endswith("zeit"):
        return format_time_value(el)
    if isinstance(el, dict):
        return text_field(el, "name") or text_field(el, "bezeichnung") or text_field(el, "label")
    return None


def format_time_value(value: Any) -> str | None:
    if isinstance(value, str):
        return value if "T" in value else None
    if isinstance(value, (int, float)):
        millis = int(value)
        if millis > 1_000_000_000_000:
            return datetime.fromtimestamp(millis / 1000, tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%S")
        return str(millis)
    return None


def time_field(obj: dict | None, *keys: str) -> str | None:
    if not obj:
        return None
    for key in keys:
        el = obj.get(key)
        if isinstance(el, str):
            return el
        if isinstance(el, (int, float)):
            formatted = format_time_value(el)
            if formatted:
                return formatted
        if isinstance(el, dict):
            for sub in ("zeitpunkt", "zeit", "value"):
                t = text_field(el, sub)
                if t:
                    return t
    return None


def name_from_halt_id(halt_id: str | None) -> str | None:
    if not halt_id:
        return None
    m = HALT_ID_NAME.search(halt_id)
    return m.group(1).replace("+", " ") if m else None


def halt_object(section: dict, *keys: str) -> dict | None:
    for key in keys:
        val = section.get(key)
        if isinstance(val, dict):
            return val
    return None


def station_name_from_halt(halt: dict | None) -> str | None:
    if not halt:
        return None
    return (
        text_field(halt, "name")
        or text_field(halt, "bezeichnung")
        or name_from_halt_id(text_field(halt, "id"))
    )


def station_name(section: dict, ort_key: str, halt: dict | None) -> str | None:
    return (
        text_field(section, ort_key)
        or (text_field(section.get(ort_key), "name") if isinstance(section.get(ort_key), dict) else None)
        or text_field(halt, "name")
        or text_field(halt, "bezeichnung")
        or name_from_halt_id(text_field(halt, "id"))
    )


def halte_array(section: dict) -> list[dict]:
    for key in ("halte", "halt", "stops"):
        arr = section.get(key)
        if isinstance(arr, list):
            return [h for h in arr if isinstance(h, dict)]
    return []


def map_abschnitt(section: dict) -> bool:
    halte = halte_array(section)
    first_halt = halte[0] if halte else None
    last_halt = halte[-1] if halte else None
    start_halt = halt_object(section, "startHalt", "start", "abfahrtsHalt")
    ziel_halt = halt_object(section, "zielHalt", "ziel", "ankunftsHalt")
    dep_name = (
        station_name(section, "abfahrtsOrt", first_halt)
        or station_name(section, "abgangsOrt", first_halt)
        or station_name_from_halt(start_halt)
    )
    arr_name = station_name(section, "ankunftsOrt", last_halt) or station_name_from_halt(ziel_halt)
    dep_time = time_field(
        section, "abfahrtsZeitpunkt", "abgangsZeitpunkt", "abfahrtsZeit", "abfahrt",
    ) or time_field(start_halt, "abfahrtsZeitpunkt", "abfahrtsZeit", "zeitpunkt") or time_field(
        first_halt, "abfahrtsZeitpunkt", "abgangsDatum", "abfahrtsZeit",
    )
    arr_time = time_field(
        section, "ankunftsZeitpunkt", "ankunftsDatum", "ankunftsZeit", "ankunft",
    ) or time_field(ziel_halt, "ankunftsZeitpunkt", "ankunftsZeit", "zeitpunkt") or time_field(
        last_halt, "ankunftsZeitpunkt", "ankunftsDatum", "ankunftsZeit",
    )
    return bool(dep_name and arr_name and dep_time and arr_time)


def map_summary_leg(connection: dict) -> bool:
    dep_name = text_field(connection, "abfahrtsOrt") or text_field(connection, "origin")
    arr_name = text_field(connection, "ankunftsOrt") or text_field(connection, "destination")
    dep_time = time_field(connection, "abfahrtsZeit", "abfahrtsZeitpunkt")
    arr_time = time_field(connection, "ankunftsZeit", "ankunftsZeitpunkt")
    return bool(dep_name and arr_name and dep_time and arr_time)


def count_parseable_journeys(root: dict) -> tuple[int, int, str]:
    """Returns (raw_connections, parseable_journeys, source)."""
    connections, source = extract_connection_elements(root)
    raw = len(connections)
    parseable = 0
    for conn in connections:
        legs = [s for s in flatten_abschnitte(conn) if map_abschnitt(s)]
        if not legs and map_summary_leg(conn):
            legs = [conn]
        if legs:
            parseable += 1
    return raw, parseable, source


def main() -> None:
    when = (datetime.now(timezone.utc) + timedelta(hours=3)).strftime("%Y-%m-%dT%H:%M:%S")
    print("== OpenBahn live API smoke (Hamburg Hbf → Berlin Hbf) ==")
    print("== Step 1: station search GET /reiseloesung/orte ==")
    h = search_station("Hamburg Hbf")
    b = search_station("Berlin Hbf")
    print(f"From: {h['name']} eva={h.get('extId')} id={h['id'][:56]}…")
    print(f"To:   {b['name']} eva={b.get('extId')} id={b['id'][:56]}…")
    body = {
        "abfahrtsHalt": h["id"],
        "ankunftsHalt": b["id"],
        "anfrageZeitpunkt": when,
        "ankunftSuche": "ABFAHRT",
        "bikeCarriage": False,
        "deutschlandTicketVorhanden": False,
        "nurDeutschlandTicketVerbindungen": False,
        "schnelleVerbindungen": False,
        "sitzplatzOnly": False,
        "reservierungsKontingenteVorhanden": False,
        "klasse": "KLASSE_2",
        "reisende": [{"typ": "ERWACHSENER", "anzahl": 1, "alter": [], "ermaessigungen": []}],
        "produktgattungen": PRODUCTS,
    }

    print(f"== Step 2: journey search POST /angebote/fahrplan (departure {when}) ==")
    raw = fetch(
        f"{API}/angebote/fahrplan",
        data=json.dumps(body).encode(),
    )
    if "OPS_BLOCKED" in raw:
        print("FAIL: Deutsche Bahn blocked this IP (OPS_BLOCKED) on /fahrplan")
        sys.exit(2)
    data = json.loads(raw)
    if data.get("status") == "ERROR":
        print(f"FAIL: API error code={data.get('code')}")
        sys.exit(1)

    raw_n, parseable, source = count_parseable_journeys(data)
    print(
        f"status={data.get('status', 'ok')} rawConnections={raw_n} "
        f"parseableRoutes={parseable} source={source}",
    )

    if raw_n == 0:
        print('FAIL: API returned no connections (app would show "No connections found")')
        sys.exit(1)
    if parseable == 0:
        print(
            "FAIL: API returned connections but none have parseable legs — "
            'app would show "No connections found" (parser/schema issue)',
        )
        sys.exit(1)

    first = merge_connection((data.get("verbindungen") or [{}])[0])
    flat = flatten_abschnitte(first)
    sample = flat[0] if flat else first
    print(
        f"== Step 3: route check OK — {parseable} journey(s) with legs "
        f"(sample: {station_name(sample, 'abfahrtsOrt', halte_array(sample)[0] if halte_array(sample) else None)} "
        f"→ {station_name(sample, 'ankunftsOrt', halte_array(sample)[-1] if halte_array(sample) else None)}) ==",
    )
    print("OK: station search + journey search + parseable routes from this network")


if __name__ == "__main__":
    main()
