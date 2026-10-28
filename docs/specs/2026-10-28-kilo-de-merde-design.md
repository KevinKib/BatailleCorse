# Kilo de merde: design

Scope: a new game, **Kilo de merde**, a 3-to-10-player real-time card game (pass a card left, collect four of a
kind, slap the pile, the slowest slapper takes "kilos"), from the domain to the bots and the table, delivered as a
series of PRs (plan: `docs/plans/2026-10-28-kilo-de-merde-plan.md`). Written by Claude Opus 5.5 on `origin/main` at
`8f7a36d1`. No code here; names follow `docs/architecture/game-conventions.md` and the checklist
`docs/architecture/add-a-game.md`. Models: 99 (`docs/specs/2026-10-28-ninety-nine-design.md`) for a multi-player
card game with a determinism seam, three bot levels and a round pause; Bataille Corse for the slap race.

**The owner was away and asked for every decision to be taken without him** ("decide autonomously; I will test the
finished game and give feedback afterwards"). So this spec has **no owner questions and no design canvas**: every
rule gap and every UI choice is decided here and listed in the numbered table of section 5, with its reason and the
alternative rejected, so that any of them can be overturned later by changing one row.

Labels:

- **[Du]** the publisher Ducale's beginner rules (main source, section 4.1), taken as they are;
- **[Ca]** the Cartamundi notice (same publisher group, longer text), used where Ducale is silent;
- **[Rj]** regles-du-jeu.net, used only to confirm or to show a variant;
- **[A n]** a decision taken autonomously, row n of section 5;
- **[X]** a design choice where no rule applies (online play, data model, UI).

## 1. Goal

Add Kilo de merde as a seat game hosted by the session core (rooms, seats, tokens, lobby, options, presence,
forfeit, play again, bots as flagged seats, public game list, usage statistics), every rule in a pure
`kilodemerde/domain` aggregate. 3 to 10 players each hold 4 cards dealt from a small "hand deck" whose ranks depend
on the player count. In each **pass step** every player picks one card at the same time and the picked cards all
move one seat to the left. A player holding **four of a kind** slaps the pile; once a slap is valid every player must
slap, and the **last** to slap draws the top card of the face-down **kilo pile**: its value is the kilos they collect
(an Ace is 100 kg; a 7 is a "flush" that wipes the drawer's kilos). A slap when nobody holds four of a kind costs the
slapper the card. After a set number of **rounds** the player with the fewest kilos wins.

Unlike 99, the game is **real time**: passes are simultaneous and the slap is a race between all players.

## 2. Identity

| Form | Value | Why |
|------|-------|-----|
| `<id>` | `kilo-de-merde` | the owner's id; reads well in `/kilo-de-merde/room/…`; the prefix of no other id |
| `<pkg>` | `kilodemerde` | |
| `<Pascal>` | `KiloDeMerde` | |
| `<camel>` | `kiloDeMerde` | |
| Display name | **Kilo de merde** in English and in French | a proper noun (owner); the UI tone around it stays neutral and playful, no extra vulgarity |
| Picker icon | `pi pi-stopwatch`, severity `warn` [A19] | a reflex game; the entry chunk checks the icon exists in the installed `primeicons` 7 (fallback `pi pi-clock`) |

Domain vocabulary (English code and texts; French in parentheses for the French bundle): **round** (manche), **pass
step** (passe), **hand deck** (the dealt ranks), **kilo pile** (tas de kilos), **slap** (taper), **race** (the slaps
after a valid one), **flush** (chasse d'eau, the 7), **kilos** (kilos, written `kg`).

## 3. Rules retained

### 3.1 Setup

- **Players**: 3 to 10 [Du, Ca]. `minPlayers() = 3`, `maxPlayers() = 10`. Solo play is one human and at least two
  bots.
- **Deck**: one 52-card French deck, no joker [A1].
- **Hand deck** [Du, Ca]: exactly 4 cards per player, all four suits of `n` ranks:

  | Players | Dealt ranks | Hand deck | Kilo pile |
  |---------|-------------|-----------|-----------|
  | 3 | J, Q, K | 12 | 40 |
  | 4 | + 2 | 16 | 36 |
  | 5 | + 3 | 20 | 32 |
  | 6 | + 4 | 24 | 28 |
  | 7 | + 5 | 28 | 24 |
  | 8 | + 6 | 32 | 20 |
  | 9 | + 8 (the 7 is skipped) | 36 | 16 |
  | 10 | + 9 | 40 | 12 |

  The 7s, the Aces and the 10s are never dealt; they and the unused ranks form the **kilo pile**, shuffled, face
  down [Du].
- **Deal**: the hand deck is shuffled (through the determinism seam) and every player gets 4 cards [Du]. A dealt
  four of a kind is kept and may be slapped at once [A6].
- The kilo pile is shuffled **once per game** and lives across the rounds; only the hand deck is redealt each round
  [A7].

### 3.2 A pass step

- At the start of a step every active player holds 4 cards. Each one **chooses** one card to pass (`choosePass`);
  the choice is private, final for that step, and the card stays in the hand until the step resolves [A2].
- When every active player has chosen, the step resolves **atomically**: every chosen card moves to the next active
  seat in seat order ("the neighbour on the left" [Du]); everyone holds 4 cards again; the step number goes up. Each
  player learns the card they received; nobody else does.
- A **pass timer** runs from the start of each step; when it expires, the server chooses for every seat that has
  not chosen (the domain's `defaultPass`, section 8.3) and the step resolves [A3].
- Because the hand changes atomically, "every player holds 4 cards" is always true between steps, so the rule "do
  not slap before everyone has 4 cards" [Du, Ca] has nothing left to forbid [A2].

### 3.3 The slap

- A slap is allowed at any time in the `PASSING` phase, by any active player, whatever their hand [Du].
- **Valid slap**: at the moment of the slap, at least one active player's hand is four cards of one rank (not
  necessarily the slapper's) [Du: "s'il tape alors que personne n'a 4 cartes de même valeur, c'est lui qui prend la
  carte"; A4]. It opens a **race**: passing is frozen, the slapper is first in the slap order.
- **False slap**: no hand is four of a kind. The slapper alone draws the top card of the kilo pile; the round ends
  at once, no race [Du, Ca].
- **Race**: every other active player slaps (`slap`); the server records the order of arrival. When all but one
  have slapped, the remaining player is **last** and draws one card; the round ends [Du].
- **Race timeout**: if the race is still open after the race timeout, every player who has not slapped is "last",
  and each draws one card, in seat order starting left of the first slapper [A5].
- A second slap by the same seat in a race, or a slap outside `PASSING` / `RACE`, is refused with no penalty [X].

### 3.4 Drawing kilos and the flush

- The drawer takes the top card of the kilo pile and puts it face up in front of them: it counts its **kilo value**
  [Du]: Ace 100, King 13, Queen 12, Jack 11, 2 to 10 at face value (the 10 counts 10, the 9 counts 9, and so on).
- **The 7 is the flush** [Du, Ca]: the drawer's collected cards **and the 7** go back into the kilo pile, which is
  shuffled (through the seam); the drawer is back at 0 kg [Du "on remet les cartes dans le tas qu'on mélange";
  Ca "tous les kilos accumulés par ce joueur sont annulés"; A8 for the 7 itself].
- **Empty pile**: a draw that finds the kilo pile empty draws nothing (the draw is recorded as skipped); the game
  ends when that round ends [A9].

### 3.5 End of a round, end of the game

- A round ends after a false slap, after the race's draws, or when it **stalls**: after `stallLimit` pass steps
  with no slap, the round ends with no draw [A10]. Every ended round counts towards the round total.
- **Between rounds**: all hands are revealed (proof of the four of a kind, or of the false slap), then after the
  round pause the next round is dealt automatically (no ready barrier) [A11].
- **End**: the game ends after the number of rounds chosen in the lobby (`rounds`, default 10) [Du: "les joueurs
  doivent décider en amont combien de tours"; A12], or earlier when the kilo pile is empty at the end of a round.
- **Ranking**: fewest kilos first; equal totals **share** the place (1st, 1st, 3rd: standard competition
  ranking) [A13]. `KiloDeMerdeResult`: places and kilos per seat, reason `ROUNDS_PLAYED`, `PILE_EMPTY` or
  `FORFEIT`.

## 4. Sources

### 4.1 Main source: Ducale, beginner rules (verbatim)

https://bk.jeux-ducale.fr/app/uploads/2022/06/cartes-a-jouer-Ducale-regle-jeu-kilo-de-merde-pour-joueur-debutant.pdf
(fetched 2026-10-28; text extracted with `pypdf`):

> BUT DU JEU. Avoir le moins de kilos de merde possible.
> MISE EN PLACE DU JEU. En fonction du nombre de joueurs on va distribuer différentes cartes : à 3 joueurs, on joue
> avec les Rois, Dames et Valets ; à 4 joueurs, on y ajoute les 2 ; à 5 joueurs, les 3 ; à 6 joueurs, les 4 ; à 7
> joueurs, les 5 ; à 8 joueurs, les 6 ; à 9 joueurs, les 8 ; à 10 joueurs, les 9. Dans tous les cas on ne distribue
> pas les 7. Chaque joueur reçoit 4 cartes, le reste des cartes (avec les 7) constitue « le tas de kilos » posé au
> centre de la table, face cachée. Les joueurs doivent décider en amont combien de tours il faut faire pour arriver
> au bout de la partie.
> DÉROULEMENT DU JEU. Chacun leur tour, les joueurs font passer une carte de leur choix à leur voisin de gauche dans
> le but d'avoir quatre cartes de même valeur. Le premier ayant ces 4 cartes identiques, doit taper sur le tas
> central mais seulement quand tous les joueurs ont 4 cartes en main. Tout le monde tape ensuite sur le tas. Le
> dernier à taper perd et doit prendre la première carte du tas. La valeur de la carte indique le nombre de kilos de
> merde qu'il obtient. Si c'est un 7, tous les kilos accumulés sont annulés, on remet les cartes dans le « tas de
> kilos » qu'on mélange. Attention, si un joueur tape avant que tout le monde ait 4 cartes en main ou s'il tape alors
> que personne n'a 4 cartes de même valeur, c'est lui qui prend la carte du tas.
> FIN DE LA PARTIE. En fin de partie on compte les kilos accumulés : l'As compte pour 100 kilos ; le roi pour 13
> kilos ; la dame pour 12 kilos ; le valet pour 11 kilos ; puis les autres cartes suivent en fonction de leur valeur
> respective : 2 = 2 kilos, etc.
> (Box: 6+, 3 à 10 joueurs, débutant/occasionnel.)

### 4.2 Other sources

- **[Ca]** Cartamundi, "Batailles" notice, section "Le jeu du kilo" (3 to 10 players, 8+, about 25 minutes):
  https://cartamundi.fr/app/uploads/2018/06/729730_notice_BATAILLES.pdf . Same hand-deck table (with "attention : le
  7 étant la chasse d'eau, il reste dans le tas"); "Le joueur qui a la Dame de Cœur commence à jouer. Chacun leur
  tour, les joueurs vont passer la carte de leur choix à leur voisin de gauche"; a false or early slap: "c'est le
  joueur qui a tapé en premier qui prend la première carte du tas de kilos"; "Si la carte piochée est un 7, tous les
  kilos accumulés par ce joueur sont annulés. La manche est terminée. Les joueurs relancent une manche en
  redistribuant les cartes".
- **[Rj]** regles-du-jeu.net, "Règle du kilo de merde": https://www.regles-du-jeu.net/?p=2599 . Same principle;
  adds the queen of hearts starting, the pass "to the right", and a **bluff** (pretending to slap; whoever then
  slaps takes a card).
- Commercial listings (Ducale box 54 cards with jokers, 3-10 players; Grimaud "about 15 minutes") only confirm
  the player range.

### 4.3 Where the sources disagree or are silent

| Point | Ducale | Cartamundi | regles-du-jeu.net | Decision |
|-------|--------|------------|-------------------|----------|
| Player range, hand deck by count | 3-10, table above | same | same (one ambiguous line) | Ducale |
| Who starts, order of passing | "chacun leur tour", to the left | queen of hearts starts, in turn, to the left | in turn, to the **right** | **simultaneous steps to the left** [A2]; nobody "starts" |
| "Everyone must hold 4 cards" before a slap | yes | yes | yes | always true with atomic steps [A2] |
| Who may slap first | "le premier ayant ces 4 cartes" | the holder | the holder; a bluff exists | anyone; valid iff some hand is four of a kind [A4] |
| False slap | the slapper takes the card | the first slapper takes it | the fooled follower takes it (bluff) | the slapper draws, no race [Du]; bluff later (section 15) |
| A player never slaps | silent | silent | silent | race timeout, every non-slapper draws [A5] |
| The 7 | "tous les kilos accumulés sont annulés, on remet les cartes dans le tas qu'on mélange" | "tous les kilos accumulés par ce joueur sont annulés" | the drawer "may" put their kilos back under the pile | the drawer's kilos and the 7 back in the pile, shuffled [A8] |
| Round after a draw | silent | the round ends, cards redealt | silent | Cartamundi: redeal [A7] |
| Length | rounds agreed beforehand | same | same | lobby option, default 10 [A12] |
| Kilo pile runs out | silent | silent | silent | the game ends after that round [A9] |
| Ties at the end | silent | silent | silent | shared place [A13] |
| Jokers | the box has 54 cards; rules silent | silent | silent | no jokers [A1] |
| A dealt four of a kind | silent | silent (Kem's in the same notice redeals) | silent | kept, may be slapped [A6] |
| Nobody ever completes a set | silent | silent | silent | stall limit [A10] |

## 5. Decisions taken autonomously

Each row can be overturned by the owner; the "Where" column says what changes.

| # | Decision | Reason | Alternative rejected | Where |
|---|----------|--------|----------------------|-------|
| A1 | One 52-card deck, no jokers | no source gives jokers a role | jokers as 0 kg or as a wild card | `KiloDeck` |
| A2 | **Simultaneous pass steps**: everyone chooses a card, then all chosen cards move left at once | online with up to 10 players, a chain of one-at-a-time passes leaves 9 players idle and makes "everyone has 4 cards" a timing trap no one can see; simultaneous passing is how the game is played at a table in practice (everyone passes at once) | the literal chain (queen of hearts starts, one pass at a time, a slap is false while someone holds 3 or 5 cards) | `KiloDeMerde.choosePass`, `resolveStep` |
| A3 | A **pass timer** (15 s) chooses for a seat that has not chosen, with the domain's `defaultPass` | a simultaneous step waits for the slowest player, and a disconnected one would block the table for the 60 s grace | no timer (99's choice, but 99 is turn-based) | `KiloDeMerdePassTimer` (presentation), `defaultPass` |
| A4 | A slap is valid when **any** hand is four of a kind, not only the slapper's | the Ducale penalty speaks of "personne n'a 4 cartes"; it also keeps a follower who slaps early safe | only the holder may open | `KiloDeMerde.slap` |
| A5 | **Race timeout** 5 s: every non-slapper is "last" and draws one card, in seat order from the first slapper's left | a race must end even if a player is away; one draw for one of several absentees would be arbitrary | pick one absentee at random; wait forever | `closeRace` |
| A6 | A dealt four of a kind stays and may be slapped at once | the rules are silent; a free first round is part of the luck | redeal (Kem's rule) | none (no special case) |
| A7 | The kilo pile is shuffled once per game and keeps shrinking; only the hand deck is redealt each round | the drawn cards stay with the players; a flush puts cards back | rebuild the pile each round (impossible: drawn cards are held) | `KiloDeMerde.nextRound` |
| A8 | On a flush the **7 goes back too**, with the drawer's cards, and the pile is shuffled | otherwise the 7s run out and the game loses its comeback | the 7 stays with the drawer at 0 kg | `drawFor` |
| A9 | A draw from an empty pile draws nothing; the game ends at the end of that round (`PILE_EMPTY`) | 10 players have only 12 kilo cards; with 10 rounds the pile can run out | reshuffle all players' kilos (would erase the score) | `drawFor`, end check |
| A10 | A round **stalls** after 40 pass steps without a slap: it ends with no draw and counts as played | humans could pass forever; 40 steps is about 4 times the usual length of a round with bots | no limit | `KiloDeMerdeRules.STALL_LIMIT` |
| A11 | Between rounds: hands revealed, a 3 s pause, then the next deal (no ready barrier) | 10 players never wait for a click; the reveal shows why someone lost | Kobo/President ready barrier | `KiloDeMerdeRoundPause` |
| A12 | Lobby option `rounds` = 10 (default), 5, 15, 20 | the rules leave it to the players; 10 rounds is about 10 minutes with bots | free number; a target score | `KiloDeMerdeOptions` |
| A13 | Equal kilos share the place | fair and the same as 99's quick mode | tie-break by fewer cards or seat order | `KiloDeMerdeResult` |
| A14 | **Forfeit**: the leaver is placed last; the current round is voided (not counted, no draw); the table re-forms for `n - 1` players: the hand-deck rank no longer dealt joins the kilo pile, the leaver's kilo cards go back into the pile, the pile is shuffled, a new round is dealt. Below 3 players the game ends (`FORFEIT`), the others ranked by kilos | the hand deck must hold exactly 4 cards per player, so the table cannot keep a hole | the server plays the leaver's seat (needs a bot takeover the session core does not have) | `KiloDeMerde.forfeit` |
| A15 | `defaultPass(seat)`: a card of the rank the seat holds **fewest** of; ties broken by the rank order 2..K, then suit order clubs, diamonds, hearts, spades | the obvious sensible pass, deterministic for tests; the same rule as the `medium` bot | a random card | `KiloDeMerdeRules.defaultPass` |
| A16 | No bluff in v1 (a slap is either valid or false and resolved at once) | the bluff of [Rj] needs a "fake slap" gesture with no clear online equivalent; listed in section 15 | a "fake slap" command | none |
| A17 | All hands are public in the round result; kilo cards are public all the time; hands, choices and received cards are private during a round; who has already chosen is public ("ready" marks) | shows the proof of the set; the ready marks let a table see who it waits for | hide hands at the end | DTO |
| A18 | Three bot levels, reaction times as the main lever (section 11) | slapping speed is the core skill; passing choices add a second lever | two levels | `KiloDeMerdeBotTuning` |
| A19 | Picker icon `pi pi-stopwatch`, severity `warn` | a reflex game; no other game uses the icon (every severity is already taken by some game) | `pi pi-bolt` (Bullshit's) | `games.ts` |
| A20 | Table layout of section 12.2: the ellipse ring of plates (99's geometry, promoted to `model/table`), the kilo pile as the single slap target in the middle, my four cards at the bottom, a tap on a card passes it (no confirm) | reuses the tested 12-seat ring; one big target is easiest under time pressure; a pass costs little so a confirm only slows the game | a separate "Slap" button; tap-to-select then confirm | F2 |
| A21 | Remappable shortcuts through the shared hotkey settings: `slap` (Space), `pass1`..`pass4` (1-4) | single-key shortcuts must be remappable or off (WCAG 2.1.4, existing gear) | fixed keys | `games.ts` `hotkeys` |
| A22 | The display name is "Kilo de merde" in both languages; texts call the cards "kilos" and the pile "the kilo pile"; no other crude word | owner's brief | translated name | locales |

## 6. Options

### 6.1 Host options (game level)

`OptionType.CHOICE` through the existing lobby mechanism (`optionDescriptors()`, `validate` parsing a
`KiloDeMerdeOptions` record strictly, the 99 pattern). Label keys `options.kiloDeMerde.<key>`.

| Key | Values (first = default) | Meaning |
|-----|--------------------------|---------|
| `rounds` | `10`, `5`, `15`, `20` | number of rounds [A12] |

### 6.2 Seat options (bots)

`level` = `easy|medium|hard`, default `medium`, label `options.kiloDeMerde.level` (99 pattern).

## 7. Numeric parameters

"Source" is Ducale (Cartamundi where Ducale is silent). A row flagged **changed** is a value this spec sets where the
sources give none or another. The owner asked for autonomous decisions: these values are implemented as proposed and
can be changed later (one property or constant each).

| Parameter | Source | Proposed | |
|-----------|--------|----------|-|
| Players | 3 to 10 | 3 to 10 | |
| Hand size | 4 | 4 | |
| Hand deck by player count | table 3.1 | table 3.1 | |
| Kilo values | A 100, K 13, Q 12, J 11, others face value | same; 7 = flush | |
| Rounds | agreed by the players | option 5 / **10** / 15 / 20 | **changed** (new value, A12) |
| Pass timer | none (live table) | 15,000 ms; off under `test` | **changed** (new value, A3) |
| Race timeout | none | 5,000 ms; 3,000 ms under `test` | **changed** (new value, A5) |
| Stall limit | none | 40 pass steps | **changed** (new value, A10) |
| Round pause (hands revealed) | none | 3,000 ms; 0 under `test` | **changed** (new value, A11) |
| Bot pass delay (all levels) | — | 500 to 1,000 ms; 0 under `test` | **changed** (new value) |
| Bot `easy` own-set slap delay / follow delay | — | 1,500-2,500 ms / 1,200-2,000 ms | **changed** (new value) |
| Bot `medium` own-set slap delay / follow delay | — | 700-1,100 ms / 700-1,100 ms | **changed** (new value) |
| Bot `hard` own-set slap delay / follow delay | — | 350-600 ms / 450-750 ms | **changed** (new value) |
| Bot delays under `test` | — | 0 (pass), own-set and follow slaps 0 | **changed** (new value) |

The bot reaction numbers aim at: `easy` loses most races to a human, `medium` about even with a casual human
(a human reacts in about 300 ms plus the network), `hard` close to a quick human. They are properties
(`kilodemerde.bots.*`) so the owner can tune them after testing.

## 8. Domain model (`kilodemerde/domain`)

One aggregate root `KiloDeMerde implements Game`, 99 style: every mutating method `synchronized`, validates first
then mutates, bumps `version()`, returns a small outcome; a refused command never mutates. Package-private
constructor with explicit hands and pile for tests (`KiloDeMerdeBuilder`).

### 8.1 Types

| Type | Role |
|------|------|
| `KiloDeMerdeOptions` (+ `Rounds`) | typed options, `from` lenient / `parseStrict` strict, `toGameOptions` |
| `KiloDeck` (pure) | `dealtRanks(players)` (table 3.1), `handDeck(players)`, `kiloPile(players)` (the 52 cards minus the hand deck) |
| `KiloValue` (pure) | `of(card)` (A 100, K 13, Q 12, J 11, else face value; 0 for a 7), `isFlush(card)`, `total(cards)` |
| `KiloDeMerdeRules` (pure) | `isFourOfAKind(hand)`, `anyFourOfAKind(hands)`, `defaultPass(hand)` (A15), `STALL_LIMIT = 40`; shared by the aggregate, the timer and the bots, so no rule is written twice |
| `Phase` | `PASSING`, `RACE`, `ROUND_OVER`, `FINISHED` |
| `RoundOutcome` (record) | `kind` (`RACE`, `FALSE_SLAP`, `STALLED`, `VOIDED`), `firstSlapper`, `slapOrder`, `holders` (seats with four of a kind), `draws` (list of `KiloDraw(seat, card or null when skipped, flushed, kilosAfter)`), `hands` (every hand at the end) |
| `DeckSource` (port) | `shuffleHandDeck(List<Card>)` and `shufflePile(List<Card>)`; `RandomDeckSource` default, `ScriptedDeckSource` under `test` (a queue of scripted orders per call, then random with a fixed seed) |
| `KiloDeMerdeEvent` (sealed) | `RoundDealt(round)`, `PassChosen(seat)`, `StepResolved(round, step)`, `RaceOpened(seat)`, `Slapped(seat, rank)`, `FalseSlap(seat)`, `KiloDrawn(seat, card, kilosAfter)`, `Flushed(seat, returnedCount)`, `RoundOver(outcome)`, `GameFinished(result)`, `Forfeited(seat)` |
| `KiloDeMerdeResult` | places and kilos per seat, `Reason` `ROUNDS_PLAYED`, `PILE_EMPTY`, `FORFEIT` |
| Exceptions (checked `KiloDeMerdeException`) | `FinishedGameException`, `WrongPhaseException`, `AlreadyChoseException`, `CardNotInHandException`, `AlreadySlappedException`, `NotActiveSeatException` |

### 8.2 Commands and queries

Commands:

- `choosePass(seat, Card)`: `PASSING` only; card in hand; not already chosen this step. If it completes the step,
  `resolveStep()` runs inside the same call (rotation, `step + 1`, stall check).
- `slap(seat)`: in `PASSING`, opens a race (valid) or resolves a false slap; in `RACE`, records the seat (refused if
  already slapped); when one seat is left, it draws and the round ends.
- `expirePassTimer(step)`: called by the timer; refused unless `PASSING` at that step; chooses `defaultPass` for
  every seat that has not chosen, then resolves.
- `closeRace(raceId)`: called by the race timeout; refused unless that race is open; every non-slapper draws (A5).
- `nextRound()`: called after the round pause; refused outside `ROUND_OVER`; ends the game (rounds played or empty
  pile) or deals the next round.
- `forfeit(PlayerId)` (A14).

Queries: `phase()`, `round()`, `roundsTotal()`, `step()`, `raceId()`, `version()`, `activeSeats()`, `hand(seat)`,
`chosen(seat)` (the viewer's own choice), `hasChosen(seat)` (public), `lastReceived(seat)`, `lastGiven(seat)`,
`passHistory(seat)` (the seat's own given and received cards this round, for its bot), `slapOrder()`,
`kiloCards(seat)`, `kilos(seat)`, `pileSize()`, `lastOutcome()`, `availableActions(seat)` (`PASS`, `SLAP`),
`options()`, `result()`.

### 8.3 Worked examples (test contract of the domain chunk)

Four players A, B, C, D in seat order (A passes to B, D to A), hand deck J, Q, K, 2; one parameterised test row
each. Cards written rank then suit (`K♠`).

1. **Step**: A chooses `Q♣`, B `2♦`, C `J♥`, D `K♣`. When D chooses, the step resolves: A has received `K♣`, B
   `Q♣`, C `2♦`, D `J♥`; `step` is 2; every hand holds 4.
2. **Choice is final**: A chooses `Q♣` then `K♠` in the same step: the second is refused (`AlreadyChoseException`).
   A card A does not hold is refused (`CardNotInHandException`).
3. **Valid race**: A holds `K♠ K♥ K♦ K♣`. A slaps: phase `RACE`, order [A]; `choosePass` now refused
   (`WrongPhaseException`). C slaps, then B: D is last and draws the top card `A♥`: D has 100 kg; `ROUND_OVER`; the
   outcome lists the four hands and A as holder.
4. **Any slapper**: A holds four Queens and does not slap; C slaps: valid, C first. A slaps second, D third: B
   draws.
5. **False slap**: no hand is four of a kind; B slaps: B draws `9♣` (9 kg); `ROUND_OVER`, kind `FALSE_SLAP`.
6. **Double slap**: in a race, C slaps twice: the second is refused (`AlreadySlappedException`), the order is
   unchanged.
7. **Flush**: D holds `A♥ K♠` (113 kg) and draws `7♦`: `A♥`, `K♠` and `7♦` go back into the pile, which is
   shuffled through the seam (pile size + 3); D is at 0 kg.
8. **Race timeout**, five players A to E: A opens, B and C slap; `closeRace` makes D then E draw one card each
   (seat order from A's left). A stale `closeRace` (another race id) is refused.
9. **Pass timer**: A and B chose, C and D did not; `expirePassTimer(step)` chooses `defaultPass` for C and D (C holds
   `J♠ J♥ Q♦ 2♣`: a rank held once, ties by rank order 2..K: `2♣`) and resolves. A stale step number is refused.
10. **Stall**: 40 resolved steps without a slap: `ROUND_OVER`, kind `STALLED`, no draw, the round counts.
11. **End by rounds**: `rounds = 5`, the 5th round over, `nextRound()` → `FINISHED`, reason `ROUNDS_PLAYED`. Kilos A
    0, B 24, C 0, D 113 → A and C 1st, B 3rd, D 4th.
12. **End by empty pile**: the pile holds one card; a race makes C draw it; the round ends; `nextRound()` →
    `FINISHED`, reason `PILE_EMPTY`. In a timeout with two non-slappers and one card left, the second draw is
    recorded as skipped.
13. **Forfeit, table re-forms**: B (24 kg) forfeits during `PASSING` with four players: B is placed 4th; the round is
    voided (not counted); the 2s and B's kilo cards go into the pile, which is shuffled; a new round is dealt J, Q,
    K to A, C, D. Then C forfeits: two players left → `FINISHED`, `FORFEIT`, A and D ranked by kilos, C 3rd, B 4th.
14. **Version**: every accepted command bumps `version()`; every refused one leaves the whole state unchanged.

## 9. Disconnect, forfeit, rematch

- **Disconnect**: the existing 60 s grace, then forfeit (presence context). During the grace, the pass timer passes
  for the absent seat and a race timeout makes it draw: an absent player loses kilos, as at a real table [X].
- **Forfeit**: A14.
- **Rematch**: `FINISHED`, then `POST /api/kilo-de-merde/game/{id}/play-again`; a new game at 0 kg, options and bots
  kept by the session core.

## 10. Presentation

### 10.1 WebSocket (`/app/kilo-de-merde/...`, through `SeatCommands`)

| Destination | Payload | Effect |
|-------------|---------|--------|
| `/kilo-de-merde/start` | `GameActionPayload` | host starts (game-type check first: "This room is not a Kilo de merde game") |
| `/kilo-de-merde/pass` | `KiloDeMerdePassPayload(gameId, token, card: CardDto)` | choose the card to pass |
| `/kilo-de-merde/slap` | `GameActionPayload` | slap |
| `/presence`, `/forfeit` | existing | unchanged |

`KiloDeMerdeEventType`: `START`, `ROUND_START`, `PASS_CHOSEN`, `STEP`, `RACE`, `SLAP`, `FALSE_SLAP`, `DRAW`,
`FLUSH`, `ROUND_OVER`, `GAME_OVER`. One `SuccessResponse` per human seat through
`SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto>`; refusals to the sender alone with a code (French translation
design D3: the frontend never renders a server `message`).

Slaps are ordered by arrival at the server under the game monitor, as in Bataille Corse [X].

### 10.2 Timers (presentation, the `NinetyNineRoundPause` pattern, scheduler injected)

| Timer | Armed on | Calls | Key (stale call dropped) | Property |
|-------|----------|-------|--------------------------|----------|
| `KiloDeMerdePassTimer` | `ROUND_START`, `STEP` | `expirePassTimer(step)` | `(round, step)` | `kilodemerde.pass-timer-ms` (0 = off) |
| `KiloDeMerdeRaceTimeout` | `RACE` | `closeRace(raceId)` | `raceId` | `kilodemerde.race-timeout-ms` |
| `KiloDeMerdeRoundPause` | `ROUND_OVER` | `nextRound()` | `round` | `kilodemerde.round-pause-ms` |

Keys are domain counters, **not** `version()`: every slap and every choice bumps the version, and a version key
would drop a valid timer.

### 10.3 REST (`/api/kilo-de-merde`) over `SeatGameRest`

`GET /game/{id}?token=`, `POST /game/{id}/join`, `POST /game/{id}/play-again`. Test seam under `@Profile("test")`:
`KiloDeMerdeTestDealController`, `PUT`/`DELETE /api/test/kilo-de-merde/{gameId}/deal` (99 pattern) with a body
`{ hands: CardDto[][] (in seat order), pile: CardDto[] (top first) }`: the next deal and the pile order. A pile of
one card makes a one-round game for the e2e (A9).

### 10.4 Per-seat DTO (`KiloDeMerdeDto.forViewer(game, viewer, names, bots)`)

Public: `version`, `round`, `roundsTotal`, `step`, `phase`, `pileSize`, `options`, `passTimerEndsAt` (server epoch
ms, for the countdown; null when off), `raceEndsAt`, per seat `{seat, name, bot?, level?, kilos, kiloCards,
chosen (bool), slapRank?, active, place?}`, `race {firstSlapper, order}`, `lastOutcome` (the `RoundOutcome`, with all
hands, only once the round is over), `result` (places, kilos, reason).

Private (viewer only): `hand`, `myChoice`, `lastReceived {card, from}`, `lastGiven {card, to}`,
`availableActions`.

Never in another seat's DTO during a round: a hand, a choice, a received card, the pile order.

Names follow the French translation design D3 (bots and unnamed players sent as `name: null`, the client names
them).

### 10.5 Configuration

`config/KiloDeMerdeConfig.java`: `kiloDeMerdeFactory`, `kiloDeMerdeStateBroadcaster`, `kiloDeMerdeGameActions`,
`kiloDeMerdeLifecycleBroadcaster`, the `DeckSource` by profile, the three timers, the bot coordinator. `AppConfig` is
not edited. Shared backend tests per `add-a-game.md` §2.2. The public game list and the usage statistics need
nothing: both key on the game type the session core already carries.

## 11. Bots

Three levels: the choice is real twice (what to pass, how fast to slap), so three levels are meaningful [A18].

- **Domain** (`kilodemerde/domain/bot`, pure): `KiloDeMerdeBotView.forSeat(game, seat)` (exactly what the seat's
  DTO shows, plus its own `passHistory` this round; a parity test pins it), `KiloDeMerdeBotStrategy` (port:
  `choosePass(view) → Card`), `KiloDeMerdeBotLevel` (`easy|medium|hard`), `KiloDeMerdeBotTuning` (the delay ranges
  of section 7, from properties), `KiloDeMerdeReflex` (pure: `ownSetDelay(level, rng)`, `followDelay(level, rng)`).
- **Bots never false-slap**: a bot opens a race only when its own hand is four of a kind, and follows every race.
- `easy` (**random**): passes a uniformly random card.
- `medium` (**greedy**): `defaultPass` (A15): keeps the rank it holds most of, passes from the rank it holds fewest of.
- `hard` (**strongest feasible**, memory): picks a target rank by `own count + 0.5 × cards of that rank received
  this round` (what the right neighbour keeps sending is what it does not collect, so more may come), never passes
  the target rank, passes first the ranks it has itself passed before (they are travelling the ring anyway), then
  the rank held fewest. Hidden hands and simultaneous moves make a search pointless; the real edge is speed.
- **Strength benchmark** (C2, opt-in: `mvn test -Dtest=KiloDeMerdeBotStrengthBenchmark -Dkilodemerde.bench=true`):
  200 games of 4 players, 10 rounds, seats rotated, races simulated with each level's sampled delays; mean kilos per
  level written in this section. Expected order: hard < medium < easy in kilos.
  **Result (C2, simulator of the rules in the test tree because the aggregate was not merged yet; 2,000 games of 4
  bots, 10 rounds, seats rotated, default delays of section 7):** mean kilos per player, fewer is better.

  | Table | easy | medium | hard |
  |-------|------|--------|------|
  | easy, medium, hard, hard | 89.9 | 6.2 | 0.1 |
  | 1 hard + 3 medium | | 44.7 | 0.2 |
  | 1 hard + 3 easy | 42.4 | | 0.0 |
  | 1 medium + 3 easy | 43.4 | 0.0 | |
  | 4 medium (control) | | 23.1 | |

  The expected order holds (hard < medium < easy). The gap is almost entirely the reaction time: the last
  slapper draws, so a seat that is never last never draws. The two passing strategies barely matter against the
  delays, and the delays of section 7 make `hard` nearly unbeatable and `easy` the usual loser among bots. This is
  only bot against bot; against a human (about 300 ms plus network) the owner may want `easy` and `medium` slower
  and `hard` less extreme: every range is a property (`kilodemerde.bots.*`).
- **Coordinator** (`presentation/bot/KiloDeMerdeBotCoordinator`): after each broadcast, for each bot seat:
  - `PASSING`, not chosen this step: one pass task after the pass delay, keyed on `(round, step)`;
  - `PASSING`, its own hand is four of a kind: one slap task after `ownSetDelay`, keyed on `(round, step)`;
  - `RACE`, not slapped yet: one slap task after `followDelay`, keyed on `raceId`.
  A task re-checks its key under the game monitor and acts through `KiloDeMerdeGameActions`; a task whose key moved
  on is dropped. Unlike Bataille Corse's coordinator it does **not** cancel tasks on every version bump (a slap
  in the race must not cancel the other bots' slaps). A failing strategy falls back to `defaultPass`.

## 12. Frontend

### 12.1 Files (per `game-conventions.md` §4)

`model/kilodemerde/KiloDeMerdeState.ts` (types of 10.4) and `fixtures.ts`; `model/kilodemerde/` helpers
(`kiloLabel`, `seatOrder` from `opponentOrder.ts`, `roundSummary` codes, `countdown`), `tempo.ts`, `motion.ts`
(anchors `KiloDeMerdeAnchor`: seats, pile, my hand); `application/KiloDeMerdeSession.ts`
(`KILO_DE_MERDE_SESSION_CONFIG`, `pass(card)`, `slap()`), `state/KiloDeMerde.store.ts` (`kilo-de-merde-store`),
`composables/useKiloDeMerdeBootstrap.ts`, `components/kilodemerde/`, `view/kilodemerde/KiloDeMerdeGameScreen.vue`.
Texts: rules at `kiloDeMerde`, screen at `kiloDeMerdeUi`, options at `options.kiloDeMerde`, hotkey action labels in
the shared `hotkeys.actions` (`slap` exists; `pass1`..`pass4` added). Registry:
`define('kilo-de-merde', { supportsBots: true, picker: { icon: 'pi pi-stopwatch', severity: 'warn' }, hotkeys:
{ actions: ['slap', 'pass1', 'pass2', 'pass3', 'pass4'], storageKeys: ['kilo-de-merde.hotkeys'] } })`.

The client has no rule: what may be done comes from `availableActions`; the four-of-a-kind is never highlighted
(spotting it is the game).

### 12.2 Table layout (decided here, A20; no canvas at the owner's request)

- **Felt and ring**: the 99 felt; up to 10 plates on the ellipse ring, me at the bottom. 99's pure
  `model/ninetynine/ringLayout.ts` is promoted to `model/table/ringLayout.ts` (99 imports it from there; its tests
  move with it); below its minimum felt the 99 "rail" fallback applies. My left neighbour (who receives my card) is
  the next plate clockwise, i.e. on my left.
- **Plate**: name (and bot level), kilos (`113 kg`, the number in tabular figures), a small "ready" check when the
  seat has chosen this step, during a race a hand mark with its slap rank (1, 2, 3…), at the round end a "last"
  tag and the drawn card. Tapping a plate opens a popover with its kilo cards.
- **Centre**: the kilo pile, face down, with its count, is **the** slap target: a button of at least 96 px, label
  "Slap the pile", pulsing while a race is open and I have not slapped; above it "Round 3 of 10 · pass 7" and the
  pass countdown ring (15 s).
- **My hand**: my 4 cards at the bottom, large; a tap on a card passes it (A20): the card lifts and greys with
  "Passing…", the others are disabled until the step resolves; then the received card glows for a moment with
  "from <name>". Keys `1`-`4` pass, Space slaps (remappable, A21).
- **Race banner**: "<name> slapped! Slap!" at the top of the felt, with the live order; after the race, the round
  banner: "<name> was last and takes K♠: 13 kg", "False slap: <name> takes 9♣: 9 kg", "Flush! <name> drops all
  their kilos", "Nobody completed a set: no kilos this round". During the round pause every plate shows its hand
  (compact).
- **Standings**: the shared `TotalsTable` (player, kilos, cards), collapsible on a phone; the end screen
  (`EndGameOverlay`) lists places (shared places) and kilos.
- **Lobby**: `rounds` through `LobbyGameOptions`, bot `level` per seat, as 99.
- **Animation** (shared module, `src/animation` barrel): on `STEP` one face-down flight per seat to its left
  neighbour (mine face up when I receive it); on `DRAW` a flight from the pile to the drawer's plate, turned face up;
  on `FLUSH` the drawer's cards fly back to the pile. Reduced motion: no flights, the texts still follow. Numbers in
  `tempo.ts` (step flight 350 ms, draw 600 ms, flush 700 ms).
- **Spacing**: the `frontend-spacing` skill (scale, 375x667 and 1280x720 checks, `expectGap` / `expectNoOverlap`
  in the Cypress spec).
- **Accessibility**: a polite live region for "You received K♣ from Alice", "Round 4"; an assertive one for
  "Alice slapped! Slap!" and the round result; the pile button keeps its accessible name in every state.

## 13. Texts

All English in `locales/en.ts`, all French in `locales/fr/kilodemerde.ts` (composed in `fr/index.ts`; the parity
test fails on a missing or untranslated key). Formal "vous" in French (project decision). The rules panel:

| Section | English | French |
|---------|---------|--------|
| Goal | Collect as few kilos as possible. You collect kilos when you are the last to slap the pile. | Récoltez le moins de kilos possible. Vous en récoltez quand vous êtes le dernier à taper sur le tas. |
| Setup | Everyone gets 4 cards. Only some ranks are dealt: Jacks, Queens and Kings with 3 players, plus the 2s with 4, the 3s with 5, the 4s with 6, the 5s with 7, the 6s with 8, the 8s with 9 and the 9s with 10. The 7s are never dealt. All the other cards make the kilo pile, face down. | Chacun reçoit 4 cartes. Seules certaines valeurs sont distribuées : les Valets, Dames et Rois à 3 joueurs, puis les 2 à 4, les 3 à 5, les 4 à 6, les 5 à 7, les 6 à 8, les 8 à 9 et les 9 à 10. Les 7 ne sont jamais distribués. Toutes les autres cartes forment le tas de kilos, face cachée. |
| Passing | Everyone picks a card at the same time and passes it to the player on their left; you receive one from your right. You have 15 seconds; after that, a card is passed for you. | Tout le monde choisit une carte en même temps et la passe à son voisin de gauche ; vous en recevez une de votre voisin de droite. Vous avez 15 secondes ; ensuite, une carte est passée pour vous. |
| Slapping | When a player holds four cards of the same rank, slap the pile! As soon as someone slaps, everyone must slap. The last one takes the top card of the kilo pile. Slap when nobody holds four of a kind and you take the card yourself. | Quand un joueur a quatre cartes de même valeur, tapez sur le tas ! Dès que quelqu'un tape, tout le monde doit taper. Le dernier prend la première carte du tas de kilos. Si vous tapez alors que personne n'a quatre cartes identiques, c'est vous qui la prenez. |
| Kilos | Ace 100 kg, King 13, Queen 12, Jack 11, other cards their value. A 7 is the flush: all your kilos go back into the pile with it. | As 100 kg, Roi 13, Dame 12, Valet 11, les autres cartes leur valeur. Le 7 est la chasse d'eau : tous vos kilos retournent dans le tas avec lui. |
| End | The game lasts the number of rounds chosen in the lobby, or until the kilo pile runs out. Fewest kilos wins; equal totals share the place. | La partie dure le nombre de manches choisi dans le salon, ou jusqu'à ce que le tas de kilos soit vide. Le moins de kilos gagne ; les égalités partagent la place. |

The screen texts (`kiloDeMerdeUi`) cover the strings of 12.2 (pile label, round and pass line, countdown, "Passing…",
"from {name}", race and round banners, the four outcome kinds, "last" tag, kilo popover title, standings headers, end
subtitles for the three reasons, live-region lines, error codes of the refusals). Picker texts
(`gamePicker.games['kilo-de-merde']`): title "Kilo de merde", tagline "Pass, collect four of a kind, slap the
pile. Last one takes the kilos." / "Passez, réunissez un carré, tapez sur le tas. Le dernier ramasse les kilos.",
players "3-10 players" / "3 à 10 joueurs". Options: "Rounds" / "Manches"; levels Easy, Medium, Hard / Facile, Moyen,
Difficile.

## 14. Tests

- Strict TDD at domain level: `KiloDeckTest` (table 3.1 for 3 to 10; no 7, Ace or 10 dealt; pile = 52 − 4n),
  `KiloValueTest`, `KiloDeMerdeRulesTest` (four of a kind, `defaultPass` ties), the worked examples of 8.3 as
  parameterised rows, `KiloDeMerdeEndTest` (rounds, empty pile, shared places), `KiloDeMerdeForfeitTest` (re-form,
  below 3), `KiloDeMerdeOptionsTest`, `KiloDeMerdeFactoryTest` (type, 3 to 10, options strict parse); bots: every
  level passes a card in hand on 1,000 random views, `medium` equals `defaultPass`, `hard` keeps its target rank on
  hand-written views, reflex delays within their ranges, opt-in benchmark.
- Presentation: `KiloDeMerdeDtoTest` (no foreign hand, choice or received card during a round; all hands in
  `lastOutcome`), bot view parity, `KiloDeMerdeGameActionsTest`, `KiloDeMerdeRestAndLifecycleTest`,
  `KiloDeMerdeWebSocketControllerTest`, `KiloDeMerdeWebSocketControllerIT` (STOMP: create, three seats, start, a
  step, a race to a draw, a false slap refused to nobody but resolved, the end), the timers with a scheduler stub
  (stale keys dropped), the coordinator (a race slap is not cancelled by another bot's slap), the seam ITs (present
  under `test`, absent otherwise).
- Frontend: Vitest per file (model, session, store, bootstrap, motion, components, screen), `parity.test.ts`.
- **Cypress: nominal only, one spec**, `cypress/specs/kilo-de-merde-game.cy.ts`, written in the entry chunk and
  added to `frontend/cypress/smoke-specs.json`: the host creates a room, two scripted players
  (`joinAsScriptedPlayer(gameId, name, 'kilo-de-merde')`) join, the seam scripts the deal (the host is one card from
  four Kings, the player on the host's right holds the fourth) and a one-card pile (`A♠`); Start; the host taps a
  card to pass, the scripted players pass; the host receives the King and taps the pile; the scripted players slap
  in order; the end screen (the round pause is 0 under `test`, so the round banner is covered by Vitest, not here)
  shows the host and the first scripted slapper sharing 1st place at 0 kg and the last one 3rd with 100 kg. No lobby-only spec, no bot spec, no flush or timeout spec (Vitest and backend tests cover
  them); the shared picker specs get the new game in their lists.

## 15. Later (not built; each cheap and separable)

| Variant | Source | Why later |
|---------|--------|-----------|
| Bluff: a "fake slap" that catches whoever follows | [Rj] | needs a gesture and a rule for who is caught |
| Literal turn-by-turn passing from the queen of hearts | [Ca] | A2; could be an option `passing = simultaneous / chain` |
| Jokers in the kilo pile (0 kg or "double") | box contents | no source rule |
| A target score instead of a number of rounds | none | an option value of `rounds` |
| Pass to the right | [Rj] | a direction option |
