import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ref } from 'vue';
import { useSeatPresence, SEAT_LIFECYCLE_EVENT, FORFEIT_NOTICE_HOLD_MS } from './useSeatPresence';

describe('useSeatPresence', () => {
  beforeEach(() => vi.useFakeTimers({ now: 1_000_000 }));
  afterEach(() => vi.useRealTimers());

  it('givenDisconnectEvent_thenRecordsSeatDeadline', () => {
    const p = useSeatPresence();
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED,
      { disconnectedSeat: 2, deadlineEpochMs: 1_060_000 });
    expect(p.disconnections.value).toEqual({ 2: 1_060_000 });
  });

  it('givenReconnectEvent_thenRemovesSeat', () => {
    const p = useSeatPresence();
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED,
      { disconnectedSeat: 2, deadlineEpochMs: 1_060_000 });
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.OPPONENT_RECONNECTED, { reconnectedSeat: 2 });
    expect(p.disconnections.value).toEqual({});
  });

  it('givenForfeitEvent_thenRemovesSeatAndShowsNoticeThatAutoClears', () => {
    const p = useSeatPresence();
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED,
      { disconnectedSeat: 3, deadlineEpochMs: 1_060_000 });
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.FORFEIT, { loserSeat: 3 });
    expect(p.disconnections.value).toEqual({});
    expect(p.forfeitNotice.value).toEqual({ seat: 3 });

    vi.advanceTimersByTime(FORFEIT_NOTICE_HOLD_MS);
    expect(p.forfeitNotice.value).toBeNull();
  });

  it('givenPresentSeatsGetter_thenLiveDisconnectionsDropsAbsentSeats', () => {
    const present = ref<number[]>([0, 1]);
    const p = useSeatPresence({ presentSeats: () => present.value });
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED,
      { disconnectedSeat: 1, deadlineEpochMs: 1_060_000 });
    expect(p.liveDisconnections.value).toEqual({ 1: 1_060_000 });

    present.value = [0];          // seat 1 left the table
    expect(p.liveDisconnections.value).toEqual({});
  });

  it('givenUnknownEvent_thenNoOp', () => {
    const p = useSeatPresence();
    p.applyPresenceEvent('CALL_BULLSHIT', { whatever: true });
    expect(p.disconnections.value).toEqual({});
    expect(p.forfeitNotice.value).toBeNull();
  });

  it('givenReset_thenClearsStateAndTimer', () => {
    const p = useSeatPresence();
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED,
      { disconnectedSeat: 2, deadlineEpochMs: 1_060_000 });
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.FORFEIT, { loserSeat: 5 });
    p.reset();
    expect(p.disconnections.value).toEqual({});
    expect(p.forfeitNotice.value).toBeNull();
    vi.advanceTimersByTime(FORFEIT_NOTICE_HOLD_MS);   // timer must not resurrect anything
    expect(p.forfeitNotice.value).toBeNull();
  });
});
