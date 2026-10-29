import type { Component } from 'vue';
import CheckerboardIcon from '../components/icons/CheckerboardIcon.vue';

/**
 * Every game the app knows by key. Keys are opened ahead of their game: a game only appears on the Game
 * selection once its own change registers it in `GAMES`.
 */
export type GameKey = 'bataillecorse' | 'bullshit' | 'kobo' | 'three-men-morris' | 'president' | 'mangola' | 'ninety-nine' | 'draughts'
  | 'blackjack' | 'kilo-de-merde';

/**
 * How a game's card looks on the Game selection (PrimeVue button icon and severity). The icon is a PrimeIcons
 * class, or a component (an inline SVG) for a glyph PrimeIcons does not have.
 */
export interface GamePickerStyle {
  icon: string | Component;
  severity: 'success' | 'warn' | 'info' | 'secondary' | 'danger' | 'help' | 'contrast';
}

/**
 * The local preferences (per device) a game declares: the remappable keyboard actions and the
 * localStorage entries they live in (one set per game). Action ids are the keys of `hotkeys.actions`
 * in the locale messages.
 */
export interface GameHotkeys {
  actions: readonly string[];
  storageKeys: readonly string[];
}

/**
 * What the Game selection, the router and the Game home need to know about a game. "New game" creates the
 * lobby straight away (`POST /api/lobby`) and the player lands in it.
 * Exits from a game never use it: they always lead to the Game selection (`/`).
 */
export interface GameDefinition {
  key: GameKey;
  /** The game type the server knows (`POST /api/lobby`, `/api/lobbies/{type}`): the key, except `bataille-corse`. */
  serverType: string;
  /** The game's route prefix, also where the Game home lives. */
  gameHomeRoute: string;
  joinRoute: (id?: string) => string;
  roomRoute: (id: string) => string;
  /** Whether the lobby offers "Add bot" (the server refuses bots for the games that have none). */
  supportsBots: boolean;
  picker: GamePickerStyle;
  /** Absent for a game without remappable shortcuts: it then has no gear on its home and lobby. */
  hotkeys?: GameHotkeys;
}

type GameTraits = Pick<GameDefinition, 'supportsBots' | 'picker' | 'hotkeys'>;

function define(key: GameKey, traits: GameTraits): GameDefinition {
  const base = `/${key}`;
  return {
    key,
    serverType: key === 'bataillecorse' ? 'bataille-corse' : key,
    gameHomeRoute: base,
    joinRoute: (id) => (id ? `${base}/join/${id}` : `${base}/join`),
    roomRoute: (id) => `${base}/room/${id}`,
    ...traits,
  };
}

export const GAMES: readonly GameDefinition[] = [
  // Bataille Corse keeps the keys it has always stored, one entry per action.
  define('bataillecorse', {
    supportsBots: true,
    picker: { icon: 'pi pi-play', severity: 'success' },
    hotkeys: { actions: ['send', 'slap'], storageKeys: ['bc_sendKey', 'bc_slapKey'] },
  }),
  define('bullshit', {
    supportsBots: true,
    picker: { icon: 'pi pi-bolt', severity: 'warn' },
    hotkeys: { actions: ['discard', 'call'], storageKeys: ['bullshit.hotkeys'] },
  }),
  // Bots play at three levels (a seat option); no remappable shortcuts (Tab, Enter, Escape and arrows only):
  // no gear on its home and lobby.
  define('kobo', {
    supportsBots: true,
    picker: { icon: 'pi pi-star', severity: 'info' },
  }),
  // The board is operated with Tab, arrows, Enter and Escape: no remappable shortcut, so no gear.
  // Bots play at three levels (a seat option).
  define('three-men-morris', {
    supportsBots: true,
    picker: { icon: 'pi pi-th-large', severity: 'info' },
  }),
  // Cards are tapped and then confirmed with buttons: no remappable shortcut, so no gear.
  // Bots play at three levels (a seat option).
  define('president', {
    supportsBots: true,
    picker: { icon: 'pi pi-crown', severity: 'contrast' },
  }),
  // The board is operated with Tab, arrows, Enter and Space: no remappable shortcut, so no gear.
  // Bots play at three levels (a seat option).
  define('mangola', {
    supportsBots: true,
    picker: { icon: 'pi pi-circle', severity: 'help' },
  }),
  // Cards are tapped (an Ace asks for its value): no remappable shortcut, so no gear.
  // Bots play at three levels (a seat option).
  define('ninety-nine', {
    supportsBots: true,
    picker: { icon: 'pi pi-hashtag', severity: 'danger' },
  }),
  // Pieces are tapped on the board: no remappable shortcut, so no gear. Bots play at three levels (a seat option).
  define('draughts', {
    supportsBots: true,
    picker: { icon: CheckerboardIcon, severity: 'secondary' },
  }),
  // Bets are chips and play is four buttons: no remappable shortcut, so no gear. Bots play at three levels (a seat option).
  define('blackjack', {
    supportsBots: true,
    picker: { icon: 'pi pi-wallet', severity: 'success' },
  }),
  // Cards are tapped and the pile is slapped, and every key is remappable (Space slaps, 1 to 4 pass, R is ready).
  // Bots play at three levels (a seat option).
  define('kilo-de-merde', {
    supportsBots: true,
    picker: { icon: 'pi pi-stopwatch', severity: 'warn' },
    hotkeys: { actions: ['slap', 'pass1', 'pass2', 'pass3', 'pass4', 'ready'], storageKeys: ['kilo-de-merde.hotkeys'] },
  }),
];

export function findGame(key: unknown): GameDefinition | undefined {
  return GAMES.find(g => g.key === key);
}
