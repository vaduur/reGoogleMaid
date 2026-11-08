#!/system/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
# re:GoogleMaid modification: idempotent bootstrap, compatible with automatic recovery.
set -eu
status() { content call --uri content://dev.evoker.homeholdcts.desktopsearch --method status 2>/dev/null || true; }
connected() { printf '%s' "$1" | grep -Eq 'pid=[1-9][0-9]*'; }
current=$(status)
if connected "$current"; then printf '%s\nAGENT_ALREADY_CONNECTED\n' "$current"; exit 0; fi
# Give the existing recovery process a chance to attach. Do not spawn duplicates.
for i in 1 2 3 4; do
  pidof colorgoogle-search >/dev/null || break
  sleep 1
  current=$(status)
  if connected "$current"; then printf '%s\nAGENT_RECOVERED\n' "$current"; exit 0; fi
done
if pidof colorgoogle-search >/dev/null; then echo ERROR_AGENT_RUNNING_WITHOUT_CONNECTION_OPEN_COLORGOOGLE; exit 1; fi
apk=$(pm path --user 0 dev.evoker.homeholdcts | sed -n "s/^package://p" | head -n 1)
test -r "$apk"
export CLASSPATH="$apk"
nohup app_process / --nice-name=colorgoogle-search dev.evoker.homeholdcts.DesktopSearchAgent </dev/null >/data/local/tmp/colorgoogle-search.log 2>&1 &
sleep 2
current=$(status)
printf '%s\n' "$current"
connected "$current" || { echo ERROR_AGENT_DID_NOT_CONNECT; exit 1; }
