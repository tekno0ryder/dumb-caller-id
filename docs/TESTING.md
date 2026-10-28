# Testing

## Unit tests and lint

```sh
./gradlew lintDebug testDebugUnitTest
```

- Lint fails the build on any warning; the accepted exceptions are listed, with reasons, in `app/lint.xml`.
- Unit tests run on your computer, with no phone needed: plain JUnit for the logic, and [Robolectric](https://robolectric.org) for code that uses Android (settings screens, the popup window, notifications). Robolectric simulates Android 14, so behaviour that only newer Android versions enforce still needs a real phone (see below).
- GitHub Actions runs the same on every push and pull request.

## On a phone

Install a debug build with `./gradlew installDebug`. It's signed with a different key than releases, so uninstall a release build of the app first.

Worth checking after changes to calls, setup or the popup:

- a call from a number **not** in your contacts shows the popup, and Look up opens the bot with the number filled in;
- a call from a saved contact shows no popup;
- on a locked phone, Look up asks to unlock first;
- the popup closes by itself when the call ends;
- setup goes through every step without crashing, in English and Arabic.

Try the newest Android version you have: Android 17, for example, refused a background job the tests didn't flag, and the app crashed on start.

## The update flow

To see the update notification and install work without publishing a release, connect a phone with USB or wireless debugging and run:

```sh
scripts/try-update.sh            # or: scripts/try-update.sh <adb serial> with several phones connected
```

It installs the current debug build, serves a "newer" v99.0 from your computer as if it were the latest release, and runs the app's update check. Then tap the notification, then Update, as a user would. Debug builds look for updates on your computer; release builds only ever check GitHub.

Afterwards the phone has v99.0, which a real release can't update: uninstall it before installing a release.
