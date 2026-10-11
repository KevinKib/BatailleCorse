const FLIGHT_MS = 380;
const STAGGER_MS = 70;

export function prefersReducedMotion(): boolean {
  try {
    return typeof window.matchMedia === 'function'
      && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  } catch {
    return false;
  }
}

// Flies a copy of each source card (a hand card) to the pile. The copies are fixed-position
// ghosts on <body>, so the layout never moves (no scroll, no reflow) and they vanish when
// landed. Returns how many ghosts were launched: 0 under reduced motion, or without a target
// or Web Animations support. The pile itself is rendered by the game state, not by this.
export function flyCardsToPile(
  sources: HTMLElement[],
  target: Pick<DOMRect, 'left' | 'top' | 'width'> | null,
  opts: { reduceMotion: boolean },
): number {
  if (opts.reduceMotion || !target || sources.length === 0) return 0;
  if (typeof HTMLElement.prototype.animate !== 'function') return 0;

  let launched = 0;
  sources.forEach((source, i) => {
    const from = source.getBoundingClientRect();
    if (from.width === 0) return;
    const ghost = (source.querySelector('.playing_card') ?? source).cloneNode(true) as HTMLElement;
    ghost.setAttribute('data-test', 'flying-card');
    ghost.setAttribute('aria-hidden', 'true');
    Object.assign(ghost.style, {
      position: 'fixed', left: '0', top: '0', margin: '0',
      width: from.width + 'px', height: from.height + 'px',
      transformOrigin: 'top left', pointerEvents: 'none', zIndex: '3000', willChange: 'transform',
    });
    document.body.appendChild(ghost);

    const scale = target.width / from.width;
    // Cards land slightly fanned, like cards dropped on a pile.
    const tilt = (i % 2 === 0 ? 1 : -1) * (3 + i);
    const animation = ghost.animate(
      [
        { transform: `translate(${from.left}px, ${from.top}px) scale(1) rotate(0deg)`, opacity: 1 },
        { transform: `translate(${target.left}px, ${target.top}px) scale(${scale}) rotate(${tilt}deg)`, opacity: 1, offset: 0.92 },
        { transform: `translate(${target.left}px, ${target.top}px) scale(${scale}) rotate(${tilt}deg)`, opacity: 0 },
      ],
      { duration: FLIGHT_MS, delay: i * STAGGER_MS, easing: 'cubic-bezier(0.22, 0.8, 0.3, 1)', fill: 'both' },
    );
    const remove = () => ghost.remove();
    animation.finished.then(remove, remove);
    launched++;
  });
  return launched;
}

const TAKE_MS = 460;
const TAKE_STAGGER_MS = 90;
const TAKE_GHOSTS = 3;

// Flies a few face-down copies of the pile to the player who takes it (a seat or my own zone):
// they shrink and fade as they arrive, centered on the destination. Same technique as
// flyCardsToPile (fixed ghosts on <body>, no layout change). Returns the ghosts launched: 0 under
// reduced motion, without a source or target, or without Web Animations support.
export function flyPileToTaker(
  source: HTMLElement | null,
  target: Pick<DOMRect, 'left' | 'top' | 'width' | 'height'> | null,
  pileSize: number,
  opts: { reduceMotion: boolean },
): number {
  if (opts.reduceMotion || !source || !target || pileSize <= 0) return 0;
  if (typeof HTMLElement.prototype.animate !== 'function') return 0;
  const from = source.getBoundingClientRect();
  if (from.width === 0) return 0;

  const count = Math.min(TAKE_GHOSTS, pileSize);
  const scale = Math.min(1, Math.max(0.3, target.width / from.width));
  const toLeft = target.left + target.width / 2 - from.width / 2;
  const toTop = target.top + target.height / 2 - from.height / 2;
  for (let i = 0; i < count; i++) {
    const ghost = (source.querySelector('.playing_card') ?? source).cloneNode(true) as HTMLElement;
    ghost.setAttribute('data-test', 'flying-pile');
    ghost.setAttribute('aria-hidden', 'true');
    Object.assign(ghost.style, {
      position: 'fixed', left: '0', top: '0', margin: '0',
      width: from.width + 'px', height: from.height + 'px',
      transformOrigin: 'center', pointerEvents: 'none', zIndex: '3000', willChange: 'transform',
    });
    document.body.appendChild(ghost);
    const tilt = (i % 2 === 0 ? 1 : -1) * i * 6;
    const animation = ghost.animate(
      [
        { transform: `translate(${from.left}px, ${from.top}px) scale(1) rotate(0deg)`, opacity: 1 },
        { transform: `translate(${toLeft}px, ${toTop}px) scale(${scale}) rotate(${tilt}deg)`, opacity: 1, offset: 0.9 },
        { transform: `translate(${toLeft}px, ${toTop}px) scale(${scale}) rotate(${tilt}deg)`, opacity: 0 },
      ],
      { duration: TAKE_MS, delay: i * TAKE_STAGGER_MS, easing: 'cubic-bezier(0.5, 0, 0.75, 0.3)', fill: 'both' },
    );
    const remove = () => ghost.remove();
    animation.finished.then(remove, remove);
  }
  return count;
}
