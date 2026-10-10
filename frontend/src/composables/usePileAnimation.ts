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
