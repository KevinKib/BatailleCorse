// The President exchange synchronisation, end to end (spec 2026-10-19-president-exchange-sync-design.md section 9):
// after the card exchange of round 2 the first trick waits until every human seat has seen the exchange.
// The browser (Alice, seat 0) is the Scum; Bob (seat 1, the President) is a scripted guest HELD BACK (it does not
// acknowledge by itself), Carol (seat 2) a scripted guest that acknowledges like a human who watched the exchange.
//  1. the server waits in EXCHANGE_VIEW: the browser reads "Waiting for Bob to see the exchange", Play and Pass are
//     disabled although the browser is the first lead, then Bob acknowledges and the browser's turn starts;
//  2. against a bot (the third seat), a held human keeps the bot from playing, then the play begins;
//  3. a reload during the view shows the board again (no flights) and acknowledges after the same reading time;
//  4. a seat that never acknowledges is let through by the server timeout (20 s by default);
//  5. at the Instant speed the browser acknowledges at once.
// Every wait is on a DOM state or a frame; the one fixed wait (2) is the negative check "nothing moved".
import {
  awaitState, hostLobby, noHorizontalScroll, scriptedPass, scriptedPlay, scriptedReady, type Guest,
} from '../support/presidentRoom';
import { dealFrom, roundOneDealBrowserLast, without } from '../support/presidentExchangeDeals';
import { joinAsScriptedPlayer } from '../support/stompPlayer';
import { autoplay, connectMyself } from '../support/presidentAutoplay';

const WAITING_FOR_BOB = 'Waiting for Bob to see the exchange';
/** Server timeout of the exchange view: 20 s by default; a stack started with another value passes it with `--env exchangeViewTimeoutMs=...`. */
const timeoutMs = () => Number(Cypress.env('exchangeViewTimeoutMs') ?? 20000);

/** Bob (held back) and Carol (acknowledging by herself) join the lobby, in seats 1 and 2. */
function joinBobHeldAndCarol(id: string) {
  const guests: Guest[] = [];
  joinAsScriptedPlayer(id, 'Bob', 'president', { holdExchange: true }).then((g) => { guests.push(g); });
  joinAsScriptedPlayer(id, 'Carol', 'president').then((g) => { guests.push(g); });
  cy.get('[data-test="player-count"]').should('contain.text', '3 / 8');
  return cy.then(() => guests);
}

/** Round 1 ends with the browser last (Scum), Bob first (President), Carol second; every seat then presses ready. */
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
  cy.get('[data-test="motion-card"]', { timeout: 10000 }).should('not.exist');
  cy.get('[data-test="ready"]').click();
  scriptedReady(bob);
  scriptedReady(carol);
}

/** Round 2 opens on the exchange: Bob owes two cards, gives them back, and the view opens. */
function startAtTheView(afterStart?: () => void) {
  return hostLobby('Alice').then((id) => joinBobHeldAndCarol(id).then(([bob, carol]) => {
    const mine = ['2C', '2S', '4C', '5C', '6C', '7C', '8C', '9C', '10C', 'JC', 'QC', 'KC', 'AC', '3S', '4S', '5S', '6S'];
    const rest = without(mine);
    cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: roundOneDealBrowserLast() });
    cy.request('PUT', `/api/test/president/${id}/deal`, { round: 2, cards: dealFrom(rest.slice(17), rest.slice(0, 17), mine) });
    cy.get('[data-test="start"]').should('not.be.disabled').click();
    cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
    afterStart?.();
    playRoundOne(bob, carol);
    awaitState(bob, 'Bob owes two cards', (s) => s.phase === 'EXCHANGE' && s.toGiveBack === 2);
    cy.then(() => bob.publish('/app/president/giveBack', { cards: bob.lastState().hand.slice(0, 2) }));
    awaitState(bob, 'the exchange view, Bob still to acknowledge', (s) =>
      s.phase === 'EXCHANGE_VIEW' && s.availableActions.includes('SEE_EXCHANGE'));
    return cy.wrap({ id, bob, carol }, { log: false });
  }));
}

/** The server state still waits: Bob has not seen it, the view is open, no card was played. */
function serverStillWaits(observer: Guest) {
  awaitState(observer, 'the view is still open, no trick yet', (s) =>
    s.phase === 'EXCHANGE_VIEW' && s.trick.plays.length === 0
    && s.players.find((p: any) => p.seat === 1).sawExchange === false);
}

[375, 1280].forEach((width) => {
  describe(`President exchange synchronisation at ${width} px`, () => {
    beforeEach(() => {
      cy.viewport(width, width === 375 ? 667 : 800);
      cy.window().then((win) => win.localStorage.removeItem('animationSpeed'));
    });

    it('waits for a human who has not seen the exchange, then starts the first trick', () => {
      startAtTheView().then(({ bob, carol }) => {
        // The browser watches the exchange, then has seen it: it waits for Bob, the only human left.
        cy.get('[data-test="phase-text"]', { timeout: 20000 }).should('have.text', WAITING_FOR_BOB);
        cy.get('[data-test="play"]').should('be.disabled');
        cy.get('[data-test="pass"]').should('be.disabled');
        cy.get('[data-test="exchange-board"]').should('be.visible');
        cy.get('[data-test="board-seen"]').should('have.class', 'seen--off');
        // The server agrees: Carol and the browser have seen it, Bob has not, and nobody has played.
        awaitState(carol, 'the browser and Carol have seen the exchange, Bob has not', (s) =>
          s.phase === 'EXCHANGE_VIEW' && s.trick.plays.length === 0
          && s.players.filter((p: any) => p.sawExchange).map((p: any) => p.seat).sort().join() === '0,2');
        serverStillWaits(carol);
        noHorizontalScroll();

        // Bob has seen it: the first trick starts, and the browser (the Scum) leads.
        cy.then(() => bob.seeExchange());
        cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
        awaitState(carol, 'the round is on', (s) => s.phase === 'PLAYING' && s.currentSeat === 0);
        noHorizontalScroll();
      });
    });
  });
});

describe('President exchange synchronisation with a bot', () => {
  it('keeps the bot from playing until the held human has seen the exchange', () => {
    cy.viewport(1280, 800);
    cy.window().then((win) => win.localStorage.removeItem('animationSpeed'));
    hostLobby('Alice').then((id) => {
      cy.get('[data-test="add-bot"]').click();
      joinAsScriptedPlayer(id, 'Bob', 'president', { holdExchange: true }).then((bob) => {
        cy.get('[data-test="player-count"]').should('contain.text', '3 / 8');
        // The twin plays the browser's seat (lowest sets, gives back); the browser acknowledges the exchange itself,
        // Bob plays by himself and is held back at the view only. The bot is seat 1 (its name is Bot 1).
        connectMyself(id).then((me) => autoplay(id, me, { ready: false, seeExchange: false }));
        autoplay(id, bob, { seeExchange: false });
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
        cy.get('[data-test="round-over-title"]', { timeout: 60000 }).should('have.text', 'Round 1 is over');
        cy.get('[data-test="ready"]').click();

        // The bot acknowledges at once; Bob and the browser have not yet when the view opens.
        awaitState(bob, 'the exchange view with the bot already done', (s) =>
          s.phase === 'EXCHANGE_VIEW' && s.round === 2 && s.players.some((p: any) => p.bot && p.sawExchange));
        cy.get('[data-test="phase-text"]', { timeout: 20000 }).should('have.text', WAITING_FOR_BOB);
        cy.get('[data-test="play"]').should('be.disabled');
        cy.get('[data-test="pass"]').should('be.disabled');

        // Nothing moves while Bob has not seen the exchange. The bots of the test profile answer with no delay, so
        // this short wait is long enough for any move to have come; the view timeout is longer than the spec's reading.
        cy.wait(1500);
        awaitState(bob, 'still in the view, nobody played', (s) =>
          s.phase === 'EXCHANGE_VIEW' && s.trick.plays.length === 0 && s.players.every((p: any) => !p.out));
        cy.get('[data-test="phase-text"]').should('have.text', WAITING_FOR_BOB);

        // Bob has seen it: the play begins (whoever leads: the bot, Bob's twin or the browser's twin).
        cy.then(() => bob.seeExchange());
        awaitState(bob, 'the first trick has started', (s) => s.phase === 'PLAYING' || s.phase === 'ROUND_OVER');
        cy.get('[data-test="phase-text"]').should('not.have.text', WAITING_FOR_BOB);
      });
    });
  });
});

describe('President exchange synchronisation, reload and timeout', () => {
  beforeEach(() => {
    cy.viewport(1280, 800);
    cy.window().then((win) => win.localStorage.removeItem('animationSpeed'));
  });

  it('a reload during the view shows the board again, without flights, and still waits for Bob', () => {
    startAtTheView().then(({ id, bob, carol }) => {
      serverStillWaits(carol);
      // The `test` profile forfeits a seat that stays disconnected for 5 s (presence.forfeit-grace-seconds), and a reload
      // of the dev server can take longer: a twin on the browser's seat asserts its presence all through the reload, as
      // the reconnecting page does within the grace period of a real table. The twin never acknowledges the exchange.
      let keepAlive: ReturnType<typeof setInterval> | undefined;
      connectMyself(id).then((me) => {
        keepAlive = setInterval(() => me.publish('/app/presence', {}), 300);
        Cypress.once('test:after:run', () => clearInterval(keepAlive));
        cy.reload();
      });
      cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
      // No flights after a reload: the board is on screen at once, and the table reads the exchange.
      cy.get('[data-test="exchange-board"]', { timeout: 10000 }).should('be.visible');
      cy.get('[data-test="board-bar"]').should('exist');
      cy.then(() => clearInterval(keepAlive));
      cy.get('[data-test="motion-card"]').should('not.exist');
      cy.get('[data-test="play"]').should('be.disabled');
      // The same reading time, then the browser has seen it again and waits for Bob.
      cy.get('[data-test="phase-text"]', { timeout: 15000 }).should('have.text', WAITING_FOR_BOB);
      cy.then(() => bob.seeExchange());
      cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
    });
  });

  it('the server lets the table through when a human never acknowledges (timeout)', () => {
    startAtTheView().then(({ carol }) => {
      // Nobody acknowledges for Bob: the server closes the view by itself once the timeout is over, and the browser
      // (the Scum) leads. The wait is counted from the moment the view was seen open.
      const openedAt = Date.now();
      cy.get('[data-test="phase-text"]', { timeout: timeoutMs() + 10000 }).should('have.text', 'Your turn');
      cy.then(() => {
        expect(Date.now() - openedAt, 'the table waited for the timeout').to.be.at.least(timeoutMs() - 2000);
      });
      awaitState(carol, 'the round is on after the timeout', (s) => s.phase === 'PLAYING' && s.currentSeat === 0);
    });
  });
});

describe('President exchange synchronisation at the Instant speed', () => {
  it('acknowledges at once instead of reading the board for 3 s', () => {
    cy.viewport(375, 667);
    cy.window().then((win) => win.localStorage.removeItem('animationSpeed'));
    startAtTheView(() => {
      cy.get('[data-test="speed-button"]').click();
      cy.get('[data-test="speed-button"]').click();
      cy.get('[data-test="speed-button"]').should('have.attr', 'aria-label', 'Animation speed: Instant');
    }).then(({ bob, carol }) => {
      // The view is already open (Bob's state); the Normal speed would still be reading the board here.
      cy.get('[data-test="phase-text"]', { timeout: 2500 }).should('have.text', WAITING_FOR_BOB);
      awaitState(carol, 'the browser has seen the exchange', (s) =>
        s.phase === 'EXCHANGE_VIEW' && s.players.find((p: any) => p.seat === 0).sawExchange);
      cy.get('[data-test="play"]').should('be.disabled');
      cy.then(() => bob.seeExchange());
      cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
    });
  });
});
