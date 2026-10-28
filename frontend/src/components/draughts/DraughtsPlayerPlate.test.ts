import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import DraughtsPlayerPlate from './DraughtsPlayerPlate.vue';
import DraughtsCounterChips from './DraughtsCounterChips.vue';
import { messagesEn } from '../../locales/en';

const ui = messagesEn.draughtsUi;

const plate = (props: Record<string, unknown> = {}) => mount(DraughtsPlayerPlate, {
  props: { name: 'Bob', colour: 'black', active: false, men: 20, kings: 0, captured: 0, ...props },
});

describe('DraughtsPlayerPlate', () => {
  it('shows the name, the colour in words and the counts', () => {
    const w = plate({ men: 6, kings: 1, captured: 16 });
    expect(w.get('[data-test="plate-name"]').text()).toBe('Bob');
    expect(w.get('[data-test="plate-colour"]').text()).toBe('Black');
    expect(w.get('[data-test="plate-counts"]').text()).toBe('6 men · 1 king · took 16');
  });

  it('leaves out the kind of piece the player has none of', () => {
    expect(plate({ men: 0, kings: 1, captured: 17 }).get('[data-test="plate-counts"]').text()).toBe('1 king · took 17');
    expect(plate({ men: 20, kings: 0, captured: 0 }).get('[data-test="plate-counts"]').text()).toBe('20 men · took 0');
    expect(plate({ men: 0, kings: 3, captured: 19 }).get('[data-test="plate-counts"]').text()).toBe('3 kings · took 19');
  });

  it('says the same thing to a screen reader in one sentence', () => {
    const w = plate({ name: 'Alice', colour: 'white', men: 3, kings: 1, captured: 14 });
    expect(w.get('[data-test="plate"]').attributes('aria-label')).toBe('Alice, White: 3 men · 1 king · took 14');
  });

  it('wears the colour of its pieces', () => {
    expect(plate({ colour: 'white' }).get('[data-test="plate-chip"]').attributes('data-colour')).toBe('white');
    expect(plate({ colour: 'black' }).get('[data-test="plate-chip"]').attributes('data-colour')).toBe('black');
  });

  it('lights up while it is the player’s turn', () => {
    expect(plate({ active: true }).get('[data-test="plate"]').attributes('data-active')).toBe('true');
    expect(plate({ active: false }).get('[data-test="plate"]').attributes('data-active')).toBe('false');
  });

  it('keeps a long name on one line (text only)', () => {
    const w = plate({ name: '<b>x</b>'.repeat(10) });
    expect(w.find('b').exists()).toBe(false);
    expect(w.get('[data-test="plate-name"]').text()).toContain('<b>x</b>');
  });
});

describe('DraughtsCounterChips', () => {
  const chips = (props: Record<string, unknown> = {}) => mount(DraughtsCounterChips, { props: { repeatedTwice: false, ...props } });

  it('shows nothing while no count matters', () => {
    const w = chips();
    expect(w.find('[data-test="kings-only"]').exists()).toBe(false);
    expect(w.find('[data-test="short-ending"]').exists()).toBe(false);
    expect(w.find('[data-test="repetition-hint"]').exists()).toBe(false);
  });

  it('counts the moves each player has left before the kings-only draw, from the counters the server sends', () => {
    const w = chips({ counters: { kingOnlyPlies: 30, kingOnlyLimit: 50 } });
    expect(w.get('[data-test="kings-only"]').text()).toBe('Kings only: 10 moves left');
    expect(chips({ counters: { kingOnlyPlies: 49, kingOnlyLimit: 50 } }).get('[data-test="kings-only"]').text())
      .toBe('Kings only: 1 move left');
    expect(chips({ counters: { kingOnlyPlies: 41, kingOnlyLimit: 50 } }).get('[data-test="kings-only"]').text())
      .toBe('Kings only: 5 moves left');
  });

  it('counts the moves left in a short ending and says which one', () => {
    const w = chips({ counters: { endgame: { case: 'FIVE', plies: 4, limit: 10 } } });
    expect(w.get('[data-test="short-ending"]').text()).toBe('Short ending: draw in 3 moves');
    expect(w.get('[data-test="short-ending"]').attributes('data-case')).toBe('FIVE');
    const sixteen = chips({ counters: { endgame: { case: 'SIXTEEN', plies: 17, limit: 32 } } });
    expect(sixteen.get('[data-test="short-ending"]').text()).toBe('Short ending: draw in 8 moves');
  });

  it('shows both counts together', () => {
    const w = chips({ counters: { kingOnlyPlies: 32, kingOnlyLimit: 50, endgame: { case: 'SIXTEEN', plies: 20, limit: 32 } } });
    expect(w.find('[data-test="kings-only"]').exists()).toBe(true);
    expect(w.find('[data-test="short-ending"]').exists()).toBe(true);
  });

  it('warns that a position seen twice is one repetition from a draw', () => {
    const w = chips({ repeatedTwice: true });
    expect(w.get('[data-test="repetition-hint"]').text()).toBe(ui.table.repetition);
  });
});
