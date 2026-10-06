Je veux que tu développes une application Android complète permettant de saisir, calculer, consulter, sauvegarder et exporter des heures de travail.

L’objectif est d’obtenir une application moderne, très simple à utiliser, rapide, configurable et utilisable entièrement hors ligne.

Je veux pouvoir générer un APK Android installable directement sur un téléphone.

Le projet doit être suffisamment propre, générique et documenté pour pouvoir éventuellement être publié sur GitHub et utilisé par d’autres personnes ayant des horaires, règles de paie et seuils d’heures supplémentaires différents.

# 1. Technologies

Utiliser :

- Kotlin
- Jetpack Compose
- Material Design 3
- Room Database
- ViewModel
- Repository
- DataStore pour les préférences utilisateur
- WorkManager ou la meilleure solution Android moderne adaptée aux notifications planifiées
- API modernes Java/Kotlin pour les dates et heures

L’application doit fonctionner entièrement hors ligne.

Ne pas utiliser :

- serveur;
- cloud obligatoire;
- compte utilisateur;
- API distante;
- collecte de données;
- analytics;
- publicité.

Toutes les informations restent localement sur le téléphone, sauf lorsque l’utilisateur choisit volontairement d’exporter ou de sauvegarder ses données.

# 2. Fonction principale : saisir les heures travaillées

Je veux pouvoir sélectionner une date et ajouter une ou plusieurs périodes de travail pour cette journée.

Exemple :

06:30 → 15:00

Une journée peut également être coupée en plusieurs périodes.

Exemple :

06:30 → 15:00

puis

19:00 → 21:00

L’application doit comprendre qu’il s’agit de deux périodes de travail appartenant à la même journée.

Elle doit additionner automatiquement les périodes.

Dans cet exemple :

06:30 → 15:00 = 8 h 30

19:00 → 21:00 = 2 h 00

Total = 10 h 30

Je dois pouvoir ajouter autant de périodes que nécessaire dans une même journée.

# 3. Périodes traversant minuit

L’application doit gérer correctement les horaires traversant minuit.

Exemple :

22:00 → 02:00

Cela doit être calculé comme :

4 heures de travail.

Ne jamais interpréter cela comme une durée négative.

Dans l’interface de saisie, lorsqu’une heure de fin est antérieure à l’heure de début, proposer ou détecter clairement que la période se termine le lendemain.

Exemple :

Début :
Lundi 22:00

Fin :
Mardi 02:00

Prévoir une logique fiable pour les périodes traversant plusieurs dates.

# 4. Pauses

Ajouter un système de pauses configurable.

Dans mon cas personnel, les pauses sont normalement payées.

Mais l’application doit permettre plusieurs comportements afin d’être utilisable par d’autres personnes.

Lors de la création ou modification d’une période de travail, permettre de choisir :

- aucune pause;
- pause payée;
- pause non payée.

Exemple :

06:30 → 15:00

Pause : 30 minutes

Si pause payée :

Temps travaillé payé = 8 h 30.

Si pause non payée :

Temps travaillé payé = 8 h 00.

Permettre plusieurs pauses dans une même période si nécessaire.

Exemple :

Pause 1 : 15 minutes payées
Pause 2 : 30 minutes non payées

Les pauses doivent pouvoir être :

- ajoutées;
- modifiées;
- supprimées.

Créer dans les paramètres une préférence par défaut :

Pause par défaut :

- payée;
- non payée;
- demander à chaque fois.

Pour mon utilisation personnelle, la valeur par défaut initiale peut être :

Pause payée.

# 5. Modification des entrées

Je dois pouvoir :

- ajouter une période;
- modifier une période;
- supprimer une période;
- modifier une journée précédente;
- ajouter des heures oubliées;
- ajouter ou supprimer des pauses;
- corriger une erreur plusieurs semaines plus tard.

Toujours demander confirmation avant une suppression.

Détecter les périodes qui se chevauchent.

Exemple invalide :

06:30 → 15:00
14:30 → 17:00

Afficher un avertissement clair.

Ne pas bloquer une situation inhabituelle sans explication : si nécessaire, permettre à l’utilisateur de confirmer explicitement qu’un chevauchement est volontaire.

# 6. Écran Aujourd’hui

Créer un écran principal extrêmement simple.

Exemple :

Aujourd’hui
Mardi 6 octobre

06:30 → 15:00
8 h 30

19:00 → 21:00
2 h 00

TOTAL
10 h 30

Ajouter un bouton principal très visible :

+ Ajouter des heures

Permettre également de naviguer vers la journée précédente ou suivante.

# 7. Saisie rapide

La saisie des heures doit être très rapide.

Utiliser un Time Picker adapté à Android.

Permettre idéalement :

- sélection de l’heure de début;
- sélection de l’heure de fin;
- ajout rapide d’une pause;
- duplication d’une journée précédente.

Ajouter une fonction :

« Copier les heures d’hier »

et éventuellement :

« Copier une journée »

pour les personnes ayant souvent le même horaire.

# 8. Vue Semaine

La semaine commence toujours le lundi et se termine le dimanche.

Afficher les sept jours :

Lundi
Mardi
Mercredi
Jeudi
Vendredi
Samedi
Dimanche

Exemple :

Lundi        8 h 00
Mardi       10 h 30
Mercredi     8 h 00
Jeudi        7 h 30
Vendredi     8 h 00
Samedi       —
Dimanche     —

TOTAL       42 h 00

Permettre la navigation :

← semaine précédente

semaine actuelle

semaine suivante →

Afficher également :

- total travaillé;
- heures normales;
- heures supplémentaires;
- nombre de jours travaillés;
- moyenne par journée travaillée;
- salaire brut estimé.

# 9. Heures supplémentaires

Par défaut :

les heures travaillées au-delà de 40 heures dans une semaine sont considérées comme heures supplémentaires.

Mais cette règle doit être entièrement configurable.

Créer dans les paramètres :

Seuil d’heures supplémentaires :

40 h / semaine

Permettre à l’utilisateur de modifier cette valeur.

Exemples possibles :

35 h
37,5 h
40 h
44 h

Ne jamais coder 40 heures en dur dans toute l’application.

La logique doit utiliser la valeur configurable.

Exemple :

Total semaine : 45 h

Seuil : 40 h

Résultat :

Heures normales : 40 h
Heures supplémentaires : 5 h

# 10. Multiplicateur d’heures supplémentaires

Ajouter également un multiplicateur configurable.

Par défaut :

1,5×

Exemples possibles :

1×
1,5×
2×

Créer le paramètre :

Multiplicateur heures supplémentaires

Valeur par défaut :

1,5×

Cette valeur doit être utilisée dans l’estimation du salaire.

# 11. Salaire

Créer une option permettant d’entrer le taux horaire.

Exemple :

Taux horaire :
40,00 $ / h

Permettre à l’utilisateur de choisir sa devise.

Par défaut :

CAD / dollar canadien.

Mais ne pas coder l’application spécifiquement pour le Canada.

Prévoir des devises configurables comme :

CAD
USD
EUR
GBP

ou utiliser les paramètres régionaux du téléphone lorsque possible.

Calculer :

Salaire normal estimé

+

Salaire des heures supplémentaires

=

Salaire brut estimé.

Exemple :

40 h × 40 $ = 1 600 $

5 h supplémentaires × 40 $ × 1,5 = 300 $

Total brut estimé :

1 900 $

Afficher clairement qu’il s’agit d’une estimation brute avant impôts, retenues, assurances ou autres déductions.

# 12. Format d’affichage des heures

Permettre deux formats.

Format 1 :

Heures et minutes

Exemple :

7:45

ou :

7 h 45

Format 2 :

Heures décimales

Exemple :

7.75

Créer dans les paramètres :

Format des heures :

- Heures/minutes
- Décimal

Important :

7 h 45 doit devenir exactement :

7.75

et non 7.45.

Exemples :

7 h 30 = 7.50
7 h 15 = 7.25
7 h 45 = 7.75
8 h 06 = 8.10

Conserver les calculs internes en minutes ou dans un format précis afin d’éviter les erreurs dues aux nombres flottants.

Le choix heures/minutes ou décimal doit uniquement concerner l’affichage.

# 13. Résumé hebdomadaire

Afficher par exemple :

Total semaine : 45 h 00
Heures normales : 40 h 00
Temps supplémentaire : 5 h 00
Jours travaillés : 5
Moyenne : 9 h 00 / jour

Taux horaire : 40,00 $
Salaire normal : 1 600,00 $
Temps supplémentaire : 300,00 $

Brut estimé :
1 900,00 $

Adapter automatiquement les valeurs selon les réglages utilisateur.

# 14. Notification du vendredi

Créer un rappel hebdomadaire.

Par défaut :

Vendredi à 15:30.

Notification :

« Résumé de tes heures »

Exemple :

« Tu as enregistré 38 h 30 depuis lundi. Vérifie que toutes tes heures sont entrées. »

En touchant la notification, ouvrir directement la semaine actuelle.

Le rappel doit continuer de fonctionner correctement après un redémarrage du téléphone.

# 15. Paramètres des notifications

Créer une section Notifications permettant :

- activation/désactivation;
- choix du jour;
- choix de l’heure.

Valeurs par défaut :

Jour :
Vendredi

Heure :
15:30

Même si vendredi est la valeur par défaut, le jour doit pouvoir être changé.

Exemple :

Jeudi
Vendredi
Samedi
Dimanche

Gérer correctement les permissions de notifications sur les versions Android modernes.

# 16. Historique

Créer un écran Historique.

Exemple :

28 sept. – 4 oct.     40 h 30
21 sept. – 27 sept.   38 h 00
14 sept. – 20 sept.   42 h 15

Afficher éventuellement :

40 h normales
0 h 30 supplémentaires
Brut estimé : XXX $

En sélectionnant une semaine, ouvrir son détail.

# 17. Calendrier

Ajouter une vue calendrier simple permettant de voir les journées travaillées.

Chaque journée peut afficher discrètement son total.

Exemple :

6
8 h 30

7
10 h 00

8
—

Ne pas transformer l’application en logiciel complexe de planification.

Le calendrier sert principalement à retrouver rapidement une journée.

# 18. Export CSV

Permettre d’exporter les heures au format CSV.

Inclure au minimum :

- date;
- début;
- fin;
- durée;
- pauses;
- pauses payées;
- pauses non payées;
- durée payée;
- heures normales;
- heures supplémentaires.

Créer également un export résumé par semaine.

Le CSV doit pouvoir être ouvert avec :

- Excel;
- Google Sheets;
- LibreOffice;
- Numbers.

# 19. Export Excel

Si raisonnablement possible sans ajouter une dépendance trop lourde, permettre également l’export XLSX.

Sinon, un CSV parfaitement compatible Excel est acceptable.

Privilégier la stabilité et la simplicité.

# 20. Export PDF

Permettre de générer un rapport PDF.

L’utilisateur doit pouvoir choisir :

- une semaine;
- plusieurs semaines;
- une période personnalisée.

Le PDF doit contenir :

- période sélectionnée;
- heures par jour;
- périodes travaillées;
- pauses;
- total;
- heures normales;
- temps supplémentaire;
- taux horaire;
- salaire brut estimé.

Le PDF doit avoir une présentation propre et professionnelle.

# 21. Partage

Après création d’un CSV ou PDF, permettre d’utiliser le menu de partage Android.

Exemples :

- courriel;
- Google Drive;
- OneDrive;
- Dropbox;
- Messages;
- autre application installée.

Ne pas imposer un service particulier.

# 22. Sauvegarde locale

Créer une fonction :

Sauvegarder mes données

Produire un fichier contenant :

- toutes les périodes;
- toutes les pauses;
- paramètres;
- taux horaire;
- seuil d’heures supplémentaires;
- multiplicateur;
- préférences d’affichage.

Le fichier doit pouvoir être conservé ailleurs par l’utilisateur.

# 23. Restauration

Créer une fonction :

Restaurer une sauvegarde

L’utilisateur sélectionne le fichier.

Avant restauration :

- vérifier la validité du fichier;
- afficher les informations de la sauvegarde;
- demander confirmation.

Exemple :

Sauvegarde :
2 octobre 2026

150 journées
325 périodes de travail

Restaurer ?

Prévoir une stratégie permettant de gérer de futures versions de la structure de données.

# 24. Sauvegarde automatique

Ajouter éventuellement une option :

Créer automatiquement une sauvegarde périodique locale.

Mais ne jamais envoyer automatiquement de données vers Internet.

Si cette fonction complexifie trop la première version, construire l’architecture pour pouvoir l’ajouter plus tard.

# 25. Données

Utiliser Room.

Créer des modèles suffisamment propres pour représenter par exemple :

WorkEntry

avec :

- id;
- date;
- startDateTime;
- endDateTime;
- createdAt;
- updatedAt;
- note optionnelle.

BreakEntry :

- id;
- workEntryId;
- duration;
- paid;
- createdAt;
- updatedAt.

Les noms exacts peuvent être adaptés si une architecture plus propre est préférable.

# 26. Notes

Permettre facultativement d’ajouter une petite note à une journée ou période.

Exemples :

« Formation »

« Heures supplémentaires »

« Travail Airbus »

« Intervention soir »

Cette fonction doit rester discrète et facultative.

# 27. Paramètres

Créer un écran Paramètres regroupant :

Apparence

- clair;
- sombre;
- automatique.

Heures

- heures/minutes;
- décimal.

Pauses

- payées par défaut;
- non payées par défaut;
- demander.

Paie

- taux horaire;
- devise;
- seuil temps supplémentaire;
- multiplicateur du temps supplémentaire.

Notifications

- activer;
- jour;
- heure.

Données

- exporter CSV;
- exporter PDF;
- sauvegarder;
- restaurer.

# 28. Navigation

Navigation principale extrêmement simple.

Utiliser par exemple une barre de navigation inférieure :

Aujourd’hui
Semaine
Historique

Et une icône Paramètres en haut.

Le calendrier peut être intégré à Historique ou accessible via une icône.

Éviter les menus complexes.

# 29. Design

Utiliser Material Design 3.

Le design doit être :

- moderne;
- minimaliste;
- professionnel;
- rapide;
- très lisible.

Utiliser :

- cartes simples;
- grands boutons;
- bons espacements;
- hiérarchie visuelle claire;
- icônes cohérentes;
- petites animations uniquement lorsqu’elles améliorent l’expérience.

Supporter :

- thème clair;
- thème sombre;
- thème système.

L’application doit donner l’impression d’être une vraie application Android publiée sur le Play Store et non un prototype scolaire.

# 30. Localisation

Préparer correctement les chaînes de caractères Android afin de permettre plusieurs langues.

Créer au minimum :

Français
Anglais

Détecter la langue du téléphone par défaut.

Permettre éventuellement de sélectionner manuellement la langue.

Ne pas mettre le texte de l’interface directement en dur dans les composants Compose.

Utiliser les ressources Android appropriées.

# 31. Formats régionaux

Respecter lorsque possible :

- format 12 h / 24 h;
- séparateurs décimaux;
- devise;
- format de date;
- langue.

Mon utilisation personnelle sera principalement :

Français
Canada / Québec
Format 24 heures

Mais le projet doit rester générique pour GitHub.

# 32. Précision des calculs

Ne jamais utiliser directement des Float ou Double pour stocker les heures travaillées.

Stocker/calculer les durées avec :

- Duration;
- minutes;
- secondes;
- ou une autre représentation exacte.

Utiliser Decimal/BigDecimal ou une méthode fiable pour les calculs monétaires.

Les montants financiers ne doivent pas souffrir d’erreurs classiques de nombres flottants.

# 33. Fuseaux horaires et changement d’heure

Gérer correctement :

- changement heure été/hiver;
- fuseau horaire;
- périodes traversant minuit.

Éviter de simplement soustraire des chaînes de caractères représentant les heures.

Utiliser les API modernes de date et heure.

# 34. Validation

Tester les cas suivants.

Cas 1 :

06:30 → 15:00

Résultat :

8 h 30.

Cas 2 :

06:30 → 15:00
19:00 → 21:00

Résultat :

10 h 30.

Cas 3 :

22:00 → 02:00

Résultat :

4 h 00.

Cas 4 :

06:30 → 15:00
Pause non payée 30 minutes

Résultat payé :

8 h 00.

Cas 5 :

06:30 → 15:00
Pause payée 30 minutes

Résultat payé :

8 h 30.

Cas 6 :

45 heures dans une semaine
Seuil supplémentaire = 40 h

Résultat :

40 h normales
5 h supplémentaires.

Cas 7 :

7 h 45 en mode décimal

Résultat :

7.75.

Cas 8 :

7 h 45 en mode heures/minutes

Résultat :

7:45.

# 35. Tests

Créer des tests unitaires couvrant au minimum :

- durée simple;
- périodes multiples;
- traversée de minuit;
- pause payée;
- pause non payée;
- plusieurs pauses;
- chevauchement;
- total quotidien;
- total hebdomadaire;
- semaine lundi-dimanche;
- temps supplémentaire;
- changement du seuil de temps supplémentaire;
- multiplicateur supplémentaire;
- conversion heures/minutes vers décimal;
- calcul salarial;
- export/import sauvegarde.

Ajouter des tests UI lorsque cela apporte une vraie valeur.

# 36. Architecture

Utiliser une architecture propre mais sans sur-ingénierie.

Par exemple :

UI Compose

↓

ViewModel

↓

Use cases / logique métier si nécessaire

↓

Repository

↓

Room / DataStore

Séparer clairement :

- stockage;
- logique métier;
- calcul de paie;
- calcul du temps;
- interface;
- notifications;
- export/import.

# 37. GitHub

Préparer le projet pour pouvoir être publié publiquement sur GitHub.

Créer :

README.md

.gitignore

LICENSE si approprié, de préférence MIT sauf raison contraire.

Documenter :

- installation;
- compilation;
- architecture;
- fonctionnalités;
- permissions Android;
- stockage;
- confidentialité;
- format de sauvegarde;
- contribution.

Ne mettre aucune donnée personnelle dans le dépôt.

Ne mettre aucune clé secrète.

# 38. Confidentialité

L’application ne doit pas envoyer les heures ou informations salariales vers un serveur.

Ajouter dans le README une section :

Privacy

Expliquant que :

- les données restent localement;
- aucune donnée n’est collectée;
- aucun compte n’est requis;
- les exports sont déclenchés manuellement par l’utilisateur.

# 39. Nom de l’application

Nom retenu : **HourLog**.

- App name : `HourLog`
- Project name : `HourLog`
- Package name : `com.hourlog.app`

# 40. APK

Configurer Gradle afin de générer correctement :

- APK debug;
- APK release.

À la fin, indique précisément :

1. comment compiler le projet;
2. comment lancer les tests;
3. comment générer le debug APK;
4. comment générer le release APK;
5. où se trouvent les APK;
6. comment signer le release APK;
7. comment installer l’APK sur un téléphone Android.

# 41. README

Créer un README complet contenant :

- présentation du projet;
- captures d’écran ou emplacements prévus pour celles-ci;
- fonctionnalités;
- technologies;
- prérequis;
- compilation;
- installation;
- architecture;
- confidentialité;
- import/export;
- sauvegarde;
- notifications;
- règles de temps supplémentaire configurables;
- contribution;
- licence.

# 42. Méthode de développement

Ne te contente pas de produire des morceaux de code ou des exemples.

Construis réellement l’application complète.

Procède dans cet ordre :

1. inspecter l’environnement;
2. créer/configurer le projet Android;
3. créer les modèles;
4. créer Room;
5. créer DataStore;
6. créer la logique de dates/heures;
7. créer la logique des pauses;
8. créer la logique du temps supplémentaire;
9. créer la logique salariale;
10. ajouter les tests de logique métier;
11. créer l’écran Aujourd’hui;
12. créer la saisie/modification;
13. créer la vue Semaine;
14. créer Historique;
15. créer le calendrier;
16. créer Paramètres;
17. ajouter les notifications;
18. ajouter CSV;
19. ajouter PDF;
20. ajouter sauvegarde/restauration;
21. ajouter localisation FR/EN;
22. améliorer UI/UX;
23. compléter les tests;
24. compiler;
25. corriger les erreurs;
26. générer l’APK;
27. compléter le README.

Après chaque grande étape, exécute les tests et vérifie que le projet compile.

Ne laisse pas d’erreurs de compilation ou de fonctions essentielles sous forme de TODO si elles peuvent être implémentées maintenant.

# 43. Priorités

Si certaines fonctions secondaires compliquent excessivement la première version, prioriser dans cet ordre :

Priorité 1 :
- saisie heures;
- journées coupées;
- périodes après minuit;
- pauses;
- total journalier;
- total semaine;
- historique;
- Room.

Priorité 2 :
- temps supplémentaire;
- salaire;
- paramètres;
- notifications;
- format décimal/heures-minutes.

Priorité 3 :
- CSV;
- PDF;
- sauvegarde/restauration;
- calendrier.

Priorité 4 :
- améliorations UX;
- options avancées;
- fonctions destinées à GitHub.

Même lorsqu’une fonction est reportée, préparer une architecture qui permettra de l’ajouter proprement sans refaire toute l’application.

# 44. Critère final

Le projet est considéré terminé lorsque :

- il compile;
- les tests essentiels passent;
- je peux saisir mes heures;
- je peux avoir plusieurs périodes dans une journée;
- les périodes traversant minuit fonctionnent;
- les pauses fonctionnent;
- les semaines lundi-dimanche sont correctes;
- les heures supplémentaires sont configurables;
- le salaire estimé fonctionne;
- les notifications fonctionnent;
- l’historique fonctionne;
- mes données restent après fermeture/redémarrage;
- je peux exporter/sauvegarder les données;
- un APK installable est généré.

Lorsque tu rencontres une décision technique mineure non spécifiée, ne m’interromps pas inutilement.

Choisis la solution Android moderne la plus simple, robuste et maintenable.

Pour une décision importante qui modifierait le comportement utilisateur, explique le choix avant de l’implémenter.