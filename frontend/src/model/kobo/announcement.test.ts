import { describe, expect, it } from 'vitest';
import { liveAnnouncement } from './announcement';

const base = { phase: 'DRAW' as const, actor: 0, mySeat: 0, promptText: null, phaseText: 'Your turn: draw a card', roundText: 'Round 2' };

describe('liveAnnouncement', () => {
  it('givenMyTurnToDraw_thenNothingIsSaidBecauseTheTurnCueSaysIt', () => {
    expect(liveAnnouncement(base)).toBe('');
  });

  it('givenMyDecision_thenTheInstructionIsSaid', () => {
    expect(liveAnnouncement({ ...base, phase: 'DECISION', phaseText: 'Swap or discard' })).toBe('Swap or discard');
  });

  it('givenMyTurnWithAClickExpected_thenThePromptReplacesThePhase', () => {
    expect(liveAnnouncement({ ...base, phase: 'DECISION', promptText: 'Pick one of your cards' })).toBe('Pick one of your cards');
  });

  it('givenAnotherPlayersTurn_thenNothingIsSaidBecauseTheLogSaysWhatTheyDo', () => {
    expect(liveAnnouncement({ ...base, actor: 1, phaseText: 'Bob is drawing' })).toBe('');
  });

  it('givenTheMemorisingPhase_thenTheRoundIsNamedFirst', () => {
    expect(liveAnnouncement({ ...base, phase: 'MEMORISING', actor: 1, phaseText: 'Memorise your two cards' }))
      .toBe('Round 2. Memorise your two cards');
  });

  it.each(['ROUND_OVER', 'FINISHED'] as const)('given%s_thenItIsSaidToEveryone', (phase) => {
    expect(liveAnnouncement({ ...base, phase, actor: 1, phaseText: 'Round over' })).toBe('Round over');
  });

  it('givenTheGiftOfAnotherPlayer_thenNothingIsSaid', () => {
    expect(liveAnnouncement({ ...base, phase: 'GIFT', actor: 1, phaseText: 'Waiting for Bob to give 10 points' })).toBe('');
  });

  it('givenMyGift_thenItIsSaid', () => {
    expect(liveAnnouncement({ ...base, phase: 'GIFT', phaseText: 'Choose who takes 10' })).toBe('Choose who takes 10');
  });
});
