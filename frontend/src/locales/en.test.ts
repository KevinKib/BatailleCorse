import { describe, it, expect } from 'vitest';
import { messagesEn } from './en';

describe('English messages', () => {
  it('givenEnglishMessages_thenRulesHasToggleAndTitle', () => {
    expect(messagesEn.rules.toggleLabel).toBeTruthy();
    expect(messagesEn.rules.panelTitle).toBeTruthy();
  });

  it('givenEnglishMessages_thenRulesHasSixSectionsEachWithTitleAndBody', () => {
    expect(messagesEn.rules.sections).toHaveLength(6);
    for (const section of messagesEn.rules.sections) {
      expect(section.title).toBeTruthy();
      expect(section.body.length).toBeGreaterThan(0);
    }
  });
});
