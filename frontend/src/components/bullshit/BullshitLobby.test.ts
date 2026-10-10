import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
import BullshitLobby from './BullshitLobby.vue';
import { messagesEn } from '../../locales/en';
import type { LobbyView } from '../../model/bullshit/LobbyView';

const ui = messagesEn.bullshitUi.lobby;

function lobby(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false,
    gameId: 'g1',
    players: [
      { seat: 0, name: 'Alice', joined: true, bot: false },
      { seat: 1, name: 'Botty', joined: true, bot: true },
      { seat: 2, name: null, joined: false, bot: false },
    ],
    hostSeat: 0,
    mySeat: 0,
    minPlayers: 2,
    maxPlayers: 6,
    canStart: true,
    options: { claimMode: 'suit' },
    ...overrides,
  };
}

function mountLobby(props: Partial<{ lobby: LobbyView; isHost: boolean; canStart: boolean }> = {}) {
  return mount(BullshitLobby, {
    props: { lobby: lobby(), joinLink: 'http://x/games/bullshit/join/g1', isHost: true, canStart: true, ...props },
    global: { plugins: [PrimeVue] },
  });
}

describe('BullshitLobby', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it('lists joined players only, marking me, the host and bots', () => {
    const wrapper = mountLobby();
    const rows = wrapper.findAll('.players li');
    expect(rows).toHaveLength(2);
    expect(rows[0].text()).toContain('Alice');
    expect(rows[0].find('[data-test="badge-you"]').text()).toBe(ui.youBadge);
    expect(rows[0].text()).toContain(ui.hostBadge);
    expect(rows[1].find('[data-test="badge-bot"]').text()).toBe(ui.botBadge);
    expect(rows[1].find('[data-test="badge-you"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="player-count"]').text()).toBe('2 / 6 players');
  });

  it('falls back to the numbered seat label when a player has no name', () => {
    const wrapper = mountLobby({ lobby: lobby({ players: [{ seat: 0, name: null, joined: true, bot: false }] }) });
    expect(wrapper.get('.players li').text()).toContain('Player 1');
  });

  it('shows the chosen claim mode', () => {
    const wrapper = mountLobby();
    expect(wrapper.get('[data-test="claim-mode"]').text()).toContain(messagesEn.bullshitUi.start.claimModes.suit);
  });

  it('omits the claim mode row when the lobby carries none', () => {
    const wrapper = mountLobby({ lobby: lobby({ options: undefined }) });
    expect(wrapper.find('[data-test="claim-mode"]').exists()).toBe(false);
  });

  it('shows the invite link and gives Copied! feedback after copying', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    const wrapper = mountLobby();
    expect((wrapper.get('[data-test="invite-link"]').element as HTMLInputElement).value).toBe('http://x/games/bullshit/join/g1');
    expect(wrapper.get('[data-test="copied"]').text()).toBe('');

    await wrapper.get('[data-test="copy-link"]').trigger('click');
    await vi.advanceTimersByTimeAsync(0);
    expect(writeText).toHaveBeenCalledWith('http://x/games/bullshit/join/g1');
    expect(wrapper.get('[data-test="copied"]').text()).toBe(ui.copied);

    await vi.advanceTimersByTimeAsync(1600);
    expect(wrapper.get('[data-test="copied"]').text()).toBe('');
  });

  it('lets the host start only when allowed, with a hint otherwise', async () => {
    const blocked = mountLobby({
      canStart: false,
      lobby: lobby({ canStart: false, players: [{ seat: 0, name: 'Alice', joined: true, bot: false }] }),
    });
    expect((blocked.get('[data-test="start"]').element as HTMLButtonElement).disabled).toBe(true);
    expect(blocked.get('[data-test="start-hint"]').text()).toBe('Waiting for 1 more player to start…');

    const ready = mountLobby();
    expect((ready.get('[data-test="start"]').element as HTMLButtonElement).disabled).toBe(false);
    expect(ready.find('[data-test="start-hint"]').exists()).toBe(false);
    await ready.get('[data-test="start"]').trigger('click');
    expect(ready.emitted('start')).toHaveLength(1);
  });

  it('shows guests a waiting message instead of the Start button', () => {
    const wrapper = mountLobby({ isHost: false, canStart: false });
    expect(wrapper.find('[data-test="start"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="waiting-host"]').text()).toBe(ui.waitingForHost);
  });
});
