import { Client, type IMessage } from '@stomp/stompjs';

/**
 * A second player driven from the spec: it holds a seat token, listens on its seat channel and sends lobby
 * commands over a raw STOMP socket. The browser under test is the other player, so one Cypress run covers two
 * clients without a second browser.
 */
export interface ScriptedPlayer {
  /** Every frame received on the seat channel, oldest first. */
  frames: Array<{ eventType: string; state?: any; success?: boolean; message?: string }>;
  /** The latest lobby (or game) state this player was sent. */
  lastState(): any;
  publish(destination: string, body: Record<string, unknown>): void;
  disconnect(): void;
  /**
   * President: while true the guest does not acknowledge the card exchange by itself, so a spec can keep the table
   * waiting for it (then `seeExchange()` lets it through). Guests acknowledge as soon as the server offers
   * `SEE_EXCHANGE` by default, like a human who has watched the exchange; for any other game nothing is ever offered.
   */
  holdExchange: boolean;
  /** Acknowledges the exchange of the round the latest state is in (a no-op for the server when it is not waiting). */
  seeExchange(): void;
}

/** Options of a scripted guest. */
export interface ScriptedPlayerOptions {
  /** President: start with the exchange acknowledgement held back (see `ScriptedPlayer.holdExchange`). */
  holdExchange?: boolean;
}

// Every scripted socket opened in the current test. The spec iframe lives for the whole spec file, so a socket
// left open would outlive its test (and keep its seat connected on the server): they are all closed after each test.
// Cypress bundles the support file and each spec separately, so this module runs twice: the set lives on the shared
// window, otherwise the support file's afterEach would empty a set the specs never fill.
const openClients: Set<Client> = ((globalThis as any).__scriptedStompClients ??= new Set<Client>());

export function disconnectAllScriptedPlayers(): Promise<void> {
  const closing = [...openClients].map((client) => client.deactivate());
  openClients.clear();
  return Promise.all(closing).then(() => undefined);
}

/** The game types a scripted player can join, as the server names them (`/api/lobby`'s `gameType`). */
export type ScriptedGameType = 'bataille-corse' | 'bullshit' | 'kobo' | 'three-men-morris' | 'president' | 'mangola' | 'ninety-nine' | 'draughts' | 'blackjack';

/** Where a guest claims a seat: Bataille Corse keeps the historical unprefixed REST base. */
const joinPath = (gameType: ScriptedGameType, gameId: string) =>
  gameType === 'bataille-corse' ? `/api/game/${gameId}/join` : `/api/${gameType}/game/${gameId}/join`;

/**
 * Joins the lobby `gameId` as a guest named `name` and connects. `gameType` defaults to Bullshit, what the
 * first specs used, so those calls stay as they are.
 */
export function joinAsScriptedPlayer(
  gameId: string,
  name: string,
  gameType: ScriptedGameType = 'bullshit',
  options: ScriptedPlayerOptions = {},
): Cypress.Chainable<ScriptedPlayer & { token: string; seat: number }> {
  return cy.request('POST', joinPath(gameType, gameId), { name }).then((res) => {
    const { playerId, token } = res.body as { playerId: number; token: string };
    return connectScriptedPlayer(gameId, token, playerId, options);
  });
}

/**
 * Waits, without a fixed delay, until the scripted player has received a frame matching `predicate`, then
 * yields that frame. It retries until the Cypress timeout, so a spec reads "wait for the state I need"
 * instead of sleeping.
 */
export function waitForFrame(
  player: Pick<ScriptedPlayer, 'frames'>,
  predicate: (frame: ScriptedPlayer['frames'][number]) => boolean,
  options: { timeout?: number } = {},
): Cypress.Chainable<ScriptedPlayer['frames'][number]> {
  return cy.wrap(player.frames, { timeout: options.timeout ?? 10000 })
    .should((frames) => {
      expect(frames.some(predicate), 'a received frame matches the expected state').to.eq(true);
    })
    .then((frames) => frames.find(predicate)!);
}

/**
 * Connects and subscribes to the seat channel. It resolves once the server has answered a probe command on that
 * channel (an options change with nothing in it: a success for the host, a refusal for anybody else), which proves
 * the subscription is live: a frame sent before that would be lost, and no fixed wait is needed.
 */
export function connectScriptedPlayer(gameId: string, token: string, seat: number, options: ScriptedPlayerOptions = {}) {
  const base = (Cypress.config('baseUrl') as string).replace(/^http/, 'ws');
  return cy.wrap(new Promise<ScriptedPlayer & { token: string; seat: number }>((resolve, reject) => {
    const frames: ScriptedPlayer['frames'] = [];
    const client = new Client({ brokerURL: `${base}/connect/websocket`, reconnectDelay: 0 });
    openClients.add(client);
    const player = {
      token,
      seat,
      frames,
      lastState: () => frames.map((f) => f.state).filter(Boolean).pop(),
      publish: (destination: string, body: Record<string, unknown>) =>
        client.publish({ destination, body: JSON.stringify({ gameId, token, ...body }) }),
      disconnect: () => { openClients.delete(client); void client.deactivate(); },
      holdExchange: options.holdExchange ?? false,
      seeExchange: () => {
        const state = player.lastState();
        if (state?.availableActions?.includes('SEE_EXCHANGE')) player.publish('/app/president/seeExchange', { round: state.round });
      },
    };
    client.onStompError = (frame) => reject(new Error(frame.headers['message']));
    client.onWebSocketError = () => reject(new Error('scripted player: websocket error'));
    client.onConnect = () => {
      client.subscribe(`/topic/game/${gameId}/seat/${token}`, (message: IMessage) => {
        frames.push(JSON.parse(message.body));
        // The exchange of a President round: a guest is a human seat, the table waits for it (inert until offered).
        if (!player.holdExchange) player.seeExchange();
        if (frames.length === 1) resolve(player);
      }, { token });
      // The probe is repeated until the first frame comes back, whenever the subscription became active.
      const probe = setInterval(() => {
        if (frames.length > 0 || !client.connected) { clearInterval(probe); return; }
        player.publish('/app/lobby/setOptions', { values: {} });
      }, 200);
    };
    client.activate();
  }), { timeout: 10000 });
}
