// President equal play on a fixed deal (PUT /api/test/president/{id}/deal, test profile only). The browser is seat 0
// (Alice), Bob and Carol are scripted. Three tricks:
//  1. the four kings are played in succession, round the table: the fourth play, in turn, closes the trick;
//  2. Alice passes on a "9 or nothing" binding, then completes the four nines out of turn with the button;
//  3. Carol, who passed, sends her completion after a play: the server refuses it to her alone.
import {
  awaitState, hostLobby, joinGuests, noHorizontalScroll, scriptedPass, scriptedPlay,
} from '../support/presidentRoom';

const SEATS = [
  // Alice: the king of hearts leads round 1; the kings, the nines and the sixes are her cards of the tricks.
  ['KH', 'KS', '5H', '9C', '9S', '6H', '8H', '2H', '2D', '2C', '3H', '3D', '3C', '3S', '4H', '4D', '4C', '4S'],
  ['KD', '9H', '6D', '2S', '5D', '5C', '5S', '7H', '7D', '7C', '7S', '10H', '10D', '10C', '10S', 'JH', 'JD'],
  ['KC', '9D', '6C', '6S', '8D', '8C', '8S', 'JC', 'JS', 'QH', 'QD', 'QC', 'QS', 'AH', 'AD', 'AC', 'AS'],
];

/** The deal goes seat 0, 1, 2, 0, ... */
function equalPlayDeal(): string[] {
  const deal: string[] = [];
  for (let i = 0; i < 17; i++) deal.push(SEATS[0][i], SEATS[1][i], SEATS[2][i]);
  deal.push(SEATS[0][17]);
  return deal;
}

/** Taps one card of my hand and plays it. */
function playOne(card: string) {
  cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
  cy.get(`[data-card="${card}"]`).click({ force: true });
  cy.get('[data-test="play"]').should('not.be.disabled').click();
}

const chipTexts = () => cy.get('[data-test="rule-chip"]').then(($c) => [...$c].map((c) => c.textContent?.trim()));

[375, 1280].forEach((width) => {
  describe(`President equal play at ${width} px`, () => {
    it('closes the four in turn, completes out of turn after a pass, and refuses a completion that came too late', () => {
      cy.viewport(width, width === 375 ? 667 : 800);
      hostLobby('Alice').then((id) => {
        joinGuests(id, ['Bob', 'Carol']).then(([bob, carol]) => {
          cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: equalPlayDeal() });
          cy.get('[data-test="start"]').should('not.be.disabled').click();
          cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
          cy.get('[data-test="complete"]').should('not.exist');

          // 1. The four kings round the table, closed in turn by Alice.
          playOne('HEART_KING');
          scriptedPlay(bob, 'KING', 1);
          scriptedPlay(carol, 'KING', 1);
          cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
          chipTexts().then((texts) => {
            expect(texts).to.include('King or nothing');
            expect(texts).to.include('3 plays of King in a row');
            expect(texts).not.to.include('To beat: 1 × King');
          });
          // Alice holds the fourth king and plays in turn: no button is needed, the play is the completion.
          cy.get('[data-test="complete"]').should('be.visible').and('have.text', 'Complete the four');
          noHorizontalScroll();
          playOne('SPADE_KING');
          // The four closed the trick: Alice leads again on an empty table.
          cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
          cy.get('[data-test="trick-card"]').should('not.exist');
          cy.get('[data-test="complete"]').should('not.exist');

          // 2. Alice leads a 5, Bob and Carol answer with 9s; Alice passes and completes out of turn.
          playOne('HEART_5');
          scriptedPlay(bob, '9', 1);
          scriptedPlay(carol, '9', 1);
          cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
          chipTexts().then((texts) => {
            expect(texts).to.include('9 or nothing');
            expect(texts).to.include('2 plays of 9 in a row');
          });
          cy.get('[data-test="pass"]').should('not.be.disabled').click();
          cy.get('[data-test="phase-text"]').should('have.text', 'Bob is playing');
          cy.get('[data-test="complete"]').should('be.visible').and('not.be.disabled');
          noHorizontalScroll();
          cy.get('[data-test="complete"]').click();
          cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
          cy.get('[data-test="trick-card"]').should('not.exist');
          cy.get('[data-test="complete"]').should('not.exist');

          // 3. Carol passes on a pair of sixes; Alice plays on, so Carol's completion comes too late.
          playOne('HEART_6');
          scriptedPlay(bob, '6', 1);
          scriptedPass(carol);
          playOne('HEART_8');
          cy.get('[data-test="phase-text"]').should('have.text', 'Bob is playing');
          cy.get('[data-test="complete"]').should('not.exist');
          awaitState(carol, 'Carol sees the eight on the table', (s) => s.trick.plays.length === 3);
          cy.then(() => {
            const before = carol.lastState().version;
            const cards = carol.lastState().hand.filter((c: any) => c.rank === '6');
            expect(cards, 'Carol holds both missing sixes').to.have.length(2);
            // The connection probe of a guest was itself refused: count the refusals, do not look for one.
            const refusals = (p: { frames: Array<{ success?: boolean }> }) => p.frames.filter((f) => f.success === false).length;
            const carolBefore = refusals(carol);
            const bobBefore = refusals(bob);
            carol.publish('/app/president/play', { cards });
            cy.wrap(carol, { timeout: 10000 }).should((p) => {
              expect(refusals(p), 'a refusal reached Carol').to.eq(carolBefore + 1);
            });
            cy.then(() => {
              expect(carol.lastState().version, 'nothing changed for Carol').to.eq(before);
              expect(refusals(bob), 'Bob was not told').to.eq(bobBefore);
            });
          });
          // The table is as it was: one eight on the pile, Bob to play.
          cy.get('[data-test="trick-card"]').should('have.length', 1);
          cy.get('[data-test="phase-text"]').should('have.text', 'Bob is playing');
          noHorizontalScroll();
        });
      });
    });
  });
});
