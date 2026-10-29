import { t, type GameContract } from './wireContract';

/**
 * How the frontend reads the wire of each game: one entry per folder of the repository-level `contract/`. The
 * types named here are the ones the stores and screens read the server payloads as.
 */

const LOBBY_VIEW = t('model/lobby/LobbyView', 'LobbyView');
const JOIN = t('application/SeatGameSession', 'JoinBody');
const SEAT_REST = { join: JOIN };

export const WIRE_CONTRACTS: GameContract[] = [
  {
    folder: 'session',
    view: LOBBY_VIEW,
    eventType: null,
    eventData: {},
    rest: {
      created: t('application/SeatGameSession', 'LobbyCreatedBody'),
      lobbies: t('model/lobby/PublicLobby', 'PublicLobbyList'),
      session: `{ players: ${t('model/SessionSeat', 'default')}[] }`,
    },
  },
  {
    folder: 'kilo-de-merde',
    view: t('model/kilodemerde/KiloDeMerdeState', 'KiloDeMerdeView'),
    eventType: t('model/kilodemerde/KiloDeMerdeState', 'KiloDeMerdeEventType'),
    eventData: { '*': t('model/kilodemerde/KiloDeMerdeState', 'KiloDeMerdeEventData') },
    rest: SEAT_REST,
    fixtures: ['model/kilodemerde/fixtures.ts'],
  },
];
