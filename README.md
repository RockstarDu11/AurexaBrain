# AurexaBrain - HikaBrain pour Aurexalium (Spigot 1.8.8)

Deux equipes sur des plateformes, un pont au milieu, un lit a chaque bout.
Touche le lit adverse pour marquer. Premier a 5 points gagne.
Stuff fer (armure + epee), pioche, sandstone, pommes d'or.
Tomber dans le vide = mort + respawn sur la plateforme de ton equipe.
Coins donnes via une commande console (config.yml).

## Compiler
Il faut un JDK 8+ et Maven :

    mvn clean package

Le plugin est dans `target/AurexaBrain.jar` -> mets-le dans `plugins/`.

## Creer une map
Types : `1v1` (2 joueurs), `2v2` (4 joueurs), `arene` (2 a 8 joueurs).

1. `/hb create ma-map 1v1`
2. Va dans le lobby d'attente : `/hb setlobby ma-map`
3. Sur la plateforme rouge : `/hb setspawn ma-map red`
   Sur la plateforme bleue : `/hb setspawn ma-map blue`
4. Tiens-toi SUR le lit rouge (debout dessus) : `/hb setbed ma-map red`
   Tiens-toi SUR le lit bleu : `/hb setbed ma-map blue`
   (la zone du lit = 3x3 blocs autour de toi, pas de selection a faire)
5. Selectionne TOUTE la zone de jeu (pont, plateformes, du bas jusqu'au dessus) :
   `/hb pos1`, `/hb pos2`, `/hb setregion ma-map`
   Le bas de cette selection = hauteur de mort (tomber dessous = mort).
   On ne peut poser/casser des blocs que dans cette zone.
6. `/hb info ma-map` verifie que tout est en place (tout doit etre vert).

## Jouer
`/hb join ma-map`, `/hb leave`, `/hb list` (aliases : /hika, /brain)

## Permission
`aurexabrain.admin` : configurer les arenes (op par defaut).
