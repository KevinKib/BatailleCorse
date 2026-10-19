import { describe, it, expect } from 'vitest';
import {
  buildPresidentCard, buildPresidentHand, buildPresidentPlayer, buildPresidentState, CLASSIC_PRESIDENT_OPTIONS,
} from './president';

// The field lists of the backend records (PresidentDto.java), so a drifting fixture fails here.
const PRESIDENT_DTO_FIELDS = [
  'started', 'id', 'gameType', 'version', 'round', 'rounds', 'phase', 'currentSeat', 'options', 'players', 'trick',
  'exchange', 'lastRound', 'standings', 'outcome', 'hand', 'received', 'given', 'toGiveBack', 'legalPlays',
  'availableActions',
];
const PLAYER_DTO_FIELDS = ['seat', 'name', 'handSize', 'title', 'place', 'out', 'passed', 'points', 'ready', 'forbiddenFinish', 'sawExchange'];
const TRICK_DTO_FIELDS = ['leader', 'plays', 'top', 'lastPlayer', 'passed', 'constraint', 'requiredRank', 'requiredCount', 'chain'];

describe('President fixtures', () => {
  it('buildPresidentState has exactly the fields of PresidentDto', () => {
    expect(Object.keys(buildPresidentState()).sort()).toEqual([...PRESIDENT_DTO_FIELDS].sort());
  });

  it('players and the trick have exactly the fields of their DTO records', () => {
    const state = buildPresidentState();
    expect(Object.keys(state.players[0]).sort()).toEqual([...PLAYER_DTO_FIELDS].sort());
    expect(Object.keys(state.trick).sort()).toEqual([...TRICK_DTO_FIELDS].sort());
  });

  it('cards are shaped like CardDto (labels of the French deck, SUIT_RANK name)', () => {
    expect(buildPresidentCard('KING', 'SPADE')).toEqual({ rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' });
    expect(buildPresidentCard('3', 'CLUB')).toEqual({ rank: '3', suit: 'CLUB', name: 'CLUB_3' });
  });

  it('a hand is built from rank and suit pairs', () => {
    expect(buildPresidentHand(['3', 'HEART'], ['KING', 'SPADE']).map(c => c.name)).toEqual(['HEART_3', 'SPADE_KING']);
  });

  it('a player at a three-seat table is idle, with its hand size and a name', () => {
    expect(buildPresidentPlayer(2, { handSize: 5 })).toMatchObject({ seat: 2, name: 'Player 2', handSize: 5, out: false });
  });

  it('the default options are the full set of the server descriptors, defaults filled', () => {
    expect(Object.keys(CLASSIC_PRESIDENT_OPTIONS).sort())
      .toEqual(['afterPass', 'exchange', 'giveBack', 'length', 'mustPlay',
        'nextLead', 'reducedCut', 'scoring']);
    // the Classic preset is the plan's defaults
    expect(CLASSIC_PRESIDENT_OPTIONS).toMatchObject({
      mustPlay: 'off',
      reducedCut: 'off',
    });
    expect(buildPresidentState().options).toEqual(CLASSIC_PRESIDENT_OPTIONS);
  });
});
