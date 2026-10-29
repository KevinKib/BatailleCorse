import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
import BlackjackActionBar from './BlackjackActionBar.vue';
import BlackjackChips from './BlackjackChips.vue';
import BlackjackDealer from './BlackjackDealer.vue';
import BlackjackHand from './BlackjackHand.vue';
import BlackjackMySeat from './BlackjackMySeat.vue';
import BlackjackSeatPlate from './BlackjackSeatPlate.vue';
import BlackjackTable from './BlackjackTable.vue';
import { blackjackPlayingState, card } from '../../model/blackjack/fixtures';

const global = { plugins: [PrimeVue] };
const nameOf = (seat: number) => ['Alice', 'Bob', 'Bot 1', 'Bot 2'][seat] ?? `Player ${seat + 1}`;
const [alice, bob, bot1] = blackjackPlayingState.players;

describe('BlackjackChips', () => {
  it('writes the amount and names the pile', () => {
    const w = mount(BlackjackChips, { props: { amount: 25 }, global });
    expect(w.get('[data-test="bet-chips-amount"]').text()).toBe('25');
    expect(w.get('[data-test="bet-chips"]').attributes('aria-label')).toBe('Bet of 25 points');
  });

  it('a sealed bet shows a question mark, never the amount', () => {
    const w = mount(BlackjackChips, { props: { amount: null, sealed: true }, global });
    expect(w.get('[data-test="bet-chips-amount"]').text()).toBe('?');
    expect(w.get('[data-test="bet-chips"]').attributes('aria-label')).toBe('Bet placed, amount hidden');
    expect(w.get('[data-test="bet-chips"]').attributes('data-sealed')).toBe('true');
  });

  it('grows with the amount', () => {
    const discs = (amount: number) => mount(BlackjackChips, { props: { amount }, global }).findAll('.bj-disc').length;
    expect(discs(3)).toBeLessThan(discs(30));
    expect(discs(30)).toBeLessThan(discs(80));
  });

  it('carries the payout of the hand', () => {
    const w = mount(BlackjackChips, { props: { amount: 10, result: 'win' }, global });
    expect(w.get('[data-test="bet-chips"]').attributes('data-result')).toBe('win');
    expect(w.classes()).toContain('bj-chips--win');
  });
});

describe('BlackjackHand while cards are on their way', () => {
  const hand = { ...bob.hands[0], cards: [card('TEN', 'HEART')], pending: true };

  it('shows the cards that landed and no total yet', () => {
    const w = mount(BlackjackHand, { props: { hand, cardSize: 34, anchor: 's1h0' }, global });
    expect(w.findAll('.playing_card')).toHaveLength(1);
    expect(w.find('[data-test="hand-total"]').exists()).toBe(false);
    expect(w.find('[data-test="hand-total-wait"]').exists()).toBe(true);
  });

  it('keeps the room of a card for an empty hand, and names its place', () => {
    const w = mount(BlackjackHand, { props: { hand: { ...hand, cards: [] }, cardSize: 34, anchor: 's1h1' }, global });
    expect(w.get('[data-anchor="s1h1"]').exists()).toBe(true);
    const row = w.get('[data-test="hand-cards"]');
    expect(row.attributes('data-card-size')).toBe('34');
    expect(row.attributes('style')).toContain('--bj-card-w: 34px');
  });

  it('holds back the tags until the cards have landed', () => {
    const busted = { ...bob.hands[0], status: 'BUST' as const, pending: true };
    expect(mount(BlackjackHand, { props: { hand: busted }, global }).find('[data-test="tag-bust"]').exists()).toBe(false);
    expect(mount(BlackjackHand, { props: { hand: { ...busted, pending: false } }, global }).find('[data-test="tag-bust"]').exists()).toBe(true);
  });
});

describe('BlackjackDealer, the shoe and the status line', () => {
  it('has a shoe the cards leave from, the same size as the dealer cards', () => {
    const w = mount(BlackjackDealer, { props: { dealer: blackjackPlayingState.dealer }, global });
    expect(w.get('[data-anchor="shoe"]').exists()).toBe(true);
    expect(w.get('.bj-shoe-card--front').attributes('width')).toBe('56');
    expect(w.get('[data-test="dealer-cards"]').attributes('data-card-size')).toBe('56');
  });

  it('shows the hole card face down from the deal, as the second card', () => {
    const w = mount(BlackjackDealer, { props: { dealer: blackjackPlayingState.dealer }, global });
    const imgs = w.findAll('[data-test="dealer-cards"] .playing_card');
    expect(imgs).toHaveLength(2);
    expect(imgs[1].attributes('data-test')).toBe('hole-card');
  });

  it('says what the dealer does, with the total', () => {
    const line = { kind: 'stands' as const, total: 18 };
    const w = mount(BlackjackDealer, { props: { dealer: blackjackPlayingState.dealer, line }, global });
    expect(w.get('[data-test="dealer-status"]').text()).toBe('The dealer stands on 18');
    const draws = mount(BlackjackDealer, { props: { dealer: blackjackPlayingState.dealer, line: { kind: 'draws', total: 0 } }, global });
    expect(draws.get('[data-test="dealer-status"]').text()).toBe('The dealer draws a card');
  });

  it('does not tell a total while the cards are still being dealt', () => {
    const dealer = { ...blackjackPlayingState.dealer, cards: [], holeHidden: false, total: 0, pending: true };
    const w = mount(BlackjackDealer, { props: { dealer }, global });
    expect(w.find('[data-test="dealer-total"]').exists()).toBe(false);
  });
});

describe('BlackjackSeatPlate: what the bots do and bet', () => {
  const plate = (props: Record<string, unknown>) =>
    mount(BlackjackSeatPlate, { props: { player: bot1, label: 'Bot 1', activeHandIndex: 0, ...props }, global });

  it('pops a short tag over the seat for its action', () => {
    const w = plate({ tag: { kind: 'hit', key: 1 } });
    expect(w.get('[data-test="seat-tag"]').text()).toBe('Hit');
    expect(w.get('[data-test="seat-tag"]').attributes('data-kind')).toBe('hit');
  });

  it('keeps the last action on the plate once the tag is gone, unless it is the turn of the seat', () => {
    expect(plate({ lastAction: 'stand' }).get('[data-test="seat-last"]').text()).toBe('Stands');
    expect(plate({ lastAction: 'stand', current: true }).find('[data-test="seat-last"]').exists()).toBe(false);
  });

  it('shows the bet as chips: the amount once told, a sealed pile before', () => {
    const open = plate({});
    expect(open.get('[data-test="bet-chips-amount"]').text()).toBe('10');
    expect(open.get('[data-test="seat-bet"]').text()).toBe('Bet 10');
    const sealed = plate({ player: { ...bot1, bet: null } });
    expect(sealed.get('[data-test="bet-chips-amount"]').text()).toBe('?');
    expect(sealed.get('[data-test="seat-bet"]').text()).toBe('Bet placed');
  });

  it('plays the payout on the chips once the hands are settled', () => {
    const settled = { ...bot1, hands: bot1.hands.map(h => ({ ...h, net: 10 })) };
    expect(plate({ player: settled }).get('[data-test="bet-chips"]').attributes('data-result')).toBe('win');
    const lost = { ...bot1, hands: bot1.hands.map(h => ({ ...h, net: -10 })) };
    expect(plate({ player: lost }).get('[data-test="bet-chips"]').attributes('data-result')).toBe('lose');
  });

  it('has no chips for a seat that has not bet', () => {
    expect(plate({ player: { ...bot1, bet: null, betPlaced: false } }).find('[data-test="bet-chips"]').exists()).toBe(false);
  });
});

describe('BlackjackMySeat: my turn', () => {
  const mine = (props: Record<string, unknown> = {}) =>
    mount(BlackjackMySeat, { props: { player: alice, activeHandIndex: 0, ...props }, global });

  it('keeps the line of the turn on screen at all times, lit only on my turn', () => {
    const idle = mine();
    expect(idle.get('[data-test="my-turn"]').classes()).not.toContain('bj-turn--on');
    expect(idle.get('[data-test="my-turn"]').text()).toBe('');
    const on = mine({ current: true });
    expect(on.get('[data-test="my-turn"]').classes()).toContain('bj-turn--on');
    expect(on.get('[data-test="my-turn"]').text()).toBe('Your turn');
    expect(on.classes()).toContain('bj-mine--current');
  });

  it('pops my own action with its "You" wording', () => {
    expect(mine({ tag: { kind: 'bust', key: 3 } }).get('[data-test="my-tag"]').text()).toBe('You bust');
    expect(mine({ tag: { kind: 'stand', key: 4 } }).get('[data-test="my-tag"]').text()).toBe('You stand');
  });

  it('shows my bet as chips', () => {
    expect(mine().get('[data-test="bet-chips-amount"]').text()).toBe('20');
  });
});

describe('BlackjackActionBar, idle', () => {
  it('keeps the four buttons on screen, dimmed and disabled, while it is not my turn', () => {
    const w = mount(BlackjackActionBar, { props: { actions: [], idle: true }, global });
    expect(w.find('[data-test="action-bar"]').exists()).toBe(false);
    const buttons = w.findAll('[data-test="action-bar-idle"] button');
    expect(buttons).toHaveLength(4);
    expect(buttons.every(b => b.attributes('disabled') !== undefined)).toBe(true);
    expect(w.get('[data-test="action-bar-idle"]').attributes('aria-hidden')).toBe('true');
  });
});

describe('BlackjackTable: the felt and the banner', () => {
  const props = {
    game: blackjackPlayingState, opponents: [bob], me: alice, mySeat: 0, nameOf, disconnected: () => false, secondsRemaining: () => null,
  };

  it('has a felt and announces the beat of the hand', () => {
    const w = mount(BlackjackTable, { props: { ...props, banner: { key: 1, text: 'Dealer’s turn' } }, global });
    expect(w.find('.bj-felt').exists()).toBe(true);
    expect(w.get('[data-test="phase-banner"]').text()).toBe('Dealer’s turn');
    expect(mount(BlackjackTable, { props, global }).find('[data-test="phase-banner"]').exists()).toBe(false);
  });

  it('hands the tags and the last actions to the plates, and the dealer line to the dealer', () => {
    const w = mount(BlackjackTable, {
      props: { ...props, tags: { 1: { kind: 'double', key: 2 } }, lastAction: { 1: 'double' }, dealerLine: { kind: 'draws', total: 0 } }, global,
    });
    expect(w.get('[data-test="seat-tag"]').text()).toBe('Double');
    expect(w.get('[data-test="dealer-status"]').text()).toBe('The dealer draws a card');
  });
});
