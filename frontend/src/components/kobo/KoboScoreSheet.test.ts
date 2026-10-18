import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import KoboScoreSheet from './KoboScoreSheet.vue';
import type { KoboRound, KoboState } from '../../model/kobo/KoboState';
import { buildKoboPlayer, buildKoboState } from '../../model/fixtures/kobo';

const NAMES = ['Alice', 'Bob', 'Carol', 'Dan'];

function lastRound(overrides: Partial<KoboRound> = {}): KoboRound {
  return {
    players: [
      { seat: 0, handValue: 2, roundPoints: 0, totalBefore: 10, totalAfter: 10 },
      { seat: 1, handValue: 30, roundPoints: 30, totalBefore: 5, totalAfter: 35 },
    ],
    announcer: 0, announcerWon: true, giftTo: null,
    ...overrides,
  };
}

function mountSheet(game: Partial<KoboState> = {}, props: Record<string, unknown> = {}) {
  const state = buildKoboState({
    phase: 'GIFT', revealed: true, round: 3, announcer: 0, currentSeat: 0, lastRound: lastRound(),
    availableActions: ['GIVE_TEN'],
    players: [buildKoboPlayer(0), buildKoboPlayer(1), buildKoboPlayer(2)],
    ...game,
  });
  return mount(KoboScoreSheet, {
    props: { game: state, mySeat: 0, nameOf: (seat: number) => NAMES[seat], busy: false, ...props },
  });
}

describe('KoboScoreSheet gift', () => {
  it('givenIWonMyKobo_thenTheKoboBadgeTheTitleAndTheSubtitleAreForMe', () => {
    const wrapper = mountSheet();
    expect(wrapper.get('[data-test="sheet-badge"]').text()).toBe('Kobo!');
    expect(wrapper.get('[data-test="sheet-title"]').text()).toBe('You had the fewest points');
    expect(wrapper.get('[data-test="sheet-subtitle"]').text()).toBe('Choose who takes 10 points');
    expect(wrapper.get('[data-test="score-sheet"]').attributes('aria-label')).toBe('Scores of round 3');
  });

  it('givenIWonMyKobo_thenOneGiveButtonPerOtherPlayerAndNoneForMe', () => {
    const wrapper = mountSheet();
    expect(wrapper.findAll('[data-test^="give-"]').map(b => b.text()))
      .toEqual(['Give 10 to Bob', 'Give 10 to Carol']);
    expect(wrapper.find('[data-test="give-0"]').exists()).toBe(false);
  });

  it('givenAGiveButtonIsClicked_thenItEmitsTheReceiversSeat', async () => {
    const wrapper = mountSheet();
    await wrapper.get('[data-test="give-2"]').trigger('click');
    expect(wrapper.emitted('give')).toEqual([[2]]);
  });

  it('givenACommandIsInFlight_thenTheGiveButtonsAreDisabled', () => {
    const wrapper = mountSheet({}, { busy: true });
    expect(wrapper.get('[data-test="give-1"]').attributes('disabled')).toBeDefined();
  });

  it('givenAnotherPlayerWonTheirKobo_thenNoGiveButtonAndTheSubtitleSaysWeWait', () => {
    const wrapper = mountSheet({
      announcer: 1, currentSeat: 1, availableActions: [],
      lastRound: lastRound({ announcer: 1 }),
    });
    expect(wrapper.get('[data-test="sheet-title"]').text()).toBe('Bob had the fewest points');
    expect(wrapper.get('[data-test="sheet-subtitle"]').text()).toBe('Waiting for Bob to give 10 points');
    expect(wrapper.find('[data-test^="give-"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="action-next-round"]').exists()).toBe(false);
  });
});

describe('KoboScoreSheet notice', () => {
  it('givenARefusedCommand_thenTheNoticeShowsAsAnAlert', () => {
    const wrapper = mountSheet({}, { notice: 'moveImpossible' });
    expect(wrapper.get('[data-test="notice"]').attributes('role')).toBe('alert');
    expect(wrapper.get('[data-test="notice"]').text()).toBe('That move is no longer possible');
  });

  it('givenNoNotice_thenNoAlert', () => {
    expect(mountSheet().find('[data-test="notice"]').exists()).toBe(false);
  });
});

describe('KoboScoreSheet round over', () => {
  const over = (overrides: Partial<KoboState> = {}) => mountSheet({
    phase: 'ROUND_OVER', availableActions: ['READY'], readySeats: [], ...overrides,
  });

  it('givenTheGiftWasGiven_thenTheSubtitleSaysWhoGotIt', () => {
    const wrapper = over({ lastRound: lastRound({ giftTo: 1 }) });
    expect(wrapper.get('[data-test="sheet-subtitle"]').text()).toBe('You gave 10 points to Bob');
  });

  it('givenAnotherPlayerGaveMe10_thenTheSubtitleSaysYou', () => {
    const wrapper = over({ announcer: 1, lastRound: lastRound({ announcer: 1, giftTo: 0 }) });
    expect(wrapper.get('[data-test="sheet-subtitle"]').text()).toBe('Bob gave 10 points to you');
  });

  it('givenTheGiftMarksTheReceiverRow', () => {
    const wrapper = over({ lastRound: lastRound({ giftTo: 1 }) });
    expect(wrapper.get('[data-test="score-row-1"] [data-test="pill-gift"]').text()).toBe('+10');
  });

  it('givenAFailedKobo_thenTheVerdictIsTheAnnouncersAndThereIsNoGift', () => {
    const wrapper = over({ announcer: 1, lastRound: lastRound({ announcer: 1, announcerWon: false }) });
    expect(wrapper.get('[data-test="sheet-title"]').text()).toBe("Bob's Kobo failed: someone had fewer points");
    expect(wrapper.get('[data-test="sheet-subtitle"]').text()).toBe('The announcer takes a penalty on top of their hand');
    expect(wrapper.find('[data-test="pill-gift"]').exists()).toBe(false);
  });

  it('givenMyKoboFailed_thenTheTitleSpeaksToMe', () => {
    const wrapper = over({ lastRound: lastRound({ announcerWon: false }) });
    expect(wrapper.get('[data-test="sheet-title"]').text()).toBe('Your Kobo failed: someone had fewer points');
  });

  it('givenNobodyCalledKobo_thenNoBadgeAndAPlainTitle', () => {
    const wrapper = over({ announcer: null, lastRound: lastRound({ announcer: null, announcerWon: false }) });
    expect(wrapper.find('[data-test="sheet-badge"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="sheet-title"]').text()).toBe('Round over');
  });

  it('givenIAmNotReady_thenNextRoundEmitsReady', async () => {
    const wrapper = over();
    expect(wrapper.get('[data-test="action-next-round"]').text()).toBe('Next round');
    await wrapper.get('[data-test="action-next-round"]').trigger('click');
    expect(wrapper.emitted('next-round')).toHaveLength(1);
  });

  it('givenTheReadyTicks_thenTheyAreOnTheRowsOfTheReadyPlayers', () => {
    const wrapper = over({ readySeats: [1] });
    expect(wrapper.find('[data-test="ready-1"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="ready-0"]').exists()).toBe(false);
  });

  it('givenIAmReady_thenNoButtonAndTheSheetSaysWeWaitForTheOthers', () => {
    const wrapper = over({ availableActions: [], readySeats: [0] });
    expect(wrapper.find('[data-test="action-next-round"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="sheet-wait"]').text()).toBe('Waiting for the other players');
    expect(wrapper.find('[data-test="ready-0"]').exists()).toBe(true);
  });

  it('givenGiftPhase_thenNoReadyTicksAreShown', () => {
    expect(mountSheet().find('[data-test^="ready-"]').exists()).toBe(false);
  });
});

// happy-dom does not apply scoped CSS, so the motion contract is read from the stylesheet itself (the real
// computed values are checked in Cypress, kobo-animations).
describe('KoboScoreSheet appearance animation', () => {
  const style = async () => {
    const source = (await import('./KoboScoreSheet.vue?raw')).default as string;
    return source.slice(source.indexOf('<style'));
  };

  it('risesFromBelowWithASoftFadeInAboutAQuarterOfASecond', async () => {
    const css = await style();
    expect(css.match(/\.sheet \{[^}]*\}/)![0])
      .toMatch(/animation:\s*sheet-rise 0\.25s cubic-bezier\(\.2, ?\.8, ?\.2, ?1\) backwards/);
    expect(css).toMatch(/@keyframes sheet-rise \{\s*from \{ transform: translateY\(16px\); opacity: 0; \}/);
  });

  it('isCutByReducedMotion', async () => {
    expect(await style()).toMatch(/@media \(prefers-reduced-motion: reduce\) \{\s*\.sheet \{ animation: none; \}/);
  });
});
