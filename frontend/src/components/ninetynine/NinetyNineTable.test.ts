import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import NinetyNineTable from './NinetyNineTable.vue';
import { buildNinetyNineFourSeatRound, buildNinetyNineTwelveSeatRound } from '../../model/fixtures/ninetynine';

const nameOf = (seat: number) => `P${seat}`;

function mountTable(state = buildNinetyNineTwelveSeatRound(), extra: Record<string, unknown> = {}) {
  return mount(NinetyNineTable, {
    props: {
      players: state.players, mySeat: 0, currentSeat: state.currentSeat, direction: state.direction, total: state.total,
      pileTop: state.pileTop, stockSize: state.stockSize, maxLives: 3, nameOf, ...extra,
    },
    slots: { overlay: '<div data-test="banner-slot">banner</div>' },
  });
}

const labels = (wrapper: ReturnType<typeof mountTable>) => wrapper.findAll('[data-test="seat-name"]').map(n => n.text());

describe('NinetyNineTable', () => {
  it('givenTwelveSeats_thenEverySeatHasAPlateAndTheRingStartsWithMeThenSeatOrder', () => {
    const wrapper = mountTable();
    expect(wrapper.findAll('[data-test="seat-plate"]')).toHaveLength(12);
    expect(labels(wrapper)).toEqual(['You', ...Array.from({ length: 11 }, (_, i) => `P${i + 1}`)]);
  });

  it('givenAMeasuredFeltThatKeepsTheGap_thenTheRingIsUsed', () => {
    const wrapper = mountTable(buildNinetyNineTwelveSeatRound(), { frame: { width: 344, height: 365 } });
    expect(wrapper.get('[data-test="ninety-nine-table"]').attributes('data-layout')).toBe('ring');
    expect(wrapper.findAll('[data-test="seat-plate"]').every(p => p.element.parentElement?.getAttribute('style')?.includes('left'))).toBe(true);
  });

  it('givenAFeltTooNarrowForTwelvePlates_thenTheRailOfSeatsInTurnOrderIsUsed', () => {
    const wrapper = mountTable(buildNinetyNineTwelveSeatRound(), { frame: { width: 300, height: 365 } });
    expect(wrapper.get('[data-test="ninety-nine-table"]').attributes('data-layout')).toBe('rail');
    expect(wrapper.findAll('[data-test="seat-plate"]')).toHaveLength(12);
    expect(wrapper.get('[data-test="turn-order"]').text()).toContain('Turn order');
  });

  it('givenTheRail_thenTheSeatsAreNumberedInTurnOrderWithTheEliminatedOnesUnnumbered', () => {
    const state = buildNinetyNineTwelveSeatRound(); // direction reversed: after me comes seat 11
    const wrapper = mountTable(state, { frame: { width: 300, height: 365 } });
    const names = labels(wrapper);
    expect(names.slice(0, 3)).toEqual(['You', 'P11', 'P10']);
    const orders = wrapper.findAll('[data-test="seat-plate"]').map(p => p.find('[data-test="seat-order"]').exists() ? p.get('[data-test="seat-order"]').text() : '');
    expect(orders[0]).toBe('1');
    expect(orders[1]).toBe('2');
    // seat 9 is out: in the order of play it is the 3rd seat after mine (11, 10, 9...)
    expect(names[3]).toBe('P9');
    expect(orders[3]).toBe('');
  });

  it('givenTheRail_thenTheRailChipsKeepTheLivesAndTheOutState', () => {
    const wrapper = mountTable(buildNinetyNineTwelveSeatRound(), { frame: { width: 300, height: 365 } });
    expect(wrapper.findAll('[data-test="seat-out"]')).toHaveLength(1);
    expect(wrapper.findAll('[data-test="heart"][data-full="true"]').length).toBeGreaterThan(0);
  });

  it('givenTheSeatOnTurn_thenOnlyItsPlateIsMarked', () => {
    const wrapper = mountTable(buildNinetyNineFourSeatRound(), { currentSeat: 2 });
    const marked = wrapper.findAll('[data-test="seat-plate"][data-current="true"]');
    expect(marked).toHaveLength(1);
    expect(marked[0].attributes('data-seat')).toBe('2');
  });

  it('givenMyPlate_thenItIsTheOneNamedYouAndHighlighted', () => {
    const wrapper = mountTable(buildNinetyNineFourSeatRound());
    expect(wrapper.get('[data-test="seat-plate"][data-seat="0"]').classes()).toContain('plate--mine');
  });

  it('givenTheMiddleBlock_thenTheTotalPileAndStockAreShown', () => {
    const wrapper = mountTable();
    expect(wrapper.get('[data-test="total"]').text()).toContain('94');
    expect(wrapper.find('[data-test="pile"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="stock"]').exists()).toBe(true);
    expect(wrapper.get('[data-test="reversed"]').text()).toBe('Reversed');
  });

  it('givenAHitSeat_thenItShowsTheMinusOneBadge', () => {
    const wrapper = mountTable(buildNinetyNineFourSeatRound(), { hitSeat: 3 });
    expect(wrapper.findAll('[data-test="seat-hit"]')).toHaveLength(1);
    expect(wrapper.get('[data-seat="3"] [data-test="seat-hit"]').exists()).toBe(true);
  });

  it('givenAnOverlay_thenItIsRenderedOverTheFelt', () => {
    expect(mountTable().find('[data-test="banner-slot"]').exists()).toBe(true);
  });

  it('givenADisconnectedSeat_thenItsPlateShowsTheCountdown', () => {
    const wrapper = mountTable(buildNinetyNineFourSeatRound(), { disconnected: (seat: number) => seat === 1, secondsRemaining: () => 30 });
    expect(wrapper.get('[data-seat="1"] [data-test="seat-disconnect-badge"]').text()).toContain('30');
  });

  it('givenTheTable_thenItIsALabelledRegion', () => {
    expect(mountTable().get('[data-test="ninety-nine-table"]').attributes('role')).toBe('region');
  });
});
