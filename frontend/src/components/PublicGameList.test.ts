import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { createMemoryHistory } from 'vue-router';
import PrimeVue from 'primevue/config';
import PublicGameList from './PublicGameList.vue';
import { createAppRouter } from '../router';
import { findGame } from '../model/games';
import { messagesEn } from '../locales/en';
import { HttpError } from '../model/HttpError';
import type { PublicLobby } from '../model/lobby/PublicLobby';

function lobby(id: string, over: Partial<PublicLobby> = {}): PublicLobby {
  return { gameId: id, gameType: 'kobo', hostName: 'Zoe', joined: 2, bots: 0, maxPlayers: 4, state: 'OPEN', ...over };
}

const LOBBIES = [
  lobby('open1', { hostName: 'Zoe', joined: 2, bots: 1 }),
  lobby('full1', { hostName: 'Max', joined: 4, state: 'FULL' }),
  lobby('play1', { hostName: 'Ida', joined: 3, state: 'IN_PLAY' }),
];

async function mountList(props: Record<string, unknown> = {}, attach = false) {
  const router = createAppRouter(createMemoryHistory());
  await router.push('/kobo/join');
  await router.isReady();
  const wrapper = mount(PublicGameList, {
    props: {
      game: findGame('kobo')!,
      lobbies: LOBBIES,
      total: 3,
      truncated: false,
      loading: false,
      online: true,
      announcement: '',
      join: vi.fn().mockResolvedValue(undefined),
      ...props,
    },
    global: { plugins: [router, PrimeVue] },
    attachTo: attach ? document.body : undefined,
  });
  return { wrapper, router };
}

const ui = messagesEn.join.publicGames;

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal('matchMedia', (q: string) => ({ matches: false, media: q, addEventListener() {}, removeEventListener() {} }));
});
afterEach(() => {
  vi.unstubAllGlobals();
  document.body.innerHTML = '';
});

describe('PublicGameList', () => {
  it('showsEachOpenRowWithHostBadgeAndCounts', async () => {
    const { wrapper } = await mountList();
    const row = wrapper.get('[data-game-id="open1"]');
    expect(row.text()).toContain('Zoe');
    expect(row.get('[data-test="pg-badge"]').text()).toBe(ui.open);
    expect(row.text()).toContain('2 / 4 players');
    expect(row.text()).toContain('· 1 bot');
  });

  it.each([null, '', '   '])('namesAHostWithoutANameLikeSeatOne (%j)', async (hostName) => {
    const { wrapper } = await mountList({ lobbies: [lobby('a', { hostName })], total: 1 });
    const label = messagesEn.lobby.playerLabel.replace('{n}', '1');
    expect(wrapper.get('[data-game-id="a"] .pg-host').text()).toBe(label);
    expect(wrapper.get('[data-game-id="a"]').html()).toContain(`Join ${label}`);
  });

  it('showsNoBotTextWithoutBots', async () => {
    const { wrapper } = await mountList({ lobbies: [lobby('a', { bots: 0 })], total: 1 });
    expect(wrapper.get('[data-game-id="a"]').text()).not.toContain('bot');
  });

  it('drawsSeatDotsForHumansBotsAndFreeSeats', async () => {
    const { wrapper } = await mountList({ lobbies: [lobby('a', { joined: 3, bots: 1, maxPlayers: 4 })], total: 1 });
    const dots = wrapper.get('[data-test="pg-dots"]');
    expect(dots.attributes('aria-hidden')).toBe('true');
    expect(dots.findAll('.dot--human')).toHaveLength(2);
    expect(dots.findAll('.dot--bot')).toHaveLength(1);
    expect(dots.findAll('.dot--free')).toHaveLength(1);
  });

  it('showsAJoinButtonOnOpenRowsOnly', async () => {
    const { wrapper } = await mountList();
    await wrapper.get('[data-test="pg-toggle"]').setValue(false);
    expect(wrapper.findAll('[data-test="pg-row"]')).toHaveLength(3);
    expect(wrapper.findAll('[data-test="pg-join"]')).toHaveLength(1);
    expect(wrapper.get('[data-game-id="full1"]').get('[data-test="pg-badge"]').text()).toBe(ui.full);
    expect(wrapper.get('[data-game-id="play1"]').get('[data-test="pg-badge"]').text()).toBe(ui.inPlay);
    expect(wrapper.get('[data-game-id="full1"]').classes()).toContain('row--muted');
  });

  it('labelsTheJoinButtonWithTheHostAndTheSeats', async () => {
    const { wrapper } = await mountList();
    expect(wrapper.get('[data-test="pg-join"]').attributes('aria-label')).toBe("Join Zoe's game, 2 of 4 players");
  });

  it('callsJoinWithTheGameIdWhenClicked', async () => {
    const join = vi.fn().mockResolvedValue(undefined);
    const { wrapper } = await mountList({ join });
    await wrapper.get('[data-test="pg-join"]').trigger('click');
    expect(join).toHaveBeenCalledWith('open1');
  });

  it('givenARefusedJoin_thenShowsTheMessageInThatRowAsAnAlert', async () => {
    const join = vi.fn().mockRejectedValue(new HttpError('Join failed: 409', 409, 'full'));
    const { wrapper } = await mountList({ join });
    await wrapper.get('[data-test="pg-join"]').trigger('click');
    await flushPromises();
    const err = wrapper.get('[data-game-id="open1"] [data-test="pg-row-error"]');
    expect(err.text()).toBe(messagesEn.join.full);
    expect(err.attributes('role')).toBe('alert');
  });

  describe('Open games only toggle', () => {
    it('isOnByDefaultAndHidesFullAndInPlayRows', async () => {
      const { wrapper } = await mountList();
      expect((wrapper.get('[data-test="pg-toggle"]').element as HTMLInputElement).checked).toBe(true);
      expect(wrapper.findAll('[data-test="pg-row"]')).toHaveLength(1);
    });

    it('showsEveryRowWhenTurnedOffAndRemembersTheChoice', async () => {
      const { wrapper } = await mountList();
      await wrapper.get('[data-test="pg-toggle"]').setValue(false);
      expect(wrapper.findAll('[data-test="pg-row"]')).toHaveLength(3);
      const second = await mountList();
      expect((second.wrapper.get('[data-test="pg-toggle"]').element as HTMLInputElement).checked).toBe(false);
    });

    it('toleratesAFailingLocalStorage', async () => {
      const broken = { getItem() { throw new Error('denied'); }, setItem() { throw new Error('denied'); } };
      vi.stubGlobal('localStorage', broken);
      const { wrapper } = await mountList();
      expect((wrapper.get('[data-test="pg-toggle"]').element as HTMLInputElement).checked).toBe(true);
      await wrapper.get('[data-test="pg-toggle"]').setValue(false);
      expect(wrapper.findAll('[data-test="pg-row"]')).toHaveLength(3);
    });
  });

  describe('heading', () => {
    it('givenOpenOnly_thenCountsTheOpenGames', async () => {
      const { wrapper } = await mountList();
      expect(wrapper.get('[data-test="pg-heading"]').text()).toContain(ui.title);
      expect(wrapper.get('[data-test="pg-count"]').text()).toBe('1 open');
    });

    it('givenAllGames_thenCountsOpenAndTotal', async () => {
      const { wrapper } = await mountList();
      await wrapper.get('[data-test="pg-toggle"]').setValue(false);
      expect(wrapper.get('[data-test="pg-count"]').text()).toBe('1 open · 3 total');
    });

    it('isFocusableByCodeOnly', async () => {
      const { wrapper } = await mountList();
      expect(wrapper.get('[data-test="pg-heading"]').attributes('tabindex')).toBe('-1');
    });
  });

  it('putsTheListInAScrollRegionNamedByTheHeading', async () => {
    const { wrapper } = await mountList();
    const region = wrapper.get('[data-test="pg-region"]');
    expect(region.attributes('role')).toBe('region');
    expect(region.attributes('tabindex')).toBe('0');
    expect(region.attributes('aria-labelledby')).toBe(wrapper.get('[data-test="pg-heading"]').attributes('id'));
  });

  describe('states', () => {
    it('loading', async () => {
      const { wrapper } = await mountList({ loading: true, lobbies: [], total: 0 });
      expect(wrapper.get('[data-test="pg-loading"]').text()).toBe(ui.loading);
      expect(wrapper.get('[data-test="pg-region"]').attributes('aria-busy')).toBe('true');
      expect(wrapper.find('[data-test="pg-empty"]').exists()).toBe(false);
    });

    it('emptyWithALinkToTheGameHome', async () => {
      const { wrapper, router } = await mountList({ lobbies: [], total: 0 });
      expect(wrapper.get('[data-test="pg-empty"]').text()).toContain('No public Kobo game right now.');
      await wrapper.get('[data-test="pg-new-game"]').trigger('click');
      await flushPromises();
      expect(router.currentRoute.value.path).toBe('/kobo');
    });

    it('emptyWhenOnlyNonOpenGamesAreHidden', async () => {
      const { wrapper } = await mountList({ lobbies: [lobby('f', { state: 'FULL' })], total: 1 });
      expect(wrapper.find('[data-test="pg-empty"]').exists()).toBe(true);
    });

    it('offlineKeepsTheListUsableAndSaysSo', async () => {
      const { wrapper } = await mountList({ online: false });
      const offline = wrapper.get('[data-test="pg-offline"]');
      expect(offline.text()).toBe(ui.offline);
      expect(offline.attributes('role')).toBe('status');
      expect(wrapper.findAll('[data-test="pg-join"]')).toHaveLength(1);
    });

    it('truncatedFooter', async () => {
      const { wrapper } = await mountList({ total: 73, truncated: true });
      expect(wrapper.get('[data-test="pg-truncated"]').text()).toBe('Showing 3 of 73 games.');
    });

    it('noFooterWhenNotTruncated', async () => {
      const { wrapper } = await mountList();
      expect(wrapper.find('[data-test="pg-truncated"]').exists()).toBe(false);
    });
  });

  it('rendersTheAnnouncementInAPoliteLiveRegion', async () => {
    const { wrapper } = await mountList({ announcement: '1 new game. 2 open.' });
    const live = wrapper.get('[data-test="pg-live"]');
    expect(live.attributes('aria-live')).toBe('polite');
    expect(live.text()).toBe('1 new game. 2 open.');
  });

  it('whenTheFocusedRowDisappears_thenFocusMovesToTheHeading', async () => {
    const { wrapper } = await mountList({}, true);
    (wrapper.get('[data-test="pg-join"]').element as HTMLElement).focus();
    expect(document.activeElement?.getAttribute('data-test')).toBe('pg-join');
    await wrapper.setProps({ lobbies: [], total: 0 });
    await nextTick();
    expect(document.activeElement).toBe(wrapper.get('[data-test="pg-heading"]').element);
  });

  it('whenAnotherRowDisappears_thenFocusStays', async () => {
    const { wrapper } = await mountList({ lobbies: [lobby('a'), lobby('b')], total: 2 }, true);
    (wrapper.get('[data-game-id="a"] [data-test="pg-join"]').element as HTMLElement).focus();
    await wrapper.setProps({ lobbies: [lobby('a')], total: 1 });
    await nextTick();
    expect(document.activeElement?.getAttribute('data-test')).toBe('pg-join');
  });

  it('givenReducedMotion_thenTheListIsNotAnimated', async () => {
    vi.stubGlobal('matchMedia', (q: string) => ({ matches: true, media: q, addEventListener() {}, removeEventListener() {} }));
    const { wrapper } = await mountList();
    expect(wrapper.get('[data-test="pg-list"]').classes()).not.toContain('animated');
  });

  it('givenNoReducedMotion_thenTheListIsAnimated', async () => {
    const { wrapper } = await mountList();
    expect(wrapper.get('[data-test="pg-list"]').classes()).toContain('animated');
  });
});
