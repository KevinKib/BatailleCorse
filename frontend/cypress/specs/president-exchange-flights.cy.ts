// The exchange flights of President (spec §11 decision 7, chunk 2 of the animations), on fixed deals (PUT
// /api/test/president/{id}/deal, test profile only). The browser is the Scum (Alice, seat 0), Bob the President and
// Carol Neutral, both scripted:
//  1. at the deal of round 2 the Scum's two best cards (the 2 of clubs and the 2 of spades) fly to Bob: copies of the
//     faces travel, the rings mark both ends, Bob's fan counts the cards only when they have landed (18 then 20);
//  2. Bob gives two cards back: they fly to my hand and land in it (17 cards);
//  3. a small summary then names who gave what to whom, with the faces of my pair, and can be closed;
//  4. at the Instant speed nothing flies: the same rings, still, and the same summary.
// Every wait is on a DOM state, never a fixed delay.
import {
  awaitState, hostLobby, joinGuests, noHorizontalScroll, scriptedPass, scriptedPlay, scriptedReady, type Guest,
} from '../support/presidentRoom';
import { dealFrom, roundOneDealBrowserLast, without } from '../support/presidentExchangeDeals';
import { autoplay, connectMyself } from '../support/presidentAutoplay';

const bobSeat = '.seat-slot[data-seat="1"]';

/** Plays round 1 so that the browser ends last (Scum), then every seat presses ready: round 2 opens on the exchange. */
function playRoundOne(bob: Guest, carol: Guest) {
  const browserPass = () => {
    cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
    cy.get('[data-test="pass"]').should('not.be.disabled').click();
  };
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
  // Let the last plays finish flying, so that what flies next is the exchange alone.
  cy.get('[data-test="motion-card"]', { timeout: 10000 }).should('not.exist');
  cy.get('[data-test="ready"]').click();
  scriptedReady(bob);
  scriptedReady(carol);
}

function startAsScum(afterStart?: () => void) {
  return hostLobby('Alice').then((id) => joinGuests(id, ['Bob', 'Carol']).then(([bob, carol]) => {
    // The browser (Scum) deals round 2, so Bob (President) is dealt first and holds 18 cards; the browser holds the 2s
    // of clubs and spades, its best two cards.
    const mine = ['2C', '2S', '4C', '5C', '6C', '7C', '8C', '9C', '10C', 'JC', 'QC', 'KC', 'AC', '3S', '4S', '5S', '6S'];
    const rest = without(mine);
    cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: roundOneDealBrowserLast() });
    cy.request('PUT', `/api/test/president/${id}/deal`, { round: 2, cards: dealFrom(rest.slice(17), rest.slice(0, 17), mine) });
    cy.get('[data-test="start"]').should('not.be.disabled').click();
    cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
    afterStart?.();
    playRoundOne(bob, carol);
    return cy.wrap({ bob, carol }, { log: false });
  }));
}

function bobGivesTwoCardsBack(bob: Guest) {
  awaitState(bob, 'Bob owes two cards', (s) => s.phase === 'EXCHANGE' && s.toGiveBack === 2);
  cy.then(() => {
    const back = bob.lastState().hand.slice(0, 2);
    bob.publish('/app/president/giveBack', { cards: back });
  });
}

[375, 1280].forEach((width) => {
  describe(`President exchange flights at ${width} px`, () => {
    beforeEach(() => {
      cy.viewport(width, width === 375 ? 667 : 720);
      cy.window().then((win) => win.localStorage.removeItem('animationSpeed'));
    });

    it('the Scum sees the best cards leave, the return arrive, and reads who gave what', () => {
      startAsScum().then(({ bob }) => {
        // 1. The deal: two copies of my best cards fly from my hand to Bob, ringed at both ends.
        cy.get('[data-test="exchange-panel"]', { timeout: 10000 }).should('be.visible');
        cy.get('[data-test="motion-card"]').should('have.length.at.least', 1);
        cy.get('[data-test="motion-mark"]').should('have.length.at.least', 2);
        // The flight carries the face I give, not a card back.
        cy.get(`${bobSeat} [data-test="fan-back"] img`).first().invoke('attr', 'src').then((back) => {
          cy.get('[data-test="motion-card"] img').should(($imgs) => {
            expect([...$imgs].some((i) => i.getAttribute('src') !== back), 'a face in flight').to.eq(true);
          });
        });
        cy.screenshot(`exchange-flight-deal-${width}`, { capture: 'viewport' });
        // Once the cards have landed Bob's plate says what his state says: 18 dealt and the 2 of mine.
        cy.get(`${bobSeat} [data-test="seat-cards"]`).should('have.text', '20 cards');
        // What the panel shows has not changed.
        cy.get('[data-test="exchange-role"]').should('have.text', 'You are the Scum');
        cy.get('[data-test="exchange-instruction"]')
          .should('have.text', 'Your 2 best cards went to Bob. Waiting for them to give 2 back.');
        cy.get('[data-test="exchange-pair"]').should('have.text', 'Bob and Alice: 2 cards, pending');
        cy.get('[data-test^="hand-card-"]').should('have.length', 15);
        cy.get('[data-test="exchange-recap"]').should('not.exist');
        noHorizontalScroll();

        // 2. Bob gives two cards back: they fly to my hand, which then holds 17 cards.
        bobGivesTwoCardsBack(bob);
        cy.get('[data-test="motion-card"]').should('have.length.at.least', 1);
        cy.get('[data-test="exchange-panel"]', { timeout: 10000 }).should('not.exist');
        cy.get('[data-test^="hand-card-"]').should('have.length', 17);
        cy.get('.hand-card.landing').should('not.exist');
        cy.get(`${bobSeat} [data-test="seat-cards"]`).should('have.text', '18 cards');

        // 3. The summary names who gave what to whom, with the faces in my pair.
        cy.get('[data-test="exchange-recap"]').should('be.visible');
        cy.get('[data-test="recap-given"]').should('have.text', 'Alice gave Bob:');
        cy.get('[data-test="recap-given-cards"] img').should('have.length', 2)
          .then(($imgs) => {
            expect([...$imgs].map((i) => i.getAttribute('alt')).sort()).to.deep.eq(['2 of clubs', '2 of spades']);
          });
        cy.get('[data-test="recap-returned"]').should('have.text', 'Bob gave back:');
        cy.get('[data-test="recap-returned-cards"] img').should('have.length', 2);
        cy.get('[data-test="president-table"]').should(($felt) => {
          expect($felt[0].scrollHeight, 'felt content height').to.be.at.most($felt[0].clientHeight + 1);
        });
        cy.screenshot(`exchange-recap-${width}`, { capture: 'viewport' });
        noHorizontalScroll();
        // The summary does not push the table off the screen, nor hide the Play and Pass buttons.
        cy.window().then((win) => {
          expect(win.document.documentElement.scrollHeight, 'page height').to.be.at.most(win.innerHeight);
        });
        cy.get('[data-test="play"]').should('be.visible');
        cy.get('[data-test="pass"]').should('be.visible');
        cy.get('[data-test="recap-close"]').click();
        cy.get('[data-test="exchange-recap"]').should('not.exist');
      });
    });
  });
});

describe('President exchange flights at the Instant speed', () => {
  it('rings the places but flies nothing, and still summarises the exchange', () => {
    cy.viewport(375, 667);
    cy.window().then((win) => win.localStorage.removeItem('animationSpeed'));
    startAsScum(() => {
      cy.get('[data-test="speed-button"]').click();
      cy.get('[data-test="speed-button"]').click();
      cy.get('[data-test="speed-button"]').should('have.attr', 'aria-label', 'Animation speed: Instant');
    }).then(({ bob }) => {
      cy.get('[data-test="exchange-panel"]', { timeout: 10000 }).should('be.visible');
      cy.get('[data-test="motion-mark"]').should('have.length.at.least', 2);
      cy.get('[data-test="motion-card"]').should('not.exist');
      cy.get(`${bobSeat} [data-test="seat-cards"]`).should('have.text', '20 cards');
      bobGivesTwoCardsBack(bob);
      cy.get('[data-test="exchange-panel"]', { timeout: 10000 }).should('not.exist');
      cy.get('[data-test="motion-card"]').should('not.exist');
      cy.get('[data-test^="hand-card-"]').should('have.length', 17);
      cy.get('[data-test="recap-given"]').should('have.text', 'Alice gave Bob:');
    });
  });
});

// Eight players (seven bots that answer at once under the test profile, a scripted twin of the browser's seat that plays
// round 1): whatever title the browser ends with, round 2 opens on an exchange of several pairs. The flights ring the
// places, the table still fits on the screen, and the summary names every pair (the faces only in my own).
[[375, 667], [375, 812], [360, 640], [1280, 720]].forEach(([width, height]) => {
  describe(`President exchange flights with 8 players at ${width}x${height}`, () => {
    it('rings the exchange, keeps the table in the screen and summarises every pair', () => {
      cy.viewport(width, height);
      cy.window().then((win) => win.localStorage.removeItem('animationSpeed'));
      hostLobby('Alice').then((id) => {
        for (let i = 0; i < 7; i++) cy.get('[data-test="add-bot"]').click();
        cy.get('[data-test="player-count"]').should('contain.text', '8 / 8');
        cy.wrap(null, { log: false }).as('twin');
        connectMyself(id).then((me) => { autoplay(id, me, { ready: false }); cy.wrap(me, { log: false }).as('twin'); });
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
        cy.get('[data-test="round-over-title"]', { timeout: 120000 }).should('have.text', 'Round 1 is over');
        cy.get('[data-test="motion-card"]', { timeout: 20000 }).should('not.exist');
        cy.get('[data-test="ready"]').click();
        cy.get('[data-test="round"]', { timeout: 30000 }).should('have.text', 'Round 2 of 5');
        // Once the exchange is over the twin stops: the bots play up to my turn and wait for me, so that the round cannot
        // end under the summary.
        cy.get('@twin').then((me) => {
          awaitState(me as any, 'round 2 is on', (st) => st.round === 2 && st.phase === 'PLAYING');
          cy.then(() => (me as any).disconnect());
        });
        // The exchange moved cards between the pairs: their places are ringed (and the copies fly, unless it is over).
        cy.get('[data-test="motion-mark"]').should('have.length.at.least', 2);
        cy.screenshot(`exchange-8-players-flights-${width}x${height}`, { capture: 'viewport' });
        // The play has begun: the summary names every pair, and the felt does not scroll inside itself (measured in one
        // synchronous check).
        cy.get('[data-test="exchange-recap"]', { timeout: 20000 }).should(($recap) => {
          const doc = $recap[0].ownerDocument;
          const felt = doc.querySelector('[data-test="president-table"]') as HTMLElement;
          expect(doc.querySelectorAll('[data-test="recap-line"]').length, 'a line per pair').to.be.at.least(2);
          expect($recap.text()).to.contain('gave');
          expect(doc.documentElement.scrollWidth, 'page width').to.be.at.most(doc.documentElement.clientWidth);
          expect(doc.documentElement.scrollHeight, 'page height').to.be.at.most(doc.defaultView!.innerHeight);
          expect(doc.querySelectorAll('[data-test="opponent-seat"]').length, 'opponents').to.eq(7);
        });
        cy.screenshot(`exchange-8-players-recap-${width}x${height}`, { capture: 'viewport' });
        // The summary takes no height from the felt: what the felt overflows by (a mid-round table of 8 on a small phone
        // can already, with the titles and the passes written under the plates) is the same once it is closed.
        const overflow = ($felt: JQuery<HTMLElement>) => $felt[0].scrollHeight - $felt[0].clientHeight;
        cy.get('[data-test="president-table"]').then(($felt) => {
          const withRecap = overflow($felt);
          cy.log(`felt overflow with the summary: ${withRecap}px`);
          cy.get('[data-test="recap-close"]').click({ force: true });
          cy.get('[data-test="exchange-recap"]').should('not.exist');
          cy.get('[data-test="president-table"]').should(($after) => {
            expect(withRecap, 'felt overflow with the summary open').to.be.at.most(overflow($after) + 1);
          });
        });
      });
    });
  });
});
