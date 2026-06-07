import type { Messages } from './Messages';

export const messagesEn: Messages = {
  rules: {
    toggleLabel: 'Rules',
    panelTitle: 'How to play',
    closeLabel: 'Close',
    sections: [
      {
        title: 'Goal',
        body: ['Win all the cards. The game ends when one player has them all.'],
      },
      {
        title: 'Send',
        body: ['On your turn, send your top card to the central pile. Play then passes to your opponent.'],
      },
      {
        title: 'Slap',
        body: [
          'Either player can slap when the pile shows:',
          'Doubles — top two cards same rank.',
          'Sandwich — top card matches the card two below.',
          'Sum of ten — top two cards add up to ten.',
          'Tens — top card is a ten.',
          'First to slap a valid pile takes it all.',
        ],
      },
      {
        title: 'Wrong slap',
        body: ['Slap with no valid pattern and two of your cards go under the pile.'],
      },
      {
        title: 'Honour cards (J Q K A)',
        body: ["Send one and your opponent must answer with an honour card within a few cards: J → 1, Q → 2, K → 3, A → 4. If they don’t, the pile is yours to grab."],
      },
      {
        title: 'Grab',
        body: ['When the pile becomes grabbable, grab it to take every card.'],
      },
    ],
  },
};
