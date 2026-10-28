import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import KiloDeMerdeTable from './KiloDeMerdeTable.vue';
import { ringBoxes, boxGap, MIN_GAP } from '../../model/table/ringLayout';
import { kiloDeMerdeRaceState, kiloDeMerdeRoundOverState } from '../../model/kilodemerde/fixtures';
import type { KiloDeMerdeState } from '../../model/kilodemerde/KiloDeMerdeState';

const nameOf = (seat: number) => `P${seat}`;

function mountTable(state: KiloDeMerdeState = kiloDeMerdeRaceState, extra: Record<string, unknown> = {}) {
  return mount(KiloDeMerdeTable, {
    props: {
      players: state.players, mySeat: 0, nameOf, roundLine: 'Round 4 of 10 · pass 6', countdown: null,
      pileSize: state.pileSize, canSlap: true, racing: true, ready: false, hands: null, lastSeats: [],
      ...extra,
    },
    slots: { overlay: '<div data-test="banner-slot">banner</div>' },
  });
}

const labels = (wrapper: ReturnType<typeof mountTable>) => wrapper.findAll('[data-test="seat-name"]').map(n => n.text());

describe('KiloDeMerdeTable', () => {
  it('givenTenSeats_thenEverySeatHasAPlateAndTheRingStartsWithMeThenMyLeftNeighbour', () => {
    const wrapper = mountTable();
    expect(wrapper.findAll('[data-test="seat-plate"]')).toHaveLength(10);
    expect(labels(wrapper)).toEqual(['You', ...Array.from({ length: 9 }, (_, i) => `P${i + 1}`)]);
  });

  it('givenAMeasuredFeltThatKeepsTheGap_thenTheRingIsUsed', () => {
    const wrapper = mountTable(kiloDeMerdeRaceState, { frame: { width: 1280, height: 520 } });
    expect(wrapper.get('[data-test="kilo-table"]').attributes('data-layout')).toBe('ring');
    expect(wrapper.findAll('.ring-slot')).toHaveLength(10);
  });

  it('givenAFeltTooNarrowForTenPlates_thenTheRailOfSeatsIsUsedAndEverySeatStaysVisible', () => {
    const wrapper = mountTable(kiloDeMerdeRaceState, { frame: { width: 280, height: 300 } });
    expect(wrapper.get('[data-test="kilo-table"]').attributes('data-layout')).toBe('rail');
    expect(wrapper.findAll('[data-test="seat-plate"]')).toHaveLength(10);
  });

  it('givenTheGeometryOfTenPlates_thenThePlatesKeepTheGapAt375And1280', () => {
    for (const frame of [{ width: 343, height: 380 }, { width: 1100, height: 520 }]) {
      const boxes = ringBoxes(10, frame);
      for (let i = 0; i < boxes.length; i++) {
        for (let j = i + 1; j < boxes.length; j++) expect(boxGap(boxes[i], boxes[j])).toBeGreaterThanOrEqual(MIN_GAP);
      }
    }
  });

  it('givenTheCentre_thenTheRoundLineAndThePileAreShownAndThePileSlaps', async () => {
    const wrapper = mountTable();
    expect(wrapper.get('[data-test="round-heading"]').text()).toBe('Round 4 of 10 · pass 6');
    expect(wrapper.get('[data-test="pile"]').attributes('aria-label')).toContain('Slap the pile');
    expect(wrapper.get('[data-test="pile-count"]').text()).toBe('12');
    await wrapper.get('[data-test="pile"]').trigger('click');
    expect(wrapper.emitted('slap')).toHaveLength(1);
  });

  it('givenACountdown_thenTheRingShowsTheSecondsLeft', () => {
    const wrapper = mountTable(kiloDeMerdeRaceState, { countdown: { remainingMs: 7200, seconds: 8, fraction: 0.48 } });
    expect(wrapper.get('[data-test="countdown"]').text()).toBe('8');
    expect(wrapper.get('[data-test="countdown"]').attributes('aria-label')).toBe('8 seconds left to pass');
    expect(mountTable().find('[data-test="countdown"]').exists()).toBe(false);
  });

  it('givenTheSeatsThatChose_thenOnlyTheirPlatesShowTheReadyCheck', () => {
    const wrapper = mountTable(kiloDeMerdeRaceState, { ready: true });
    expect(wrapper.findAll('[data-test="seat-ready"]')).toHaveLength(0);
    const players = kiloDeMerdeRaceState.players.map(p => ({ ...p, chosen: p.seat === 2 || p.seat === 5 }));
    expect(mountTable(kiloDeMerdeRaceState, { players, ready: true }).findAll('[data-test="seat-ready"]')).toHaveLength(2);
  });

  it('givenARoundOver_thenOnlyThenEveryPlateShowsItsHandAndTheLastDrawerIsTagged', () => {
    const over = kiloDeMerdeRoundOverState;
    expect(mountTable(over, { hands: null }).findAll('[data-test="seat-hand"]')).toHaveLength(0);
    const wrapper = mountTable(over, { hands: over.lastOutcome!.hands, lastSeats: [2] });
    expect(wrapper.findAll('[data-test="seat-hand"]')).toHaveLength(4);
    expect(wrapper.findAll('[data-test="seat-last"]')).toHaveLength(1);
    expect(wrapper.get('[data-seat="2"] [data-test="seat-last"]').exists()).toBe(true);
  });

  it('givenTheOverlaySlot_thenItIsRendered', () => {
    expect(mountTable().get('[data-test="banner-slot"]').exists()).toBe(true);
  });

  it('givenATapOnKilos_thenThePopoverOfThatSeatOpensAndCloses', async () => {
    const wrapper = mountTable();
    await wrapper.get('[data-seat="4"] [data-test="seat-kilos"]').trigger('click');
    expect(wrapper.get('[data-test="kilo-popover"]').attributes('aria-label')).toBe('Kilos of P4');
    expect(wrapper.findAll('[data-test="kilo-card"]').map(c => c.text())).toEqual(['9♣']);
    await wrapper.get('[data-test="kilo-close"]').trigger('click');
    expect(wrapper.find('[data-test="kilo-popover"]').exists()).toBe(false);
  });
});
