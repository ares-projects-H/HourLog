# HourLog 1.2.0 validation — 2026-10-06

Package `com.hourlog.app`, code 3, Android 8+ (API 26), target/compile API 36. The optimized signed APK retains the original certificate.

| Check | Result |
|---|---|
| Debug/release compilation and R8 | Passed |
| JVM suite | 77 tests passed, zero failures |
| Android feature suite | 29 tests passed on Android 16 ARM64 emulator |
| Custom lock delay | Seconds/minutes/hours parsing, limits, persistence, existing PIN preservation, background expiry and return-before-expiry passed |
| Update notice | Checkbox persistence, rotation, subsequent dialog suppression, cancellation and re-enabling passed |
| Unlocked captures | FLAG_SECURE cleared after authentication; real screenshot captured and reviewed; lock screen still protected |
| Reminder selection | No initial schedule, incomplete selection rejected, chosen Thursday 09:17 persisted, disabling cancels work |
| Legacy preferences/backups | Old disabled Friday 15:30 cleared; enabled/custom schedules preserved; explicitly selected Friday 15:30 round trip passed |
| Export/import | Real Android document save/read and validated restore passed in the feature suite |
| Lint | Zero errors; dependency/style advisories remain |
| Actual 1.1.0 → optimized 1.2.0 installation | Passed without uninstall; seeded entries, settings and the same scheduled work ID preserved |
| Actual reboot after upgrade | Independent Java ReleaseProbe passed |
| Signature | Matches the original published APK certificate |
| GitHub Actions | Source release commit passed unit tests, lint and debug/release builds |
| Public update client | 2 additional tests passed against published 1.2.0: current-version detection and actual APK download/checksum verification before same-version rejection |
| Public asset readback | GitHub-downloaded APK matches published SHA256SUMS and the original certificate |

The upgrade baseline was seeded on the actual 1.1.0 debug APK in an isolated emulator, then replaced with the signed optimized 1.2.0 APK using `adb install -r`. The framework-only `ReleaseProbe` verified stored entries, settings and scheduled work before and after reboot. Local evidence is in ignored `artifacts/v1.2.0/qa/`.

Circle to Search availability depends on the phone and Google configuration; that service was not exercised on the emulator. Unlocked capture and locked-window protection were tested. WorkManager reminders remain best effort rather than exact alarms. New backups with an unconfigured reminder should be restored using 1.2.0 or newer.

---

# Historical HourLog 1.1.0 validation — 2026-10-06

Package `com.hourlog.app`, code 2, Android 8+ (API 26), target/compile API 36. Optimized signed APK preserves the original certificate so it replaces the initially installed 1.0.0 debug APK.

| Check | Result |
|---|---|
| Debug/release compilation and R8 | Passed |
| JVM suite | 64 tests passed, zero failures |
| Main Android suite | 22 tests plus 1 custom-color UI test passed on Android 16 ARM64 emulator |
| APK version/certificate validation | 4 additional tests passed: accept higher version with same certificate; reject another certificate, downgrade, invalid archive |
| System authentication | 1 additional test passed using the actual Android phone-PIN confirmation dialog |
| Custom PIN/password | Keystore encryption, credential verification, persistent retry cooldown and changes requiring old credential passed |
| App gate | Hidden content, wrong PIN, correct PIN, Activity recreation and FLAG_SECURE passed |
| Export/import | Real ContentResolver file save/read, preview, cancel, full restore and invalid-input preservation passed |
| Adaptive icon | Full green background verified and raster rendered/visually reviewed |
| Custom color UI | Persisted purple color, draft after Activity recreation and reset to default passed; screenshot reviewed |
| Legacy backup | Old preferences without color field default to original green |
| Lint | Zero errors; dependency/style advisories remain |
| Actual 1.0.0 → optimized 1.1.0 installation | Passed without uninstall; entries, preferences and same scheduled work ID preserved |
| Actual reboot of optimized release | Java framework probe passed after emulator reboot; application launched and 10:30 synthetic total was visually reviewed |
| APK certificates | SHA-256 certificate fingerprints match initial APK |
| Public GitHub update client | 2 tests passed against published release: current version detection and real APK download/checksum verification before same-version rejection |
| Public asset readback | GitHub-downloaded APK SHA-256 matches published SHA256SUMS and locally verified APK |

There are 30 passing feature instrumentation tests across the explicitly executed suites, plus the lifecycle seed/baseline checks and the independent optimized-release probe.

The baseline is seeded with `LifecycleDeviceTest#prepareRebootFixture`, verified on 1.0.0, then upgraded with `adb install -r` to the optimized release. Debug instrumentation cannot load renamed Kotlin internals in an R8 release; the independent Java `ReleaseProbe` uses Android APIs to check the private SQLite entries, stored settings and WorkManager ID without depending on renamed classes.

```sh
./gradlew -PhourlogTestRunner=com.hourlog.app.ReleaseProbe :app:assembleDebugAndroidTest
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w com.hourlog.app.test/com.hourlog.app.ReleaseProbe
# Reset the runner for ordinary debug tests:
./gradlew :app:assembleDebugAndroidTest
```

`UpdateNetworkDeviceTest` is opt-in with instrumentation argument `networkUpdateTests=true`; it checks the public stable release API and downloads the published APK through the actual client, verifying SHA-256 before rejecting the already installed version. APK fixture tests require local code-3 files and do not publish those fixtures. Phone credential tests require a configured test-device credential and actual system confirmation.

Local evidence is in ignored `artifacts/v1.1.0/qa/`. The distributed APK is `artifacts/v1.1.0/HourLog-v1.1.0.apk`; `SHA256SUMS` covers it. No signing key or user backup is uploaded. Published builds retain the original development certificate for update continuity; CI-generated debug APKs are not release updates.

A physical phone's fingerprint sensor, manufacturer restrictions and all external document providers were not exercised. Biometric availability comes from Android; the phone-credential path was tested on the emulator. App lock controls UI access and protected captures; Room is in private Android storage, without separate database encryption. Manual exports are plain documents. Update checking/downloading uses Internet only when requested; the app transmits no work/salary data. Android confirms installation.

---

# Initial 1.0.0 validation — 2026-10-06

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
