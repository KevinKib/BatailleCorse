import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
import BlackjackActionBar from './BlackjackActionBar.vue';
import BlackjackBetPanel from './BlackjackBetPanel.vue';
import BlackjackDealer from './BlackjackDealer.vue';
import BlackjackHand from './BlackjackHand.vue';
import BlackjackHandSummary from './BlackjackHandSummary.vue';
import BlackjackSeatPlate from './BlackjackSeatPlate.vue';
import BlackjackStandings from './BlackjackStandings.vue';
import BlackjackTable from './BlackjackTable.vue';
import {
  blackjackBettingState, blackjackPlayingState, blackjackSettledState, blackjackSoloEndState, blackjackTournamentEndState,
  card,
} from '../../model/blackjack/fixtures';

const global = { plugins: [PrimeVue] };
const nameOf = (seat: number) => ['Alice', 'Bob', 'Bot 1', 'Bot 2'][seat] ?? `Player ${seat + 1}`;

describe('BlackjackDealer', () => {
  it('keeps the hole card face down while holeHidden, with the total of the shown cards', () => {
    const w = mount(BlackjackDealer, { props: { dealer: blackjackPlayingState.dealer }, global });
    expect(w.find('[data-test="hole-card"]').exists()).toBe(true);
    expect(w.get('[data-test="dealer-total"]').text()).toBe('Dealer 6');
  });

  it('shows no hole card once it is shown, and the tags', () => {
    const dealer = { upCard: card('ACE'), cards: [card('ACE'), card('KING')], holeHidden: false, total: 21, soft: true, blackjack: true, bust: false };
    const w = mount(BlackjackDealer, { props: { dealer }, global });
    expect(w.find('[data-test="hole-card"]').exists()).toBe(false);
    expect(w.get('[data-test="dealer-blackjack"]').exists()).toBe(true);
  });
});

describe('BlackjackHand', () => {
  it('writes a soft total as low / high', () => {
    const hand = { cards: [card('ACE'), card('SIX')], total: 17, soft: true, status: 'ACTIVE' as const, doubled: false, fromSplit: false };
    const w = mount(BlackjackHand, { props: { hand }, global });
    expect(w.get('[data-test="hand-total"]').text()).toBe('7 / 17');
  });

  it('tags a split double hand and writes its net', () => {
    const hand = { cards: [card('FIVE'), card('SIX')], total: 11, soft: false, status: 'STOOD' as const, doubled: true, fromSplit: true, net: 20 };
    const w = mount(BlackjackHand, { props: { hand }, global });
    expect(w.find('[data-test="tag-double"]').exists()).toBe(true);
    expect(w.find('[data-test="tag-split"]').exists()).toBe(true);
    expect(w.get('[data-test="hand-net"]').text()).toContain('+20');
  });
});

describe('BlackjackSeatPlate', () => {
  it('shows "Bet placed" and never an amount during sealed bets', () => {
    const bob = blackjackBettingState.players[1];
    const w = mount(BlackjackSeatPlate, { props: { player: bob, label: 'Bob' }, global });
    expect(w.get('[data-test="seat-bet"]').text()).toBe('Bet placed');
  });

  it('shows the amount after the deal, the level of a bot and the turn highlight', () => {
    const bot = blackjackPlayingState.players[2];
    const w = mount(BlackjackSeatPlate, { props: { player: bot, label: 'Bot 1', current: true }, global });
    expect(w.get('[data-test="seat-bet"]').text()).toBe('Bet 10');
    expect(w.get('[data-test="seat-level"]').text()).toBe('Medium');
    expect(w.get('[data-test="seat-plate"]').attributes('data-current')).toBe('true');
  });

  it('greys an eliminated seat with its place', () => {
    const out = blackjackTournamentEndState.players[3];
    const w = mount(BlackjackSeatPlate, { props: { player: out, label: 'Bot' }, global });
    expect(w.get('[data-test="seat-out"]').text()).toContain('4th');
  });
});

describe('BlackjackBetPanel', () => {
  const limits = { min: 1, max: 50 };

  it('prefills "Bet N" with the last bet and sends it in one tap', async () => {
    const w = mount(BlackjackBetPanel, { props: { limits, lastBet: 20 }, global });
    expect(w.get('[data-test="bet-place"]').text()).toBe('Bet 20');
    await w.get('[data-test="bet-place"]').trigger('click');
    expect(w.emitted('bet')).toEqual([[20]]);
  });

  it('defaults to 10 on the first hand, adds chips, Max and Clear', async () => {
    const w = mount(BlackjackBetPanel, { props: { limits, lastBet: null }, global });
    expect(w.get('[data-test="bet-place"]').text()).toBe('Bet 10');
    await w.get('[data-test="chip-5"]').trigger('click');
    expect(w.get('[data-test="bet-place"]').text()).toBe('Bet 15');
    await w.get('[data-test="chip-max"]').trigger('click');
    expect(w.get('[data-test="bet-place"]').text()).toBe('Bet 50');
    await w.get('[data-test="chip-clear"]').trigger('click');
    expect(w.get('[data-test="bet-place"]').attributes('disabled')).toBeDefined();
  });

  it('clamps to a short stack', () => {
    const w = mount(BlackjackBetPanel, { props: { limits: { min: 1, max: 7 }, lastBet: 20 }, global });
    expect(w.get('[data-test="bet-place"]').text()).toBe('Bet 7');
  });

  it('only waits once my bet is in', () => {
    const w = mount(BlackjackBetPanel, { props: { limits: null, lastBet: null, placed: true, placedAmount: 10 }, global });
    expect(w.find('[data-test="bet-place"]').exists()).toBe(false);
    expect(w.get('[data-test="waiting-bets"]').exists()).toBe(true);
  });
});

describe('BlackjackActionBar', () => {
  it('has only the available actions as buttons and emits them', async () => {
    const w = mount(BlackjackActionBar, { props: { actions: ['HIT', 'STAND', 'DOUBLE'] }, global });
    expect(w.findAll('button')).toHaveLength(3);
    expect(w.find('[data-test="action-split"]').exists()).toBe(false);
    await w.get('[data-test="action-double"]').trigger('click');
    expect(w.emitted('double')).toHaveLength(1);
  });
});

describe('BlackjackHandSummary', () => {
  it('reads the dealer total, my net and the best hand', () => {
    const w = mount(BlackjackHandSummary, { props: { lastHand: blackjackSettledState.lastHand!, mySeat: 0, nameOf }, global });
    expect(w.get('[data-test="summary-you"]').text()).toMatch(/^You /);
    expect(w.get('[data-test="summary-best"]').text()).toContain('+15');
  });
});

describe('BlackjackStandings', () => {
  it('lists shared places and the eliminated', () => {
    const w = mount(BlackjackStandings, { props: { players: blackjackTournamentEndState.players, nameOf, mySeat: 0, solo: false }, global });
    const places = w.findAll('[data-test="place"]').map(p => p.text());
    expect(places).toEqual(['1st', '1st', '3rd', '4th']);
    expect(w.findAll('[data-test="place-note"]')).toHaveLength(1);
  });

  it('shows the net in solo', () => {
    const w = mount(BlackjackStandings, { props: { players: blackjackSoloEndState.players, nameOf, mySeat: 0, solo: true, net: 23 }, global });
    expect(w.get('[data-test="solo-final"]').text()).toBe('You finish with 123 points (+23)');
  });
});

describe('BlackjackTable', () => {
  it('shows the dealer, the opponents and my split hands side by side with the active one marked', () => {
    const g = blackjackPlayingState;
    const w = mount(BlackjackTable, {
      props: { game: g, opponents: g.players.slice(1), me: g.players[0], mySeat: 0, nameOf, disconnected: () => false, secondsRemaining: () => null },
      global,
    });
    expect(w.find('[data-test="dealer"]').exists()).toBe(true);
    expect(w.findAll('[data-test="seat-plate"]')).toHaveLength(3);
    const mine = w.get('[data-test="my-seat"]');
    expect(mine.findAll('[data-test="hand"]')).toHaveLength(2);
    expect(mine.findAll('[data-active="true"]')).toHaveLength(1);
  });

  it('has no plates in solo', () => {
    const g = blackjackSoloEndState;
    const w = mount(BlackjackTable, {
      props: { game: g, opponents: [], me: g.players[0], mySeat: 0, nameOf, disconnected: () => false, secondsRemaining: () => null },
      global,
    });
    expect(w.find('[data-test="opponents"]').exists()).toBe(false);
  });
});
