// President: the texts of the table follow the pictures (owner feedback, 2026-10-21). On a fixed deal (PUT
// /api/test/president/{id}/deal, test profile only) the browser is seat 0 (Alice); Bob, Carol and Dave are scripted.
//  1. Bob leads a 7, Carol and Dave answer with 9s in a burst: the "9 or nothing" banner comes only once Dave's 9 has
//     flown in, never as soon as his state arrives.
//  2. Everyone passes, Dave sets the trick aside and leads at once: his count only ever drops (it never dips while the
//     trick is swept, then climbs back).
import { awaitState, hostLobby, joinGuests, scriptedPass, scriptedPlay } from '../support/presidentRoom';

const SEATS = [
  ['3H', '3D', '3C', '3S', '4H', '4D', '4C', '4S', '5H', '5D', '5C', '5S', '6H'],
  ['KH', '7H', '7D', '7C', '7S', '8H', '8D', '8C', '8S', '10H', '10D', '10C', '10S'],
  ['9H', '9D', 'JH', 'JD', 'JC', 'JS', 'QH', 'QD', 'QC', 'QS', 'KD', 'KC', 'KS'],
  ['9C', '9S', '6D', '6C', '6S', 'AH', 'AD', 'AC', 'AS', '2H', '2D', '2C', '2S'],
];

/** The deal goes seat 0, 1, 2, 3, 0, ... (Bob holds the king of hearts: he leads round 1). */
function deal(): string[] {
  const cards: string[] = [];
  for (let i = 0; i < 13; i++) SEATS.forEach(seat => cards.push(seat[i]));
  return cards;
}

interface Sample { t: number; binding: string | null; by: string | null; phase: string | null; dave: number | null }

/** Reads the table every 30 ms into `window.__samples` (what the player sees, over time). */
function startSampling() {
  cy.window().then((win) => {
    const samples: Sample[] = [];
    (win as any).__samples = samples;
    const text = (selector: string) => win.document.querySelector(selector)?.textContent?.trim() ?? null;
    (win as any).__sampler = win.setInterval(() => {
      const dave = text('.seat-slot[data-seat="3"] [data-test="seat-cards"]');
      samples.push({
        t: Date.now(),
        binding: text('[data-test="binding"]'),
        by: text('[data-test="trick-by"]'),
        phase: text('[data-test="phase-text"]'),
        dave: dave ? Number(dave.split(' ')[0]) : null,
      });
    }, 30);
  });
}

const samples = () => cy.window().then(win => (win as any).__samples as Sample[]);

describe('President texts follow the animations', () => {
  it('shows "9 or nothing" once the second 9 has landed, and a trick winner who leads at once never sees its count climb back', () => {
    cy.viewport(1280, 800);
    hostLobby('Alice').then((id) => {
      joinGuests(id, ['Bob', 'Carol', 'Dave']).then(([bob, carol, dave]) => {
        cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: deal() });
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
        startSampling();

        // 1. 7, 9, 9 in a burst.
        scriptedPlay(bob, '7', 1);
        scriptedPlay(carol, '9', 1);
        scriptedPlay(dave, '9', 1);
        awaitState(dave, 'Dave sees his 9 on the table', s => s.trick.plays.length === 3);
        cy.then(() => Date.now()).then((daveAt) => {
          // Dave's state is in, his 9 is not: no banner yet, and the table does not say it is my turn.
          cy.get('[data-test="trick"]').then(($trick) => {
            expect($trick.find('[data-test="binding"]').length, 'no banner when the state arrives').to.eq(0);
          });
          cy.get('[data-test="binding"]', { timeout: 10000 }).should('contain.text', '9').and('contain.text', 'or nothing');
          cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
          // The sampler has seen the banner too (it reads every 30 ms).
          cy.window().should(win => expect(((win as any).__samples as Sample[]).some(s => s.binding !== null)).to.eq(true));
          samples().then((all) => {
            const first = all.find(s => s.binding !== null)!;
            // A 9 flies 0.88 s at the Normal speed: the banner can only come after Dave's flight, not with his state.
            expect(first.t - daveAt, 'the banner waits for the flight').to.be.greaterThan(600);
            // Before the banner, the line said who played, in order, and never Dave.
            const before = all.filter(s => s.t < first.t).map(s => s.by).filter(Boolean);
            expect(before, 'Dave had not been told before the banner').not.to.include('Dave played');
            expect(first.by).to.eq('Dave played');
            expect(all.filter(s => s.t < first.t).map(s => s.phase), 'not my turn before the banner').not.to.include('Your turn');
          });
        });

        // 2. Everyone passes; Dave sets the trick aside and leads at once.
        cy.get('[data-test="pass"]').should('not.be.disabled').click();
        scriptedPass(bob);
        scriptedPass(carol);
        scriptedPlay(dave, '6', 1);
        awaitState(dave, 'Dave led his 6', s => s.trick.plays.length === 1 && s.trick.plays[0].seat === 3);
        cy.get('.seat-slot[data-seat="3"] [data-test="seat-cards"]', { timeout: 15000 }).should('have.text', '11 cards');
        cy.get('[data-test="trick-by"]', { timeout: 15000 }).should('have.text', 'Dave played');
        // The sampler has seen the last count too (it reads every 30 ms).
        cy.window().should((win) => {
          const all = (win as any).__samples as Sample[];
          expect(all[all.length - 1].dave).to.eq(11);
        });
        samples().then((all) => {
          const counts = all.map(s => s.dave).filter((n): n is number => n !== null);
          expect(counts[0], 'Dave held 13 cards').to.eq(13);
          counts.forEach((n, i) => {
            if (i > 0) expect(n, `Dave's count never climbs back (sample ${i})`).to.be.at.most(counts[i - 1]);
          });
        });
        cy.window().then(win => win.clearInterval((win as any).__sampler));
      });
    });
  });
});
