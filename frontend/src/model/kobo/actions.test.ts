import { describe, it, expect } from 'vitest';
import { barButtons } from './actions';
import { buildKoboState } from '../fixtures/kobo';
import type { KoboAction, KoboPhase } from './KoboState';

const buttons = (phase: KoboPhase, availableActions: KoboAction[]) =>
  barButtons(buildKoboState({ phase, availableActions }));

describe('barButtons', () => {
  it('givenMemorisingAndReadyListed_thenOffersIHaveMemorised', () => {
    expect(buttons('MEMORISING', ['READY'])).toEqual(['ready']);
  });

  it('givenReadyAlreadySent_thenNoButton', () => {
    expect(buttons('MEMORISING', [])).toEqual([]);
  });

  it('givenReadyListedBetweenRounds_thenNotOffered_theNextRoundButtonIsForTheRoundEndStep', () => {
    expect(buttons('ROUND_OVER', ['READY'])).toEqual([]);
  });

  it.each<[KoboPhase, KoboAction[], string[]]>([
    ['DRAW', ['DRAW'], ['draw']],
    ['DECISION', ['SWAP_DRAWN', 'DISCARD_DRAWN'], ['discard']],
    ['POWER', ['PEEK', 'SKIP_POWER'], ['skip']],
    ['POWER', ['BLIND_SWAP', 'SKIP_POWER'], ['skip']],
    ['KING_DECISION', ['KING_SWAP', 'SKIP_POWER'], ['skip']],
    ['TURN_END', ['END_TURN', 'ANNOUNCE_KOBO'], ['endTurn', 'announce']],
    ['TURN_END', ['END_TURN'], ['endTurn']],
  ])('given%sWith%j_thenButtons%j', (phase, actions, expected) => {
    expect(buttons(phase, actions)).toEqual(expected);
  });

  it('givenMatchAndGiftListed_thenTheyAreNotButtonsOfThisBar_theyBelongToTheNextSteps', () => {
    expect(buttons('DRAW', ['MATCH_DISCARD'])).toEqual([]);
    expect(buttons('GIFT', ['GIVE_TEN'])).toEqual([]);
  });

  it('givenNotMyTurn_thenNothingListedMeansNoButton', () => {
    expect(buttons('DECISION', [])).toEqual([]);
  });
});
