import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { nextTick } from 'vue';
import { useCardAnimation } from './useCardAnimation';

const rect = (left: number, top: number, width: number, height: number) =>
  ({ left, top, width, height, right: left + width, bottom: top + height }) as DOMRect;

function setup() {
  return useCardAnimation(() => null, () => null, () => null, () => null);
}

// A ghost flies from a source rect to a destination rect. Animating `width`/`height`
// forces a layout and a repaint on every frame; the flight must be a pure transform
// (translate + scale) so the browser can run it on the compositor.
describe('useCardAnimation ghost flight', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('givenSend_thenGhostStartsAtSourceBox', () => {
    const { ghostCard, animateSend } = setup();
    animateSend(rect(10, 20, 90, 130), rect(200, 300, 125, 180), undefined);
    expect(ghostCard).toMatchObject({ x: 10, y: 20, width: 90, height: 130, scaleX: 1, scaleY: 1, transitioning: false });
  });

  it('givenSend_whenTransitionStarts_thenOnlyTranslateAndScaleChange', async () => {
    const { ghostCard, animateSend } = setup();
    animateSend(rect(10, 20, 90, 130), rect(200, 300, 125, 180), undefined);
    await nextTick();
    await nextTick();
    expect(ghostCard.transitioning).toBe(true);
    expect(ghostCard.x).toBe(200);
    expect(ghostCard.y).toBe(300);
    // The layout box never changes: the size change is a scale factor.
    expect(ghostCard.width).toBe(90);
    expect(ghostCard.height).toBe(130);
    expect(ghostCard.scaleX).toBeCloseTo(125 / 90);
    expect(ghostCard.scaleY).toBeCloseTo(180 / 130);
  });

  it('givenPileToWinner_whenTransitionStarts_thenSlapGhostsKeepTheirLayoutBox', async () => {
    const { slapGhosts, animatePileToWinner } = setup();
    void animatePileToWinner(rect(100, 100, 125, 180), rect(10, 500, 90, 130),
      [{ rank: '2', suit: 'club' } as never]);
    vi.advanceTimersByTime(1);
    await nextTick();
    await nextTick();
    const ghost = slapGhosts[0];
    expect(ghost.transitioning).toBe(true);
    expect(ghost.width).toBe(125);
    expect(ghost.height).toBe(180);
    expect(ghost.scaleX).toBeCloseTo(90 / 125);
    expect(ghost.scaleY).toBeCloseTo(130 / 180);
  });
});
