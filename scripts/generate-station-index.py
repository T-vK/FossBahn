#!/usr/bin/env python3
"""Build app/src/main/assets/openbahn/stations.tsv from DB Haltestellendaten CSV.

Source (CC BY 4.0, Deutsche Bahn):
  https://data.deutschebahn.com/dataset/data-haltestellen
Mirror used by default:
  https://raw.githubusercontent.com/ratopi/haltestellendaten/master/D_Bahnhof_2016_01_alle.csv

Usage:
  python3 scripts/generate-station-index.py
  python3 scripts/generate-station-index.py /path/to/D_Bahnhof.csv
"""

from __future__ import annotations

import csv
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/openbahn/stations.tsv"
DEFAULT_URL = (
    "https://raw.githubusercontent.com/ratopi/haltestellendaten/master/"
    "D_Bahnhof_2016_01_alle.csv"
)
IMPORTANCE = {"FV": "0", "RV": "1", "nur DPN": "2"}


def load_rows(path: Path | None) -> list[tuple[str, str, str, str, str]]:
    if path is None:
        with urllib.request.urlopen(DEFAULT_URL, timeout=60) as resp:
            text = resp.read().decode("utf-8-sig")
    else:
        text = path.read_text(encoding="utf-8-sig")

    reader = csv.DictReader(text.splitlines(), delimiter=";")
    rows: list[tuple[str, str, str, str, str]] = []
    for row in reader:
        eva = (row.get("EVA_NR") or "").strip()
        name = (row.get("NAME") or "").strip().replace("\t", " ").replace("\n", " ")
        lat = (row.get("BREITE") or "").strip().replace(",", ".")
        lon = (row.get("LAENGE") or "").strip().replace(",", ".")
        verkehr = (row.get("VERKEHR") or "").strip()
        if not eva.isdigit() or not name or not lat or not lon:
            continue
        rows.append((eva, name, lat, lon, IMPORTANCE.get(verkehr, "2")))
    rows.sort(key=lambda r: (int(r[4]), r[1].casefold(), r[0]))
    return rows


def main() -> None:
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    rows = load_rows(src)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8", newline="\n") as f:
        f.write("# EVA\tNAME\tLAT\tLON\tIMPORTANCE(0=FV,1=RV,2=DPN)\n")
        f.write("# Source: Deutsche Bahn Haltestellendaten (CC BY 4.0)\n")
        f.write("# https://data.deutschebahn.com/dataset/data-haltestellen\n")
        for eva, name, lat, lon, importance in rows:
            f.write(f"{eva}\t{name}\t{lat}\t{lon}\t{importance}\n")
    print(f"Wrote {len(rows)} stations to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
