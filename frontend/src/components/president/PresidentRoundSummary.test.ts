import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentRoundSummary from './PresidentRoundSummary.vue';
import { buildPresidentPlayer } from '../../model/fixtures/president';
import type { PresidentRound } from '../../model/president/PresidentState';

// Round 2 just ended. `players[].title` is the title played the round with, `lastRound` holds the ones earned.
const players = [
  buildPresidentPlayer(0, { place: 2, title: 'PRESIDENT', ready: true }),
  buildPresidentPlayer(1, { place: 1, title: 'SCUM' }),
  buildPresidentPlayer(2, { place: 3, title: 'NEUTRAL' }),
];
const lastRound: PresidentRound = {
  round: 2, finishingOrder: [1, 0, 2],
  players: [
    { seat: 1, place: 1, title: 'PRESIDENT', points: 3, total: 4, forbiddenFinish: false },
    { seat: 0, place: 2, title: 'NEUTRAL', points: 2, total: 7, forbiddenFinish: false },
    { seat: 2, place: 3, title: 'SCUM', points: 1, total: 2, forbiddenFinish: false },
  ],
};

function mountSummary(props: Record<string, unknown> = {}) {
  return mount(PresidentRoundSummary, {
    props: { players, round: 2, lastRound, nameOf: (seat: number) => `P${seat}`, canReady: true, ...props },
  });
}
const rows = (w: ReturnType<typeof mountSummary>) => w.findAll('[data-test="round-over-place"]');
const cell = (row: ReturnType<ReturnType<typeof mountSummary>['findAll']>[number], name: string) =>
  row.get(`[data-test="place-${name}"]`).text();

describe('PresidentRoundSummary, forbidden finishes', () => {
  const forbidden: PresidentRound = {
    round: 2, finishingOrder: [1, 0, 2],
    players: [
      { seat: 1, place: 1, title: 'PRESIDENT', points: 3, total: 4, forbiddenFinish: false },
      { seat: 0, place: 2, title: 'NEUTRAL', points: 2, total: 7, forbiddenFinish: false },
      { seat: 2, place: 3, title: 'SCUM', points: 1, total: 2, forbiddenFinish: true },
    ],
  };

  it('marksTheSeatThatWentOutOnATwoWithAVisibleLabel', () => {
    const r = rows(mountSummary({ lastRound: forbidden }));
    expect(r.map(row => row.find('[data-test="place-forbidden"]').exists())).toEqual([false, false, true]);
    const marker = r[2].get('[data-test="place-forbidden"]');
    expect(marker.text()).toBe('On a 2');
    expect(marker.attributes('title')).toBe('Went out on a 2: placed last');
  });

  it('givenNoForbiddenFinish_thenNoRowIsMarked', () => {
    expect(rows(mountSummary()).some(row => row.find('[data-test="place-forbidden"]').exists())).toBe(false);
  });
});

describe('PresidentRoundSummary', () => {
  it('saysWhichRoundIsOver', () => {
    expect(mountSummary().get('[data-test="round-over-title"]').text()).toBe('Round 2 is over');
  });

  it('listsTheFinishingOrderWithTheTitleEarned', () => {
    const r = rows(mountSummary());
    expect(r.map(row => cell(row, 'label'))).toEqual(['1. P1', '2. P0', '3. P2']);
    expect(r.map(row => cell(row, 'title'))).toEqual(['President', 'Neutral', 'Scum']);
  });

  it('showsTheRoundPointsAndTheTotals', () => {
    const r = rows(mountSummary());
    expect(r.map(row => cell(row, 'points'))).toEqual(['+3', '+2', '+1']);
    expect(r.map(row => cell(row, 'total'))).toEqual(['4', '7', '2']);
  });

  it.each([
    [0, 'up', 'Up from Scum'],
    [1, 'down', 'Down from President'],
    [2, 'down', 'Down from Neutral'],
  ])('showsTheChangeOfTitleOfRow%i_as_%s', (index, direction, label) => {
    const change = rows(mountSummary())[index].get('[data-test="place-change"]');
    expect(change.attributes('data-change')).toBe(direction);
    expect(change.text()).toContain(label);
  });

  it('givenTheSameTitleAsBefore_thenItIsMarkedUnchanged', () => {
    const same = players.map(p => ({ ...p, title: lastRound.players.find(r => r.seat === p.seat)!.title }));
    const change = rows(mountSummary({ players: same }))[0].get('[data-test="place-change"]');
    expect(change.attributes('data-change')).toBe('same');
  });

  it('givenRoundOne_thenThereIsNoChangeToShow', () => {
    const noTitle = players.map(p => ({ ...p, title: null }));
    const r = rows(mountSummary({ players: noTitle }));
    expect(r.every(row => !row.find('[data-test="place-change"]').exists())).toBe(true);
    expect(r.map(row => cell(row, 'title'))).toEqual(['President', 'Neutral', 'Scum']);
  });

  it('marksWhoIsReady', () => {
    const r = rows(mountSummary());
    expect(r.map(row => row.find('[data-test="place-ready"]').exists())).toEqual([false, true, false]);
    expect(r[1].get('[data-test="place-ready"]').text()).toContain('Ready');
  });

  it('givenNoLastRound_thenItFallsBackToThePlacesOfThePlayers', () => {
    const r = rows(mountSummary({ lastRound: null }));
    expect(r.map(row => cell(row, 'label'))).toEqual(['1. P1', '2. P0', '3. P2']);
    expect(r[0].find('[data-test="place-points"]').exists()).toBe(false);
  });

  it('whenReadyIsPressed_thenItEmitsReady', async () => {
    const wrapper = mountSummary();
    await wrapper.get('[data-test="ready"]').trigger('click');
    expect(wrapper.emitted('ready')).toHaveLength(1);
  });

  it('givenIAlreadyPressedReady_thenTheButtonGivesWayToAWaitingLine', () => {
    const wrapper = mountSummary({ canReady: false });
    expect(wrapper.find('[data-test="ready"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="round-over-waiting"]').text()).toBe('Waiting for the others');
  });
});
