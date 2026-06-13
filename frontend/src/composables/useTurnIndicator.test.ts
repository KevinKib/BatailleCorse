import { describe, it, expect } from 'vitest';
import { ref, nextTick } from 'vue';
import { useTurnIndicator, type TurnState } from './useTurnIndicator';

function setup(opts?: { canSend?: (i: number) => boolean }) {
  const canSend = opts?.canSend ?? (() => false);
  const state = ref<TurnState | undefined>({ canSend });
  const waiting = ref(false);
  const ended = ref(false);
  const api = useTurnIndicator({
    state: () => state.value,
    myPlayerIndex: () => 0,
    opponentIndex: () => 1,
    isWaiting: () => waiting.value,
    showEndOverlay: () => ended.value,
  });
  return { state, waiting, ended, ...api };
}

describe('useTurnIndicator', () => {
  it('givenMyTurn_thenShowMyTurnTrueOpponentFalse', () => {
    const { showMyTurn, showOpponentTurn, cancel } = setup({ canSend: (i) => i === 0 });
    expect(showMyTurn.value).toBe(true);
    expect(showOpponentTurn.value).toBe(false);
    cancel();
  });

  it('givenWaitingOverlay_thenCuesSuppressed', () => {
    const ctx = setup({ canSend: () => true });
    ctx.waiting.value = true;
    expect(ctx.showMyTurn.value).toBe(false);
    expect(ctx.showOpponentTurn.value).toBe(false);
    ctx.cancel();
  });

  it('givenEndOverlay_thenCuesSuppressed', () => {
    const ctx = setup({ canSend: () => true });
    ctx.ended.value = true;
    expect(ctx.showMyTurn.value).toBe(false);
    ctx.cancel();
  });

  it('givenFirstTimeMyTurn_thenHintShows_andDismissesForeverAfterIPlay', async () => {
    const canSend = ref(true);
    const state = ref<TurnState | undefined>({ canSend: (i) => i === 0 && canSend.value });
    const { showMyTurn, showFirstTurnHint, cancel } = useTurnIndicator({
      state: () => state.value,
      myPlayerIndex: () => 0,
      opponentIndex: () => 1,
      isWaiting: () => false,
      showEndOverlay: () => false,
    });

    await nextTick();
    expect(showMyTurn.value).toBe(true);
    expect(showFirstTurnHint.value).toBe(true);

    canSend.value = false;        // I played; no longer my turn
    await nextTick();
    expect(showFirstTurnHint.value).toBe(false);

    canSend.value = true;         // my turn again later
    await nextTick();
    expect(showFirstTurnHint.value).toBe(false); // never returns
    cancel();
  });
});
