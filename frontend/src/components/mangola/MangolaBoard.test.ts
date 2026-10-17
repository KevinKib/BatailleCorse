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
    expect(w.findAll('button.mg-pit')).toHaveLength(32);
    expect(w.findAll('button')).toHaveLength(33); // and the speed button under the board
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
    const enabled = w.findAll('button.mg-pit').filter(b => b.attributes('aria-disabled') === undefined).map(b => b.attributes('data-test'));
    expect(enabled.sort()).toEqual(['pit-a2', 'pit-b1', 'pit-c2', 'pit-d1', 'pit-e2', 'pit-f1', 'pit-g2', 'pit-h1'].sort());
    expect(pit(w, 'a2').attributes('aria-label')).toContain('can sow');
  });

  it('enables nothing when it is not my turn or the board is disabled', async () => {
    const w = board({ interactive: false });
    expect(w.findAll('button.mg-pit').every(b => b.attributes('aria-disabled') === 'true')).toBe(true);
    await w.setProps({ interactive: true, disabled: true });
    expect(w.findAll('button.mg-pit').every(b => b.attributes('aria-disabled') === 'true')).toBe(true);
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

describe('MangolaBoard during a sowing animation', () => {
  const sowing = { animating: true, hand: { pit: 10, seeds: 3 }, activePit: 10 };

  it('shows no hand and no Skip button when nothing is playing', () => {
    const w = board();
    expect(w.find('[data-test="sowing-hand"]').exists()).toBe(false);
    expect(w.find('[data-test="skip-sowing"]').exists()).toBe(false);
  });

  it('draws the hand over the pit it is on, hidden from assistive technology, with the seeds it holds', () => {
    const w = board(sowing);
    const hand = w.get('[data-test="sowing-hand"]');
    expect(hand.attributes('aria-hidden')).toBe('true');
    expect(hand.attributes('data-pit')).toBe('10');
    expect(hand.text()).toBe('3');
    // c2 is in screen row 2 (of 0..3) and column 2 for the viewer on side A: a cell is 12.5 % wide, 25 % high.
    expect(hand.attributes('style')).toContain('left: 25%');
    expect(hand.attributes('style')).toContain('top: 50%');
  });

  it('moves the hand when the pit changes, and for the second side too', async () => {
    const w = board(sowing);
    await w.setProps({ hand: { pit: 25, seeds: 1 } });
    // b4 (pit 25) is in screen row 0 and column 1 for side A.
    const style = w.get('[data-test="sowing-hand"]').attributes('style');
    expect(style).toContain('left: 12.5%');
    expect(style).toContain('top: 0%');
    const b = board({ rows: viewerRows(mangolaState(), 1), ...sowing, hand: { pit: 25, seeds: 1 } }, 1);
    // Seen from side B, b4 is in screen row 3 and column 6.
    expect(b.get('[data-test="sowing-hand"]').attributes('style')).toContain('left: 75%');
    expect(b.get('[data-test="sowing-hand"]').attributes('style')).toContain('top: 75%');
  });

  it('marks the active pit and the pits being captured', () => {
    const w = board({ ...sowing, capturing: [18, 26] });
    expect(pit(w, 'c2').attributes('data-active')).toBe('true');
    expect(w.findAll('[data-active="true"]')).toHaveLength(1);
    expect(pit(w, 'c3').attributes('data-capturing')).toBe('true');
    expect(pit(w, 'c4').attributes('data-capturing')).toBe('true');
  });

  it('keeps every pit inert even on my turn, and a press on a pit skips instead of sowing', async () => {
    const w = board(sowing);
    expect(w.findAll('button[data-pit]').every(b => b.attributes('aria-disabled') === 'true')).toBe(true);
    await pit(w, 'a2').trigger('click');
    expect(w.emitted('sow')).toBeUndefined();
    expect(w.emitted('skip')).toHaveLength(1);
    await pit(w, 'c2').trigger('keydown', { key: 'Enter' });
    expect(w.emitted('sow')).toBeUndefined();
    expect(w.emitted('skip')).toHaveLength(2);
  });

  it('has a Skip button that emits skip', async () => {
    const w = board(sowing);
    const skip = w.get('[data-test="skip-sowing"]');
    expect(skip.text()).toBe(ui.board.skip);
    expect(skip.element.tagName).toBe('BUTTON');
    await skip.trigger('click');
    expect(w.emitted('skip')).toHaveLength(1);
  });

  it('a tap on the board between the pits skips too', async () => {
    const w = board(sowing);
    await w.get('.mg-board__grid').trigger('click');
    expect(w.emitted('skip')).toHaveLength(1);
  });

  it('does not repeat the announcement: the live region keeps the one sentence it was given', async () => {
    const w = board({ ...sowing, announcement: 'Bob sowed from a2.' });
    await w.setProps({ hand: { pit: 11, seeds: 2 }, activePit: 11 });
    expect(w.get('[data-test="board-live"]').text()).toBe('Bob sowed from a2.');
    expect(w.find('[data-test="sowing-hand"]').element.closest('[role="status"]')).toBeNull();
  });

  it('shows the intermediate counts it is given', () => {
    const state = mangolaState({ board: Array.from({ length: 32 }, (_, i) => (i === 8 ? 0 : i === 9 ? 5 : 1)) });
    const w = board({ rows: viewerRows(state, 0), ...sowing });
    expect(pit(w, 'a2').attributes('aria-label')).toContain('0 seeds');
    expect(pit(w, 'b2').attributes('aria-label')).toContain('5 seeds');
  });

  describe('the speed button', () => {
    const speedButton = (w: ReturnType<typeof board>) => w.get('[data-test="speed-button"]');

    it('is a real button, always there, named after the speed in words', () => {
      const w = board();
      const button = speedButton(w);
      expect(button.element.tagName).toBe('BUTTON');
      expect(button.attributes('type')).toBe('button');
      expect(button.attributes('aria-label')).toBe('Animation speed: Normal');
      expect(button.text()).toContain('Normal');
    });

    it('says the speed it is given: Fast, Instant', async () => {
      const w = board({ speed: 'fast' });
      expect(speedButton(w).attributes('aria-label')).toBe('Animation speed: Fast');
      await w.setProps({ speed: 'instant' });
      expect(speedButton(w).attributes('aria-label')).toBe('Animation speed: Instant');
      expect(speedButton(w).text()).toContain('Instant');
    });

    it('emits cycle-speed on a tap, also while a sowing plays (and does not skip it)', async () => {
      const w = board({ animating: true, hand: { pit: 8, seeds: 2 }, activePit: 8 });
      await speedButton(w).trigger('click');
      expect(w.emitted('cycle-speed')).toHaveLength(1);
      expect(w.emitted('skip')).toBeUndefined();
    });

    it('is on the left of the line under the board, Skip on its right, the legend between', async () => {
      const w = board({ animating: true });
      const footer = w.get('.mg-board__footer');
      const order = [...footer.element.children].map(c => (c as HTMLElement).dataset.test ?? c.className);
      expect(order.indexOf('speed-button')).toBeGreaterThanOrEqual(0);
      expect(order.indexOf('speed-button')).toBeLessThan(order.indexOf('mg-board__legend mg-board__legend--bottom'));
      expect(order.indexOf('mg-board__legend mg-board__legend--bottom')).toBeLessThan(order.indexOf('skip-sowing'));
    });

    it('announces the new speed once it changes, not at first', async () => {
      const w = board({ speed: 'normal' });
      expect(w.get('[data-test="speed-live"]').text()).toBe('');
      expect(w.get('[data-test="speed-live"]').attributes('role')).toBe('status');
      await w.setProps({ speed: 'fast' });
      expect(w.get('[data-test="speed-live"]').text()).toBe('Animation speed: Fast');
      await w.setProps({ speed: 'instant' });
      expect(w.get('[data-test="speed-live"]').text()).toBe('Animation speed: Instant');
    });

    it('slides the hand in the time of the speed', async () => {
      const props = { hand: { pit: 8, seeds: 2 }, activePit: 8, animating: true };
      const w = board({ ...props, speed: 'normal' });
      expect(w.get('[data-test="sowing-hand"]').attributes('style')).toContain('180ms');
      await w.setProps({ speed: 'fast' });
      expect(w.get('[data-test="sowing-hand"]').attributes('style')).toContain('90ms');
    });
  });

  it('flags the pit the opponent chose', () => {
    const w = board({ chosenPit: 8, animating: true, hand: { pit: 8, seeds: 2 } });
    expect(pit(w, 'a2').attributes('data-chosen')).toBe('true');
    expect(w.findAll('[data-chosen="true"]')).toHaveLength(1);
    expect(board().findAll('[data-chosen="true"]')).toHaveLength(0);
  });
});
