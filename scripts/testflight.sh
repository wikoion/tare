#!/bin/sh
# Archives a Release build and uploads it to App Store Connect for TestFlight, signed with the Apple ID
# signed in to Xcode (automatic signing makes the distribution certificate and profile the first time).
# Needs iosApp/Config/Local.xcconfig and the app's record in App Store Connect for APP_BUNDLE_ID.
# Usage: scripts/testflight.sh [an archive from an earlier run, to upload it again]
set -eu
cd "$(dirname "$0")/../iosApp"
# Homebrew clang in CC/CXX breaks xcodebuild's C compiles.
unset CC CXX
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
# App Store Connect takes only builds from the iOS 26 SDK or later. This Mac's default Xcode is older (macOS 15
# can't run Xcode 27), so use the newest side-by-side Xcode 26 or later that xcodes installed, if there is one.
NEWEST=$(ls -d /Applications/Xcode-2[6-9]*.app 2>/dev/null | sort -V | tail -1)
export DEVELOPER_DIR="${DEVELOPER_DIR:-${NEWEST:-/Applications/Xcode.app}/Contents/Developer}"
echo "Building with $(xcodebuild -version | head -1) ($DEVELOPER_DIR)"
[ -f Config/Local.xcconfig ] || { echo "Missing iosApp/Config/Local.xcconfig (see Local.xcconfig.example)"; exit 1; }

# Over SSH the login keychain is locked, and codesign can't reach the signing key.
KEYCHAIN="$HOME/Library/Keychains/login.keychain-db"
security show-keychain-info "$KEYCHAIN" 2>/dev/null || security unlock-keychain "$KEYCHAIN"

mkdir -p build/archives
TEAM=$(sed -n 's/^DEVELOPMENT_TEAM *= *//p' Config/Local.xcconfig)
if [ $# -gt 0 ]; then
  ARCHIVE="$1"
else
  # Every upload needs a higher build number: the UTC date and time, e.g. 20261102.1215.
  BUILD=$(date -u +%Y%m%d.%H%M)
  ARCHIVE="build/archives/Tare-$BUILD.xcarchive"
  xcodegen generate --quiet
  xcodebuild archive -project Tare.xcodeproj -scheme Tare -configuration Release \
    -destination generic/platform=iOS -archivePath "$ARCHIVE" -derivedDataPath build/DerivedDataRelease \
    -allowProvisioningUpdates CURRENT_PROJECT_VERSION="$BUILD" -quiet
fi

OPTIONS=build/archives/ExportOptions-testflight.plist
cat > "$OPTIONS" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>method</key><string>app-store-connect</string>
  <key>destination</key><string>upload</string>
  <key>teamID</key><string>$TEAM</string>
  <key>signingStyle</key><string>automatic</string>
  <key>manageAppVersionAndBuildNumber</key><false/>
  <key>uploadSymbols</key><true/>
</dict>
</plist>
PLIST
xcodebuild -exportArchive -archivePath "$ARCHIVE" -exportOptionsPlist "$OPTIONS" \
  -exportPath "${ARCHIVE%.xcarchive}-export" -allowProvisioningUpdates
VERSION=$(/usr/libexec/PlistBuddy -c "Print :ApplicationProperties:CFBundleVersion" "$ARCHIVE/Info.plist")
echo "Uploaded build $VERSION. TestFlight has it once App Store Connect finishes processing (usually 5–15 minutes)."
