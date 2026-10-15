# Social Battery — Galaxy Watch6 (Wear OS)

## Utilisation
- **Mode AUTO** (par défaut) : le niveau se calcule seul (heure, pas, fréquence cardiaque vs ta base de repos). Le % s'affiche sous l'emoji.
- **Tap** sur l'écran ou **tourner la lunette** : tu forces un niveau (mode MANUEL).
- **Appui long** : retour au mode AUTO.
- Textes, couleurs, emojis : `Level.kt`. Formule de calcul : `Store.kt`.

## 1. Compiler (PC)
1. Installer **Android Studio** (gratuit) : developer.android.com/studio
2. *File > Open* → choisir le dossier `SocialBattery`.
3. Attendre la synchronisation Gradle (quelques minutes la 1re fois). Si Android Studio propose de mettre à jour une version de dépendance, accepter.

## 2. Préparer la montre
1. *Paramètres > À propos de la montre > Informations sur le logiciel* → taper **7 fois** sur *Version du logiciel*.
2. *Paramètres > Options de développement* → activer **Débogage ADB** et **Débogage sans fil**.
3. Montre et PC sur le **même Wi‑Fi**.

## 3. Installer
1. Sur la montre : *Débogage sans fil > Associer un nouvel appareil* → note l'IP:port et le code.
2. Dans le terminal d'Android Studio :
   ```
   adb pair IP:PORT_ASSOCIATION   (saisir le code)
   adb connect IP:PORT_AFFICHÉ_SUR_ÉCRAN_DÉBOGAGE_SANS_FIL
   ```
3. La montre apparaît en haut d'Android Studio → bouton **Run ▶**.
4. Au 1er lancement, **autoriser** capteurs et activité physique.

## 4. Installer le cadran (badge + heure à la place de ton cadran)
1. En haut d'Android Studio, choisir le module **watchface** dans la liste des configurations.
2. *Edit Configurations… > Launch* → **Nothing** (le cadran n'a pas d'écran à ouvrir), puis **Run ▶**.
3. Sur la montre : **appui long sur le cadran actuel** → faire défiler → **Social Battery**.
4. Si le badge reste vide : appui long > *Personnaliser* > complications → choisir **Social Battery** pour les 2 emplacements.

Au repos (AOD), le cadran passe en noir avec le mot du niveau et l'heure. Lever le poignet ou toucher l'écran → badge en couleur. Toucher le badge ouvre l'appli pour changer de niveau à la main.
Le cadran se met à jour au changement de niveau, et au minimum toutes les 5 min.

## Notes
- L'écran reste allumé tant que l'appli est ouverte (consomme de la batterie montre).
- Estimation indicative, pas une mesure médicale. La base de repos s'affine après 1-2 jours de port.
