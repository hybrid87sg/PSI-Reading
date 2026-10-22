#!/bin/bash
# Test helper for CI: replaces the app's saved settings on a rooted emulator, then reopens it.
# Usage: psi-app/ci/seed.sh psi-app/ci/prefs-4-locations.xml
set -e
adb root >/dev/null
adb wait-for-device
adb shell am force-stop sg.psi.app
adb push "$1" /data/data/sg.psi.app/shared_prefs/psi.xml >/dev/null
adb shell 'u=$(stat -c %u /data/data/sg.psi.app); chown $u:$u /data/data/sg.psi.app/shared_prefs/psi.xml; chmod 660 /data/data/sg.psi.app/shared_prefs/psi.xml'
adb shell am start -n sg.psi.app/.MainActivity >/dev/null
