# Contributing to HourLog

Use JDK 17 and Android SDK 36. Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`; run `:app:connectedDebugAndroidTest` for storage or UI changes on a clean emulator.

Keep changes focused. New interface text must be added to both default English and French Android resources. Time calculations belong in `domain/`, use exact minutes and preserve the start-date attribution rule. Financial calculations use `BigDecimal`.

Do not add network access, collection, advertising or user accounts. Preserve confirmations before deletion, restoration and counting intentional overlaps. Add tested Room/backup migrations before changing stored formats. Never enable destructive database migration.

Use synthetic data in tests and screenshots. Do not include personal exports, signing keys, passwords, device identifiers or local SDK paths. Explain behavior and relevant validation in pull requests.
