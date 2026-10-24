import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import MangolaPit from './MangolaPit.vue';
import { messagesEn } from '../../locales/en';

const ui = messagesEn.mangolaUi;

function pit(props: Record<string, unknown> = {}) {
  return mount(MangolaPit, { props: { name: 'b3', count: 5, arrow: 'right', ...props } });
}

describe('MangolaPit', () => {
  it('is a real button named after the pit and its seeds', () => {
    const w = pit();
    expect(w.element.tagName).toBe('BUTTON');
    expect(w.attributes('type')).toBe('button');
    expect(w.attributes('aria-label')).toBe('Pit b3, 5 seeds');
  });

  it('uses the singular for one seed and says empty pits hold 0 seeds', () => {
    expect(pit({ count: 1 }).attributes('aria-label')).toBe('Pit b3, 1 seed');
    expect(pit({ count: 0 }).attributes('aria-label')).toBe('Pit b3, 0 seeds');
  });

  it('draws exactly one seed per seed, for 1 to 64', () => {
    for (let n = 1; n <= 64; n++) {
      expect(pit({ count: n }).findAll('[data-test="seed"]')).toHaveLength(n);
    }
  });

  it('places each seed inside the bowl with a size in percent, so the pit never changes size', () => {
    const seed = pit({ count: 12 }).findAll('[data-test="seed"]')[0];
    const style = seed.attributes('style') ?? '';
    expect(style).toMatch(/left: [\d.]+%/);
    expect(style).toMatch(/top: [\d.]+%/);
    expect(style).toMatch(/width: [\d.]+%/);
    expect(seed.attributes('aria-hidden')).toBeUndefined(); // hidden by the container, not per seed
  });

  it('writes the exact count in a badge on every non-empty pit, with or without a pile', () => {
    for (const n of [1, 5, 8, 9, 12, 17, 30, 64]) {
      const badge = pit({ count: n }).get('[data-test="pit-count"]');
      expect(badge.text()).toBe(String(n));
      expect(badge.attributes('aria-hidden')).toBe('true');
    }
  });

  it('never writes the number in the middle of the bowl any more', () => {
    expect(pit({ count: 30 }).find('[data-test="pit-number"]').exists()).toBe(false);
  });

  it('keeps the exact count in the name of the pit, whatever the pile', () => {
    expect(pit({ count: 30 }).attributes('aria-label')).toBe('Pit b3, 30 seeds');
    expect(pit({ count: 12 }).attributes('aria-label')).toBe('Pit b3, 12 seeds');
  });

  it('an empty pit shows neither seeds nor a badge', () => {
    const w = pit({ count: 0 });
    expect(w.findAll('[data-test="seed"]')).toHaveLength(0);
    expect(w.find('[data-test="pit-count"]').exists()).toBe(false);
  });

  it('flashes the badge when the count changes, not when the pit first shows', async () => {
    const w = pit({ count: 5 });
    expect(w.get('[data-test="pit-count"]').attributes('data-changed')).toBeUndefined();
    await w.setProps({ count: 6 });
    expect(w.get('[data-test="pit-count"]').attributes('data-changed')).toBe('true');
    await w.setProps({ count: 6 });
    expect(w.get('[data-test="pit-count"]').attributes('data-changed')).toBe('true');
  });

  it('does not flash when a hidden pit is revealed', async () => {
    const w = pit({ count: null });
    await w.setProps({ count: 4 });
    expect(w.get('[data-test="pit-count"]').attributes('data-changed')).toBeUndefined();
  });
  it('a hidden pit says so and shows no seeds', () => {
    const w = pit({ count: null });
    expect(w.attributes('aria-label')).toBe('Pit b3, hidden');
    expect(w.findAll('[data-test="seed"]')).toHaveLength(0);
    expect(w.attributes('data-hidden')).toBe('true');
  });

  it('a selectable pit says it can sow; an inert one is aria-disabled and does not emit', async () => {
    const live = pit({ selectable: true });
    expect(live.attributes('aria-label')).toBe('Pit b3, 5 seeds, can sow');
    expect(live.attributes('aria-disabled')).toBeUndefined();
    await live.trigger('click');
    expect(live.emitted('press')).toHaveLength(1);

    const inert = pit({ selectable: false });
    expect(inert.attributes('aria-disabled')).toBe('true');
    await inert.trigger('click');
    expect(inert.emitted('press')).toBeUndefined();
  });

  it('draws the direction chevron for every pit', () => {
    for (const arrow of ['left', 'right', 'up', 'down']) {
      const w = pit({ arrow });
      expect(w.get('[data-test="chevron"]').attributes('data-arrow')).toBe(arrow);
      expect(w.get('[data-test="chevron"]').attributes('aria-hidden')).toBe('true');
    }
  });

  it('marks the start and end of the last move with text in the name', () => {
    const start = pit({ lastStart: true });
    expect(start.attributes('data-last-start')).toBe('true');
    expect(start.attributes('aria-label')).toContain(ui.pit.lastStart);
    const end = pit({ lastEnd: true });
    expect(end.attributes('data-last-end')).toBe('true');
    expect(end.attributes('aria-label')).toContain(ui.pit.lastEnd);
  });

  it('shows -n on a captured pit and names it', () => {
    const w = pit({ count: 0, captured: true, capturedSeeds: 3 });
    expect(w.attributes('data-captured')).toBe('true');
    expect(w.get('[data-test="captured-seeds"]').text()).toBe('-3');
    expect(w.attributes('aria-label')).toContain('captured, 3 seeds taken');
  });

  it('a captured pit of unknown size is still ringed and named', () => {
    const w = pit({ count: 0, captured: true, capturedSeeds: null });
    expect(w.find('[data-test="captured-seeds"]').exists()).toBe(false);
    expect(w.attributes('aria-label')).toContain(ui.pit.capturedUnknown);
  });

  it('appends an editor hint to the name', () => {
    expect(pit({ hint: 'add' }).attributes('aria-label')).toContain(ui.pit.tapToAdd);
    expect(pit({ hint: 'remove' }).attributes('aria-label')).toContain(ui.pit.tapToRemove);
  });

  it('tells my side from the opponent side', () => {
    expect(pit({ mine: true }).attributes('data-mine')).toBe('true');
    expect(pit({ mine: false }).attributes('data-mine')).toBe('false');
  });

  it('a pit the opponent has just chosen is flagged and says so in its name', () => {
    const w = pit({ chosen: true });
    expect(w.attributes('data-chosen')).toBe('true');
    expect(w.attributes('aria-label')).toContain(ui.pit.chosen);
    expect(pit().attributes('data-chosen')).toBeUndefined();
    expect(pit().attributes('aria-label')).not.toContain(ui.pit.chosen);
  });
});
