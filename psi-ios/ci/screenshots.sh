#!/bin/bash
# Installs the simulator build on an iPhone and an iPad simulator and takes screenshots.
# Usage: ci/screenshots.sh path/to/PSI.app output-dir
set -euo pipefail
APP="$1"
OUT="$2"
mkdir -p "$OUT"

# First available simulator whose name starts with one of the given prefixes.
pick() {
  xcrun simctl list devices available -j | python3 -c '
import json, sys
prefixes = sys.argv[1:]
devices = [d for runtime, ds in json.load(sys.stdin)["devices"].items() if "iOS" in runtime for d in ds]
for p in prefixes:
    for d in devices:
        if d["name"].startswith(p):
            print(d["udid"]); sys.exit(0)
sys.exit("no simulator for " + " / ".join(prefixes))
' "$@"
}

start() {
  xcrun simctl boot "$1" || true
  xcrun simctl bootstatus "$1" -b
  xcrun simctl status_bar "$1" override --time 9:41 --batteryLevel 100 --cellularBars 4 || true
  xcrun simctl install "$1" "$APP"
}

IPHONE=$(pick "iPhone 16 Pro" "iPhone 15 Pro" "iPhone")
start "$IPHONE"
# Fresh install: first-run setup asks for the home region.
xcrun simctl launch "$IPHONE" sg.psi.app
sleep 15
xcrun simctl io "$IPHONE" screenshot "$OUT/psi-ios-setup.png"
# Skip setup (launch argument overrides the stored flag) to show the main screen.
xcrun simctl terminate "$IPHONE" sg.psi.app || true
xcrun simctl launch "$IPHONE" sg.psi.app -setup_done YES
sleep 15
xcrun simctl io "$IPHONE" screenshot "$OUT/psi-ios-iphone.png"
xcrun simctl shutdown "$IPHONE" || true

IPAD=$(pick "iPad Pro 13" "iPad Pro" "iPad Air" "iPad")
start "$IPAD"
xcrun simctl launch "$IPAD" sg.psi.app -setup_done YES
sleep 15
xcrun simctl io "$IPAD" screenshot "$OUT/psi-ios-ipad.png"
xcrun simctl shutdown "$IPAD" || true
