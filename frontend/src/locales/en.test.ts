import { describe, it, expect } from 'vitest';
import { messagesEn } from './en';
import { SLAP_EXAMPLES } from '../components/slapExamples';
import { GAMES } from '../model/games';

describe('English messages', () => {
  it('givenEnglishMessages_thenRulesHasToggleTitleAndCloseLabel', () => {
    expect(messagesEn.rules.toggleLabel).toBeTruthy();
    expect(messagesEn.rules.panelTitle).toBeTruthy();
    expect(messagesEn.rules.closeLabel).toBeTruthy();
  });

  it('givenEnglishMessages_thenRulesHasSixSectionsEachWithATitle', () => {
    expect(messagesEn.rules.sections).toHaveLength(6);
    for (const section of messagesEn.rules.sections) {
      expect(section.title).toBeTruthy();
    }
  });

  it('givenTextSections_thenEachHasNonEmptyBody', () => {
    const textSections = messagesEn.rules.sections.filter(s => s.kind === 'text');
    expect(textSections.length).toBeGreaterThan(0);
    for (const section of textSections) {
      expect(section.body.length).toBeGreaterThan(0);
    }
  });

  it('givenSlapSection_thenItHasAFooterAndALabelForEverySlapExample', () => {
    const slap = messagesEn.rules.sections.find(s => s.kind === 'slap');
    if (slap?.kind !== 'slap') throw new Error('expected a slap section');
    expect(slap.footer).toBeTruthy();
    expect(SLAP_EXAMPLES).toHaveLength(4);
    for (const example of SLAP_EXAMPLES) {
      expect(slap.labels[example.key]).toBeTruthy();
    }
  });

  it('givenBullshitMessages_thenRulesHaveToggleTitleAndCloseLabel', () => {
    expect(messagesEn.bullshit.toggleLabel).toBeTruthy();
    expect(messagesEn.bullshit.panelTitle).toBeTruthy();
    expect(messagesEn.bullshit.closeLabel).toBeTruthy();
  });

  it('givenBullshitSections_thenEachHasATitleAndTextSectionsHaveBody', () => {
    expect(messagesEn.bullshit.sections.length).toBeGreaterThan(0);
    for (const section of messagesEn.bullshit.sections) {
      expect(section.title).toBeTruthy();
      if (section.kind === 'text') expect(section.body.length).toBeGreaterThan(0);
    }
  });

  it('givenBullshitCycleSection_thenItHasStepsAndACaption', () => {
    const cycle = messagesEn.bullshit.sections.find(s => s.kind === 'cycle');
    if (cycle?.kind !== 'cycle') throw new Error('expected a cycle section');
    expect(cycle.steps.length).toBeGreaterThan(1);
    expect(cycle.caption).toBeTruthy();
  });

  it('givenBullshitBranchSection_thenItHasOneNegativeAndOnePositiveOutcome', () => {
    const branch = messagesEn.bullshit.sections.find(s => s.kind === 'branch');
    if (branch?.kind !== 'branch') throw new Error('expected a branch section');
    expect(branch.intro).toBeTruthy();
    expect(branch.outcomes.some(o => o.tone === 'positive')).toBe(true);
    expect(branch.outcomes.some(o => o.tone === 'negative')).toBe(true);
    for (const outcome of branch.outcomes) {
      expect(outcome.condition).toBeTruthy();
      expect(outcome.result).toBeTruthy();
    }
  });

  it('givenBullshitUiMessages_thenEveryLeafStringIsNonEmpty', () => {
    const empty: string[] = [];
    const visit = (node: unknown, path: string) => {
      if (typeof node === 'string') {
        if (!node.trim()) empty.push(path);
      } else if (node && typeof node === 'object') {
        for (const [key, value] of Object.entries(node)) visit(value, `${path}.${key}`);
      }
    };
    visit(messagesEn.bullshitUi, 'bullshitUi');
    expect(empty).toEqual([]);
  });

  it('givenBullshitUiMessages_thenPluralFormsExistForPlayersAndCards', () => {
    expect(messagesEn.lobby.waitingForPlayers.one).toContain('{n}');
    expect(messagesEn.lobby.waitingForPlayers.other).toContain('{n}');
    expect(messagesEn.bullshitUi.table.myCards.one).toContain('{n}');
    expect(messagesEn.bullshitUi.table.myCards.other).toContain('{n}');
    expect(messagesEn.bullshitUi.table.playedFaceDown.one).toContain('{n}');
    expect(messagesEn.bullshitUi.table.playedFaceDown.other).toContain('{n}');
  });

  it('givenLobbyMessages_thenItHasBadgesCopyFeedbackLeaveAndTheHostNotice', () => {
    const lobby = messagesEn.lobby;
    for (const key of ['title', 'leave', 'playersLabel', 'youBadge', 'botBadge', 'hostBadge', 'copy', 'copied', 'nowHost', 'renameLabel', 'waitingForPlayer', 'playerLabel', 'seatOptionLabel'] as const) {
      expect(lobby[key]).toBeTruthy();
    }
    expect(lobby.copied).toBe('Copied!');
    expect(lobby.nowHost).toBe('You are now the host.');
  });

  it('givenHomeAndJoinMessages_thenTheLobbyCreationAndLostSeatCasesAreWorded', () => {
    expect(messagesEn.home.createFailed).toBeTruthy();
    expect(messagesEn.home.creating).toBeTruthy();
    expect(messagesEn.home.seatLost).toBeTruthy();
    expect(messagesEn.join.notFound).toBeTruthy();
  });

  it('givenBullshitUiMessages_thenHandCardsHaveAnAccessibleLabelTemplate', () => {
    expect(messagesEn.bullshitUi.table.cardLabel).toContain('{rank}');
    expect(messagesEn.bullshitUi.table.cardLabel).toContain('{suit}');
  });

  it('givenOptionMessages_thenBullshitOptionsAreLabelledForEveryValue', () => {
    const o = messagesEn.options.bullshit;
    for (const text of [o.claimMode.label, o.claimMode.rank, o.claimMode.suit, o.deckSize.label, o.deckSize['32'], o.deckSize['52']]) {
      expect(text).toBeTruthy();
    }
    expect(o.vars.range.deckSize['32']).toBeTruthy();
    expect(o.vars.range.deckSize['52']).toBeTruthy();
  });

  it('givenBatailleCorseTier_thenTheNineTiersAreNamedFromTrainingToLegend', () => {
    const tier = messagesEn.options.bataillecorse.tier;
    expect(tier.label).toBeTruthy();
    expect(['0', '1', '2', '3', '4', '5', '6', '7', '8'].map(v => tier[v])).toEqual(
      ['Training', 'Bronze', 'Silver', 'Gold', 'Platinum', 'Diamond', 'Champion', 'Challenger', 'Legend']);
  });

  it('givenMorrisBotLevel_thenTheThreeLevelsAreNamedEasyMediumExpert', () => {
    const level = messagesEn.options.threeMenMorris.level;
    expect(level.label).toBe('Level');
    expect(['random', 'greedy', 'perfect'].map(v => level[v])).toEqual(['Easy', 'Medium', 'Expert']);
  });

  it('givenBatailleCorseTableMessages_thenEveryLeafStringIsNonEmptyAndTheBotNameQuotesItsTier', () => {
    for (const text of Object.values(messagesEn.batailleCorseUi)) expect(text).toBeTruthy();
    expect(messagesEn.batailleCorseUi.botWithTier).toContain('{bot}');
    expect(messagesEn.batailleCorseUi.botWithTier).toContain('{tier}');
  });

  it('givenGamePickerMessages_thenEveryLeafStringIsNonEmpty', () => {
    const empty: string[] = [];
    const visit = (node: unknown, path: string) => {
      if (typeof node === 'string') {
        if (!node.trim()) empty.push(path);
      } else if (node && typeof node === 'object') {
        for (const [key, value] of Object.entries(node)) visit(value, `${path}.${key}`);
      }
    };
    visit(messagesEn.gamePicker, 'gamePicker');
    expect(empty).toEqual([]);
  });

  it('givenGamePickerMessages_thenBothGamesAreDescribedWithTheirPlayerCount', () => {
    expect(messagesEn.gamePicker.games.bataillecorse!.players).toBe('2 players');
    expect(messagesEn.gamePicker.games.bullshit!.players).toBe('2–6 players');
    expect(messagesEn.gamePicker.games.bullshit!.description).not.toMatch(/bot/i);
    expect(messagesEn.gamePicker.games.kobo!.name).toBe('Kobo');
    expect(messagesEn.gamePicker.games.kobo!.players).toBe('2–6 players');
    expect(messagesEn.gamePicker.games.kobo!.description).toBe('Remember your hidden cards and end the round with the fewest points.');
  });

  it('givenKoboMessages_thenRulesHaveATitleAndBodyPerSectionAndTheScreenTextsExist', () => {
    expect(messagesEn.kobo.toggleLabel).toBeTruthy();
    expect(messagesEn.kobo.panelTitle).toBeTruthy();
    expect(messagesEn.kobo.closeLabel).toBeTruthy();
    expect(messagesEn.kobo.sections.length).toBeGreaterThan(0);
    for (const section of messagesEn.kobo.sections) {
      expect(section.title).toBeTruthy();
      if (section.kind === 'text') expect(section.body.length).toBeGreaterThan(0);
    }
    expect(messagesEn.koboUi.connecting).toBeTruthy();
    expect(messagesEn.koboUi.back).toBeTruthy();
    expect(messagesEn.koboUi.table.round).toContain('{n}');
    expect(Object.keys(messagesEn.koboUi.phase.mine)).toEqual(Object.keys(messagesEn.koboUi.phase.other));
    // Every sentence of the event log is filled, and says who acted (except the empty draw pile).
    for (const [key, text] of Object.entries(messagesEn.koboUi.log)) {
      expect(text, key).toBeTruthy();
      if (key !== 'ownerYour' && key !== 'drawEmpty') expect(text, key).toContain('{actor}');
    }
  });
});

describe('game home and join messages', () => {
  it('givenHomeAndJoin_thenEveryLeafStringIsNonEmpty', () => {
    const empty: string[] = [];
    const visit = (node: unknown, path: string) => {
      if (typeof node === 'string') {
        if (!node.trim()) empty.push(path);
      } else if (node && typeof node === 'object') {
        for (const [key, value] of Object.entries(node)) visit(value, `${path}.${key}`);
      }
    };
    visit(messagesEn.home, 'home');
    visit(messagesEn.join, 'join');
    expect(empty).toEqual([]);
  });
});

describe('shared screen messages', () => {
  it('givenEveryRegisteredGame_thenThePickerHasItsChoiceTexts', () => {
    for (const g of GAMES) {
      const choice = messagesEn.gamePicker.games[g.key];
      expect(choice, g.key).toBeDefined();
      for (const text of Object.values(choice!)) expect(text.trim()).not.toBe('');
    }
  });

  it('givenTheEndAndDisconnectScreens_thenTheirEnglishWordingIsTheOneTheyAlwaysHad', () => {
    expect(messagesEn.endScreen).toEqual({ victory: 'VICTORY', defeat: 'DEFEAT', draw: 'DRAW', backToHome: 'Back to home' });
    expect(messagesEn.endGame).toEqual({
      won: '{opponent} won.',
      resigned: '{opponent} resigned.',
      disconnected: '{opponent} disconnected.',
      beat: 'You beat {opponent}!',
    });
    expect(messagesEn.disconnect).toEqual({
      title: '{player} disconnected',
      waiting: 'Waiting for them to return…',
      countdown: 'You win in {n}s',
    });
    expect(messagesEn.leaveGuard.confirm).toBe('Leave the game? You will forfeit and your opponent wins.');
  });
});

describe("Three Men's Morris messages", () => {
  const emptyLeaves = (root: unknown, name: string): string[] => {
    const empty: string[] = [];
    const visit = (node: unknown, path: string) => {
      if (typeof node === 'string') {
        if (!node.trim()) empty.push(path);
      } else if (node && typeof node === 'object') {
        for (const [key, value] of Object.entries(node)) visit(value, `${path}.${key}`);
      }
    };
    visit(root, name);
    return empty;
  };

  it('givenThePicker_thenMorrisIsNamedWithAShortPitchAndItsTwoPlayers', () => {
    expect(messagesEn.gamePicker.games['three-men-morris']).toEqual({
      name: "Three Men's Morris",
      description: 'Line up your three pieces. Place, then slide.',
      players: '2 players',
      play: 'Play',
    });
  });

  it('givenTheRules_thenTheyCoverGoalBoardPlacementMovementBlockedAndForfeit', () => {
    const rules = messagesEn.threeMenMorris;
    expect(rules.toggleLabel).toBeTruthy();
    expect(rules.panelTitle).toBeTruthy();
    expect(rules.closeLabel).toBeTruthy();
    expect(rules.sections.map(s => s.kind)).toEqual(Array(rules.sections.length).fill('text'));
    const text = rules.sections.map(s => [s.title, ...(s.kind === 'text' ? s.body : [])].join(' ')).join(' ').toLowerCase();
    for (const topic of ['goal', 'board', 'placement', 'movement', 'blocked', 'forfeit']) {
      expect(rules.sections.some(s => s.title.toLowerCase().includes(topic)), topic).toBe(true);
    }
    expect(text).toContain('cannot move');
    expect(text).toContain('white');
  });

  it('givenTheTableMessages_thenEveryLeafStringIsNonEmpty', () => {
    expect(emptyLeaves(messagesEn.threeMenMorrisUi, 'threeMenMorrisUi')).toEqual([]);
    expect(emptyLeaves(messagesEn.threeMenMorris, 'threeMenMorris')).toEqual([]);
  });

  it('givenTheTableMessages_thenColoursPhaseHintsAndEndReasonsAreWorded', () => {
    const ui = messagesEn.threeMenMorrisUi;
    expect([ui.white, ui.black]).toEqual(['White', 'Black']);
    expect(ui.table.hintPlace).toBe('Place a piece');
    expect(ui.table.hintMove).toBe('Move a piece');
    expect(ui.table.piecesLeft.one).toContain('{n}');
    expect(ui.table.piecesLeft.other).toContain('{n}');
    expect(ui.end.blockedWon).toContain('{player}');
    expect(ui.end.blockedLost).toContain('{player}');
    expect(ui.end.winner).toBe('Winner: {player}');
  });

  it('givenTheDrawMessages_thenOfferAnswersRepetitionAndEndReasonsAreWorded', () => {
    const draw = messagesEn.threeMenMorrisUi.draw;
    expect(draw.offer).toBe('Offer a draw');
    expect(draw.offered).toBe('Draw offered');
    expect(draw.incoming).toBe('{player} offers a draw');
    expect(draw.accept).toBe('Accept draw');
    expect(draw.decline).toBe('Decline');
    expect(draw.repetition).toBe('This position has now appeared twice. A third time is a draw.');
    expect(draw.offeredBy).toContain('{player}');
    expect(draw.declinedBy).toContain('{player}');
    expect(draw.agreed).toBe('Draw agreed.');
    expect(draw.byRepetition).toBe('Draw by repetition: the same position occurred three times.');
  });
});
