import { describe, it, expect } from 'vitest';
import { createKoboMatchCoach, MATCH_COACH_STORAGE_KEY } from './useKoboMatchCoach';

function memoryStorage(initial: Record<string, string> = {}) {
  const data = { ...initial };
  return {
    getItem: (k: string) => data[k] ?? null,
    setItem: (k: string, v: string) => { data[k] = v; },
    data,
  };
}

describe('createKoboMatchCoach', () => {
  it('showsTheCueTheFirstTime', () => {
    expect(createKoboMatchCoach(memoryStorage()).seen.value).toBe(false);
  });

  it('remembersTheDismissalInStorage', () => {
    const storage = memoryStorage();
    const coach = createKoboMatchCoach(storage);
    coach.dismiss();
    expect(coach.seen.value).toBe(true);
    expect(storage.data[MATCH_COACH_STORAGE_KEY]).toBe('seen');
    expect(createKoboMatchCoach(storage).seen.value).toBe(true);
  });

  it('dismissingTwiceIsHarmless', () => {
    const coach = createKoboMatchCoach(memoryStorage());
    coach.dismiss();
    coach.dismiss();
    expect(coach.seen.value).toBe(true);
  });

  it('givenAStorageThatThrows_thenTheCueStillWorksForThisVisit', () => {
    const broken = {
      getItem: () => { throw new Error('blocked'); },
      setItem: () => { throw new Error('blocked'); },
    };
    const coach = createKoboMatchCoach(broken);
    expect(coach.seen.value).toBe(false);
    coach.dismiss();
    expect(coach.seen.value).toBe(true);
  });

  it('givenNoStorage_thenTheCueShowsAndCanBeDismissed', () => {
    const coach = createKoboMatchCoach(null);
    expect(coach.seen.value).toBe(false);
    coach.dismiss();
    expect(coach.seen.value).toBe(true);
  });
});
