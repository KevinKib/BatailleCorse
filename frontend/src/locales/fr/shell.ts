import type { Messages } from '../Messages';

// T1: shell, lobby and Bataille Corse. Typography: U+202F before ; ! ? » and after «, U+00A0 before :.

export const rules: Messages['rules'] = {
  toggleLabel: 'Règles',
  panelTitle: 'Comment jouer',
  closeLabel: 'Fermer',
  sections: [
    {
      kind: 'text',
      title: 'But du jeu',
      body: ['Gagnez toutes les cartes. La partie s’arrête quand un joueur les a toutes.'],
    },
    {
      kind: 'text',
      title: 'Envoyer',
      body: ['À votre tour, envoyez votre carte du dessus sur le tas central. Le tour passe alors à votre adversaire.'],
    },
    {
      kind: 'slap',
      title: 'Tapez quand vous voyez',
      labels: {
        doubles: 'Doublé',
        sandwich: 'Sandwich',
        sumOfTen: 'Somme de dix',
        tens: 'Dix',
      },
      footer: 'Le premier à taper sur un tas valide le ramasse en entier.',
    },
    {
      kind: 'text',
      title: 'Tape ratée',
      body: ['Tapez sans motif valide et deux de vos cartes passent sous le tas.'],
    },
    {
      kind: 'text',
      title: 'Les têtes (valet, dame, roi, as)',
      body: ['Envoyez-en une et votre adversaire doit répondre par une tête dans un nombre limité de cartes : valet → 1, dame → 2, roi → 3, as → 4. S’il n’y arrive pas, le tas est à prendre.'],
    },
    {
      kind: 'text',
      title: 'Ramasser',
      body: ['Quand le tas peut être ramassé, prenez-le pour gagner toutes ses cartes.'],
    },
  ],
};

export const batailleCorseUi: Messages['batailleCorseUi'] = {
  connecting: 'Connexion à la table…',
  opponent: 'Adversaire',
  playerLabel: 'Joueur {n}',
  botLabel: 'Bot {n}',
  botWithTier: '{bot} ({tier})',
  playAgain: 'Rejouer',
  rematchPending: 'Connexion à la nouvelle table…',
  rematchFailed: 'Impossible de lancer une nouvelle partie. Réessayez.',
  back: 'Retour',
  send: 'Envoyer',
  slap: 'Taper',
  you: 'Vous',
  sound: { mute: 'Couper le son', unmute: 'Activer le son' },
  offline: 'Connexion perdue. Reconnexion…',
};

export const gamePicker: Messages['gamePicker'] = {
  title: 'CardGamesZone',
  choose: 'Choisissez un jeu',
  navLabel: 'Aller à une catégorie',
  categories: { cards: 'Jeux de cartes', board: 'Jeux de plateau' },
  back: 'Retour',
  playAria: '{play} {name}',
  privacy: 'Confidentialité',
  games: {
    bataillecorse: {
      name: 'Bataille corse',
      description: 'Un duel rapide à deux. Tapez sur le tas dès que vous repérez une combinaison.',
      players: '2 joueurs',
      play: 'Jouer',
    },
    bullshit: {
      name: 'Menteur',
      description: 'Bluffez pour vider votre main. Démasquez les menteurs.',
      players: '2 à 6 joueurs',
      play: 'Jouer',
    },
    kobo: {
      name: 'Kobo',
      description: 'Mémorisez vos cartes cachées et finissez la manche avec le moins de points.',
      players: '2 à 6 joueurs',
      play: 'Jouer',
    },
    'three-men-morris': {
      name: 'Moulin',
      description: 'Tapatan, Achi ou Moulin à six. Posez, puis glissez.',
      players: '2 joueurs',
      play: 'Jouer',
    },
    president: {
      name: 'Président',
      description: 'Videz votre main avant les autres et décrochez le titre de président.',
      players: '3 à 8 joueurs',
      play: 'Jouer',
    },
    mangola: {
      name: 'Mangola',
      description: 'Disposez vos graines, puis semez et capturez. Prenez toutes les graines de l’adversaire.',
      players: '2 joueurs',
      play: 'Jouer',
    },
    'ninety-nine': {
      name: '99',
      description: 'Gardez le total à 99 ou moins. Celui qui ne peut plus jouer perd une vie.',
      players: '2 à 12 joueurs',
      play: 'Jouer',
    },
    draughts: {
      name: 'Dames',
      description: 'Dames internationales sur un plateau de 10 sur 10. Prenez toutes les pièces adverses, ou laissez l’adversaire sans coup possible.',
      players: '2 joueurs',
      play: 'Jouer',
    },
    blackjack: {
      name: 'Blackjack',
      description: 'Battez le croupier, seul ou dans un tournoi à 100 points.',
      players: '1 à 6 joueurs',
      play: 'Jouer',
    },
    'kilo-de-merde': {
      name: 'Kilo de merde',
      description: 'Passez, réunissez un carré, tapez sur le tas. Le dernier ramasse les kilos.',
      players: '3 à 10 joueurs',
      play: 'Jouer',
    },
  },
};

export const endScreen: Messages['endScreen'] = {
  victory: 'VICTOIRE',
  defeat: 'DÉFAITE',
  draw: 'ÉGALITÉ',
  backToHome: 'Retour à l’accueil',
};

export const endGame: Messages['endGame'] = {
  won: '{opponent} a gagné.',
  resigned: '{opponent} a abandonné.',
  disconnected: '{opponent} s’est déconnecté.',
  beat: 'Vous avez battu {opponent} !',
};

export const disconnect: Messages['disconnect'] = {
  title: '{player} est déconnecté',
  waiting: 'En attente de son retour…',
  countdown: 'Victoire dans {n} s',
  reconnecting: 'Reconnexion',
  reconnectingCountdown: 'Reconnexion… {n} s',
};

export const leaveGuard: Messages['leaveGuard'] = {
  confirm: 'Quitter la partie ? Vous déclarez forfait et votre adversaire gagne.',
};

export const home: Messages['home'] = {
  newGame: 'Nouvelle partie',
  joinGame: 'Rejoindre une partie',
  yourName: 'Votre nom',
  namePlaceholder: 'Saisissez votre nom…',
  back: 'Retour',
  gameGone: 'Cette partie n’existe plus.',
  seatLost: 'Votre place dans cette partie a été perdue. Rejoignez-la de nouveau avec son lien d’invitation.',
  creating: 'Création…',
  privateGame: 'Partie privée',
  privateGameHint: 'Masquée de la liste publique. Seules les personnes ayant le lien d’invitation peuvent la rejoindre.',
  createFailed: 'Impossible de créer la partie. Vérifiez votre connexion et réessayez.',
};

export const join: Messages['join'] = {
  title: 'Rejoindre une partie',
  gameId: 'Identifiant de la partie',
  gameIdPlaceholder: 'Collez l’identifiant ou le lien…',
  yourName: 'Votre nom',
  namePlaceholder: 'Saisissez votre nom…',
  submit: 'Rejoindre la partie',
  joining: 'Connexion…',
  failed: 'Impossible de rejoindre cette partie. Vérifiez l’identifiant et réessayez.',
  full: 'Ce salon est complet.',
  started: 'Cette partie a déjà commencé.',
  notFound: 'Aucune partie trouvée. Elle a peut-être été fermée, ou l’identifiant est erroné.',
  back: 'Retour',
  or: 'Ou choisissez une partie publique',
  publicGames: {
    title: 'Parties publiques',
    openCount: 'Ouvertes : {open}',
    totalCount: 'Ouvertes : {open} · {total} au total',
    openOnly: 'Parties ouvertes uniquement',
    join: 'Rejoindre',
    joinAria: 'Rejoindre la partie de {host}, places prises : {joined} sur {max}',
    open: 'Ouverte',
    full: 'Complète',
    inPlay: 'En cours',
    players: '{joined} / {max} joueurs',
    bots: { one: '· {n} bot', other: '· {n} bots' },
    loading: 'Recherche de parties…',
    empty: 'Aucune partie publique de {game} pour le moment.',
    emptyHint: 'Lancez-en une, elle apparaîtra ici.',
    newGame: 'Nouvelle partie',
    offline: 'Mises à jour en direct suspendues. Reconnexion…',
    truncated: '{shown} parties affichées sur {total}.',
    announce: {
      newGames: { one: '{n} nouvelle partie.', other: '{n} nouvelles parties.' },
      openNow: 'Parties ouvertes : {open}.',
      noneOpen: 'Aucune partie ouverte.',
    },
  },
};

export const lobby: Messages['lobby'] = {
  title: 'Salon',
  leave: 'Quitter le salon',
  playerCount: '{joined} / {max} joueurs',
  playersLabel: 'Joueurs',
  youBadge: 'vous',
  botBadge: 'bot',
  hostBadge: 'hôte',
  playerLabel: 'Joueur {n}',
  botName: 'Bot {n}',
  seatOptionLabel: '{option} pour {name}',
  addBot: 'Ajouter un bot',
  removeBot: 'Retirer {name}',
  removeSeat: 'Retirer {name}',
  offlineBadge: 'hors ligne',
  removedByHost: 'L’hôte vous a retiré du salon.',
  actionFailed: 'Ce changement n’a pas pu être fait. Vérifiez le salon et réessayez.',
  renameLabel: 'Votre nom',
  waitingForPlayer: 'En attente d’un joueur',
  inviteLabel: 'Invitez des joueurs avec ce lien',
  copy: 'Copier',
  copied: 'Copié !',
  startGame: 'Lancer la partie',
  starting: 'Démarrage de la partie…',
  waitingForPlayers: {
    one: 'Il manque {n} joueur pour démarrer…',
    other: 'Il manque {n} joueurs pour démarrer…',
  },
  waitingForHost: 'En attente du démarrage par l’hôte…',
  nowHost: 'Vous êtes maintenant l’hôte.',
  customise: 'Personnaliser',
  privateGame: 'Partie privée',
  privateHint: 'Masquée de la liste publique. Seules les personnes ayant le lien d’invitation peuvent la rejoindre.',
  privateChip: 'Partie privée',
  inviteHintPrivate: 'Seules les personnes ayant ce lien peuvent la rejoindre.',
  inviteHintPublic: 'Listée dans les parties publiques. Chacun peut la rejoindre tant qu’une place est libre.',
};

export const options: Messages['options'] = {
  bataillecorse: {
    tier: {
      label: 'Niveau',
      '0': 'Entraînement', '1': 'Bronze', '2': 'Argent', '3': 'Or', '4': 'Platine',
      '5': 'Diamant', '6': 'Champion', '7': 'Challenger', '8': 'Légende',
    },
  },
  bullshit: {
    claimMode: { label: 'Mode d’annonce', rank: 'Par valeur ({range})', suit: 'Par couleur' },
    deckSize: { label: 'Paquet', '32': '32 cartes (du 7 à l’as)', '52': '52 cartes' },
    vars: { range: { deckSize: { '32': '7→A', '52': 'A→R' } } },
  },
  threeMenMorris: {
    variant: { label: 'Variante', tapatan: 'Tapatan', achi: 'Achi', six: 'Moulin à six' },
    hostColour: { label: 'L’hôte joue', white: 'Blancs', black: 'Noirs', random: 'Au hasard' },
    level: { label: 'Niveau', random: 'Facile', greedy: 'Moyen', perfect: 'Expert' },
  },
  kobo: {
    level: { label: 'Niveau', easy: 'Facile', medium: 'Moyen', hard: 'Difficile' },
  },
  president: {
    preset: { classic: 'Classique', custom: 'Personnalisé' },
    level: { label: 'Niveau', easy: 'Facile', medium: 'Moyen', hard: 'Difficile' },
    afterPass: { label: 'Après avoir passé', reenter: 'Rejouer', out: 'Hors du pli' },
    mustPlay: { label: 'Jeu obligatoire', off: 'Non, on peut passer', on: 'Oui, pas de passe si on peut jouer' },
    reducedCut: { label: 'Coupe avec les 2', off: 'Autant de 2 que de cartes', on: 'Un 2 de moins sur une paire ou un brelan' },
    exchange: { label: 'Échange', two: '2 cartes, puis 1', one: '1 carte', scaled: 'Plus avec plus de joueurs', none: 'Aucun' },
    giveBack: { label: 'Cartes rendues', free: 'Au choix', lowest: 'Les plus basses' },
    nextLead: { label: 'Entame dès la manche 2', scum: 'Trou', president: 'Président' },
    scoring: { label: 'Score', rank: 'Par place', titles: 'Par titre', zeroSum: 'À somme nulle' },
    length: {
      label: 'Durée de la partie', 'rounds-5': '5 manches', 'rounds-3': '3 manches', 'rounds-10': '10 manches',
      'points-11': 'Premier à 11', 'points-21': 'Premier à 21', endless: 'Sans fin',
    },
  },
  mangola: {
    firstPlayer: { label: 'Premier joueur', host: 'Hôte', guest: 'Invité', random: 'Au hasard' },
    direction: { label: 'Sens du semis', anticlockwise: 'Sens antihoraire', clockwise: 'Sens horaire' },
    level: { label: 'Niveau', random: 'Facile', greedy: 'Moyen', expert: 'Expert' },
  },
};

export const hotkeys: Messages['hotkeys'] = {
  open: 'Raccourcis clavier',
  title: 'Raccourcis clavier',
  enabled: 'Raccourcis activés',
  actions: {
    discard: 'Défausser', call: 'Dire Menteur', send: 'Envoyer une carte', slap: 'Taper sur le tas',
    pass1: 'Passer la carte 1', pass2: 'Passer la carte 2', pass3: 'Passer la carte 3', pass4: 'Passer la carte 4', ready: 'Prêt',
  },
  change: 'Modifier',
  changeAria: 'Modifier la touche pour {action}',
  listening: 'Appuyez sur une touche…',
  reset: 'Rétablir les valeurs par défaut',
  space: 'Espace',
  errors: {
    reserved: 'Utilisez une lettre ou un symbole seul (pas Tab, Entrée, Espace, Échap, les flèches ni les modificateurs).',
    duplicate: 'Cette touche est déjà utilisée par une autre action.',
  },
};

export const turn: Messages['turn'] = {
  yourTurn: 'À vous de jouer',
};

export const language: Messages['language'] = {
  label: 'Langue',
  settings: 'Paramètres',
  names: { en: 'English', fr: 'Français' },
};
