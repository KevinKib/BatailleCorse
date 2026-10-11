// Cross-fade between routed views. The leaving view stays mounted until its
// fade is done, so completion must never depend on a single transitionend:
// it can be cancelled, or stolen by a transition bubbling from a child. A
// timeout fallback guarantees the old view is always removed.
export const ROUTE_FADE_MS = 800;
const FALLBACK_MARGIN_MS = 200;

function fadeTo(div: HTMLElement, opacity: '0' | '1', finish: () => void) {
  let settled = false;
  const settle = () => {
    if (settled) return;
    settled = true;
    clearTimeout(fallback);
    div.removeEventListener('transitionend', onEnd);
    finish();
  };
  const onEnd = (e: Event) => {
    if (e.target === div) settle();
  };
  // Armed immediately: it must not depend on the animation frames below,
  // which can be throttled.
  const fallback = setTimeout(settle, ROUTE_FADE_MS + FALLBACK_MARGIN_MS);
  requestAnimationFrame(() => requestAnimationFrame(() => {
    if (settled) return;
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
  fadeTo(div, '1', () => {
    div.style.cssText = '';
    done();
  });
}

export function fadeOut(el: Element, done: () => void) {
  const div = el as HTMLElement;
  div.style.willChange = 'opacity';
  div.style.position = 'absolute';
  div.style.inset = '0';
  div.style.zIndex = '0';
  fadeTo(div, '0', done);
}
