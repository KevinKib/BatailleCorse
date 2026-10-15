import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import MangolaPlacementEditor from './MangolaPlacementEditor.vue';
import { viewerRows } from '../../model/mangola/board';
import { clearLayout, seedsLeft as seedsLeftOf, twoPerPit } from '../../model/mangola/draft';
import { placementState } from '../../model/mangola/mangolaFixtures';
import { messagesEn } from '../../locales/en';

const ui = messagesEn.mangolaUi;

// Side A loop: inner row (pits 8..15) left to right, then the outer row (7..0) right to left.
function editor(props: Record<string, unknown> = {}) {
  const draft = (props.draft as number[] | undefined) ?? clearLayout();
  return mount(MangolaPlacementEditor, {
    props: {
      rows: viewerRows(placementState(draft), 0),
      side: 'A',
      draft,
      seedsLeft: seedsLeftOf(draft),
      canConfirm: seedsLeftOf(draft) === 0,
      ready: false,
      opponentReady: false,
      opponentName: 'Bob',
      draftOnlyEndless: false,
      error: null,
      ...props,
    },
  });
}
const pit = (w: ReturnType<typeof editor>, name: string) => w.get(`[data-test="pit-${name}"]`);
const byTest = (w: ReturnType<typeof editor>, id: string) => w.get(`[data-test="${id}"]`);

describe('MangolaPlacementEditor', () => {
  it('shows my 16 pits as buttons and the opponent half as 16 inert ghost pits', () => {
    const w = editor();
    expect(w.findAll('button[data-test^="pit-"]')).toHaveLength(16);
    expect(w.findAll('[data-test="ghost-pit"]')).toHaveLength(16);
    expect(w.findAll('[data-test="ghost-pit"] button')).toHaveLength(0);
  });

  it('the opponent half is blank (no seeds, no counts) whatever the draft', () => {
    const w = editor({ draft: twoPerPit() });
    const ghosts = w.get('[data-test="opponent-half"]');
    expect(ghosts.findAll('[data-test="seed"]')).toHaveLength(0);
    expect(ghosts.attributes('aria-hidden')).toBeUndefined(); // the status line is read, the ghost pits are hidden
    expect(w.findAll('[data-test="ghost-pit"][aria-hidden="true"]')).toHaveLength(16);
  });

  it('shows the seeds left in a status region, and says when all are placed', () => {
    const draft = clearLayout();
    draft[0] = 20;
    const w = editor({ draft });
    expect(byTest(w, 'seeds-left').text()).toBe('12 seeds left');
    expect(byTest(w, 'seeds-left').attributes('role')).toBe('status');
    expect(editor({ draft: twoPerPit() }).get('[data-test="seeds-left"]').text()).toBe(ui.placement.allPlaced);
    const one = twoPerPit();
    one[0] = 1;
    expect(editor({ draft: one }).get('[data-test="seeds-left"]').text()).toBe('1 seed left');
  });

  it('shows the count of every pit from the draft, not from the server board', () => {
    const draft = clearLayout();
    draft[0] = 3; // first of the loop = inner row, left: a2
    const w = editor({ draft });
    expect(pit(w, 'a2').attributes('aria-label')).toContain('3 seeds');
    expect(pit(w, 'b2').attributes('aria-label')).toContain('0 seeds');
  });

  it('a tap on a pit asks to add a seed at its place in the loop', async () => {
    const w = editor();
    await pit(w, 'a2').trigger('click'); // loop index 0
    await pit(w, 'a1').trigger('click'); // outer row, right to left: a1 is the last, index 15
    await pit(w, 'h2').trigger('click'); // index 7
    expect(w.emitted('add')).toEqual([[0], [15], [7]]);
  });

  it('never asks to add once all 32 seeds are placed', async () => {
    const w = editor({ draft: twoPerPit() });
    await pit(w, 'a2').trigger('click');
    expect(w.emitted('add')).toBeUndefined();
    expect(pit(w, 'a2').attributes('aria-disabled')).toBe('true');
  });

  it('the Remove toggle turns a tap into a removal, and never below zero', async () => {
    const draft = clearLayout();
    draft[0] = 1;
    draft[1] = 31;
    const w = editor({ draft });
    const toggle = byTest(w, 'mode-remove');
    expect(toggle.attributes('aria-pressed')).toBe('false');
    await toggle.trigger('click');
    expect(toggle.attributes('aria-pressed')).toBe('true');
    await pit(w, 'a2').trigger('click');
    await pit(w, 'c2').trigger('click'); // empty: nothing to remove
    expect(w.emitted('remove')).toEqual([[0]]);
    expect(w.emitted('add')).toBeUndefined();
    expect(pit(w, 'c2').attributes('aria-disabled')).toBe('true');
    expect(pit(w, 'a2').attributes('aria-label')).toContain(ui.pit.tapToRemove);
    await byTest(w, 'mode-add').trigger('click');
    expect(byTest(w, 'mode-remove').attributes('aria-pressed')).toBe('false');
  });

  it('names the action of a pit in add mode', () => {
    expect(pit(editor(), 'a2').attributes('aria-label')).toContain(ui.pit.tapToAdd);
  });

  it('offers the four presets by name and emits them', async () => {
    const w = editor();
    const labels = ['checkerboard', 'two-per-pit', 'random', 'clear'];
    for (const key of labels) await byTest(w, `preset-${key}`).trigger('click');
    expect(w.emitted('preset')).toEqual(labels.map(k => [k]));
    expect(byTest(w, 'preset-checkerboard').text()).toBe(ui.placement.presets.checkerboard);
    expect(byTest(w, 'preset-two-per-pit').text()).toBe(ui.placement.presets.twoPerPit);
    expect(byTest(w, 'preset-random').text()).toBe(ui.placement.presets.random);
    expect(byTest(w, 'preset-clear').text()).toBe(ui.placement.presets.clear);
  });

  it('Confirm is enabled at exactly 0 seeds left and not before', async () => {
    const draft = twoPerPit();
    const almost = [...draft];
    almost[0] = 1;
    const w = editor({ draft: almost });
    const confirm = byTest(w, 'confirm');
    expect(confirm.attributes('disabled')).toBeDefined();
    expect(w.text()).toContain('Place all your seeds to confirm (1 left).');
    await confirm.trigger('click');
    expect(w.emitted('confirm')).toBeUndefined();

    const full = editor({ draft });
    expect(byTest(full, 'confirm').attributes('disabled')).toBeUndefined();
    await byTest(full, 'confirm').trigger('click');
    expect(full.emitted('confirm')).toHaveLength(1);
  });

  it('once I confirmed: pits are frozen, Confirm is replaced by Edit layout', async () => {
    const w = editor({ draft: twoPerPit(), ready: true });
    expect(w.find('[data-test="confirm"]').exists()).toBe(false);
    expect(w.text()).toContain(ui.placement.confirmedWaiting);
    for (const b of w.findAll('button[data-test^="pit-"]')) expect(b.attributes('aria-disabled')).toBe('true');
    expect(byTest(w, 'preset-random').attributes('disabled')).toBeDefined();
    await byTest(w, 'edit-layout').trigger('click');
    expect(w.emitted('edit')).toHaveLength(1);
    await pit(w, 'a2').trigger('click');
    expect(w.emitted('add')).toBeUndefined();
  });

  it('greys the opponent with Placing... then Ready', async () => {
    const w = editor();
    const status = byTest(w, 'opponent-status');
    expect(status.text()).toBe('Bob: Placing…');
    expect(status.attributes('role')).toBe('status');
    expect(byTest(w, 'opponent-half').attributes('data-ready')).toBe('false');
    await w.setProps({ opponentReady: true });
    expect(status.text()).toBe('Bob: Ready');
    expect(byTest(w, 'opponent-half').attributes('data-ready')).toBe('true');
  });

  it('names an opponent without a name', () => {
    expect(editor({ opponentName: null }).get('[data-test="opponent-status"]').text()).toBe('Opponent: Placing…');
  });

  it('warns when the draft only has endless moves', () => {
    expect(editor().find('[data-test="endless-warning"]').exists()).toBe(false);
    const w = editor({ draftOnlyEndless: true });
    expect(w.get('[data-test="endless-warning"]').text()).toContain(ui.placement.endlessWarning);
  });

  it('shows the refusal of a start without a move, announced as an alert', () => {
    const w = editor({ draft: twoPerPit(), error: ui.errors.stuckStart });
    const alert = w.get('[data-test="placement-error"]');
    expect(alert.attributes('role')).toBe('alert');
    expect(alert.text()).toBe(ui.errors.stuckStart);
    expect(editor().find('[data-test="placement-error"]').exists()).toBe(false);
  });

  it('draws the direction chevrons on my pits', () => {
    expect(editor().findAll('[data-test="chevron"]').length).toBeGreaterThanOrEqual(16);
  });
});
