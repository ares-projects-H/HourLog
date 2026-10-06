# Installer et compiler HourLog

Nom de l’application et du projet : **HourLog**. Package : `com.hourlog.app`. Android 8.0 ou plus récent.

## Installer l’APK livré

1. Téléchargez `HourLog-v1.2.1.apk` depuis [la release GitHub](https://github.com/ares-projects-H/HourLog/releases/latest), ou transférez-le depuis le dossier local `artifacts/v1.2.1`.
2. Ouvrez le fichier et autorisez l’installation depuis votre gestionnaire de fichiers si Android le demande.
3. Ouvrez HourLog. Dans Paramètres, saisissez votre taux horaire et vérifiez le seuil hebdomadaire, le multiplicateur et la devise.
4. Activez le rappel et enregistrez les paramètres pour accorder la permission de notification.

L’APK publié est optimisé et conserve la signature de développement initiale : il peut remplacer la version 1.0.0 déjà installée sans désinstallation. Conservez la même clé pour les futures versions. Une nouvelle clé nécessite une migration ou une nouvelle installation.

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

## Protection et couleurs

Dans **Paramètres → Protection**, choisissez le code PIN personnel (6–12 chiffres), un mot de passe (8–128 caractères), ou l’authentification du téléphone. Sur Android 11 et plus, la biométrie est proposée par Android si elle est disponible et enregistrée ; le code/schéma/mot de passe du téléphone reste utilisable. Sur Android 8–10, utilisez le code du téléphone. L’application ne connaît jamais le code du téléphone ni vos empreintes.

Vous pouvez permettre l’authentification du téléphone en secours du code personnel. Conservez votre code : il n’existe pas de réinitialisation distante. Les sauvegardes d’heures n’incluent pas le verrou et ne le désactivent pas lors d’une restauration. Les fichiers exportés ne sont pas chiffrés. Le verrou protège l’accès à l’interface ; la base reste dans le stockage privé Android.

Saisissez un délai personnalisé en secondes, minutes ou heures, jusqu’à 24 heures. 0 verrouille immédiatement. Vous pouvez modifier le délai en laissant les nouveaux champs de code vides pour conserver votre PIN ou mot de passe actuel ; le code actuel reste demandé. Un démarrage à froid est toujours verrouillé. Les captures et l’aperçu sont autorisés après déverrouillage ; l’écran verrouillé et les champs de code restent protégés. Circle to Search peut ainsi accéder à l’écran sur un téléphone compatible. Les rappels masquent les totaux lorsque la protection est configurée.

Dans **Paramètres → Couleur**, choisissez une palette ou saisissez six caractères hexadécimaux, puis **Enregistrer**. Le bouton de couleur par défaut restaure le vert d’origine. Le mode clair/sombre reste indépendant.

## Mises à jour

Depuis la version 1.1.0 : **Paramètres → Mises à jour → Vérifier**, puis télécharger et installer. Android peut demander d’autoriser les installations depuis HourLog ; revenez ensuite dans l’application et appuyez à nouveau sur Installer. L’installation reste confirmée par Android. Cochez **Ne plus afficher ce message** puis **Continuer** pour mémoriser ce choix. La confirmation ne sera plus affichée, même après fermeture de l’app. Annuler ne mémorise pas la case. Le bouton **Réafficher la confirmation des mises à jour** permet de revenir au fonctionnement initial. Le contrôle se fait uniquement à votre demande et ne transmet aucun horaire ni salaire. Une connexion Internet est nécessaire pour contacter GitHub.

Pour Obtainium, ajoutez ce dépôt :

```text
https://github.com/ares-projects-H/HourLog
```

Le raccourci intégré peut aussi l’ajouter à Obtainium si celui-ci est installé. Les futures releases conserveront le même package et doivent conserver le même certificat pour remplacer votre installation. Évitez les APK temporaires de CI : leur clé peut être différente.

## Rappel hebdomadaire

Le rappel est désactivé au départ, sans jour ni heure préchoisis. Activez le bouton, choisissez le jour puis l’heure et enregistrez. Aucun rappel ne peut être activé tant que ce choix est incomplet. Désactiver le bouton et enregistrer annule le rappel. La mise à jour conserve les horaires déjà activés ; l’ancien vendredi 15:30 désactivé par défaut est retiré. Les sauvegardes récentes contenant un rappel non configuré doivent être importées avec HourLog 1.2.0 ou plus récent.
