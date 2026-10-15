# Mangola: design

Plan: `docs/plans/2026-10-15-mangola-plan.md`. Written by Claude Opus 5.5 on 2026-10-15 from `origin/main` at
`28c5832`. A fifth game: the four-row sowing (mancala) game the owner learned in their youth in the Congo
and wrote in JavaScript (`KevinKib/Ngola`, `script/`). Two players, real time, bots with levels, a **free
initial placement** of each player's 32 seeds, played like Three Men's Morris (seat game, lobby options,
bots as a seat option, forfeit, presence and rematch through the shared mechanisms).

Legend: **[V]** verified by reading code (this repository at `28c5832`, or the JS repository, with file
names); **[S]** stated by a written source (link in "Name and origin"); **[O]** owner's answer;
**[C]** computed by a throwaway simulation of the owner's rules (a port of `ngola.js`, not committed; the
domain PR re-checks every figure that a test relies on); **[H]** hypothesis to confirm in the PR that relies
on it; **[Q]** product question, listed at the end with a recommendation.

## Key decisions

1. **The rules are the owner's, as coded in `ngola.js` [V] and confirmed by the owner [O]**, and they match
   the documented **Mangola** of the Democratic Republic of the Congo almost rule for rule [S] (see "Name and
   origin"). The engine is a pure domain context `mangola` (new bounded context, like `threemenmorris`), with
   the board geometry and the rule switches held as data (`MangolaRules`), so the documented neighbours
   (Mangola as reported in 1952, Omweso, Kiela, Igisoro) can later be offered as variants without a rewrite.
   Version 1 offers **one rule set** (the owner's) and one host option, who starts. The set-up is
   always free placement (owner decision, 2026-10-15: "le set up sera toujours le free placement, jamais autre
   chose"), so there is no set-up option.
2. **Free initial placement is a game phase in the domain.** Each player distributes exactly 32 seeds over
   their own 16 pits; both arrange **at the same time and hidden from each other**, confirm, and may take the
   confirmation back until the other has confirmed too; both layouts are revealed together and play starts.
   Presets (checkerboard, random, two per pit) only fill the editor; they are not rules **[Q2, Q3]**.
3. **A move is computed whole on the server and returned as a trace.** One command `sow(pit)`; the
   aggregate runs the full relay sowing (laps, the empty-pit rule, captures, re-sowing) and records each
   step. The DTO carries the resulting board, and the `SOWN` event carries the trace so the client animates
   it; the client never computes a rule.
4. **Endless sowings are detected exactly and are not playable.** Some sowings never end (a relay cycle on
   one's own side, found by the simulation [C]). The JS stops after 100,000 seeds and ends the game with no
   winner [V]; we recommend instead that a pit whose sowing never ends is **not a legal move** (detected by a
   cycle check at lap boundaries, with the JS cap kept as a safety net), so that "a player who cannot move
   loses" stays the only way to end a game, and a losing player cannot escape into a draw **[Q1]**. The draw
   outcome (as in the JS) is the alternative and costs the same.
5. **Bots reuse the Morris plumbing; no perfect bot.** Seat option `level` (`random`, `greedy`, `expert`);
   the expert is an alpha-beta search with iterative deepening under a time budget. An exact solver is out
   of reach (the state space is far beyond a retrograde table, unlike Three Men's Morris), and we say so in
   the level names and the rules panel. Bots also arrange their seeds in the placement phase.

## What exists today

### The owner's JavaScript [V]

`KevinKib/Ngola`, `script/`:

- `case.js`: a pit (`Case`) knows its name (`a1`..`h4`), its owner (rows 1-2: player 1, rows 3-4: player 2),
  its seeds (`nbBilles`, **2 by default**), its next pit, its attacked pit and its linked pit. It is playable
  when it holds more than one seed (`peutJouer`: `nbBilles > 1`).
- `plateau.js`: builds the 32 pits and the links. Player 1's loop: `a2 → b2 → … → h2 → h1 → g1 → … → a1 → a2`;
  player 2's loop: `a4 → … → h4 → h3 → … → a3 → a4`. Rows 2 and 3 are the **inner rows** and attack each
  other in the same column (`c2` attacks `c3`, `c3` attacks `c2`); the linked pit is the outer pit of the
  same column (`c3`'s is `c4`, `c2`'s is `c1`). `setCases` refuses a half-board that does not hold exactly
  32 seeds.
- `ngola.js` (`play`): the move algorithm transcribed in "Rules" below; `listeLegalMoves` (own pits with at
  least two seeds); the winner is the opponent of a player who has no legal move after a move
  (`actualiserVainqueur`); an endless-loop counter (`MAX_REPEATS = 100000` sown seeds) ends the game with no
  winner (`__vainqueur = null`, `enJeu` false); player 1 starts (`premierJoueurAleatoire` exists, unused).
  Two set-ups: `initialiserBillesPlateauClassique` (4 seeds in every pit where `(column + row)` is even:
  a checkerboard of 4s and 0s) and `initialiserAleatoirementIndex` (32 seeds dropped at random on a side).
  The random set-up has a bug: `Math.floor(Math.random() * (16 - 1))` never picks the 16th pit (`h2` for
  player 1, `h4` for player 2); not ported.
- `ai.js` (random move, random set-up), `ai_minimax.js` (alpha-beta with depth 0 to 3, evaluation =
  seed difference plus ±1000 for a finished game, `evaluation.js`), `human.js` (console input),
  `main.js` (AI against AI, 1000-move cap). The minimax mixes two sign conventions (the root maximises
  values computed from the child's point of view and the leaf multiplies by the colour of a flag that
  flips at every level): it is a reference for ideas, not code to port.
- `todo`: "change the number of seeds at the start, chosen by the players" is open: free placement is the
  missing feature the owner asked for [O].
- `rapport.txt`: an unfinished report; random-start statistics there (5 moves per game on average) predate
  the current rules and are not reproduced by our simulation (median 45 plies with random players [C]).

### This repository [V]

- Three Men's Morris is the model: `threemenmorris/` (domain, bots, presentation), `config/ThreeMenMorrisConfig.java`,
  its spec and plans (`docs/specs/2026-10-14-three-men-morris-v2-design.md`). Variants as data
  (`MorrisRules`, `MorrisVariant`), host options (`variant`, `hostColour`) through `GameFactory.optionDescriptors`,
  bot level as a seat option, a `RandomGenerator` bean that is constant under the `test` profile, a bot
  coordinator that schedules after a `ThinkingDelay` and re-checks the game version under the monitor,
  draws as `Finished(null, reason)` and the shared end screen's `result: 'draw'` (`components/EndGameOverlay.vue`).
- The checklist `docs/architecture/add-a-game.md` and the names of `docs/architecture/game-conventions.md`
  apply as they are; the only new name is `mangola`.
- Hidden information per seat already exists (Kobo's cards): `forViewer` is the only projection and hides
  what a seat must not see. The placement phase uses the same pattern.
- Every DTO carries `version`; the clients drop stale states by version (#214).
- Open PR #216 (President, docs) will also produce an entry PR: only one entry PR may be open at a time
  (`add-a-game.md`, section 1).

## Name and origin

The owner first called the game "Ngola", learned it in their youth and believes it is Congolese (DR Congo or
Congo-Brazzaville), with no written source [O]. The owner decided on 2026-10-15 that the game is called
**Mangola** (id `mangola`), the documented DR Congo name. The research below looked for a documented game with the
owner's distinctive rule ("a last seed that would fall into an empty pit goes to the next pit and the turn
ends").

### Ranked candidates

| Rank | Name | Evidence | Confidence |
|------|------|----------|------------|
| 1 | **Mangola** (DR Congo, Kinshasa and the whole central basin; locally also "Angola", and listed under the DR Congo names "Ngola", "Mongola", "Amungola", "Mungolo") | Same board (4×8, two rows each, 64 seeds), **2 seeds per pit at the start** (the JS default in `case.js`), an opening that leaves **a checkerboard of 4s and empty pits** (the JS "classic" set-up), anticlockwise sowing, at least two seeds to move (singletons cannot move), **"if the last seed is dropped in an empty hole, it is moved to the next hole and the turn ends"** (the owner's rule, word for word), relay sowing, capture from a non-empty inner pit of **both** opposite pits, the next lap beginning again from where the capturing lap began, and "a player who cannot move loses". The seeds themselves are called *ngola* in the description. | **High** that the owner's game is Mangola or a local form of it; "Mangola" is a documented DR Congo name in the same family, without its own rule description. |
| 2 | Omweso (Uganda) and the Mongola it lists as a Congolese relative | Same board, territory loop, relay, inner-pit capture of both opposite pits with re-sowing from where the lap began, "last player able to move wins"; **free arrangement of each player's 32 seeds before play** (first player arranges, then the second). But the last seed in an empty pit simply ends the move, there are reverse (clockwise) captures, special opening sowings and special wins. | A close relative, not the game. Its set-up is the documented precedent for free placement. |
| 3 | Kiela (northern Angola, Kimbundu speakers) | 4 rows (of **10**), relay, inner-pit capture, "cannot move loses"; **expert players rearrange their seeds at their first turn**. The last seed in an empty pit ends the turn; a capture needs only the opposite inner pit (the outer one is taken too if occupied). | Related (same region and family), different game. |
| 4 | Igisoro (Rwanda, Burundi) | 4×8, relay, capture of both opposite pits re-sown from the start of the turn, but the **capture is optional** (*ndahise* "I pass", answered by *ndakubye*). | Related, different game; the precedent for an optional capture. |

**Nothing matches exactly.** Mangola as reported in 1952 differs from the owner's game in three points:
(a) its opening is fixed (2 per pit, then a compulsory redistributing first move into the checkerboard, and a
second move that must start from an end pit) where the owner wants free placement; the same source says
there are "probably more than 30" local variants with different openings, "some with simultaneous opening
moves"; (b) its capture also takes the contents of the capturing player's own pit, where the JS leaves them
in place; (c) the place where the captured seeds are re-sown is worded ambiguously ("the next lap after the
hole which effected the capture" in the wiki text, "after the last empty hole" in a paraphrase), where the
JS restarts from the pit where the capturing lap began (as Omweso does). These are the variant switches
listed under "Room for documented variants".

The name: Mangola is reported as possibly "the plural form of Angola", "a name also used for this game in
Kinshasa", or the plural of *N'gola* (a royal title, the word behind "Angola"); the seeds are called *ngola*
in the Kinshasa description, and *Ngola* is listed among the DR Congo names of mancala games. "Mangola" is
therefore a legitimate name of this family, and the one the owner chose (they first called it "Ngola").

### Sources

- Mancala World, "Mangola (traditional)", after Suzanne Comhaire-Sylvain, *Jeux congolais*, Zaïre 6 (4),
  1952, and Philip Townshend, *Les jeux de mancala au Zaïre, au Rwanda et au Burundi*, Cahiers du CEDAF 3,
  1977: <https://mancala.fandom.com/wiki/Mangola_(traditional)>
- Mancala World, "Names" (DR Congo: Amungola, Mangola, Mongola, Mungolo, Ngola…):
  <https://mancala.fandom.com/wiki/Names>
- Mancala World, "Traditional mancala games" (four-row games by region):
  <https://mancala.fandom.com/wiki/Traditional_mancala_games>
- Mancala World, "Kiela": <https://mancala.fandom.com/wiki/Kiela>; "Omweso":
  <https://mancala.fandom.com/wiki/Omweso>; "Embeli" (seeds of the *ngola* tree used as counters):
  <https://mancala.fandom.com/wiki/Embeli>
- Wikipedia, "Omweso": <https://en.wikipedia.org/wiki/Omweso>; "Igisoro":
  <https://en.wikipedia.org/wiki/Igisoro>; "List of mancala games":
  <https://en.wikipedia.org/wiki/List_of_mancala_games>
- BoardGameMatcher, "Mangola" (a paraphrase of the same 1952 rules): <https://boardgamematcher.com/game/mangola>
- Not read (cited by the above, for a later check): Comhaire-Sylvain 1952; Townshend 1977 and 1979
  (*Azania* 14); Larry Russ, *The Complete Mancala Games Book*, 2000, pp. 37-41; Bernardo Campos, *Kiela*, 1998.

### Naming decision [Q4]

- **Id `mangola`** (permanent: URLs, tokens, wire). Short, the owner's name, not the prefix of another id.
  Forms: `<pkg>` `mangola`, `<Pascal>` `Mangola`, `<camel>` `mangola` (`game-conventions.md`, section 1).
- **Display name "Mangola"** (decided by the owner on 2026-10-15); picker line "Four-row sowing game from the
  Congo"; the rules panel opens with "Also known in the DR Congo under other names (Ngola, Mongola, Amungola,
  Angola in Kinshasa)" and one sentence on its origin. We avoid "Angola" (the country) and
  "Mancala" (the family name, and a trademark of a commercial two-row game).

## Rules (the owner's game, version 1)

### Board

- 4 rows of 8 pits, columns `a` to `h`, rows `1` to `4` (the JS names, kept for tests and notation).
  Seat A owns rows 1 and 2, seat B rows 3 and 4. Rows 2 and 3 are the **inner rows** (facing the opponent),
  rows 1 and 4 the **outer rows**.
- Each seat sows along a closed loop of its own 16 pits, never into the opponent's half:
  seat A `a2 b2 c2 d2 e2 f2 g2 h2 h1 g1 f1 e1 d1 c1 b1 a1` then `a2` again; seat B
  `a4 b4 … h4 h3 g3 … a3` then `a4`. Seen from above, both loops run anticlockwise; seen by each player
  from their own side, their inner row runs from their right to their left [V, plateau.js].
- The pit **facing** an inner pit is the opponent's inner pit of the same column; behind it is the
  opponent's outer pit of the same column (`c2` faces `c3`, behind it `c4`; `c3` faces `c2`, behind it `c1`).
- 64 seeds: 32 per player. Seeds never leave the board: the total stays 64 (an invariant the tests assert).

### Who starts

A host option, `firstPlayer`: `host`, `guest`, `random`; default **`random`** (the JS always starts with
player 1, which is seat A) **[Q5]**. The draw happens once, in the factory, through the injected
`RandomGenerator` (constant under the `test` profile: the host starts). Seat A is always the player who
starts, so the board geometry is fixed and the DTO says which seat sits on which side.

### A move

On their turn a player picks one of their own pits holding **at least two seeds** that is a legal move (see
"Endless sowings"), and the move runs as follows (exactly `Ngola.play`, `ngola.js` [V]):

1. Take every seed of the chosen pit into the hand. The chosen pit is the **start of the current lap**.
2. While more than one seed is in hand, drop one seed into each next pit along the player's loop. A lap of
   16 or more seeds passes over its own start pit and drops into it like any other pit.
3. The **last seed** of the lap:
   - **Empty pit:** if the next pit is empty, the last seed is not dropped there but into the pit after it
     (empty or not), and **the turn ends**. No relay and no capture follow that drop [O: a rule of the
     real game, not a bug].
   - **Occupied pit:** the last seed is dropped there. Then:
     - **Capture** if that pit is in the player's inner row and **both** the facing pit and the pit behind
       it hold at least one seed: all their seeds are taken into the hand (the capturing pit keeps its
       seeds), and the player sows them from the pit **after the start of the current lap**; the start of
       the lap does not change. The capture is **automatic** (not a choice).
     - **Relay** otherwise (outer row, or a facing pit or the pit behind it empty): take every seed of that
       pit (including the last one) into the hand; this pit becomes the start of the new lap; go on at step 2.
4. When the turn ends, the other player is to move. If the other player has no legal move, the player
   who just moved **wins** (reason `NO_MOVE`).

Hand-checked examples (each an exact test of the domain PR; pits not listed are empty; the simulation
reproduces each one [C]):

| # | Before (seat A to move) | Sow | Trace | After |
|---|-------------------------|-----|-------|-------|
| E1 | `a2:3` | `a2` | 1 seed in `b2`, 1 in `c2`, last seed: `d2` is empty, so it goes to `e2`, end | `b2:1 c2:1 e2:1` |
| E2 | `a2:2 c2:3` | `a2` | `b2`+1; last seed into `c2` (now 4), `c3`/`c4` empty so relay 4 from `c2`: `d2 e2 f2`+1; last: `g2` empty, so `h2`, end | `b2:1 d2:1 e2:1 f2:1 h2:1` |
| E3 | `a2:2 c2:1`, B: `c3:2 c4:3` | `a2` | `b2`+1; last into `c2` (now 2), `c3` and `c4` occupied: capture 5; sow from after `a2`: `b2 c2 d2 e2`+1; last: `f2` empty, so `g2`, end | `b2:2 c2:3 d2:1 e2:1 g2:1`, B empty (A 8 seeds) |
| E4 | `a2:2 c2:1`, B: `c3:2` | `a2` | as E3 but `c4` empty: relay 2 from `c2`: `d2`+1; last: `e2` empty, so `f2`, end | `b2:1 d2:1 f2:1`, B `c3:2` |
| E5 | `g1:2 e1:1`, B: `e3:2 e4:2` | `g1` | `f1`+1; last into `e1` (outer row: no capture), relay 2: `d1`+1; last: `c1` empty, so `b1`, end | `b1:1 d1:1 f1:1`, B unchanged |
| E6 | `g2:3 a1:4` | `g2` | `h2`+1, `h1`+1 (wrap from row 2 to row 1); last: `g1` empty, so `f1`, end | `a1:4 f1:1 h1:1 h2:1` |
| E7 | B: `h4:2 g3:1`, A: `g2:1 g1:1`, **seat B to move** | `h4` | `h3`+1; last into `g3` (now 2), `g2`, `g1` occupied: capture 2; sow from after `h4`: `h3`+1; last into `g3` (now 3), `g2`/`g1` now empty: relay 3: `f3 e3`+1; last: `d3` empty, so `c3`, end | B `c3:1 e3:1 f3:1 h3:2`, A empty |
| E8 | `a2:17` | `a2` | 16 seeds round the loop back into `a2`, the 17th into `b2` (now 2), relay 2, and so on through `d2 f2 h2 g1 e1 c1 a1`; last: `b2` empty, so `c2`, end (33 seeds dropped) | `b1:2 d1:2 f1:2 h1:2 a2:2 c2:3 e2:2 g2:2` |
| E9 | `a2:2 c2:1 d2:1`, B: `c3:1 c4:2 d3:1 d4:1` | `a2` | capture `c3`+`c4` (3) from `c2`, sow from after `a2`, last into `d2`: capture `d3`+`d4` (2), sow again from after `a2`, last into `c2` (now 4): relay 4; last: `g2` empty, so `h2`, end | `b2:3 d2:3 e2:1 f2:1 h2:1`, B empty |
| E10 | `a2:2 d2:5` | `a2` | `b2`+1; last: `c2` empty, so into `d2` (now 6), end: no relay even though `d2` is occupied | `b2:1 d2:6` |

### Endless sowings

- A sowing can cycle for ever on the player's own side: with no capture, the side's seeds are a closed
  system, and relays can return to an earlier state. Example [C]: seat A `a1:1 c1:2 d1:1 e1:2 g1:1 b2:1
  d2:1 f2:1 g2:2 h2:1`, opponent's half empty, sow `c1`: after 120 laps (272 seeds dropped) the side, the pit
  and the hand are back to their start, for ever. (A cycle cannot include a capture: a capture strictly
  lowers the opponent's seeds, so no earlier state can come back.)
- In simulated games [C] (2,000 games per set-up and policy, random or 1-ply greedy players, set-ups
  checkerboard, two per pit and random), up to 0.4 % of games with random players reach an endless sowing
  (none with greedy players, who avoid them); finite sowings took at most 131 laps and 595 seeds dropped (median 2 to 4 laps,
  8 to 19 seeds); one endless sowing did not repeat a state within 20 million laps, so the cycle check must
  stay bounded.
- **Detection (domain):** at each lap boundary the state (the 32 pits, the pit in hand and the seeds in
  hand) is compared by Brent's cycle algorithm (constant memory); a repeat means endless. A hard cap of
  100,000 seeds dropped (the JS `MAX_REPEATS`) also counts as endless, so a request never runs away. Both
  are pure and cheap for real moves (at most a few hundred drops).
- **Rule [Q1], recommended:** a pit whose sowing is endless is **not a legal move**; it is not offered and
  `sow` refuses it (`EndlessSowingException`). Legal moves are therefore computed by simulating each
  candidate pit (at most 16 simulations, each bounded). A player whose every pit with two seeds or more is
  endless has no legal move and loses. **Alternative (the JS):** the move is played and the game ends as a
  draw (`ENDLESS_SOWING`); this lets a losing player pick a known endless pit to escape, and a bot would.
  Both are one switch in `MangolaRules`.

### End of the game

- **`NO_MOVE`:** the player to move has no legal move; the other player wins. At the start no player can be
  without a pit to sow: 32 seeds on 16 pits always leave a pit with two or more (pigeonhole). With the
  recommended endless rule, a revealed layout whose every candidate pit is endless would lose at once; it
  needs a deliberate layout (the endless example above uses 13 seeds in a precise pattern) and depends on
  the opponent's half (a capture breaks a cycle). The viewer's DTO still flags a draft whose moves are all
  endless on an otherwise empty board (`draftOnlyEndless`, computed by the server) and the editor shows a
  warning, but that flag is only a warning.
- **Stuck start refused (owner-decided 2026-10-15):** a confirmation that would leave the **starting** seat
  without a legal move on the revealed board is refused (`StuckStartException`, message = the locale key
  `mangola.errors.stuckStart`). Where the check lives was verified by a random search (400,000 pairs): the
  starter's half alone decides nothing, in either direction. Of 99,912 drafts that are endless alone, none
  was stuck on the full board (the opponent's seeds turn a relay into a capture, which breaks the cycle),
  and a draft that has a finite move alone can still be stuck on the full board (a capture adds seeds to
  the hand and can create a cycle). So the check runs on the **full revealed board, at the confirmation
  that completes the pair**, and refuses that confirmation whichever seat sends it; the refused seat
  stays unconfirmed and may arrange again, and the other seat may take its confirmation back
  (`editLayout`). A refusal changes nothing (no version bump, no broadcast); the first confirmation is never
  refused. Feedback without leaking: the refusal carries only the locale key, identical for both seats,
  and the sender's own projection (the other half stays `null`); it does reveal one bit (this pair leaves
  the starter stuck), which can only be learnt by the seat that completes the pair, and only when the
  pair is stuck. The seat that does **not** start is not constrained: if it has no legal move later, the
  normal `NO_MOVE` rule applies. No confirmation timeout is added (owner decision), so a seat that
  never confirms keeps the other waiting, as before.
- **`FORFEIT`:** the shared forfeit (presence grace, Leave), as for every game.
- **`REPETITION` (draw):** the third occurrence of the same position (32 pits and the player to move) ends
  the game with no winner, as in Morris; a safety net, since no endless game appeared in the simulated
  games (the longest took 352 plies) **[Q6]**.
- No draw offer in version 1 (games are short: a median of 5 to 45 plies in the simulations [C]) **[Q7]**.
- The result keeps the Morris shape: `MangolaResult(winner, reason)`, `winner` null for a draw.

### Balance (for the owner's information) [C]

With a 1-ply greedy player on both sides, the outcome is decided by the set-up: from two seeds per pit the
first player always wins in 5 plies; from the checkerboard the second player wins 74 % of games; from random
layouts the first player wins 81 %. Free placement makes the layout itself the first decision of the game,
which is why the placement protocol (hidden or not, who places first) matters **[Q2]**.

## The placement phase

- **Phases:** `PLACEMENT → PLAY → FINISHED`. The game always starts in `PLACEMENT`
  (owner decision 2026-10-15: no set-up option, no preset board built by the factory); tests and bots reach
  `PLAY` through `Mangola.fromLayout`.
- **Command `arrange(seat, int[16] counts)`**: the seat's whole layout, in the order of its loop (`a2..h2,
  h1..a1` for seat A); refused unless there are 16 non-negative integers summing to 32
  (`InvalidLayoutException`), the game is in `PLACEMENT` and the seat has not confirmed. No cap per pit
  (Omweso's "10 per pit" is a disputed local restriction) **[Q3]**. The latest layout replaces the previous one.
- **Command `confirmLayout(seat)`**: the seat is ready; refused without a layout, and refused with
  `StuckStartException` when completing the pair would leave the starter without a legal move (see "End of
  the game"). **`editLayout(seat)`**
  takes the confirmation back, allowed until both are ready. When both seats are ready, the layouts are
  written on the board, the phase becomes `PLAY` and the starting seat is to move (one version bump, one
  event `LAYOUTS_REVEALED`). Every command bumps `version`.
- **Hidden until both are ready (recommended [Q2]):** `forViewer` shows a seat its own draft and the
  opponent's half as unknown (`null` per pit) with only `opponentReady`; once revealed, everything is public.
  Alternatives: **sequential** (Omweso: the starting player arranges first and confirms, then the second
  arranges seeing it; one more `placementOrder` field and a turn in the phase), or **visible live**
  (each sees the other's drafts; not recommended: it rewards waiting).
- **Drafts:** the client edits locally and sends `arrange` on each change (debounced), so a reload or a
  reconnect restores the draft from the server (the DTO holds the viewer's draft). No timer: the project has
  none; a player who never confirms is handled by presence and Leave like any idle player.
- **Presets in the editor (client side, not rules):** "Checkerboard" (4 in every other pit, the Mangola
  opening and the JS classic), "Two per pit" (the Mangola start), "Random" (32 seeds dropped uniformly,
  without the JS bug), "Clear". Presets only fill the editor; the server only checks the sum.
- **Host option `setup`: removed (owner decision 2026-10-15).** It offered `free`, `checkerboard` and `random`
  and the last two skipped the phase. The set-up is always free placement; the editor presets above are the only
  shortcuts. An old client sending `setup` gets the same refusal as for any unknown option.
- **Rematch:** the options are kept (`SessionService.playAgain`); a new placement phase starts.

## Domain design

Package `org.kevinkib.cardgames.mangola` (`package-info.java`), `domain` pure (ArchUnit: no Spring, session,
presentation, no other game).

- `Pit` helpers on `int` indexes `0..31` (`row * 8 + column`, row 0 = "1"): `MangolaBoard` (immutable `int[32]`,
  `seeds(pit)`, `sideTotal(seat)`, `withLayout(seat, counts)`), `MangolaGeometry` (constant: each seat's loop,
  `next(pit)`, `isInner(pit)`, `facing(pit)`, `behind(pit)`, `owner(pit)`, names `a1..h4`).
- `MangolaRules` (record): `endless` (`ILLEGAL` or `DRAW`),
  `capture` (`AUTOMATIC`; room for `OPTIONAL`), `capturedSeedsFrom` (`LAP_START`; room for
  `AFTER_CAPTURING_PIT`), `captureTakesOwnPit` (false; true for the 1952 Mangola), `lastSeedInEmptyPit`
  (`NEXT_PIT_AND_STOP`; room for `STOP`, Omweso and Kiela), `minSeedsToMove` (2). Constant `MangolaRules.OWNER`.
  Version 1 builds only `OWNER` (with `endless` from the options); the other switches exist as
  enums with one value plus a test per value, so a later variant is data plus tests.
- `Sowing` (pure function): `sow(board, seat, pit, rules) → SowingResult(board, trace, endless)`; the
  trace is a list of `SowingStep`: `Pick(pit, count)`, `Drop(pit)`, `SkipEmpty(pit)` (the last seed passed
  over an empty pit), `Capture(fromPit, facing, behind, count)`, `Relay(pit, count)`, `ResumeAfter(pit)`.
  Used by the aggregate, the legal-move check and the bots.
- `Mangola` (aggregate, implements `game.Game`): `Mangola(GameId, MangolaRules, PlayerId firstSeat, board or
  null)`; commands `arrange`, `confirmLayout`, `editLayout`, `sow`, `forfeit`; queries `phase()`,
  `currentSeat()`, `board()`, `layoutOf(seat)`, `isReady(seat)`, `legalPits(seat)`, `lastMove()`
  (`seat`, `pit`, `trace`), `result()`, `repetitions()`, `position()` (immutable `MangolaPosition`: board and
  seat to move, used by bots and the repetition count), `version()`. A `fromLayout` test factory, as in
  Morris.
- Exceptions (`MangolaException` and subclasses): `WrongPhaseException`, `NotPlayersTurnException`,
  `NotYourPitException`, `TooFewSeedsException`, `EndlessSowingException`, `InvalidLayoutException`,
  `AlreadyReadyException`, `NoLayoutException`, `StuckStartException` (owner-decided 2026-10-15),
  `FinishedGameException`.
- Events (`MangolaEvent`): `LayoutChanged(seat)`, `LayoutConfirmed(seat)`, `LayoutWithdrawn(seat)`,
  `LayoutsRevealed`, `Sown(seat, pit, trace, captures)`, `Finished(result)`.
- `MangolaFactory`: `GAME_TYPE = "mangola"`, 2 players, `supportsBots() = true` (from the bots PR), options
  `firstPlayer` (`host|guest|random`, default `random`; no set-up option, owner decision 2026-10-15), seat
  option `level`; `create` maps `firstPlayer` to the seat that becomes seat A through the injected
  `RandomGenerator`, so the aggregate stays deterministic, and always builds a game in `PLACEMENT`.

## Network contract

- **REST** `/api/mangola/game/{id}`, `/join`, `/play-again` over `SeatGameRest`.
- **STOMP** (payloads carry `gameId`, `token`): `/app/mangola/start`, `/app/mangola/arrange` (`counts`: 16
  integers), `/app/mangola/confirmLayout`, `/app/mangola/editLayout`, `/app/mangola/sow` (`pit`: 0..31). A refused
  command answers the sender alone with an `ErrorResponse` and its state (`SeatCommands`).
- **Events** (`MangolaEventType`): `LAYOUT_CHANGED` (it carries no counts, so the other seat learns only that
  a draft changed; **[H]** whether the seat broadcaster can also skip it for the other seat, checked in N3),
  `LAYOUT_CONFIRMED`, `LAYOUT_WITHDRAWN`, `LAYOUTS_REVEALED`, `SOWN` (data: `seat`, `pit`, `trace`,
  `captured`), `GAME_OVER` (the `SOWN` that ended the game, or a forfeit).
- **`MangolaDto`** (per viewer): `started`, `id`, `gameType`, `version`, `phase`, `currentSeat`, `seatA`
  (which seat owns rows 1-2), `board` (32 integers; during a hidden placement the opponent's 16 are `null`
  and the viewer's are their draft), `players[]` (`seat`, `name`, `current`, `side` `A|B`, `seeds` on their
  side, `ready` in the placement phase), `legalPits` (viewer, in play), `availableActions`
  (`ARRANGE`, `CONFIRM_LAYOUT`, `EDIT_LAYOUT`, `SOW`), `lastMove` (`seat`, `pit`, `trace`, so a client that
  reconnects mid-animation can still show the last move), `repetitions` (from 2),
  `outcome` (`ONGOING` / `FINISHED{winner, reason}`). Trace size: a few hundred steps at most for a finite
  move [C]; it is sent once per move.

## Bots

- **Port** `MangolaBot` (`mangola/domain/bot`, pure): `decideLayout(rules, seat, opponentLayout or null) →
  int[16]` and `decideMove(position, seat) → pit`. Every tie broken by an injected `RandomGenerator`.
- **Levels** (seat option `level`, default `greedy`, labels Easy / Medium / Expert) **[Q8]**:
  - `random`: a uniformly random legal pit; a random layout.
  - `greedy`: one ply: the pit that leaves it the most seeds (captures), tie on the most legal moves left.
  - `expert`: negamax with alpha-beta and iterative deepening under a time budget (default 300 ms,
    property `mangola.bots.think-ms`, a node cap for tests), move ordering by captures, evaluation = seed
    difference + mobility (pits with two seeds or more) − exposure (own inner pits whose facing and behind
    pits are both occupied on the opponent's side next turn) and ±∞ for a finished game. The branching
    factor is about 7 [C] and a move simulation costs tens of drops, so depths of 6 to 10 plies are
    reachable in the budget; the end of most games is solved exactly by the search. **No perfect level:**
    a position is 32 pit counts summing to 64 (about 10^25 boards: C(95, 31)), so no retrograde table like
    Morris's is possible, and free placement means no fixed opening to precompute.
  - **Layouts:** `random` and `greedy` draw a random layout or one of the presets; `expert` samples a set of
    candidate layouts (the presets, random ones and mutations of the best) and keeps the one whose shallow
    search against the opponent's layout scores best (with hidden placement it plays against a set of
    typical layouts). Budget 1 s. Simple, and replaceable later.
- **Coordinator** `presentation/bot/MangolaBotCoordinator` (as `MorrisBotCoordinator`): on each broadcast,
  for a bot seat that has an action, compute the decision **outside the game monitor** on a snapshot of the
  position, then under the monitor re-check the version and act through `MangolaGameActions`; delay
  `mangola.bots.delay-min-ms` 700 / `delay-max-ms` 1300 (0 under `test`), plus the client's animation time is
  not waited for: the client queues the bot's trace after the human's **[Q9]**. A failing strategy falls back
  to the first legal pit (or a checkerboard layout).
- Rematch, disconnection and bot seats receiving no message: unchanged from Morris (`claimBotsOf`,
  `sessionService::botSeats`).

## Frontend

- Files per `add-a-game.md`: `model/mangola/MangolaState.ts` (DTO types, pit names, rotation of the board for the
  viewer, trace helpers), `application/MangolaSession.ts` (`MANGOLA_SESSION_CONFIG`; `arrange`, `confirmLayout`,
  `editLayout`, `sow`), `state/Mangola.store.ts`, `composables/useMangolaBootstrap.ts`,
  `components/mangola/` (`MangolaBoard.vue`, `MangolaPit.vue`, `MangolaPlacementEditor.vue`,
  `useSowingAnimation.ts`), `view/mangola/MangolaGameScreen.vue`, locales `mangola` (rules), `mangolaUi`,
  `options.mangola`.
- The client holds no rule: legal pits, captures and the end come from the server; the placement editor
  only counts seeds left (32 minus the sum) to enable Confirm, and the server re-checks.
- Board orientation: each viewer sees their own half at the bottom (the board is rotated for seat A), so
  "my inner row" is always the second row from the bottom.
- Animation (`useSowingAnimation`): plays a trace step by step on a local copy of the previous board, then
  snaps to the DTO board (the truth). Speed adapts to the trace length (a long relay accelerates), a Skip
  button and a click on the board finish it, and `prefers-reduced-motion` shows the result with the last
  move highlighted. A state that arrives during an animation is queued; a newer `version` than the one
  being animated flushes the queue to the latest state.
- Accessibility: every pit is a button with an accessible name ("c2, 3 seeds, your inner row"), arrow keys
  move between pits, Enter sows; a live region announces each move in words ("Bob sowed c3, captured 5
  seeds"); the placement editor is operable by keyboard (+ / − on the focused pit).
- Lobby: nothing to build; the shared lobby renders `firstPlayer` and `level`.
- Rules panel: the rules above in plain words with a small diagram of each loop, the empty-pit rule, the
  capture, and the origin paragraph.

## UI decisions taken (Claude Design canvas, owner's choice of 2026-10-15)

Canvas: https://claude.ai/artifact/9pYmbUu1rN9QdNo5SYcwDp (built on the real E-examples and a simulated
position). The options are kept for context; the chosen one is marked **Chosen**.

1. **Board and seeds.** (a) A 4×8 grid of round pits with the **count** in each pit and up to 6 seed dots,
   viewer's half at the bottom, the inner rows separated by a line; **(b) the same grid with seed dots only
   (count on hover/focus), closer to the real board. Chosen (owner, 2026-10-15)**, with this owner-approved
   rule: up to 8 seeds the pit shows **exactly as many dots as seeds** (the count on hover/focus); above 8 the
   dots are replaced by the **written number**, never an approximation (a pit can hold up to 32 seeds);
   (c) a counts-only compact grid with a heat colour per count. At 375 px, 8 columns of 40 px pits fit in
   343 px; the 44 px touch target needs the gap to count as target or a 2-row zoom: to settle in the
   frontend PR (N5), whose evidence includes the legibility of the dots at 375 px.
2. **Sowing animation.** **(a) A "hand" token travelling pit to pit that accelerates, with a Skip button or a
   tap to jump to the end. Chosen (owner, 2026-10-15).** Timing: tick 2400 ms / seeds dropped, clamped to
   60..280 ms per seed, halving toward the end of the trace; pauses of 1.5 ticks (minimum 200 ms); total
   cap 4.5 s; with `prefers-reduced-motion` the final board is shown at once. Not chosen: (b) lap by lap;
   (c) no animation plus a "replay last move" control with a step slider; (c)'s replay is a possible later
   addition, not part of the first frontend PRs.
3. **Placement screen.** **(a) Tap a pit to add a seed, a "Remove" toggle (instead of a long press) to remove
   one, a "seeds left" counter, presets Checkerboard / Two per pit / Random / Clear. Chosen (owner,
   2026-10-15).** Confirm is enabled only at 0 seeds left; once confirmed, an "Edit layout" button takes the
   confirmation back until the other player is ready; the opponent's half is greyed with "Placing..." then
   "Ready", and both layouts are revealed together. Not chosen: (b) dragging seeds from a reserve heap;
   (c) a +/- stepper under each pit.
4. **Path and capture cues.** **(a) Direction arrows always visible as faint chevrons along each loop.
   Chosen (owner, 2026-10-15).** Not chosen: (b) arrows only during the first turns and in the rules panel;
   (c) none. No preview of a sowing's destination on hover (it would reveal computed outcomes: the server would
   have to send them; out of version 1).
5. **Last move and opponent's turn.** **(a) The opponent's last move is shown by pit marks only: a ring on the
   start pit, dashed rings with "-n" on the captured pits, a ring on the end pit. Chosen (owner,
   2026-10-15).** The one-line move log of (b) is not part of the on-screen design (nor (c), both); the
   screen-reader announcement sentence of the live region ("Bob sowed c3, captured 5 seeds") stays.

### Backend rule decided with these choices (2026-10-15)

At confirmation, a layout that would leave the **starting player** without any legal move is refused (the
exception name is left to the implementing PR). **No confirmation timeout is added**: presence (grace period,
forfeit) and Leave cover a player who never confirms. This tightens the `NO_MOVE` paragraph of "End of the
game" for the starting side only; the case of a layout whose every move is endless against the unknown
opponent half stays flagged by `draftOnlyEndless`, since the check at confirmation can only use what the
confirming seat knows. The PR that implements the refusal states which opponent half it assumes and covers it
with tests.

## Room for documented variants (not built)

Each is a switch of `MangolaRules` plus tests, a `variant` option and a rules-panel section:

- **Mangola 1952 (Comhaire-Sylvain):** 2 per pit, compulsory redistributing first move, second move from an
  end pit, capture takes the capturing pit too; the re-sowing start to be read in the 1952 text.
- **Omweso:** last seed in an empty pit ends the move; reverse captures from the four leftmost pits;
  special opening sowings; special wins (*emitwe-ebiri*, *akakyala*, *akawumbi*).
- **Igisoro:** optional capture (a choice step in the turn: a new action, like Morris's draw answer).
- **Kiela:** a 4×10 board (geometry as data, a second geometry constant), capture of the facing pit alone.
- **Sequential placement** (Omweso's protocol) if the owner prefers it to hidden placement.

## What we do not do

No documented variants in version 1; no optional capture, reverse sowing or special wins; no draw offer;
no move preview; no timer; no change to the session core, the lobby components' logic or other games; no
port of the JS minimax (rewritten); no "Angola" or "Mancala" in the name.

## Risks

- **Endless sowings** cost a bounded simulation per candidate pit and per searched node (the cap keeps
  the worst case at 100,000 drops; the bot search skips a candidate as soon as it is endless). Tested with the
  fixture above and a performance test of `legalPits` on the worst fixture found.
- **Rule mismatch with the owner's memory**: the JS is the reference, but its author may remember a detail
  differently (for instance whether the capturing pit keeps its seeds, as the JS does, or is taken, as in the
  1952 report). Mitigation: the E-examples are shown to the owner in the plan review; every such point is a
  switch.
- **Long animations**: a finite move can drop about 600 seeds [C]; the animation accelerates and can be
  skipped; the server never waits for it.
- **Hidden placement leaks**: `LAYOUT_CHANGED` and the DTO must never carry the opponent's draft; a
  `forViewer` test and an IT check both seats' frames.
- **Second entry PR at the same time** (President, #216): the entry PRs go one after the other.
- **Balance**: the layout decides much of the game [C]; the owner may want sequential placement after
  playing; it is one switch.

## Product questions (owner) with a recommendation

- **Q1 Endless sowing.** Recommend: not a legal move (a player with only endless moves loses). Alternative:
  the JS behaviour, the game ends as a draw.
- **Q2 Placement protocol.** Recommend: simultaneous and hidden, confirm and "edit again" until both are
  ready. Alternatives: sequential (Omweso: the first player arranges first, visible), or visible live.
- **Q3 Placement limits and set-up option.** Recommend: no per-pit cap. A `setup` option (free, checkerboard,
  random) was first recommended, then **removed by the owner on 2026-10-15**: the set-up is always free placement.
- **Q4 Name.** Recommend id `mangola`, display "Mangola", "also known under other names (Ngola, Mongola, Amungola, Angola)" in the rules panel. Decided by the owner on 2026-10-15: the game is called Mangola; id `mangola`.
- **Q5 Who starts.** Recommend a `firstPlayer` option, default `random`; the starting player takes seat A.
- **Q6 Repetition draw.** Recommend the third occurrence of a position with the same player to move ends
  in a draw (a safety net).
- **Q7 Draw offers.** Recommend none in version 1.
- **Q8 Bot levels.** Recommend Easy (random), Medium (greedy, default), Expert (search), with "Expert is
  strong but not perfect" in the rules panel.
- **Q9 Bot pace.** Recommend 700 to 1300 ms after the state, and the client plays the bot's animation after
  the human's own.
- **Q10 Captures.** Confirm the JS: automatic (not optional), the capturing pit keeps its seeds, the captured
  seeds are sown from the pit after the start of the capturing lap.
