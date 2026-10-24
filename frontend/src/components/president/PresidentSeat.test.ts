import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentSeat from './PresidentSeat.vue';
import { buildPresidentPlayer } from '../../model/fixtures/president';

function mountSeat(props: Record<string, unknown> = {}) {
  return mount(PresidentSeat, { props: { player: buildPresidentPlayer(1, { handSize: 5 }), label: 'Bob', ...props } });
}

describe('PresidentSeat', () => {
  it('showsTheNameAndTheCardCount', () => {
    const wrapper = mountSeat();
    expect(wrapper.get('[data-test="seat-label"]').text()).toBe('Bob');
    expect(wrapper.get('[data-test="seat-cards"]').text()).toBe('5 cards');
  });

  it('givenOneCard_thenTheCountIsSingular', () => {
    expect(mountSeat({ player: buildPresidentPlayer(1, { handSize: 1 }) }).get('[data-test="seat-cards"]').text()).toBe('1 card');
  });

  it.each([
    ['PRESIDENT', 'President'],
    ['VICE_PRESIDENT', 'Vice-President'],
    ['NEUTRAL', 'Neutral'],
    ['VICE_SCUM', 'Vice-Scum'],
    ['SCUM', 'Scum'],
  ] as const)('givenTheTitle_%s_thenItIsWrittenAsText', (title, text) => {
    const wrapper = mountSeat({ player: buildPresidentPlayer(1, { title }) });
    expect(wrapper.get('[data-test="seat-title"]').text()).toBe(text);
  });

  it('givenNoTitleYet_thenNoTitleIsShown', () => {
    expect(mountSeat().find('[data-test="seat-title"]').exists()).toBe(false);
  });

  it('givenTheCurrentSeat_thenItSaysSo', () => {
    const wrapper = mountSeat({ current: true });
    expect(wrapper.get('[data-test="seat-label"]').classes()).toContain('seat-label--active');
    expect(wrapper.get('[data-test="seat-current"]').text()).toBe('Playing');
  });

  it('givenANarrowPlate_thenThePlayingMarkerIsOnlyForScreenReaders_theLitNameSaysItOnScreen', () => {
    const wrapper = mountSeat({ current: true, narrow: true, fan: true });
    expect(wrapper.get('[data-test="seat-current"]').classes()).toContain('sr-only');
    expect(wrapper.get('[data-test="seat-label"]').classes()).toContain('seat-label--active');
    expect(mountSeat({ current: true }).get('[data-test="seat-current"]').classes()).not.toContain('sr-only');
  });

  it('givenAPassedSeat_thenItSaysPassed', () => {
    const wrapper = mountSeat({ player: buildPresidentPlayer(1, { passed: true }) });
    expect(wrapper.get('[data-test="seat-passed"]').text()).toBe('Passed');
  });

  it('givenASeatThatIsOut_thenItShowsAMedalWithItsPlaceAndNotThePassMarker', () => {
    const wrapper = mountSeat({ fan: true, player: buildPresidentPlayer(1, { out: true, place: 2, handSize: 0, passed: true }) });
    const medal = wrapper.get('[data-test="seat-out"]');
    expect(medal.attributes('data-place')).toBe('2');
    expect(medal.get('[data-test="seat-medal-place"]').text()).toBe('2nd');
    expect(medal.get('[data-test="seat-medal-word"]').text()).toBe('Out');
    expect(wrapper.find('[data-test="seat-passed"]').exists()).toBe(false);
  });

  it('givenAMedal_thenItsVisibleTextIsHiddenFromAssistiveTechnologyAndItsLabelSaysItAll', () => {
    const wrapper = mountSeat({ fan: true, player: buildPresidentPlayer(1, { out: true, place: 1, handSize: 0 }) });
    const medal = wrapper.get('[data-test="seat-out"]');
    expect(medal.get('[data-test="seat-medal-place"]').attributes('aria-hidden')).toBe('true');
    expect(medal.get('[data-test="seat-medal-word"]').attributes('aria-hidden')).toBe('true');
    const label = medal.get('.sr-only');
    expect(label.text()).toBe('Out, 1st place');
  });

  it.each([[1, '1st'], [2, '2nd'], [3, '3rd'], [4, '4th']])('givenPlace_%i_thenTheMedalReads_%s', (place, text) => {
    const wrapper = mountSeat({ fan: true, player: buildPresidentPlayer(1, { out: true, place, handSize: 0 }) });
    expect(wrapper.get('[data-test="seat-medal-place"]').text()).toBe(text);
  });

  it('givenMyOwnPlateThatIsOut_thenTheMedalIsTheCompactSideVariantBesideMyName', () => {
    const wrapper = mountSeat({ player: buildPresidentPlayer(1, { out: true, place: 1, handSize: 0 }) });
    const medal = wrapper.get('.seat-markers--side [data-test="seat-out"]');
    expect(medal.classes()).toContain('medal--side');
    expect(medal.get('[data-test="seat-medal-place"]').text()).toBe('1st');
  });

  it('givenAFinishedSeat_thenTheLabelIsFinishedGoldOutlined_neverDimmed', () => {
    const wrapper = mountSeat({ fan: true, player: buildPresidentPlayer(1, { out: true, place: 1, handSize: 0 }) });
    expect(wrapper.classes()).toContain('president-seat--finished');
    expect(wrapper.classes()).toContain('president-seat--out');
  });

  it('givenAForbiddenFinish_thenNotFinished_noGoldOutline', () => {
    const wrapper = mountSeat({ fan: true, player: buildPresidentPlayer(1, { out: true, place: null, handSize: 0, forbiddenFinish: true }) });
    expect(wrapper.classes()).not.toContain('president-seat--finished');
    expect(wrapper.find('[data-test="seat-medal-place"]').exists()).toBe(false);
  });

  it('givenASeatStillInTheGame_thenNoMedalAndNotFinished', () => {
    const wrapper = mountSeat({ fan: true });
    expect(wrapper.classes()).not.toContain('president-seat--finished');
    expect(wrapper.find('[data-test="seat-out"]').exists()).toBe(false);
  });

  it('givenAForbiddenFinisher_thenItIsOutOnATwoWithoutAPlace', () => {
    const wrapper = mountSeat({ player: buildPresidentPlayer(1, { out: true, place: null, handSize: 0, forbiddenFinish: true }) });
    expect(wrapper.get('[data-test="seat-out"]').text()).toBe('Out on a 2');
    expect(wrapper.get('[data-test="seat-out"]').classes()).toContain('marker--forbidden');
  });

  it('givenAForbiddenFinisherOnceTheRoundIsOver_thenItKeepsTheMarkerAndShowsItsPlace', () => {
    const wrapper = mountSeat({ player: buildPresidentPlayer(1, { out: true, place: 4, handSize: 0, forbiddenFinish: true }) });
    expect(wrapper.get('[data-test="seat-out"]').text()).toBe('Out on a 2, place 4');
  });

  it('givenAReadySeatAtTheRoundEnd_thenItSaysReady', () => {
    const wrapper = mountSeat({ player: buildPresidentPlayer(1, { ready: true }), showReady: true });
    expect(wrapper.get('[data-test="seat-ready"]').text()).toBe('Ready');
  });

  describe('the pictures of the table', () => {
    it('givenAnOpponentsPlate_thenTheHandIsAFanOfBacksWithItsCount', () => {
      const wrapper = mountSeat({ fan: true });
      expect(wrapper.findAll('[data-test="fan-back"]')).toHaveLength(5);
      expect(wrapper.get('[data-test="seat-cards"]').text()).toBe('5 cards');
    });

    it('givenCardsStillInFlight_thenTheFanShowsTheCountItIsGiven', () => {
      const wrapper = mountSeat({ fan: true, count: 7 });
      expect(wrapper.findAll('[data-test="fan-back"]')).toHaveLength(7);
      expect(wrapper.get('[data-test="seat-cards"]').text()).toBe('7 cards');
    });

    it('givenMyOwnPlate_thenNoFan', () => {
      expect(mountSeat().find('[data-test="seat-fan"]').exists()).toBe(false);
    });

    it.each([
      ['pass', 'Pass'],
      ['outForbidden', 'Out on a 2'],
      ['completed', 'Four completed'],
    ] as const)('givenABubble_%s_thenItPopsOverThePlate', (kind, text) => {
      const bubble = mountSeat({ bubble: { kind, key: 1 } }).get('[data-test="seat-bubble"]');
      expect(bubble.text()).toBe(text);
      expect(bubble.attributes('data-kind')).toBe(kind);
      expect(bubble.attributes('aria-hidden')).toBe('true');
    });

    it('givenAnOutBubbleWithItsPlace_thenItReadsOutDotPlace_inGold', () => {
      const bubble = mountSeat({ bubble: { kind: 'out', vars: { place: '1st' }, key: 1 } }).get('[data-test="seat-bubble"]');
      expect(bubble.text()).toBe('Out · 1st');
      expect(bubble.attributes('data-kind')).toBe('out');
      expect(bubble.classes()).toContain('seat-bubble--out');
    });

    it('givenAPassedSeat_thenThePlateIsDimmed', () => {
      expect(mountSeat({ player: buildPresidentPlayer(1, { passed: true }) }).classes()).toContain('president-seat--passed');
    });
  });

  describe('a compact plate (a phone: the markers sit on the fan, never on a line of their own)', () => {
    const compact = (props: Record<string, unknown> = {}) => mountSeat({ fan: true, narrow: true, compact: true, ...props });
    const overlay = (w: ReturnType<typeof compact>) => w.find('.seat-hand .seat-markers--overlay');

    it('givenAPassedSeat_thenPassedIsOverTheFan_andStillSaysPassed', () => {
      const w = compact({ player: buildPresidentPlayer(1, { passed: true }) });
      expect(overlay(w).exists()).toBe(true);
      expect(overlay(w).get('[data-test="seat-passed"]').text()).toBe('Passed');
      expect(w.find('.seat-hand [data-test="seat-fan"]').exists()).toBe(true);
    });

    it('givenASeatThatIsOut_thenItsMedalTakesTheFansPlace', () => {
      const w = compact({ player: buildPresidentPlayer(1, { out: true, place: 2, handSize: 0 }) });
      expect(overlay(w).get('[data-test="seat-out"] [data-test="seat-medal-place"]').text()).toBe('2nd');
      expect(w.find('.fan-picture').classes()).toContain('fan-picture--reserved');
    });

    it('givenAForbiddenFinisher_thenOutOnATwoStaysRed', () => {
      const w = compact({ player: buildPresidentPlayer(1, { out: true, place: null, handSize: 0, forbiddenFinish: true }) });
      expect(overlay(w).get('[data-test="seat-out"]').text()).toBe('Out on a 2');
      expect(overlay(w).get('[data-test="seat-out"]').classes()).toContain('marker--forbidden');
    });

    it('givenReadyAtTheRoundEnd_thenReadyIsACheckInTheSamePill_andTheTextStaysForScreenReaders', () => {
      const w = compact({ player: buildPresidentPlayer(1, { out: true, place: 1, handSize: 0, ready: true }), showReady: true });
      const ready = overlay(w).get('[data-test="seat-ready"]');
      expect(ready.text()).toBe('Ready');
      expect(ready.find('.ready-text').classes()).toContain('sr-only');
      expect(ready.find('.pi-check').exists()).toBe(true);
    });

    it('givenNothingToSay_thenNoOverlay', () => {
      expect(overlay(compact()).exists()).toBe(false);
    });

    it('givenANormalPlate_thenTheMarkersAreOnTheFanToo_neverOnALineOfTheirOwn', () => {
      const w = mountSeat({ fan: true, player: buildPresidentPlayer(1, { passed: true }) });
      expect(w.find('.seat-hand .seat-markers--overlay [data-test="seat-passed"]').exists()).toBe(true);
    });

    it('givenASeatThatIsOutOnANormalPlate_thenTheFanKeepsItsHeight', () => {
      const w = mountSeat({ fan: true, player: buildPresidentPlayer(1, { out: true, place: 2, handSize: 0 }) });
      expect(w.find('.fan-picture').classes()).toContain('fan-picture--reserved');
    });
  });

  describe('the reserved places (nothing moves when a text comes or goes)', () => {
    it('givenATitleLineIsReserved_thenAPlateWithoutTitleKeepsTheLine', () => {
      const w = mountSeat({ fan: true, reserveTitle: true });
      expect(w.find('[data-test="seat-title"]').exists()).toBe(false);
      expect(w.get('[data-test="seat-title-slot"]').attributes('aria-hidden')).toBe('true');
    });

    it('givenNoTitleLineIsReserved_thenThereIsNoSlot', () => {
      expect(mountSeat({ fan: true }).find('[data-test="seat-title-slot"]').exists()).toBe(false);
    });

    it('givenATitle_thenItFillsTheReservedLineInsteadOfAddingOne', () => {
      const w = mountSeat({ fan: true, reserveTitle: true, player: buildPresidentPlayer(1, { title: 'SCUM' }) });
      expect(w.findAll('.seat-title')).toHaveLength(1);
      expect(w.get('[data-test="seat-title"]').text()).toBe('Scum');
    });

    it('givenMyOwnPlate_thenItsMarkersStandBesideTheNameNotUnderIt', () => {
      const w = mountSeat({ current: true });
      expect(w.find('.seat-markers--side [data-test="seat-current"]').exists()).toBe(true);
    });

    it('givenAnyNameState_thenTheTurnDotIsThereLitOrNot', () => {
      expect(mountSeat().find('.seat-label .turn-dot').exists()).toBe(true);
      expect(mountSeat({ current: true }).find('.seat-label .turn-dot').classes()).toContain('turn-dot--on');
    });
  });

  it('givenADisconnectedSeat_thenTheBadgeShows', () => {
    const wrapper = mountSeat({ disconnected: true, secondsRemaining: 30 });
    expect(wrapper.find('[data-test="seat-disconnect-badge"]').exists()).toBe(true);
  });
});
