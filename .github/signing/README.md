# CI release signing

`openbahn-ci.jks` is a **public** debug-style key used only so GitHub Release APKs share one signature and can be upgraded in place (`versionCode` must still increase each release).

Passwords are in `ci.properties`. Do not use this key for Play Store or high-trust distribution.

F-Droid builds from source with its own signing.

## F-Droid custom repo index signing

`fdroid-repo.p12` signs the **repository index** (`index-v1.jar`) for the GitHub Pages binary repo — not the APK files themselves. Passwords are in `fdroid-repo.properties`. CI copies this keystore into `fdroid/keystore.p12` before each `fdroid update`.
