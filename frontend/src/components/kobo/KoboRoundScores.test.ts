import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import KoboRoundScores from './KoboRoundScores.vue';
import { messages } from '../../locales';
import { messagesFr } from '../../locales/fr';
import { setCurrentLocaleForTest } from '../../locales/locale';
import type { RoundRow } from '../../model/kobo/roundScores';

const row = (seat: number, overrides: Partial<RoundRow> = {}): RoundRow => ({
  seat, handValue: 12, roundPoints: 12, totalBefore: 20, totalAfter: 32, announcer: false, gift: false,
  ...overrides,
});
const NAMES = ['Alice', 'Bob', 'Carol'];

function mountScores(rows: RoundRow[], props: Record<string, unknown> = {}) {
  return mount(KoboRoundScores, {
    props: { rows, nameOf: (seat: number) => NAMES[seat], mySeat: 0, readySeats: null, ...props },
  });
}

describe('KoboRoundScores', () => {
  it('showsHandRoundPointsAndTheTotalBeforeAndAfterOfEveryPlayer', () => {
    const wrapper = mountScores([row(0), row(1, { handValue: 7, roundPoints: 7, totalBefore: 3, totalAfter: 10 })]);
    expect(wrapper.findAll('[data-test^="score-row-"]')).toHaveLength(2);
    const bob = wrapper.get('[data-test="score-row-1"]');
    expect(bob.get('[data-test="score-name"]').text()).toBe('Bob');
    expect(bob.get('[data-test="score-hand"]').text()).toBe('7');
    expect(bob.get('[data-test="score-round"]').text()).toBe('7');
    expect(bob.get('[data-test="score-total"]').text()).toBe('3 → 10');
  });

  it('givenMySeat_thenMyRowIsCalledYouAndMarked', () => {
    const wrapper = mountScores([row(0), row(1)]);
    expect(wrapper.get('[data-test="score-row-0"] [data-test="score-name"]').text()).toBe('You');
    expect(wrapper.get('[data-test="score-row-0"]').classes()).toContain('score-row--me');
  });

  it('givenTheAnnouncer_thenKoboPillOnTheirRowOnly', () => {
    const wrapper = mountScores([row(0), row(1, { announcer: true })]);
    expect(wrapper.get('[data-test="score-row-1"] [data-test="pill-kobo"]').text()).toBe('Kobo!');
    expect(wrapper.find('[data-test="score-row-0"] [data-test="pill-kobo"]').exists()).toBe(false);
  });

  it('givenTheGiftReceiver_thenPlusTenPillWithAnAccessibleName', () => {
    const wrapper = mountScores([row(0), row(1, { gift: true })]);
    const pill = wrapper.get('[data-test="score-row-1"] [data-test="pill-gift"]');
    expect(pill.text()).toBe('+10');
    expect(pill.attributes('aria-label')).toBe('Bob received 10 points');
  });

  it('givenIReceivedTheGift_thenThePillSpeaksToMeInBothLanguages', () => {
    const pill = () => mountScores([row(0, { gift: true }), row(1)]).get('[data-test="score-row-0"] [data-test="pill-gift"]');
    expect(pill().attributes('aria-label')).toBe('You received 10 points');
    messages.fr = messagesFr;
    setCurrentLocaleForTest('fr');
    try {
      expect(pill().attributes('aria-label')).toBe('Vous avez reçu 10 points');
      expect(mountScores([row(0), row(1, { gift: true })]).get('[data-test="score-row-1"] [data-test="pill-gift"]')
        .attributes('aria-label')).toBe('Bob a reçu 10 points');
    } finally {
      setCurrentLocaleForTest('en');
      delete messages.fr;
    }
  });

  it('givenATotalOfExactly75_thenItIsShownAsIsWithoutAnyResetMarker', () => {
    const wrapper = mountScores([row(0), row(1, { totalBefore: 60, roundPoints: 15, totalAfter: 75 })]);
    expect(wrapper.get('[data-test="score-row-1"] [data-test="score-total"]').text()).toBe('60 → 75');
    expect(wrapper.find('[data-test="pill-reset"]').exists()).toBe(false);
  });

  it('givenReadySeats_thenOnlyTheReadyPlayersGetATick', () => {
    const wrapper = mountScores([row(0), row(1), row(2)], { readySeats: [0, 2] });
    expect(wrapper.find('[data-test="ready-0"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="ready-1"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="ready-2"]').text()).toBe('Ready');
  });

  it('givenNoReadyList_thenNoTicks', () => {
    expect(mountScores([row(0)]).find('[data-test^="ready-"]').exists()).toBe(false);
  });

  it('hasAHeaderRowWithTheFourColumns', () => {
    const headers = mountScores([row(0)]).findAll('thead th').map(h => h.text());
    expect(headers).toEqual(['Player', 'Hand', 'Round', 'Total']);
  });
});
