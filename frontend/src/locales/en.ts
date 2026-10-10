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
  bullshit: {
    toggleLabel: 'Rules',
    panelTitle: 'How to play',
    closeLabel: 'Close',
    sections: [
      {
        kind: 'text',
        title: 'Goal',
        body: ['Be the first to empty your hand — and survive the challenge on your very last card.'],
      },
      {
        kind: 'text',
        title: 'The deal',
        body: ['The whole deck is dealt out. Everyone starts with a fistful of cards.'],
      },
      {
        kind: 'text',
        title: 'Your turn',
        body: ['Place 1 to 4 cards face-down and claim they are the current rank. You choose how many and which cards — so you can bluff.'],
      },
      {
        kind: 'cycle',
        title: 'The claim advances on its own',
        steps: ['A', '2', '3', '4', '…', 'K'],
        loops: true,
        caption: 'The claimed rank is fixed each turn and steps forward automatically, then wraps back to A and keeps cycling. You never pick the rank — only your cards.',
        note: 'Some variants cycle suits (♥ ♦ ♣ ♠) instead of ranks — it still advances on its own.',
      },
      {
        kind: 'branch',
        title: 'Call Bullshit',
        intro: 'Think the latest discard is a lie? Call Bullshit — but never on your own discard. The cards flip face-up:',
        outcomes: [
          { tone: 'negative', condition: "Cards don't match — it was a bluff", result: 'The bluffer picks up the whole pile.' },
          { tone: 'positive', condition: 'Cards match — the claim was true', result: 'The caller picks up the whole pile.' },
        ],
      },
      {
        kind: 'text',
        title: 'Winning',
        body: ['Play your last card and survive the call on it to win. Get caught bluffing on it and the whole pile comes back to you.'],
      },
    ],
  },
};
