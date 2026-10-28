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

1. In `app/build.gradle.kts`, raise `versionCode` by 1 and set a new `versionName` (e.g. `1.1`). Android only installs an update with a higher `versionCode`.
2. Commit and push to `main`.

[release.yml](.github/workflows/release.yml) runs on every push to `main`. When `versionName` has no release yet, it runs lint and tests, signs the APK, tags the commit `v1.1` and publishes `dumb-caller-id-v1.1.apk` as a GitHub release. Pushes that keep the same version publish nothing. It stops with an error if `versionName` changed but `versionCode` didn't go up.

If a release fails (e.g. a missing secret), fix it and push again, or use "Run workflow" on the Release workflow in the Actions tab.

To build a signed APK on your machine instead: `./gradlew assembleRelease` (output in `app/build/outputs/apk/release/`).

Publishing on Google Play instead is covered separately in [docs/GOOGLE_PLAY.md](docs/GOOGLE_PLAY.md).
