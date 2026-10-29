import { describe, it, expect, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import KiloDeMerdeRoundBanner from './KiloDeMerdeRoundBanner.vue';
import { messages } from '../../locales';
import { messagesFr } from '../../locales/fr';
import { setCurrentLocaleForTest } from '../../locales/locale';
import { kiloCard } from '../../model/kilodemerde/fixtures';
import type { RoundLine } from '../../model/kilodemerde/roundSummary';

const nameOf = (seat: number) => `P${seat}`;
const mountBanner = (props: Record<string, unknown>) =>
  mount(KiloDeMerdeRoundBanner, { props: { race: null, lines: [], nameOf, ...props } });
const line = (code: RoundLine['code'], seat: number | null, card = kiloCard('KING', 'SPADE'), kg = 13): RoundLine =>
  ({ code, seat, card: seat === null ? null : card, kg });

describe('KiloDeMerdeRoundBanner', () => {
  it('givenNothingToSay_thenNothingIsRendered', () => {
    expect(mountBanner({}).find('[data-test="round-banner"]').exists()).toBe(false);
  });

  it('givenAnOpenRace_thenItNamesTheFirstSlapperAndListsTheOrder', () => {
    const wrapper = mountBanner({ race: { firstSlapper: 3, order: [3, 7, 5] } });
    expect(wrapper.get('[data-test="race-banner"]').text()).toContain('P3 slapped! Slap!');
    expect(wrapper.findAll('[data-test="race-order"] li').map(li => li.text())).toEqual(['1P3', '2P7', '3P5']);
  });

  it('givenTheOutcomeLines_thenEachKindHasItsText', () => {
    const cases: [RoundLine, string][] = [
      [line('LAST_DRAWS', 2), 'P2 was last and takes K♠: 13 kg'],
      [line('FALSE_SLAP', 2, kiloCard('9', 'CLUB'), 9), 'False slap: P2 takes 9♣: 9 kg'],
      [line('FLUSH', 2, kiloCard('7', 'HEART'), 0), 'Flush! P2 drops all their kilos'],
      [line('NOTHING', null), 'Nobody completed a set: no kilos this round'],
      [line('VOIDED', null), 'A player left: this round does not count'],
    ];
    for (const [l, text] of cases) {
      expect(mountBanner({ lines: [l] }).get('[data-test="round-line"]').text()).toBe(text);
    }
  });

  describe('my own seat', () => {
    afterEach(() => { setCurrentLocaleForTest('en'); delete messages.fr; });
    const inFrench = () => { messages.fr = messagesFr; setCurrentLocaleForTest('fr'); };

    it('givenIFirstSlapped_thenTheRaceTitleSpeaksToMe', () => {
      expect(mountBanner({ race: { firstSlapper: 0, order: [0] }, mySeat: 0 }).get('.race-title').text())
        .toBe('You slapped! Waiting for the others');
      inFrench();
      expect(mountBanner({ race: { firstSlapper: 0, order: [0] }, mySeat: 0 }).get('.race-title').text())
        .toBe('Vous avez tapé ! En attente des autres');
      expect(mountBanner({ race: { firstSlapper: 3, order: [3] }, mySeat: 0 }).get('.race-title').text())
        .toBe('P3 a tapé ! Tapez !');
    });

    it('givenIDrew_thenTheLineIsConjugatedForMe', () => {
      inFrench();
      expect(mountBanner({ lines: [line('LAST_DRAWS', 0)], mySeat: 0 }).get('[data-test="round-line"]').text())
        .toBe('Trop tard ! Vous prenez R♠ (13 kg)');
      expect(mountBanner({ lines: [line('LAST_DRAWS', 2)], mySeat: 0 }).get('[data-test="round-line"]').text())
        .toBe('Trop tard ! P2 prend R♠ (13 kg)');
    });
  });

  it('givenSeveralDrawers_thenEveryLineIsShown', () => {
    const wrapper = mountBanner({ lines: [line('LAST_DRAWS', 1), line('LAST_DRAWS', 4)] });
    expect(wrapper.findAll('[data-test="round-line"]')).toHaveLength(2);
  });
});
