import type { Messages } from '../Messages';

// French typography: U+00A0 before ":" and U+202F before "!", "?", ";" and "»" (written as escapes so they stay visible).
// Lines about a player use "{player} : …" or a verb in the third person; a line about my own seat has
// its own `…You` key conjugated for "vous" ("Vous prenez", never "Vous prend").
export const kiloDeMerdeUi: Messages['kiloDeMerdeUi'] = {
  connecting: 'Connexion…',
  back: 'Retour',
  you: 'Vous',
  playerLabel: 'Joueur {n}',
  botLabel: 'Bot {n}',
  ordinal: { one: '{n}er', two: '{n}e', few: '{n}e', other: '{n}e' },
  ranks: { JACK: 'Valet', QUEEN: 'Dame', KING: 'Roi', ACE: 'As' },
  suits: { HEART: 'cœur', DIAMOND: 'carreau', CLUB: 'trèfle', SPADE: 'pique' },
  cardLabel: '{rank} de {suit}',
  cardSelected: '{card}, passée',
  levels: { easy: 'Facile', medium: 'Moyen', hard: 'Difficile' },
  table: {
    roundLine: 'Manche {round} sur {total} · passe {step}',
    countdown: 'Temps restant pour passer : {seconds} s',
    kilos: '{kg} kg',
    kilosLabel: '{player} : {kg} kg',
    pile: 'Tas de kilos',
    pileCount: { one: '{n} carte restante', other: '{n} cartes restantes' },
    slap: 'Taper sur le tas',
    yourCards: 'Votre main',
    opponents: 'Table',
    standings: 'Classement',
    ready: '{player} a choisi une carte',
    readyCountdown: 'Les joueurs absents seront comptés comme prêts dans {seconds} s',
    confirmed: '{player}\u00A0: prêt',
    notConfirmed: '{player}\u00A0: en attente',
    readyYou: 'Vous avez choisi une carte',
    confirmedYou: 'Vous êtes prêt',
    notConfirmedYou: 'Vous êtes en attente',
    forfeited: '{player} a quitté la partie',
    offline: 'Vous êtes hors ligne. Reconnexion…',
  },
  readyGate: {
    button: 'Prêt',
    startPrompt: 'Confirmez que vous êtes prêt\u00A0: la première manche commence quand tout le monde l’est.',
    roundPrompt: 'Quand vous avez vu le résultat, confirmez que vous êtes prêt pour la manche suivante.',
    waitingFor: { one: 'En attente de {n} joueur', other: 'En attente de {n} joueurs' },
  },
  hand: {
    passing: 'Passage…',
    passCard: 'Passer {card}',
    waiting: 'En attente des autres',
    received: 'de {name}',
    revealed: 'Main de {player} : {cards}',
    revealedYou: 'Votre main : {cards}',
  },
  race: {
    slapped: '{name} a tapé ! Tapez !',
    slappedYou: 'Vous avez tapé ! En attente des autres',
    rank: '{n}',
    order: 'Ordre des claques',
  },
  outcome: {
    last: 'Trop tard ! {name} prend {card} ({kg} kg)',
    lastYou: 'Trop tard ! Vous prenez {card} ({kg} kg)',
    falseSlap: 'Fausse claque : {name} prend {card} ({kg} kg)',
    falseSlapYou: 'Fausse claque : vous prenez {card} ({kg} kg)',
    flush: 'Chasse d’eau ! {name} perd tous ses kilos',
    flushYou: 'Chasse d’eau ! Vous perdez tous vos kilos',
    stalled: 'Personne n’a réuni de carré : pas de kilos cette manche',
    voided: 'Un joueur est parti : cette manche ne compte pas',
    noCard: '{name} devait prendre une carte, mais le tas de kilos est vide',
    lastTag: 'Dernier',
  },
  kiloPopover: {
    title: 'Kilos de {player}',
    titleYou: 'Vos kilos',
    none: 'Aucun kilo pour l’instant',
    total: 'Total : {kg} kg',
  },
  standings: {
    player: 'Joueur',
    kilos: 'Kilos',
    cards: 'Cartes',
    toggle: 'Afficher ou masquer le classement',
  },
  end: {
    winner: 'Gagnant : {players}',
    winners: 'Gagnants : {players}',
    reasons: {
      roundsPlayed: 'Les {n} manches ont été jouées. Le joueur qui a le moins de kilos gagne.',
      pileEmpty: 'Le tas de kilos est vide. Le joueur qui a le moins de kilos gagne.',
      forfeit: 'Trop de joueurs ont quitté la table. Les autres sont classés selon leurs kilos.',
    },
    youWonForfeit: 'Les autres joueurs ont quitté la partie. Vous gagnez par forfait.',
    places: 'Classement final',
    playAgain: 'Rejouer',
    rematchPending: 'Lancement…',
    rematchFailed: 'Impossible de lancer une nouvelle partie. Réessayez.',
  },
  live: {
    round: 'Manche {n}',
    received: 'Vous avez reçu {card} de {name}',
    slapped: '{name} a tapé ! Tapez !',
    kilosChange: '{name} a maintenant {kg} kg',
    kilosChangeYou: 'Vous avez maintenant {kg} kg',
  },
  errors: {
    finishedGame: 'La partie est terminée.',
    wrongPhase: 'Vous ne pouvez pas faire cela maintenant.',
    alreadyChose: 'Vous avez déjà choisi une carte à passer.',
    cardNotInHand: 'Cette carte n’est pas dans votre main.',
    alreadySlapped: 'Vous avez déjà tapé.',
    notActiveSeat: 'Vous ne participez plus à cette partie.',
  },
  notice: { connection: 'Connexion perdue. Réessayez.' },
  motion: {
    speed: 'Vitesse d’animation : {speed}',
    speeds: { normal: 'Normale', fast: 'Rapide', instant: 'Instantanée' },
  },
};

export const kiloDeMerdeOptions: Messages['options']['kiloDeMerde'] = {
  rounds: { label: 'Manches', '5': '5', '10': '10', '15': '15', '20': '20' },
  level: { label: 'Niveau', easy: 'Facile', medium: 'Moyen', hard: 'Difficile' },
};

// The rules panel of Kilo de merde.
export const kiloDeMerde: Messages['kiloDeMerde'] = {
  toggleLabel: 'Règles',
  panelTitle: 'Kilo de merde',
  closeLabel: 'Fermer les règles',
  sections: [
    {
      kind: 'text',
      title: 'But du jeu',
      body: ['Récoltez le moins de kilos possible. Vous en récoltez quand vous êtes le dernier à taper sur le tas.'],
    },
    {
      kind: 'text',
      title: 'Mise en place',
      body: ['Chacun reçoit 4 cartes. Seules certaines valeurs sont distribuées : les valets, les dames et les rois avec 3 joueurs, puis on ajoute les 2 avec 4 joueurs, les 3 avec 5, les 4 avec 6, les 5 avec 7, les 6 avec 8, les 8 avec 9 et les 9 avec 10. Les 7 ne sont jamais distribués. Toutes les autres cartes forment le tas de kilos, face cachée.'],
    },
    {
      kind: 'text',
      title: 'Passer une carte',
      body: ['Tout le monde choisit une carte en même temps et la passe à son voisin de gauche ; vous en recevez une de votre voisin de droite. Vous avez 15 secondes ; ensuite, une carte est passée pour vous.'],
    },
    {
      kind: 'text',
      title: 'Taper sur le tas',
      body: ['Quand un joueur a quatre cartes de même valeur, tapez sur le tas ! Dès que quelqu’un tape, tout le monde doit taper. Le dernier prend la première carte du tas de kilos. Si vous tapez alors que personne n’a quatre cartes identiques, c’est vous qui la prenez.'],
    },
    {
      kind: 'text',
      title: 'Kilos',
      body: ['As 100 kg, Roi 13, Dame 12, Valet 11, les autres cartes leur valeur. Le 7 est la chasse d’eau : tous vos kilos retournent dans le tas avec lui.'],
    },
    {
      kind: 'text',
      title: 'Fin de la partie',
      body: ['La partie dure le nombre de manches choisi dans le salon, ou jusqu’à ce que le tas de kilos soit vide. Le joueur qui a le moins de kilos gagne ; les ex æquo partagent la place.'],
    },
  ],
};
