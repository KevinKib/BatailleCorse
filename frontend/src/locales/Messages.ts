import type { GameKey } from '../model/games';
import type { KoboPromptKey } from '../model/kobo/interaction';

export interface TextSection {
  kind: 'text';
  title: string;
  body: string[]; // one entry per paragraph / bullet line
}

// The four slap conditions. The card examples that illustrate each pattern are
// language-independent and live in `components/slapExamples.ts`; only the
// human-readable labels are translated here.
export interface SlapPatternLabels {
  doubles: string;
  sandwich: string;
  sumOfTen: string;
  tens: string;
}

export interface SlapSection {
  kind: 'slap';
  title: string;
  labels: SlapPatternLabels;
  footer: string;
}

// A self-advancing sequence shown as a row of rank chips with arrows and an
// optional wrap-around loop (used for Bullshit's auto-cycling claim).
export interface CycleSection {
  kind: 'cycle';
  title: string;
  steps: string[];   // chip labels in order, e.g. ['A', '2', '3', '…', 'K']
  loops: boolean;    // show the wrap-around loop indicator
  caption: string;   // explanation beneath the chips
  note?: string;     // optional aside (e.g. the suit variant)
}

// One outcome of a two-way branch. `tone` drives the marker/colour: 'positive'
// renders a green check, 'negative' a red cross.
export interface BranchOutcome {
  tone: 'positive' | 'negative';
  condition: string;
  result: string;
}

// A condition → outcome split (used for what happens when Bullshit is called).
export interface BranchSection {
  kind: 'branch';
  title: string;
  intro: string;
  outcomes: BranchOutcome[];
}

export type RulesSection = TextSection | SlapSection | CycleSection | BranchSection;

export interface RulesMessages {
  toggleLabel: string;      // text on the floating chip
  panelTitle: string;       // header of the expanded panel
  closeLabel: string;       // accessible label for the close button
  sections: RulesSection[]; // ordered rule sections
}

// Singular / other forms of a message; both contain `{n}` for the count.
export interface PluralForms {
  one: string;
  other: string;
}

// Keyboard shortcut settings shared by every game (WCAG 2.1.4: turn off or remap single-key shortcuts).
export interface HotkeyMessages {
  open: string;                        // accessible name and tooltip of the gear button
  title: string;
  enabled: string;                     // label of the master switch (pressed = shortcuts on)
  actions: { discard: string; call: string; send: string; slap: string }; // names of the remappable actions
  change: string;                      // button that starts listening for a new key
  changeAria: string;                  // '{action}': accessible name of that button
  listening: string;                   // shown on the button while it waits for a key
  reset: string;
  errors: { reserved: string; duplicate: string };
}

// Screen text of the Bullshit game. Placeholders use `{name}` and are filled by
// `format()` / `plural()` in `./format`.
export interface BullshitUiMessages {
  playerLabel: string;                 // '{n}' = 1-based seat number
  botLabel: string;                    // '{n}' = 1-based position among the bots
  you: string;
  back: string;
  sound: { mute: string; unmute: string }; // accessible names of the sound toggle (the action it performs)
  connecting: string;                  // shown while the first server state is on its way
  table: {
    claim: string;
    myCards: PluralForms;               // '{n}' = cards in my hand (accessible name of my counter)
    cardLabel: string;                 // '{rank}', '{suit}': accessible name of a hand card
    discardAs: string;                 // '{target}'
    callBullshit: string;
    truthful: string;
    bluff: string;
    revealCaption: string;             // '{caller}', '{claimant}', '{picker}'
    playedFaceDown: PluralForms;       // '{player}', '{n}' = number of cards
    forfeited: string;                 // '{player}'
    verdictAnnouncement: string;       // '{verdict}', '{caption}': what a screen reader hears after a call
    shortcut: string;                  // '{key}': tooltip of a button that has a keyboard shortcut
    keysHint: string;                  // '{discard}', '{call}': discreet reminder of the shortcuts
    offline: string;                   // my own connection is down; the socket retries by itself
  };
  end: {
    winner: string;                    // '{player}': names the winner on the end screen
    youWon: string;
    youLost: string;
    youWonResigned: string;            // '{player}': the last opponent resigned
    youWonDisconnected: string;        // '{player}': the last opponent timed out
    youWonOthersLeft: string;          // won with cards left but the reason is unknown (after a reload)
    playAgain: string;
    rematchPending: string;            // my play-again request is in flight
    rematchFailed: string;             // the server refused it
  };
}

// Game picker page (`/games`) and the back link of the game menus.
export interface GameChoiceMessages {
  name: string;
  description: string;
  players: string;
  play: string;
}

export interface GamePickerMessages {
  title: string;
  choose: string;          // accessible name of the list of games
  back: string;            // back link of a game menu, towards the picker
  /** One entry per game, by registry key; a registered game must have its entry (see `gameChoice`). */
  games: { [K in GameKey]?: GameChoiceMessages };
}

// End screen shared by the tables (`EndGameOverlay`).
export interface EndScreenMessages {
  victory: string;
  defeat: string;
  backToHome: string;
}

// End-screen subtitle of a duel. Placeholder: `{opponent}`.
export interface EndGameMessages {
  won: string;             // I lost: the opponent won
  resigned: string;        // I won: the opponent resigned
  disconnected: string;    // I won: the opponent disconnected
  beat: string;            // I won on the cards
}

// Overlay shown while an opponent's seat waits out the disconnect grace period.
export interface DisconnectMessages {
  title: string;           // '{player}'
  waiting: string;
  countdown: string;       // '{n}' = seconds left
}

export interface LeaveGuardMessages {
  confirm: string;         // native confirmation before leaving a game in progress
}

// Game home (`/games/<game>`): the front door of every game.
export interface GameHomeMessages {
  newGame: string;
  joinGame: string;
  yourName: string;
  namePlaceholder: string;
  back: string;            // towards the Game selection
  gameGone: string;        // notice after a room link that could not be restored
  seatLost: string;        // notice when the stored seat no longer resolves (the game is still there)
  creating: string;        // New game while the lobby is being created
  createFailed: string;    // the lobby could not be created; the player stays on the Game home
}

// Join form (`/games/<game>/join/<id?>`).
export interface JoinGameMessages {
  title: string;
  gameId: string;
  gameIdPlaceholder: string;
  yourName: string;
  namePlaceholder: string;
  submit: string;
  joining: string;
  failed: string;          // a refused join stays on the form with this line
  full: string;            // the lobby has no free seat left (409)
  started: string;         // the game has already started (409)
  notFound: string;        // the invite link points to a game that no longer exists (404)
  back: string;            // towards the Game home
}

// Shared lobby (`GameLobby`), the same for every game.
export interface LobbyMessages {
  title: string;
  leave: string;
  playerCount: string;               // '{joined}', '{max}'
  playersLabel: string;
  youBadge: string;
  botBadge: string;
  hostBadge: string;
  playerLabel: string;               // '{n}' = 1-based seat number, for a player without a name
  botName: string;                   // '{n}' = 1-based bot number
  seatOptionLabel: string;           // '{option}', '{name}': accessible name of a computer seat's option selector
  addBot: string;
  removeBot: string;                 // '{name}': accessible name of the remove button
  removeSeat: string;                // '{name}': accessible name of the host's remove-player button
  offlineBadge: string;              // a player whose connection dropped
  removedByHost: string;             // notice on the Game selection after the host removed me
  actionFailed: string;              // a lobby command was refused or could not be sent
  renameLabel: string;               // accessible name of the field holding my own name
  waitingForPlayer: string;          // free seat, shown to guests
  inviteLabel: string;
  copy: string;
  copied: string;
  startGame: string;
  starting: string;                  // announced while the Start is on its way
  waitingForPlayers: PluralForms;    // '{n}' = players still needed
  waitingForHost: string;
  nowHost: string;                   // announced when the host seat passes to me
}

// Texts of the game options a lobby renders from the backend descriptors. A descriptor's `labelKey`
// ("options.bullshit.claimMode") names a group: `label` is its title, every other entry the text of a
// value. `vars` feeds the placeholders of a value text from another option (the rank mode quotes the
// range of the deck in play): var name, then the option it depends on, then that option's value.
export type OptionTexts = Record<string, string>;
export interface OptionsMessages {
  bataillecorse: {
    tier: OptionTexts;                 // a bot seat's tier: `label`, then '0' (Training) to '8' (Legend)
  };
  bullshit: {
    claimMode: OptionTexts;
    deckSize: OptionTexts;
    vars: Record<string, Record<string, Record<string, string>>>;
  };
}

// Kobo screens (`KoboGameScreen`): the lobby wiring and the table (read only for now, the actions come next).
export interface KoboUiMessages {
  connecting: string;                  // before the first lobby view or game state
  back: string;
  you: string;                         // my own tag
  playerLabel: string;                 // '{n}' = 1-based seat number, for a player without a name
  table: {
    round: string;                     // '{n}'
    total: string;                     // '{n}' = the player's total points
    cards: PluralForms;                // '{n}' = cards in a tableau
    yourCards: string;                 // name of my grid
    cardsOf: string;                   // '{player}': name of an opponent's grid
    ownerYour: string;                 // owner part of a slot name, for my own cards
    ownerOf: string;                   // '{player}': owner part of a slot name, for another player's
    slotDown: string;                  // '{owner}', '{n}' = 1-based slot number: a face-down card
    slotUp: string;                    // '{owner}', '{n}', '{card}': a face-up card
    cardName: string;                  // '{rank}', '{suit}'
    drawPile: PluralForms;             // '{n}' = cards left
    discardPile: string;               // '{card}', '{n}': top card face up
    discardEmpty: string;
    drawnYours: string;                // '{card}'
    drawnOther: string;                // '{player}': a drawn card the viewer cannot see
    drawnNone: string;
    drawLabel: string;                 // visible captions of the piles
    discardLabel: string;
    drawnLabel: string;
    current: string;                   // seat marker, also its accessible text
    ready: string;
    announcer: string;                 // the player who called Kobo
    finalTurn: string;                 // still has a last turn to play
    forfeited: string;                 // '{player}'
    offline: string;
  };
  phase: {
    mine: Record<KoboPhaseKey, string>;
    other: Record<KoboPhaseKey, string>;   // '{player}' = the player the table waits for
  };
  actions: {                           // buttons of the action bar
    ready: string;                     // "I have memorised"
    draw: string;
    discard: string;                   // the drawn card
    skip: string;                      // a power
    endTurn: string;
    announce: string;                  // Kobo!
    cancel: string;                    // drops a half-made selection
    hide: string;                      // turns the peeked card face down
    match: string;                     // arms the matching discard
  };
  prompt: Record<KoboPromptKey, string>;   // one-line instruction while a click on a card is expected
  notice: Record<KoboNoticeKey, string>;   // why my last command did not go through
  log: KoboLogMessages;                    // the one-line event log
  youObject: string;                       // "you", as the object of a sentence
  sheet: KoboSheetMessages;                // the score sheet of the round end
  end: KoboEndMessages;                    // the end screen
}

/** The score sheet that rises under the table at the gift and at the end of a round. */
export interface KoboSheetMessages {
  label: string;                       // '{n}' = round number: accessible name of the sheet
  badge: string;                       // the announcer's "Kobo!"
  title: {
    won: string;                       // '{player}': the announcer had the fewest points
    wonYou: string;
    lost: string;                      // '{player}': the announcer did not
    lostYou: string;
    none: string;                      // nobody called Kobo
  };
  subtitle: {
    giftChoose: string;                // I won my Kobo and pick the receiver
    giftWaiting: string;               // '{player}' = the announcer
    gave: string;                      // '{giver}', '{receiver}'
    lost: string;                      // the announcer's penalty
  };
  columns: { player: string; hand: string; round: string; total: string };
  tableCaption: string;                // accessible name of the scores table
  totalChange: string;                 // '{before}', '{after}'
  pills: {
    kobo: string;
    gift: string;                      // "+10"
    giftLabel: string;                 // '{player}': accessible text of the gift pill
    reset: string;                     // '{from}', '{to}'
    resetLabel: string;                // '{from}', '{to}': accessible text of the reset pill
  };
  ready: string;                       // the tick of a player who is ready for the next round
  giveTen: string;                     // '{player}'
  nextRound: string;
  waitingOthers: string;               // I am ready, the others are not
}

/** The end screen: names, subtitle by score or by forfeit, final totals. */
export interface KoboEndMessages {
  winner: string;                      // '{players}'
  winners: string;                     // '{players}': several winners
  lowestTotal: PluralForms;            // '{n}' = the winners' total
  youWonResigned: string;              // '{player}'
  youWonDisconnected: string;
  youWonOthersLeft: string;
  playAgain: string;
  rematchPending: string;
  rematchFailed: string;
  totals: { title: string; player: string; rank: string; total: string; over: string };
}

/** '{actor}' = "You" or a name; '{owner}' = ownerYour or "{name}'s"; '{n}', '{m}' = 1-based slot numbers; '{card}'. */
export interface KoboLogMessages {
  ownerYour: string;                   // "your", mid-sentence
  draw: string;
  drawEmpty: string;
  swap: string;                        // '{actor}', '{n}' = slot swapped into, '{card}' = discarded
  discard: string;
  peek: string;                        // '{owner}', '{n}'
  blindSwap: string;                   // '{n}' = actor's slot, '{owner}', '{m}' = the other slot
  kingSwap: string;
  skipPower: string;
  endTurn: string;
  kobo: string;
  ready: string;
  matchOwn: string;                    // '{n}', '{card}'
  matchOther: string;                  // '{owner}', '{n}', '{card}', '{m}' = the card given
  matchOtherNoGive: string;
  matchMissed: string;
  giveTen: string;                     // '{owner}' = receiver (a name, "you")
}

export type KoboNoticeKey = 'matchTooLate' | 'moveImpossible' | 'connection';

export type KoboPhaseKey =
  | 'MEMORISING' | 'DRAW' | 'DECISION' | 'POWER' | 'KING_DECISION' | 'TURN_END' | 'GIFT' | 'ROUND_OVER' | 'FINISHED';

// Bataille Corse table (`GameScreen`): texts added with the shared lobby.
export interface BatailleCorseUiMessages {
  connecting: string;                  // before the first lobby view or game state
  opponent: string;                    // the opponent before the seats are known
  playerLabel: string;                 // '{n}' = 1-based seat number, for a player without a name
  botLabel: string;                    // '{n}' = 1-based bot number
  botWithTier: string;                 // '{bot}', '{tier}': the opponent label of a bot
  playAgain: string;
  rematchPending: string;              // my Play again request is in flight
  rematchFailed: string;               // the server refused it
}

// Texts shared by several game tables.
export interface TurnMessages {
  yourTurn: string;                    // the cue shown (and announced) when it is my turn
}

// Three Men's Morris board: every word of its accessible names (no visible text on the board itself).
export interface MorrisBoardMessages {
  boardLabel: string;                  // name of the board group
  pointNames: string[];                // the nine points, row by row (0 1 2 / 3 4 5 / 6 7 8)
  white: string;                       // a White piece is on the point
  black: string;                       // a Black piece is on the point
  free: string;                        // the point is empty
  canMove: string;                     // I may select this piece
  canPlace: string;                    // I may put a piece on this free point
  selected: string;                    // this piece is the selected one
  pointLabel: string;                  // {point} then the state words, e.g. "Centre, White piece, can move"
}

// Screen text of the Three Men's Morris table. Placeholders use `{name}` and are filled by `format()` / `plural()`.
export interface ThreeMenMorrisUiMessages {
  white: string;                       // colour of seat 0, who always starts
  black: string;                       // colour of seat 1
  you: string;
  playerLabel: string;                 // '{n}' = 1-based seat number, for a player without a name
  back: string;
  connecting: string;                  // shown while the first server state is on its way
  table: {
    youPlay: string;                   // '{colour}': shown before the first piece, with who starts
    hintPlace: string;                 // my turn, placement phase
    hintMove: string;                  // my turn, movement phase
    waitingFor: string;                // '{player}': not my turn
    piecesLeft: PluralForms;           // '{n}' = pieces still to place (accessible name of the discs)
    colourTag: string;                 // '{name}', '{colour}': accessible name of a player tag
    placed: string;                    // '{player}', '{point}': live region after a placement
    moved: string;                     // '{player}', '{from}', '{to}': live region after a move
    forfeited: string;                 // '{player}'
    offline: string;                   // my own connection is down; the socket retries by itself
  };
  end: {
    winner: string;                    // '{player}': names the winner on the end screen
    youWonLine: string;
    youLostLine: string;               // '{player}' lined up three
    blockedWon: string;                // '{player}' (the loser) cannot move
    blockedLost: string;               // '{player}' (the winner)
    youWonResigned: string;            // '{player}'
    youWonDisconnected: string;        // '{player}'
    youWonForfeit: string;             // the opponent left, reason unknown (event missed after a reload)
    youLostForfeit: string;            // I left (resigned or timed out) and see the finished game again
    playAgain: string;
    rematchPending: string;
    rematchFailed: string;
  };
}

// Whole-app message tree. Each game owns a rules namespace; future namespaces
// (game, lobby, ...) are added here additively.
export interface Messages {
  rules: RulesMessages;     // BatailleCorse
  bullshit: RulesMessages;  // Bullshit
  kobo: RulesMessages;      // Kobo
  hotkeys: HotkeyMessages; // keyboard shortcut settings
  turn: TurnMessages;      // turn cue shared by the tables
  threeMenMorris: RulesMessages;    // Three Men's Morris
  morrisBoard: MorrisBoardMessages; // Three Men's Morris board
  threeMenMorrisUi: ThreeMenMorrisUiMessages; // Three Men's Morris screens
  bullshitUi: BullshitUiMessages; // Bullshit screens
  koboUi: KoboUiMessages;   // Kobo screens
  batailleCorseUi: BatailleCorseUiMessages; // Bataille Corse table
  gamePicker: GamePickerMessages; // Game choice page
  endScreen: EndScreenMessages;   // End overlay shared by the tables
  endGame: EndGameMessages;       // End-screen subtitle of a duel
  disconnect: DisconnectMessages; // Opponent disconnected overlay
  leaveGuard: LeaveGuardMessages; // Confirmation before forfeiting by leaving
  home: GameHomeMessages;   // Game home
  join: JoinGameMessages;   // Join form
  lobby: LobbyMessages;     // Shared lobby
  options: OptionsMessages; // Game options shown in the lobby
}
