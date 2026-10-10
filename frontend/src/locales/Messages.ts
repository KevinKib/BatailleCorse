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

// Whole-app message tree. Each game owns a rules namespace; future namespaces
// (game, lobby, ...) are added here additively.
export interface Messages {
  rules: RulesMessages;     // BatailleCorse
  bullshit: RulesMessages;  // Bullshit
}
