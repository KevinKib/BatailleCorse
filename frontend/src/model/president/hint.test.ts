import { describe, it, expect } from 'vitest';
import { hintText } from './hint';
import { messagesEn } from '../../locales/en';

const ui = messagesEn.presidentUi;

describe('hintText', () => {
  it.each([
    [{ code: 'nothingSelected' as const }, 'Tap the cards you want to play.'],
    [{ code: 'mixedRanks' as const }, 'Cards of one rank only.'],
    [{ code: 'mustPass' as const }, 'Nothing you hold can play: pass.'],
    [{ code: 'cannotPlay' as const }, 'Wait for your turn.'],
    [{ code: 'notPlayable' as const }, 'This play is not allowed.'],
    [{ code: 'wrongCount' as const, counts: [2] }, 'Number of cards to play: 2.'],
    [{ code: 'wrongCount' as const, counts: [1, 2] }, 'Number of cards to play: 1 or 2.'],
    [{ code: 'wrongCount' as const, counts: [1, 2, 3] }, 'Number of cards to play: 1, 2 or 3.'],
    [{ code: 'notHigher' as const, rank: '9', count: 1 }, 'Play a card higher than 9.'],
    [{ code: 'notHigher' as const, rank: 'JACK', count: 2 }, 'Play 2 cards higher than Jack.'],
    [{ code: 'matchOnly' as const, rank: 'JACK', count: 1 }, 'Play a Jack or nothing.'],
    [{ code: 'matchOnly' as const, rank: 'JACK', count: 2 }, 'Play 2 Jacks or nothing.'],
    [{ code: 'matchOnly' as const, rank: '9', count: 3 }, 'Play 3 9s or nothing.'],
  ])('givenTheRefusal_%j_thenTheLineIs_%s', (refusal, expected) => {
    expect(hintText(refusal, ui)).toBe(expected);
  });

  it('givenNoRefusal_thenThereIsNoHint', () => {
    expect(hintText(null, ui)).toBe('');
  });
});
