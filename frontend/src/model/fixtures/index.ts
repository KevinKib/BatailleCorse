import type Card from '../Card';
import Pile from '../Pile';
import Player from '../Player';
import BatailleCorse from '../BatailleCorse';
import type Response from '../Response';

// ---------------------------------------------------------------------------
// Primitive builders
// ---------------------------------------------------------------------------

export function buildCard(overrides: Partial<Card> = {}): Card {
  return { rank: '2', suit: 'heart', name: '2 of hearts', ...overrides };
}

// ---------------------------------------------------------------------------
// Domain builders — return real class instances with behaviour methods
// ---------------------------------------------------------------------------

export function buildPile(overrides: Partial<{
  cards: Card[];
  grabbable: boolean;
  nbCardsSinceLastHonourCard: number;
  playerThatAddedLastHonourCard: { id: string };
}> = {}): Pile {
  return new Pile(
    overrides.cards ?? [],
    overrides.grabbable ?? false,
    overrides.nbCardsSinceLastHonourCard ?? 0,
    overrides.playerThatAddedLastHonourCard ?? { id: '0' },
  );
}

export function buildPlayer(overrides: Partial<{
  id: string;
  nbCards: number;
  availableActions: string[];
}> = {}): Player {
  return new Player(
    overrides.id ?? '0',
    overrides.nbCards ?? 26,
    overrides.availableActions ?? [],
  );
}

export function buildGame(overrides: Partial<{
  pile: Pile;
  players: Player[];
  winner: { id: string } | null;
}> = {}): BatailleCorse {
  const players = overrides.players ?? [
    buildPlayer({ id: '0' }),
    buildPlayer({ id: '1' }),
  ];
  return new BatailleCorse(
    players[0],
    overrides.pile ?? buildPile(),
    players,
    overrides.winner ?? null,
  );
}

// ---------------------------------------------------------------------------
// Response builders
// ---------------------------------------------------------------------------

export function buildResponse(overrides: Partial<Response> = {}): Response {
  return {
    success: true,
    eventType: 'SEND',
    eventData: {},
    message: '',
    state: buildGame(),
    ...overrides,
  };
}

export function buildCreateResponse(
  gameId: string,
  tokens: Record<number, string>,
  state: BatailleCorse = buildGame(),
): Response {
  return {
    success: true,
    eventType: 'CREATE',
    eventData: { game: { id: gameId }, tokens },
    message: 'Game created',
    state,
  };
}

export function buildGrabResponse(
  winnerPlayerId: string,
  state: BatailleCorse = buildGame(),
): Response {
  return {
    success: true,
    eventType: 'GRAB',
    eventData: { player: { id: winnerPlayerId } },
    message: '',
    state,
  };
}

export function buildSlapResponse(
  isSuccessful: boolean,
  slapperPlayerId: string,
  state: BatailleCorse = buildGame(),
): Response {
  return {
    success: true,
    eventType: 'SLAP',
    eventData: { isSuccessful, player: { id: slapperPlayerId } },
    message: '',
    state,
  };
}
