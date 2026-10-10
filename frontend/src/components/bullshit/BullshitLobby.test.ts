import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
import { createPinia, setActivePinia } from 'pinia';
import BullshitLobby from './BullshitLobby.vue';
import { useBullshitStore } from '../../state/Bullshit.store';
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
    global: { plugins: [PrimeVue, createPinia()] },
  });
}

describe('BullshitLobby', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.useFakeTimers(); });
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

  it('shows the chosen deck size and omits it when the lobby carries none', () => {
    const withDeck = mountLobby({ lobby: lobby({ options: { claimMode: 'rank', deckSize: '32' } }) });
    expect(withDeck.get('[data-test="deck-size"]').text()).toContain(messagesEn.bullshitUi.start.deckSizes['32']);

    const without = mountLobby({ lobby: lobby({ options: { claimMode: 'rank' } }) });
    expect(without.find('[data-test="deck-size"]').exists()).toBe(false);
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

  describe('bots', () => {
    const humanAboveBot = lobby({
      players: [
        { seat: 0, name: 'Alice', joined: true, bot: false },
        { seat: 1, name: 'Bot 1', joined: true, bot: true },
        { seat: 2, name: 'Carol', joined: true, bot: false },
        { seat: 3, name: 'Bot 2', joined: true, bot: true },
      ],
      removableBotSeats: [3],
    });

    it('labels bots "Bot N" from the flag, whatever name the server stored', () => {
      const wrapper = mountLobby({ lobby: lobby({ players: [
        { seat: 0, name: 'Alice', joined: true, bot: false },
        { seat: 1, name: 'Roboto', joined: true, bot: true },
        { seat: 2, name: 'Roboto', joined: true, bot: true },
      ] }) });
      const rows = wrapper.findAll('.players li');
      expect(rows[1].text()).toContain('Bot 1');
      expect(rows[2].text()).toContain('Bot 2');
      expect(rows[1].text()).not.toContain('Roboto');
    });

    it('counts bots in the player count and the waiting hint', () => {
      const wrapper = mountLobby({ canStart: false, lobby: lobby({ canStart: false, minPlayers: 3 }) });
      expect(wrapper.get('[data-test="player-count"]').text()).toBe('2 / 6 players');
      expect(wrapper.get('[data-test="start-hint"]').text()).toBe('Waiting for 1 more player to start…');
    });

    it('lets the host add a bot, and disables the button when the lobby is full', async () => {
      const wrapper = mountLobby();
      const store = useBullshitStore();
      const spy = vi.spyOn(store, 'addBot').mockImplementation(() => {});
      const button = wrapper.get('[data-test="add-bot"]');
      expect(button.text()).toContain(ui.addBot);
      expect((button.element as HTMLButtonElement).disabled).toBe(false);
      await button.trigger('click');
      expect(spy).toHaveBeenCalledTimes(1);

      const full = mountLobby({ lobby: lobby({ maxPlayers: 2 }) });
      expect((full.get('[data-test="add-bot"]').element as HTMLButtonElement).disabled).toBe(true);
    });

    it('enables the remove button only on removable bot seats and removes that seat', async () => {
      const wrapper = mountLobby({ lobby: humanAboveBot });
      const store = useBullshitStore();
      const spy = vi.spyOn(store, 'removeBot').mockImplementation(() => {});
      expect((wrapper.get('[data-test="remove-bot-1"]').element as HTMLButtonElement).disabled).toBe(true);
      const enabled = wrapper.get('[data-test="remove-bot-3"]');
      expect((enabled.element as HTMLButtonElement).disabled).toBe(false);
      expect(enabled.attributes('aria-label')).toBe('Remove Bot 2');
      await enabled.trigger('click');
      expect(spy).toHaveBeenCalledWith(3);
      expect(wrapper.find('[data-test="remove-bot-0"]').exists()).toBe(false);
      expect(wrapper.find('[data-test="remove-bot-2"]').exists()).toBe(false);
    });

    it('shows guests the bots without any control', () => {
      const wrapper = mountLobby({ isHost: false, canStart: false, lobby: { ...humanAboveBot, mySeat: 2, removableBotSeats: undefined } });
      expect(wrapper.findAll('[data-test="badge-bot"]')).toHaveLength(2);
      expect(wrapper.find('[data-test="add-bot"]').exists()).toBe(false);
      expect(wrapper.find('[data-test^="remove-bot-"]').exists()).toBe(false);
    });

    it('shows a visible error to the host when the server refused the bot change', () => {
      const wrapper = mountLobby();
      useBullshitStore().applyEvent({ type: 'error', eventType: 'JOIN', message: 'Room is full' });
      return wrapper.vm.$nextTick().then(() => {
        expect(wrapper.get('[data-test="bot-error"]').text()).toBe(ui.botActionFailed);
      });
    });
  });
});
