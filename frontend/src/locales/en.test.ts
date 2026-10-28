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

  it('givenKoboBotLevel_thenTheThreeLevelsAreNamedEasyMediumHard', () => {
    const level = messagesEn.options.kobo.level;
    expect(level.label).toBe('Level');
    expect(['easy', 'medium', 'hard'].map(v => level[v])).toEqual(['Easy', 'Medium', 'Hard']);
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

describe('President messages', () => {
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

  it('givenThePicker_thenPresidentIsNamedWithItsPlayerCount', () => {
    expect(messagesEn.gamePicker.games.president!.name).toBe('President');
    expect(messagesEn.gamePicker.games.president!.players).toBe('3–8 players');
  });

  it('givenThePresidentTexts_thenEveryLeafStringIsNonEmpty', () => {
    expect(emptyLeaves(messagesEn.presidentUi, 'presidentUi')).toEqual([]);
    expect(emptyLeaves(messagesEn.president, 'president')).toEqual([]);
    expect(emptyLeaves(messagesEn.options.president, 'options.president')).toEqual([]);
  });

  it('givenThePresidentTexts_thenTheCardsOfATrickAreSetAside_neverTakenOrWon', () => {
    // Owner feedback (2026-10-21): the player who wins a trick does not take its cards, they set them aside.
    const leaves: string[] = [];
    const visit = (node: unknown) => {
      if (typeof node === 'string') leaves.push(node);
      else if (node && typeof node === 'object') Object.values(node).forEach(visit);
    };
    visit(messagesEn.presidentUi);
    visit(messagesEn.president);
    const wrong = leaves.filter(text => /\b(takes?|took|taken|collects?|collected|wins?|won)\b[^.]*\b(trick|cards of the trick|pile)\b/i.test(text));
    expect(wrong).toEqual([]);
    expect(messagesEn.presidentUi.trickEnd.ALL_PASSED).toBe('{player} sets the trick aside: everyone else passed.');
  });

  it('givenThePresidentRules_thenTheyHaveATitleAndABodyPerSection', () => {
    expect(messagesEn.president.sections.length).toBeGreaterThan(0);
    for (const section of messagesEn.president.sections) {
      expect(section.title).toBeTruthy();
      if (section.kind === 'text') expect(section.body.length).toBeGreaterThan(0);
    }
  });

  it('givenTheExchangeView_thenItsFivePhaseLinesAreWritten', () => {
    const v = messagesEn.presidentUi.exchangeView;
    expect(v.watching).toBe('The exchange');
    expect(v.waitingOne).toBe('Waiting for {player} to see the exchange');
    expect(v.waitingTwo).toBe('Waiting for {a} and {b}');
    expect(v.waitingMany).toBe('Waiting for {n} players');
    expect(v.starting).toBe('The first trick starts');
  });

  it('givenTheTitles_thenTheyAreTheEnglishOnes', () => {
    expect(messagesEn.presidentUi.titles).toEqual({
      president: 'President', vicePresident: 'Vice-President', neutral: 'Neutral', viceScum: 'Vice-Scum', scum: 'Scum',
    });
  });

  it('givenTheOptions_thenEveryKeyAndValueOfTheLobbyIsLabelled', () => {
    const o = messagesEn.options.president;
    const values: Record<string, string[]> = {
      afterPass: ['reenter', 'out'],
      exchange: ['two', 'one', 'scaled', 'none'],
      giveBack: ['free', 'lowest'],
      nextLead: ['scum', 'president'],
      scoring: ['rank', 'titles', 'zeroSum'],
      length: ['rounds-5', 'rounds-3', 'rounds-10', 'points-11', 'points-21', 'endless'],
      mustPlay: ['off', 'on'],
      reducedCut: ['off', 'on'],
    };
    for (const [key, list] of Object.entries(values)) {
      expect(o[key].label, key).toBeTruthy();
      for (const value of list) expect(o[key][value], `${key}.${value}`).toBeTruthy();
    }
    expect(o.preset.classic).toBeTruthy();
    // equal play, the king of hearts lead and the forbidden finish are fixed rules: no option text for them
    for (const removed of ['equalPlay', 'firstLead', 'forbiddenFinish']) expect(o[removed], removed).toBeUndefined();
  });

  it('givenTheTrickVariants_thenTheirChipsEndReasonsAndMarkersAreWritten', () => {
    const ui = messagesEn.presidentUi;
    for (const chip of ['mustPlay.on', 'reducedCut.on']) expect(ui.chips[chip], chip).toBeTruthy();
    for (const reason of ['ALL_PASSED', 'FIRST_OUT', 'CUT', 'QUAD']) expect(ui.trickEnd[reason], reason).toContain('{player}');
    expect(ui.trickEnd.forbidden).toContain('{player}');
    expect(ui.table).not.toHaveProperty('out');
    expect(ui.table.outForbidden).toBeTruthy();
    expect(ui.table.outForbiddenPlace).toContain('{n}');
    expect(ui.summary.forbidden).toBeTruthy();
    expect(ui.summary.forbiddenLabel).toBeTruthy();
  });

  it('givenTheTableAnimations_thenTheBadgesBubblesIndicatorAndSpeedAreWritten', () => {
    const m = messagesEn.presidentUi.motion;
    for (const reason of ['ALL_PASSED', 'FIRST_OUT', 'CUT', 'QUAD']) expect(m.closed[reason], reason).toBeTruthy();
    expect(m.closed.CUT).toBe('Closed by a 2');
    expect(m.leads).toContain('{player}');
    expect(m.binding.orNothing).toBe('or nothing');
    expect(m.binding.played).toContain('{n}');
    expect(m.trickCount.other).toContain('{n}');
    expect(m.bubbles).toEqual({ pass: 'Pass', outForbidden: 'Out on a 2', completed: 'Four completed', out: 'Out · {place}' });
    expect(m.speed).toContain('{speed}');
    expect(m.speeds).toEqual({ normal: 'Normal', fast: 'Fast', instant: 'Instant' });
  });

  it('givenTheFinishAndExchangeVisibility_thenTheirTextsAreWritten', () => {
    const ui = messagesEn.presidentUi;
    expect(ui.ordinal).toEqual({ one: '{n}st', two: '{n}nd', few: '{n}rd', other: '{n}th' });
    expect(ui.motion.bubbles.out).toBe('Out · {place}');
    expect(ui.table.medal).toEqual({ out: 'Out', label: 'Out, {place} place' });
    const b = ui.exchange.board;
    expect(b.title).toBe('The exchange');
    expect(b.neutral).toBe('You are Neutral: no exchange for you');
    expect(b.choosing).toBe('{player} is choosing {n}');
    expect(b.youChoose).toBe('You choose {n}');
    expect(b.seen).toBe('I have seen the exchange');
    for (const state of ['giving', 'choosing', 'returning', 'done'] as const) expect(b.states[state], state).toBeTruthy();
    for (const form of [b.row.one, b.row.other]) for (const hole of ['{high}', '{low}', '{state}']) expect(form).toContain(hole);
    expect(b.row.other).toContain('{n}');
    expect(ui.announce.out).toBe('{player} is out, {place}');
    expect(ui.announce.outYou).toBe('You are out, {place}');
    for (const key of ['best', 'bestMine', 'bestToMe', 'back', 'backMine', 'backToMe'] as const) {
      expect(ui.announce[key].one, key).toBeTruthy();
      expect(ui.announce[key].other, key).toBeTruthy();
    }
    expect(ui.announce.bestMine.other).toContain('{cards}');
    expect(ui.announce.bestToMe.other).toContain('{cards}');
  });

  it('givenABotSeat_thenItsPresidentLevelsAreNamed', () => {
    const { level } = messagesEn.options.president;
    expect(level.label).toBe('Level');
    expect(['easy', 'medium', 'hard'].map(v => level[v])).toEqual(['Easy', 'Medium', 'Hard']);
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

describe('public game list messages', () => {
  it('givenJoinMessages_thenThePublicGamesListHasEveryText', () => {
    const list = messagesEn.join.publicGames;
    for (const key of ['title', 'openCount', 'totalCount', 'openOnly', 'join', 'joinAria', 'open', 'full', 'inPlay',
      'players', 'loading', 'empty', 'emptyHint', 'newGame', 'offline', 'truncated'] as const) {
      expect(list[key], key).toBeTruthy();
    }
    expect(messagesEn.join.or).toBeTruthy();
    expect(list.joinAria).toContain('{host}');
    expect(list.players).toContain('{joined}');
    expect(list.bots.one).toContain('{n}');
    expect(list.bots.other).toContain('{n}');
    expect(list.announce.newGames.one).toContain('{n}');
    expect(list.announce.newGames.other).toContain('{n}');
    expect(list.announce.openNow).toContain('{open}');
    expect(list.announce.noneOpen).toBeTruthy();
  });

  it('givenHomeMessages_thenThePrivateGameCheckboxIsWorded', () => {
    expect(messagesEn.home.privateGame).toBeTruthy();
    expect(messagesEn.home.privateGameHint).toBeTruthy();
  });

  it('givenLobbyMessages_thenThePrivateGameOptionIsWorded', () => {
    const lobby = messagesEn.lobby;
    for (const key of ['privateGame', 'privateHint', 'privateChip', 'inviteHintPrivate', 'inviteHintPublic'] as const) {
      expect(lobby[key], key).toBeTruthy();
    }
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
      name: 'Morris',
      description: "Tapatan, Achi or Six Men's Morris. Place, then slide.",
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

  it('givenTheSixRules_thenTheyCoverMillsRemovalDrawsAndTheBots', () => {
    const rules = messagesEn.morrisVariantRules.six;
    expect(rules.panelTitle).toBe("Six Men's Morris");
    for (const topic of ['goal', 'board', 'placement', 'movement', 'mills', 'blocked', 'draws', 'bots', 'forfeit']) {
      expect(rules.sections.some(s => s.title.toLowerCase().includes(topic)), topic).toBe(true);
    }
    const text = rules.sections.map(s => (s.kind === 'text' ? s.body : []).join(' ')).join(' ').toLowerCase();
    expect(text).toContain('two pieces');
    expect(text).toContain('never jump');
    expect(text).toContain('fifty moves');
    expect(text).toContain('three moves ahead');
    expect(emptyLeaves(messagesEn.morrisVariantRules, 'morrisVariantRules')).toEqual([]);
  });

  it('givenTheSixBoard_thenItNamesSixteenPointsAndTheMillStates', () => {
    const board = messagesEn.morrisBoard;
    expect(board.sixPointNames).toHaveLength(16);
    expect(new Set(board.sixPointNames).size).toBe(16);
    expect(emptyLeaves(board, 'morrisBoard')).toEqual([]);
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

describe('Mangola messages', () => {
  const emptyLeaves = (node: unknown, path: string): string[] => {
    if (typeof node === 'string') return node.trim() === '' ? [path] : [];
    if (Array.isArray(node)) return node.flatMap((v, i) => emptyLeaves(v, `${path}[${i}]`));
    if (node && typeof node === 'object') return Object.entries(node).flatMap(([k, v]) => emptyLeaves(v, `${path}.${k}`));
    return [];
  };

  it('givenThePicker_thenMangolaIsNamedWithAShortPitchAndItsTwoPlayers', () => {
    expect(messagesEn.gamePicker.games.mangola).toEqual({
      name: 'Mangola',
      description: 'Lay out your seeds, then sow and capture. Take every seed of your opponent.',
      players: '2 players',
      play: 'Play',
    });
  });

  it('givenTheRules_thenTheyCoverGoalBoardPlacementMoveCaptureEndAndForfeit', () => {
    const rules = messagesEn.mangola;
    expect(rules.toggleLabel).toBeTruthy();
    expect(rules.panelTitle).toBe('Mangola');
    expect(rules.closeLabel).toBeTruthy();
    for (const topic of ['goal', 'board', 'placement', 'move', 'capture', 'draw', 'forfeit']) {
      expect(rules.sections.some(s => s.title.toLowerCase().includes(topic)), topic).toBe(true);
    }
  });

  it('givenTheMessages_thenEveryLeafStringIsNonEmpty', () => {
    expect(emptyLeaves(messagesEn.mangolaUi, 'mangolaUi')).toEqual([]);
    expect(emptyLeaves(messagesEn.mangola, 'mangola')).toEqual([]);
    expect(emptyLeaves(messagesEn.options.mangola, 'options.mangola')).toEqual([]);
  });

  it('givenTheLobbyOptions_thenEveryValueTheServerOffersIsNamed', () => {
    const { firstPlayer } = messagesEn.options.mangola;
    // the set-up is always free placement (owner decision 2026-10-15): there is no set-up option to name
    expect('setup' in messagesEn.options.mangola).toBe(false);
    expect(firstPlayer.label).toBeTruthy();
    expect(['host', 'guest', 'random'].map(v => firstPlayer[v])).toEqual(['Host', 'Guest', 'Random']);
  });

  it('givenTheSowingDirectionOption_thenItAndTheRulesSentenceAreNamed', () => {
    const { direction } = messagesEn.options.mangola;
    expect(direction.label).toBe('Sowing direction');
    expect(['anticlockwise', 'clockwise'].map(v => direction[v])).toEqual(['Anticlockwise', 'Clockwise']);
    const board = messagesEn.mangola.sections.find(s => s.title.toLowerCase().includes('board'));
    expect(board?.kind).toBe('text');
    expect(board?.kind === 'text' && board.body.join(' ')).toContain(
      'The small arrows show the direction: anticlockwise unless the host chose clockwise in the lobby.');
  });

  it('givenABotSeat_thenItsLevelsAreNamedAndTheRulesSayTheExpertIsNotPerfect', () => {
    const { level } = messagesEn.options.mangola;
    expect(level.label).toBe('Level');
    expect(['random', 'greedy', 'expert'].map(v => level[v])).toEqual(['Easy', 'Medium', 'Expert']);
    const bots = messagesEn.mangola.sections.find(s => s.title.toLowerCase().includes('bot'));
    expect(bots?.kind).toBe('text');
    expect(bots?.kind === 'text' && bots.body.join(' ')).toContain('Expert is strong but not perfect');
  });

  it('givenTheTextsMovedOutOfTemplates_thenEnglishKeepsTheWordsPlayersSeeToday', () => {
    expect(messagesEn.batailleCorseUi).toMatchObject({ back: 'Back', send: 'Send', slap: 'Slap', you: 'You' });
    expect(messagesEn.gamePicker.playAria).toBe('{play} {name}');
    expect(messagesEn.mangolaUi.move.sentence).toBe('{head}: {details}.');
    expect(messagesEn.mangolaUi.move.sentenceAlone).toBe('{head}.');
    expect(messagesEn.language).toEqual({ label: 'Language', settings: 'Settings', names: { en: 'English', fr: 'Français' } });
  });

  it('givenTheTableMessages_thenTheEndScreenWordsEveryReason', () => {
    const ui = messagesEn.mangolaUi;
    expect(ui.end.winner).toBe('Winner: {player}');
    expect(ui.end.noMoveWon).toContain('{player}');
    expect(ui.end.noMoveLost).toBeTruthy();
    expect(ui.end.drawRepetition).toContain('three times');
    expect(ui.end.drawEndless).toBeTruthy();
    expect(ui.end.youWonResigned).toContain('{player}');
  });
});
