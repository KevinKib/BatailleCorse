import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentRoundOver from './PresidentRoundOver.vue';
import { buildPresidentPlayer } from '../../model/fixtures/president';

const players = [
  buildPresidentPlayer(0, { place: 3, title: 'SCUM' }),
  buildPresidentPlayer(1, { place: 1, title: 'PRESIDENT' }),
  buildPresidentPlayer(2, { place: 2, title: 'NEUTRAL' }),
];

function mountOver(props: Record<string, unknown> = {}) {
  return mount(PresidentRoundOver, {
    props: { players, round: 2, nameOf: (seat: number) => `P${seat}`, canReady: true, ...props },
  });
}

describe('PresidentRoundOver (placeholder until the round summary)', () => {
  it('saysWhichRoundIsOverAndListsThePlacesWithTheirTitles', () => {
    const wrapper = mountOver();
    expect(wrapper.get('[data-test="round-over-title"]').text()).toBe('Round 2 is over');
    expect(wrapper.findAll('[data-test="round-over-place"]').map(l => l.text())).toEqual([
      '1. P1 President', '2. P2 Neutral', '3. P0 Scum',
    ]);
  });

  it('givenARoundOne_thenTheTitlesEarnedComeFromTheLastRound', () => {
    const noTitle = players.map(p => ({ ...p, title: null }));
    const lastRound = {
      round: 1, finishingOrder: [1, 2, 0],
      players: [
        { seat: 1, place: 1, title: 'PRESIDENT' as const, points: 3, total: 3 },
        { seat: 2, place: 2, title: 'NEUTRAL' as const, points: 2, total: 2 },
        { seat: 0, place: 3, title: 'SCUM' as const, points: 1, total: 1 },
      ],
    };
    const wrapper = mountOver({ players: noTitle, lastRound });
    expect(wrapper.findAll('[data-test="round-over-place"]').map(l => l.text())).toEqual([
      '1. P1 President', '2. P2 Neutral', '3. P0 Scum',
    ]);
  });

  it('whenReadyIsPressed_thenItEmitsReady', async () => {
    const wrapper = mountOver();
    await wrapper.get('[data-test="ready"]').trigger('click');
    expect(wrapper.emitted('ready')).toHaveLength(1);
  });

  it('givenIAlreadyPressedReady_thenTheButtonGivesWayToAWaitingLine', () => {
    const wrapper = mountOver({ canReady: false });
    expect(wrapper.find('[data-test="ready"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="round-over-waiting"]').text()).toBe('Waiting for the others');
  });
});
