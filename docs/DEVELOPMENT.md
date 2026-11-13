# Development notes

Day-to-day notes for working on Tare. The [README](../README.md) covers setup and building;
[PROTOCOL.md](PROTOCOL.md) covers the scales' Bluetooth protocols.

## Toolchain

- Gradle needs JDK 21: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`.
- If `CC` or `CXX` point at Homebrew's clang, `unset CC CXX` before running `xcodebuild`; it
  breaks Xcode's C compiles. The scripts do this themselves.
- `./gradlew :core:jvmTest` runs the protocol, body-composition, unit and storage tests on the
  JVM in seconds.
- `./gradlew :composeApp:compileKotlinIosSimulatorArm64` type-checks the app's Kotlin without
  an Xcode build.
- `iosApp/Tare.xcodeproj` is generated from `iosApp/project.yml` by `xcodegen generate`. Edit
  `project.yml`, not the project, and don't commit the project.

## Launch arguments

For the simulator, which has no Bluetooth, and for UI checks:

| Argument | Effect |
|---|---|
| `-demoData` | Uses a separate database (`tare-demo.db`) seeded with four months of made-up weigh-ins and a sample profile |
| `-unit kg\|lb\|st` | With `-demoData`: sets the weight unit |
| `-onboarding` | With `-demoData`: starts at the first-run screens |
| `-screen trends\|settings\|about\|log\|pairing` | Opens that screen over Home |
| `-sheet detail\|goal\|log` | Opens a sheet: the latest weigh-in, the goal, or manual entry |
| `-scrollHome` | Scrolls Home to the bottom |

## Running on an iPhone

Copy `iosApp/Config/Local.xcconfig.example` to `iosApp/Config/Local.xcconfig` (gitignored)
and set `DEVELOPMENT_TEAM` and a unique `APP_BUNDLE_ID`. `scripts/install-app.sh` then builds
a Debug app and installs it on a connected iPhone with `devicectl`, or with RoamRun when
that's set up.

## TestFlight

`scripts/testflight.sh` archives a Release build and uploads it with the Apple ID signed in to
Xcode. It needs a paid developer account, `Local.xcconfig`, and an App Store Connect record for
your bundle ID.

- App Store Connect accepts only builds from the iOS 26 SDK or later. The script uses the
  newest side-by-side `/Applications/Xcode-26*.app` (or later), or `DEVELOPER_DIR` if set, so
  an older default Xcode can stay in place.
- The build number is the UTC date and time (for example `20261102.1215`), so every upload is
  higher than the last. Pass an existing `.xcarchive` to upload it again.
- The app is iPhone only (`TARGETED_DEVICE_FAMILY` 1). As a universal app, portrait-only fails
  App Store Connect's iPad multitasking check.
- `iosApp/Tare/PrivacyInfo.xcprivacy` declares the required-reason APIs the binary uses (the
  Kotlin framework's `stat`/`fstat`, and `NSUserDefaults`). App Store Connect rejects an upload
  that uses one it doesn't list, so check again after adding a library.

## Bluetooth log

Settings › Bluetooth log shows this session's lines and can share them. Every line also goes to
`Documents/bluetooth-log.txt` in the app's container (the previous 2 MB moves to
`bluetooth-log.1.txt`). While pairing, the log records every advertisement heard, so a scale
Tare doesn't recognise can be identified from it. To copy it off a phone, including from a
TestFlight build:

```sh
xcrun devicectl device copy from --device <device> --domain-type appDataContainer \
  --domain-identifier <bundle ID> --source Documents/bluetooth-log.txt --destination .
```

## Screenshots

The images in `docs/images/` come from the iOS Simulator with `-demoData`, never from real
readings. To make a new set:

1. Create and boot a simulator, and set its region to the UK so dates and times read
   `12/11` and `13:41`:
   `xcrun simctl spawn <udid> defaults write -g AppleLocale en_GB`, then reboot it.
2. Fix the status bar:
   `xcrun simctl status_bar <udid> override --time 9:41 --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3`.
3. Install a fresh build, so the demo database is seeded again (with a paired ESF-551 that
   never touches Bluetooth), and launch with
   `-demoData -unit kg` plus nothing (home), `-sheet detail`, `-screen trends`,
   `-screen settings` or `-onboarding`. For dark mode, `xcrun simctl ui <udid> appearance dark`.
4. Capture with `xcrun simctl io <udid> screenshot <file>.png`, then scale to half size
   (603 px wide for an iPhone 17 Pro) and keep each file under about 400 KB.

`docs/images/icon.png` is `iosApp/Tare/Assets.xcassets/AppIcon.appiconset/AppIcon.png` at
256 px with rounded corners.
