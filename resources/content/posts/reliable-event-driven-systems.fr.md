---
{:title "Concevoir des flux evenementiels fiables"
 :slug "concevoir-des-flux-evenementiels-fiables"
 :locale "fr"
 :date "2026-03-28"
 :date-label "28 mars 2026"
 :category "Architecture"
 :tags ["Event-driven" "Fiabilite"]
 :excerpt "Emettre un evenement, c'est facile. Le dur, c'est ce qui se passe quand il arrive deux fois, en retard, ou pas du tout — et qu'il faut comprendre pourquoi."}
---

## Introduction

Quand un systeme devient distribue pour de vrai — pas en theorie, pas sur un diagramme, mais en production avec du vrai trafic — la difficulte change de nature. Le probleme n'est plus de faire circuler les messages. Le probleme, c'est ce qui se passe quand un message arrive deux fois. Quand il arrive en retard. Quand il n'arrive pas du tout et que personne ne s'en rend compte avant le lendemain.

J'aime aborder ces situations avec trois questions simples : est-ce qu'on voit ce qui se passe ? Est-ce qu'on peut rejouer chaque etape ? Et ou se trouve la verite metier quand deux composants ne sont pas d'accord ?

## Rendre les retries ennuyeux

Un retry devrait etre un non-evenement. Si rejouer une tache provoque des effets inattendus — un email envoye en double, un paiement declenche deux fois — c'est que la conception du flux a un probleme, pas l'infrastructure.

Dans la pratique, ca passe par quelques habitudes :

- Des identifiants d'idempotence stables, generes cote producteur, pas cote consommateur
- Des transitions d'etat explicites plutot que des effets de bord implicites
- Des logs structures qui racontent l'histoire complete d'un message, de son emission a son traitement

Le but n'est pas d'empecher les erreurs — c'est de rendre leur correction ennuyeuse. Un incident qu'on corrige en rejouant un batch sans stress, c'est un incident bien gere.

## Observer avant la crise

La plupart des equipes decouvrent leurs metriques pendant un incident. C'est trop tard. Les metriques utiles sont celles qu'on regarde un mardi normal, quand tout va bien, pour reperer les signaux faibles.

Concretement, sur un systeme evenementiel, je surveille :

- La taille des files d'attente, pas juste leur existence
- Le delai entre emission et traitement — pas la moyenne, mais les percentiles hauts
- Le taux d'erreurs temporaires : quand elles deviennent regulieres, elles ne sont plus temporaires

> Une bonne architecture backend, c'est souvent celle qui rend un incident comprehensible avant meme d'avoir a le corriger.

## Conclusion

Quand je concois un flux evenementiel, je ne cherche pas la sophistication. Je cherche des flux qu'on peut lire, des contrats qu'on peut verifier, et des actions qu'on peut rejouer sans peur. C'est moins elegant sur un schema d'architecture. C'est beaucoup plus agreable a exploiter a trois heures du matin.
