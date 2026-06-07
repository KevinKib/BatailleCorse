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

export type RulesSection = TextSection | SlapSection;

export interface RulesMessages {
  toggleLabel: string;      // text on the floating chip
  panelTitle: string;       // header of the expanded panel
  closeLabel: string;       // accessible label for the close button
  sections: RulesSection[]; // ordered rule sections
}

// Whole-app message tree. Only `rules` is populated today; future namespaces
// (game, lobby, ...) are added here additively.
export interface Messages {
  rules: RulesMessages;
}
