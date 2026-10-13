import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import MorrisBoard from './MorrisBoard.vue';
import { messagesEn } from '../../locales/en';

type Cell = 'white' | 'black' | null;
const ui = messagesEn.morrisBoard;
const none = (): boolean[] => Array(9).fill(false);
const empty = (): Cell[] => Array(9).fill(null);
const flags = (...on: number[]): boolean[] => none().map((_, i) => on.includes(i));

function board(props: Record<string, unknown> = {}) {
  return mount(MorrisBoard, { props: { cells: empty(), ...props } });
}
const point = (w: ReturnType<typeof board>, n: number) => w.get(`[data-test="point-${n}"]`);

describe('MorrisBoard rendering', () => {
  it('renders nine real buttons named after their position', () => {
    const w = board();
    expect(w.findAll('button')).toHaveLength(9);
    for (let n = 0; n < 9; n++) {
      expect(point(w, n).element.tagName).toBe('BUTTON');
      expect(point(w, n).attributes('type')).toBe('button');
      expect(point(w, n).attributes('aria-label')).toContain(ui.pointNames[n]);
    }
    expect(ui.pointNames[4]).toBe('Centre');
  });

  it('marks the owner of each point and names it', () => {
    const cells = empty();
    cells[0] = 'white';
    cells[8] = 'black';
    const w = board({ cells });
    expect(point(w, 0).attributes('data-owner')).toBe('white');
    expect(point(w, 8).attributes('data-owner')).toBe('black');
    expect(point(w, 4).attributes('data-owner')).toBe('none');
    expect(point(w, 0).attributes('aria-label')).toContain(ui.white);
    expect(point(w, 8).attributes('aria-label')).toContain(ui.black);
    expect(point(w, 4).attributes('aria-label')).toContain(ui.free);
    expect(w.findAll('[data-test="piece"]')).toHaveLength(2);
  });

  it('adds "can move" to a selectable piece and "can place" to a selectable free point', () => {
    const cells = empty();
    cells[0] = 'white';
    const w = board({ cells, selectable: flags(0, 4) });
    expect(point(w, 0).attributes('aria-label')).toContain(ui.canMove);
    expect(point(w, 4).attributes('aria-label')).toContain(ui.canPlace);
    expect(point(w, 8).attributes('aria-label')).not.toContain(ui.canPlace);
  });

  it('shows the highlighted points only while the board is interactive', async () => {
    const w = board({ highlighted: flags(1, 2) });
    expect(point(w, 1).attributes('data-state')).toBe('highlighted');
    expect(point(w, 3).attributes('data-state')).toBe('idle');
    await w.setProps({ interactive: false });
    expect(point(w, 1).attributes('data-state')).toBe('idle');
  });

  it('shows the selected piece with aria-pressed and a selected state', () => {
    const cells = empty();
    cells[0] = 'white';
    const w = board({ cells, selected: 0, selectable: flags(0, 4) });
    expect(point(w, 0).attributes('aria-pressed')).toBe('true');
    expect(point(w, 0).attributes('data-state')).toBe('selected');
    expect(point(w, 0).attributes('aria-label')).toContain(ui.selected);
    expect(point(w, 4).attributes('aria-pressed')).toBe('false');
  });

  it('draws the eight lines, decoratively, and strokes the winning one', async () => {
    const w = board();
    const svg = w.get('svg');
    expect(svg.attributes('aria-hidden')).toBe('true');
    expect(svg.findAll('[data-line]')).toHaveLength(8);
    expect(w.findAll('[data-winning="true"]')).toHaveLength(0);
    await w.setProps({ winningLine: [2, 4, 6] });
    const won = w.findAll('line[data-winning="true"]');
    expect(won).toHaveLength(1);
    expect(won[0].attributes('data-line')).toBe('2-4-6');
    expect(point(w, 4).attributes('data-winning')).toBe('true');
    expect(point(w, 0).attributes('data-winning')).toBeUndefined();
  });

  it('exposes an announcement through a live region that is always in the page', async () => {
    const w = board();
    const region = w.get('[data-test="board-live"]');
    expect(region.attributes('role')).toBe('status');
    expect(region.text()).toBe('');
    await w.setProps({ announcement: 'Black placed on the centre' });
    expect(w.get('[data-test="board-live"]').element).toBe(region.element);
    expect(region.text()).toBe('Black placed on the centre');
  });

  it('labels the whole board as a group', () => {
    expect(board().get('[data-test="morris-board"]').attributes('aria-label')).toBe(ui.boardLabel);
  });
});

describe('MorrisBoard input', () => {
  it('emits select with the point on click of a selectable point', async () => {
    const w = board({ selectable: flags(4) });
    await point(w, 4).trigger('click');
    expect(w.emitted('select')).toEqual([[4]]);
  });

  it('emits clear, not select, on a click of a point that is not selectable', async () => {
    const w = board({ selectable: flags(4) });
    await point(w, 0).trigger('click');
    expect(w.emitted('select')).toBeUndefined();
    expect(w.emitted('clear')).toHaveLength(1);
  });

  it('emits select on Enter and Space, once each', async () => {
    const w = board({ selectable: flags(4) });
    await point(w, 4).trigger('keydown', { key: 'Enter' });
    await point(w, 4).trigger('keydown', { key: ' ' });
    expect(w.emitted('select')).toEqual([[4], [4]]);
  });

  it('emits clear on Escape', async () => {
    const w = board({ selectable: flags(4), selected: 4 });
    await point(w, 4).trigger('keydown', { key: 'Escape' });
    expect(w.emitted('clear')).toHaveLength(1);
  });

  it('is inert when disabled: aria-disabled, no event, still readable', async () => {
    const cells = empty();
    cells[0] = 'white';
    const w = board({ cells, selectable: flags(0, 4), disabled: true });
    expect(point(w, 4).attributes('aria-disabled')).toBe('true');
    await point(w, 4).trigger('click');
    await point(w, 4).trigger('keydown', { key: 'Enter' });
    await point(w, 4).trigger('keydown', { key: 'Escape' });
    expect(w.emitted('select')).toBeUndefined();
    expect(w.emitted('clear')).toBeUndefined();
    expect(point(w, 0).attributes('data-owner')).toBe('white');
    expect(point(w, 0).attributes('aria-label')).toContain(ui.white);
    expect(point(w, 0).attributes('aria-label')).not.toContain(ui.canMove);
  });

  it('is inert when not interactive, with the same rules as disabled', async () => {
    const w = board({ selectable: flags(4), interactive: false });
    expect(point(w, 4).attributes('aria-disabled')).toBe('true');
    await point(w, 4).trigger('click');
    expect(w.emitted('select')).toBeUndefined();
  });

  it('is interactive and enabled by default', () => {
    const w = board({ selectable: flags(4) });
    expect(point(w, 4).attributes('aria-disabled')).toBeUndefined();
  });
});

describe('MorrisBoard keyboard focus', () => {
  const tabindexes = (w: ReturnType<typeof board>) =>
    Array.from({ length: 9 }, (_, n) => point(w, n).attributes('tabindex'));

  it('has a single tab stop, on the first selectable point', () => {
    const w = board({ selectable: flags(3, 5) });
    expect(tabindexes(w).filter((t) => t === '0')).toHaveLength(1);
    expect(point(w, 3).attributes('tabindex')).toBe('0');
  });

  it('falls back to the selected point, then to the centre', () => {
    expect(point(board({ selected: 6 }), 6).attributes('tabindex')).toBe('0');
    expect(point(board(), 4).attributes('tabindex')).toBe('0');
  });

  it('moves focus with the arrows and keeps a single tab stop', async () => {
    const w = mount(MorrisBoard, { props: { cells: empty(), selectable: flags(4) }, attachTo: document.body });
    await point(w, 4).trigger('keydown', { key: 'ArrowRight' });
    expect(document.activeElement).toBe(point(w, 5).element);
    expect(point(w, 5).attributes('tabindex')).toBe('0');
    expect(point(w, 4).attributes('tabindex')).toBe('-1');
    await point(w, 5).trigger('keydown', { key: 'ArrowDown' });
    expect(document.activeElement).toBe(point(w, 8).element);
    await point(w, 8).trigger('keydown', { key: 'ArrowLeft' });
    await point(w, 7).trigger('keydown', { key: 'ArrowUp' });
    expect(document.activeElement).toBe(point(w, 4).element);
    w.unmount();
  });

  it('stops at the edges instead of wrapping', async () => {
    const w = mount(MorrisBoard, { props: { cells: empty() }, attachTo: document.body });
    await point(w, 4).trigger('keydown', { key: 'ArrowUp' });
    await point(w, 1).trigger('keydown', { key: 'ArrowUp' });
    expect(document.activeElement).toBe(point(w, 1).element);
    await point(w, 1).trigger('keydown', { key: 'ArrowRight' });
    await point(w, 2).trigger('keydown', { key: 'ArrowRight' });
    expect(document.activeElement).toBe(point(w, 2).element);
    w.unmount();
  });

  it('follows a focus that arrives by pointer or script', async () => {
    const w = mount(MorrisBoard, { props: { cells: empty() }, attachTo: document.body });
    await point(w, 7).trigger('focus');
    expect(point(w, 7).attributes('tabindex')).toBe('0');
    expect(point(w, 4).attributes('tabindex')).toBe('-1');
    w.unmount();
  });
});

describe('MorrisBoard pieces', () => {
  it('draws a piece when a point becomes occupied', async () => {
    const w = board();
    expect(w.findAll('[data-test="piece"]')).toHaveLength(0);
    const next = empty();
    next[4] = 'black';
    await w.setProps({ cells: next });
    expect(point(w, 4).find('[data-test="piece"]').exists()).toBe(true);
  });
});
