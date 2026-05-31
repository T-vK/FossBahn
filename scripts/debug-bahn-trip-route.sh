#!/usr/bin/env bash
# Capture live bahn.de API data for full vehicle routes (Termux / Linux).
# Usage:
#   ./scripts/debug-bahn-trip-route.sh
#   ./scripts/debug-bahn-trip-route.sh "Hamburg Hbf" "Berlin Hbf"
#   JOURNEY_ID='2|#ZB#RB    31#ZE#81633#' ./scripts/debug-bahn-trip-route.sh
#
# Output: ./trip-route-debug/ (journey search + fahrt + boards). Share that folder for debugging.
set -euo pipefail

FROM="${1:-Hamburg Hbf}"
TO="${2:-Uelzen}"
OUT_DIR="${OUT_DIR:-./trip-route-debug}"
BASE="${BAHN_API_BASE:-https://int.bahn.de/web/api}"
UA="${BAHN_USER_AGENT:-OpenBahnNavigator/1.0 (debug; Termux)}"

mkdir -p "$OUT_DIR"

curl_api() {
  local method="$1"
  local url="$2"
  shift 2
  if [ "$method" = GET ]; then
    curl -sS -G "$url" \
      -H "Accept: application/json" \
      -H "User-Agent: $UA" \
      "$@" \
      -w '\nHTTP_STATUS:%{http_code}\n'
  else
    curl -sS -X POST "$url" \
      -H "Accept: application/json" \
      -H "Content-Type: application/json" \
      -H "User-Agent: $UA" \
      "$@" \
      -w '\nHTTP_STATUS:%{http_code}\n'
  fi
}

echo "==> Resolving stations: $FROM -> $TO"
FROM_JSON=$(curl_api GET "$BASE/reiseloesung/orte" \
  --data-urlencode "suchbegriff=$FROM" \
  --data-urlencode "typ=ALL" \
  --data-urlencode "max=5" \
  --data-urlencode "locale=de")
printf '%s\n' "$FROM_JSON" > "$OUT_DIR/01-ort-from.json"

TO_JSON=$(curl_api GET "$BASE/reiseloesung/orte" \
  --data-urlencode "suchbegriff=$TO" \
  --data-urlencode "typ=ALL" \
  --data-urlencode "max=5" \
  --data-urlencode "locale=de")
printf '%s\n' "$TO_JSON" > "$OUT_DIR/02-ort-to.json"

FROM_ID=$(printf '%s' "$FROM_JSON" | sed '/HTTP_STATUS:/d' | python3 -c "
import json,sys
raw=sys.stdin.read()
data=json.loads(raw)
for o in (data if isinstance(data,list) else data.get('orte') or data.get('locations') or []):
    e=o.get('extId') or o.get('id')
    if e: print(str(e).strip('\"')); break
" 2>/dev/null || true)
TO_ID=$(printf '%s' "$TO_JSON" | sed '/HTTP_STATUS:/d' | python3 -c "
import json,sys
raw=sys.stdin.read()
data=json.loads(raw)
for o in (data if isinstance(data,list) else data.get('orte') or data.get('locations') or []):
    e=o.get('extId') or o.get('id')
    if e: print(str(e).strip('\"')); break
" 2>/dev/null || true)

if [ -z "$FROM_ID" ] || [ -z "$TO_ID" ]; then
  echo "Could not parse station IDs. Check 01-ort-from.json and 02-ort-to.json" >&2
  exit 1
fi

WHEN=$(date -u -d '+2 hours' +%Y-%m-%dT%H:%M:%S 2>/dev/null || date -u -v+2H +%Y-%m-%dT%H:%M:%S)
FAHRPLAN_BODY=$(cat <<EOF
{
  "anfrageZeitpunkt": "$WHEN",
  "abfahrtsHalt": "A=1@L=${FROM_ID}@",
  "ankunftsHalt": "A=1@L=${TO_ID}@",
  "ankunftSuche": "ABFAHRT",
  "maxUmstiege": 2,
  "produktgattungen": ["ICE","EC_IC","IR","REGIONAL","SBAHN","BUS","SCHIFF","UBAHN","TRAM","ANRUFPFLICHTIG"]
}
EOF
)

echo "==> Journey search ($FROM_ID -> $TO_ID at $WHEN)"
FAHRPLAN=$(curl_api POST "$BASE/angebote/fahrplan" --data-binary "$FAHRPLAN_BODY")
printf '%s\n' "$FAHRPLAN" > "$OUT_DIR/03-fahrplan.json"

JOURNEY_ID="${JOURNEY_ID:-}"
if [ -z "$JOURNEY_ID" ]; then
  JOURNEY_ID=$(printf '%s' "$FAHRPLAN" | sed '/HTTP_STATUS:/d' | python3 -c "
import json,sys
j=json.loads(sys.stdin.read())
conns=j.get('verbindungen') or []
if not conns: sys.exit(0)
v=conns[0].get('verbindung') or conns[0]
secs=v.get('verbindungsAbschnitte') or v.get('segmente') or []
for s in secs:
    if s.get('journeyId'):
        print(s['journeyId']); break
" 2>/dev/null || true)
fi

if [ -z "$JOURNEY_ID" ]; then
  echo "No journeyId in search response. Set JOURNEY_ID=... manually and re-run." >&2
  echo "Files written to $OUT_DIR" >&2
  exit 0
fi

echo "==> Using journeyId: $JOURNEY_ID"
printf '%s\n' "$JOURNEY_ID" > "$OUT_DIR/04-journey-id.txt"

echo "==> GET /reiseloesung/fahrt"
FAHRT=$(curl_api GET "$BASE/reiseloesung/fahrt" --data-urlencode "journeyId=$JOURNEY_ID")
printf '%s\n' "$FAHRT" > "$OUT_DIR/05-fahrt.json"

DATE=$(printf '%s' "$WHEN" | cut -c1-10)
TIME=$(printf '%s' "$WHEN" | cut -c12-19)

echo "==> GET abfahrten mitVias at origin $FROM_ID"
ABF=$(curl_api GET "$BASE/reiseloesung/abfahrten" \
  --data-urlencode "ortExtId=$FROM_ID" \
  --data-urlencode "datum=$DATE" \
  --data-urlencode "zeit=$TIME" \
  --data-urlencode "mitVias=true" \
  --data-urlencode "dauer=60")
printf '%s\n' "$ABF" > "$OUT_DIR/06-abfahrten-mitvias.json"

echo "==> GET ankuenfte mitVias at origin $FROM_ID"
ANK=$(curl_api GET "$BASE/reiseloesung/ankuenfte" \
  --data-urlencode "ortExtId=$FROM_ID" \
  --data-urlencode "datum=$DATE" \
  --data-urlencode "zeit=$TIME" \
  --data-urlencode "mitVias=true" \
  --data-urlencode "dauer=60")
printf '%s\n' "$ANK" > "$OUT_DIR/07-ankuenfte-mitvias.json"

printf '%s\n' "$FAHRPLAN" | sed '/HTTP_STATUS:/d' | python3 -c "
import json,sys
j=json.loads(sys.stdin.read())
conns=j.get('verbindungen') or []
if not conns:
    print('fahrplan: 0 connections'); sys.exit(0)
v=conns[0].get('verbindung') or conns[0]
secs=v.get('verbindungsAbschnitte') or v.get('segmente') or []
for i,s in enumerate(secs):
    halte=s.get('halte') or []
    print(f'leg[{i}] journeyId={s.get(\"journeyId\",\"?\")[:60]} halte_in_search={len(halte)}')
" 2>/dev/null || true

printf '%s\n' "$FAHRT" | sed '/HTTP_STATUS:/d' | python3 -c "
import json,sys
j=json.loads(sys.stdin.read())
h=j.get('halte') or []
print(f'fahrt: halte={len(h)}')
if h:
    print('  first:', h[0].get('name'))
    print('  last:', h[-1].get('name'))
" 2>/dev/null || true

echo ""
echo "Done. Share folder: $OUT_DIR"
echo "  especially 03-fahrplan.json, 05-fahrt.json, 04-journey-id.txt"
