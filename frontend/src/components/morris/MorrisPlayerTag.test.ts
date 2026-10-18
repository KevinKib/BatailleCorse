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
});
