#!/usr/bin/env bash
# Tries the in-app update flow on a connected phone, without publishing a release:
#   1. installs the current debug build,
#   2. serves a "newer" debug build (v99.0) from this computer as if it were the latest GitHub release
#      (debug builds look for updates at http://127.0.0.1:8765, see app/build.gradle.kts),
#   3. runs the app's daily update check right away.
# Then the phone shows the update notification; tap it, then Update, as a user would.
#
# Usage: scripts/try-update.sh [adb serial]   (the serial is needed when more than one phone is connected)
set -euo pipefail
cd "$(dirname "$0")/.."

port=8765
package=com.ahmedalsinan.dumbcallerid
update_job=1 # Updates.JOB_ID
adb=(adb ${1:+-s "$1"})
work=$(mktemp -d)

cleanup() {
  [ -n "${server:-}" ] && kill "$server" 2> /dev/null || true
  "${adb[@]}" reverse --remove "tcp:$port" 2> /dev/null || true
  rm -rf "$work"
}
trap cleanup EXIT

echo "Building the current version and a newer one (v99.0)..."
./gradlew -q assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk "$work/current.apk"
./gradlew -q assembleDebug -PtryVersionCode=9999 -PtryVersionName=99.0
cp app/build/outputs/apk/debug/app-debug.apk "$work/dumb-caller-id-v99.0.apk"
./gradlew -q assembleDebug # leave the normal debug build in place

cat > "$work/latest.json" << JSON
{"tag_name": "v99.0", "assets": [{"name": "dumb-caller-id-v99.0.apk",
  "browser_download_url": "http://127.0.0.1:$port/dumb-caller-id-v99.0.apk"}]}
JSON

echo "Installing the current version..."
if ! "${adb[@]}" install -r "$work/current.apk" > /dev/null; then
  echo "Install failed. If a release build is installed, uninstall it first (different signing key):"
  echo "  ${adb[*]} uninstall $package"
  exit 1
fi
"${adb[@]}" shell pm grant "$package" android.permission.POST_NOTIFICATIONS 2> /dev/null || true
# Forget earlier tries, so v99.0 is announced again.
"${adb[@]}" shell run-as "$package" rm -f shared_prefs/updates.xml

echo "Serving the fake release on port $port..."
"${adb[@]}" reverse "tcp:$port" "tcp:$port" > /dev/null
python3 -m http.server "$port" --bind 127.0.0.1 --directory "$work" > /dev/null 2>&1 &
server=$!
sleep 1

echo "Running the app's update check..."
"${adb[@]}" shell am start -n "$package/.MainActivity" > /dev/null # starts the app, which schedules the check
sleep 2
"${adb[@]}" shell cmd jobscheduler run -f "$package" "$update_job" > /dev/null

cat << 'DONE'

On the phone you should now see the notification "Update available: v99.0".
  1. Tap it: the app opens with the update banner.
  2. Tap Update. The first time, allow "Install unknown apps" for the app, go back and tap Update again.
  3. Confirm Android's install screen. Settings in the app should then show Version 99.0.

Press Enter when done (this stops the local server).
DONE
read -r
echo "Done. To go back to the normal version: ${adb[*]} uninstall $package, then install the release APK."
