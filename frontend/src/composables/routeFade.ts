// Cross-fade between routed views. The leaving view stays mounted until its
// fade is done, so completion must never depend on a single transitionend:
// it can be cancelled, or stolen by a transition bubbling from a child. A
// timeout fallback guarantees the old view is always removed.
//
// A navigation can also land in the middle of a fade (Back right after a
// click). Two rules keep the screen to one view at rest:
//  - a view that is still fading in and is now removed (Vue calls leave on it)
//    gets its enter fade cancelled without completing, so the stale enter can
//    never reset the styles of the leaving view and flash it back in place;
//  - a view still leaving from an earlier navigation is removed right away
//    when the next navigation starts, instead of fading on top of the others.
export const ROUTE_FADE_MS = 800;
const FALLBACK_MARGIN_MS = 200;

interface Fade {
  el: HTMLElement;
  leaving: boolean;
  epoch: number;
  /** Stops the timers and listeners without calling the completion callback. */
  cancel: () => void;
  /** Ends the fade now and calls the completion callback. */
  settle: () => void;
}

const fades = new Map<HTMLElement, Fade>();

// All the hooks of one navigation run in the same task (a single render flush);
// the epoch tells those apart from the hooks of a later navigation.
let epoch = 0;
let epochOpen = false;
function currentEpoch() {
  if (!epochOpen) {
    epoch++;
    epochOpen = true;
    queueMicrotask(() => { epochOpen = false; });
  }
  return epoch;
}

function startFade(div: HTMLElement, leaving: boolean, finish: () => void) {
  const thisEpoch = currentEpoch();
  fades.get(div)?.cancel();
  // Views still leaving from an earlier navigation: gone now.
  for (const fade of [...fades.values()]) {
    if (fade.leaving && fade.el !== div && fade.epoch < thisEpoch) fade.settle();
  }

  const opacity = leaving ? '0' : '1';
  let ended = false;
  const stop = () => {
    ended = true;
    clearTimeout(fallback);
    div.removeEventListener('transitionend', onEnd);
    if (fades.get(div) === fade) fades.delete(div);
  };
  const settle = () => {
    if (ended) return;
    stop();
    finish();
  };
  const onEnd = (e: Event) => {
    if (e.target === div) settle();
  };
  const fade: Fade = { el: div, leaving, epoch: thisEpoch, cancel: stop, settle };
  fades.set(div, fade);
  // Armed immediately: it must not depend on the animation frames below,
  // which can be throttled.
  const fallback = setTimeout(settle, ROUTE_FADE_MS + FALLBACK_MARGIN_MS);
  requestAnimationFrame(() => requestAnimationFrame(() => {
    if (ended) return;
    div.style.transition = `opacity ${ROUTE_FADE_MS}ms ease`;
    div.style.opacity = opacity;
    div.addEventListener('transitionend', onEnd);
  }));
}

export function fadeIn(el: Element, done: () => void) {
  const div = el as HTMLElement;
  div.style.willChange = 'opacity';
  div.style.position = 'absolute';
  div.style.inset = '0';
  div.style.zIndex = '1';
  div.style.opacity = '0';
  startFade(div, false, () => {
    div.style.cssText = '';
    done();
  });
}

export function fadeOut(el: Element, done: () => void) {
  const div = el as HTMLElement;
  // Interrupting an enter: start from where it got to, without a jump.
  if (fades.get(div)) {
    div.style.transition = 'none';
    div.style.opacity = getComputedStyle(div).opacity;
  }
  div.style.willChange = 'opacity';
  div.style.position = 'absolute';
  div.style.inset = '0';
  div.style.zIndex = '0';
  startFade(div, true, done);
}
