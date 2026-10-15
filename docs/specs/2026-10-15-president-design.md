# President (Le Président / Trou du cul) — design

Scope: a fifth game, **President**, with its variants, from the domain to the bots, delivered as a series of
PRs (plan: `docs/plans/2026-10-15-president-plan.md`). Written by Claude Opus 5.5 on `origin/main` at
`28c5832`. This document holds no code; names follow `docs/architecture/game-conventions.md` and the
checklist `docs/architecture/add-a-game.md`.

Labels used below:

- **[S]** sourced rule (sources in section 12), several sources agree;
- **[U]** uncertain: a single source, sources disagree, or the detail is not written anywhere and is chosen
  here; each one is listed again in section 13;
- **[D]** a design choice of this spec where no rule applies (online play, data model, UI).

A docker-compose service called `capitalism` exists outside this repository; it is unrelated and ignored.

## 1. Goal

Add President as a seat game hosted by the existing session core (rooms, seats, tokens, lobby, options,
presence, forfeit, play again, bots as flagged seats), with every rule in a pure `president/domain` aggregate.
3 to 8 players with one deck, up to 10 with two decks. A **match** is a series of rounds: in each round the
players race to empty their hands by climbing tricks; the finishing order gives titles (President ... Scum)
that set a card exchange at the start of the next round. Points over rounds decide the match winner.

The game is turn-based: unlike Kobo or Bataille Corse there is no race. Variants that would introduce
out-of-turn plays are not offered (section 3.4).

## 2. Identity

| Form | Value |
|------|-------|
| `<id>` | `president` |
| `<pkg>` | `president` |
| `<Pascal>` | `President` |
| `<camel>` | `president` |
| Display name (English UI) | President |
| Aliases shown in the rules text | Le Président, Trou du cul, Scum, Asshole, Arschloch, Capitalism, Daifugō (cousin) |

The id is a plain word, not a prefix of another game id, and reads well in `/games/president` [D].

## 3. Rules

### 3.1 Base game (always on) [S]

- **Deck**: 52 cards (no jokers unless the `jokers` option says so). Suits do not matter, except under the
  run and suit-lock variants.
- **Ranking**, low to high: 3 4 5 6 7 8 9 10 J Q K A 2. Jokers, when used as high cards, rank above the 2.
- **Deal**: all cards are dealt one at a time in seat order, starting with the seat after the dealer; some
  players may hold one card more [S, pagat]. The dealer of round 1 is the last seat (so seat 0 receives the
  first card); from round 2 the dealer is the previous Scum [S, fr.wikipedia] (dealing is automatic; the
  dealer only decides who gets the extra cards) [D].
- **A play** is one or more cards of the same rank: single, pair, triple, quad (more with two decks or wild
  jokers).
- **Leading**: the leader of a trick plays any legal combination.
- **Following**: each next player in seat order either plays the **same number** of cards of a **strictly
  higher** rank, or **passes**. Passing is always allowed (unless `mustPlay`).
- **Trick end**: when every other player still in the trick has passed since the last play, the trick is won
  by the last player who played; the cards are set aside face down and that player leads a new trick with
  anything.
- **Lead after a player has gone out**: if the last play of a trick belongs to a player who has just emptied
  their hand, the lead goes to the next player in seat order who still has cards [S, fr.wikipedia, pagat].
- **Going out**: a player who plays their last card leaves the round; their finishing place is the next free
  place from the top (1st, 2nd...). Play continues among the others.
- **Round end**: when only one player still holds cards, that player takes the last place; the round is over
  (the last player does not play their cards out) [S].
- **Titles** from the finishing order, by player count `n` [S, fr.wikipedia, regles-du-jeu.net]:

| Place | n = 3 | n = 4 | n ≥ 5 |
|-------|-------|-------|-------|
| 1st | President | President | President |
| 2nd | Neutral | Vice-President | Vice-President |
| 3rd .. n-2 | - | - | Neutral (one or more) |
| n-1 | - | Vice-Scum | Vice-Scum |
| n | Scum | Scum | Scum |

French titles (rules text): Président, Vice-Président, Neutre, Vice-Trou du cul (also Secrétaire), Trou du
cul. The English UI uses President, Vice-President, Neutral, Vice-Scum, Scum (decision Q1).

- **Round 1** has no titles and no exchange [S]. Its first lead is set by `firstLead` (default: the holder of
  the 3 of clubs) [S, pagat, en.wikipedia].
- **Rounds 2 and later**: titles come from the previous round; the exchange (3.2) happens; the first lead is
  set by `nextLead` (default: the Scum) [S, regles-du-jeu.net; pagat gives the President, so it is an
  option].

### 3.2 Card exchange (rounds 2 and later) [S]

French classic, the default (`exchange = two`) [S, fr.wikipedia, regles-du-jeu.net, 10h26.com]:

- the Scum gives their **2 best** cards to the President, who gives back **2 cards of their choice**;
- the Vice-Scum gives their **best** card to the Vice-President, who gives back **1 card of their choice**;
- Neutrals neither give nor receive.

With 3 players only the President / Scum pair exchanges. "Best" follows the ranking in force at the deal
(jokers first when they are in play; a revolution never carries over the deal unless
`revolution = persistent`, see 3.3) [D].

Flow [D]: the server moves the low ranks' best cards at once when the round is dealt (they have no choice);
then each high rank picks the cards they give back, all pairs at the same time, in any order. Play starts
when every pending return is done. A high rank may give back a card they have just received [S, nothing
forbids it]. Each return is visible only to its two players; the others see the counts.

### 3.3 Variant catalogue

One row per variant. **Status**: *Offered* = a host option of this design (PR in the plan), *Not offered* =
listed for completeness, with the reason. **Combined** = how often it is played together with others in the
sources. Defaults are those of the "Classic" preset (section 4.2).

| # | Variant | Names | Rule | Combined | Interactions / conflicts | Default | Status | Certainty |
|---|---------|-------|------|----------|--------------------------|---------|--------|-----------|
| V1 | Re-entry after passing | "passer plusieurs fois son tour"; Australian "no playing after passing" is the opposite | `reenter`: a player who passed may play again when the turn comes back in the same trick. `out`: a pass is final until the trick ends | always one of the two | With `out`, the trick ends as soon as nobody but the last player is still in it. With equal-play skip, a skipped player is not "out" | `reenter` | Offered (`afterPass`), P1 | [U] sources split: pagat and regles-du-jeu.net allow re-entry, regles.com memo says a passer "generally does not come back" |
| V2 | Equal play skips the next player | "égaler (et faire sauter)", "ta gueule", "passage de tour", "Chump", "equal play skips" | A play of the same count and the **same** rank as the top is legal; the next player in turn is skipped (counted as a pass) | very common in French play | With two players left, the skip gives the turn back to the player, so they win the trick and lead [S, pagat]. Accumulated equal plays can complete a quad (V5, V7). Not allowed on a cut (V4) | `skip` | Offered (`equalPlay = skip`), V1 | [S] regles-du-jeu.net, fr.wikipedia, pagat, 10h26 |
| V3 | "... or nothing" | "X ou rien", "carte égale ou rien" | As V2, but the skipped player may instead play the same rank and count, which passes the constraint on to the following player; a pass ends the constraint | common in schoolyard play | Same as V2 | off | Offered (`equalPlay = orNothing`), V1 | [U] fr.wikipedia describes it as a sub-variant of V2 ("if that next player also holds a card of the same value, they may play it"); the name "ou rien" is not found in any written source |
| V4 | The 2 cuts | "le 2 coupe", "2 utilisé comme atout", "single two wins", "bomb" | `single`: a single top card (2) may be played on any play (any count) and ends the trick at once; the player leads. `ladder`: k twos beat a set of k+1 cards of a lower rank (a 2 beats a pair, two 2s a triple, three 2s a quad), and end the trick | common | Under a revolution the cutting card is the top rank in force (the 3) [U]. A cut is not allowed as the answer to an "or nothing" constraint [D]. Leading with a 2 is allowed (it simply wins the trick at once) [U: en.wikipedia mentions groups that forbid leading a 2] | off | Offered (`twoCuts = off|single|ladder`), V1 | [S] single: fr.wikipedia, pagat, 10h26, officialgamerules; ladder: fr.wikipedia example "two 2s beat a triple of jacks", pagat Sofia/Toronto |
| V5 | Quad clears the trick | "carré qui coupe", "bombe", Danish Røvhul "four of a rank clear the pile" | A play of four (or more) cards of one rank, or the fourth card of a rank completed by successive equal plays (V2), ends the trick; that player leads | regional | With revolution (V8) both apply: order reversed, then a fresh lead. With `revolution = trick` the reversal has no effect (documented, not refused) | off | Offered (`quadClears`), V1 | [S] pagat (Røvhul), gathertogethergames; the completion-by-equal-plays form is Danish only [U] |
| V6 | Eight stops | "8 coupe", "8 giri", "eight cut", "eights end the trick" | Any play containing an 8 ends the trick; the player leads | Japanese Daifugō; rare in French play | Forbidden finish (V9) also covers 8s when this variant is on [S, pagat Daifugō] | off | Offered (`eightClears`), V1 | [U] well sourced for Daifugō (pagat), no written French source |
| V7 | Forbidden finish | "interdiction de terminer par un 2", "on ne finit pas sur un 2", "cannot go out with the highest card" | A player whose last play contains a top-rank card (2; the 3 under a revolution), a joker, or an 8 under V6, goes out but is placed **last**; among several such players the later one is lower [S, pagat Røvhul] | very common in French play | Under a revolution the forbidden card is the top rank in force (3) [S fr.wikipedia, pagat Daifugō]. Alternative "the play is refused" not offered [U] | `lastPlace` | Offered (`forbiddenFinish = off|lastPlace`), V1 | [S] regles-du-jeu.net, fr.wikipedia, 10h26, pagat Daifugō |
| V8 | Revolution | "révolution", "contre-révolution", "four of a kind inverts ranks", "kakumei" | A quad (four or more of a rank, jokers included) reverses the ranking (3 high, 2 low); a later quad reverses it back (counter-revolution). Duration: `round` (until the end of the round) [S, fr.wikipedia, pagat Daifugō], `trick` (until the trick ends) [U, fr.wikipedia also says so], `persistent` (carries over to later rounds until a new quad) [S, fr.wikipedia variant] | common | Effect is **immediate**: the next player must beat the quad in the reversed order, i.e. with a lower quad [S, fr.wikipedia "must follow the newly established order"]; pagat Daifugō says "from the next clearing" [U]. Jokers stay on top under a revolution [D, Daifugō]. Exchange "best cards" follow the order in force at the deal | off | Offered (`revolution = off|round|trick|persistent`), V2 | [S] for the rule, [U] for timing and duration |
| V9 | Jokers | "avec Joker", "joker coupe", "joker wild" | `high`: 2 jokers (4 with two decks) rank above the 2; a single joker beats any play and ends the trick [S, regles-du-jeu.net Québec]; jokers may be paired with each other. `wild`: a joker joins a set and takes its rank; a natural set and a wild set of the same rank are equal; a lone joker or a set of jokers only is the highest rank | common (high), less (wild) | Wild jokers count toward a quad (V5, V8) [S, Daifugō]. Jokers are not usable in runs (V12) [D]. A joker as last card is a forbidden finish (V7) | `none` | Offered (`jokers = none|high|wild`), V2 | [S] pagat, en.wikipedia, regles-du-jeu.net; "natural equals wild" is one of three variants in pagat [U] |
| V10 | Aces high, 2 low | "Aces high", West Coast | Ranking A K Q ... 3 2 | rare | First lead `lowestClub` becomes the 2 of clubs | `twoHigh` | Offered (`ranking = twoHigh|aceHigh`), V2 | [S] pagat, en.wikipedia |
| V11 | Compulsory beating | "obligation de jouer", "compulsory beating" | A player who has a legal play must play; pass only when none | rare | Counts cuts and equal plays as legal plays when those variants are on [D] | off | Offered (`mustPlay`), V1 | [S] pagat. "Obligation de jouer le plus petit" (must play the lowest legal play) has **no** written source [U]: not offered |
| V12 | Runs | "suites", "sequences", "kaidan" | A run is 3 or more consecutive ranks; `sameSuit`: all of one suit (Daifugō); `anySuit`: suits free. A run is beaten only by a run of the same length with a higher top card. 2 may end a run (K-A-2), no wrap (A-2-3 is not a run) | Daifugō, Zheng Shangyou | Equal play (V2) on a run: same length and same top. Revolution reverses run order. No jokers in runs [D] | off | Offered (`runs = off|sameSuit|anySuit`), V3 | [S] pagat Daifugō for `sameSuit`; `anySuit` is the Zheng Shangyou family [U for President] |
| V13 | Suit lock | "shibari", "following suit", "même couleur" | When two consecutive plays in a trick have the same suits, every later play of the trick must use those suits | Daifugō | Wild jokers bypass the lock [S, Daifugō]. With runs `sameSuit`, a run locks when beaten by a run of the same suit | off | Offered (`suitLock`), V3 | [S] pagat Daifugō; the partial lock "kata-shibari" is not offered |
| V14 | Exchange amount | "deux cartes / une carte", "Butthead version", Hűbéres 3/2/1 | `none`; `one` (top pair exchanges one card); `two` (default, 3.2); `scaled` (with p title pairs, the i-th pair from the top exchanges p - i + 1 cards: 6 players 3/2/1) | always | `scaled` with 3 players is `one` | `two` | Offered (`exchange`), P2 | [S] pagat, fr.wikipedia |
| V15 | What the high rank gives back | "le Président donne ses plus faibles", "returns any unwanted card" | `free`: cards of their choice [S]; `lowest`: automatically their lowest cards, even out of a pair [S, fr.wikipedia variant] | always one | `lowest` makes the exchange instant (no exchange phase wait) | `free` | Offered (`giveBack`), P2 | [S] |
| V16 | First lead of round 1 | "3 de trèfle", "Dame de cœur", "joueur après le donneur" | `lowestClub` (holder of the lowest club: 3♣, or 2♣ with aces high); `queenOfHearts` (fr.wikipedia variant); `random` (a seat drawn at random, replacing "after the dealer") | always one | With two decks, the first holder after the dealer [D]. The leader is not obliged to include that card [U] | `lowestClub` | Offered (`firstLead`), P1 | [S] pagat, en.wikipedia, fr.wikipedia |
| V17 | First lead of rounds 2+ | "le Trou du cul commence", "the President leads" | `scum` or `president` | always one | - | `scum` | Offered (`nextLead`), P2 | [S] both, sources split |
| V18 | Points and match length | "points", "partie en N donnes" | Scoring `rank` (place p of n scores n - p, fr.wikipedia), `titles` (President 2, Vice 1, others 0, pagat), `zeroSum` (+k for the k-th title pair from the top, -k from the bottom: 5 players 2 1 0 -1 -2, pagat). Length: 3, 5 or 10 rounds, or first to 11 or 21 points | always | `zeroSum` with a points target may never end: the match then ends after 10 rounds anyway [D] | `rank`, `rounds-5` | Offered (`scoring`, `length`), P2 | [S] pagat, fr.wikipedia, regles-du-jeu.net (4/8/12 deals) |
| V19 | Two decks | "plusieurs jeux", "double deck" | 104 cards (108 with jokers) for many players; identical cards are interchangeable | 9+ players | Sets up to 8 cards; a quad is "four or more"; the 3♣ lead goes to the first holder after the dealer | `auto` (one deck up to 8 players, two above) | Offered (`decks = auto|one|two`), V4 (max players 8 → 10) | [S] that it exists (en.wikipedia, pagat); no written rule for its details [U] |
| V20 | Putsch | "le putsch" | Before the exchange, the Scum may declare a putsch: no exchange this round. If the Scum finishes first, they are President with one bonus point; otherwise the round stops as soon as someone else goes out first, every player keeps their previous title, the putschist loses one point, gives their 4 best cards to the President next round and cannot putsch then | rare | Not with `exchange = none` (nothing to refuse) [D] | off | Offered (`putsch`), V4 | [U] single source (fr.wikipedia, citation needed) |
| V21 | The President falls | "miyako-ochi", "falling from the capital" | If the reigning President does not finish first, they are placed last at once (out of the round) | Daifugō | With putsch: a successful putsch already removes the President from first place; the fall still applies [D] | off | Offered (`presidentFall`), V4 | [S] pagat Daifugō; not French [U] |

### 3.4 Variants not offered

| Variant | Source | Why not |
|---------|--------|---------|
| Out-of-turn plays (a 2, or completing a quad, out of turn) | en.wikipedia, gathertogethergames | Turns the game into a race; the turn model stays strict |
| Single-lap tricks (each player gets one chance) | pagat | Close to `afterPass = out`; one more rule for little gain |
| Larger sets beat smaller ones (by rank or regardless of rank) | pagat | Rare; conflicts with the same-count rule every other variant assumes |
| Skip as many players as cards played | pagat | Rare; V2 covers the common form |
| Colour reversing, suit ranking, following suit for singles | pagat | Rare; suit lock (V13) covers the suit family |
| Transparent threes, black threes or jack of diamonds high, 3♠ beats a joker | pagat | Rare, single sources |
| 10 discards, 7 passes, 11 back, 5 skip | pagat Daifugō, Japanese apps | Japanese local rules, no French or English President source |
| 32-card deck (7 high) for 3-4 players | fr.wikipedia | Rare; a `deck` option can come later without model change |
| Extra cards set aside at the deal | fr.wikipedia | The default deal gives every card [S, pagat] |
| Collective best cards (a pair of kings over an ace and a king), negotiated exchange, merchants, auction | fr.wikipedia, pagat | Needs negotiation or interpretation; no server rule |
| Hand of Collusion, Klootzakken, drinking and seating penalties | pagat, fr.wikipedia | Different games or social rules |
| "Must play the lowest legal play" | none found | No written source [U] |
| Revolution triggered by a 5-card run | pagat Daifugō | Only with runs, rare; can be added to V12 later |

## 4. Options (host, game-level)

### 4.1 Option keys

All options are `OptionType.CHOICE` (the only built type) and go through the existing lobby mechanism:
`GameFactory.optionDescriptors()` declares them, the host edits them before Start, `SessionService.updateOptions`
merges a change map and calls `PresidentFactory.validate`, which parses them strictly into a
`PresidentOptions` record (pattern of `BullshitOptions.parseStrict`). Label keys are
`options.president.<key>`. Every combination is legal: a combination where one rule makes another moot is
documented, never refused, so the host can set options in any order [D].

| Key | Values (first = default) | PR |
|-----|--------------------------|----|
| `afterPass` | `reenter`, `out` | P1 / P3 |
| `firstLead` | `lowestClub`, `queenOfHearts`, `random` | P1 / P3 |
| `exchange` | `two`, `one`, `scaled`, `none` | P2 / P3 |
| `giveBack` | `free`, `lowest` | P2 / P3 |
| `nextLead` | `scum`, `president` | P2 / P3 |
| `scoring` | `rank`, `titles`, `zeroSum` | P2 / P3 |
| `length` | `rounds-5`, `rounds-3`, `rounds-10`, `points-11`, `points-21` | P2 / P3 |
| `equalPlay` | `skip`, `orNothing`, `off` | V1 |
| `twoCuts` | `off`, `single`, `ladder` | V1 |
| `quadClears` | `off`, `on` | V1 |
| `eightClears` | `off`, `on` | V1 |
| `forbiddenFinish` | `lastPlace`, `off` | V1 |
| `mustPlay` | `off`, `on` | V1 |
| `jokers` | `none`, `high`, `wild` | V2 |
| `revolution` | `off`, `round`, `trick`, `persistent` | V2 |
| `ranking` | `twoHigh`, `aceHigh` | V2 |
| `runs` | `off`, `sameSuit`, `anySuit` | V3 |
| `suitLock` | `off`, `on` | V3 |
| `decks` | `auto`, `one`, `two` | V4 |
| `putsch` | `off`, `on` | V4 |
| `presidentFall` | `off`, `on` | V4 |

Until a variant's PR ships, its key does not exist (the server refuses it as unknown), and the domain
behaves as the "off" value. Defaults are the Classic preset, so a room created without touching anything
plays French classic rules.

### 4.2 Presets (frontend convenience) [D]

A preset is a named bundle of values sent as **one** change map (`updateOptions` already merges several keys
in one call); the server knows no preset. The lobby shows which preset matches the current values, or
"Custom".

| Preset | Values (only those that differ from the defaults) |
|--------|---------------------------------------------------|
| Classic (French) | defaults: re-entry, equal play skips, forbidden finish on a 2, exchange 2/1, Scum leads, rank points, 5 rounds |
| Pagat basic | `equalPlay=off`, `forbiddenFinish=off`, `exchange=one`, `nextLead=president`, `scoring=titles`, `length=points-11` |
| Strict | `afterPass=out`, `equalPlay=off`, `forbiddenFinish=off` |
| Party | `jokers=high`, `twoCuts=single`, `revolution=round`, `equalPlay=orNothing` |
| Daifugō-like | `equalPlay=off`, `forbiddenFinish=lastPlace`, `jokers=wild`, `revolution=round`, `eightClears=on`, `runs=sameSuit`, `suitLock=on`, `presidentFall=on`, `nextLead=scum` |

A preset that names a key not yet shipped is hidden until it ships.

### 4.3 Seat options

Only for bots (B2): `level` = `easy|medium|hard`, default `medium`, label `options.president.level`, the
Kobo pattern.

## 5. Players, decks, deal

- `minPlayers() = 3`, `maxPlayers() = 8` from P3; `maxPlayers() = 10` from V4 (two decks) [D]. Two players is
  mechanically possible but degenerate (no title pairs to speak of); it is excluded (decision Q2).
- Deck: `CardsService.createDeck(DeckType.FRENCH | FRENCH_WITH_JOKERS, ...)` (both exist in `frenchcards`
  0.2.1; jokers are `FrenchRank.JOKER` with suits `RED_JOKER` / `BLACK_JOKER`; the frontend already has the two
  joker SVGs). Two decks are two such decks concatenated.
- `frenchcards.Card.equals` compares rank and suit, so two identical cards of a double deck are equal: hands
  are multisets (`List<Card>`, removal by one matching instance) and a command naming a card twice removes
  two copies. No card identity is needed because identical cards are interchangeable [D].
- **Determinism seam** (Kobo pattern, `add-a-game.md` §5): a `DeckSource` port in `president/domain`
  (`List<Card> deck(GameId, int round, long seed, DeckSpec)`), `RandomDeckSource` by default,
  `ScriptedDeckSource` and `PresidentTestDealController` (`PUT`/`DELETE /api/test/president/{gameId}/deal`)
  under the Spring `test` profile only.

## 6. Domain model (`president/domain`)

One aggregate root, `President implements Game`, Kobo style: every mutating method is `synchronized`,
validates everything first, then mutates, bumps `version()` and returns a small outcome; a refused command
never mutates. The aggregate owns the whole match (rounds, titles, points), because the exchange, the
end-of-match condition, forfeit and rematch span rounds. Package-private constructor with explicit hands for
tests (`PresidentBuilder`).

Sub-packages (each with the exceptions it protects):

| Package | Content |
|---------|---------|
| `rules` | `PresidentOptions` (typed options, `from` lenient / `parseStrict` strict, `toGameOptions`), one enum per option (`AfterPass`, `EqualPlay`, `TwoCuts`, `Jokers`, `Revolution`, `Ranking`, `Runs`, `ExchangeAmount`, `GiveBack`, `FirstLead`, `NextLead`, `Scoring`, `MatchLength`, `Decks`), `PresidentException` |
| `card` | `RankOrder` (strength of a rank under `ranking` and the revolution state; jokers), `HandSort` |
| `play` | `Play` (value object: cards, kind `SET` or `RUN`, effective rank or top, count, suits, wild count), `PlayParser` (cards → `Play` or `IllegalPlayException`), `BeatRule` (does a play beat the top, is it equal, is it a cut), `LegalPlays` (every legal play of a hand against the trick state: used by the DTO hints, `mustPlay` and the bots), `PlayEffects` (clears the trick, reverses, skips) |
| `trick` | `Trick` (leader, plays, last player, seats that passed or were skipped since the last play, seats out of the trick under `afterPass = out`, constraint `FREE`, `BEAT`, `MATCH_OR_PASS`, locked suits) |
| `round` | `Round` (hands, finishing order, forbidden finishers, phase, current seat, revolution state, dealer), `Phase` |
| `exchange` | `Title`, `Titles.forPlaces(n)`, `ExchangePlan` (pairs and amounts from `exchange`, `n` and a failed putsch), `PendingReturn` |
| `scoring` | `MatchScoring` (pure: points of a round from the finishing order, putsch bonus or malus), `RoundResult`, `Standings` |
| `event` | sealed `PresidentEvent`, `PresidentResult` (winners, reason `POINTS` or `FORFEIT`) |

### 6.1 Phases

| Phase | Meaning | Leaves with |
|-------|---------|-------------|
| `PUTSCH` | (V4, `putsch = on`, round ≥ 2) the Scum decides | `declarePutsch(true|false)` |
| `EXCHANGE` | round ≥ 2 with an exchange; low ranks' best cards already moved; high ranks choose what to give back | the last `giveBack` (instant when `giveBack = lowest`) |
| `PLAYING` | tricks | the last but one player goes out (or a failed putsch ends the round) |
| `ROUND_OVER` | round scored and shown; barrier | `ready` from every remaining player, or `FINISHED` |
| `FINISHED` | the match length is reached, or one player is left | Play again (outside the aggregate) |

### 6.2 Commands and queries

Commands: `play(seat, List<Card>)`, `pass(seat)`, `giveBack(seat, List<Card>)`, `ready(seat)`,
`declarePutsch(seat, boolean)` (V4), `forfeit(PlayerId)` (the `Game` contract).

Queries: `phase()`, `round()`, `version()`, `currentSeat()`, `hand(seat)`, `handSizes()`, `trick()` (read
model), `revolution()`, `finishingOrder()`, `titles()` (titles in force this round, from the previous one),
`exchange()` (pairs, amounts, pending returns, and per seat what they gave and received), `standings()`,
`lastRoundResult()`, `legalPlays(seat)`, `availableActions(seat)` (`PLAY`, `PASS`, `GIVE_BACK`, `READY`,
`PUTSCH`), `options()`, `result()`.

Rules enforced in the domain only: turn ownership, phase, card ownership (multiset), combination validity,
beating, equal play and its skip, cuts and clears, revolution, forbidden finish, compulsory play, the
exchange (who, how many, best cards), first leads, titles, scoring, end of match.

### 6.3 Trick algorithm [D]

- Eligible seats: seats with cards, not forfeited and, under `afterPass = out`, not out of this trick.
- `play`: the cards must be in the hand; `PlayParser` builds the `Play`; `BeatRule` checks it against the
  constraint (`FREE` when leading; `BEAT` same count or a cut; `MATCH_OR_PASS` same rank and count only).
  Then, in this order: remove the cards; record the play; apply the revolution (quad); detect a going-out
  (place, forbidden finish, president fall); apply the clear effects (cut, 8, quad, joker): the trick ends
  and the player leads (or the next eligible seat when they just went out); otherwise compute the skip
  (equal play) and the next seat. The "responded since the last play" set is cleared on every play.
- `pass`: allowed for the current seat unless the trick is `FREE` (a leader must lead) or `mustPlay` and a
  legal play exists. The seat joins "responded" (and "out of the trick" under `afterPass = out`); under
  `MATCH_OR_PASS` the constraint ends after this pass.
- Skip: after an equal play (`equalPlay = skip`) the next eligible seat is added to "responded" with a
  `Skipped` event and the turn moves past it; under `orNothing` that seat is instead given the
  `MATCH_OR_PASS` constraint.
- Trick end: when every eligible seat except the last player is in "responded". The winner is the last
  player if they still have cards, else the next eligible seat after them. Event `TrickWon(reason)`, reasons
  `ALL_PASSED`, `CUT`, `EIGHT`, `QUAD`, `JOKER`.
- Round end: one seat with cards left (or a failed putsch). That seat takes the last free place, above the
  forbidden finishers, who fill the bottom in reverse order of going out [S, pagat Røvhul].

### 6.4 Events (domain outcomes)

`RoundDealt(round, dealer, handSizes)`, `BestCardsTaken(from, to, count)` (cards only for the pair),
`CardsGivenBack(from, to, count)`, `ExchangeDone`, `PutschDeclared(seat)`, `PutschDeclined`, `Played(seat,
cards, kind)`, `Passed(seat)`, `Skipped(seat)`, `RevolutionToggled(active)`, `TrickWon(seat, reason)`,
`PlayerOut(seat, place, forbidden)`, `PresidentFell(seat)`, `RoundScored(RoundResult)`, `Ready(seat)`,
`MatchFinished(PresidentResult)`, `PlayerForfeited(seat)`.

### 6.5 Exceptions

All extend a checked `PresidentException`: `FinishedGameException`, `WrongPhaseException`,
`NotPlayersTurnException`, `CardsNotInHandException`, `IllegalPlayException` (not one rank, not a run, jokers
where not allowed), `PlayDoesNotBeatException`, `MustLeadException`, `MustPlayException`,
`WrongGiveBackException` (count, or not your turn to return), `AlreadyReadyException`.

## 7. Presentation

### 7.1 WebSocket (`/app/president/...`, token-authenticated through `SeatCommands`)

| Destination | Payload | Effect |
|-------------|---------|--------|
| `/president/start` | `GameActionPayload` | host starts (game-type check first: "This room is not a President game") |
| `/president/play` | `PresidentCardsPayload(gameId, token, cards: CardDto[])` | play |
| `/president/pass` | `GameActionPayload` | pass |
| `/president/giveBack` | `PresidentCardsPayload` | exchange return |
| `/president/ready` | `GameActionPayload` | round barrier |
| `/president/putsch` | `PresidentPutschPayload(gameId, token, declare)` | V4 |
| `/presence`, `/forfeit` | existing | unchanged |

`PresidentEventType`: `START`, `ROUND_START`, `EXCHANGE`, `PUTSCH`, `PLAY`, `PASS`, `SKIP`, `TRICK_WON`,
`REVOLUTION`, `PLAYER_OUT`, `ROUND_END`, `READY`, `GAME_OVER`. One `SuccessResponse` per human seat through a
`SeatStateBroadcaster<President, PresidentDto>`; refusals answered to the sender alone with
`ErrorResponse(type, message, state)`.

### 7.2 REST (`/api/president`) over `SeatGameRest`

`GET /game/{id}?token=`, `POST /game/{id}/join`, `POST /game/{id}/play-again`.

### 7.3 Per-seat DTO (`PresidentDto.forViewer(game, viewer, names, bots)`, the only projection)

Public: `version`, `round`, `rounds` (length), `phase`, `currentSeat`, `revolution`, `options` (the values in
force, for the rules panel and table hints), per seat `{seat, name, bot?, handSize, title, place, out,
forbiddenFinish, passed, skipped, points, ready}`, the trick `{leader, plays: [{seat, cards}], top,
constraint, requiredCount, lockedSuits, lastPlayer}`, exchange counts per pair, `lastRound` (finishing order,
titles, points, putsch outcome), `standings`, `outcome`.

Private (viewer only): `hand` (sorted by strength in force), `received` / `given` (the exchange cards of the
viewer's pair), `toGiveBack` (count, while pending), `legalPlays` (patterns `{kind, rank or top, count,
suits?, wild?}` for the viewer's turn, from the domain `LegalPlays`, so the client never computes a rule;
the client only matches a selection to a pattern), `availableActions`.

Never in another seat's DTO: a card of a hand, the exchange cards of another pair.

### 7.4 Configuration

`config/PresidentConfig.java`: `presidentFactory`, `presidentStateBroadcaster`, `presidentGameActions`,
`presidentLifecycleBroadcaster` (a `SeatLifecycleBroadcaster`), the `DeckSource` (random / scripted by
profile), later the bot coordinator. `AppConfig` is not edited. Shared tests updated per `add-a-game.md` §2.2
(`PerGameConfigTest`, `ArchitectureRules.GAMES`, `ArchitectureTest`, `MalformedTokenIT`,
`LobbyControllerTest`).

## 8. Disconnect, forfeit, rematch

- **Disconnect**: the existing 60 s grace; the table waits for the player (their turn, their exchange
  return, the ready barrier). No turn timer, as in every other game [D].
- **Forfeit** (timer or `/forfeit`): the player leaves the match. During `EXCHANGE` a pending return is first
  completed with their lowest cards; then their hand is removed from play. If they were the current player
  the turn moves on; if their play is the trick top, the trick goes on and its winner is computed as for a
  player who went out. They take no place in the round; titles are computed among the others. Their points
  are dropped from the standings (Kobo). A forfeiting putschist voids the putsch. Down to two players the
  match goes on; one player left: `FINISHED`, reason `FORFEIT` [D].
- **Rematch**: as Kobo and Bullshit: `FINISHED`, then `POST /api/president/game/{id}/play-again`; a new match
  starts at round 1 with no titles; options and bots are kept by the session core.

## 9. Frontend (summary; screens decided through the canvas, section 11)

Files per `game-conventions.md` §4: `model/president/` (`PresidentState.ts`, `selection.ts` matching a
selection to a `legalPlays` pattern, `titles.ts`), `application/PresidentSession.ts` (`PRESIDENT_SESSION_CONFIG`,
commands `play`, `pass`, `giveBack`, `ready`, `putsch`), `state/President.store.ts`,
`composables/usePresidentBootstrap.ts`, `components/president/` (`PresidentHand`, `PresidentTrick`,
`PresidentSeat`, `PresidentExchangePanel`, `PresidentRoundSummary`, `PresidentLobbyOptions`,
`PresidentRuleChips`), `view/president/PresidentGameScreen.vue`. Texts in `presidentUi`, rules in `president`,
options in `options.president`. Registry: `define('president', { supportsBots, picker })`.

The lobby options component is data-driven: it renders the presets and groups the descriptors it knows
(Trick, Special cards, Exchange, Match); any descriptor it does not know falls back to the generic radio
group, so a backend variant never needs a frontend change to be settable (only texts).

## 10. Bots (later PRs)

Seat option `level` (`easy|medium|hard`), `PresidentFactory.supportsBots() = true` from B2.

- **Domain** (`president/domain/bot`, pure): `PresidentBotView.forSeat(game, seat)` (exactly what the seat's
  DTO shows, a parity test pins it), `PresidentBotMemory` (cards seen played this round, exchange cards
  received and given; never another hand), `PresidentBotStrategy` (port: `decide(view, memory)` returning
  `Play(cards)`, `Pass`, `GiveBack(cards)`, `Ready`, `Putsch(boolean)`), `HeuristicPresidentBot`,
  `PresidentBotLevel` / `PresidentBotTuning`.
- **Candidates** always come from the domain's `LegalPlays`, so a new variant can never make a bot play an
  illegal card; the heuristic only ranks them.
- **Heuristic** (all levels, tuned constants):
  - lead the lowest set that does not break a bigger set; keep control cards (top ranks, jokers, cutting 2s,
    eights under V6) for the end;
  - follow with the lowest beating play that does not break a set; pass rather than break a pair unless the
    hand is short; use an equal play when the skip lands on the player most dangerous (fewest cards);
  - end-game planner (`hard`): count the "tricks to go out" of the hand and make sure the last play is not a
    forbidden finish (keep a low card for last);
  - revolution: play a quad when the hand's average strength is low (it helps), keep it otherwise;
  - exchange: give back the lowest singletons, never break a pair; putsch only when the hand holds enough
    control cards (`hard`);
  - card counting (`medium`: ranks of the top cards; `hard`: every card) to know when a play is a sure
    winner.
- **Levels**: `easy` = a random legal play 30% of the time and a random pass when legal 20%; `medium` =
  heuristic with partial counting; `hard` = full counting and the planner.
- **Coordinator** (`presentation/bot/PresidentBotCoordinator`, Morris pattern: a `GameStateListener` and
  `GameEvictionListener`): after each broadcast, one task per bot seat that must act (its turn, its return,
  the barrier, the putsch), after the `ThinkingDelay` (`president.bots.delay-min-ms` / `delay-max-ms`, 700 to
  1300 ms, 0 under `test`), keyed on `version()` and dropped when stale; a failing strategy falls back to
  pass, else the lowest legal play, else the lowest cards to give back, else ready. No race, so no reaction
  delay.

## 11. UI decisions to take (Claude Design canvas, owner's choice before the frontend spec)

Per the global rule, each is settled on a canvas with 2-3 options on real President data (a 6-player table
at round 3 with titles, a revolution active, an exchange pending). This spec does not choose.

1. **Table layout for 3 to 10 players.** (A) Ring of opponents around the central trick, my hand fanned at
   the bottom (Kobo/Bullshit ring); (B) a compact list of opponent rows (name, title badge, card count,
   passed / out) above the trick, scaling to 10 on a phone; (C) an arc of opponents across the top only, the
   trick in the middle, my hand at the bottom.
2. **Selecting and playing cards.** (A) Tap cards to select, Play enabled only when the selection matches a
   `legalPlays` pattern; (B) the hand grouped in rank stacks, tap a stack then a count chip (1..n);
   (C) a bar of suggested plays ("Pair of 9s", "Single 2: cut") above the hand, plus free selection.
3. **Lobby options (about twenty keys).** (A) Preset cards (Classic, Pagat, Strict, Party, Daifugō) with a
   "Customise" disclosure grouping every option; (B) all options in four accordions (Trick, Special cards,
   Exchange, Match) with the generic radios; (C) preset first, then a list of rule chips, each opening a
   small sheet to change it.
4. **Exchange step.** (A) Inline at the table: a banner, my received cards highlighted in my hand, select k
   cards and Give; (B) a modal sheet with "You received" and "Choose k cards to give back", with a
   "Suggest" button; (C) a trade view showing both partners of each pair (counts only for others).
5. **Round end and standings.** (A) An overlay sheet with the finishing order, title changes (arrows), round
   and total points, ready checks; (B) a persistent side scoreboard plus a short toast at round end;
   (C) a full-screen podium (President to Scum) then the next-round button.
6. **Trick and rule state on the table.** (A) Only the top play, with a small stack and a log line;
   (B) the whole trick fanned with the seat name on each play; (C) top play plus status chips (Revolution,
   "9 or nothing", Suits locked, Skip) next to the pile.

## 12. Sources

- fr.wikipedia, "Trou du cul (jeu)": https://fr.wikipedia.org/wiki/Trou_du_cul_(jeu) (ranking, titles,
  exchange 2/1 and its variants, revolution and its duration, putsch, passage de tour, 2 as trump,
  forbidden finish on a 2, rank points; carries citation-needed banners).
- en.wikipedia, "President (card game)": https://en.wikipedia.org/wiki/President_(card_game) (roles, deal,
  exchange, re-entry varies, bombs, no lead with a 2 in some groups, 3♣ start, 9+ players with two decks).
- pagat.com, "President": https://www.pagat.com/climbing/president.html (basic game with re-entry, all
  playing-procedure, card, suit and scoring variations, exchange versions, Røvhul).
- pagat.com, "Dai Fugō / Dai Hinmin": https://pagat.com/climbing/daifugo.html (runs, revolution timing,
  8-cut, shibari, miyako-ochi, forbidden finishes, joker and 3♠).
- pagat.com, "President variations": https://www.pagat.com/invented/president_vars.html (Chump skip).
- regles-du-jeu.net, "Règle du Président": https://www.regles-du-jeu.net/?p=8171 ("égaler et faire
  sauter", re-entry, forbidden finish on a 2, exchange 2/1, the Scum leads).
- regles-du-jeu.net, "Règle du Trou du cul (Président)": https://www.regles-du-jeu.net/?p=7688 (Québec:
  joker beats anything, points 4/3/2/0 over 4, 8 or 12 deals).
- regles.com memo: https://www.regles.com/fiches/trou-de-cul-memo.pdf (a passer "generally does not come
  back"; bombs and finishing on a 2 to fix before playing).
- 10h26.com (ex-Topito), "pire coup ... trouduc président":
  https://www.10h26.com/top-pire-coup-pute-jeu-trouduc-president ("ta gueule", 2 cuts, no finish on a 2).
- App Store listings of French President apps (variant names only: "jeu avec égalisation", "passage de
  tour sur égalisation", "passer plusieurs fois son tour", "Révolution", "Joker").

No written source was found for: the name "ou rien", the "8 coupe" rule in French play, "obligation de jouer
le plus petit". They are marked [U] or not offered.

## 13. Decisions to confirm

The first five are the key ones; the plan repeats them.

1. **Q1 — Identity and titles.** Id `president`, English UI titles President / Vice-President / Neutral /
   Vice-Scum / Scum; the French names (including "Trou du cul") only in the rules text. Alternative: French
   titles in the UI, or "Asshole".
2. **Q2 — Players and decks.** 3 to 8 players with one deck (jokers optional) when the game opens; 10 with two
   decks in V4. No 2-player mode.
3. **Q3 — Classic defaults.** Re-entry after a pass, equal play skips the next player, forbidden finish on a
   2 (last place), exchange 2/1 with a free return, the Scum leads rounds 2+, the 3♣ holder leads round 1,
   rank points over 5 rounds. Everything else off.
4. **Q4 — Exchange flow.** The server takes the low ranks' best cards automatically at the deal; the high
   ranks choose their returns freely, all pairs at once, with no timeout (the disconnect forfeit covers an
   absent player); a "Suggest" helper in the UI, not a server rule.
5. **Q5 — Delivery order.** Base game playable end to end first (P1-P3, F1-F4), then bots (B1-B3) on the base
   rules with candidates from the domain's `LegalPlays`, then the variant groups V1-V4 (each full stack: rule,
   option, texts, table indicator, bot tweak), then the e2e matrix.
6. Revolution takes effect immediately (the next player must answer in the reversed order) and lasts until
   the end of the round by default when enabled; jokers stay on top under a revolution; the cutting card and
   the forbidden finish follow the top rank in force (3 under a revolution).
7. Equal play counts as a pass for the skipped player; under "or nothing", the constraint ends at the first
   pass. A cut cannot answer an "or nothing" constraint.
8. Forbidden finishers go out and fill the bottom places, the later one lower.
9. A barrier (`ready`) between rounds, no timeout, as in Kobo.
10. Forfeit: the match continues with two players; one left ends it with reason `FORFEIT`; points of a
    forfeiter are dropped.
11. Every option combination is accepted; moot combinations are documented, not refused.
12. Uncertain rules kept as offered options: `orNothing` (V3), `eightClears` (V6), `revolution = trick`,
    `runs = anySuit`, double-deck details, putsch (V20), president fall (V21).
