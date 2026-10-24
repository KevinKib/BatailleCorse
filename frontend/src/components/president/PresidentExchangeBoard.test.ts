import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentExchangeBoard from './PresidentExchangeBoard.vue';
import type { ExchangeBoard, ExchangeBoardRow } from '../../model/president/exchangeBoard';
import { buildPresidentCard as card } from '../../model/fixtures/president';

const NAMES = ['Ann', 'Ben', 'Cy', 'Di', 'Ed'];
const TITLES = ['President', 'Vice-President', 'Neutral', 'Vice-Scum', 'Scum'];

const row = (over: Partial<ExchangeBoardRow> = {}): ExchangeBoardRow => ({
  key: '0-4', high: 0, low: 4, count: 2, mine: false, state: 'choosing', best: null, back: null, ...over,
});
const board = (rows: ExchangeBoardRow[] = [row(), row({ key: '1-3', high: 1, low: 3, count: 1 })], neutral = false): ExchangeBoard =>
  ({ rows, neutral });

function mountBoard(props: Record<string, unknown> = {}) {
  return mount(PresidentExchangeBoard, {
    props: {
      board: board(), nameOf: (seat: number) => NAMES[seat], titleOf: (seat: number) => TITLES[seat], mySeat: 2,
      canSee: false, reading: null, still: false, ...props,
    },
  });
}
const rows = (w: ReturnType<typeof mountBoard>) => w.findAll('[data-test="board-row"]');

describe('PresidentExchangeBoard', () => {
  it('givenTheBoard_thenItIsARegionNamedTheExchangeWithItsTitle', () => {
    const w = mountBoard();
    const region = w.get('[data-test="exchange-board"]');
    expect(region.attributes('role')).toBe('region');
    expect(region.attributes('aria-label')).toBe('The exchange');
    expect(w.get('[data-test="board-title"]').text()).toBe('The exchange');
  });

  it('givenSeveralPairs_thenOneRowEachWithTheLowSeatTheHighSeatAndTheirTitles', () => {
    const w = mountBoard();
    expect(rows(w)).toHaveLength(2);
    const first = rows(w)[0];
    expect(first.get('[data-test="board-low"]').text()).toContain('Ed');
    expect(first.get('[data-test="board-low"]').text()).toContain('Scum');
    expect(first.get('[data-test="board-high"]').text()).toContain('Ann');
    expect(first.get('[data-test="board-high"]').text()).toContain('President');
  });

  it('givenATitleToCome_thenTheSeatShowsItsNameAlone', () => {
    const w = mountBoard({ titleOf: () => null });
    expect(rows(w)[0].get('[data-test="board-low"]').text()).toBe('Ed');
  });

  it('givenIAmInAPair_thenThereIsNoNeutralNote', () => {
    expect(mountBoard().find('[data-test="board-neutral"]').exists()).toBe(false);
  });

  it('givenANeutral_thenTheHeaderSaysThereIsNoExchangeForMe', () => {
    const w = mountBoard({ board: board(undefined, true) });
    expect(w.get('[data-test="board-neutral"]').text()).toBe('You are Neutral: no exchange for you');
  });

  it('givenEachLeg_thenItShowsAsManyMiniCardsAsThePairExchanges', () => {
    const w = mountBoard({ board: board([row({ state: 'done', count: 3 })]) });
    expect(w.get('[data-test="board-best"]').findAll('img')).toHaveLength(3);
    expect(w.get('[data-test="board-back"]').findAll('img')).toHaveLength(3);
  });

  it('givenAPairThatIsNotMine_thenTheMiniCardsAreBacks', () => {
    const w = mountBoard({ board: board([row({ state: 'done' })]) });
    const alts = w.get('[data-test="board-row"]').findAll('img').map(i => i.attributes('alt'));
    expect(alts.every(a => a === '')).toBe(true);
  });

  it('givenMyPair_thenTheMiniCardsAreFaces', () => {
    const best = [card('KING', 'HEART'), card('ACE', 'CLUB')];
    const back = [card('3', 'CLUB'), card('9', 'HEART')];
    const w = mountBoard({ board: board([row({ mine: true, state: 'done', best, back })]) });
    expect(w.get('[data-test="board-best"]').findAll('img').map(i => i.attributes('alt'))).toEqual(['King of hearts', 'Ace of clubs']);
    expect(w.get('[data-test="board-back"]').findAll('img').map(i => i.attributes('alt'))).toEqual(['3 of clubs', '9 of hearts']);
  });

  it.each(['giving', 'choosing'] as const)('givenTheReturnNotGivenYet_%s_thenTheSecondLegSaysWhoIsChoosing', (state) => {
    const w = mountBoard({ board: board([row({ state })]) });
    expect(w.get('[data-test="board-back"]').text()).toBe('Ann is choosing 2');
    expect(w.get('[data-test="board-back"]').findAll('img')).toHaveLength(0);
    expect(w.get('[data-test="board-best"]').findAll('img')).toHaveLength(2);
  });

  it('givenIAmTheHighSeatChoosing_thenTheSecondLegSaysYouChoose', () => {
    const w = mountBoard({ mySeat: 0, board: board([row({ mine: true })]) });
    expect(w.get('[data-test="board-back"]').text()).toBe('You choose 2');
  });

  it('givenAGivingRow_thenItsSecondLegIsDimmed', () => {
    const w = mountBoard({ board: board([row({ state: 'giving' }), row({ key: '1-3', state: 'choosing' })]) });
    expect(w.findAll('[data-test="board-back"]')[0].classes()).toContain('leg--dim');
    expect(w.findAll('[data-test="board-back"]')[1].classes()).not.toContain('leg--dim');
  });

  it.each([['giving', 'Giving the best cards'], ['choosing', 'Choosing the cards to give back'],
    ['returning', 'Giving cards back'], ['done', 'Done']] as const)('givenA%sRow_thenItsMarkCarriesTheStateAndItsName', (state, name) => {
    const mark = mountBoard({ board: board([row({ state })]) }).get('[data-test="board-mark"]');
    expect(mark.attributes('data-state')).toBe(state);
    expect(mark.attributes('aria-label')).toBe(name);
  });

  it('givenARow_thenAScreenReaderGetsItsFullSentence', () => {
    const w = mountBoard({ board: board([row({ state: 'done' }), row({ key: '1-3', high: 1, low: 3, count: 1, state: 'choosing' })]) });
    expect(rows(w)[0].get('[data-test="board-sentence"]').text())
      .toBe('Ed gives Ann their 2 best cards, Ann gives 2 back: Done');
    expect(rows(w)[1].get('[data-test="board-sentence"]').text())
      .toBe('Di gives Ben their best card, Ben gives one back: Choosing the cards to give back');
  });

  it('givenMyRow_thenItIsMarkedMine', () => {
    const w = mountBoard({ board: board([row({ mine: true }), row({ key: '1-3' })]) });
    expect(rows(w).map(r => r.attributes('data-mine'))).toEqual(['true', 'false']);
  });

  describe('the Seen button', () => {
    it('givenIMayAcknowledge_thenItIsThereNamedAndEmitsSeen', async () => {
      const w = mountBoard({ canSee: true });
      const seen = w.get('[data-test="board-seen"]');
      expect(seen.attributes('aria-label')).toBe('I have seen the exchange');
      expect(seen.attributes('disabled')).toBeUndefined();
      await seen.trigger('click');
      expect(w.emitted('seen')).toHaveLength(1);
    });

    it('givenIMayNotAcknowledge_thenItsPlaceIsKeptButItIsOutOfReachAndHidden', async () => {
      const w = mountBoard({ canSee: false });
      const seen = w.get('[data-test="board-seen"]');
      expect(seen.classes()).toContain('seen--off');
      expect(seen.attributes('disabled')).toBeDefined();
      expect(seen.attributes('aria-hidden')).toBe('true');
      await seen.trigger('click');
      expect(w.emitted('seen')).toBeUndefined();
    });
  });

  describe('the reading bar', () => {
    const reading = { startsInMs: 1200, durationMs: 3000, key: 2 };

    it('givenNoReading_thenThereIsNoBar', () => {
      expect(mountBoard().find('[data-test="board-bar"]').exists()).toBe(false);
    });

    it('givenAReading_thenTheBarFillsOverItsDurationAfterItsDelay', () => {
      const fill = mountBoard({ reading }).get('[data-test="board-bar-fill"]');
      expect(fill.attributes('style')).toContain('animation-duration: 3000ms');
      expect(fill.attributes('style')).toContain('animation-delay: 1200ms');
    });

    it('givenFast_thenTheBarIsAsLongAsTheReadingTime', () => {
      const fill = mountBoard({ reading: { ...reading, durationMs: 1500 } }).get('[data-test="board-bar-fill"]');
      expect(fill.attributes('style')).toContain('animation-duration: 1500ms');
    });

    it('givenStillOrInstant_thenTheBarIsNotDrawn', () => {
      expect(mountBoard({ reading, still: true }).find('[data-test="board-bar"]').exists()).toBe(false);
      expect(mountBoard({ reading: { ...reading, durationMs: 0 } }).find('[data-test="board-bar"]').exists()).toBe(false);
    });

    it('givenAnotherRound_thenTheBarStartsOver', async () => {
      const w = mountBoard({ reading });
      const before = w.get('[data-test="board-bar-fill"]').element;
      await w.setProps({ reading: { ...reading, key: 3 } });
      expect(w.get('[data-test="board-bar-fill"]').element).not.toBe(before);
    });
  });

  it('givenStill_thenTheBoardSaysSoForItsStyles', () => {
    expect(mountBoard({ still: true }).get('[data-test="exchange-board"]').classes()).toContain('exchange-board--still');
    expect(mountBoard().get('[data-test="exchange-board"]').classes()).not.toContain('exchange-board--still');
  });

  it('givenManyRows_thenTheyScrollInsideTheBoardNotTheBoardItself', () => {
    const w = mountBoard({ board: board(Array.from({ length: 4 }, (_, i) => row({ key: `k${i}` }))) });
    expect(w.get('[data-test="board-rows"]').element.tagName).toBe('UL');
    expect(rows(w)).toHaveLength(4);
  });
});
