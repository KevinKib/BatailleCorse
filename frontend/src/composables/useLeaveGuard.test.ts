import { describe, it, expect, vi, beforeEach } from 'vitest';
import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { messagesEn } from '../locales/en';

let leaveHook: (() => unknown) | undefined;
vi.mock('vue-router', () => ({
  onBeforeRouteLeave: (fn: () => unknown) => { leaveHook = fn; },
}));

import { useLeaveGuard } from './useLeaveGuard';

function stubConfirm(answer: boolean) {
  const confirm = vi.fn().mockReturnValue(answer);
  vi.stubGlobal('confirm', confirm);
  return confirm;
}

function mountGuard(inProgress: boolean, forfeit = vi.fn()) {
  mount(defineComponent({ setup() { useLeaveGuard({ isInProgress: () => inProgress, forfeit }); return () => h('div'); } }));
  return forfeit;
}

describe('useLeaveGuard', () => {
  beforeEach(() => { leaveHook = undefined; vi.unstubAllGlobals(); });

  it('givenAGameInProgress_thenTheConfirmationTextComesFromTheLocales', () => {
    const confirm = stubConfirm(true);
    const forfeit = mountGuard(true);
    expect(leaveHook!()).toBe(true);
    expect(confirm).toHaveBeenCalledWith(messagesEn.leaveGuard.confirm);
    expect(forfeit).toHaveBeenCalledOnce();
  });

  it('givenTheLeaveIsRefused_thenNothingIsForfeited', () => {
    stubConfirm(false);
    const forfeit = mountGuard(true);
    expect(leaveHook!()).toBe(false);
    expect(forfeit).not.toHaveBeenCalled();
  });

  it('givenTheSocketIsDown_whenTheLeaveIsConfirmed_thenThePlayerStillLeaves', () => {
    stubConfirm(true);
    // Publishing throws while the socket reconnects: the server timer will concede the seat instead.
    const forfeit = mountGuard(true, vi.fn(() => { throw new TypeError('There is no underlying STOMP connection'); }));
    expect(leaveHook!()).toBe(true);
    expect(forfeit).toHaveBeenCalledOnce();
  });

  it('givenNoGameInProgress_thenLeavingIsFree', () => {
    const confirm = stubConfirm(true);
    mountGuard(false);
    expect(leaveHook!()).toBe(true);
    expect(confirm).not.toHaveBeenCalled();
  });
});
