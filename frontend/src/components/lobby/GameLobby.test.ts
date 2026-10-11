import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
import GameLobby from './GameLobby.vue';
import { findGame } from '../../model/games';
import { messagesEn } from '../../locales/en';
import type { LobbyView, OptionDescriptor } from '../../model/lobby/LobbyView';

const ui = messagesEn.lobby;

const claimMode: OptionDescriptor = {
  key: 'claimMode', type: 'CHOICE', values: ['rank', 'suit'], defaultValue: 'rank', labelKey: 'options.bullshit.claimMode',
};
const deckSize: OptionDescriptor = {
  key: 'deckSize', type: 'CHOICE', values: ['32', '52'], defaultValue: '32', labelKey: 'options.bullshit.deckSize',
};
const tier: OptionDescriptor = {
  key: 'tier', type: 'CHOICE', values: ['0', '1', '2'], defaultValue: '1', labelKey: 'options.somegame.tier',
};

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
    options: { claimMode: 'suit', deckSize: '32' },
    optionDescriptors: [claimMode, deckSize],
    seatOptionDescriptors: [],
    canEditOptions: true,
    removableBotSeats: [],
    lobbyVersion: 1,
    ...overrides,
  };
}

function mountLobby(props: Partial<{ lobby: LobbyView; error: string | null }> = {}) {
  return mount(GameLobby, {
    props: { game: findGame('bullshit')!, lobby: lobby(), ...props },
    global: { plugins: [PrimeVue] },
  });
}

describe('GameLobby', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it('shows the game title and the lobby title', () => {
    const wrapper = mountLobby();
    expect(wrapper.get('h1').text()).toBe('Bullshit');
    expect(wrapper.get('[data-test="lobby-title"]').text()).toBe(ui.title);
  });

  it('lists joined players only, marking me, the host and bots', () => {
    const wrapper = mountLobby();
    const rows = wrapper.findAll('.players li');
    expect(rows).toHaveLength(2);
    expect(rows[0].find('input').element.value).toBe('Alice');
    expect(rows[0].find('[data-test="badge-you"]').text()).toBe(ui.youBadge);
    expect(rows[0].text()).toContain(ui.hostBadge);
    expect(rows[1].find('[data-test="badge-bot"]').text()).toBe(ui.botBadge);
    expect(rows[1].find('[data-test="badge-you"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="player-count"]').text()).toBe('2 / 6 players');
  });

  it('falls back to the numbered seat label when a player has no name', () => {
    const wrapper = mountLobby({ lobby: lobby({ players: [{ seat: 0, name: null, joined: true, bot: false }] }) });
    expect(wrapper.get('.players li input').element.value).toBe('Player 1');
  });

  describe('game options', () => {
    it('shows the host a radio group per descriptor, with the current value checked', () => {
      const wrapper = mountLobby();
      const group = wrapper.get('fieldset[data-test="option-claimMode"]');
      expect(group.get('legend').text()).toBe(messagesEn.options.bullshit.claimMode.label);
      expect((group.get('input[value="suit"]').element as HTMLInputElement).checked).toBe(true);
      expect((group.get('input[value="rank"]').element as HTMLInputElement).checked).toBe(false);
      expect(wrapper.findAll('fieldset[data-test^="option-"]')).toHaveLength(2);
    });

    it('emits set-option with the key and value when the host picks another value', async () => {
      const wrapper = mountLobby();
      await wrapper.get('[data-test="option-deckSize"] input[value="52"]').setValue(true);
      expect(wrapper.emitted('set-option')).toEqual([['deckSize', '52']]);
    });

    it('shows a guest the same options as read-only text, with no radio', () => {
      const wrapper = mountLobby({ lobby: lobby({ mySeat: 2, canEditOptions: false, players: [
        { seat: 0, name: 'Alice', joined: true, bot: false },
        { seat: 2, name: 'Carol', joined: true, bot: false },
      ] }) });
      expect(wrapper.find('input[type="radio"]').exists()).toBe(false);
      expect(wrapper.get('[data-test="option-claimMode"]').text()).toContain(messagesEn.options.bullshit.claimMode.suit);
      expect(wrapper.get('[data-test="option-claimMode"]').text()).toContain(messagesEn.options.bullshit.claimMode.label);
    });

    it('labels the rank mode with the range of the deck in play', () => {
      const short = mountLobby({ lobby: lobby({ options: { claimMode: 'rank', deckSize: '32' }, canEditOptions: false, mySeat: 1 }) });
      expect(short.get('[data-test="option-claimMode"]').text()).toContain('7→A');
      const full = mountLobby({ lobby: lobby({ options: { claimMode: 'rank', deckSize: '52' }, canEditOptions: false, mySeat: 1 }) });
      expect(full.get('[data-test="option-claimMode"]').text()).toContain('A→K');
    });

    it('shows the deck size', () => {
      const wrapper = mountLobby({ lobby: lobby({ canEditOptions: false, mySeat: 1 }) });
      expect(wrapper.get('[data-test="option-deckSize"]').text()).toContain(messagesEn.options.bullshit.deckSize['32']);
    });

    it('renders no section at all when the game declares no option', () => {
      const wrapper = mountLobby({ lobby: lobby({ optionDescriptors: [], options: {}, canEditOptions: false }) });
      expect(wrapper.find('[data-test="game-options"]').exists()).toBe(false);
    });

    it('falls back to the descriptor default when the lobby carries no value', () => {
      const wrapper = mountLobby({ lobby: lobby({ options: undefined, canEditOptions: false, mySeat: 1 }) });
      expect(wrapper.get('[data-test="option-claimMode"]').text()).toContain(messagesEn.options.bullshit.claimMode.rank.slice(0, 7));
    });
  });

  it('shows the invite link built from the game and gives Copied! feedback after copying', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    const wrapper = mountLobby();
    const link = (wrapper.get('[data-test="invite-link"]').element as HTMLInputElement).value;
    expect(link).toBe(`${location.origin}/games/bullshit/join/g1`);
    expect(wrapper.get('[data-test="copied"]').text()).toBe('');
    expect(wrapper.get('[data-test="copied"]').attributes('aria-live')).toBe('polite');

    await wrapper.get('[data-test="copy-link"]').trigger('click');
    await vi.advanceTimersByTimeAsync(0);
    expect(writeText).toHaveBeenCalledWith(link);
    expect(wrapper.get('[data-test="copied"]').text()).toBe(ui.copied);

    await vi.advanceTimersByTimeAsync(1600);
    expect(wrapper.get('[data-test="copied"]').text()).toBe('');
  });

  it('lets the host start only when allowed, with a hint otherwise', async () => {
    const blocked = mountLobby({
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
    const wrapper = mountLobby({ lobby: lobby({ mySeat: 1, canStart: false }) });
    expect(wrapper.find('[data-test="start"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="waiting-host"]').text()).toBe(ui.waitingForHost);
  });

  describe('my own name', () => {
    it('is editable on my row only', () => {
      const wrapper = mountLobby({ lobby: lobby({ mySeat: 2, canEditOptions: false, players: [
        { seat: 0, name: 'Alice', joined: true, bot: false },
        { seat: 1, name: 'Botty', joined: true, bot: true },
        { seat: 2, name: 'Carol', joined: true, bot: false },
      ] }) });
      expect(wrapper.findAll('input[data-test="rename"]')).toHaveLength(1);
      const rows = wrapper.findAll('.players li');
      expect(rows[0].find('input').exists()).toBe(false);
      expect(rows[0].text()).toContain('Alice');
      expect(rows[2].get('input').attributes('maxlength')).toBe('20');
      expect(rows[2].get('input').attributes('aria-label')).toBe(ui.renameLabel);
    });

    it('is committed on Enter, trimmed, and not sent when unchanged', async () => {
      const wrapper = mountLobby();
      const input = wrapper.get('input[data-test="rename"]');
      await input.setValue('  Zed ');
      await input.trigger('keydown.enter');
      expect(wrapper.emitted('rename')).toEqual([['Zed']]);
      await input.trigger('blur');
      expect(wrapper.emitted('rename')).toHaveLength(1);
    });

    it('is committed on blur', async () => {
      const wrapper = mountLobby();
      const input = wrapper.get('input[data-test="rename"]');
      await input.setValue('Zed');
      await input.trigger('blur');
      expect(wrapper.emitted('rename')).toEqual([['Zed']]);
    });

    it('is reverted on Escape, without sending anything', async () => {
      const wrapper = mountLobby();
      const input = wrapper.get('input[data-test="rename"]');
      await input.setValue('Zed');
      await input.trigger('keydown.esc');
      expect((input.element as HTMLInputElement).value).toBe('Alice');
      await input.trigger('blur');
      expect(wrapper.emitted('rename')).toBeUndefined();
    });

    it('follows the name the server confirms', async () => {
      const wrapper = mountLobby();
      await wrapper.setProps({ lobby: lobby({ lobbyVersion: 2, players: [
        { seat: 0, name: 'Zed', joined: true, bot: false },
        { seat: 1, name: 'Botty', joined: true, bot: true },
      ] }) });
      expect((wrapper.get('input[data-test="rename"]').element as HTMLInputElement).value).toBe('Zed');
    });
  });

  describe('free seats', () => {
    it('offers the host Add bot, and tells a guest the lobby waits for a player', () => {
      const host = mountLobby();
      expect(host.find('[data-test="add-bot"]').exists()).toBe(true);
      expect(host.find('[data-test="waiting-for-player"]').exists()).toBe(false);

      const guest = mountLobby({ lobby: lobby({ mySeat: 1, canEditOptions: false }) });
      expect(guest.find('[data-test="add-bot"]').exists()).toBe(false);
      expect(guest.get('[data-test="waiting-for-player"]').text()).toBe(ui.waitingForPlayer);
    });

    it('says nothing about a free seat once the lobby is full', () => {
      const full = mountLobby({ lobby: lobby({ mySeat: 1, canEditOptions: false, maxPlayers: 2 }) });
      expect(full.find('[data-test="waiting-for-player"]').exists()).toBe(false);
    });
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
      const wrapper = mountLobby({ lobby: lobby({ canStart: false, minPlayers: 3 }) });
      expect(wrapper.get('[data-test="player-count"]').text()).toBe('2 / 6 players');
      expect(wrapper.get('[data-test="start-hint"]').text()).toBe('Waiting for 1 more player to start…');
    });

    it('lets the host add a bot, and disables the button when the lobby is full', async () => {
      const wrapper = mountLobby();
      const button = wrapper.get('[data-test="add-bot"]');
      expect(button.text()).toContain(ui.addBot);
      expect((button.element as HTMLButtonElement).disabled).toBe(false);
      await button.trigger('click');
      expect(wrapper.emitted('add-bot')).toHaveLength(1);

      const full = mountLobby({ lobby: lobby({ maxPlayers: 2 }) });
      expect((full.get('[data-test="add-bot"]').element as HTMLButtonElement).disabled).toBe(true);
    });

    it('enables the remove button only on removable bot seats and removes that seat', async () => {
      const wrapper = mountLobby({ lobby: humanAboveBot });
      expect((wrapper.get('[data-test="remove-bot-1"]').element as HTMLButtonElement).disabled).toBe(true);
      const enabled = wrapper.get('[data-test="remove-bot-3"]');
      expect((enabled.element as HTMLButtonElement).disabled).toBe(false);
      expect(enabled.attributes('aria-label')).toBe('Remove Bot 2');
      await enabled.trigger('click');
      expect(wrapper.emitted('remove-bot')).toEqual([[3]]);
      expect(wrapper.find('[data-test="remove-bot-0"]').exists()).toBe(false);
      expect(wrapper.find('[data-test="remove-bot-2"]').exists()).toBe(false);
    });

    it('shows guests the bots without any control', () => {
      const wrapper = mountLobby({ lobby: { ...humanAboveBot, mySeat: 2, canEditOptions: false, removableBotSeats: undefined } });
      expect(wrapper.findAll('[data-test="badge-bot"]')).toHaveLength(2);
      expect(wrapper.find('[data-test="add-bot"]').exists()).toBe(false);
      expect(wrapper.find('[data-test^="remove-bot-"]').exists()).toBe(false);
    });

    it('shows a visible error when the server refused a change', () => {
      const wrapper = mountLobby({ error: 'Room is full' });
      const alert = wrapper.get('[data-test="lobby-error"]');
      expect(alert.text()).toBe(ui.actionFailed);
      expect(alert.attributes('role')).toBe('alert');
      expect(mountLobby({ error: null }).find('[data-test="lobby-error"]').exists()).toBe(false);
    });
  });

  describe('seat options', () => {
    const withTiers = lobby({
      seatOptionDescriptors: [tier],
      players: [
        { seat: 0, name: 'Alice', joined: true, bot: false },
        { seat: 1, name: 'Bot 1', joined: true, bot: true, seatOptions: { tier: '2' } },
      ],
      removableBotSeats: [1],
    });

    it('gives the host one labelled selector per bot, showing its value', () => {
      const wrapper = mountLobby({ lobby: withTiers });
      const select = wrapper.get('select[data-test="seat-option-1-tier"]');
      expect((select.element as HTMLSelectElement).value).toBe('2');
      expect(select.attributes('aria-label')).toContain('Bot 1');
      expect(wrapper.findAll('select')).toHaveLength(1);
    });

    it('emits set-seat-option when the host picks another value', async () => {
      const wrapper = mountLobby({ lobby: withTiers });
      await wrapper.get('select[data-test="seat-option-1-tier"]').setValue('0');
      expect(wrapper.emitted('set-seat-option')).toEqual([[1, 'tier', '0']]);
    });

    it('renders nothing for a game whose bots declare no option, and nothing for a guest', () => {
      expect(mountLobby().find('select').exists()).toBe(false);
      const guest = mountLobby({ lobby: { ...withTiers, mySeat: 1, canEditOptions: false } });
      expect(guest.find('select').exists()).toBe(false);
    });
  });

  describe('leaving', () => {
    it('offers a Leave button at the top that emits leave', async () => {
      const wrapper = mountLobby();
      const button = wrapper.get('[data-test="leave-lobby"]');
      expect(button.text()).toContain(ui.leave);
      await button.trigger('click');
      expect(wrapper.emitted('leave')).toHaveLength(1);
    });

    it('offers it to a guest too', () => {
      const wrapper = mountLobby({ lobby: lobby({ mySeat: 1, canEditOptions: false }) });
      expect(wrapper.find('[data-test="leave-lobby"]').exists()).toBe(true);
    });
  });

  describe('becoming the host', () => {
    const guestView = () => lobby({ mySeat: 1, canEditOptions: false, lobbyVersion: 1, players: [
      { seat: 0, name: 'Alice', joined: true, bot: false },
      { seat: 1, name: 'Bob', joined: true, bot: false },
    ] });

    it('announces it in a polite live region when the host seat passes to me', async () => {
      const wrapper = mountLobby({ lobby: guestView() });
      const region = wrapper.get('[data-test="host-notice"]');
      expect(region.attributes('aria-live')).toBe('polite');
      expect(region.attributes('role')).toBe('status');
      expect(region.text()).toBe('');

      await wrapper.setProps({ lobby: lobby({ mySeat: 0, lobbyVersion: 2, players: [
        { seat: 0, name: 'Bob', joined: true, bot: false },
      ] }) });
      expect(wrapper.get('[data-test="host-notice"]').text()).toBe(ui.nowHost);
      expect(wrapper.find('[data-test="start"]').exists()).toBe(true);
      expect(wrapper.find('fieldset[data-test="option-claimMode"]').exists()).toBe(true);
    });

    it('says nothing to a host who has been the host all along', async () => {
      const wrapper = mountLobby();
      expect(wrapper.get('[data-test="host-notice"]').text()).toBe('');
      await wrapper.setProps({ lobby: lobby({ lobbyVersion: 2 }) });
      expect(wrapper.get('[data-test="host-notice"]').text()).toBe('');
    });
  });
});
