import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import MorrisPlayerTag from './MorrisPlayerTag.vue';

function tag(props: Partial<InstanceType<typeof MorrisPlayerTag>['$props']> = {}) {
  return mount(MorrisPlayerTag, {
    props: { name: 'Alice', colour: 'white', piecesToPlace: 3, active: false, ...props },
  });
}

describe('MorrisPlayerTag', () => {
  it('shows the name and the colour in words next to a colour chip', () => {
    const w = tag();
    expect(w.get('[data-test="tag-name"]').text()).toBe('Alice');
    expect(w.get('[data-test="tag-colour"]').text()).toBe('White');
    expect(w.get('[data-test="tag-chip"]').attributes('data-colour')).toBe('white');
    expect(tag({ colour: 'black', name: 'Bob' }).get('[data-test="tag-colour"]').text()).toBe('Black');
  });

  it('renders the name as text only, never as markup', () => {
    const w = tag({ name: '<img src=x onerror=alert(1)>' });
    expect(w.find('img').exists()).toBe(false);
    expect(w.get('[data-test="tag-name"]').text()).toBe('<img src=x onerror=alert(1)>');
  });

  it('draws one disc per piece left to place, with an accessible count', () => {
    const w = tag({ piecesToPlace: 2 });
    expect(w.findAll('[data-test="tag-reserve"] [data-test="reserve-piece"]')).toHaveLength(2);
    expect(w.get('[data-test="tag-reserve"]').attributes('aria-label')).toBe('2 pieces left to place');
    expect(tag({ piecesToPlace: 1 }).get('[data-test="tag-reserve"]').attributes('aria-label')).toBe('1 piece left to place');
  });

  it('keeps the reserve in the page, empty and named, once everything is placed', () => {
    const w = tag({ piecesToPlace: 0 });
    expect(w.findAll('[data-test="reserve-piece"]')).toHaveLength(0);
    expect(w.get('[data-test="tag-reserve"]').attributes('aria-label')).toBe('0 pieces left to place');
  });

  it('marks the player whose turn it is', () => {
    expect(tag().attributes('data-active')).toBe('false');
    expect(tag({ active: true }).attributes('data-active')).toBe('true');
  });

  it('carries no mark on the chip by default, as on Tapatan and Achi', () => {
    expect(tag().find('[data-test="tag-mark"]').exists()).toBe(false);
    expect(tag({ colour: 'black' }).find('[data-test="tag-mark"]').exists()).toBe(false);
  });

  it('marks the chip like the pieces when asked: a ring for White, a small diamond for Black', () => {
    expect(tag({ marked: true }).get('[data-test="tag-chip"] [data-test="tag-mark"]').attributes('data-mark')).toBe('ring');
    expect(tag({ marked: true, colour: 'black' }).get('[data-test="tag-chip"] [data-test="tag-mark"]').attributes('data-mark')).toBe('diamond');
  });

  it('is named for screen readers by name and colour', () => {
    expect(tag().attributes('aria-label')).toBe('Alice, White');
  });

  it('shows no counts on Tapatan and Achi', () => {
    const w = tag();
    expect(w.find('[data-test="tag-on-board"]').exists()).toBe(false);
    expect(w.find('[data-test="tag-lost"]').exists()).toBe(false);
    expect(w.find('[data-test="tag-counts"]').exists()).toBe(false);
  });

  describe('with counts (Six Men\'s Morris)', () => {
    const counts = (props: Record<string, unknown> = {}) => tag({ onBoard: 5, lost: 1, piecesToPlace: 0, ...props });

    it('shows the pieces on the board and the pieces lost, next to the reserve discs', () => {
      const w = counts({ piecesToPlace: 2 });
      expect(w.get('[data-test="tag-on-board"]').text()).toBe('5 on board');
      expect(w.get('[data-test="tag-lost"]').text()).toBe('1 lost');
      expect(w.findAll('[data-test="reserve-piece"]')).toHaveLength(2);
      expect(w.get('[data-test="tag-on-board"] b').text()).toBe('5');
    });

    it('shows zero lost at the start, not nothing', () => {
      expect(counts({ onBoard: 0, lost: 0, piecesToPlace: 6 }).get('[data-test="tag-lost"]').text()).toBe('0 lost');
    });

    it('has no empty reserve once every piece is down', () => {
      const w = counts();
      expect(w.find('[data-test="tag-reserve"]').exists()).toBe(false);
    });

    it('is named with every count', () => {
      expect(counts({ piecesToPlace: 2, onBoard: 3, lost: 1 }).attributes('aria-label'))
        .toBe('Alice, White: 3 on board, 2 in hand, 1 lost');
    });

    it('flags the count at three pieces left, and only then', () => {
      expect(counts().get('[data-test="tag-on-board"]').attributes('data-warn')).toBeUndefined();
      expect(counts({ warn: true }).get('[data-test="tag-on-board"]').attributes('data-warn')).toBe('true');
    });

    it('keeps the colour mark on the chip when asked, with the counts', () => {
      expect(counts({ marked: true }).get('[data-test="tag-mark"]').attributes('data-mark')).toBe('ring');
    });
  });
});
