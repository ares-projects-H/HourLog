# Publishing an HourLog update

Repository: https://github.com/ares-projects-H/HourLog

1. Increment `versionCode` and `versionName` in `app/build.gradle.kts`. Preserve application ID `com.hourlog.app` and the signing certificate used by the installed app. Test Room migrations if the schema changes. Published 1.1.x APKs use the original local development certificate for continuity with the first installation; preserve it privately. GitHub CI does not hold that key and CI debug APKs are not update artifacts.
2. Run unit tests, Lint, instrumentation and an upgrade preserving existing entries. Build an optimized signed release with the four local signing environment variables documented in README. Never upload the key or passwords.
3. Commit reviewed source, documentation and validation results, then push `main`. Wait for Android CI to pass.
4. Prepare the exact release names, replacing `1.1.0` with the new version:

```sh
mkdir -p artifacts/publish
cp app/build/outputs/apk/release/app-release.apk artifacts/publish/HourLog-v1.1.0.apk
cd artifacts/publish
shasum -a 256 HourLog-v1.1.0.apk > SHA256SUMS
```

5. Verify the APK signature with Android SDK `apksigner verify --print-certs`, then publish one APK plus checksums. Prepare release notes in a plain local file before this command:

```sh
gh release create v1.1.0 HourLog-v1.1.0.apk SHA256SUMS \
  --repo ares-projects-H/HourLog --target main \
  --title 'HourLog 1.1.0' --notes-file /path/to/release-notes.md
```

6. Download the public assets again and verify SHA-256. Check the updater from the previous installed version and verify installation/data retention. The updater accepts stable tags in `vMAJOR.MINOR.PATCH` format and assets named `HourLog-vMAJOR.MINOR.PATCH.apk` and `SHA256SUMS`. Obtainium can track the same repository. Do not publish fixture APKs used for tests or multiple installable variants in a stable release.

Releases and source are public. Use synthetic data only in screenshots, tests and issue reports. Android installation requires user confirmation; no silent installs or unattended network checks are implemented.
