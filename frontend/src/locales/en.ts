import type { Messages } from './Messages';

export const messagesEn: Messages = {
  rules: {
    toggleLabel: 'Rules',
    panelTitle: 'How to play',
    closeLabel: 'Close',
    sections: [
      {
        kind: 'text',
        title: 'Goal',
        body: ['Win all the cards. The game ends when one player has them all.'],
      },
      {
        kind: 'text',
        title: 'Send',
        body: ['On your turn, send your top card to the central pile. Play then passes to your opponent.'],
      },
      {
        kind: 'slap',
        title: 'Slap when you see',
        labels: {
          doubles: 'Doubles',
          sandwich: 'Sandwich',
          sumOfTen: 'Sum of ten',
          tens: 'Tens',
        },
        footer: 'First to slap a valid pile takes it all.',
      },
      {
        kind: 'text',
        title: 'Wrong slap',
        body: ['Slap with no valid pattern and two of your cards go under the pile.'],
      },
      {
        kind: 'text',
        title: 'Honour cards (Jack, Queen, King, Ace)',
        body: ["Send one and your opponent must answer with an honour card within a few cards: Jack → 1, Queen → 2, King → 3, Ace → 4. If they don’t, the pile is yours to grab."],
      },
      {
        kind: 'text',
        title: 'Grab',
        body: ['When the pile becomes grabbable, grab it to take every card.'],
      },
    ],
  },
};
