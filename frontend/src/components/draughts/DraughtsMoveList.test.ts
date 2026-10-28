import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import DraughtsMoveList from './DraughtsMoveList.vue';
import { messagesEn } from '../../locales/en';

const ui = messagesEn.draughtsUi.moves;
const list = (history: string[]) => mount(DraughtsMoveList, { props: { history } });
const rows = (w: ReturnType<typeof list>) => w.findAll('[data-test="moves-row"]')
  .map(r => r.findAll('[data-test^="moves-cell"]').map(c => c.text()));

describe('DraughtsMoveList', () => {
  it('says there is nothing yet before the first move', () => {
    const w = list([]);
    expect(w.get('[data-test="moves-empty"]').text()).toBe(ui.empty);
    expect(w.findAll('[data-test="moves-row"]')).toHaveLength(0);
  });

  it('pairs the moves by turn, White first, the notation as the server wrote it', () => {
    expect(rows(list(['32-28', '19-23', '28x19', '12-18']))).toEqual([
      ['1', '32-28', '19-23'],
      ['2', '28x19', '12-18'],
    ]);
  });

  it('leaves the Black cell empty while Black has not answered', () => {
    expect(rows(list(['32-28', '19-23', '28x19']))).toEqual([
      ['1', '32-28', '19-23'],
      ['2', '28x19', ''],
    ]);
  });

  it('heads the columns and names the list with the number of moves played', () => {
    const w = list(['32-28', '19-23', '28x19']);
    expect(w.get('[data-test="moves-head-white"]').text()).toBe(ui.white);
    expect(w.get('[data-test="moves-head-black"]').text()).toBe(ui.black);
    expect(w.get('[data-test="moves"]').attributes('aria-label')).toBe('Moves, 3 played');
  });

  it('emits close from the hide button', async () => {
    const w = list(['32-28']);
    await w.get('[data-test="moves-hide"]').trigger('click');
    expect(w.emitted('close')).toHaveLength(1);
    expect(w.get('[data-test="moves-hide"]').attributes('aria-label')).toBe(ui.hide);
  });
});
