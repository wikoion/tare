#!/usr/bin/env bash
# Builds Tare for a device and installs it on a connected iPhone (USB/Wi-Fi via devicectl,
# or over Tailscale with RoamRun when available).
# Needs iosApp/Config/Local.xcconfig with DEVELOPMENT_TEAM and APP_BUNDLE_ID.
set -euo pipefail
cd "$(dirname "$0")/.."
unset CC CXX
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"

[[ -f iosApp/Config/Local.xcconfig ]] || { echo "Create iosApp/Config/Local.xcconfig from Local.xcconfig.example first." >&2; exit 1; }

ROAMRUN_DEVICE=${ROAMRUN_DEVICE:-iPhone}
roamrun_ready() { command -v roamrun >/dev/null && roamrun status 2>/dev/null | grep -q "^$ROAMRUN_DEVICE: Ready"; }
DEVICE=$(xcrun devicectl list devices 2>/dev/null | awk '/iPhone/ && /available \(paired\)|connected/ && !/unavailable/ {print $3; exit}')
[[ -n "$DEVICE" ]] || roamrun_ready || { echo "No iPhone available: connect it (or bring it up with RoamRun)." >&2; exit 1; }

(cd iosApp && xcodegen generate --quiet)
xcodebuild -project iosApp/Tare.xcodeproj -scheme Tare -configuration Debug \
  -destination generic/platform=iOS -derivedDataPath iosApp/build/DerivedData -allowProvisioningUpdates build \
  | grep -E "error:|BUILD (SUCCEEDED|FAILED)" || true
APP=iosApp/build/DerivedData/Build/Products/Debug-iphoneos/Tare.app
[[ -d "$APP" ]] || { echo "build failed" >&2; exit 1; }
if roamrun_ready; then
  roamrun install "$ROAMRUN_DEVICE" "$APP"
else
  xcrun devicectl device install app --device "$DEVICE" "$APP"
fi
