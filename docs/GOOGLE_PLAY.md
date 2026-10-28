# Publishing on Google Play

The app is distributed as an APK through [GitHub releases](../RELEASING.md), not Google Play. These are the steps if that ever changes.

## Before the first upload

- **Battery permission.** The battery setup step uses `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for its one-tap dialog. Google Play only allows it for some kinds of apps, and caller ID apps aren't clearly among them. To be safe, remove the permission from `AndroidManifest.xml` and the dialog from `BatteryUi` in `Device.kt`: the step then opens the battery settings page, which needs no permission. Also remove the `BatteryLife` exception from `app/lint.xml`.
- **Privacy policy.** Play needs a public link. Use the GitHub page of [PRIVACY.md](../PRIVACY.md).
- **Target SDK.** Play requires a recent `targetSdk` every year; check the current minimum and raise it in `app/build.gradle.kts` if needed.
- **Signing.** Turn on Play App Signing when creating the app and upload with the same key as the GitHub releases (`release.jks`), so APK and Play installs stay updatable.

## Building for Play

```sh
./gradlew bundleRelease   # app/build/outputs/bundle/release/app-release.aab
```

Play takes an `.aab` (Android App Bundle), not the APK. `versionCode` must be higher than every previous upload, the same rule as for APK updates.

## In the Play Console

- **App content forms:** data safety (the app stores recent calls on the phone and shares a number only when the user taps Look up), target audience, ads (none).
- **Sharing with a few people:** the internal testing track lets up to 100 testers by email install from Play, without a public listing or a full review.
- **Permissions:** the app gets incoming numbers through the caller ID role, not the call log, so no restricted call-log declaration should be needed.
