import { describe, it, expect } from 'vitest';
import { format } from '../format';
import { presidentUi } from './cards';
import { threeMenMorrisUi } from './boards';
import { draughtsUi } from './draughts';

// French agreement of the texts built from parts: a part that may be feminine (Dame, dame blanche) or a count that
// may be 0 or 1 must not force a masculine article, a masculine past participle or a plural.
describe('French texts built from parts', () => {
  it('President: the "match only" hint reads well for a feminine rank', () => {
    const text = format(presidentUi.hint.matchOnly.one, { rank: 'Dame' });
    expect(text).not.toMatch(/\bun Dame\b/);
    expect(text).toContain('Dame');
  });

  it('Six Men’s Morris: the counts tag reads well with 0 or 1 piece lost', () => {
    const text = format(threeMenMorrisUi.table.countsTag, { name: 'Alice', colour: 'blancs', board: 5, hand: 0, lost: 1 });
    expect(text).not.toMatch(/\b1 perdus\b/);
    expect(text).toContain('1');
  });

  it('Draughts: the square states agree with a dame as well as a pion', () => {
    for (const piece of [draughtsUi.board.whiteKing, draughtsUi.board.whiteMan]) {
      const state = [piece, draughtsUi.board.selected].join(', ');
      expect(state).not.toMatch(/dame blanche, sélectionné$/);
      expect(state).not.toMatch(/pion blanc, sélectionnée$/);
    }
    expect([draughtsUi.board.blackKing, draughtsUi.board.taken].join(', ')).not.toMatch(/dame noire, serait pris$/);
  });
});
