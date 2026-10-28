import { describe, expect, it } from 'vitest';
import { messagesEn } from '../../locales/en';
import { seatNameWithBotLevel } from './botLevel';

const labels = { player: 'Player {n}', bot: 'Bot {n}' };
const name = (
  players: { seat: number; name?: string | null; bot?: boolean }[], seat: number, levels: Record<number, string> = {},
) => seatNameWithBotLevel(messagesEn, 'options.mangola.level', '{name} ({level})', players, seat, levels, labels);

describe('seatNameWithBotLevel', () => {
  it('callsAHumanWithoutANamePlayerN', () => {
    expect(name([{ seat: 0, name: null }, { seat: 1, name: 'Bob' }], 0)).toBe('Player 1');
  });

  it('keepsTheNameAHumanTyped', () => {
    expect(name([{ seat: 0, name: null }, { seat: 1, name: 'Bob' }], 1)).toBe('Bob');
  });

  it('callsAComputerSeatBotNWhateverTheServerSent', () => {
    expect(name([{ seat: 0, name: 'Alice' }, { seat: 1, name: null, bot: true }], 1)).toBe('Bot 1');
  });

  it('addsTheLevelOfAComputerSeat', () => {
    expect(name([{ seat: 0, name: 'Alice' }, { seat: 1, name: null, bot: true }], 1, { 1: 'expert' })).toBe('Bot 1 (Expert)');
  });

  it('knowsAComputerSeatFromItsLevelWhenTheViewHasNoBotFlag', () => {
    expect(name([{ seat: 0, name: 'Alice' }, { seat: 1, name: null }], 1, { 1: 'greedy' })).toBe('Bot 1 (Medium)');
  });
});
