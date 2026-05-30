#!/usr/bin/env python3
"""Smoke-test Hamburg Hbf → Berlin Hbf on int.bahn.de (no Gradle)."""
from __future__ import annotations

import gzip
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import zlib
from datetime import datetime, timedelta, timezone
from email.message import Message

API = "https://int.bahn.de/web/api"
HAMBURG_EVA = "8002549"
BERLIN_EVA = "8011160"
PRODUCTS = [
    "ICE", "EC_IC", "IR", "REGIONAL", "SBAHN", "BUS", "SCHIFF", "UBAHN", "TRAM", "ANRUFPFLICHTIG",
]


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


def count_connections(d: dict) -> tuple[int, str]:
    top = d.get("verbindungen") or []
    if top:
        return len(top), "verbindungen"
    total = 0
    source = "none"
    for key in ("intervalle", "tagesbestPreisIntervalle"):
        intervals = d.get(key) or []
        n = sum(len(i.get("verbindungen") or []) for i in intervals)
        if n > 0:
            total += n
            source = key
    return total, source


def main() -> None:
    when = (datetime.now(timezone.utc) + timedelta(hours=3)).strftime("%Y-%m-%dT%H:%M:%S")
    print("== OpenBahn live API smoke (Hamburg Hbf → Berlin Hbf) ==")
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

    print(f"== POST /angebote/fahrplan (departure {when}) ==")
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
    n, source = count_connections(data)
    print(f"status={data.get('status', 'ok')} connections={n} source={source}")
    if n == 0:
        print('FAIL: no connections (app would show "No connections found")')
        sys.exit(1)
    print(f"OK: {n} connection(s) — bahn.de reachable from this network")


if __name__ == "__main__":
    main()
