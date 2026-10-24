import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, RouterView } from 'vue-router';
import { defineComponent, h } from 'vue';
import PrimeVue from 'primevue/config';
import { createAppRouter } from '../../router';
import { usePresidentStore } from '../../state/President.store';
import type { PresidentState } from '../../model/president/PresidentState';
import { buildPresidentHand, buildPresidentPlayer, buildPresidentState, buildPresidentTrick } from '../../model/fixtures/president';

// Characterisation of the President table's pictures (shared animation module, chunk 2): a scripted log is driven
// through the screen with a stubbed motion layer, and everything the pictures do is recorded on a fake clock: every
// `play(movements, options)` call with the places it resolved, the bubbles, the counts, the held pile, the texts, when
// the exchange is acknowledged and when the round sheet and the final scores show. Written and recorded on the code
// before the migration: the recording (the snapshot) is the expected value, and the migration must leave it unchanged.

const recording = vi.hoisted(() => ({
  lines: [] as unknown[],
  t0: 0,
}));

vi.mock('../../animation', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../animation')>();
  const { defineComponent: define, h: render } = await import('vue');
  type Resolve = (anchor: unknown) => { rect: unknown; cards: unknown[] } | null;
  const where = (resolve: Resolve, anchor: unknown) => {
    const target = resolve(anchor);
    return target ? { rect: target.rect, cards: target.cards.length } : null;
  };
  const StubLayer = define({
    name: 'CardMotionLayer',
    props: ['resolve', 'markMs', 'markReducedMs', 'markStyle', 'arrowId'],
    setup(props, { expose }) {
      recording.lines.push({ layer: { markMs: props.markMs, markReducedMs: props.markReducedMs, markStyle: props.markStyle, arrowId: props.arrowId } });
      expose({
        play(movements: Record<string, unknown>[], options?: Record<string, unknown>) {
          const resolve = props.resolve as Resolve;
          recording.lines.push({
            at: Date.now() - recording.t0,
            play: options ?? null,
            movements: movements.map(m => ({
              kind: m.kind, card: (m.card as { name: string } | null)?.name ?? null, actor: m.actor, byOpponent: m.byOpponent,
              delayMs: m.delayMs, durationMs: m.durationMs, holdAfterMs: m.holdAfterMs, flip: m.flip, turnDown: m.turnDown,
              from: m.from, to: m.to, marks: m.marks,
              fromAt: where(resolve, m.from), toAt: where(resolve, m.to),
              marksAt: (m.marks as unknown[]).map(a => where(resolve, a)),
            })),
          });
          return { endsInMs: 0 };
        },
      });
      return () => render('div', { 'data-test': 'motion-layer' });
    },
  });
  return { ...actual, CardMotionLayer: StubLayer };
});

const seatsOf = (n: number, overrides: Record<number, Partial<ReturnType<typeof buildPresidentPlayer>>> = {}) =>
  Array.from({ length: n }, (_, seat) => buildPresidentPlayer(seat, { name: `P${seat}`, ...overrides[seat] }));
const card = (rank: string, suit: string) => ({ rank, suit, name: `${suit}_${rank}` });

function playing(overrides: Partial<PresidentState> = {}): PresidentState {
  return buildPresidentState({
    players: seatsOf(4),
    hand: buildPresidentHand(['3', 'CLUB'], ['9', 'HEART'], ['9', 'SPADE'], ['KING', 'HEART']),
    legalPlays: [{ kind: 'SET', rank: '3', count: 1 }, { kind: 'SET', rank: '9', count: 1 }, { kind: 'SET', rank: '9', count: 2 },
      { kind: 'SET', rank: 'KING', count: 1 }],
    availableActions: ['PLAY', 'PASS'],
    ...overrides,
  });
}

const event = (seat: number, action: 'PLAY' | 'PASS' | 'COMPLETION', over: Record<string, unknown> = {}, eventType: string = action) => ({
  type: 'event' as const, eventType, message: '',
  eventData: {
    seat, action, cards: action === 'PASS' ? null : [card('2', 'CLUB')], count: null,
    outPlace: null, outForbidden: null, trickWinner: null, trickEndReason: null, barrierOpened: null, ...over,
  },
});

/** Every element has a place on screen of its own (happy-dom measures nothing): its position in the document. */
function measureByDocumentOrder() {
  vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function (this: Element) {
    const index = Array.prototype.indexOf.call(document.querySelectorAll('*'), this);
    return { left: index, top: 2 * index, width: 40, height: 58, right: index + 40, bottom: 2 * index + 58, x: index, y: 2 * index, toJSON: () => ({}) } as DOMRect;
  });
}

async function mountTable(state: PresidentState, mySeat = 0) {
  const store = usePresidentStore();
  localStorage.setItem('president:tokens:p1', JSON.stringify({ [mySeat]: 'tok' }));
  store.applyEvent({ type: 'seat-change', seat: mySeat });
  store.applyEvent({ type: 'state-update', state });
  vi.spyOn(store, 'hydrate').mockImplementation(() => {});
  vi.spyOn(store, 'restore').mockImplementation(() => {});
  const ack = vi.spyOn(store, 'seeExchange').mockImplementation(() => {
    recording.lines.push({ at: Date.now() - recording.t0, acknowledged: true });
    return true;
  });
  vi.spyOn(store, 'play').mockImplementation(() => {
    recording.lines.push({ at: Date.now() - recording.t0, sent: 'play', selected: [...store.selectedCards.map(c => c.name)] });
    return true;
  });
  const router = createAppRouter(createMemoryHistory());
  await router.push('/games/president/room/p1');
  await router.isReady();
  const Root = defineComponent({ render: () => h(RouterView) });
  const wrapper = mount(Root, { attachTo: document.body, global: { plugins: [router, PrimeVue] } });
  await flushPromises();
  return { store, wrapper, ack };
}

type Wrapper = Awaited<ReturnType<typeof mountTable>>['wrapper'];

/** What the table shows that the pictures drive, in one line. */
function look(wrapper: Wrapper): string {
  const text = (selector: string) => {
    const found = wrapper.find(selector);
    return found.exists() ? found.text().replace(/\s+/g, ' ').trim() : '-';
  };
  const seats = wrapper.findAll('.seat-slot').map((slot) => {
    const seat = slot.attributes('data-seat');
    const bubble = slot.find('[data-test="seat-bubble"]');
    const count = slot.find('[data-test="seat-cards"]');
    const backs = slot.findAll('[data-test="fan-back"]').length;
    const current = slot.find('.president-seat--current').exists() ? '*' : '';
    return `${seat}${current}:${count.exists() ? count.text() : '-'}/${backs}${bubble.exists() ? `(${bubble.text()})` : ''}`;
  });
  const mine = wrapper.find('[data-test="my-zone"] [data-test="seat-bubble"]');
  const pile = wrapper.find('[data-test="trick-cards"]');
  return [
    `phase=${text('[data-test="phase-text"]')}`,
    `by=${text('[data-test="trick-by"]')}`,
    `binding=${text('[data-test="binding"]')}`,
    `closed=${text('[data-test="trick-closed"]')}`,
    `note=${text('[data-test="trick-note"]')}`,
    `count=${text('[data-test="trick-count"]')}`,
    `pile=${pile.exists() ? pile.classes().filter(c => c.startsWith('trick-cards--')).join('+') : '-'}`,
    `cards=${wrapper.findAll('[data-test="trick-card"]').length}/${wrapper.findAll('[data-test="closed-trick-card"]').length}`,
    `seats=${seats.join(' ')}`,
    `me=${mine.exists() ? mine.text() : '-'}`,
    `hand=${wrapper.findAll('[data-test^="hand-card-"]').map(c => `${c.attributes('data-card')}${c.classes().includes('landing') ? '~' : ''}`).join(',')}`,
    `chips=${wrapper.findAll('[data-test="rule-chip"]').map(c => c.text()).join('|')}`,
    `board=${wrapper.findAll('[data-test="board-row"]').map(r => r.attributes('data-state')).join('+') || '-'}`,
    `bar=${wrapper.find('[data-test="action-bar"]').exists()}`,
    `sheet=${wrapper.find('[data-test="round-over"]').exists()}`,
    `end=${wrapper.find('[data-test="end"]').exists()}`,
    `round=${text('[data-test="round"]')}`,
  ].join(' | ');
}

/** Moves the clock in 50 ms steps for `ms`, recording the table whenever it changed. */
async function watchFor(wrapper: Wrapper, ms: number) {
  let last = '';
  const sample = () => {
    const now = look(wrapper);
    if (now !== last) recording.lines.push({ at: Date.now() - recording.t0, look: now });
    last = now;
  };
  sample();
  for (let t = 0; t < ms; t += 50) {
    await vi.advanceTimersByTimeAsync(50);
    await flushPromises();
    sample();
  }
}

describe('PresidentGameScreen pictures (characterisation)', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})));
    setActivePinia(createPinia());
    localStorage.clear();
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] });
    vi.setSystemTime(new Date(1_000_000));
    recording.lines = [];
    recording.t0 = Date.now();
    measureByDocumentOrder();
  });
  afterEach(() => {
    document.body.innerHTML = '';
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  const nine = card('9', 'HEART');
  const nineAgain = card('9', 'SPADE');
  const king = card('KING', 'HEART');
  const kingAgain = card('KING', 'SPADE');

  it('myPlayMeasuredAtTheClick_aPass_anOpponentPair_andAnotherPass', async () => {
    const { store, wrapper } = await mountTable(playing({ currentSeat: 0 }));
    await wrapper.get('[data-card="HEART_9"]').trigger('click');
    await wrapper.get('[data-card="SPADE_9"]').trigger('click');
    await wrapper.get('[data-test="play"]').trigger('click');
    await vi.advanceTimersByTimeAsync(120);
    store.applyEvent({ type: 'state-update', state: playing({
      version: 2, currentSeat: 1, players: seatsOf(4, { 0: { handSize: 6 } }), availableActions: [], legalPlays: [],
      hand: buildPresidentHand(['3', 'CLUB'], ['KING', 'HEART']),
      trick: buildPresidentTrick({ leader: 0, plays: [{ seat: 0, cards: [nine, nineAgain] }], top: { kind: 'SET', rank: '9', count: 2 }, constraint: 'BEAT' }),
    }) });
    store.applyEvent(event(0, 'PLAY', { cards: [nine, nineAgain] }));
    await flushPromises();
    await watchFor(wrapper, 700);
    store.applyEvent({ type: 'state-update', state: playing({
      version: 3, currentSeat: 2, players: seatsOf(4, { 0: { handSize: 6 }, 1: { passed: true } }), availableActions: [], legalPlays: [],
      hand: buildPresidentHand(['3', 'CLUB'], ['KING', 'HEART']),
      trick: buildPresidentTrick({ leader: 0, plays: [{ seat: 0, cards: [nine, nineAgain] }], top: { kind: 'SET', rank: '9', count: 2 }, constraint: 'BEAT', passed: [1] }),
    }) });
    store.applyEvent(event(1, 'PASS'));
    store.applyEvent({ type: 'state-update', state: playing({
      version: 4, currentSeat: 3, players: seatsOf(4, { 0: { handSize: 6 }, 1: { passed: true }, 2: { handSize: 6 } }), availableActions: [], legalPlays: [],
      hand: buildPresidentHand(['3', 'CLUB'], ['KING', 'HEART']),
      trick: buildPresidentTrick({
        leader: 0, plays: [{ seat: 0, cards: [nine, nineAgain] }, { seat: 2, cards: [king, kingAgain] }],
        top: { kind: 'SET', rank: 'KING', count: 2 }, constraint: 'BEAT', passed: [1],
      }),
    }) });
    store.applyEvent(event(2, 'PLAY', { cards: [king, kingAgain] }));
    store.applyEvent({ type: 'state-update', state: playing({
      version: 5, currentSeat: 0, players: seatsOf(4, { 0: { handSize: 6 }, 1: { passed: true }, 2: { handSize: 6 }, 3: { passed: true } }),
      hand: buildPresidentHand(['3', 'CLUB'], ['KING', 'HEART']), availableActions: ['PASS'], legalPlays: [],
      trick: buildPresidentTrick({
        leader: 0, plays: [{ seat: 0, cards: [nine, nineAgain] }, { seat: 2, cards: [king, kingAgain] }],
        top: { kind: 'SET', rank: 'KING', count: 2 }, constraint: 'BEAT', passed: [1, 3],
      }),
    }) });
    store.applyEvent(event(3, 'PASS'));
    await flushPromises();
    await watchFor(wrapper, 4000);
    expect(recording.lines).toMatchSnapshot();
  });

  /** A trick that seat 1 closes with a 2 on my king, then seat 1 leads a 3 at once. */
  async function cutThenLead(mount: () => ReturnType<typeof mountTable>) {
    const { store, wrapper } = await mount();
    store.applyEvent({ type: 'state-update', state: playing({ version: 2, currentSeat: 1, players: seatsOf(4, { 1: { handSize: 7 } }) }) });
    store.applyEvent(event(1, 'PLAY', { trickWinner: 1, trickEndReason: 'CUT' }));
    await flushPromises();
    await watchFor(wrapper, 900);
    const lead = card('3', 'DIAMOND');
    store.applyEvent({ type: 'state-update', state: playing({
      version: 3, currentSeat: 2, players: seatsOf(4, { 1: { handSize: 6 } }),
      trick: buildPresidentTrick({ leader: 1, plays: [{ seat: 1, cards: [lead] }], top: { kind: 'SET', rank: '3', count: 1 } }),
    }) });
    store.applyEvent(event(1, 'PLAY', { cards: [lead] }));
    await flushPromises();
    await watchFor(wrapper, 6000);
    expect(recording.lines).toMatchSnapshot();
  }
  const kingOnThePile = () => mountTable(playing({
    currentSeat: 1, trick: buildPresidentTrick({ leader: 0, plays: [{ seat: 0, cards: [king] }, { seat: 3, cards: [card('ACE', 'CLUB')] }] }),
  }));

  it('aCutClosesTheTrick_theBadgeHolds_theCardsSweep_thenTheWinnersLeadFliesIn', () => cutThenLead(kingOnThePile));

  it('aCutAndALead_atFast', () => {
    localStorage.setItem('animationSpeed', 'fast');
    return cutThenLead(kingOnThePile);
  });

  it('aCutAndALead_atInstant', () => {
    localStorage.setItem('animationSpeed', 'instant');
    return cutThenLead(kingOnThePile);
  });

  it('aCutAndALead_underReducedMotion', () => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.includes('prefers-reduced-motion'), media: query, addEventListener: () => {}, removeEventListener: () => {},
    }));
    return cutThenLead(kingOnThePile);
  });

  it('aFourCompletedOutOfTurn_thenAForbiddenFinish', async () => {
    const { store, wrapper } = await mountTable(playing({
      currentSeat: 3,
      trick: buildPresidentTrick({
        leader: 1, plays: [{ seat: 1, cards: [card('7', 'CLUB'), card('7', 'HEART'), card('7', 'SPADE')] }],
        top: { kind: 'SET', rank: '7', count: 3 },
      }),
    }));
    // seat 2 completes the four out of turn: the trick closes, seat 2 leads
    store.applyEvent({ type: 'state-update', state: playing({ version: 2, currentSeat: 2, players: seatsOf(4, { 2: { handSize: 7 } }) }) });
    store.applyEvent(event(2, 'COMPLETION', { cards: [card('7', 'DIAMOND')], trickWinner: 2, trickEndReason: 'QUAD' }, 'COMPLETION'));
    // seat 2 goes out on a 2: a forbidden finish, the trick closes, seat 3 leads
    store.applyEvent({ type: 'state-update', state: playing({
      version: 3, currentSeat: 3, players: seatsOf(4, { 2: { handSize: 0, out: true, place: 4, forbiddenFinish: true } }),
    }) });
    store.applyEvent(event(2, 'PLAY', { cards: [card('2', 'HEART')], outForbidden: true, outPlace: 4, trickWinner: 3, trickEndReason: 'CUT' }));
    await flushPromises();
    await watchFor(wrapper, 9000);
    expect(recording.lines).toMatchSnapshot();
  });

  describe('a seat finishing', () => {
    const lastCard = card('ACE', 'HEART');
    const base = (me = 0) => playing({
      currentSeat: 2, players: seatsOf(4, { 2: { handSize: 1 } }),
      trick: buildPresidentTrick({ leader: me, plays: [{ seat: me, cards: [king] }] }),
    });
    const finished = (seat: number) => playing({
      version: 2, currentSeat: 3, players: seatsOf(4, { [seat]: { handSize: 0, out: true, place: 1 } }),
      trick: buildPresidentTrick({ leader: 0, plays: [{ seat, cards: [lastCard] }], top: { kind: 'SET', rank: 'ACE', count: 1 } }),
    });
    const announced = (wrapper: Wrapper) => wrapper.findAll('[data-test="president-announcements"] li').map(li => li.text());
    const medalOf = (wrapper: Wrapper, seat: number) => wrapper.find(`.seat-slot[data-seat="${seat}"] [data-test="seat-out"]`);

    it('anOpponentGoesOut_thenTheBubbleTheMedalAndTheAnnouncementComeWhenTheMoveIsTold_notWhenItsStateArrives', async () => {
      const { store, wrapper } = await mountTable(base());
      store.applyEvent({ type: 'state-update', state: finished(2) });
      store.applyEvent(event(2, 'PLAY', { cards: [lastCard], outPlace: 1 }));
      await flushPromises();
      // The latest state says it already; the table does not, while the last card is in flight.
      expect(store.game?.players[2].out).toBe(true);
      const told: string[] = [];
      for (let t = 0; t < 4000; t += 50) {
        await vi.advanceTimersByTimeAsync(50);
        await flushPromises();
        const bubble = wrapper.find('.seat-slot[data-seat="2"] [data-test="seat-bubble"]');
        told.push(`${bubble.exists() ? bubble.text() : '-'}|${medalOf(wrapper, 2).exists() ? 'medal' : '-'}|${announced(wrapper).join(';') || '-'}`);
      }
      const first = told.findIndex(line => line.startsWith('Out · 1st'));
      expect(first).toBeGreaterThanOrEqual(0);
      expect(told[first]).toBe('Out · 1st|medal|P2 is out, 1st');
      // Nothing of it before: bubble, medal and announcement are born together.
      expect(told.slice(0, first).every(line => line === '-|-|-')).toBe(true);
      // The bubble has a life: it is gone while the medal stays.
      expect(told[told.length - 1]).toBe('-|medal|P2 is out, 1st');
    });

    it('iGoOut_thenTheAnnouncementSaysYou_andTheMedalIsOnMyPlate', async () => {
      const { store, wrapper } = await mountTable(playing({ currentSeat: 0, players: seatsOf(4, { 0: { handSize: 1 } }) }));
      store.applyEvent({ type: 'state-update', state: finished(0) });
      store.applyEvent(event(0, 'PLAY', { cards: [lastCard], outPlace: 1 }));
      await flushPromises();
      await watchFor(wrapper, 1500);
      expect(announced(wrapper)).toEqual(['You are out, 1st']);
      expect(wrapper.find('[data-test="my-zone"] [data-test="seat-out"]').attributes('data-place')).toBe('1');
    });

    it('aForbiddenFinish_isNotAnnouncedAsAPlace_andKeepsItsOwnBubble', async () => {
      const { store, wrapper } = await mountTable(base());
      store.applyEvent({ type: 'state-update', state: playing({
        version: 2, currentSeat: 3, players: seatsOf(4, { 2: { handSize: 0, out: true, place: 4, forbiddenFinish: true } }),
      }) });
      store.applyEvent(event(2, 'PLAY', { cards: [card('2', 'HEART')], outForbidden: true, outPlace: 4 }));
      await flushPromises();
      const seen = new Set<string>();
      for (let t = 0; t < 3000; t += 50) {
        await vi.advanceTimersByTimeAsync(50);
        await flushPromises();
        const bubble = wrapper.find('.seat-slot[data-seat="2"] [data-test="seat-bubble"]');
        if (bubble.exists()) seen.add(bubble.text());
      }
      expect([...seen]).toEqual(['Out on a 2']);
      expect(announced(wrapper)).toEqual([]);
      // No medal for a forbidden finish: the red marker stays, and the name is not dimmed either.
      expect(wrapper.find('.seat-slot[data-seat="2"] [data-test="seat-medal-place"]').exists()).toBe(false);
      expect(wrapper.find('.seat-slot[data-seat="2"] .marker--forbidden').exists()).toBe(true);
    });

    it('atInstant_theFinishIsToldAtOnce_withoutAPop', async () => {
      localStorage.setItem('animationSpeed', 'instant');
      const { store, wrapper } = await mountTable(base());
      store.applyEvent({ type: 'state-update', state: finished(2) });
      store.applyEvent(event(2, 'PLAY', { cards: [lastCard], outPlace: 1 }));
      await flushPromises();
      expect(wrapper.find('.seat-slot[data-seat="2"] [data-test="seat-bubble"]').text()).toBe('Out · 1st');
      expect(wrapper.find('.seat-slot[data-seat="2"] [data-test="seat-bubble"]').classes()).toContain('seat-bubble--still');
      expect(medalOf(wrapper, 2).exists()).toBe(true);
      expect(announced(wrapper)).toEqual(['P2 is out, 1st']);
    });
  });

  async function burst() {
    const { store, wrapper } = await mountTable(playing({
      currentSeat: 1, trick: buildPresidentTrick({ leader: 0, plays: [{ seat: 0, cards: [king] }] }),
    }));
    for (let version = 2; version < 8; version++) {
      store.applyEvent({ type: 'state-update', state: playing({ version, currentSeat: 1 }) });
      store.applyEvent(event(1, 'PLAY', { trickWinner: 1, trickEndReason: 'QUAD' }));
    }
    const lead = card('3', 'DIAMOND');
    store.applyEvent({ type: 'state-update', state: playing({
      version: 9, currentSeat: 2, trick: buildPresidentTrick({ leader: 1, plays: [{ seat: 1, cards: [lead] }] }),
    }) });
    store.applyEvent(event(1, 'PLAY', { cards: [lead] }));
    await flushPromises();
    await watchFor(wrapper, 12000);
    expect(recording.lines).toMatchSnapshot();
  }

  it('aBurstOfClosedTricks_pastTheQueueBounds', burst);

  it('aBurstOfClosedTricks_atInstant', () => {
    localStorage.setItem('animationSpeed', 'instant');
    return burst();
  });

  describe('an exchange round', () => {
    const titles = { 0: { title: 'PRESIDENT' as const }, 1: { title: 'NEUTRAL' as const }, 2: { title: 'NEUTRAL' as const }, 3: { title: 'SCUM' as const } };
    const over = () => playing({ round: 1, phase: 'ROUND_OVER', legalPlays: [], availableActions: ['READY'], players: seatsOf(4, titles) });
    const dealt = (over2: Partial<PresidentState> = {}) => playing({
      round: 2, version: 2, phase: 'EXCHANGE', legalPlays: [], availableActions: ['GIVE_BACK'], toGiveBack: 2,
      players: seatsOf(4, { ...titles, 0: { title: 'PRESIDENT', handSize: 15 }, 3: { title: 'SCUM', handSize: 11 } }),
      exchange: [{ high: 0, low: 3, count: 2, done: false }],
      hand: buildPresidentHand(['3', 'CLUB'], ['9', 'HEART'], ['9', 'SPADE'], ['KING', 'HEART'], ['ACE', 'CLUB']),
      received: buildPresidentHand(['KING', 'HEART'], ['ACE', 'CLUB']),
      ...over2,
    });

    async function exchangeRound() {
      const { store, wrapper } = await mountTable(over());
      store.applyEvent({ type: 'state-update', state: dealt() });
      await flushPromises();
      await watchFor(wrapper, 2500);
      // I give the 3 and the 9 of hearts back: the view opens
      store.applyEvent({ type: 'state-update', state: dealt({
        version: 3, phase: 'EXCHANGE_VIEW', availableActions: ['SEE_EXCHANGE'], toGiveBack: 0, currentSeat: 3,
        players: seatsOf(4, { ...titles, 0: { title: 'PRESIDENT', handSize: 13 }, 3: { title: 'SCUM', handSize: 13 } }),
        exchange: [{ high: 0, low: 3, count: 2, done: true }],
        hand: buildPresidentHand(['9', 'SPADE'], ['KING', 'HEART'], ['ACE', 'CLUB']),
        given: buildPresidentHand(['3', 'CLUB'], ['9', 'HEART']),
      }) });
      await flushPromises();
      await watchFor(wrapper, 6000);
      expect(recording.lines).toMatchSnapshot();
    }

    it('theDealsCardsFlyToMyHand_thenMyReturnsFly_thenTheSummaryIsReadAndAcknowledged', exchangeRound);

    it('atFast', () => {
      localStorage.setItem('animationSpeed', 'fast');
      return exchangeRound();
    });

    it('atInstant', () => {
      localStorage.setItem('animationSpeed', 'instant');
      return exchangeRound();
    });

    it('underReducedMotion', () => {
      vi.stubGlobal('matchMedia', (query: string) => ({
        matches: query.includes('prefers-reduced-motion'), media: query, addEventListener: () => {}, removeEventListener: () => {},
      }));
      return exchangeRound();
    });
  });

  describe('the end of a round and of the match', () => {
    const places = { 0: { place: 3, title: 'PRESIDENT' as const }, 1: { place: 1, title: 'SCUM' as const }, 2: { place: 2, title: 'NEUTRAL' as const }, 3: { place: 4, title: 'NEUTRAL' as const } };
    const scored = (round: number) => ({
      round, finishingOrder: [1, 2, 0, 3],
      players: [
        { seat: 1, place: 1, title: 'PRESIDENT' as const, points: 3, total: 5, forbiddenFinish: false },
        { seat: 2, place: 2, title: 'NEUTRAL' as const, points: 2, total: 4, forbiddenFinish: false },
        { seat: 0, place: 3, title: 'NEUTRAL' as const, points: 1, total: 3, forbiddenFinish: false },
        { seat: 3, place: 4, title: 'SCUM' as const, points: 0, total: 1, forbiddenFinish: false },
      ],
    });

    async function lastPlay(final: boolean) {
      const { store, wrapper } = await mountTable(playing({
        round: 2, currentSeat: 1, players: seatsOf(4, { 1: { handSize: 1 } }),
        trick: buildPresidentTrick({ leader: 0, plays: [{ seat: 0, cards: [king] }] }),
      }));
      store.applyEvent({ type: 'state-update', state: playing({
        version: 2, round: 2, phase: final ? 'FINISHED' : 'ROUND_OVER', availableActions: final ? [] : ['READY'], legalPlays: [],
        players: seatsOf(4, places), lastRound: scored(2),
        ...(final ? { outcome: { status: 'FINISHED' as const, winners: [1], reason: 'POINTS' as const },
          standings: [{ seat: 1, points: 12 }, { seat: 0, points: 8 }, { seat: 2, points: 5 }, { seat: 3, points: 1 }] } : {}),
      }) });
      store.applyEvent(event(1, 'PLAY', { cards: [card('ACE', 'HEART')], outPlace: 1, trickWinner: 1, trickEndReason: 'FIRST_OUT' }));
      await flushPromises();
      await watchFor(wrapper, 7000);
      if (final) {
        await wrapper.get('[data-test="final-scores"]').trigger('click');
        await flushPromises();
        await watchFor(wrapper, 100);
      }
      expect(recording.lines).toMatchSnapshot();
    }

    it('theLastPlayOfARound_thenTheSheet', () => lastPlay(false));
    it('theLastPlayOfTheMatch_thenTheSheet_thenTheFinalScoresOnceContinued', () => lastPlay(true));

    it('aForfeitEndsTheMatch_thenTheFinalScoresAfterTheTableSettles', async () => {
      const { store, wrapper } = await mountTable(playing({ round: 2, currentSeat: 1 }));
      store.applyEvent({ type: 'state-update', state: playing({
        version: 2, round: 2, phase: 'FINISHED', availableActions: [], legalPlays: [],
        outcome: { status: 'FINISHED', winners: [0], reason: 'FORFEIT' },
      }) });
      await flushPromises();
      await watchFor(wrapper, 2500);
      expect(recording.lines).toMatchSnapshot();
    });
  });
});
