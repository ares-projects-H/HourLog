# Delivery validation — 2026-10-06

Identity: **HourLog**, application ID `com.hourlog.app`, version 1.0.0 (code 1), min SDK 26, target/compile SDK 36.

## Verified

| Check | Result |
|---|---|
| Debug compilation | Passed; installable development-signed APK generated |
| Release compilation | Passed; R8 optimized unsigned APK generated |
| JVM business/backup/CSV tests | 50 passed, zero failures |
| Android instrumentation suite | 16 passed on Android 16 / API 36 ARM64 emulator |
| Android Lint | Zero errors; 18 dependency-update advisories |
| Required calculation examples | Covered by passing unit tests |
| UI create/edit/paid and unpaid breaks | Passed |
| Deletion and overlap confirmation | Passed |
| Unsaved break draft after Activity recreation | Passed |
| Room reopening with periods and breaks | Passed |
| JSON round trip and invalid restore preservation | Passed |
| Committed restore preference-journal recovery | Passed |
| Real notification and subsequent scheduled occurrence | Passed |
| Actual Android emulator reboot | Separate prepare/reboot/verify phases passed; entries, preferences and the same scheduled work ID persisted |
| PDF generation | One-page ordinary report and 12-page stress report generated; first/last/sample pages rendered and visually reviewed |
| Optimized release runtime | Locally signed with the development key for testing; installed and launched without crash |
| English / French and light / dark UI | Screenshots visually reviewed with synthetic data |
| APK signature and manifest | Debug signature verified; correct name/package/version; no INTERNET permission |
| Gradle wrapper | Official Gradle 9.3.1 distribution SHA-256 pinned |

## Commands used

```sh
gradle :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
adb -s emulator-5556 shell am instrument -w com.hourlog.app.test/androidx.test.runner.AndroidJUnitRunner
# Also run as separate phases with an actual adb reboot between them:
adb -s emulator-5556 shell am instrument -w -e class 'com.hourlog.app.LifecycleDeviceTest#prepareRebootFixture' com.hourlog.app.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 reboot
adb -s emulator-5556 shell am instrument -w -e class 'com.hourlog.app.LifecycleDeviceTest#verifyRebootFixture' com.hourlog.app.test/androidx.test.runner.AndroidJUnitRunner
```

The first command used the installed Gradle 9.3.1 distribution, JDK 17 and the local Android SDK, with an isolated project Gradle cache. The included wrapper selects the same distribution. `:app:connectedDebugAndroidTest` was also exercised successfully before adding the two-phase lifecycle fixture and screenshot/rotation checks.

Local `artifacts/qa/` holds build output, final instrumentation output, reboot-phase results and report samples. It is deliberately excluded from Git, along with SDK configuration, build caches, emulator state, keys and APKs. Screenshots checked into `docs/screenshots/` contain only synthetic time entries.

## Delivery and limits

- `artifacts/HourLog-v1.0.0-debug.apk`: directly installable; development signature.
- `artifacts/HourLog-v1.0.0-release-unsigned.apk`: optimized release; needs your private signing key.
- `artifacts/HourLog-v1.0.0-source.zip`: source/documentation/wrapper archive without caches, local paths or signing files.
- `artifacts/SHA256SUMS`: checksums for those delivery files.

A physical phone and manufacturer battery restrictions were not tested. The reminder is best effort through WorkManager, not an exact alarm. Document saving and sharing use Android APIs; each external document provider/sharing app remains device dependent. A production release signature and any GitHub/Play publication are not part of this local delivery. XLSX and automatic periodic backup are omitted as permitted by the specification; CSV/PDF and manual backup/restore are implemented.
