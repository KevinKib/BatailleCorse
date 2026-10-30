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

describe('BlackjackDealer and the shoe', () => {
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

  it('shows the label with the total above the cards, and tells nothing else (the rail does)', () => {
    const w = mount(BlackjackDealer, { props: { dealer: blackjackPlayingState.dealer }, global });
    expect(w.find('[data-test="dealer-status"]').exists()).toBe(false);
    const html = w.html();
    expect(html.indexOf('data-test="dealer-total"')).toBeLessThan(html.indexOf('data-test="dealer-cards"'));
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

  it('tells my own action in the rail with its "You" wording', () => {
    const table = (kind: 'bust' | 'stand') => mount(BlackjackTable, {
      props: { game: blackjackPlayingState, opponents: [bob], me: alice, mySeat: 0, nameOf, disconnected: () => false, secondsRemaining: () => null,
        tags: { 0: { kind, key: 3 } } }, global });
    expect(table('bust').get('[data-test="my-tag"]').text()).toBe('You bust');
    expect(table('stand').get('[data-test="my-tag"]').text()).toBe('You stand');
  });

  it('holds the dock in a slot, under the cards', () => {
    const w = mount(BlackjackMySeat, { props: { player: alice, activeHandIndex: 0 }, slots: { dock: '<button data-test="x">Hit</button>' }, global });
    const html = w.html();
    expect(html.indexOf('data-test="hand-cards"')).toBeLessThan(html.indexOf('data-test="dock"'));
    expect(w.get('[data-test="dock"] [data-test="x"]').exists()).toBe(true);
    expect(mount(BlackjackMySeat, { props: { player: alice }, global }).find('[data-test="dock"]').exists()).toBe(false);
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

  it('tells an action of a seat, with its name, and the dealer line, in the rail', () => {
    const w = mount(BlackjackTable, { props: { ...props, tags: { 1: { kind: 'double', key: 2 } }, lastAction: { 1: 'double' } }, global });
    expect(w.get('[data-test="status-rail"] [data-test="seat-tag"]').text()).toBe('Bob · Double');
    expect(w.get('[data-test="seat-tag"]').attributes('data-kind')).toBe('double');
    expect(w.find('[data-test="dealer-status"]').exists()).toBe(false);
    const line = mount(BlackjackTable, { props: { ...props, dealerLine: { kind: 'draws', total: 0 } }, global });
    expect(line.get('[data-test="status-rail"] [data-test="dealer-status"]').text()).toBe('The dealer draws a card');
    const stands = mount(BlackjackTable, { props: { ...props, dealerLine: { kind: 'stands', total: 18 } }, global });
    expect(stands.get('[data-test="dealer-status"]').text()).toBe('The dealer stands on 18');
  });

  it('says one thing at a time in the rail: the banner first, then the latest action, then the dealer line', () => {
    const all = { ...props, banner: { key: 1, text: 'Results' }, tags: { 1: { kind: 'hit' as const, key: 1 } }, dealerLine: { kind: 'draws' as const, total: 0 } };
    const w = mount(BlackjackTable, { props: all, global });
    expect(w.get('[data-test="status-rail"]').text()).toBe('Results');
    const noBanner = mount(BlackjackTable, { props: { ...all, banner: null }, global });
    expect(noBanner.get('[data-test="status-rail"]').text()).toBe('Bob · Hit');
  });
});

// --- Layout contract ------------------------------------------------------------------------------------------------
// happy-dom has no layout engine, so the contract is checked on the structure of the DOM (status text lives in the one
// rail of the table, never inside a cards row) and on the style blocks (one spacing scale, nothing floats over cards).
const sfcs = {
  ...import.meta.glob('./*.vue', { query: '?raw', import: 'default', eager: true }),
  ...import.meta.glob('../../view/blackjack/BlackjackGameScreen.vue', { query: '?raw', import: 'default', eager: true }),
} as Record<string, string>;
const styleOf = (src: string) => (src.match(/<style[^>]*>([\s\S]*?)<\/style>/g) ?? []).join('\n');
const declarations = (css: string) =>
  [...css.matchAll(/(?:^|[;{\s])((?:padding|margin|gap|row-gap|column-gap)(?:-(?:top|right|bottom|left|inline|block))?)\s*:\s*([^;}]+)/g)]
    .map(m => ({ property: m[1], value: m[2].trim() }));

describe('Blackjack spacing: one scale', () => {
  it('every margin, padding and gap of the Blackjack components is written with the --bj-space-* tokens', () => {
    const offenders: string[] = [];
    for (const [file, src] of Object.entries(sfcs)) {
      for (const d of declarations(styleOf(src))) {
        const rest = d.value.replace(/var\(--bj-space-[1-5]\)/g, '').replace(/env\([^)]*\)/g, '');
        if (/\d*\.?\d+(px|rem|em|%)/.test(rest) || /var\(--space-/.test(rest)) offenders.push(`${file}: ${d.property}: ${d.value}`);
      }
    }
    expect(offenders).toEqual([]);
  });

  it('defines the scale once, on the 4/8 grid and in rem', () => {
    const app = import.meta.glob('../../App.vue', { query: '?raw', import: 'default', eager: true });
    const src = Object.values(app)[0] as string;
    expect(src).toMatch(/--bj-space-1:\s*0\.25rem/);
    expect(src).toMatch(/--bj-space-2:\s*0\.5rem/);
    expect(src).toMatch(/--bj-space-3:\s*1rem/);
    expect(src).toMatch(/--bj-space-4:\s*1\.5rem/);
    expect(src).toMatch(/--bj-space-5:\s*2rem/);
  });
});

describe('Blackjack status text never floats over the cards', () => {
  it('the banner is laid out in the rail, not positioned over the felt', () => {
    for (const [file, src] of Object.entries(sfcs)) {
      for (const m of styleOf(src).matchAll(/\.(bj-banner|bj-tag-pop)[^{,]*\{([^}]*)\}/g)) {
        expect(m[2], `${file} .${m[1]}`).not.toMatch(/position:\s*(absolute|fixed)/);
      }
    }
  });

  it('the table has ONE status rail, always there, apart from the dealer, the plates and my seat', () => {
    const props = { game: blackjackPlayingState, opponents: [bob], me: alice, mySeat: 0, nameOf, disconnected: () => false, secondsRemaining: () => null };
    const without = mount(BlackjackTable, { props, global });
    expect(without.findAll('[data-test="status-rail"]')).toHaveLength(1);
    expect(without.get('[data-test="status-rail"]').find('[data-test="phase-banner"]').exists()).toBe(false);
    const withBanner = mount(BlackjackTable, { props: { ...props, banner: { key: 1, text: 'Dealer’s turn' } }, global });
    const rail = withBanner.get('[data-test="status-rail"]');
    expect(rail.get('[data-test="phase-banner"]').text()).toBe('Dealer’s turn');
    for (const sel of ['[data-test="dealer"]', '[data-test="my-seat"]', '[data-test="opponents"]']) {
      expect(withBanner.get(sel).element.contains(rail.element), sel).toBe(false);
      expect(rail.element.contains(withBanner.get(sel).element), sel).toBe(false);
    }
    expect(rail.find('.playing_card').exists()).toBe(false);
    const html = withBanner.html();
    expect(html.indexOf('data-test="dealer"')).toBeLessThan(html.indexOf('data-test="status-rail"'));
    expect(html.indexOf('data-test="status-rail"')).toBeLessThan(html.indexOf('data-test="opponents"'));
  });

  it('the rail is a fixed 48 px (3rem) lane with gold borders at 25 %', () => {
    const table = Object.entries(sfcs).find(([file]) => file.endsWith('BlackjackTable.vue'))![1];
    const rule = table.match(/\.bj-rail\s*\{([^}]*)\}/)![1];
    expect(rule).toMatch(/height:\s*3rem/);
    expect(rule).toMatch(/rgba\(245, 200, 66, 0\.25\)/);
  });

  it('no plate and no seat carries status text of its own', () => {
    const plate = mount(BlackjackSeatPlate, { props: { player: bot1, label: 'Bot 1', activeHandIndex: 0, lastAction: 'stand' }, global });
    expect(plate.find('[data-test="seat-tag"]').exists()).toBe(false);
    const mine = mount(BlackjackMySeat, { props: { player: alice, activeHandIndex: 0, current: true }, global });
    expect(mine.find('[data-test="my-tag"]').exists()).toBe(false);
  });

  it('the dealer\'s total and tags are outside the row of cards', () => {
    const dealer = { ...blackjackPlayingState.dealer, blackjack: true };
    const w = mount(BlackjackDealer, { props: { dealer }, global });
    const cards = w.get('[data-test="dealer-cards"]').element;
    for (const sel of ['[data-test="dealer-total"]', '[data-test="dealer-blackjack"]']) {
      expect(cards.contains(w.get(sel).element), sel).toBe(false);
    }
  });
});

describe('Blackjack action buttons: standard states only', () => {
  it('the style of the action bar has no glow, shadow, pulse or filter', () => {
    const bar = Object.entries(sfcs).find(([file]) => file.endsWith('BlackjackActionBar.vue'))![1];
    expect(styleOf(bar)).not.toMatch(/box-shadow|text-shadow|drop-shadow|filter:|animation/);
  });

  it('the screen puts no glow on the bar of actions', () => {
    const screen = Object.entries(sfcs).find(([file]) => file.endsWith('BlackjackGameScreen.vue'))![1];
    expect(screen).not.toMatch(/turn-bar/);
    expect(styleOf(screen)).not.toMatch(/drop-shadow|text-shadow/);
  });

  it('keeps a clear disabled state when it is not my turn or the table is busy', () => {
    const busy = mount(BlackjackActionBar, { props: { actions: ['HIT', 'STAND'], busy: true }, global });
    expect(busy.findAll('button').every(b => b.attributes('disabled') !== undefined)).toBe(true);
    const ready = mount(BlackjackActionBar, { props: { actions: ['HIT', 'STAND'] }, global });
    expect(ready.findAll('button').every(b => b.attributes('disabled') === undefined)).toBe(true);
  });
});
