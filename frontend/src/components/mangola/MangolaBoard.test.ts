import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import MangolaBoard from './MangolaBoard.vue';
import { viewerRows } from '../../model/mangola/board';
import { NO_BOARD_MARKS } from '../../model/mangola/marks';
import { mangolaState } from '../../model/mangola/mangolaFixtures';
import { messagesEn } from '../../locales/en';

const ui = messagesEn.mangolaUi;

function board(props: Record<string, unknown> = {}, seat = 0) {
  return mount(MangolaBoard, {
    attachTo: document.body,
    props: { rows: viewerRows(mangolaState(), seat), interactive: true, ...props },
  });
}
const pit = (w: ReturnType<typeof board>, name: string) => w.get(`[data-test="pit-${name}"]`);

describe('MangolaBoard', () => {
  it('is a named group of 32 real buttons in a 4 x 8 grid', () => {
    const w = board();
    expect(w.get('[data-test="mangola-board"]').attributes('aria-label')).toBe(ui.board.label);
    expect(w.findAll('button')).toHaveLength(32);
    expect(w.findAll('[data-test="board-row"]')).toHaveLength(4);
    for (const row of w.findAll('[data-test="board-row"]')) expect(row.findAll('button')).toHaveLength(8);
  });

  it('every pit shows its count and its name', () => {
    const w = board();
    expect(pit(w, 'b1').attributes('aria-label')).toContain('Pit b1, 4 seeds');
    expect(pit(w, 'a1').attributes('aria-label')).toContain('Pit a1, 0 seeds');
    expect(w.findAll('[data-test="seed"]').length).toBe(64); // 16 pits of 4 on the checkerboard
  });

  it('puts the viewer half at the bottom: rows 4, 3, 2, 1 for side A, rows 1, 2, 3, 4 for side B', () => {
    const a = board({}, 0);
    const namesA = a.findAll('[data-test="board-row"]').map(r => r.findAll('button')[0].attributes('data-test'));
    expect(namesA).toEqual(['pit-a4', 'pit-a3', 'pit-a2', 'pit-a1']);
    const b = board({ rows: viewerRows(mangolaState(), 1) }, 1);
    const namesB = b.findAll('[data-test="board-row"]').map(r => r.findAll('button')[0].attributes('data-test'));
    expect(namesB).toEqual(['pit-h1', 'pit-h2', 'pit-h3', 'pit-h4']);
  });

  it('enables only the legal pits', () => {
    const w = board();
    const enabled = w.findAll('button').filter(b => b.attributes('aria-disabled') === undefined).map(b => b.attributes('data-test'));
    expect(enabled.sort()).toEqual(['pit-a2', 'pit-b1', 'pit-c2', 'pit-d1', 'pit-e2', 'pit-f1', 'pit-g2', 'pit-h1'].sort());
    expect(pit(w, 'a2').attributes('aria-label')).toContain('can sow');
  });

  it('enables nothing when it is not my turn or the board is disabled', async () => {
    const w = board({ interactive: false });
    expect(w.findAll('button').every(b => b.attributes('aria-disabled') === 'true')).toBe(true);
    await w.setProps({ interactive: true, disabled: true });
    expect(w.findAll('button').every(b => b.attributes('aria-disabled') === 'true')).toBe(true);
  });

  it('emits sow with the pit number on a click of a legal pit, and nothing on another', async () => {
    const w = board();
    await pit(w, 'a2').trigger('click');
    expect(w.emitted('sow')).toEqual([[8]]);
    await pit(w, 'a1').trigger('click');
    expect(w.emitted('sow')).toHaveLength(1);
  });

  it('has a single tab stop, on the first legal pit in reading order', () => {
    const w = board();
    const stops = w.findAll('button').filter(b => b.attributes('tabindex') === '0');
    expect(stops).toHaveLength(1);
    expect(stops[0].attributes('data-test')).toBe('pit-a2');
  });

  it('moves focus with the arrow keys over the screen grid and stops at the edges', async () => {
    const w = board();
    const start = pit(w, 'c2');
    (start.element as HTMLElement).focus();
    await start.trigger('keydown', { key: 'ArrowUp' });
    expect(document.activeElement?.getAttribute('data-test')).toBe('pit-c3');
    await pit(w, 'c3').trigger('keydown', { key: 'ArrowRight' });
    expect(document.activeElement?.getAttribute('data-test')).toBe('pit-d3');
    await pit(w, 'd3').trigger('keydown', { key: 'ArrowDown' });
    await pit(w, 'd2').trigger('keydown', { key: 'ArrowDown' });
    expect(document.activeElement?.getAttribute('data-test')).toBe('pit-d1');
    await pit(w, 'd1').trigger('keydown', { key: 'ArrowDown' });
    expect(document.activeElement?.getAttribute('data-test')).toBe('pit-d1');
    await pit(w, 'd1').trigger('keydown', { key: 'ArrowLeft' });
    expect(document.activeElement?.getAttribute('data-test')).toBe('pit-c1');
    w.unmount();
  });

  it('Enter and Space sow from a legal pit', async () => {
    const w = board();
    await pit(w, 'c2').trigger('keydown', { key: 'Enter' });
    await pit(w, 'e2').trigger('keydown', { key: ' ' });
    await pit(w, 'a1').trigger('keydown', { key: 'Enter' });
    expect(w.emitted('sow')).toEqual([[10], [12]]);
  });

  it('draws a direction chevron on every pit', () => {
    expect(board().findAll('[data-test="chevron"]')).toHaveLength(32);
  });

  it('separates the two inner rows with a divider', () => {
    expect(board().find('[data-test="inner-divider"]').exists()).toBe(true);
  });

  it('marks the last move: start ring, end ring, captured pits with -n', () => {
    const w = board({ marks: { start: 16, end: 18, captured: [{ pit: 9, seeds: 3 }, { pit: 1, seeds: null }] } });
    expect(pit(w, 'a3').attributes('data-last-start')).toBe('true');
    expect(pit(w, 'c3').attributes('data-last-end')).toBe('true');
    expect(pit(w, 'b2').attributes('data-captured')).toBe('true');
    expect(pit(w, 'b2').get('[data-test="captured-seeds"]').text()).toBe('-3');
    expect(pit(w, 'b1').attributes('data-captured')).toBe('true');
    expect(w.findAll('[data-last-start="true"]')).toHaveLength(1);
  });

  it('shows no mark without a last move', () => {
    const w = board({ marks: NO_BOARD_MARKS });
    expect(w.findAll('[data-last-start],[data-last-end],[data-captured]')).toHaveLength(0);
  });

  it('keeps the screen-reader sentence of the last move in a live region', () => {
    const w = board({ announcement: 'Bob sowed c3, captured 5 seeds' });
    const live = w.get('[data-test="board-live"]');
    expect(live.attributes('role')).toBe('status');
    expect(live.text()).toBe('Bob sowed c3, captured 5 seeds');
  });

  it('names the two halves', () => {
    const w = board();
    expect(w.text()).toContain(ui.board.yourSide);
    expect(w.text()).toContain(ui.board.opponentSide);
  });

  it('shows a hidden pit for each null count', () => {
    const state = mangolaState({ board: Array.from({ length: 32 }, (_, i) => (i < 16 ? 2 : null)) });
    const w = board({ rows: viewerRows(state, 0) });
    expect(w.findAll('[data-hidden="true"]')).toHaveLength(16);
  });
});
