# PokeIsland Helper

Mod **client** Fabric pour Minecraft 1.21.1 + Cobblemon (serveur PokeIsland).
Le serveur ne reçoit rien et ne voit rien : tout se passe dans ton jeu.

## Ce que fait le mod

### 1. Surbrillance Pokédex (touche **U**)
Les Pokémon sauvages qui manquent à ton Pokédex ont un contour coloré :
- **rouge** : espèce jamais vue ;
- **jaune** : espèce vue mais pas capturée (désactivable).

Par défaut, un Pokémon ne brille que s'il est à moins de 32 blocs **et que tu le vois vraiment**
(pas de détection à travers les murs). Voir « Règlement » plus bas.

### 2. Panneau de combat / raid (touche **O**)
Pendant un combat (raids compris), un panneau s'affiche à droite avec :
- le nom et les types de l'adversaire ;
- ses faiblesses (×4, ×2), résistances (×½, ×¼) et immunités ;
- **« À envoyer »** : les 3 meilleurs Pokémon de ton équipe contre lui, avec leur meilleure
  attaque (STAB compris) et ce qu'ils risquent d'encaisser.

**Boss téracristallisé** : le client ne connaît pas toujours le type Téracristal.
Tape `/praid tera Acier` (ou `/praid tera Feu Vol`) pour forcer les types de l'adversaire.
Ça se remet à zéro tout seul à la fin du combat, ou avec `/praid reset`.

### 3. Équipe cible (touche **P** ou `/pteam`)
Tu décris l'équipe que tu veux construire, et l'écran compare avec ton équipe actuelle :
✔ / ✘ pour la nature, le talent, l'objet, chaque IV et chaque EV.

```
/pteam add Carchacrok
/pteam nature 1 Jovial
/pteam talent 1 Peau Dure
/pteam objet 1 Mouchoir Choix
/pteam iv 1 31 31 31 x 31 31        (PV Atq Déf AtqS DéfS Vit — x = peu importe)
/pteam ev 1 4 252 0 0 0 252
/pteam list      /pteam remove 1      /pteam clear      /pteam reload
```
Les noms marchent en français ou en anglais (Jovial = Jolly, Peau Dure = roughskin).
Tout est enregistré dans `.minecraft/config/pokeislandhelper-team.json`, modifiable à la main.

### 4. Conditions d'évolution
- En regardant un Pokémon (sauvage ou à toi), le panneau affiche ses évolutions et leurs conditions :
  niveau, objet, échange (+ objet tenu), bonheur, moment de la journée, attaque connue, biome, etc.
- **Touche J** (ou `/pevo` tout seul) : écran « Évolutions de l'équipe » avec, pour chaque Pokémon
  de ton équipe, ses évolutions et sa progression : ✔ condition remplie, ✘ pas encore
  (niv. 12/16, bonheur 120/160, objet tenu, attaque connue…), sans marque = à vérifier toi-même
  (moment de la journée, biome…). Une évolution entièrement remplie s'affiche en vert.
- `/pevo <espèce>` (ex. `/pevo Évoli`) : toutes ses évolutions, formes régionales comprises,
  et de quel Pokémon il évolue.
- Les données viennent des fichiers de Cobblemon installés chez toi : si PokeIsland a modifié une
  évolution uniquement côté serveur, le mod affiche la version de base de Cobblemon.
- `"evoInPanel": false` dans `config/pokeislandhelper.json` pour ne pas l'afficher dans le panneau.

## Réglages
`.minecraft/config/pokeislandhelper.json` (créé au premier lancement) :
couleurs, distance, ligne de vue, position du panneau (`battlePanelRight`, `battlePanelY`),
nombre de recommandations. Les touches se changent dans Options → Commandes.

## Compiler le mod (une fois)

Il faut **Java 21** (JDK, par ex. Temurin 21) et une connexion internet.

```bash
# Dans le dossier du projet
./gradlew build          # macOS / Linux
gradlew.bat build        # Windows
```
Le fichier du mod est créé dans `build/libs/pokeisland-helper-0.1.0.jar`.

Avant de compiler, ouvre `gradle.properties` et mets dans `cobblemonVersion` la version de Cobblemon
de PokeIsland (le nom du fichier `cobblemon-fabric-….jar` dans le dossier `mods` du launcher).

Pour tester en solo avant le serveur : `./gradlew runClient` lance un Minecraft avec Cobblemon et le mod.

## Installer
Copie le `.jar` dans le dossier `mods` de l'instance du launcher PokeIsland.
Le mod a besoin de Fabric API et Fabric Language Kotlin, déjà présents puisque Cobblemon les utilise.

## Règlement : à vérifier avant de jouer avec
Le serveur autorise les mods en plus, mais demande au staff (ticket Discord) si celui-ci est accepté,
en particulier la surbrillance. Elle respecte la ligne de vue par défaut ; si tu mets
`requireLineOfSight` à `false`, les contours passent à travers les murs, ce qu'un staff peut considérer
comme un avantage.

## Si quelque chose ne marche pas
Tous les accès à Cobblemon sont dans `CobbleBridge.kt`. Si une version de Cobblemon a renommé une
fonction, la fonctionnalité concernée se coupe (sans faire planter le jeu) et le journal
`logs/latest.log` affiche une ligne `PokeIsland Helper … impossible` qui dit laquelle.
Le seul appel que je n'ai pas pu vérifier sur les sources de Cobblemon est la lecture de l'équipe
(`CobblemonClient.storage.myParty.slots`) : si « À envoyer » et l'écran Équipe restent vides,
c'est celui-là qu'il faut regarder en premier.
