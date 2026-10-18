// Shared steps of the President specs: the browser under test hosts the room, scripted STOMP clients are the others.
import { joinAsScriptedPlayer, type ScriptedPlayer } from './stompPlayer';

export type Guest = ScriptedPlayer & { token: string; seat: number };

/** Creates a President room as the host and yields its id once the lobby shows. */
export function hostLobby(name: string) {
  cy.visit('/games/president');
  cy.get('[data-test="name"]').type(name);
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/president\/room\/.+/);
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  return cy.location('pathname').then((path) => path.split('/room/')[1]);
}

export function noHorizontalScroll() {
  cy.document().then((doc) => {
    expect(doc.documentElement.scrollWidth, 'page width').to.be.at.most(doc.documentElement.clientWidth);
  });
}

/** Joins `names` as scripted guests (seats 1..) and waits for the lobby to count them. */
export function joinGuests(id: string, names: string[]) {
  const guests: Guest[] = [];
  names.forEach((name) => {
    joinAsScriptedPlayer(id, name, 'president').then((guest) => { guests.push(guest); });
  });
  cy.get('[data-test="player-count"]').should('contain.text', `${names.length + 1} / 8`);
  return cy.then(() => guests);
}

/**
 * Waits, without a fixed delay, until the latest state a scripted player holds satisfies `predicate`. It looks at
 * the newest state only, so an earlier turn's state can never satisfy it.
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

const myTurn = (player: { seat: number }) => (s: any) =>
  s.phase === 'PLAYING' && s.currentSeat === player.seat;

/** The scripted player passes as soon as it is its turn. */
export function scriptedPass(player: ScriptedPlayer & { seat: number }) {
  awaitState(player, `seat ${player.seat}: my turn to pass`, (s) => myTurn(player)(s) && s.availableActions.includes('PASS'));
  cy.then(() => player.publish('/app/president/pass', {}));
}

/** The scripted player leads the cards of `rank` it holds (`count` of them) as soon as it is its turn. */
export function scriptedPlay(player: ScriptedPlayer & { seat: number }, rank: string, count: number) {
  awaitState(player, `seat ${player.seat}: my turn to play ${rank}`, (s) =>
    myTurn(player)(s) && s.availableActions.includes('PLAY') && s.hand.filter((c: any) => c.rank === rank).length >= count);
  cy.then(() => {
    const cards = player.lastState().hand.filter((c: any) => c.rank === rank).slice(0, count);
    player.publish('/app/president/play', { cards });
  });
}

/** Every player presses "ready" at the round-over barrier. */
export function scriptedReady(player: ScriptedPlayer) {
  awaitState(player, 'the round-over barrier', (s) => s.phase === 'ROUND_OVER' && s.availableActions.includes('READY'));
  cy.then(() => player.publish('/app/president/ready', {}));
}

/**
 * A full fixed deal of round 1 for three players (the deal goes seat 0, 1, 2, 0, ...):
 *  - seat 0 (the host) holds three each of K, Q, J, 10, 9, 8, king of hearts included, so it leads;
 *  - seat 1 holds three each of 7, 6, 5, 4, 3 and the pair of aces (hearts, diamonds);
 *  - seat 2 holds what is left (the spades, the four 2s, the aces of clubs and spades).
 * Every set is played alone and passed on, whatever the rules of equal play: the round ends 1st seat 0, 2nd seat 1,
 * 3rd seat 2.
 */
export function threePlayerDeal(): string[] {
  const triple = (rank: string) => [`${rank}H`, `${rank}D`, `${rank}C`];
  const seat0 = ['K', 'Q', 'J', '10', '9', '8'].flatMap(triple);
  const seat1 = [...['7', '6', '5', '4', '3'].flatMap(triple), 'AH', 'AD'];
  const seat2 = [
    ...['K', 'Q', 'J', '10', '9', '8', '7', '6', '5', '4', '3'].map((r) => `${r}S`), '2H', '2D', '2C', '2S', 'AC', 'AS',
  ];
  const deal: string[] = [];
  for (let i = 0; i < 17; i++) deal.push(seat0[i], seat1[i], seat2[i]);
  deal.push(seat0[17]);
  return deal;
}
