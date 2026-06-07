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
});
