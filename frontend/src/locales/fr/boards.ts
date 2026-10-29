import type { Messages } from '../Messages';

// French texts for the Morris family (Moulin à trois, Moulin à six, Tapatan, Achi) and Mangola.
// Conventions: "vous"; "moulin" is the mill; a no-break space ( ) goes before ":" and a narrow one
// ( ) before ; ! ? and inside « ».
const forfeitBody = 'Quitter la partie, ou rester déconnecté trop longtemps, donne la victoire à votre adversaire.';
const blockedBody = 'Un joueur qui ne peut pas jouer à son tour perd la partie.';

export const threeMenMorris: Messages['threeMenMorris'] = {
  toggleLabel: 'Règles',
  panelTitle: 'Moulin à trois',
  closeLabel: 'Fermer les règles',
  sections: [
    {
      kind: 'text',
      title: 'But du jeu',
      body: ['Alignez vos trois pions en ligne, en colonne ou en diagonale.'],
    },
    {
      kind: 'text',
      title: 'Le plateau',
      body: ['Neuf points, reliés par les lignes, les colonnes et les deux diagonales passant par le centre. Les blancs commencent.'],
    },
    {
      kind: 'text',
      title: 'Pose des pions',
      body: ['À tour de rôle, posez vos trois pions sur des points libres. Un alignement fait pendant le placement gagne aussitôt.'],
    },
    {
      kind: 'text',
      title: 'Déplacement',
      body: ['Quand les six pions sont posés, faites glisser un des vôtres le long d’une ligne vers un point voisin libre, d’un point à la fois.'],
    },
    {
      kind: 'text',
      title: 'Blocage',
      body: [blockedBody],
    },
    {
      kind: 'text',
      title: 'Forfait',
      body: [forfeitBody],
    },
  ],
};

export const threeMenMorrisUi: Messages['threeMenMorrisUi'] = {
  white: 'Blancs',
  black: 'Noirs',
  you: 'Vous',
  playerLabel: 'Joueur {n}',
  botWithLevel: '{name} ({level})',
  back: 'Retour',
  connecting: 'Arrivée à la table…',
  table: {
    youPlay: 'Vous jouez les {colour}. Les blancs commencent.',
    hintPlace: 'Posez un pion',
    hintMove: 'Déplacez un pion',
    waitingFor: 'En attente de {player}…',
    piecesLeft: { one: '{n} pion restant à poser', other: '{n} pions restants à poser' },
    colourTag: '{name}, {colour}',
    countsTag: '{name}, {colour}\u00A0: {board} sur le plateau, {hand} en main, perdus : {lost}',
    onBoard: 'sur le plateau\u00A0: {n}',
    lost: 'perdus\u00A0: {n}',
    placed: '{player} a posé un pion sur {point}.',
    moved: '{player} a déplacé un pion de {from} à {to}.',
    closedMill: '{player} a fait un moulin.',
    removed: '{player} a pris le pion de {point}.',
    lastPieces: 'Encore une perte et {player} perd.',
    quietMoves: {
      one: '{n} coup sans prise avant la nulle.',
      other: '{n} coups sans prise avant la nulle.',
    },
    removeTitleMine: 'Moulin\u202F! Prenez un pion de {player}.',
    removeCanTake: { one: '{n} pion peut être pris.', other: '{n} pions peuvent être pris.' },
    removeProtected: 'Les pions d’un moulin sont protégés tant que {player} en a d’autres.',
    removeTitleTheirs: '{player} a fait un moulin',
    removeSubTheirs: 'et prend un de vos pions.',
    forfeited: '{player} a déclaré forfait',
    offline: 'Connexion perdue. Reconnexion…',
  },
  draw: {
    offer: 'Proposer la nulle',
    offered: 'Nulle proposée',
    incoming: '{player} propose la nulle',
    incomingHint: 'Acceptez pour terminer sur une partie nulle, ou continuez à jouer.',
    accept: 'Accepter la nulle',
    decline: 'Refuser',
    repetition: 'Cette position est apparue deux fois. À la troisième, la partie est nulle.',
    offeredBy: '{player} propose la nulle.',
    declinedBy: '{player} a refusé la nulle.',
    agreed: 'Nulle d’un commun accord.',
    byRepetition: 'Nulle par répétition\u00A0: la même position est apparue trois fois.',
    byMoveLimit: 'Nulle\u00A0: trop de coups sans prise.',
  },
  end: {
    winner: 'Gagnant\u00A0: {player}',
    youWonLine: 'Vous avez aligné trois pions.',
    youLostLine: '{player} a aligné trois pions.',
    blockedWon: '{player} ne peut pas jouer.',
    blockedLost: 'Vous ne pouvez pas jouer\u00A0: {player} gagne.',
    reducedWon: 'Il ne reste que deux pions à {player}.',
    reducedLost: 'Il ne vous reste que deux pions\u00A0: {player} gagne.',
    youWonResigned: '{player} a abandonné.',
    youWonDisconnected: '{player} s’est déconnecté.',
    youWonForfeit: 'Votre adversaire a quitté la partie.',
    youLostForfeit: 'Vous avez quitté la partie.',
    playAgain: 'Rejouer',
    rematchPending: 'Arrivée à la nouvelle table…',
    rematchFailed: 'Impossible de lancer une nouvelle partie. Réessayez.',
  },
};

export const morrisLobby: Messages['morrisLobby'] = {
  variantLabel: 'Variante',
  colourLabel: 'Votre couleur',
  hostPlays: 'L’hôte joue',
  variants: {
    tapatan: {
      name: 'Tapatan',
      summary: '3 pions chacun, diagonales',
      rule: 'Tapatan\u00A0: 3 pions chacun. Glissez le long des lignes, des colonnes et des deux diagonales passant par le centre.',
    },
    achi: {
      name: 'Achi',
      summary: '4 pions chacun, un point libre',
      rule: 'Achi\u00A0: 4 pions chacun, soit huit pions sur neuf points, donc un point reste libre. Même plateau que le Tapatan.',
    },
    six: {
      name: 'Moulin à six',
      summary: '6 pions chacun, moulins',
      rule: 'Moulin à six\u00A0: 6 pions chacun sur deux carrés. Un moulin prend un pion\u202F; il faut garder au moins trois pions.',
    },
  },
  colours: { white: 'Blancs', black: 'Noirs', random: 'Au hasard' },
};

export const morrisVariantRules: Messages['morrisVariantRules'] = {
  achi: {
    toggleLabel: 'Règles',
    panelTitle: 'Achi',
    closeLabel: 'Fermer les règles',
    sections: [
      {
        kind: 'text',
        title: 'But du jeu',
        body: ['Alignez trois de vos quatre pions en ligne, en colonne ou en diagonale.'],
      },
      {
        kind: 'text',
        title: 'Le plateau',
        body: ['Neuf points, reliés par les lignes, les colonnes et les deux diagonales passant par le centre. Les blancs commencent.'],
      },
      {
        kind: 'text',
        title: 'Pose des pions',
        body: ['À tour de rôle, posez vos quatre pions sur des points libres. Huit pions remplissent tous les points sauf un. Un alignement fait pendant le placement gagne aussitôt.'],
      },
      {
        kind: 'text',
        title: 'Déplacement',
        body: ['Quand les huit pions sont posés, faites glisser un des vôtres le long d’une ligne vers le point voisin libre.'],
      },
      {
        kind: 'text',
        title: 'Blocage',
        body: [blockedBody],
      },
      {
        kind: 'text',
        title: 'Forfait',
        body: [forfeitBody],
      },
    ],
  },
  six: {
    toggleLabel: 'Règles',
    panelTitle: 'Moulin à six',
    closeLabel: 'Fermer les règles',
    sections: [
      {
        kind: 'text',
        title: 'But du jeu',
        body: ['Prenez des pions en faisant des moulins. Un joueur qui n’a plus que deux pions, ou qui ne peut plus jouer, perd.'],
      },
      {
        kind: 'text',
        title: 'Le plateau',
        body: ['Seize points sur deux carrés, l’un dans l’autre, reliés au milieu de chaque côté. Il n’y a pas de diagonales. Les blancs commencent.'],
      },
      {
        kind: 'text',
        title: 'Pose des pions',
        body: ['À tour de rôle, posez vos six pions sur des points libres. Personne ne déplace de pion avant que les douze soient posés.'],
      },
      {
        kind: 'text',
        title: 'Déplacement',
        body: ['Faites glisser un de vos pions le long d’une ligne vers un point voisin libre. Les pions ne sautent jamais, même quand il n’en reste que trois.'],
      },
      {
        kind: 'text',
        title: 'Moulins',
        body: [
          'Trois de vos pions alignés le long d’un côté d’un carré forment un moulin, en posant comme en déplaçant. Vous prenez alors un pion adverse. Fermer deux moulins d’un coup ne prend toujours qu’un seul pion.',
          'Un pion d’un moulin est protégé, sauf si tous les pions de votre adversaire sont dans un moulin.',
        ],
      },
      {
        kind: 'text',
        title: 'Blocage',
        body: [blockedBody],
      },
      {
        kind: 'text',
        title: 'Nulle',
        body: ['Les joueurs peuvent convenir d’une nulle dès que les déplacements commencent. La partie est nulle quand la même position apparaît trois fois, ou après cinquante coups sans prise.'],
      },
      {
        kind: 'text',
        title: 'Contre un bot',
        body: ['Le niveau Expert voit trois coups d’avance sur ce plateau\u00A0: un joueur attentif peut le battre.'],
      },
      {
        kind: 'text',
        title: 'Forfait',
        body: [forfeitBody],
      },
    ],
  },
};

export const morrisBoard: Messages['morrisBoard'] = {
  boardLabel: 'Plateau de moulin',
  pointNames: [
    'Haut gauche', 'Haut milieu', 'Haut droite',
    'Milieu gauche', 'Centre', 'Milieu droite',
    'Bas gauche', 'Bas milieu', 'Bas droite',
  ],
  sixPointNames: [
    'Extérieur haut gauche', 'Extérieur haut', 'Extérieur haut droite', 'Extérieur droite',
    'Extérieur bas droite', 'Extérieur bas', 'Extérieur bas gauche', 'Extérieur gauche',
    'Intérieur haut gauche', 'Intérieur haut', 'Intérieur haut droite', 'Intérieur droite',
    'Intérieur bas droite', 'Intérieur bas', 'Intérieur bas gauche', 'Intérieur gauche',
  ],
  white: 'Pion blanc',
  black: 'Pion noir',
  free: 'libre',
  inMill: 'dans le moulin qui vient d’être fait',
  canTake: 'peut être pris',
  protectedPiece: 'protégé par un moulin',
  justTaken: 'pion qui vient d’être pris',
  canMove: 'peut bouger',
  canPlace: 'peut être posé ici',
  selected: 'sélectionné',
  pointLabel: '{point}\u00A0: {state}',
};

export const mangola: Messages['mangola'] = {
  toggleLabel: 'Règles',
  panelTitle: 'Mangola',
  closeLabel: 'Fermer les règles',
  sections: [
    {
      kind: 'text',
      title: 'But du jeu',
      body: ['Capturez toutes les graines de votre adversaire, ou laissez-le sans coup possible à son tour.'],
    },
    {
      kind: 'text',
      title: 'Le plateau',
      body: [
        'Quatre rangées de huit cases, 64 graines en tout. Vous possédez les deux rangées les plus proches de vous\u00A0: 32 graines, ni plus, ni moins. Les deux rangées du milieu se font face.',
        'Chacun sème sur une boucle de ses 16 cases, jamais dans la moitié adverse. Les petites flèches montrent le sens\u00A0: antihoraire, sauf si l’hôte a choisi le sens horaire dans le salon.',
      ],
    },
    {
      kind: 'text',
      title: 'Mise en place',
      body: ['Avant le premier coup, les deux joueurs disposent leurs 32 graines en secret. Touchez une case pour ajouter une graine, passez en mode Retirer pour en reprendre une, ou partez d’un modèle. Les deux dispositions sont révélées ensemble quand les deux joueurs ont confirmé.'],
    },
    {
      kind: 'text',
      title: 'Un coup',
      body: [
        'Choisissez une de vos cases contenant au moins deux graines. Prenez toutes ses graines et déposez-les une à une dans les cases suivantes de votre boucle.',
        'Si la case après la dernière graine est vide, la dernière graine va une case plus loin et votre tour s’arrête. Si elle contient des graines, la dernière graine y tombe et le coup continue.',
      ],
    },
    {
      kind: 'text',
      title: 'Prise et relais',
      body: [
        'Quand la dernière graine tombe dans une case occupée de votre rangée du milieu, et que la case en face et celle derrière elle contiennent des graines, vous les capturez toutes et les semez, à partir de la case suivant celle d’où vous avez commencé le tour.',
        'Sinon, vous prenez toutes les graines de cette case et vous continuez à semer depuis elle (un relais).',
      ],
    },
    {
      kind: 'text',
      title: 'Fin et nulle',
      body: ['Un joueur qui ne peut pas jouer à son tour perd. Un coup qui sèmerait sans fin est interdit. La même position trois fois est une nulle.'],
    },
    {
      kind: 'text',
      title: 'Forfait',
      body: [forfeitBody],
    },
    {
      kind: 'text',
      title: 'Contre un bot',
      body: ['L’hôte peut ajouter un bot à trois niveaux\u00A0: Facile joue au hasard, Moyen joue pour prendre le plus de graines un coup à l’avance, Expert voit plusieurs coups d’avance. Expert est fort, mais pas parfait.'],
    },
  ],
};

export const mangolaUi: Messages['mangolaUi'] = {
  board: {
    label: 'Plateau de Mangola',
    yourSide: 'Votre côté',
    opponentSide: 'Côté adverse',
    skip: 'Passer',
    speed: 'Vitesse d’animation\u00A0: {speed}',
    speeds: { normal: 'Normale', fast: 'Rapide', instant: 'Instantanée' },
  },
  move: {
    sowed: '{player} a semé depuis {pit}',
    sentence: '{head}\u00A0: {details}.',
    sentenceAlone: '{head}.',
    relays: { one: '{n} relais', other: '{n} relais' },
    captured: { one: '{n} graine capturée', other: '{n} graines capturées' },
  },
  pit: {
    label: 'Case {pit}, {state}',
    seeds: { one: '{n} graine', other: '{n} graines' },
    hidden: 'cachée',
    canSow: 'peut semer',
    chosen: 'vient d’être choisie',
    lastStart: 'le dernier coup a commencé ici',
    lastEnd: 'le dernier coup s’est terminé ici',
    captured: { one: 'capturée, {n} graine prise', other: 'capturée, {n} graines prises' },
    capturedUnknown: 'capturée',
    tapToAdd: 'touchez pour ajouter une graine',
    tapToRemove: 'touchez pour retirer une graine',
  },
  placement: {
    title: 'Placez vos graines',
    boardLabel: 'Votre disposition',
    intro: 'Mettez vos 32 graines sur vos deux rangées. Votre adversaire place les siennes en même temps\u202F; les deux dispositions sont révélées ensemble.',
    seedsLeft: { one: '{n} graine restante', other: '{n} graines restantes' },
    allPlaced: 'Toutes les graines sont placées',
    add: 'Ajouter',
    remove: 'Retirer',
    presets: {
      label: 'Modèles',
      checkerboard: 'Damier',
      twoPerPit: 'Deux par case',
      random: 'Au hasard',
      clear: 'Vider',
    },
    confirm: 'Confirmer la disposition',
    confirmDisabledHint: 'Placez toutes vos graines pour confirmer (encore {n}).',
    editLayout: 'Modifier la disposition',
    confirmedWaiting: 'Disposition confirmée. Vous pouvez encore la modifier tant que votre adversaire n’est pas prêt.',
    opponent: '{player}\u00A0: {status}',
    opponentPlacing: 'Placement en cours…',
    opponentReady: 'Prêt',
    opponentAnonymous: 'Adversaire',
    endlessWarning: 'Attention\u00A0: face à une moitié adverse vide, chaque coup de cette disposition sèmerait sans fin. Cela peut encore marcher une fois les graines de votre adversaire sur le plateau.',
  },
  errors: {
    stuckStart: 'Avec ces deux dispositions, le joueur qui commence n’aurait aucun coup. Modifiez votre disposition et confirmez à nouveau.',
  },
  playerLabel: 'Joueur {n}',
  botWithLevel: '{name} ({level})',
  back: 'Retour',
  connecting: 'Arrivée à la table…',
  table: {
    waitingFor: 'En attente de {player}',
    thinking: '{player} réfléchit…',
    hintSow: 'Choisissez une de vos cases d’au moins deux graines',
    seeds: { one: '{n} graine', other: '{n} graines' },
    forfeited: '{player} a déclaré forfait',
    offline: 'Connexion perdue. Reconnexion…',
    repetition: 'Cette position est apparue deux fois. À la troisième, la partie est nulle.',
  },
  end: {
    winner: 'Gagnant\u00A0: {player}',
    noMoveWon: '{player} n’a plus de coup.',
    noMoveLost: 'Vous n’avez plus de coup.',
    drawRepetition: 'Nulle par répétition\u00A0: la même position est apparue trois fois.',
    drawEndless: 'Nulle\u00A0: un semis qui ne finit jamais.',
    youWonResigned: '{player} a abandonné.',
    youWonDisconnected: '{player} s’est déconnecté.',
    youWonForfeit: 'Votre adversaire a quitté la partie.',
    youLostForfeit: 'Vous avez quitté la partie.',
    playAgain: 'Rejouer',
    rematchPending: 'Arrivée à la nouvelle table…',
    rematchFailed: 'Impossible de lancer une nouvelle partie. Réessayez.',
  },
};
