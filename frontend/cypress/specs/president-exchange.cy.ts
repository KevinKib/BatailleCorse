// The exchange step of President, from both sides, on fixed deals (PUT /api/test/president/{id}/deal, test profile
// only). Round 1 is scripted to a known order, then round 2 opens on the exchange:
//  - as the President, the browser sees the cards it received tagged, uses Suggest and gives two cards back;
//  - as the Scum, the browser sees its best cards leave, waits, and gets the President's return.
// Everything is waited on through DOM states and frames, never a fixed delay.
import {
  awaitState, hostLobby, joinGuests, noHorizontalScroll, scriptedPass, scriptedPlay, scriptedReady, threePlayerDeal,
} from '../support/presidentRoom';
import { dealFrom, roundOneDealBrowserLast, without } from '../support/presidentExchangeDeals';

const SUITS = ['HEART', 'DIAMOND', 'CLUB'];
/** Round 1 where the browser (seat 0) goes out first, then Bob, then Carol (the same deal as president-game). */
function roundOneDealBrowserFirst() {
  return threePlayerDeal();
}

const handCard = (suit: string, rank: string) => `[data-card="${suit}_${rank}"]`;

/** Taps the three cards of `rank`, plays them and waits for the pile to show them. */
function playTriple(rank: string, goesOut = false) {
  cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
  SUITS.forEach((suit) => cy.get(handCard(suit, rank)).click({ force: true }));
  cy.get('[data-test="play"]').should('not.be.disabled').click();
  if (goesOut) return;
  cy.get('[data-test="trick-by"]').should('have.text', 'You played');
  cy.get('[data-test="phase-text"]').should('not.have.text', 'Your turn');
}

function browserPass() {
  cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
  cy.get('[data-test="pass"]').should('not.be.disabled').click();
}

[375, 1280].forEach((width) => {
  describe(`President exchange at ${width} px`, () => {
    beforeEach(() => { cy.viewport(width, width === 375 ? 667 : 800); });

    it('as the President: tagged cards, Suggest, Give', () => {
      hostLobby('Alice').then((id) => {
        joinGuests(id, ['Bob', 'Carol']).then(([bob, carol]) => {
          // Round 2: Carol (Scum) deals, so the browser is dealt first and holds 18 cards. Carol holds the 2s of
          // clubs and spades (her two best); the browser's low cards are 3C, a pair of 4s, a pair of 5s, a triple of
          // 6s, a pair of 9s, then 10C: Suggest keeps every pair and picks 3C and 10C.
          const mine = ['3C', '4C', '4D', '5C', '5D', '6C', '6D', '6H', '9C', '9D', '10C', 'JC', 'QC', 'QD', 'KC', 'AC', 'AD', '2D'];
          const rest = without(mine, ['2C', '2S', '2H']);
          const carols = ['2C', '2S', ...rest.slice(0, 15)];
          const bobs = [...rest.slice(15), '2H'];
          cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: roundOneDealBrowserFirst() });
          cy.request('PUT', `/api/test/president/${id}/deal`, { round: 2, cards: dealFrom(mine, bobs, carols) });
          cy.get('[data-test="start"]').should('not.be.disabled').click();
          cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');

          // Round 1: the browser goes out first, Bob second, Carol last.
          ['KING', 'QUEEN', 'JACK', '10', '9'].forEach((rank) => {
            playTriple(rank);
            scriptedPass(bob);
            scriptedPass(carol);
          });
          playTriple('8', true);
          ['7', '6', '5', '4', '3'].forEach((rank) => {
            scriptedPlay(bob, rank, 3);
            scriptedPass(carol);
          });
          scriptedPlay(bob, 'ACE', 2);
          cy.get('[data-test="round-over-title"]', { timeout: 10000 }).should('have.text', 'Round 1 is over');
          cy.get('[data-test="ready"]').click();
          scriptedReady(bob);
          scriptedReady(carol);

          // The exchange, the President's side.
          cy.get('[data-test="exchange-panel"]', { timeout: 10000 }).should('be.visible');
          cy.get('[data-test="exchange-role"]').should('have.text', 'You are the President');
          cy.get('[data-test="exchange-received"]')
            .should('have.text', 'Carol gave you their 2 best cards. They are tagged in your hand.');
          cy.get('[data-test="exchange-instruction"]').should('have.text', 'Choose 2 cards to give to Carol.');
          cy.get('[data-test="exchange-pair"]').should('have.length', 1)
            .and('have.text', 'Alice and Carol: 2 cards, pending');
          cy.get('[data-test^="hand-card-"]').should('have.length', 20);
          // The two cards that came from the Scum wear a visible tag and nothing else does.
          cy.get('[data-received="true"]').should('have.length', 2);
          cy.get(handCard('CLUB', '2')).should('have.attr', 'data-received', 'true')
            .and('have.attr', 'aria-label', '2 of clubs, received');
          cy.get(handCard('SPADE', '2')).should('have.attr', 'data-received', 'true');
          cy.get('[data-test="received-tag"]').should('have.length', 2).first().should('be.visible').and('have.text', 'New');
          cy.get('[data-test="exchange-give"]').should('have.text', 'Give 2 cards to Carol').and('be.disabled');
          cy.get('[data-test="exchange-clear"]').should('not.exist');
          noHorizontalScroll();
          // The waiting seats see the same pair with no card in it.
          awaitState(bob, 'the exchange is pending', (s) => s.phase === 'EXCHANGE' && s.exchange.length === 1);

          // One card is not enough; Suggest picks the lowest cards that break no pair, whatever was selected.
          cy.get(handCard('CLUB', '6')).click({ force: true });
          cy.get('[data-test="exchange-give"]').should('be.disabled');
          cy.get('[data-test="exchange-clear"]').click();
          cy.get('[data-test="exchange-clear"]').should('not.exist');
          cy.get('[aria-pressed="true"]').should('not.exist');
          cy.get('[data-test="exchange-suggest"]').click();
          cy.get('[aria-pressed="true"]').should('have.length', 2);
          cy.get(handCard('CLUB', '3')).should('have.attr', 'aria-pressed', 'true');
          cy.get(handCard('CLUB', '10')).should('have.attr', 'aria-pressed', 'true');
          cy.get(handCard('CLUB', '4')).should('have.attr', 'aria-pressed', 'false');
          cy.get('[data-test="exchange-give"]').should('not.be.disabled').click();

          // Done: the Scum has the two cards and leads; the panel is gone.
          awaitState(carol, 'the Scum holds the returned cards and the round is on', (s) =>
            s.round === 2 && s.phase === 'PLAYING'
            && s.hand.some((c: any) => c.suit === 'CLUB' && c.rank === '3')
            && s.hand.some((c: any) => c.suit === 'CLUB' && c.rank === '10'));
          cy.get('[data-test="exchange-panel"]').should('not.exist');
          cy.get('[data-test="phase-text"]', { timeout: 10000 }).should('have.text', 'Carol is playing');
          cy.get('[data-test^="hand-card-"]').should('have.length', 18);
          noHorizontalScroll();
        });
      });
    });

    it('as the Scum: the best cards leave, then the return arrives', () => {
      hostLobby('Alice').then((id) => {
        joinGuests(id, ['Bob', 'Carol']).then(([bob, carol]) => {
          // Round 2: the browser (Scum) deals, so Bob (President) is dealt first and holds 18 cards. The browser
          // holds the 2s of clubs and spades, its best two cards, which move to Bob at once.
          const mine = ['2C', '2S', '4C', '5C', '6C', '7C', '8C', '9C', '10C', 'JC', 'QC', 'KC', 'AC', '3S', '4S', '5S', '6S'];
          const rest = without(mine);
          cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: roundOneDealBrowserLast() });
          cy.request('PUT', `/api/test/president/${id}/deal`, {
            round: 2, cards: dealFrom(rest.slice(17), rest.slice(0, 17), mine),
          });
          cy.get('[data-test="start"]').should('not.be.disabled').click();
          cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');

          // Round 1: Bob goes out first (the browser and Carol pass), then Carol (the browser passes), the
          // browser stays last.
          ['KING', 'QUEEN', 'JACK', '10', '9'].forEach((rank) => {
            scriptedPlay(bob, rank, 3);
            scriptedPass(carol);
            browserPass();
          });
          scriptedPlay(bob, '8', 2);
          ['7', '6', '5', '4', '3'].forEach((rank) => {
            scriptedPlay(carol, rank, 3);
            browserPass();
          });
          scriptedPlay(carol, 'ACE', 2);
          cy.get('[data-test="round-over-title"]', { timeout: 10000 }).should('have.text', 'Round 1 is over');
          cy.get('[data-test="round-over-place"]').then(($rows) => {
            const row = (i: number, part: string) => $rows.eq(i).find(`[data-test="place-${part}"]`).text();
            expect(`${row(0, 'label')} ${row(0, 'title')}`, '1st').to.eq('1. Bob President');
            expect(`${row(2, 'label')} ${row(2, 'title')}`, '3rd').to.eq('3. Alice Scum');
          });
          cy.get('[data-test="ready"]').click();
          scriptedReady(bob);
          scriptedReady(carol);

          // The Scum's side: nothing to do but wait; the pair is visible, with no card in it.
          cy.get('[data-test="exchange-panel"]', { timeout: 10000 }).should('be.visible');
          cy.get('[data-test="exchange-role"]').should('have.text', 'You are the Scum');
          cy.get('[data-test="exchange-instruction"]')
            .should('have.text', 'Your 2 best cards went to Bob. Waiting for them to give 2 back.');
          cy.get('[data-test="exchange-pair"]').should('have.text', 'Bob and Alice: 2 cards, pending');
          cy.get('[data-test="exchange-give"]').should('not.exist');
          cy.get('[data-test="exchange-suggest"]').should('not.exist');
          cy.get('[data-test^="hand-card-"]').should('have.length', 15);
          cy.get(handCard('CLUB', '2')).should('not.exist');
          noHorizontalScroll();

          // Bob (President) gives his two lowest cards back.
          awaitState(bob, 'Bob owes two cards', (s) => s.phase === 'EXCHANGE' && s.toGiveBack === 2);
          cy.then(() => {
            const back = bob.lastState().hand.slice(0, 2);
            cy.wrap(back).as('back');
            bob.publish('/app/president/giveBack', { cards: back });
          });

          // The return arrives: the Scum leads round 2 with 17 cards, and the panel is gone.
          cy.get('[data-test="exchange-panel"]', { timeout: 10000 }).should('not.exist');
          cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
          cy.get('[data-test^="hand-card-"]').should('have.length', 17);
          cy.get('@back').then((back) => {
            (back as unknown as Array<{ name: string }>).forEach((card) =>
              cy.get(`[data-card="${card.name}"]`).should('exist'));
          });
          noHorizontalScroll();
        });
      });
    });
  });
});
