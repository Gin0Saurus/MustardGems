#!/usr/bin/env bash
# Boots a real Paper server with the plugin, runs the in-server self-test, reloads, stops.
# Usage: ci/smoke.sh <minecraft-version> <path-to-plugin-jar>
# Writes ci-server/result.txt = PASS | FAIL | SKIP (no Paper build for that version).
set -uo pipefail

MC="$1"
JAR="$(realpath "$2")"
UA="FacetGems-CI (https://github.com/Gin0Saurus/MustardGems)"
API="https://fill.papermc.io/v3/projects/paper/versions/$MC/builds"

rm -rf ci-server && mkdir -p ci-server/plugins && cd ci-server

BUILDS="$(curl -fsS -H "User-Agent: $UA" "$API" || true)"
URL="$(echo "$BUILDS" | jq -r 'if type=="array" then (map(select(.channel=="STABLE")) | .[0].downloads."server:default".url // empty) else empty end' 2>/dev/null)"
CHANNEL="STABLE"
if [ -z "$URL" ]; then
  URL="$(echo "$BUILDS" | jq -r 'if type=="array" then (.[0].downloads."server:default".url // empty) else empty end' 2>/dev/null)"
  CHANNEL="$(echo "$BUILDS" | jq -r 'if type=="array" then (.[0].channel // "?") else "?" end' 2>/dev/null)"
fi
if [ -z "$URL" ]; then
  echo "No Paper build published for $MC - skipping (this version is NOT claimed as tested)."
  echo "SKIP" > result.txt
  exit 0
fi
BUILD_ID="$(echo "$BUILDS" | jq -r "map(select(.downloads.\"server:default\".url==\"$URL\")) | .[0].id")"
echo "Paper $MC build $BUILD_ID ($CHANNEL)"
echo "$MC build $BUILD_ID ($CHANNEL)" > tested-on.txt
curl -fsSL -H "User-Agent: $UA" -o paper.jar "$URL"
cp "$JAR" plugins/

echo "eula=true" > eula.txt
cat > server.properties <<'EOF'
online-mode=false
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
max-players=2
view-distance=4
simulation-distance=4
motd=facet-ci
EOF

mkfifo console
java -Xms1G -Xmx2G -jar paper.jar --nogui < console > server.log 2>&1 &
PID=$!
exec 3>console

send() { echo "$1" >&3; sleep "${2:-4}"; }

for i in $(seq 1 300); do
  if grep -q 'Done (' server.log; then break; fi
  if ! kill -0 "$PID" 2>/dev/null; then break; fi
  sleep 1
done

if grep -q 'Done (' server.log; then
  send "gem selftest" 6
  send "gem reload" 4
  send "gem selftest" 6
  send "gem debug" 3
  send "gem give nobody strength" 2
fi
send "stop" 1
exec 3>&-
for i in $(seq 1 120); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
kill -9 "$PID" 2>/dev/null || true

echo "------------------------------------------------------------ relevant log"
grep -E 'FacetGems|Facet Gems|FACET|\[debug\]|Config:|io\.github\.gin0saurus|Exception|ERROR' server.log | head -200

fail=0
grep -q 'Facet Gems .* enabled' server.log || { echo "::error::Plugin did not enable on $MC"; fail=1; }
[ "$(grep -c 'FACET SELFTEST PASS' server.log)" -ge 2 ] || { echo "::error::Self-test did not pass twice on $MC"; fail=1; }
if grep -q 'FACET SELFTEST FAIL' server.log; then
  grep 'FACET SELFTEST FAIL' server.log | while read -r l; do echo "::error::$MC $l"; done
  fail=1
fi
if grep -q 'at io\.github\.gin0saurus' server.log; then echo "::error::Stack trace from the plugin on $MC"; fail=1; fi
if grep -qE "Error occurred while enabling FacetGems|Could not load 'plugins/FacetGems" server.log; then echo "::error::Load error on $MC"; fail=1; fi
if grep -q 'Config: ' server.log; then echo "::error::Shipped config produced warnings on $MC"; fail=1; fi

if [ "$fail" -eq 0 ]; then echo "PASS" > result.txt; echo "Smoke test PASSED on $MC"; else echo "FAIL" > result.txt; fi
exit "$fail"
