# Releasing

How to publish a signed APK. Only the maintainer needs this; building and testing is in the [README](README.md).

## The signing key

Releases are signed with your own key, which is never committed. Every update must be signed with the same key, or Android won't install it over the previous version, so keep a backup of the key and its passwords somewhere other than your computer.

To create a key the first time:

```sh
keytool -genkeypair -keystore release.jks -storetype PKCS12 -alias dumbcallerid \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Dumb Caller ID"
```

## One-time setup

**On your machine:** put `release.jks` in the project folder and a `keystore.properties` next to it (both are git-ignored):

```properties
storeFile=../release.jks
storePassword=…
keyAlias=dumbcallerid
keyPassword=…
```

**On GitHub:** add four repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | `base64 -i release.jks \| pbcopy` copies it without showing it |
| `SIGNING_STORE_PASSWORD` | `storePassword` from `keystore.properties` |
| `SIGNING_KEY_ALIAS` | `keyAlias` |
| `SIGNING_KEY_PASSWORD` | `keyPassword` |

## Publishing a version

1. In `app/build.gradle.kts`, raise `versionCode` by 1 and set `versionName` (e.g. `1.2`). Android only installs an update with a higher `versionCode`.
2. Commit, then push a tag that matches `versionName`:
   ```sh
   git tag v1.2 && git push origin v1.2
   ```
3. [release.yml](.github/workflows/release.yml) checks the tag matches, runs lint and tests, signs the APK, and publishes it as a GitHub release named `dumb-caller-id-v1.2.apk`.

To build a signed APK on your machine instead: `./gradlew assembleRelease` (output in `app/build/outputs/apk/release/`).

## Google Play

The app is distributed as an APK, not through Google Play. If that ever changes: `./gradlew bundleRelease` builds the `.aab` Play expects, and the battery setup step's `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission needs reviewing first, since Play restricts it.
