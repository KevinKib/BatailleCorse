import { t, type GameContract } from './wireContract';

/**
 * How the frontend reads the wire of each game: one entry per folder of the repository-level `contract/`. The
 * types named here are the ones the sessions, stores and screens read the server payloads as.
 */

const LOBBY_VIEW = t('model/lobby/LobbyView', 'LobbyView');
const SEAT_REST = { join: t('application/SeatGameSession', 'JoinBody') };

const BC_STATE = `Parameters<typeof ${t('model/BatailleCorse', 'default')}.fromJSON>[0]`;
const MORRIS_MOVE = t('model/threemenmorris/MorrisState', 'MorrisEventData');
const MORRIS_REMOVAL = t('model/threemenmorris/MorrisState', 'MorrisRemovalEventData');
const MORRIS_DRAW = t('model/threemenmorris/MorrisState', 'MorrisDrawEventData');
const PRESIDENT = 'model/president/PresidentEvents';
const DRAUGHTS = 'model/draughts/DraughtsState';

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
    folder: 'bataille-corse',
    view: `${LOBBY_VIEW} | ${BC_STATE}`,
    eventType: `${t('model/Response', 'default')}['eventType']`,
    eventData: {
      SEND: t('model/event/SendEventData', 'default'),
      SLAP: t('model/event/SlapEventData', 'default'),
      GRAB: t('model/event/GrabEventData', 'default'),
    },
    rest: SEAT_REST,
  },
  {
    folder: 'blackjack',
    view: t('model/blackjack/BlackjackState', 'BlackjackView'),
    eventType: t('model/blackjack/BlackjackState', 'BlackjackEventType'),
    eventData: { '*': t('model/blackjack/BlackjackState', 'BlackjackEventData') },
    // A refusal carries a stable code, typed by the client.
    response: `Omit<${t('model/blackjack/BlackjackState', 'BlackjackResponse')}, 'errorCode'>`
      + ` & { errorCode?: ${t('model/blackjack/BlackjackState', 'BlackjackErrorCode')} }`,
    rest: SEAT_REST,
    fixtures: ['model/blackjack/fixtures.ts'],
  },
  {
    folder: 'bullshit',
    view: t('model/bullshit/BullshitState', 'BullshitView'),
    eventType: null,
    eventData: {
      DISCARD: t('model/bullshit/BullshitEvents', 'DiscardEventData'),
      CALL_BULLSHIT: t('model/bullshit/BullshitEvents', 'CallBullshitEventData'),
    },
    rest: SEAT_REST,
  },
  {
    folder: 'draughts',
    view: t(DRAUGHTS, 'DraughtsView'),
    eventType: t(DRAUGHTS, 'DraughtsEventType'),
    eventData: {
      MOVE: t(DRAUGHTS, 'DraughtsMoveEventData'),
      DRAW_OFFERED: t(DRAUGHTS, 'DraughtsSeatEventData'),
      DRAW_DECLINED: t(DRAUGHTS, 'DraughtsSeatEventData'),
      GAME_OVER: `${t(DRAUGHTS, 'DraughtsMoveEventData')} | ${t(DRAUGHTS, 'DraughtsSeatEventData')}`,
    },
    rest: SEAT_REST,
    fixtures: ['model/draughts/fixtures.ts'],
  },
  {
    folder: 'kilo-de-merde',
    view: t('model/kilodemerde/KiloDeMerdeState', 'KiloDeMerdeView'),
    eventType: t('model/kilodemerde/KiloDeMerdeState', 'KiloDeMerdeEventType'),
    eventData: { '*': t('model/kilodemerde/KiloDeMerdeState', 'KiloDeMerdeEventData') },
    rest: SEAT_REST,
    fixtures: ['model/kilodemerde/fixtures.ts'],
  },
  {
    folder: 'kobo',
    view: t('model/kobo/KoboState', 'KoboView'),
    eventType: t('model/kobo/KoboEvents', 'KoboEventType'),
    eventData: { '*': t('model/kobo/KoboEvents', 'KoboEventData') },
    rest: SEAT_REST,
    fixtures: ['model/fixtures/kobo.ts'],
  },
  {
    folder: 'mangola',
    view: t('model/mangola/MangolaState', 'MangolaView'),
    eventType: null,
    eventData: { '*': t('model/mangola/MangolaState', 'MangolaEventData') },
    rest: SEAT_REST,
    fixtures: ['model/mangola/mangolaFixtures.ts', 'model/mangola/sowingFixtures.ts'],
  },
  {
    folder: 'ninety-nine',
    view: t('model/ninetynine/NinetyNineState', 'NinetyNineView'),
    eventType: t('model/ninetynine/NinetyNineState', 'NinetyNineEventType'),
    eventData: { '*': t('model/ninetynine/NinetyNineState', 'NinetyNineEventData') },
    rest: SEAT_REST,
    fixtures: ['model/fixtures/ninetynine.ts'],
  },
  {
    folder: 'president',
    view: t('model/president/PresidentState', 'PresidentView'),
    eventType: t(PRESIDENT, 'PresidentEventType'),
    eventData: {
      EXCHANGE_CLOSED: t(PRESIDENT, 'PresidentExchangeClosedData'),
      '*': t(PRESIDENT, 'PresidentEventData'),
    },
    rest: SEAT_REST,
    fixtures: ['model/fixtures/president.ts'],
  },
  {
    folder: 'three-men-morris',
    view: t('model/threemenmorris/MorrisState', 'MorrisView'),
    eventType: null,
    eventData: {
      PLACE: MORRIS_MOVE,
      MOVE: MORRIS_MOVE,
      REMOVE: MORRIS_REMOVAL,
      DRAW_OFFERED: MORRIS_DRAW,
      DRAW_DECLINED: MORRIS_DRAW,
      GAME_OVER: `${MORRIS_MOVE} | ${MORRIS_REMOVAL} | ${MORRIS_DRAW}`,
    },
    rest: SEAT_REST,
    fixtures: ['model/threemenmorris/morrisFixtures.ts'],
  },
];
