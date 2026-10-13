import { describe, it, expect, vi, afterEach } from 'vitest';
import { effectScope } from 'vue';
import { useNarrowScreen } from './useNarrowScreen';

afterEach(() => vi.unstubAllGlobals());

function stubMatchMedia(matches: boolean) {
  const listeners: Array<(e: { matches: boolean }) => void> = [];
  const query = {
    matches,
    addEventListener: (_: string, fn: (e: { matches: boolean }) => void) => listeners.push(fn),
    removeEventListener: vi.fn(),
  };
  vi.stubGlobal('matchMedia', vi.fn(() => query));
  return { query, fire: (m: boolean) => listeners.forEach(fn => fn({ matches: m })) };
}

describe('useNarrowScreen', () => {
  it('givenANarrowViewport_thenIsNarrow', () => {
    stubMatchMedia(true);
    const scope = effectScope();
    const narrow = scope.run(() => useNarrowScreen())!;
    expect(narrow.value).toBe(true);
    scope.stop();
  });

  it('givenTheViewportChanges_thenItFollows', () => {
    const media = stubMatchMedia(false);
    const scope = effectScope();
    const narrow = scope.run(() => useNarrowScreen())!;
    expect(narrow.value).toBe(false);
    media.fire(true);
    expect(narrow.value).toBe(true);
    scope.stop();
  });

  it('givenNoMatchMedia_thenIsNotNarrow', () => {
    vi.stubGlobal('matchMedia', undefined);
    const scope = effectScope();
    expect(scope.run(() => useNarrowScreen())!.value).toBe(false);
    scope.stop();
  });
});
