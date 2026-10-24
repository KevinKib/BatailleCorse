# Mangola: visible seeds and exact counts (design notes)

Date: 2026-10-24. Status: option A chosen by the owner and implemented (PR #318): the badge shows on every non-empty
pit (no threshold), and a pile of 17 or more reads as a pile with its exact count on the badge.

Canvas: https://claude.ai/artifact/Q8psFVR5zdyJSWvbKpDNnN (today's rendering plus options A, B and C, on a real
64-seed position, at 375 × 667 and 1280 × 720, felt and a light variant, with the sowing animation in four steps).

## The owner's feedback (human playtest)

> aussi il faudrait voir le vrai nombre de billes quand il y a écrit '9' ou 12 il faudrait quand même voir les
> billes physiquement, c'est perturbant sinon pour les utilisateurs. je pense qu'il faudrait quand même une
> solution pour voir le nombre à côté, mais je te laisse suggérer des modifs pour ça

## Today

- `MangolaPit.vue` draws exact dice-like dots on a 3 × 3 grid up to 8 seeds (`seedDisplay`, `MAX_DOTS = 8` in
  `model/mangola/board.ts`); from 9 the dots are replaced by the written number. The count of a dotted pit shows
  only on hover or focus (a pill in the top-right corner). This is the rule chosen on 2026-10-15
  (`docs/specs/2026-10-15-mangola-design.md`, UI decision 1b).
- Sizes: the board is `min(100%, 560px)` wide, eight cells with no gap, each pit button is its whole cell. At
  375 px a cell is 45.9 px (bowl 84 % = 38.6 px, a dot 6.8 px); at 1280 px a cell is 70 px (bowl 58.8 px, a dot
  10.4 px). The app has only the dark felt look; there is no light theme.
- One pit can hold up to 64 seeds; the whole board always holds exactly 64, so drawing every seed costs at most
  64 elements for the whole board, whatever the position.
- Sowing: a gold "hand" pill with the seeds it holds slides from pit to pit over the centre of the cell; the
  counts change step by step (`replay.ts`, `trace.ts`); reduced motion shows the final board at once.
- Accessibility: each pit is a real button whose name says the count (`Pit d4, 30 seeds, can sow`); dots and
  numbers are `aria-hidden`. This does not change in any option.

## Options

- **A. Growing pile + corner count (recommended).** Dice layout up to 8 (as today), then a sunflower pile that
  grows outward from the centre; seed size steps down only past 16 and past 32 (16, 32 and 64 fill the bowl),
  so seeds already in a pit never move when one arrives. Exact count in a small badge (14 px phone, 18 px
  desktop) in the top-right corner of the cell, always visible, gold for a moment when the count changes.
- **B. Fixed-size seeds in layers + count under the pit.** Seeds keep today's size; from 9 a honeycomb layer
  of 13, then layers of 12 or 13 stacked on top (lower layers darker), 50 drawn at most. The count is written
  under each pit, which makes every row 13 px taller on the phone (+52 px for the board).
- **C. Loose scatter + count on demand.** Seeds scattered like a real board (deterministic, a seed never moves
  once placed), shrinking past 12, 24 and 40. No number by default: hover or focus shows it as today, and a
  "Counts" toggle under the board writes all counts on the pits.

In A, B and C the sowing hand moves from the centre of the pit to its top edge, so the arriving seed (a white
halo for one step) stays visible.

## Recommendation: A

- Readability: every seed is visible (the owner's request), up to 8 they are still countable at a glance, and
  the exact number is always there without a gesture. Touch screens have no hover, which rules out C as the
  only way to read a count.
- No layout shift: the pit and the board keep their size (B makes the board taller); the badge sits in the
  corner of the cell, which the round bowl leaves empty and the direction chevrons (edge middles) do not use.
- Tap targets unchanged: the whole 45.9 px cell stays the button.
- Performance: at most 64 absolutely positioned dots on the whole board; positions are computed, no layout
  work per frame.
- Stillness: seed positions depend only on their index and the size band, so a drop adds one seed and nothing
  moves, except all seeds of a pile shrinking once when it passes 16 or 32.
- Dark mode: the badge is a dark pill with white text on the felt (and a dark pill on the light variant), more
  than 4.5:1 in both.

## Questions for the owner

1. Which option (A, B or C), or a mix (for example A's pile with C's on-demand count)?
2. In A, should the badge show for every non-empty pit, or only from a threshold (5 or 9, so small pits stay
   plain dots)? The canvas has a tweak for it.
3. Is it acceptable for a big pile (17 and more) to be seen as a pile rather than counted seed by seed, the
   exact count being on the badge?

## Scope of the implementation

Frontend only (`MangolaPit.vue`, `model/mangola/board.ts`, their tests, and the hand position in
`MangolaBoard.vue`); no backend or domain rule changes: it is presentation of counts the server already sends.
