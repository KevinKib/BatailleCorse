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

// Screen text of the Bullshit game. Placeholders use `{name}` and are filled by
// `format()` / `plural()` in `./format`.
export interface BullshitUiMessages {
  playerLabel: string;                 // '{n}' = 1-based seat number
  botLabel: string;                    // '{n}' = 1-based position among the bots
  you: string;
  back: string;
  sound: { mute: string; unmute: string }; // accessible names of the sound toggle (the action it performs)
  connecting: string;                  // shown while the first server state is on its way
  start: {
    title: string;
    yourName: string;
    claimModeLegend: string;
    claimModes: { rank: string; suit: string };
    deckSizeLegend: string;
    deckSizes: { '32': string; '52': string };
    createGame: string;
    gameId: string;
    joinGame: string;
    joinTitle: string;
    namePlaceholder: string;
    gameIdPlaceholder: string;
    joining: string;
    errors: {
      joinFailed: string;
      createFailed: string;
    };
  };
  lobby: {
    title: string;
    playerCount: string;               // '{joined}', '{max}'
    playersLabel: string;
    youBadge: string;
    botBadge: string;
    botName: string;                   // '{n}' = 1-based bot number
    addBot: string;
    removeBot: string;                 // '{name}': accessible name of the remove button
    botActionFailed: string;
    hostBadge: string;
    claimModeLabel: string;
    deckSizeLabel: string;
    inviteLabel: string;
    copy: string;
    copied: string;
    startGame: string;
    waitingForPlayers: PluralForms;    // '{n}' = players still needed
    waitingForHost: string;
  };
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
  };
  end: {
    youWon: string;
    youLost: string;
    playAgain: string;
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
  bataillecorse: GameChoiceMessages;
  bullshit: GameChoiceMessages;
}

// Whole-app message tree. Each game owns a rules namespace; future namespaces
// (game, lobby, ...) are added here additively.
export interface Messages {
  rules: RulesMessages;     // BatailleCorse
  bullshit: RulesMessages;  // Bullshit
  bullshitUi: BullshitUiMessages; // Bullshit screens
  gamePicker: GamePickerMessages; // Game choice page
}
