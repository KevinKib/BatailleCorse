import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import MorrisBoard from './MorrisBoard.vue';
import { messagesEn } from '../../locales/en';
import { GEOMETRIES } from '../../model/threemenmorris/geometries';

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

describe('MorrisBoard geometry', () => {
  it('draws the eight lines of Tapatan by default, with no short diagonal', () => {
    const w = board();
    expect(w.findAll('[data-line]')).toHaveLength(8);
    expect(w.find('[data-line="1-3"]').exists()).toBe(false);
  });

  it('draws Achi like Tapatan', () => {
    expect(board({ geometry: GEOMETRIES.achi }).findAll('[data-line]')).toHaveLength(8);
  });

  it('places each point from its coordinates', () => {
    const w = board({ geometry: GEOMETRIES.achi });
    expect(point(w, 4).attributes('style')).toContain('left: 50%');
    expect(point(w, 4).attributes('style')).toContain('top: 50%');
    expect(point(w, 2).attributes('style')).toContain('left: 83.33');
    expect(point(w, 2).attributes('style')).toContain('top: 16.67');
  });

  it('marks the points of the last opponent move', async () => {
    const w = board({ lastMove: [1, 5] });
    expect(point(w, 1).attributes('data-last')).toBe('true');
    expect(point(w, 5).attributes('data-last')).toBe('true');
    expect(point(w, 0).attributes('data-last')).toBeUndefined();
    await w.setProps({ lastMove: [] });
    expect(point(w, 1).attributes('data-last')).toBeUndefined();
  });

  it('moves focus by the geometry, not by a grid, with the arrows', async () => {
    const w = mount(MorrisBoard, { props: { cells: empty(), geometry: GEOMETRIES.tapatan }, attachTo: document.body });
    await point(w, 4).trigger('keydown', { key: 'ArrowRight' });
    expect(document.activeElement).toBe(point(w, 5).element);
    w.unmount();
  });
});

describe('MorrisBoard has no Picaria features', () => {
  it('renders nine points with no crossing, no padlock and no closed point', () => {
    const w = board();
    expect(w.findAll('button')).toHaveLength(9);
    expect(ui.pointNames).toHaveLength(9);
    expect(w.find('[data-crossing]').exists()).toBe(false);
    expect(w.find('[data-locked]').exists()).toBe(false);
    expect(w.find('[data-test="lock"]').exists()).toBe(false);
    expect(Object.keys(ui)).not.toContain('closed');
  });

  it('is not the refined table on Tapatan or Achi: no plaque, no mark, no removal or mill decoration', () => {
    const cells = empty();
    cells[0] = 'white';
    cells[8] = 'black';
    for (const geometry of [GEOMETRIES.tapatan, GEOMETRIES.achi]) {
      const w = board({ cells, geometry, selected: 0 });
      expect(w.find('[data-test="plaque"]').exists()).toBe(false);
      expect(w.get('[data-test="morris-board"]').attributes('data-refined')).toBeUndefined();
      expect(w.find('[data-test="piece-mark"]').exists()).toBe(false);
      expect(w.find('[data-test="removal-ring"]').exists()).toBe(false);
      expect(w.find('[data-test="shield"]').exists()).toBe(false);
      expect(w.find('[data-test="captured-mark"]').exists()).toBe(false);
      expect(w.find('[data-mill="true"]').exists()).toBe(false);
    }
  });
});

// Six Men's Morris: 16 points on two squares, the refined table of design option A.
describe('MorrisBoard Six Men\'s Morris', () => {
  const six = (props: Record<string, unknown> = {}) =>
    mount(MorrisBoard, { props: { cells: Array(16).fill(null), geometry: GEOMETRIES.six, ...props } });
  const sixPoint = (w: ReturnType<typeof six>, n: number) => w.get(`[data-test="point-${n}"]`);
  const sixFlags = (...on: number[]): boolean[] => Array.from({ length: 16 }, (_, i) => on.includes(i));
  const sixCells = (white: number[], black: number[]): Cell[] =>
    Array.from({ length: 16 }, (_, i) => (white.includes(i) ? 'white' : black.includes(i) ? 'black' : null));

  it('renders 16 real buttons named after their position on the two squares', () => {
    const w = six();
    expect(w.findAll('button')).toHaveLength(16);
    expect(sixPoint(w, 0).attributes('aria-label')).toContain('Outer top left');
    expect(sixPoint(w, 9).attributes('aria-label')).toContain('Inner top');
    expect(sixPoint(w, 15).attributes('aria-label')).toContain('Inner left');
    expect(ui.sixPointNames).toHaveLength(16);
    expect(w.get('[data-test="morris-board"]').attributes('data-variant')).toBe('six');
  });

  it('draws the 20 segments, a plaque under them, and marks White with a ring and Black with a diamond', () => {
    const w = six({ cells: sixCells([0], [8]) });
    expect(w.findAll('[data-line]')).toHaveLength(20);
    expect(w.get('[data-test="morris-board"]').attributes('data-refined')).toBe('true');
    expect(w.find('[data-test="plaque"]').exists()).toBe(true);
    expect(sixPoint(w, 0).get('[data-test="piece-mark"]').attributes('data-mark')).toBe('ring');
    expect(sixPoint(w, 8).get('[data-test="piece-mark"]').attributes('data-mark')).toBe('diamond');
  });

  it('places the points from the geometry (the inner twin of a corner at 29)', () => {
    const w = six();
    expect(sixPoint(w, 8).attributes('style')).toContain('left: 29%');
    expect(sixPoint(w, 8).attributes('style')).toContain('top: 29%');
    expect(sixPoint(w, 3).attributes('style')).toContain('left: 92%');
  });

  it('gives the points their own size: 18% of the board, never under 44 px (the 33% of the 3x3 boards would overlap)', () => {
    const source = readFileSync(resolve(__dirname, 'MorrisBoard.vue'), 'utf8');
    const rule = source.match(/\.morris-board\[data-refined='true'\] \.morris-point \{([^}]*)\}/)?.[1] ?? '';
    expect(rule).toContain('width: 18%');
    expect(rule).toContain('height: 18%');
    expect(rule).toContain('min-width: 44px');
    expect(rule).toContain('min-height: 44px');
  });

  it('lights the sides of the mill just closed and rings its three pieces', () => {
    const w = six({ cells: sixCells([0, 1, 2], []), mill: [0, 1, 2] });
    const lit = w.findAll('line[data-mill="true"]').map((l) => l.attributes('data-line')).sort();
    expect(lit).toEqual(['0-1', '1-2']);
    for (const n of [0, 1, 2]) expect(sixPoint(w, n).attributes('data-mill')).toBe('true');
    expect(sixPoint(w, 3).attributes('data-mill')).toBeUndefined();
    expect(sixPoint(w, 0).attributes('aria-label')).toContain(ui.inMill);
    expect(sixPoint(w, 3).attributes('aria-label')).not.toContain(ui.inMill);
  });

  it('lights nothing when there is no mill', () => {
    expect(six().findAll('[data-mill="true"]')).toHaveLength(0);
  });

  describe('removal step', () => {
    // Black holds 8 9 10 (a mill, protected) and 12; White (the viewer) may take 12 only.
    const props = {
      cells: sixCells([0, 1, 2], [8, 9, 10, 12]),
      mill: [0, 1, 2],
      removable: [12],
      protectedPoints: [8, 9, 10],
      selectable: sixFlags(12),
    };

    it('puts a dashed ring on each removable piece and a shield on each protected one, nowhere else', () => {
      const w = six(props);
      expect(w.findAll('[data-test="removal-ring"]')).toHaveLength(1);
      expect(sixPoint(w, 12).find('[data-test="removal-ring"]').exists()).toBe(true);
      expect(w.findAll('[data-test="shield"]')).toHaveLength(3);
      for (const n of [8, 9, 10]) expect(sixPoint(w, n).find('[data-test="shield"]').exists()).toBe(true);
      expect(sixPoint(w, 12).find('[data-test="shield"]').exists()).toBe(false);
      expect(sixPoint(w, 0).find('[data-test="removal-ring"]').exists()).toBe(false);
      expect(sixPoint(w, 12).attributes('data-removable')).toBe('true');
      expect(sixPoint(w, 9).attributes('data-protected')).toBe('true');
    });

    it('names them: can be taken, protected in a mill', () => {
      const w = six(props);
      expect(sixPoint(w, 12).attributes('aria-label')).toContain(ui.canTake);
      expect(sixPoint(w, 12).attributes('aria-label')).not.toContain(ui.canMove);
      expect(sixPoint(w, 9).attributes('aria-label')).toContain(ui.protectedPiece);
    });

    it('selects only the removable piece: a click or Enter on a protected one only clears', async () => {
      const w = six(props);
      await sixPoint(w, 12).trigger('click');
      expect(w.emitted('select')).toEqual([[12]]);
      await sixPoint(w, 9).trigger('click');
      await sixPoint(w, 9).trigger('keydown', { key: 'Enter' });
      expect(w.emitted('select')).toHaveLength(1);
      expect(w.emitted('clear')).toHaveLength(2);
      await sixPoint(w, 12).trigger('keydown', { key: 'Enter' });
      expect(w.emitted('select')).toEqual([[12], [12]]);
    });

    it('draws no ring while the board is inert (the other player is taking a piece)', () => {
      const w = six({ ...props, interactive: false });
      expect(w.findAll('[data-test="removal-ring"]')).toHaveLength(0);
      expect(sixPoint(w, 12).attributes('aria-disabled')).toBe('true');
    });

    it('keeps the ring turning under reduced motion: it is not stopped by the media query', () => {
      const source = readFileSync(resolve(__dirname, 'MorrisBoard.vue'), 'utf8');
      const reduced = source.match(/@media \(prefers-reduced-motion: reduce\) \{([\s\S]*?)\n\}/)?.[1] ?? '';
      expect(reduced).not.toContain('removal-ring');
      expect(source).toMatch(/\.morris-removal-ring \{[^}]*animation: ring-turn/);
    });
  });

  it('marks the point where a piece was just taken, with a name', () => {
    const w = six({ captured: 6 });
    expect(w.findAll('[data-test="captured-mark"]')).toHaveLength(1);
    expect(sixPoint(w, 6).find('[data-test="captured-mark"]').exists()).toBe(true);
    expect(sixPoint(w, 6).attributes('data-captured')).toBe('true');
    expect(sixPoint(w, 6).attributes('aria-label')).toContain(ui.justTaken);
    expect(six().find('[data-test="captured-mark"]').exists()).toBe(false);
  });

  it('puts the single tab stop on the first point to act on, else the selected one, else the first corner', () => {
    expect(sixPoint(six({ selectable: sixFlags(12, 3) }), 3).attributes('tabindex')).toBe('0');
    expect(sixPoint(six({ selected: 9 }), 9).attributes('tabindex')).toBe('0');
    const w = six();
    expect(sixPoint(w, 0).attributes('tabindex')).toBe('0');
    expect(Array.from({ length: 16 }, (_, n) => sixPoint(w, n).attributes('tabindex')).filter((t) => t === '0')).toHaveLength(1);
  });

  it('reaches every point with the arrows and with Page Down / Page Up', async () => {
    const w = mount(MorrisBoard, { props: { cells: Array(16).fill(null), geometry: GEOMETRIES.six }, attachTo: document.body });
    await sixPoint(w, 0).trigger('keydown', { key: 'ArrowRight' });
    expect(document.activeElement).toBe(sixPoint(w, 1).element);
    await sixPoint(w, 1).trigger('keydown', { key: 'ArrowDown' });
    expect(document.activeElement).toBe(sixPoint(w, 9).element);
    await sixPoint(w, 9).trigger('keydown', { key: 'ArrowDown' });
    expect(document.activeElement).toBe(sixPoint(w, 13).element);
    await sixPoint(w, 13).trigger('keydown', { key: 'PageDown' });
    expect(document.activeElement).toBe(sixPoint(w, 14).element);
    await sixPoint(w, 14).trigger('keydown', { key: 'PageDown' });
    await sixPoint(w, 15).trigger('keydown', { key: 'PageDown' });
    expect(document.activeElement).toBe(sixPoint(w, 15).element);
    await sixPoint(w, 15).trigger('keydown', { key: 'PageUp' });
    expect(document.activeElement).toBe(sixPoint(w, 14).element);
    w.unmount();
  });

  it('shows the highlighted points and the selected piece as on the other boards', () => {
    const w = six({ cells: sixCells([0], []), highlighted: sixFlags(1, 7), selected: 0, selectable: sixFlags(0, 1, 7) });
    expect(sixPoint(w, 1).attributes('data-state')).toBe('highlighted');
    expect(sixPoint(w, 0).attributes('data-state')).toBe('selected');
  });
});

describe('MorrisBoard keyboard', () => {
  it('goes by Page Down and Page Up through every point in order', async () => {
    const w = mount(MorrisBoard, { props: { cells: empty() }, attachTo: document.body });
    await point(w, 3).trigger('keydown', { key: 'PageDown' });
    expect(document.activeElement).toBe(point(w, 4).element);
    await point(w, 4).trigger('keydown', { key: 'PageUp' });
    expect(document.activeElement).toBe(point(w, 3).element);
    await point(w, 8).trigger('keydown', { key: 'PageDown' });
    expect(document.activeElement).toBe(point(w, 3).element);
    w.unmount();
  });

  it('keeps a single tab stop over the nine points, on the first one to act on', () => {
    const w = board({ selectable: flags(7, 3) });
    const stops = Array.from({ length: 9 }, (_, n) => point(w, n).attributes('tabindex')).filter((t) => t === '0');
    expect(stops).toHaveLength(1);
    expect(point(w, 3).attributes('tabindex')).toBe('0');
  });
});
