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
MOBILE_API = "https://app.services-bahn.de/mob"
HAMBURG_EVA = "8002549"
BERLIN_EVA = "8011160"
MOBILE_JOURNEY_CT = "application/x.db.vendo.mob.verbindungssuche.v9+json"
MOBILE_LOCATION_CT = "application/x.db.vendo.mob.location.v3+json"
CORRELATION = "00000000-0000-0000-0000-000000000001_00000000-0000-0000-0000-000000000002"
# Long UA with a GitHub URL gets HTML Access Denied on some /orte queries via curl;
# the Android OkHttp ClientHello still passes with the fuller app UA.
USER_AGENT = "OpenBahnNavigator/1.0 (FOSS)"
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


def fetch(
    url: str,
    data: bytes | None = None,
    *,
    accept: str = "application/json",
    content_type: str = "application/json",
    extra_headers: dict[str, str] | None = None,
    allow_blocked: bool = False,
) -> str:
    """
    Prefer curl with Chrome-like TLS curves — stock OpenSSL/urllib ClientHellos
    are often OPS_BLOCKED by Akamai (same fingerprint issue as the Android app).
    """
    import shutil
    import subprocess

    curl = shutil.which("curl")
    if curl:
        cmd = [
            curl, "-sS", "--http1.1",
            "--curves", "X25519:P-256:P-384",
            "-H", f"Accept: {accept}",
            "-H", f"User-Agent: {USER_AGENT}",
            "-w", "\n%{http_code}",
        ]
        for key, value in (extra_headers or {}).items():
            cmd += ["-H", f"{key}: {value}"]
        if data is not None:
            cmd += ["-H", f"Content-Type: {content_type}", "-X", "POST", "--data-binary", "@-"]
        cmd.append(url)
        proc = subprocess.run(
            cmd,
            input=data or b"",
            capture_output=True,
            timeout=60,
        )
        out = proc.stdout
        if proc.returncode != 0 and not out:
            print(proc.stderr.decode("utf-8", errors="replace"), file=sys.stderr)
            proc.check_returncode()
        # Last line is HTTP status from -w
        if b"\n" in out:
            body_bytes, status_bytes = out.rsplit(b"\n", 1)
            status = int(status_bytes.decode().strip() or "0")
        else:
            body_bytes, status = out, 0
        # curl may leave gzip compressed when Accept-Encoding is set
        body = decode_response_body(body_bytes, "gzip" if body_bytes[:2] == b"\x1f\x8b" else None)
        blocked = status in (403, 452) or "OPS_BLOCKED" in body or body.lstrip().startswith("<")
        if blocked:
            if allow_blocked:
                raise RuntimeError(f"blocked HTTP {status or '?'}")
            print(f"FAIL: Deutsche Bahn blocked this IP (OPS_BLOCKED) HTTP {status or '?'}")
            sys.exit(2)
        if status and status >= 400:
            print(f"HTTP {status}: {body[:500]}", file=sys.stderr)
            raise RuntimeError(f"HTTP {status}")
        return body

    headers = {
        "Accept": accept,
        "Content-Type": content_type,
        "User-Agent": USER_AGENT,
    }
    if extra_headers:
        headers.update(extra_headers)
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
            if allow_blocked:
                raise RuntimeError(f"blocked HTTP {e.code}") from e
            print(f"FAIL: Deutsche Bahn blocked this IP (OPS_BLOCKED) HTTP {e.code}")
            sys.exit(2)
        print(f"HTTP {e.code}: {body[:500]}", file=sys.stderr)
        raise


def _pick_station(stations: list[dict], name: str, *, id_key: str = "id", eva_key: str = "extId") -> dict:
    want_eva = HAMBURG_EVA if "Hamburg" in name else BERLIN_EVA if "Berlin" in name else None
    if want_eva:
        for s in stations:
            if str(s.get(eva_key) or "") == want_eva:
                return s
    for s in stations:
        if str(s.get("name", "")).lower() == name.lower():
            return s
    if not stations:
        print(f"FAIL: no stations for {name!r}")
        sys.exit(1)
    return stations[0]


def search_station_mobile(name: str) -> dict:
    raw = fetch(
        f"{MOBILE_API}/location/search",
        data=json.dumps({
            "locationTypes": ["ALL"],
            "searchTerm": name,
            "maxResults": 12,
        }).encode(),
        accept=MOBILE_LOCATION_CT,
        content_type=MOBILE_LOCATION_CT,
        extra_headers={"X-Correlation-ID": CORRELATION},
    )
    stations = json.loads(raw)
    if not isinstance(stations, list):
        stations = stations.get("locations") or []
    # Normalize mobile shape to the web fields the rest of the script expects.
    normalized = []
    for s in stations:
        if not isinstance(s, dict):
            continue
        loc_id = s.get("locationId") or s.get("id") or ""
        eva = None
        if "@L=" in loc_id:
            eva = loc_id.split("@L=", 1)[1].split("@", 1)[0]
        normalized.append({
            "id": loc_id,
            "name": s.get("name") or name,
            "extId": eva or s.get("evaNumber") or s.get("extId"),
        })
    picked = _pick_station(normalized, name, eva_key="extId")
    return picked


def hardcoded_station(name: str) -> dict:
    """Last-resort IDs so journey smoke can still run when station search is blocked."""
    if "Hamburg" in name:
        eva, label = HAMBURG_EVA, "Hamburg Hbf"
    elif "Berlin" in name:
        eva, label = BERLIN_EVA, "Berlin Hbf"
    else:
        print(f"FAIL: no hardcoded station for {name!r}")
        sys.exit(1)
    return {"id": f"A=1@O={label}@L={eva}@", "name": label, "extId": eva}


def search_station(name: str) -> dict:
    # Match the app (max=12). Some Akamai rules block Hamburg%20Hbf&max=5&locale=de.
    q = urllib.parse.quote(name)
    try:
        raw = fetch(
            f"{API}/reiseloesung/orte?suchbegriff={q}&typ=ALL&max=12&locale=de",
            allow_blocked=True,
        )
        stations = json.loads(raw)
        if isinstance(stations, list):
            return _pick_station(stations, name)
    except Exception as err:
        print(f"web /orte blocked ({err}); trying mobile location search")
    try:
        return search_station_mobile(name)
    except Exception as err:
        print(f"mobile location blocked ({err}); using hardcoded EVA for {name!r}")
        return hardcoded_station(name)


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
    if isinstance(el, bool):
        return None
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
            nested = time_field(
                el,
                "zeitpunkt",
                "zeit",
                "sollzeit",
                "istzeit",
                "prognosezeit",
                "abfahrtsZeitpunkt",
                "ankunftsZeitpunkt",
                "abfahrtszeit",
                "ankunftszeit",
                "abgangsZeitpunkt",
                "dateTime",
                "value",
            )
            if nested:
                return nested
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
    ) or time_field(start_halt, "abfahrtsZeitpunkt", "abfahrtsZeit", "zeitpunkt", "abfahrt") or time_field(
        first_halt, "abfahrtsZeitpunkt", "abgangsDatum", "abfahrtsZeit",
    )
    arr_time = time_field(
        section, "ankunftsZeitpunkt", "ankunftsDatum", "ankunftsZeit", "ankunft",
    ) or time_field(ziel_halt, "ankunftsZeitpunkt", "ankunftsZeit", "zeitpunkt", "ankunft") or time_field(
        last_halt, "ankunftsZeitpunkt", "ankunftsDatum", "ankunftsZeit",
    )
    return bool(dep_name and arr_name and dep_time and arr_time)


def abschnitt_parse_debug(section: dict) -> str:
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
    ) or time_field(start_halt, "abfahrtsZeitpunkt", "abfahrtsZeit", "zeitpunkt", "abfahrt")
    arr_time = time_field(
        section, "ankunftsZeitpunkt", "ankunftsDatum", "ankunftsZeit", "ankunft",
    ) or time_field(ziel_halt, "ankunftsZeitpunkt", "ankunftsZeit", "zeitpunkt", "ankunft")
    missing = [k for k, v in (("depName", dep_name), ("arrName", arr_name), ("depTime", dep_time), ("arrTime", arr_time)) if not v]
    abfahrt_keys = list(section.get("abfahrt").keys()) if isinstance(section.get("abfahrt"), dict) else []
    return f"missing={missing} abfahrtKeys={abfahrt_keys} sectionKeys={sorted(section.keys())[:16]}"


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

    print(f"== Step 2: journey search (mobile first, departure {when}) ==")
    mobile_body = {
        "autonomeReservierung": False,
        "einstiegsTypList": ["STANDARD"],
        "fahrverguenstigungen": {
            "deutschlandTicketVorhanden": False,
            "nurDeutschlandTicketVerbindungen": False,
        },
        "klasse": "KLASSE_2",
        "reiseHin": {
            "wunsch": {
                "abgangsLocationId": f"A=1@L={h.get('extId') or HAMBURG_EVA}@",
                "zielLocationId": f"A=1@L={b.get('extId') or BERLIN_EVA}@",
                "alternativeHalteBerechnung": True,
                "verkehrsmittel": ["ALL"],
                "zeitWunsch": {"reiseDatum": when, "zeitPunktArt": "ABFAHRT"},
            },
        },
        "reisendenProfil": {
            "reisende": [{
                "ermaessigungen": ["KEINE_ERMAESSIGUNG KLASSENLOS"],
                "reisendenTyp": "ERWACHSENER",
            }],
        },
        "reservierungsKontingenteVorhanden": False,
    }
    try:
        raw = fetch(
            f"{MOBILE_API}/angebote/fahrplan",
            data=json.dumps(mobile_body).encode(),
            accept=MOBILE_JOURNEY_CT,
            content_type=MOBILE_JOURNEY_CT,
            extra_headers={"X-Correlation-ID": CORRELATION},
            allow_blocked=True,
        )
        print("Using mobile app.services-bahn.de /fahrplan")
    except Exception as mobile_err:
        print(f"mobile blocked ({mobile_err}); trying web int.bahn.de")
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
        conns, _ = extract_connection_elements(data)
        if conns:
            sample = flatten_abschnitte(conns[0])
            sec = sample[0] if sample else conns[0]
            print(f"hint: {abschnitt_parse_debug(sec)}", file=sys.stderr)
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
