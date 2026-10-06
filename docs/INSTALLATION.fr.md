# Installer et compiler HourLog

Nom de l’application et du projet : **HourLog**. Package : `com.hourlog.app`. Android 8.0 ou plus récent.

## Installer l’APK livré

1. Transférez `HourLog-v1.0.0-debug.apk` depuis le dossier `artifacts` vers le téléphone.
2. Ouvrez le fichier et autorisez l’installation depuis votre gestionnaire de fichiers si Android le demande.
3. Ouvrez HourLog. Dans Paramètres, saisissez votre taux horaire et vérifiez le seuil hebdomadaire, le multiplicateur et la devise.
4. Activez le rappel et enregistrez les paramètres pour accorder la permission de notification.

L’APK debug utilise une signature de développement. Pour distribuer publiquement l’application, utilisez votre propre clé de signature release.

## Compiler sur ce Mac

```sh
cd /chemin/vers/HourLog
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Le fichier `local.properties` local configure le SDK déjà installé sur ce Mac. Sur une autre machine, ouvrez le projet dans Android Studio pour créer ce fichier avec son propre chemin SDK.

- Debug installable : `app/build/outputs/apk/debug/app-debug.apk`.
- Release sans signature : `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Release avec votre signature : `app/build/outputs/apk/release/app-release.apk`.

## Signer la release

Créez une clé privée hors du dépôt ; les mots de passe seront demandés localement :

```sh
keytool -genkeypair -v -keystore /chemin/prive/hourlog-release.jks \
  -alias hourlog -keyalg RSA -keysize 3072 -validity 10000
```

Configurez localement les quatre variables `HOURLOG_KEYSTORE`, `HOURLOG_STORE_PASSWORD`, `HOURLOG_KEY_ALIAS`, `HOURLOG_KEY_PASSWORD`, puis relancez `./gradlew :app:assembleRelease`. Vous pouvez aussi utiliser « Generate Signed Bundle / APK » dans Android Studio. Ne transmettez ni clé ni mot de passe dans un chat ou dans Git.

Conservez cette clé pour toutes les futures mises à jour. Une signature release différente ne remplace pas une installation debug : sauvegardez vos données avant de désinstaller pour changer de signature.

## Installer par USB

Activez les options développeur et le débogage USB du téléphone, branchez-le et acceptez l’autorisation du Mac :

```sh
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Vérifier sur votre téléphone

- Saisissez 06:30–15:00 puis 19:00–21:00 : total 10:30.
- Saisissez 22:00–02:00 et vérifiez la date de fin au lendemain : total 4:00.
- Ajoutez 30 minutes de pause non payée à 06:30–15:00 : 8:00 payées.
- Fermez puis rouvrez l’application : les entrées doivent rester présentes.
- Exportez CSV et PDF, sauvegardez JSON, puis contrôlez la prévisualisation avant toute restauration.
- Vérifiez les notifications après un redémarrage réel. Android peut retarder le rappel selon l’économie de batterie ; il ne s’agit pas d’une alarme exacte.
