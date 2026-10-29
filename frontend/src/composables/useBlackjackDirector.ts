import { computed, nextTick, ref, shallowRef, watch, type ComputedRef, type Ref, type ShallowRef } from 'vue';
import {
  canAnimate, freshEntries, prefersReducedMotion, scaleMovements, speedFactor, type AnimationSpeed, type CardMotionLayerPlay,
} from '../animation';
import type Card from '../model/Card';
import type { BlackjackState } from '../model/blackjack/BlackjackState';
import {
  scriptFor, tagKindOf, type BannerKind, type BlackjackAnchor, type BlackjackTableEvent, type DealerLineKind, type Effect,
  type ScriptContext, type ScriptStep, type TagKind,
} from '../model/blackjack/motion';
import { DEALER_KEY, DEALER_HOLE_INDEX, handKey, holdFromState, shownState, type Hold } from '../model/blackjack/shown';
import { BLACKJACK_TEMPO as TEMPO } from '../model/blackjack/tempo';

export interface DirectorTag { kind: TagKind; key: number }
export interface DirectorBanner { kind: BannerKind; key: number }
export interface DirectorDealerLine { kind: DealerLineKind; total: number; key: number }

export interface BlackjackDirectorOptions {
  /** The latest started state, null in a lobby. */
  game: () => BlackjackState | null;
  /** The table events, newest first (each entry a new object). */
  events: () => readonly BlackjackTableEvent[];
  mySeat: () => number;
  speed: Ref<AnimationSpeed>;
  layer: Ref<{ play: CardMotionLayerPlay<BlackjackAnchor> } | null>;
  /** No pictures at all: Instant, reduced motion, an environment that cannot animate. Defaults to those three. */
  still?: () => boolean;
}

export interface BlackjackDirector {
  /** What the table has told so far; null when nothing is playing (the table shows the live state). */
  hold: ShallowRef<Hold | null>;
  holding: ComputedRef<boolean>;
  /** The latest state as the table shows it. */
  shown: ComputedRef<BlackjackState | null>;
  tags: Ref<Record<number, DirectorTag>>;
  /** The last thing each seat did this hand (stays on the plate until the next deal). */
  lastAction: Ref<Record<number, TagKind>>;
  dealerLine: Ref<DirectorDealerLine | null>;
  banner: Ref<DirectorBanner | null>;
  /** Shows the live state now and drops what was waiting. */
  flush: () => void;
  cancel: () => void;
}

/**
 * The director of the Blackjack table. The server answers a whole turn at once (a deal, the dealer's draws and the
 * settlement arrive in one go); the director tells it in order, at a pace that can be followed: it keeps a `Hold` (how
 * far the table is), plays each event's script (`scriptFor`) one after the other, flies the cards through the layer,
 * and lets go once everything is told, so the table shows the live state again. Purely visual: the buttons and the
 * rules read the live state, and a hold never lasts longer than `maxHoldMs`.
 */
export function useBlackjackDirector(options: BlackjackDirectorOptions): BlackjackDirector {
  const hold = shallowRef<Hold | null>(null);
  const tags = ref<Record<number, DirectorTag>>({});
  const lastAction = ref<Record<number, TagKind>>({});
  const dealerLine = ref<DirectorDealerLine | null>(null);
  const banner = ref<DirectorBanner | null>(null);

  const stillNow = () => options.still
    ? options.still()
    : options.speed.value === 'instant' || prefersReducedMotion() || !canAnimate();
  const factor = () => speedFactor(options.speed.value) || 1;

  const timers = new Set<ReturnType<typeof setTimeout>>();
  const later = (ms: number, run: () => void): ReturnType<typeof setTimeout> => {
    const timer = setTimeout(() => { timers.delete(timer); run(); }, ms);
    timers.add(timer);
    return timer;
  };

  // The state before the latest new version, and the planned cards of the hold (what each place will hold once
  // everything queued has landed).
  let previous: BlackjackState | null = null;
  let planned: Record<string, number> = {};
  let baseStacks: Record<number, number> | null = null;
  let busyUntil = 0;
  let releaseTimer: ReturnType<typeof setTimeout> | null = null;
  let maxHoldTimer: ReturnType<typeof setTimeout> | null = null;
  let counter = 0;

  watch(options.game, (next, old) => {
    if (!next) { flush(); previous = null; return; }
    if (old && next.version > old.version) previous = old;
  }, { flush: 'sync' });

  function patch(change: Partial<Hold>) {
    if (hold.value) hold.value = { ...hold.value, ...change };
  }

  function faceAt(key: string, index: number): Card | null {
    const game = options.game();
    if (!game) return null;
    if (key === DEALER_KEY) {
      if (index === DEALER_HOLE_INDEX) return null;
      return game.dealer.cards[index] ?? null;
    }
    for (const p of game.players) {
      for (let i = 0; i < p.hands.length; i++) if (handKey(p.seat, i) === key) return p.hands[i].cards[index] ?? null;
    }
    return null;
  }

  function applyEffect(effect: Effect) {
    const current = hold.value;
    switch (effect.type) {
      case 'unseal': patch({ betsSealed: false }); break;
      case 'clear': {
        const counts: Record<string, number> = {};
        for (const key of effect.keys) counts[key] = 0;
        patch({ counts, handNumber: effect.handNumber, holeFaceDown: true, resultsHidden: true, stacks: null });
        lastAction.value = {};
        dealerLine.value = null;
        break;
      }
      case 'open': if (current) patch({ counts: { ...current.counts, [effect.key]: 0 } }); break;
      case 'land': if (current) patch({ counts: { ...current.counts, [effect.key]: (current.counts[effect.key] ?? 0) + 1 } }); break;
      case 'leave': if (current) patch({ counts: { ...current.counts, [effect.key]: Math.max(0, (current.counts[effect.key] ?? 0) - 1) } }); break;
      case 'hole': patch({ holeFaceDown: false }); break;
      case 'meta': patch({ phase: effect.phase, currentSeat: effect.currentSeat, activeHandIndex: effect.activeHandIndex }); break;
      case 'settle': patch({ resultsHidden: false, stacks: null }); break;
      case 'tag': {
        lastAction.value = { ...lastAction.value, [effect.seat]: effect.kind };
        const key = ++counter;
        tags.value = { ...tags.value, [effect.seat]: { kind: effect.kind, key } };
        later(TEMPO.tagLifeMs * factor(), () => {
          if (tags.value[effect.seat]?.key !== key) return;
          const { [effect.seat]: _gone, ...rest } = tags.value;
          tags.value = rest;
        });
        break;
      }
      case 'dealer': dealerLine.value = { kind: effect.kind, total: effect.total, key: ++counter }; break;
      case 'banner': {
        const key = ++counter;
        banner.value = { kind: effect.kind, key };
        later(TEMPO.bannerLifeMs * factor(), () => { if (banner.value?.key === key) banner.value = null; });
        break;
      }
    }
  }

  function run(step: ScriptStep) {
    try {
      applyEffect(step.effect);
    } catch {
      /* a picture is never worth an error */
    }
    const flight = step.flight;
    if (!flight) return;
    const scaled = scaleMovements([flight], factor())[0];
    void nextTick().then(() => {
      try {
        options.layer.value?.play([scaled], { startInMs: 0 });
      } catch {
        /* a picture is never worth an error */
      }
    });
  }

  function release() {
    hold.value = null;
    dealerLine.value = null;
    banner.value = null;
    planned = {};
    baseStacks = null;
    busyUntil = 0;
    if (releaseTimer) clearTimeout(releaseTimer);
    if (maxHoldTimer) clearTimeout(maxHoldTimer);
    releaseTimer = null;
    maxHoldTimer = null;
  }

  function flush() {
    timers.forEach(clearTimeout);
    timers.clear();
    tags.value = {};
    release();
  }

  function startHold(): boolean {
    if (hold.value) return true;
    const base = previous ?? options.game();
    if (!base) return false;
    const h = holdFromState(base, options.mySeat());
    h.betsSealed = base.phase === 'BETTING';
    h.resultsHidden = base.phase === 'PLAYING';
    hold.value = h;
    planned = { ...h.counts };
    baseStacks = Object.fromEntries(base.players.map(p => [p.seat, p.stack]));
    maxHoldTimer = later(TEMPO.maxHoldMs, flush);
    return true;
  }

  function handleStill(entry: BlackjackTableEvent) {
    const kind = tagKindOf(entry.eventType);
    if (kind && entry.data?.seat != null) lastAction.value = { ...lastAction.value, [entry.data.seat]: kind };
    if (entry.eventType === 'DEAL') lastAction.value = {};
  }

  function handle(entry: BlackjackTableEvent) {
    if (stillNow()) { handleStill(entry); return; }
    const ctx: ScriptContext = { mySeat: options.mySeat(), planned, faceAt };
    // Planning needs the places the hold counts; start it first, then drop it again if the event has nothing to tell.
    const had = hold.value !== null;
    if (!startHold()) return;
    ctx.planned = planned;
    const script = scriptFor(entry, ctx);
    if (script.steps.length === 0) {
      if (!had) release();
      return;
    }
    if (entry.eventType === 'SETTLE' && baseStacks && hold.value?.stacks == null) patch({ stacks: baseStacks });
    const f = factor();
    const now = Date.now();
    const startAt = Math.max(now, busyUntil);
    for (const step of script.steps) {
      const at = startAt - now + Math.round(step.atMs * f);
      if (at <= 0) run(step);
      else later(at, () => run(step));
    }
    busyUntil = startAt + Math.round(script.spanMs * f);
    if (releaseTimer) clearTimeout(releaseTimer);
    releaseTimer = later(busyUntil - now + TEMPO.releaseMs, () => { releaseTimer = null; release(); });
  }

  watch(options.events, (events, old) => {
    for (const entry of freshEntries(events, old?.[0])) {
      try {
        handle(entry);
      } catch {
        /* a picture is never worth an error */
      }
    }
  }, { flush: 'sync' });

  watch(options.speed, () => { if (stillNow()) flush(); });

  const shown = computed<BlackjackState | null>(() => {
    const game = options.game();
    return game ? shownState(game, hold.value) : null;
  });

  return {
    hold,
    holding: computed(() => hold.value !== null),
    shown,
    tags, lastAction, dealerLine, banner,
    flush,
    cancel: () => { flush(); },
  };
}
