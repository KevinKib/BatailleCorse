// A scripted President seat that plays a whole match by itself: lowest legal set it is offered, a pass otherwise,
// the first cards of its hand when it has to give back, "I have seen the exchange" when the table waits for it (unless
// `seeExchange: false`, which keeps the table waiting for this seat), Ready at the round-over barrier (unless
// `ready: false`, when the spec presses it in the browser). It acts once per state version, from the newest state it holds, and
// stops with the test (the sockets are closed after each test).
import { canonicalRank } from '../../src/model/president/ranks';
import { connectScriptedPlayer, type ScriptedPlayer } from './stompPlayer';

export type AutoPlayer = ScriptedPlayer & { token: string; seat: number };

const openingLead = (state: any): boolean =>
  state.round === 1 && state.trick.plays.length === 0 && state.hand.some((c: any) => c.name === 'HEART_KING')
  && state.players.every((p: any) => p.handSize >= 17);

export function autoplay(gameId: string, player: AutoPlayer, options: { ready?: boolean; seeExchange?: boolean } = {}): void {
  const readyToo = options.ready ?? true;
  const seesExchange = options.seeExchange ?? true;
  if (!seesExchange) player.holdExchange = true;
  let acted = -1;
  let actedAt = 0;
  let attempt = 0;
  // The start of a game is not pushed to every seat: the state is also read over REST, the newest of both is used.
  let fetched: any = null;
  let inflight = false;
  const timer = setInterval(() => {
    try {
      step();
    } catch {
      // The socket was closed under the timer (the test's afterEach deactivates it before `test:after:run` fires,
      // and a game still running at that point keeps offering moves): this seat is done.
      clearInterval(timer);
    }
  }, 150);
  function step() {
    if (!inflight) {
      inflight = true;
      fetch(`/api/president/game/${gameId}?token=${player.token}`)
        .then((res) => res.json()).then((s) => { fetched = s; }).catch(() => undefined)
        .finally(() => { inflight = false; });
    }
    const framed = player.lastState();
    const state = fetched && fetched.started && (!framed || !framed.started || fetched.version > framed.version) ? fetched : framed;
    if (!state || !state.started) return;
    // A command the server refused changes nothing: after a second without news, try the next best thing.
    if (state.version === acted && Date.now() - actedAt < 1000) return;
    attempt = state.version === acted ? attempt + 1 : 0;
    const can = (action: string) => state.availableActions.includes(action);
    let done = true;
    if (state.phase === 'EXCHANGE' && can('GIVE_BACK')) {
      player.publish('/app/president/giveBack', { cards: state.hand.slice(0, state.toGiveBack) });
    } else if (state.phase === 'EXCHANGE_VIEW' && can('SEE_EXCHANGE') && seesExchange) {
      player.publish('/app/president/seeExchange', { round: state.round });
    } else if (state.phase === 'PLAYING' && can('PLAY') && openingLead(state)) {
      // The Classic rules open round 1 with the king of hearts (the server refuses any other first play).
      player.publish('/app/president/play', { cards: state.hand.filter((c: any) => c.name === 'HEART_KING') });
    } else if (state.phase === 'PLAYING' && can('PLAY') && state.legalPlays.length > 0) {
      const pattern = state.legalPlays[Math.min(attempt, state.legalPlays.length - 1)];
      player.publish('/app/president/play', {
        cards: state.hand.filter((c: any) => c.rank === canonicalRank(pattern.rank)).slice(0, pattern.count),
      });
    } else if (state.phase === 'PLAYING' && can('PASS')) {
      player.publish('/app/president/pass', {});
    } else if (state.phase === 'ROUND_OVER' && can('READY') && readyToo) {
      player.publish('/app/president/ready', {});
    } else {
      done = false;
    }
    if (done) { acted = state.version; actedAt = Date.now(); }
  }
  const disconnect = player.disconnect;
  player.disconnect = () => { clearInterval(timer); disconnect(); };
  Cypress.once('test:after:run', () => clearInterval(timer));
}

/** Connects a second client on the browser's own seat (its token is in the browser's local storage). */
export function connectMyself(gameId: string): Cypress.Chainable<AutoPlayer> {
  return cy.window().then((win) => {
    const tokens = JSON.parse(win.localStorage.getItem(`president:tokens:${gameId}`) ?? '{}') as Record<string, string>;
    const [seat, token] = Object.entries(tokens)[0];
    // The browser acknowledges the exchange itself: this second client never does it for the page.
    return connectScriptedPlayer(gameId, token, Number(seat), { holdExchange: true });
  });
}
