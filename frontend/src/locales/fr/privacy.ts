import type { Messages } from '../Messages';

// Typography:   (no-break space) before a colon,   (narrow no-break space) inside « » and before ; ! ?.
export const privacy: Messages['privacy'] = {
  title: 'Confidentialité',
  intro: 'Ce que ce site fait de vos données, en termes simples. Cette page décrit uniquement ce que le site fait réellement aujourd’hui.',
  sections: [
    {
      id: 'visits',
      heading: 'Statistiques de visite',
      paragraphs: [
        'Pour savoir combien de personnes visitent le site, le serveur qui le distribue conserve un journal contenant une ligne par page demandée. Une ligne contient l’heure, votre adresse IP raccourcie (le dernier nombre d’une adresse IPv4 est remplacé par 0, une adresse IPv6 est coupée après ses trois premiers groupes), la page demandée, le statut et la taille de la réponse, ainsi que le nom de votre navigateur. La page demandée peut comprendre l’identifiant d’un salon de jeu.',
        'Aucun cookie n’est déposé ni lu. L’adresse de la page d’où vous venez et la partie de l’adresse située après un point d’interrogation ne sont pas consignées, pas plus que les appels de l’application vers le serveur de jeu. Les fichiers journaux sont supprimés dans un délai de 14 jours. Ils servent uniquement à compter les visites.',
      ],
    },
    {
      id: 'usage',
      heading: 'Compteurs d’utilisation des jeux',
      paragraphs: [
        'Le serveur de jeu compte combien de parties sont créées, lancées et terminées pour chaque jeu, ainsi que les revanches, les places prises et les bots ajoutés. Un compteur ne contient que le type de jeu et des nombres : aucun nom, aucune adresse IP, aucun jeton et aucun identifiant de partie. Les compteurs sont conservés dans la mémoire du serveur et écrits sous forme de lignes dans son journal.',
      ],
    },
    {
      id: 'browser',
      heading: 'Ce que stocke votre navigateur',
      paragraphs: [
        'Le site utilise le stockage local de votre navigateur, et non des cookies. Seul le site lui-même le lit, pour reprendre une partie et pour mémoriser vos choix. Vous pouvez l’effacer dans les paramètres de votre navigateur.',
        'Jeton de place, qui vous permet de revenir à la partie que vous étiez en train de jouer : tokens:<identifiant de la partie> (bataille corse), bullshit:tokens:<identifiant de la partie>, kobo:tokens:<identifiant de la partie>, three-men-morris:tokens:<identifiant de la partie>, president:tokens:<identifiant de la partie> et mangola:tokens:<identifiant de la partie>. Il est supprimé lorsque vous quittez la partie.',
        'Nom d’affichage : bc_playerName. Raccourcis clavier : bc_sendKey, bc_slapKey et bullshit.hotkeys. Son : bullshit.sound et kobo.sound. Vitesse d’animation : animationSpeed. Astuce de Kobo déjà vue : kobo.matchCoach. Langue : bc_locale.',
        'La police du site est servie par le site lui-même, de sorte que votre navigateur ne contacte aucun autre serveur pour l’afficher, et que rien n’est envoyé à un tiers.',
      ],
    },
    {
      id: 'memory',
      heading: 'Parties et noms',
      paragraphs: [
        'Une partie n’existe que dans la mémoire du serveur. Il n’y a pas de base de données. Une partie est perdue au redémarrage du serveur, environ 30 minutes après sa dernière activité, et environ 2 minutes après sa fin.',
        'Le nom que vous saisissez est envoyé au serveur et affiché aux autres joueurs de votre salon.',
      ],
    },
    {
      id: 'public',
      heading: 'La liste des parties publiques',
      paragraphs: [
        'La page « Rejoindre » de chaque jeu liste les parties publiques de ce jeu, à toute personne qui l’ouvre. Une partie listée affiche le nom d’affichage de son hôte, le nombre de places prises sur le maximum, combien d’entre elles sont des bots, et si elle est ouverte, complète ou en cours. L’identifiant d’une partie publique fait partie de la liste, puisque c’est lui qui permet de la rejoindre. Rien d’autre n’est affiché : aucun autre nom de joueur et aucun jeton.',
        'Une partie est publique par défaut. L’hôte peut cocher « Partie privée » à sa création, ou l’activer dans le salon : une partie privée n’est jamais listée, et on ne la rejoint que par son lien d’invitation.',
        'La liste est en direct : elle suit les parties au fil de leurs changements, et une partie en disparaît lorsqu’elle est supprimée. Elle ne stocke rien de plus que les parties déjà conservées dans la mémoire du serveur.',
      ],
    },
    {
      id: 'other',
      heading: 'Ce que le site ne fait pas',
      paragraphs: [
        'Pas de comptes. Pas de publicité. Pas d’outil d’analyse tiers. Pas de cookies de suivi.',
      ],
    },
  ],
  back: 'Retour aux jeux',
};
