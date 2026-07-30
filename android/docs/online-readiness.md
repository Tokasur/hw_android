# Le moteur est-il prêt pour le multijoueur en ligne ?

Audit réalisé pendant la 0.3.0, en corrigeant le desync des replays. À relire au
démarrage du chantier multijoueur — il ne propose aucune implémentation, il
dresse l'état des lieux et la liste des pièges.

## Pourquoi replay = réseau

C'est le même mécanisme, à un détail de configuration près.

- En relecture, la trame `TD` en tête de flux fait passer **toutes** les équipes
  en `ExtDriven` (`hedgewars/uTeams.pas`, `chAddTeam`). En réseau, la commande
  `erdriven` fait la même chose, équipe par équipe
  (`hedgewars/uCommandHandlers.pas`, `chTeamLocal`).
- Une équipe `ExtDriven` fait basculer `chNextTurn` de « j'émets mon checksum
  dans une trame `'N'` » à « je compare mon checksum à celui reçu, sinon
  *Desync detected* » (`hedgewars/uCommandHandlers.pas`). C'est le **même code**
  qui protège les deux modes.
- Côté frontend, le tampon de démo et le tampon réseau du client desktop sont
  remplis au même endroit avec les mêmes octets (`QTfrontend/game.cpp`). Notre
  `DemoRecorder` est donc structurellement le futur relais réseau.

Conclusion : tout ce qui a été corrigé pour les replays vaut pour le réseau.

## Ce que le correctif 0.3.0 valide

Le desync ne venait ni du protocole, ni de l'enregistreur, ni du portage Android :
**la couche Rust/Pascal du moteur amont perdait le signe des nombres à virgule
fixe**.

`hwf_raw` et `hwf_with_sign` (`rust/lib-hwengine-future/src/fpnum.rs`)
recevaient un `bool` Rust. Rust marque ce paramètre `zeroext` et lit le registre
entier ; Free Pascal, dont le `boolean` fait un octet, n'écrivait que l'octet de
poids faible et laissait le reste du registre sale. Le masque de signe renvoyé
valait donc n'importe quoi — mesuré : `0xFFFFFFFF_FFA2E400` au lieu de `0`.

Portée réelle du bug, par site d'appel :

- `GetRandomf` et `rndSign` (`hedgewars/uRandom.pas`) — toute la physique aléatoire ;
- `AngleSin` / `AngleCos` et `WithSign` (`hedgewars/uFloat.pas`) — **toute la
  trigonométrie** du moteur (visée, tirs, cordes) ;
- les constantes elles-mêmes : `_0`, `_PI`, `hwPi`, `cAirPlaneSpeed`,
  `cBombsSpeed` (`hedgewars/uConsts.pas`).

Le déchet variait selon ce que le processus avait fait juste avant l'appel.
Chaque partie restait cohérente avec elle-même (d'où un jeu qui « marche »),
mais une relecture n'empruntait pas le même chemin de code que
l'enregistrement — donc pas le même déchet, donc une divergence dès les
premiers ticks, amplifiée par `AddRandomness(CheckSum)` à chaque tick.

Correctif : les drapeaux qui entrent dans Rust sont désormais des entiers
32 bits (`LongBool` côté Pascal, `u32` côté Rust), comparés à zéro. Sur x86-64
comme sur AArch64, écrire un registre 32 bits met à zéro la moitié haute — le
déchet ne peut plus passer.

**Ce que cela garantit désormais, et qui est indispensable au réseau :**

1. Le moteur est déterministe : deux exécutions du même flux d'entrée
   produisent le même état, donc le même checksum.
2. Le détecteur de desync (`'N'`) est fiable. Avant, il criait au loup en
   permanence ; on ne pouvait donc pas s'en servir pour diagnostiquer un vrai
   problème réseau.
3. Le contrat « en `ExtDriven`, le moteur n'émet aucune trame synchronisée »
   est vérifié par le harnais (`android/scripts/host-replay-harness.py` compte
   ces trames). Mesuré sur une partie complète rejouée : **une seule**
   exception, la trame `'N'` d'un octet — sans checksum — envoyée en toute fin
   de partie par `gtATFinishGame` (`hedgewars/uGearsHandlersMess.pas`), qui
   n'est pas conditionnée par `isExternalSource`. Sans effet en relecture,
   mais deux choses à retenir pour le réseau : un client en rejeu la
   pousserait sur le réseau, et à la réception `uIO.pas` lit 4 octets de
   checksum dans une trame qui n'en contient pas (`lastTurnChecksum` prend
   n'importe quoi — inoffensif ici car plus aucune comparaison ne suit).
4. Les horodatages du protocole (`GameTicks`, époques `hiTicks` via `'#'`) sont
   respectés à l'identique entre enregistrement et rejeu.

**Attention** : ce correctif change le comportement du moteur (les signes sont
maintenant justes). Les démos enregistrées avec un moteur antérieur à la 0.3.0
ne se rejouent pas — et un client 0.3.0 ne peut pas jouer en réseau avec un
client antérieur. Toute future négociation de version doit inclure la version
du moteur, pas seulement le numéro de protocole (60).

## Risques restants, spécifiques au réseau

Aucun n'est corrigé ici : ils ne se manifestent qu'avec plusieurs machines.

1. **Contenu absent = mort du moteur, pas avertissement.** Les forts, thèmes,
   chapeaux et voix des packs téléchargeables ne sont pas couverts par la
   vérification de synchronisation du terrain. Un fort dont l'image manque est
   une erreur **fatale** pendant la génération du terrain
   (`hedgewars/uLand.pas`, `MakeFortsMap`). Comme le portage propose un
   téléchargeur de contenu que le PC n'a pas, deux joueurs peuvent facilement
   ne pas avoir les mêmes packs. Le frontend desktop s'en protège en excluant
   le contenu additionnel de ses tirages automatiques. À traiter avant la
   première partie publique.
2. **Locales mixtes = desync garanti si un script Lua est chargé.**
   `ScriptLocaleReader` (`hedgewars/uScript.pas`) **écrase** `CheckSum` avec un
   condensé du fichier de langue chargé. Deux joueurs en langues différentes
   partant sur un style de jeu scripté divergeront immédiatement. Le portage
   contourne déjà le problème pour les replays en enregistrant la locale dans
   un fichier voisin de la démo ; en réseau, il faudra imposer une locale de
   partie.
3. **File d'attente des commandes : gel silencieux.** `NetGetNextCmd`
   (`hedgewars/uIO.pas`) ne dépile une commande que si `GameTicks` est
   **exactement** égal à son horodatage. Une commande dont le tick est passé
   reste bloquée en tête de file pour toujours, et tout le reste derrière. Le
   garde-fou amont était doublement mort (assertion toujours vraie, et
   comparaison inversée) : on l'a réparé en journalisation non fatale, donc le
   symptôme est maintenant visible dans `Logs/game0.log`. Le cas se produit
   quand une trame est émise après un `'N'` dans le même tick — candidats
   connus : `setweap` via le menu d'armes, `/skip` via `TeamGoneEffect`.
4. **Trames non synchronisées réordonnables.** `'h'` (hogsay), `'s'` et `'b'`
   (chat) partent hors du tampon d'émission et doublent les commandes en
   attente (`hedgewars/uIO.pas`, `SendIPC`). Sans chat ni taunt, sans effet ;
   avec, l'ordre du flux peut ne pas être celui de l'exécution.
5. **`lastTurnChecksum` est unique.** Un seul checksum attendu à la fois : le
   modèle suppose un seul propriétaire de tour. Rien à changer pour du 1 contre 1,
   à revérifier pour les spectateurs et le rattrapage (`fastUntilLag`).
6. **Pause et absence.** `chPause` gèle `DoGameTick` localement ; en réseau la
   pause doit être une décision partagée. Voir aussi `isAFK`, `teamgone` /
   `teamback` (`'F'`/`'G'`/`'f'`/`'g'`) et `hasGone`, qui neutralise justement le
   contrôle de desync pour l'équipe partie.
7. **Époques `hiTicks`.** Au-delà de 65 536 ticks, l'avance du compteur haut
   dépend de la réception de la trame `'#'` (`hedgewars/uGears.pas`). Un client
   qui rejoint en cours de partie doit recevoir ces trames dans l'ordre, sinon
   tout son décodage temporel est décalé de 65 536 ticks.
8. **Divergence délibérée du portage.** `GameConnection.send()`
   (`android/app/.../engine/GameConnection.kt`) n'enregistre pas `epause` ni
   `eforcequit`, contrairement au desktop. C'est **volontaire et meilleur** : les
   trames `'e'` d'une démo sont exécutées au chargement, donc un `eforcequit`
   enregistré ferait quitter la relecture immédiatement. À conserver pour le
   relais réseau : les commandes de session locale ne doivent pas partir sur le
   réseau.

## Outillage laissé en place

- `android/scripts/host-replay-loop.sh` + `host-replay-harness.py` : compile un
  moteur desktop (FPC pur, ni Qt ni CMake) et enregistre puis rejoue une partie
  100 % bots en parlant le protocole exact du frontend Android. `record`,
  `replay` (socket, le chemin de production), `file` (chargement par le moteur
  lui-même), `cycle`. `--stop-after-turns N` coupe l'enregistrement pour une
  boucle d'environ 45 secondes.
- Traceur de synchronisation dans le moteur, compilé uniquement avec
  `-dSYNCDEBUG` (donc absent des `.so` livrés) : une ligne par tick avec le
  checksum, un condensé de l'état du générateur aléatoire et les compteurs
  d'appels (`hedgewars/uGears.pas`, `hedgewars/uRandom.pas`). Diffé entre
  enregistrement et relecture, il donne le tick exact de la première divergence.
  C'est l'outil qui a trouvé le bug ; il servira tel quel pour un desync réseau.

## À remonter à l'amont Hedgewars

1. **Le bug ABI booléen Rust/Pascal** — le plus important, et tout frais chez
   eux : « Reimplement gear checksum » (19 juin 2026) est à la fois leur tête
   actuelle et notre point de fork. Toute leur réécriture du moteur en Rust
   passe par cette frontière.
2. Le garde-fou mort de `NetGetNextCmd` (assertion toujours vraie + comparaison
   inversée) et le gel silencieux de la file.
3. `chTimerU`, exécutable pendant une relecture ou une partie réseau alors qu'il
   escalade en commandes checksummées, sans garde `ExtDriven`.
4. `ScriptLocaleReader` qui écrase le checksum avec un condensé dépendant de la
   langue : desync réseau garanti entre joueurs de langues différentes.
5. `FixedPoint` (`rust/fpnum/src/lib.rs`) n'a pas de `#[repr(C)]` alors que le
   `HWFloat` `#[repr(C)]` qui l'encapsule est transmuté vers Pascal — correct
   aujourd'hui par chance, à verrouiller.
6. Le moteur ne laisse au frontend aucune fenêtre pour finir son travail de fin
   de partie (voir la section précédente) : `SendIPCAndWaitReply` au lieu de
   `SendIPC` sur le `'q'` final réglerait le problème à la source.
7. `invertCursor` (`hedgewars/uTouch.pas`) était un état mis en cache écrit par
   le seul `ProcessTouch`, qui ne tourne pas quand une démo ou le réseau pilote
   les équipes — d'où une caméra tactile inversée en relecture. Corrigé chez
   nous en évaluant la condition au moment du geste ; touche aussi le réseau.
8. `isOnCurrentHog` (`hedgewars/uTouch.pas`) déréférence `CurrentHedgehog^.Gear`
   sans garde, atteignable au doigt entre deux tours.
9. `if SendHealthStatsOn then` sans `begin`/`end` dans `uStats.pas SendStats` :
   seule la première affectation est conditionnée. Sans effet aujourd'hui (le
   drapeau vaut `true` par défaut), mais ce n'est pas ce que le code dit.
10. Priorité d'opérateurs dans `doStepGenericFaller`
   (`hedgewars/uGearsHandlersMess.pas`) : `and` liant plus fort que `or`, le
   repositionnement aléatoire se déclenche bien plus souvent que l'intention
   apparente du code. Non corrigé ici — c'est un choix de gameplay amont, et le
   modifier changerait les trajectoires.

## La fin de partie n'attend personne (corrigé côté frontend, à revoir pour le réseau)

Le moteur ne se synchronise avec le frontend à aucun moment de sa sortie : il
envoie `'q'`, passe en `gsExit`, démonte tout (`freeEverything`) puis appelle
`halt()` — quelques dizaines de millisecondes plus tard le processus est mort,
tantôt proprement, tantôt sur le SIGSEGV connu d'`exit()`. Le frontend, lui,
avait encore ses écritures de fin de partie à faire. La 0.3.0 s'en sort en
posant statistiques et issue sur disque **dès l'arrivée du classement**, soit
~3 s avant le `'q'` (le fondu au noir de `gtATFinishGame`).

Pour le réseau, la même absence de poignée de main mérite mieux : un client qui
doit encore transmettre ses dernières trames au serveur au moment où le moteur
se coupe les perdra. La correction propre côté amont serait de remplacer
`SendIPC(_S'q')` (`hedgewars/uGearsHandlersMess.pas`) par un
`SendIPCAndWaitReply` : le moteur attendrait le pong du frontend, donc la fin
réelle de son travail. À proposer en même temps que le reste.

## Le moteur est-il identique d'un appareil à l'autre ?

C'est la question qui décide si le réseau peut marcher. Réponse : **oui entre
appareils ARM, non garanti face à x86**.

Ce qui joue en notre faveur : la simulation n'utilise pas de nombres à virgule
flottante. Tout passe par `hwFloat`, un format à virgule **fixe** (des entiers),
et c'est précisément pour ça qu'il existe. Les entiers donnent le même résultat
partout. Le moteur vérifie en plus le terrain au démarrage (trame `'M'`,
`landcheck`) — deux clients qui ne génèrent pas le même sol le savent tout de
suite.

**L'exception trouvée**, et elle est réelle : `LineCollisionTest`
(`hedgewars/uCollisions.pas`) calcule son point d'impact en virgule flottante,
avec une variable déclarée `extended` :

```pascal
realT := hwFloat2Float(t) / hwFloat2Float(dirNormSqr);
cX := round(hwFloat2Float(oX) + realT * hwFloat2Float(dirX));
```

Or `extended` **n'a pas la même précision selon l'architecture** — vérifié au
compilateur : 10 octets (80 bits, x87) sur x86_64, 8 octets (double) sur
aarch64. Un arrondi différent, c'est un pixel d'écart sur le point d'impact,
donc des dégâts différents, donc un desync. Le chemin est atteint par le fusil
à pompe (`ShotgunLineHitHelp`) et les armes qui poussent en ligne
(`AmmoShoveLine`) — des armes courantes.

Conséquences pratiques :

- **arm64 ↔ arm64, arm64 ↔ armeabi-v7a** : sans risque de ce côté, les deux
  ramènent `extended` à `double`. C'est le cas de tous les vrais téléphones.
- **ARM ↔ x86_64** (émulateur, quelques Chromebooks) et **ARM ↔ PC** : risque
  réel. À écarter du multijoueur, ou à corriger.
- Correction possible de notre côté : déclarer `realT` en `double` plutôt qu'en
  `extended`. Nos trois ABI deviendraient alors cohérentes entre elles — mais
  notre moteur ne calculerait plus comme celui du PC, ce qui ferme la porte au
  cross-play. C'est un choix à faire au début du chantier, pas avant.

Ce qui reste non vérifié : aucune partie n'a encore été rejouée d'une
architecture à l'autre. Le test décisif est simple et à faire tôt — enregistrer
une démo sur un appareil, la rejouer sur un autre d'ABI différente, et vérifier
l'absence de `Desync detected` dans `Logs/game0.log`.

## Tests à faire dès la première partie en réseau

Ces cas ne se voient qu'avec deux machines et ne sont couverts par aucun test
automatique. À dérouler avant toute partie publique.

1. **Clients de langues différentes** (demandé par Darryl) : un joueur en
   français, un en anglais, sur un **style de jeu scripté** (le mécanisme ne se
   déclenche que si un script Lua est chargé — voir risque n° 2). Les deux
   doivent finir la partie sans desync. Si ça desynchronise, la locale de partie
   devra être imposée par l'hôte, exactement comme une démo impose la sienne.
2. **Contenu inégal** : un joueur avec un pack téléchargé, l'autre sans. Vérifier
   qu'on avertit **avant** de lancer, parce que le moteur, lui, meurt pendant la
   génération du terrain (risque n° 1).
3. **Déterminisme entre architectures** : enregistrer une démo sur un appareil,
   la rejouer sur un autre d'ABI différente, et vérifier `Logs/game0.log`. À
   faire avant d'écrire la moindre ligne de réseau — c'est le socle. Voir la
   section sur `extended` ci-dessus : entre appareils ARM ça devrait passer,
   face à x86 non.
4. Un client qui quitte en cours de partie, et un qui met en pause.

## Questions ouvertes pour le chantier multijoueur

- Serveur officiel (cross-play avec le PC) ou serveur dédié ? L'amont fournit
  `rust/hedgewars-server`, protocole 60.
- Le relais côté Kotlin : `DemoRecorder` en version bidirectionnelle temps réel.
  Quelle bibliothèque de socket, quel thread, quelle politique de reconnexion ?
- UX de lobby au tactile, et UX de desync (que montrer au joueur quand ça arrive
  quand même ?).
- Spectateurs et rattrapage (`fastUntilLag`), reconnexion en cours de partie.
- Politique de version : le correctif ABI rend les moteurs antérieurs
  incompatibles. Négocier une version de moteur en plus du protocole.
