# Appearance corrections and publication handoff

Local review completed on 2026-10-06. The appearance corrections were first validated locally on 1.2.1/code 4. The user subsequently requested GitHub publication; the delivery version is 1.2.2/code 5. See [VALIDATION.md](VALIDATION.md) for release validation and delivery evidence.

## Problem and resulting behavior

Appearance and accent choices previously remained drafts until the user reached Save farther down Settings. Unrelated invalid settings could prevent that save. The lock screen also used an independent default Material theme.

- Light, Dark and System selections now apply and persist immediately. System tracks actual Android light/dark changes, while explicit modes override them.
- Five named color presets, valid custom hexadecimal colors and default-color reset apply and persist immediately. Custom input accepts an optional `#` and lowercase letters. Incomplete or invalid input leaves the last valid color applied and displays validation feedback.
- One theme covers the main app and lock screen, with matching Android status/navigation icon brightness. Material color roles now consistently follow the chosen accent, including inverse surfaces, with improved text contrast.
- Appearance writes are serialized and update only the selected appearance fields. A concurrent general Save preserves the latest appearance without automatically saving unrelated pay/reminder drafts. Existing repository preference recovery remains in place.
- English/French strings and usage documentation describe the automatic saving behavior. Backup format and database schema are unchanged.

## Validation

- 80 JVM tests passed.
- All 42 selected Android feature tests passed: AppearanceUiTest (7), SecurityDeviceTest (11), UserPreferencesUiTest (7), LockUiTest (1), HourLogUiTest (9), StorageAndReminderTest (6), ExportImportDeviceTest (1).
- New tests exercise actual system mode changes and rendered light/dark backgrounds, explicit overrides, durable preferences and recreation, preset/custom colors and reset, invalid-input handling, ordered writes and draft isolation, lock theme and bar icon flags, and seven text/background contrast pairs across 11 colors in both modes.
- Debug, Android test APK and optimized unsigned release builds passed; lint has zero errors. Existing dependency/style advisories remain.
- Actual light/dark, purple-accent and lock-screen screenshots were visually reviewed. Lock capture protection is unchanged in production; the test briefly clears and restores the secure flag solely to capture a synthetic locked screen.

Evidence: ignored `artifacts/appearance-fix/qa/build-final.txt`, `android-final.txt` and PNG captures. Testing used the isolated Android 16 ARM64 HourLogTest emulator. Manufacturer-specific physical phone behavior has not been tested.

## Release preparation

The remote repository and latest stable release were checked before preparing 1.2.2/code 5. The optimized APK preserves the original signing certificate. Installation over the actual published optimized 1.2.1 APK, without uninstalling, preserved synthetic work entries, preferences and the same scheduled reminder ID. The framework-only ReleaseProbe confirmed preservation on both versions and after an actual emulator reboot.

Release assets are `HourLog-v1.2.2.apk` and `SHA256SUMS`, following [RELEASING.md](RELEASING.md). The Obtainium link and human/Codex acknowledgement remain in README. Local release evidence is in ignored `artifacts/v1.2.2/qa/`; no private signing material or user backup belongs in release assets.
