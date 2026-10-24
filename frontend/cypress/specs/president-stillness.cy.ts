// The President table holds still (owner decision, option 1A "reserved slots"): a text, a button or a panel that comes
// and goes never moves the seats, the pile, my plate, my hand or the buttons by more than 1 px, on a phone and on a
// wide screen. The probe (support/presidentStillness.ts) records the boxes of those regions before and after each change
// of state: a selection, my turn starting and ending, a pass, a play, the closing badge, a seat going out, the equal
// play chain and its Complete button, the end of a round, the exchange and its recap.
import {
  awaitState, hostLobby, joinGuests, noHorizontalScroll, scriptedPass, scriptedPlay, scriptedReady, threePlayerDeal,
} from '../support/presidentRoom';
import { midRoundDeal, scriptedLead } from '../support/presidentMidRound';
import { dealFrom, without } from '../support/presidentExchangeDeals';
import { expectNothingMoved, resetMoves, stillAcross } from '../support/presidentStillness';

const NAMES = ['Bob', 'Carol', 'Dan', 'Erin', 'Fay', 'Gus', 'Hal'];
const SCREENS: Array<[number, number]> = [[375, 667], [360, 640], [375, 812], [1280, 720]];
const PLAYER_COUNTS = [3, 4, 6, 8];

beforeEach(resetMoves);

// `--env only=<text>` runs the scenarios whose title contains the text (a quicker loop while working on one screen size).
const scenario = (title: string, body: () => void) => {
  const only = Cypress.env('only') as string | undefined;
  (only && !title.includes(only) ? it.skip : it)(title, body);
};

const myTurn = () => cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
const notMyTurn = () => cy.get('[data-test="phase-text"]').should('not.have.text', 'Your turn');
const noBubble = () => cy.get('[data-test="seat-bubble"]', { timeout: 10000 }).should('not.exist');

SCREENS.forEach(([width, height]) => {
  PLAYER_COUNTS.forEach((players) => {
    scenario(`holds still through a round with ${players} players at ${width}x${height}`, () => {
      cy.viewport(width, height);
      hostLobby('Alice').then((id) => {
        joinGuests(id, NAMES.slice(0, players - 1)).then((guests) => {
          cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: midRoundDeal(players) });
          cy.get('[data-test="start"]').should('not.be.disabled').click();
          cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
          const [bob] = guests;
          myTurn();
          cy.screenshot(`president-still-${players}p-${width}x${height}-lead`, { capture: 'viewport' });

          // A selection, then none: the Clear button is always there, so Play and Pass never move.
          stillAcross('selecting a card', () => { cy.get('[data-card="HEART_KING"]').click({ force: true }); },
            () => { cy.get('[aria-pressed="true"]').should('have.length', 1); });
          stillAcross('clearing the selection', () => { cy.get('[data-test="clear"]').click(); },
            () => { cy.get('[aria-pressed="true"]').should('not.exist'); });

          // My turn ends: I play the king, Bob cuts with a 2 and the closing badge comes in on the pile.
          cy.get('[data-card="HEART_KING"]').click({ force: true });
          stillAcross('playing the king (my turn ends)', () => { cy.get('[data-test="play"]').should('not.be.disabled').click(); },
            () => { notMyTurn(); cy.get('[data-test="trick-card"]').should('have.length', 1); });
          stillAcross('a cut and its closing badge', () => { scriptedPlay(bob, '2', 1); },
            () => { cy.get('[data-test="trick-closed-reason"]').should('have.text', 'Closed by a 2'); });

          // Bob leads a four of a kind (it closes the trick), then a single that everybody passes.
          stillAcross('a four of a kind and a new lead', () => { scriptedPlay(bob, '4', 4); scriptedPlay(bob, '5', 1); },
            () => { cy.get('[data-test="trick-by"]', { timeout: 15000 }).should('have.text', 'Bob played'); });
          stillAcross('the passes and my turn starting', () => { guests.slice(1).forEach((guest) => scriptedPass(guest)); },
            () => { myTurn(); cy.get('[data-test="seat-passed"]').should('have.length', players - 2); noBubble(); });

          // I pass (my turn ends); Bob leads his last card, a 2: out on a 2, the trick closes.
          stillAcross('passing (my turn ends)', () => { cy.get('[data-test="pass"]').should('not.be.disabled').click(); },
            () => { notMyTurn(); });
          // With 8 players Bob holds exactly the seven cards he is dealt: his last 2 puts him out (the other table sizes
          // leave him cards nobody planned for).
          if (players === 8) {
            stillAcross('a seat going out on a 2', () => { scriptedPlay(bob, '2', 1); },
              () => { cy.get('[data-test="seat-out"]').should('have.length', 1); cy.get('[data-test="trick-closed"]').should('exist'); noBubble(); });

            stillAcross('passes with a seat out', () => { scriptedLead(guests[1]); guests.slice(2).forEach((guest) => scriptedPass(guest)); },
              () => { cy.get('[data-test="seat-passed"]').should('have.length', players - 3); myTurn(); noBubble(); });
          }
          noHorizontalScroll();
          expectNothingMoved();
        });
      });
    });
  });
});

// Equal play on the fixed deal of president-equal-play: the chips (the binding, the chain) and the Complete button
// come and go without moving anything.
const SEATS = [
  ['KH', 'KS', '5H', '9C', '9S', '6H', '8H', '2H', '2D', '2C', '3H', '3D', '3C', '3S', '4H', '4D', '4C', '4S'],
  ['KD', '9H', '6D', '2S', '5D', '5C', '5S', '7H', '7D', '7C', '7S', '10H', '10D', '10C', '10S', 'JH', 'JD'],
  ['KC', '9D', '6C', '6S', '8D', '8C', '8S', 'JC', 'JS', 'QH', 'QD', 'QC', 'QS', 'AH', 'AD', 'AC', 'AS'],
];
const equalPlayDeal = () => dealFrom(SEATS[0], SEATS[1], SEATS[2]);

function selectAndPlay(card: string) {
  cy.get(`[data-card="${card}"]`).click({ force: true });
  cy.get('[data-test="play"]').should('not.be.disabled').click();
}

SCREENS.forEach(([width, height]) => {
  scenario(`holds still through the equal play chain at ${width}x${height}`, () => {
    cy.viewport(width, height);
    hostLobby('Alice').then((id) => {
      joinGuests(id, ['Bob', 'Carol']).then(([bob, carol]) => {
        cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: equalPlayDeal() });
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
        myTurn();

        stillAcross('leading the king', () => { selectAndPlay('HEART_KING'); },
          () => { cy.get('[data-test="phase-text"]').should('have.text', 'Bob is playing'); });
        stillAcross('a king answered: King or nothing', () => { scriptedPlay(bob, 'KING', 1); },
          () => { cy.get('[data-test="binding"]').should('contain.text', 'King').and('contain.text', 'or nothing'); cy.get('[data-test="binding-pip"].binding-pip--on').should('have.length', 2); });
        stillAcross('the chain of three and the Complete button', () => { scriptedPlay(carol, 'KING', 1); },
          () => { myTurn(); cy.get('[data-test="complete"]').should('be.visible'); cy.get('[data-test="binding-pip"].binding-pip--on').should('have.length', 3); cy.get('[data-test="binding-pip"]').should('have.length', 4); });
        cy.screenshot(`president-still-binding-${width}x${height}`, { capture: 'viewport' });
        stillAcross('the fourth king closes the trick', () => { selectAndPlay('SPADE_KING'); },
          () => { myTurn(); cy.get('[data-test="trick-card"]').should('not.exist'); cy.get('[data-test="complete"]').should('not.exist'); });

        // A 5 answered by two 9s: a binding on 9 and a chain of two.
        stillAcross('leading a 5', () => { selectAndPlay('HEART_5'); },
          () => { cy.get('[data-test="phase-text"]').should('have.text', 'Bob is playing'); });
        stillAcross('two nines: 9 or nothing', () => { scriptedPlay(bob, '9', 1); scriptedPlay(carol, '9', 1); },
          () => { myTurn(); cy.get('[data-test="binding-rank"]').should('have.text', '9'); cy.get('[data-test="binding-pip"].binding-pip--on').should('have.length', 2); });
        stillAcross('a card that cannot be played, and its hint', () => { cy.get('[data-card="HEART_3"]').click({ force: true }); },
          () => { cy.get('[data-test="play-hint"]').should('have.text', 'Play a 9 or nothing.'); });
        stillAcross('clearing it', () => { cy.get('[data-test="clear"]').click(); },
          () => { cy.get('[aria-pressed="true"]').should('not.exist'); });
        stillAcross('passing on the binding', () => { cy.get('[data-test="pass"]').should('not.be.disabled').click(); },
          () => { cy.get('[data-test="phase-text"]').should('have.text', 'Bob is playing'); cy.get('[data-test="complete"]').should('be.visible'); });
        stillAcross('completing the four out of turn', () => { cy.get('[data-test="complete"]').click(); },
          () => { myTurn(); cy.get('[data-test="complete"]').should('not.exist'); });
        noHorizontalScroll();
        expectNothingMoved();
      });
    });
  });
});

// The end of a round, the exchange and its recap (3 players on the fixed deals of president-exchange): the felt keeps
// its size when the round sheet takes the place of my zone, when the exchange panel does, and when the recap comes.
const SUITS = ['HEART', 'DIAMOND', 'CLUB'];
function playTriple(rank: string) {
  myTurn();
  SUITS.forEach((suit) => cy.get(`[data-card="${suit}_${rank}"]`).click({ force: true }));
  cy.get('[data-test="play"]').should('not.be.disabled').click();
}

SCREENS.forEach(([width, height]) => {
  scenario(`holds still through the end of a round, the exchange and its recap at ${width}x${height}`, () => {
    cy.viewport(width, height);
    hostLobby('Alice').then((id) => {
      joinGuests(id, ['Bob', 'Carol']).then(([bob, carol]) => {
        const mine = ['3C', '4C', '4D', '5C', '5D', '6C', '6D', '6H', '9C', '9D', '10C', 'JC', 'QC', 'QD', 'KC', 'AC', 'AD', '2D'];
        const rest = without(mine, ['2C', '2S', '2H']);
        const carols = ['2C', '2S', ...rest.slice(0, 15)];
        const bobs = [...rest.slice(15), '2H'];
        cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: threePlayerDeal() });
        cy.request('PUT', `/api/test/president/${id}/deal`, { round: 2, cards: dealFrom(mine, bobs, carols) });
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');

        ['KING', 'QUEEN', 'JACK', '10', '9'].forEach((rank) => {
          playTriple(rank);
          scriptedPass(bob);
          scriptedPass(carol);
        });
        stillAcross('going out', () => { playTriple('8'); },
          () => { cy.get('[data-test="my-zone"] [data-test="seat-out"]').should('have.attr', 'data-place', '1').and('contain.text', '1st'); });
        ['7', '6', '5', '4', '3'].forEach((rank) => {
          scriptedPlay(bob, rank, 3);
          scriptedPass(carol);
        });
        stillAcross('the round is over: the sheet', () => { scriptedPlay(bob, 'ACE', 2); },
          () => { cy.get('[data-test="sheet-slot"] [data-test="round-over"]', { timeout: 120000 }).should('be.visible'); });
        cy.screenshot(`president-still-sheet-${width}x${height}`, { capture: 'viewport' });
        stillAcross('the exchange panel replaces the sheet', () => {
          cy.get('[data-test="ready"]').click();
          scriptedReady(bob);
          scriptedReady(carol);
        }, () => { cy.get('[data-test="exchange-panel"]', { timeout: 120000 }).should('be.visible'); });
        cy.screenshot(`president-still-exchange-${width}x${height}`, { capture: 'viewport' });
        awaitState(bob, 'the exchange is pending', (s) => s.phase === 'EXCHANGE' && s.exchange.length === 1);
        stillAcross('choosing the cards to give', () => { cy.get('[data-test="exchange-suggest"]').click(); },
          () => { cy.get('[aria-pressed="true"]').should('have.length', 2); });
        stillAcross('the exchange is done', () => { cy.get('[data-test="exchange-give"]').should('not.be.disabled').click(); },
          () => { cy.get('[data-test="exchange-panel"]').should('not.exist'); cy.get('[data-test="phase-text"]', { timeout: 10000 }).should('have.text', 'Carol is playing'); });
        stillAcross('the recap of the exchange comes in', () => { /* it follows the flights */ },
          () => { cy.get('[data-test="exchange-recap"]', { timeout: 20000 }).should('be.visible'); });
        cy.screenshot(`president-still-recap-${width}x${height}`, { capture: 'viewport' });
        stillAcross('closing the recap', () => { cy.get('[data-test="recap-close"]').click({ force: true }); },
          () => { cy.get('[data-test="exchange-recap"]').should('not.exist'); });
        noHorizontalScroll();
        expectNothingMoved();
      });
    });
  });
});
