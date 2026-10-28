import { describe, it, expect, afterEach, vi } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';
import { nextTick } from 'vue';
import DraughtsBoard from './DraughtsBoard.vue';
import {
  draughtsAmbiguousState, draughtsCaptureState, draughtsFinishedState, draughtsOpeningState,
} from '../../model/draughts/fixtures';
import { INSTANT_TEMPO, NORMAL_TEMPO } from '../../model/draughts/tempo';
import type { DraughtsState } from '../../model/draughts/DraughtsState';
import { messagesEn } from '../../locales/en';

const ui = messagesEn.draughtsUi;

type Props = InstanceType<typeof DraughtsBoard>['$props'];

function boardOf(state: DraughtsState, extra: Partial<Props> = {}): Props {
  return {
    board: state.board,
    orientation: 'WHITE',
    selectable: [],
    selected: null,
    targets: [],
    moves: state.legalMoves,
    tempo: INSTANT_TEMPO,
    ...extra,
  } as Props;
}

let wrapper: VueWrapper | null = null;
function mountBoard(props: Props) {
  wrapper = mount(DraughtsBoard, { props, attachTo: document.body });
  return wrapper;
}
afterEach(() => { wrapper?.unmount(); wrapper = null; document.body.innerHTML = ''; vi.useRealTimers(); });

const squares = (w: VueWrapper) => w.findAll('[data-test^="square-"]');
const squareNumbers = (w: VueWrapper) => squares(w).map(s => Number(s.attributes('data-test')!.replace('square-', '')));
const square = (w: VueWrapper, n: number) => w.get(`[data-test="square-${n}"]`);

describe('DraughtsBoard', () => {
  describe('the squares', () => {
    it('has one button per dark square and nothing to press on the light ones', () => {
      const w = mountBoard(boardOf(draughtsOpeningState));
      expect(w.findAll('button')).toHaveLength(50);
      expect(squares(w)).toHaveLength(50);
    });

    it('reads 1 to 50 from the top-left when White is at the bottom', () => {
      const w = mountBoard(boardOf(draughtsOpeningState, { orientation: 'WHITE' }));
      expect(squareNumbers(w)).toEqual(Array.from({ length: 50 }, (_, i) => i + 1));
    });

    it('reads 50 to 1 from the top-left when Black is at the bottom (the board turned by 180 degrees)', () => {
      const w = mountBoard(boardOf(draughtsOpeningState, { orientation: 'BLACK' }));
      expect(squareNumbers(w)).toEqual(Array.from({ length: 50 }, (_, i) => 50 - i));
    });

    it('puts the first dark square of the top row on the second column and the first of the next row on the first', () => {
      const w = mountBoard(boardOf(draughtsOpeningState));
      expect(square(w, 1).attributes('data-col')).toBe('1');
      expect(square(w, 1).attributes('data-row')).toBe('0');
      expect(square(w, 6).attributes('data-col')).toBe('0');
      expect(square(w, 6).attributes('data-row')).toBe('1');
    });

    it('draws the pieces of the state: 20 black on 1 to 20, 20 white on 31 to 50', () => {
      const w = mountBoard(boardOf(draughtsOpeningState));
      const on = (kind: string) => squares(w).filter(s => s.attributes('data-piece') === kind)
        .map(s => Number(s.attributes('data-test')!.replace('square-', '')));
      expect(on('black-man')).toEqual(Array.from({ length: 20 }, (_, i) => i + 1));
      expect(on('white-man')).toEqual(Array.from({ length: 20 }, (_, i) => i + 31));
      expect(on('white-king')).toEqual([]);
    });

    it('marks a king with a crown, not by colour alone', () => {
      const w = mountBoard(boardOf(draughtsFinishedState));
      expect(square(w, 47).attributes('data-piece')).toBe('white-king');
      expect(square(w, 47).find('[data-test="king-mark"]').exists()).toBe(true);
      expect(square(w, 22).attributes('data-piece')).toBe('white-man');
      expect(square(w, 22).find('[data-test="king-mark"]').exists()).toBe(false);
      expect(square(w, 47).attributes('aria-label')).toContain(ui.board.whiteKing);
      expect(square(w, 22).attributes('aria-label')).toContain(ui.board.whiteMan);
    });

    it('names a square by its number and what is on it', () => {
      const w = mountBoard(boardOf(draughtsOpeningState));
      expect(square(w, 25).attributes('aria-label')).toBe(`Square 25: ${ui.board.empty}`);
      expect(square(w, 3).attributes('aria-label')).toBe(`Square 3: ${ui.board.blackMan}`);
    });
  });

  describe('what can be played', () => {
    it('enables only the pieces that can move, and says so in their name', () => {
      const w = mountBoard(boardOf(draughtsCaptureState, { selectable: [33, 34] }));
      const enabled = squares(w).filter(s => s.attributes('aria-disabled') !== 'true')
        .map(s => Number(s.attributes('data-test')!.replace('square-', '')));
      expect(enabled).toEqual([33, 34]);
      expect(square(w, 33).attributes('aria-label')).toContain(ui.board.canMove);
      expect(square(w, 10).attributes('aria-label')).not.toContain(ui.board.canMove);
    });

    it('shows the destinations of the selected piece and marks the piece selected', () => {
      const w = mountBoard(boardOf(draughtsOpeningState, { selectable: [31, 32], selected: 32, targets: [27, 28] }));
      expect(square(w, 32).attributes('aria-pressed')).toBe('true');
      expect(square(w, 32).attributes('aria-label')).toContain(ui.board.selected);
      expect([27, 28].map(n => square(w, n).attributes('data-target'))).toEqual(['true', 'true']);
      expect(square(w, 27).attributes('aria-label')).toContain(ui.board.destination);
      expect(square(w, 26).attributes('data-target')).toBeUndefined();
      expect(square(w, 27).attributes('aria-disabled')).toBeUndefined();
    });

    it('emits the square that was pressed', async () => {
      const w = mountBoard(boardOf(draughtsOpeningState, { selectable: [31, 32] }));
      await square(w, 32).trigger('click');
      expect(w.emitted('select')).toEqual([[32]]);
    });

    it('does not answer to a press when it is not my turn or the game is over', async () => {
      const w = mountBoard(boardOf(draughtsOpeningState, { selectable: [31], interactive: false }));
      await square(w, 31).trigger('click');
      await w.setProps({ interactive: true, disabled: true });
      await square(w, 31).trigger('click');
      expect(w.emitted('select')).toBeUndefined();
    });

    it('presses with Enter and Space and clears with Escape', async () => {
      const w = mountBoard(boardOf(draughtsOpeningState, { selectable: [31, 32], selected: 32, targets: [27] }));
      await square(w, 31).trigger('keydown', { key: 'Enter' });
      await square(w, 31).trigger('keydown', { key: ' ' });
      await square(w, 31).trigger('keydown', { key: 'Escape' });
      expect(w.emitted('select')).toEqual([[31], [31]]);
      expect(w.emitted('clear')).toHaveLength(1);
    });

    it('has one tab stop, the first piece that can move', () => {
      const w = mountBoard(boardOf(draughtsOpeningState, { selectable: [32, 33] }));
      const stops = squares(w).filter(s => s.attributes('tabindex') === '0');
      expect(stops.map(s => s.attributes('data-test'))).toEqual(['square-32']);
    });

    it('moves the focus along the board with the arrow keys', async () => {
      const w = mountBoard(boardOf(draughtsOpeningState, { selectable: [32] }));
      (square(w, 32).element as HTMLElement).focus();
      await square(w, 32).trigger('keydown', { key: 'ArrowRight' });
      await nextTick();
      expect(document.activeElement).toBe(square(w, 33).element);
      await square(w, 33).trigger('keydown', { key: 'ArrowUp' });
      await nextTick();
      expect(document.activeElement).toBe(square(w, 28).element);
    });

    it('turns the arrows round with the board: Black sees square 50 at the top-left', async () => {
      const w = mountBoard(boardOf(draughtsOpeningState, { orientation: 'BLACK', selectable: [19] }));
      (square(w, 19).element as HTMLElement).focus();
      await square(w, 19).trigger('keydown', { key: 'ArrowRight' });
      await nextTick();
      expect(document.activeElement).toBe(square(w, 18).element);
    });
  });

  describe('the preview of a capture', () => {
    const rafle = draughtsAmbiguousState;

    it('draws the path of a destination and marks the pieces it takes while it is pointed at', async () => {
      const w = mountBoard(boardOf(draughtsCaptureState, { selectable: [33, 34], selected: 33, targets: [22] }));
      expect(w.find('[data-test="path-a"]').exists()).toBe(false);
      await square(w, 22).trigger('mouseenter');
      expect(w.get('[data-test="path-a"]').attributes('points')).not.toBe('');
      expect(square(w, 28).attributes('data-taken')).toBe('true');
      expect(square(w, 28).attributes('aria-label')).toContain(ui.board.taken);
      expect(square(w, 30).attributes('data-taken')).toBeUndefined();
      await square(w, 22).trigger('mouseleave');
      expect(w.find('[data-test="path-a"]').exists()).toBe(false);
      expect(square(w, 28).attributes('data-taken')).toBeUndefined();
    });

    it('previews a destination reached by keyboard focus as well', async () => {
      const w = mountBoard(boardOf(draughtsCaptureState, { selectable: [33, 34], selected: 33, targets: [22] }));
      await square(w, 22).trigger('focus');
      expect(w.find('[data-test="path-a"]').exists()).toBe(true);
    });

    it('draws every candidate of an ambiguous destination, the first solid and the second dashed', async () => {
      const w = mountBoard(boardOf(rafle, { selectable: [46], selected: 46, targets: [19] }));
      await square(w, 19).trigger('mouseenter');
      expect(w.find('[data-test="path-a"]').exists()).toBe(true);
      expect(w.find('[data-test="path-b"]').exists()).toBe(true);
    });

    it('numbers the pieces that tell the candidates apart while the chooser is open', async () => {
      const w = mountBoard(boardOf(rafle, {
        selectable: [46], selected: 46, targets: [19], choice: rafle.legalMoves,
      }));
      const badge = (n: number) => square(w, n).find('[data-test="route-badge"]');
      expect([37, 28].map(n => badge(n).text())).toEqual(['1', '1']);
      expect([32, 23].map(n => badge(n).text())).toEqual(['2', '2']);
      expect(badge(37).exists()).toBe(true);
      expect(square(w, 37).attributes('aria-label')).toContain('route 1');
      expect(w.find('[data-test="path-a"]').exists()).toBe(true);
      expect(w.find('[data-test="path-b"]').exists()).toBe(true);
    });

    it('lets a numbered piece be pressed to pick its route', async () => {
      const w = mountBoard(boardOf(rafle, {
        selectable: [46], selected: 46, targets: [19], choice: rafle.legalMoves,
      }));
      expect(square(w, 37).attributes('aria-disabled')).toBeUndefined();
      await square(w, 37).trigger('click');
      expect(w.emitted('select')).toEqual([[37]]);
    });
  });

  describe('the last move', () => {
    it('tints the start and the end of the opponent’s last move', () => {
      const w = mountBoard(boardOf(draughtsFinishedState, {
        lastMove: { from: 28, to: 22, path: [22], captures: [], promotes: false },
      }));
      expect([22, 28].map(n => square(w, n).attributes('data-last'))).toEqual(['true', 'true']);
      expect(square(w, 47).attributes('data-last')).toBeUndefined();
      expect(square(w, 22).attributes('aria-label')).toContain(ui.board.lastMove);
    });
  });

  describe('the announcement', () => {
    it('hands the text to a live region', async () => {
      const w = mountBoard(boardOf(draughtsOpeningState, { announcement: 'Bob played 19-23.' }));
      expect(w.get('[data-test="board-live"]').text()).toBe('Bob played 19-23.');
      expect(w.get('[data-test="board-live"]').attributes('role')).toBe('status');
    });

    it('names the board', () => {
      const w = mountBoard(boardOf(draughtsOpeningState));
      expect(w.get('[data-test="draughts-board"]').attributes('aria-label')).toBe(ui.board.label);
    });
  });

  describe('the motion of a move', () => {
    const before = draughtsCaptureState.board;
    // Alice takes the piece on 28 with 33x22: the piece on 22 is the one that moved.
    const after = [...before];
    after[32] = null; after[27] = null; after[21] = 'w';
    const move = { seat: 0, from: 33, to: 22, path: [22], captures: [28], promotes: false };

    it('glides the piece from where it started and keeps the captured piece until its removal', async () => {
      vi.useFakeTimers();
      const w = mountBoard(boardOf({ ...draughtsCaptureState, board: before }, { tempo: NORMAL_TEMPO }));
      await w.setProps({ board: after, motion: move });
      await nextTick();
      expect(w.find('[data-test="glide-piece"]').exists()).toBe(true);
      expect(w.get('[data-test="glide-piece"]').attributes('data-square')).toBe('33');
      expect(square(w, 22).attributes('data-piece')).toBeUndefined(); // the arrival waits for the glide to end
      expect(w.find('[data-test="ghost-28"]').exists()).toBe(true);

      await vi.advanceTimersByTimeAsync(60);
      expect(w.get('[data-test="glide-piece"]').attributes('data-square')).toBe('22');

      await vi.advanceTimersByTimeAsync(NORMAL_TEMPO.legMs + 20);
      expect(w.find('[data-test="glide-piece"]').exists()).toBe(false);
      expect(square(w, 22).attributes('data-piece')).toBe('white-man');
      expect(w.find('[data-test="ghost-28"]').exists()).toBe(true); // removed after the glide

      await vi.advanceTimersByTimeAsync(NORMAL_TEMPO.removalMs + 20);
      expect(w.find('[data-test="ghost-28"]').exists()).toBe(false);
    });

    it('shows the new position at once when nothing is to move (reduced motion or Instant speed)', async () => {
      const w = mountBoard(boardOf({ ...draughtsCaptureState, board: before }, { tempo: INSTANT_TEMPO }));
      await w.setProps({ board: after, motion: move });
      await nextTick();
      expect(w.find('[data-test="glide-piece"]').exists()).toBe(false);
      expect(w.find('[data-test="ghost-28"]').exists()).toBe(false);
      expect(square(w, 22).attributes('data-piece')).toBe('white-man');
    });

    it('stops the timers when the board goes away', async () => {
      vi.useFakeTimers();
      const w = mountBoard(boardOf({ ...draughtsCaptureState, board: before }, { tempo: NORMAL_TEMPO }));
      await w.setProps({ board: after, motion: move });
      w.unmount();
      wrapper = null;
      expect(vi.getTimerCount()).toBe(0);
    });
  });
});
