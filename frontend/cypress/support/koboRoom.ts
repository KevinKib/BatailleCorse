// Shared steps of the Kobo specs: the browser under test hosts the room, scripted STOMP clients are the others.
import { connectScriptedPlayer, joinAsScriptedPlayer, type ScriptedPlayer } from './stompPlayer';

export type Guest = ScriptedPlayer & { token: string; seat: number };

/** Creates a Kobo room as the host and yields its id once the lobby shows. */
export function hostLobby(name: string) {
  cy.visit('/games/kobo');
  cy.get('[data-test="name"]').type(name);
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/kobo\/room\/.+/);
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  return cy.location('pathname').then((path) => path.split('/room/')[1]);
}

export function noHorizontalScroll() {
  cy.document().then((doc) => {
    expect(doc.documentElement.scrollWidth, 'page width').to.be.at.most(doc.documentElement.clientWidth);
  });
}

const RANKS = ['K', 'Q', 'J', '10', '9', '8', '7', '6', '5', '4', '3', '2', 'A'];
const SUITS = ['H', 'D', 'C', 'S'];
/** The 52 deal codes in a fixed order (`KH`, `QH`, ... `AS`); the deal of `n` players takes the first 4n + 1. */
export const DECK_CODES = SUITS.flatMap((suit) => RANKS.map((rank) => `${rank}${suit}`));

/**
 * Hosts a room named Alice, adds `players - 1` scripted guests, optionally fixes the deal of round 1 (`PUT
 * /api/test/kobo/{id}/deal`, test profile only), starts, and yields the room id and the guests (seats 1..n-1)
 * once the browser shows the table.
 */
export function startKoboTable(players: number, deal: string[] = []) {
  return hostLobby('Alice').then((id) => {
    const guests: Guest[] = [];
    const names = ['Bob', 'Carol', 'Dan', 'Erin', 'Fay'];
    for (let i = 0; i < players - 1; i++) {
      joinAsScriptedPlayer(id, names[i], 'kobo').then((guest) => { guests.push(guest); });
    }
    cy.get('[data-test="player-count"]').should('contain.text', `${players} / 6`);
    if (deal.length > 0) cy.request('PUT', `/api/test/kobo/${id}/deal`, { round: 1, cards: deal });
    cy.get('[data-test="start"]').should('not.be.disabled').click();
    cy.get('[data-test="kobo-table"]', { timeout: 10000 }).should('be.visible');
    return cy.then(() => ({ id, guests }));
  });
}

/** The deal of round 1 for `players` players: the first 4n codes of the deck, then `top` as the first card drawn. */
export function dealWithTopCard(players: number, top: string): string[] {
  return [...DECK_CODES.slice(0, 4 * players), top];
}

/**
 * The browser as the guest in seat 1, against a scripted host (seat 0, who starts the game) and `players - 2`
 * more scripted guests (seats 2..). The browser has no action of its own yet, so `readyAll` also drives its
 * seat (a second socket on its own token) to leave the memorising phase.
 */
export function startKoboTableAsGuest(players: number, deal: string[] = []) {
  return cy.request('POST', '/api/lobby', { gameType: 'kobo', name: 'Alice' }).then((created) => {
    const { gameId, token } = created.body as { gameId: string; token: string };
    return cy.request('POST', `/api/kobo/game/${gameId}/join`, { name: 'Zed' }).then((joined) => {
      const me = joined.body as { playerId: number; token: string };
      cy.visit(`/games/kobo/room/${gameId}`, {
        onBeforeLoad: (win) => win.localStorage.setItem(`kobo:tokens:${gameId}`, JSON.stringify({ [me.playerId]: me.token })),
      });
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
      // The second socket of the browser's seat joins while the room is a lobby, where the connection probe is
      // answered (after the start there may be nothing to answer it).
      return connectScriptedPlayer(gameId, me.token, me.playerId).then((mine) => {
        const guests: Guest[] = [];
        const names = ['Carol', 'Dan', 'Erin', 'Fay'];
        for (let i = 0; i < players - 2; i++) {
          joinAsScriptedPlayer(gameId, names[i], 'kobo').then((guest) => { guests.push(guest); });
        }
        return connectScriptedPlayer(gameId, token, 0).then((host) => {
          cy.get('[data-test="player-count"]').should('contain.text', `${players} / 6`);
          if (deal.length > 0) cy.request('PUT', `/api/test/kobo/${gameId}/deal`, { round: 1, cards: deal });
          cy.then(() => host.publish('/app/kobo/start', {}));
          cy.get('[data-test="kobo-table"]', { timeout: 10000 }).should('be.visible');
          return cy.wrap({ id: gameId, host, guests, mine });
        });
      });
    });
  });
}

/**
 * Waits, without a fixed delay, until the latest state a scripted player holds satisfies `predicate`. Unlike
 * `waitForFrame` it looks at the newest state only, so an earlier turn's state can never satisfy it.
 */
export function awaitState(
  player: Pick<ScriptedPlayer, 'lastState'>,
  label: string,
  predicate: (state: any) => boolean,
) {
  return cy.wrap(player, { log: false, timeout: 10000 }).should((p) => {
    const state = p.lastState();
    expect(Boolean(state && state.started && predicate(state)), label).to.eq(true);
  });
}

/**
 * Waits until every flight has landed: the motion layer holds no copy and the discard top is fully shown again.
 * A tap sent before that is judged on what is displayed (the card still shows its old state) and is refused as
 * "too late", so a spec that wants its click accepted calls this first.
 */
export function awaitLanded() {
  cy.get('[data-test="motion-card"]').should('not.exist');
  cy.get('[data-test="discard-pile"] img').should('have.css', 'opacity', '1');
}

/**
 * One whole turn of a scripted player that draws, discards a card without a power and ends its turn. It waits
 * for its own state at every step (its turn, the drawn card, the end step), so it never races the browser.
 */
export function scriptedDiscardTurn(player: ScriptedPlayer & { seat: number }) {
  awaitState(player, 'my draw step', (s) => s.currentSeat === player.seat && s.phase === 'DRAW');
  cy.then(() => player.publish('/app/kobo/draw', {}));
  awaitState(player, 'the drawn card', (s) => s.phase === 'DECISION' && s.drawnCard !== null);
  cy.then(() => player.publish('/app/kobo/discardDrawn', {}));
  awaitState(player, 'the end of my turn', (s) => s.phase === 'TURN_END');
  cy.then(() => player.publish('/app/kobo/endTurn', {}));
}
