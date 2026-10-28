# Dumb Caller ID

When a number that isn't in your contacts calls, a small popup appears over the call screen. One tap looks the number up through a Telegram bot. Android 10 or newer, in English and Arabic.

The app has no servers, analytics or ads. A number leaves your phone only when you tap Look up, and then it goes to the Telegram bot you chose, which a third party runs. Its only other network use is a daily check for updates on GitHub. See [PRIVACY.md](PRIVACY.md).

## Install

1. Download the latest `dumb-caller-id-*.apk` from [Releases](../../releases), open it and tap Install. If asked, allow the app you opened it from (Browser/Files/Telegram) to install apps.
2. If Play Protect warns you, tap "More details", then "Install anyway".
3. Open the app and follow the setup steps to the end.
4. Open Telegram, go to @dlilcomApp_bot, and tap Start once.

The app checks for new versions once a day. When one is out, you get a notification (if you allowed it during setup) and the home screen shows it; tap **Update** there to install it (the first time, Android asks you to allow the app to install updates). Your settings stay.

## Something not working?

In the app, open Settings → "Copy diagnostics" and paste it into a [bug report](../../issues/new/choose). It shows your phone model, Android version and which setup steps are done, and contains no phone numbers.

## How it works

- `CallScreener` receives incoming calls from unknown numbers once the app holds Android's caller ID role, so it never reads the call log or contacts.
- `CallOverlay` draws the popup over the call screen; `Telegram` opens the bot with the number filled in.
- `Setup` and `Device` decide which setup steps a phone needs (Android version, maker-specific battery and popup settings); `SettingsLauncher` opens their screens without crashing when a phone refuses one.

## Building

Needs JDK 17 and the Android SDK (platform 36).

```sh
./gradlew lintDebug testDebugUnitTest   # lint (any warning fails the build) and unit tests
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
```

How to test on a phone, including the update flow without publishing a release: [docs/TESTING.md](docs/TESTING.md).

Every push to `main` and every pull request runs the same checks on GitHub Actions ([build.yml](.github/workflows/build.yml)). Publishing signed releases is covered in [RELEASING.md](RELEASING.md).

## Contributing

Bug reports and pull requests are welcome. Keep changes small, add a unit test for new logic, and run `./gradlew lintDebug testDebugUnitTest` before opening a pull request.

## License

[MIT](LICENSE)
