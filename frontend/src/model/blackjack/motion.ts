import type { TableMovement } from '../../animation';
import type Card from '../Card';
import type { BlackjackEventData, BlackjackPhase } from './BlackjackState';
import { DEALER_KEY, handKey } from './shown';
import { BLACKJACK_TEMPO as TEMPO } from './tempo';

/**
 * A place of the Blackjack table, as the screen names it (it turns it into a rectangle): the shoe, or one card place
 * of a hand or of the dealer (`<place>:<index>`, the index being the position the card takes in the row; see `slotAnchor`).
 */
export type BlackjackAnchor = 'shoe' | `${string}:${number}`;

export type BlackjackMovement = TableMovement<BlackjackAnchor>;

/** One event at the table, for the pictures and the live region. Empty after a first state. */
export interface BlackjackTableEvent {
  /** Increases with every entry of the session, so two equal events are two entries. */
  id: number;
  eventType: string;
  data: BlackjackEventData | null;
}

export const slotAnchor = (key: string, index: number): BlackjackAnchor => `${key}:${index}`;

/** What a seat says in a tag over its plate. */
export type TagKind = 'hit' | 'stand' | 'double' | 'split' | 'bust';

/** What the dealer's status line says. */
export type DealerLineKind = 'reveals' | 'draws' | 'stands' | 'busts' | 'blackjack';

/** The phases announced by the banner. */
export type BannerKind = 'bets' | 'deal' | 'dealer' | 'results';

const TAGS: Record<string, TagKind> = { HIT: 'hit', STAND: 'stand', DOUBLE: 'double', SPLIT: 'split', BUST: 'bust' };
export const tagKindOf = (eventType: string): TagKind | null => TAGS[eventType] ?? null;

/** One beat of a script: what the table does at `atMs` after the script starts. */
export type Effect =
  /** The bets of the table are told together. */
  | { type: 'unseal'; bets: Record<number, number> }
  /** The table is cleared for a new deal: these places are empty again, the others are gone. */
  | { type: 'clear'; keys: string[]; handNumber: number }
  /** A place with no card yet appears (the second hand of a split). */
  | { type: 'open'; key: string }
  /** A card has landed on a place. */
  | { type: 'land'; key: string }
  /** A card has left a place (the split moves one). */
  | { type: 'leave'; key: string }
  /** The dealer's second card is face up from now on. */
  | { type: 'hole' }
  | { type: 'meta'; phase: BlackjackPhase; currentSeat: number; activeHandIndex: number }
  | { type: 'tag'; seat: number; kind: TagKind }
  | { type: 'dealer'; kind: DealerLineKind; total: number }
  | { type: 'banner'; kind: BannerKind }
  /** The nets of the hands and the stacks are told. */
  | { type: 'settle' };

export interface ScriptStep {
  atMs: number;
  effect: Effect;
  /** A card that moves, starting at `atMs` (its `delayMs` is 0); the step that lands it is the `land` effect. */
  flight?: BlackjackMovement;
}

export interface Script {
  steps: ScriptStep[];
  /** When the next event may start, counted from the start of this one. */
  spanMs: number;
}

export interface ScriptContext {
  mySeat: number;
  /** The cards each place will hold once everything planned so far has landed. Updated by the script. */
  planned: Record<string, number>;
  /** The face of the card a place will hold at an index in the latest state, null for a back. */
  faceAt: (key: string, index: number) => Card | null;
}

const none: Script = { steps: [], spanMs: 0 };

function travel(from: BlackjackAnchor, to: BlackjackAnchor, actor: number, mySeat: number, durationMs: number, card: Card | null): BlackjackMovement {
  return {
    kind: 'travel', from, to, card, flip: false, turnDown: false, actor, byOpponent: actor >= 0 && actor !== mySeat,
    delayMs: 0, durationMs, holdAfterMs: 120, marks: [],
  };
}

function sortSteps(steps: ScriptStep[]): ScriptStep[] {
  return steps.map((s, i) => ({ s, i })).sort((a, b) => a.s.atMs - b.s.atMs || a.i - b.i).map(x => x.s);
}

export function scriptFor(event: BlackjackTableEvent, ctx: ScriptContext): Script {
  const data = event.data;
  if (!data) return none;
  switch (event.eventType) {
    case 'DEAL': return dealOf(data, ctx);
    case 'HIT': return drawOf(data, ctx, 'hit');
    case 'DOUBLE': return drawOf(data, ctx, 'double');
    case 'SPLIT': return splitOf(data, ctx);
    case 'STAND': return sayOf(data, ctx, 'stand');
    case 'BUST': return sayOf(data, ctx, 'bust');
    case 'DEALER_PLAY': return dealerOf(data, ctx, data.cards ?? [], data.value ?? 0);
    case 'DEALER_BLACKJACK': return dealerOf(data, ctx, null, 0);
    case 'SETTLE': return {
      steps: [
        { atMs: 0, effect: { type: 'banner', kind: 'results' } },
        { atMs: 0, effect: { type: 'settle' } },
      ],
      spanMs: TEMPO.results.holdMs,
    };
    default: return none;
  }
}

const turnOf = (seat: number, handIndex: number): Effect =>
  ({ type: 'meta', phase: 'PLAYING', currentSeat: seat, activeHandIndex: handIndex });

function dealOf(data: BlackjackEventData, ctx: ScriptContext): Script {
  const bettors = Object.keys(data.bets ?? {}).map(Number).sort((a, b) => a - b);
  if (bettors.length === 0) return none;
  const button = data.button ?? 0;
  const first = bettors.findIndex(s => s >= button);
  const order = first < 0 ? bettors : [...bettors.slice(first), ...bettors.slice(0, first)];
  const keys = [...order.map(s => handKey(s, 0)), DEALER_KEY];
  const start = TEMPO.bets.revealMs + TEMPO.bets.clearMs;
  const steps: ScriptStep[] = [
    { atMs: 0, effect: { type: 'unseal', bets: data.bets ?? {} } },
    { atMs: 0, effect: { type: 'banner', kind: 'bets' } },
    { atMs: TEMPO.bets.revealMs, effect: { type: 'clear', keys, handNumber: data.handNumber ?? 0 } },
    { atMs: TEMPO.bets.revealMs, effect: { type: 'banner', kind: 'deal' } },
  ];
  for (const key of keys) ctx.planned[key] = 0;
  [...keys, ...keys].forEach((key, i) => {
    const at = start + i * TEMPO.deal.staggerMs;
    const actor = -1;
    const index = ctx.planned[key] ?? 0;
    ctx.planned[key] = index + 1;
    const isHole = key === DEALER_KEY && index === 1;
    const face = isHole ? null : ctx.faceAt(key, index);
    steps.push({ atMs: at, effect: { type: 'meta', phase: 'BETTING', currentSeat: -1, activeHandIndex: 0 },
      flight: travel('shoe', slotAnchor(key, index), actor, ctx.mySeat, TEMPO.deal.flightMs, face) });
    steps.push({ atMs: at + TEMPO.deal.flightMs, effect: { type: 'land', key } });
  });
  const end = start + (keys.length * 2 - 1) * TEMPO.deal.staggerMs + TEMPO.deal.flightMs;
  steps.push({ atMs: end, effect: { type: 'meta', phase: 'PLAYING', currentSeat: -1, activeHandIndex: 0 } });
  return { steps: sortSteps(steps), spanMs: end + TEMPO.deal.endMs };
}

function drawOf(data: BlackjackEventData, ctx: ScriptContext, kind: 'hit' | 'double'): Script {
  if (data.seat === null) return none;
  const handIndex = data.handIndex ?? 0;
  const key = handKey(data.seat, handIndex);
  const { tagLeadMs, flightMs, endMs } = TEMPO.draw;
  const index = ctx.planned[key] ?? 0;
  ctx.planned[key] = index + 1;
  return {
    steps: [
      { atMs: 0, effect: turnOf(data.seat, handIndex) },
      { atMs: 0, effect: { type: 'tag', seat: data.seat, kind } },
      { atMs: tagLeadMs, effect: turnOf(data.seat, handIndex),
        flight: travel('shoe', slotAnchor(key, index), data.seat, ctx.mySeat, flightMs, data.card) },
      { atMs: tagLeadMs + flightMs, effect: { type: 'land', key } },
    ],
    spanMs: tagLeadMs + flightMs + endMs,
  };
}

function sayOf(data: BlackjackEventData, ctx: ScriptContext, kind: 'stand' | 'bust'): Script {
  void ctx;
  if (data.seat === null) return none;
  return {
    steps: [
      { atMs: 0, effect: turnOf(data.seat, data.handIndex ?? 0) },
      { atMs: 0, effect: { type: 'tag', seat: data.seat, kind } },
    ],
    spanMs: TEMPO.say.spanMs,
  };
}

function splitOf(data: BlackjackEventData, ctx: ScriptContext): Script {
  if (data.seat === null) return none;
  const seat = data.seat;
  const first = handKey(seat, 0);
  const second = handKey(seat, 1);
  const { tagLeadMs, flightMs, endMs } = TEMPO.split;
  // `cards` is the flat list the two hands hold right after the split: hand 0 (the pair's first card and its new
  // card) then hand 1 (the pair's second card, plus its new card for split Aces).
  const cards = data.cards ?? [];
  const steps: ScriptStep[] = [
    { atMs: 0, effect: turnOf(seat, 0) },
    { atMs: 0, effect: { type: 'tag', seat, kind: 'split' } },
    { atMs: tagLeadMs, effect: { type: 'open', key: second } },
    { atMs: tagLeadMs, effect: { type: 'leave', key: first },
      flight: travel(slotAnchor(first, 1), slotAnchor(second, 0), seat, ctx.mySeat, flightMs, cards[2] ?? null) },
    { atMs: tagLeadMs + flightMs, effect: { type: 'land', key: second } },
    { atMs: tagLeadMs + flightMs, effect: { type: 'meta', phase: 'PLAYING', currentSeat: seat, activeHandIndex: 0 },
      flight: travel('shoe', slotAnchor(first, 1), seat, ctx.mySeat, flightMs, cards[1] ?? null) },
    { atMs: tagLeadMs + 2 * flightMs, effect: { type: 'land', key: first } },
  ];
  let end = tagLeadMs + 2 * flightMs;
  ctx.planned[first] = 2;
  ctx.planned[second] = 1;
  if (cards.length > 3) {
    steps.push({ atMs: end, effect: { type: 'meta', phase: 'PLAYING', currentSeat: seat, activeHandIndex: 0 },
      flight: travel('shoe', slotAnchor(second, 1), seat, ctx.mySeat, flightMs, cards[3]) });
    steps.push({ atMs: end + flightMs, effect: { type: 'land', key: second } });
    ctx.planned[second] = 2;
    end += flightMs;
  }
  return { steps, spanMs: end + endMs };
}

function dealerOf(data: BlackjackEventData, ctx: ScriptContext, drawn: Card[] | null, total: number): Script {
  const { bannerMs, revealMs, afterRevealMs, drawGapMs, flightMs, endMs } = TEMPO.dealer;
  const reveal: BlackjackMovement = {
    kind: 'reveal', from: slotAnchor(DEALER_KEY, 1), to: slotAnchor(DEALER_KEY, 1), card: data.holeCard, flip: false,
    turnDown: false, actor: -1, byOpponent: false, delayMs: 0, durationMs: revealMs, holdAfterMs: 0, marks: [],
  };
  const steps: ScriptStep[] = [
    { atMs: 0, effect: { type: 'meta', phase: 'PLAYING', currentSeat: -1, activeHandIndex: 0 } },
    { atMs: 0, effect: { type: 'banner', kind: 'dealer' } },
    { atMs: bannerMs, effect: { type: 'dealer', kind: 'reveals', total: 0 }, flight: reveal },
    // The card shows its face when the turn is half done: the back is narrowest.
    { atMs: bannerMs + Math.round(revealMs * 0.45), effect: { type: 'hole' } },
  ];
  let at = bannerMs + revealMs + afterRevealMs;
  if (drawn === null) {
    steps.push({ atMs: at, effect: { type: 'dealer', kind: 'blackjack', total } });
    return { steps, spanMs: at + endMs };
  }
  drawn.forEach((card, i) => {
    const index = ctx.planned[DEALER_KEY] ?? 2;
    ctx.planned[DEALER_KEY] = index + 1;
    if (i > 0) at += drawGapMs;
    steps.push({ atMs: at, effect: { type: 'dealer', kind: 'draws', total: 0 },
      flight: travel('shoe', slotAnchor(DEALER_KEY, index), -1, ctx.mySeat, flightMs, card) });
    steps.push({ atMs: at + flightMs, effect: { type: 'land', key: DEALER_KEY } });
  });
  if (drawn.length > 0) at += flightMs + 400;
  steps.push({ atMs: at, effect: { type: 'dealer', kind: total > 21 ? 'busts' : 'stands', total } });
  return { steps: sortSteps(steps), spanMs: at + endMs };
}
