import { describe, it, expect } from 'vitest';
import { messagesEn } from './en';
import { SLAP_EXAMPLES } from '../components/slapExamples';

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
    expect(messagesEn.bullshitUi.lobby.waitingForPlayers.one).toContain('{n}');
    expect(messagesEn.bullshitUi.lobby.waitingForPlayers.other).toContain('{n}');
    expect(messagesEn.bullshitUi.table.myCards.one).toContain('{n}');
    expect(messagesEn.bullshitUi.table.myCards.other).toContain('{n}');
    expect(messagesEn.bullshitUi.table.playedFaceDown.one).toContain('{n}');
    expect(messagesEn.bullshitUi.table.playedFaceDown.other).toContain('{n}');
  });

  it('givenBullshitUiMessages_thenLobbyHasBadgesCopyFeedbackAndClaimModeLabel', () => {
    const lobby = messagesEn.bullshitUi.lobby;
    for (const key of ['playersLabel', 'youBadge', 'botBadge', 'hostBadge', 'claimModeLabel', 'deckSizeLabel', 'copy', 'copied'] as const) {
      expect(lobby[key]).toBeTruthy();
    }
    expect(lobby.copied).toBe('Copied!');
  });

  it('givenBullshitUiMessages_thenStartScreensHaveJoinAndCreateErrors', () => {
    const start = messagesEn.bullshitUi.start;
    expect(start.errors.joinFailed).toBeTruthy();
    expect(start.errors.createFailed).toBeTruthy();
    expect(start.joinTitle).toBeTruthy();
    expect(start.namePlaceholder).toBeTruthy();
    expect(start.gameIdPlaceholder).toBeTruthy();
  });

  it('givenBullshitUiMessages_thenHandCardsHaveAnAccessibleLabelTemplate', () => {
    expect(messagesEn.bullshitUi.table.cardLabel).toContain('{rank}');
    expect(messagesEn.bullshitUi.table.cardLabel).toContain('{suit}');
  });

  it('givenBullshitUiMessages_thenClaimModesCoverRankAndSuit', () => {
    expect(messagesEn.bullshitUi.start.claimModes.rank).toBeTruthy();
    expect(messagesEn.bullshitUi.start.claimModes.suit).toBeTruthy();
    expect(messagesEn.bullshitUi.start.deckSizeLegend).toBeTruthy();
    expect(messagesEn.bullshitUi.start.deckSizes['32']).toBeTruthy();
    expect(messagesEn.bullshitUi.start.deckSizes['52']).toBeTruthy();
    expect(messagesEn.bullshitUi.start.rankRanges['32']).toBeTruthy();
    expect(messagesEn.bullshitUi.start.rankRanges['52']).toBeTruthy();
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
    expect(messagesEn.gamePicker.bataillecorse.players).toBe('2 players');
    expect(messagesEn.gamePicker.bullshit.players).toBe('2–6 players');
    expect(messagesEn.gamePicker.bullshit.description).not.toMatch(/bot/i);
  });
});
