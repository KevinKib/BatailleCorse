export interface RulesSection {
  title: string;
  body: string[]; // one entry per paragraph / bullet line
}

export interface RulesMessages {
  toggleLabel: string;      // text on the floating chip
  panelTitle: string;       // header of the expanded panel
  sections: RulesSection[]; // ordered rule sections
}

// Whole-app message tree. Only `rules` is populated today; future namespaces
// (game, lobby, ...) are added here additively.
export interface Messages {
  rules: RulesMessages;
}
