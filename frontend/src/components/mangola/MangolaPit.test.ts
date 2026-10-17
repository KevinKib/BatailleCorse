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

  it('shows exactly as many dots as seeds up to eight', () => {
    for (let n = 1; n <= 8; n++) {
      const w = pit({ count: n });
      expect(w.findAll('[data-test="seed"]')).toHaveLength(n);
      expect(w.find('[data-test="pit-number"]').exists()).toBe(false);
    }
  });

  it('keeps the count in the DOM for hover and focus when it shows dots', () => {
    const w = pit({ count: 3 });
    expect(w.get('[data-test="pit-count"]').text()).toBe('3');
  });

  it('writes the number above eight, with no dots', () => {
    const w = pit({ count: 9 });
    expect(w.findAll('[data-test="seed"]')).toHaveLength(0);
    expect(w.get('[data-test="pit-number"]').text()).toBe('9');
    expect(pit({ count: 32 }).get('[data-test="pit-number"]').text()).toBe('32');
  });

  it('an empty pit shows neither dots nor a number', () => {
    const w = pit({ count: 0 });
    expect(w.findAll('[data-test="seed"]')).toHaveLength(0);
    expect(w.find('[data-test="pit-number"]').exists()).toBe(false);
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
