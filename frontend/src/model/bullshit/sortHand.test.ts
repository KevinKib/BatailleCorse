import { describe, it, expect } from 'vitest';
import { sortHand } from './sortHand';
import type Card from '../Card';

const c = (rank: string, suit: string): Card => ({ rank, suit, name: `${suit}_${rank}` });

describe('sortHand', () => {
  it('orders by rank, ace low then 2..10, jack, queen, king', () => {
    const hand = [c('KING', 'HEART'), c('2', 'SPADE'), c('ACE', 'CLUB'), c('10', 'HEART'), c('JACK', 'CLUB'), c('QUEEN', 'CLUB'), c('9', 'CLUB')];
    expect(sortHand(hand).map(x => x.rank)).toEqual(['ACE', '2', '9', '10', 'JACK', 'QUEEN', 'KING']);
  });

  it('groups suits together at equal rank', () => {
    const hand = [c('5', 'CLUB'), c('5', 'SPADE'), c('5', 'HEART'), c('5', 'DIAMOND')];
    expect(sortHand(hand).map(x => x.suit)).toEqual(['SPADE', 'HEART', 'CLUB', 'DIAMOND']);
  });

  it('skips absent ranks (32-card deck) and does not mutate its input', () => {
    const hand = [c('KING', 'HEART'), c('7', 'SPADE'), c('ACE', 'CLUB')];
    const copy = [...hand];
    expect(sortHand(hand).map(x => x.rank)).toEqual(['ACE', '7', 'KING']);
    expect(hand).toEqual(copy);
  });

  it('is stable when the hand changes: the same cards keep the same relative order', () => {
    const a = [c('3', 'CLUB'), c('9', 'HEART'), c('ACE', 'SPADE'), c('3', 'HEART')];
    const before = sortHand(a);
    const after = sortHand([c('QUEEN', 'CLUB'), ...a.slice().reverse()]);
    expect(after.filter(x => a.includes(x))).toEqual(before);
  });

  it('puts unknown ranks last instead of failing', () => {
    expect(sortHand([c('JOKER', 'NONE'), c('2', 'CLUB')]).map(x => x.rank)).toEqual(['2', 'JOKER']);
  });
});
