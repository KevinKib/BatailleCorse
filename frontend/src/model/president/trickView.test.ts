import { describe, expect, it } from 'vitest';
import { pileView } from './trickView';
import type { PresidentLogEntry } from './eventLog';
import { buildPresidentCard as card, buildPresidentState, buildPresidentTrick } from '../fixtures/president';

const closing = (over: Partial<PresidentLogEntry> = {}): PresidentLogEntry => ({
  id: 3, eventType: 'PLAY', action: 'PLAY', seat: 1, cards: [card('2', 'SPADE')], round: 1, outOfTurn: false,
  outForbidden: false, outPlace: null,
  closure: { winner: 1, reason: 'CUT', closer: 1, cards: [card('2', 'SPADE')], size: 3, trickCards: [card('2', 'SPADE')] },
  ...over,
});

describe('pileView', () => {
  it('givenAnEmptyTrickAndNoClosure_thenNothingOnThePile', () => {
    expect(pileView(buildPresidentState(), null)).toEqual({ play: null, closure: null, count: 0 });
  });

  it('givenPlaysOnTheTrick_thenTheTopPlayAndTheNumberOfCardsInTheTrick', () => {
    const game = buildPresidentState({
      trick: buildPresidentTrick({ plays: [{ seat: 0, cards: [card('7', 'HEART')] }, { seat: 1, cards: [card('9', 'CLUB')] }] }),
    });
    expect(pileView(game, closing())).toEqual({ play: { seat: 1, cards: [card('9', 'CLUB')] }, closure: null, count: 2 });
  });

  it('givenTheTrickJustClosed_thenTheClosingPlayStaysWithItsClosureAndTheSizeOfTheClosedTrick', () => {
    const view = pileView(buildPresidentState(), closing());
    expect(view.play).toEqual({ seat: 1, cards: [card('2', 'SPADE')] });
    expect(view.closure?.reason).toBe('CUT');
    expect(view.count).toBe(3);
  });

  it('givenTheLatestEntryClosedNothing_thenNothingOnAnEmptyPile', () => {
    expect(pileView(buildPresidentState(), closing({ closure: null })).play).toBeNull();
  });

  it('givenTheClosureIsFromAnotherRound_thenNothing', () => {
    expect(pileView(buildPresidentState({ round: 2 }), closing()).play).toBeNull();
  });

  it('givenTheRoundIsOver_thenNothing', () => {
    expect(pileView(buildPresidentState({ phase: 'ROUND_OVER' }), closing()).play).toBeNull();
  });

  it('givenAClosureWithoutCards_thenOnlyTheBadgeWithNoPlay', () => {
    const entry = closing({ closure: { winner: 0, reason: 'ALL_PASSED', closer: 0, cards: [], size: 0, trickCards: [] } });
    expect(pileView(buildPresidentState(), entry)).toEqual({ play: null, closure: entry.closure, count: 0 });
  });
});
