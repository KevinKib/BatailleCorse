import { cardAt, cardIn, visibleRect, type MotionRect, type MotionTarget } from './rect';

/** What a resolver measures with: the elements inside the table's root, and the shared helpers. */
export interface DomQuery {
  find(selector: string): HTMLElement | null;
  findAll(selector: string): HTMLElement[];
  /** `visibleRect`: null when the element is missing or has no size. */
  rect(el: Element | null | undefined): MotionRect | null;
  cardIn(host: Element | null, fallbackWidth?: number): MotionTarget | null;
  cardAt(box: MotionRect, width: number): MotionRect;
}

/** One resolver per anchor kind; `q` measures inside the table's root element. */
export type AnchorResolvers<A extends { kind: string }> = {
  [K in A['kind']]: (anchor: Extract<A, { kind: K }>, q: DomQuery) => MotionTarget | null;
};

/**
 * Turns a table's named places (its anchors) into rectangles on screen, one resolver per kind of anchor (a missing
 * kind is a compile error). A resolver that throws, or finds nothing, gives null: the layer skips that picture.
 */
export function defineAnchors<A extends { kind: string }>(
  root: () => HTMLElement | null,
  resolvers: AnchorResolvers<A>,
): (anchor: A) => MotionTarget | null {
  const query: DomQuery = {
    find: selector => root()?.querySelector<HTMLElement>(selector) ?? null,
    findAll: selector => Array.from(root()?.querySelectorAll<HTMLElement>(selector) ?? []),
    rect: visibleRect,
    cardIn,
    cardAt,
  };
  return anchor => {
    try {
      const resolve = resolvers[anchor.kind as A['kind']] as ((a: A, q: DomQuery) => MotionTarget | null) | undefined;
      return resolve?.(anchor, query) ?? null;
    } catch {
      return null;
    }
  };
}
