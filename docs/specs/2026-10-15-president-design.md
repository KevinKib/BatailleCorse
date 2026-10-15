# President (Le Président / Trou du cul) — design

Scope: a fifth game, **President**, with its variants, from the domain to the bots, delivered as a series of
PRs (plan: `docs/plans/2026-10-15-president-plan.md`). Written by Claude Opus 5.5 on `origin/main` at
`28c5832`; amended on 2026-10-15 with the owner's decisions on the business questions (section 13). This
document holds no code; names follow `docs/architecture/game-conventions.md` and the checklist
`docs/architecture/add-a-game.md`.

Labels used below:

- **[S]** sourced rule (sources in section 12), several sources agree;
- **[O]** the owner's rule, decided on 2026-10-15 (section 13), whatever the written sources say;
- **[U]** uncertain: a single source, sources disagree, or the detail is not written anywhere and is chosen
  here; each one still open is listed again in section 13;
- **[D]** a design choice of this spec where no rule applies (online play, data model, UI).

A docker-compose service called `capitalism` exists outside this repository; it is unrelated and ignored.

## 1. Goal

Add President as a seat game hosted by the existing session core (rooms, seats, tokens, lobby, options,
presence, forfeit, play again, bots as flagged seats), with every rule in a pure `president/domain` aggregate.
3 to 8 players, one 52-card deck, no jokers [O]. A **match** is a series of rounds: in each round the
players race to empty their hands by climbing tricks; the finishing order gives titles (President ... Scum)
that set a card exchange at the start of the next round. Points by place, accumulated over the rounds,
decide the match winner; an `endless` match chains rounds until the players leave (section 3.3, V18).

The game is turn-based. The one exception is the owner's **completion out of turn** (3.1.1): the holder of
the last cards of a rank whose chain is the top of the trick may play them at once. With one deck a single
seat can ever do it, so it is not a race between players, only an ordering question against the next play
(section 6.3). Other variants that would introduce out-of-turn plays are not offered (section 3.4).

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

- **Deck**: 52 cards, never any joker [O]. Suits do not matter, except under the run and suit-lock variants
  and for the first lead of round 1.
- **Ranking**, low to high: 3 4 5 6 7 8 9 10 J Q K A 2.
- **Deal**: all cards are dealt one at a time in seat order, starting with the seat after the dealer; some
  players may hold one card more [S, pagat]. The dealer of round 1 is the last seat (so seat 0 receives the
  first card); from round 2 the dealer is the previous Scum [S, fr.wikipedia] (dealing is automatic; the
  dealer only decides who gets the extra cards) [D].
- **A play** is one or more cards of the same rank: single, pair, triple or quad.
- **Leading**: the leader of a trick plays any legal combination.
- **Following**: each next player in seat order either plays the **same number** of cards of a **strictly
  higher** rank, or **passes**. Passing is always allowed (unless `mustPlay`). Under `equalPlay` (on by
  default, V2) a play of the **same** rank is also legal and binds the next player (3.1.1).
- **Re-entry** [O]: a player who passed may play again when the turn comes back to them in the same trick
  (`afterPass = reenter`, the default). Under `afterPass = out` a pass is final until the trick ends.
- **Trick end**: when every other player still in the trick has passed since the last play, the trick is won
  by the last player who played; the cards are set aside face down and that player leads a new trick with
  anything. Under `afterPass = out` this means the trick ends when every eligible player except the last one
  has passed [O, reading confirmed].
- **Going out**: a player who plays their last card leaves the round; their finishing place is the next free
  place from the top (1st, 2nd...). Play continues among the others.
- **Trick after a player goes out** [O]:
  - when the **first** player of the round goes out (the one who takes 1st place, the President), the trick
    closes at once and the next seat in seat order that still holds cards leads a fresh trick;
  - when a **later** player goes out, the trick goes on: the others may still beat that last play, and when
    the trick closes on it, the lead goes to the next player in seat order who still has cards
    [S, fr.wikipedia, pagat].
  - A forbidden finisher (V7) does not take 1st place, so their going out lets the trick go on [D, to
    confirm, section 13].
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
cul. The English UI uses President, Vice-President, Neutral, Vice-Scum, Scum [O].

- **Round 1** has no titles and no exchange [S]. Its first lead is set by `firstLead`; the default is the
  holder of the **king of hearts** [O]. The written sources give other cards: the 3 of clubs in the
  English-language sources (pagat, en.wikipedia), the queen of hearts in fr.wikipedia; the owner's version is
  the king of hearts, and the others stay as options. The designated card only says **who** leads: that
  player opens with any legal play and is not obliged to include the card [O].
- **Rounds 2 and later**: titles come from the previous round; the exchange (3.2) happens; the first lead is
  set by `nextLead` (default: the Scum) [S, regles-du-jeu.net; pagat gives the President, so it is an
  option].

### 3.1.1 Equal play ("X or nothing"), the four of a rank, completion out of turn [O]

The owner's rules, all on by default with `equalPlay = on` (decided 2026-10-15):

- **Equal play.** After a play, the next player may play the **same count** of the **same rank** instead of
  a higher one. Example: A plays a 9, B plays a 9.
- **Binding.** The player after the equal play (C) is then bound: C **must play the same rank and count, or
  pass**; C cannot play higher. The binding falls on C, the player after the equal play, not on B.
- **The binding repeats** [O]: if C plays a 9, D must play a 9 or pass, and so on. The consecutive plays of
  one rank at the top of the trick form the **chain**.
- **End of the binding** on a pass is **open** (section 13, O1): either the first pass ends it (the next
  player then beats the 9s normally), or it holds until the trick closes. Working assumption until the owner
  answers [U]: the first pass ends it.
- A pass under the binding is an ordinary pass: it counts toward the trick end, and under
  `afterPass = reenter` the passer may play again later in the same trick.
- A cut (V4) is a higher play, so it cannot answer the binding [O, "C cannot play higher"].
- Nobody is skipped: the skip form of equal play (the next player loses their turn) is not the owner's rule
  and is out of scope (3.5).
- **The four closes the trick** [O]: when the four cards of a rank have been played in succession in the
  current trick (9, 9, 9, 9 as singles; a pair of 4s then the other pair of 4s), the trick closes at once and
  the player who played the completing card(s) leads the next trick. If that play emptied their hand, they go
  out and the lead passes to the next seat with cards (and, if they are the first player out, the
  first-finisher rule gives the same result). Completing the four does not trigger a revolution (V8), which
  needs four cards in one play [D].
- **Completion out of turn** [O]: while a chain of rank r is the top of the trick, the player who holds
  **all the missing cards** of r (4 minus those of the chain) may play them **at once, out of turn**,
  whatever the count of the chain: this closes the trick and that player leads the next one. Any seat may do
  it, including one who already played in the chain or one who passed (even under `afterPass = out`). With
  one 52-card deck there is exactly one such holder at a time, so two seats never compete for it; the player
  whose turn it is completes in turn, under the same rule (the count of the binding does not apply to a
  completion).
  - **Window** [O]: it opens as soon as the chain holds two plays (at least one equal play) and stays open
    until the next **play** is made on the trick, or the trick closes. Passes, even several around the
    table, do not close it, since the chain stays the top. Whether a single play of r (no equal play yet)
    already opens it is open (section 13, O4); working assumption: it does not.
  - **Optional** [O]: the holder may keep the cards; there is no penalty.
  - This is the only out-of-turn play of the game (3.4).

### 3.1.2 Equal-play and quad rules, worked examples

The test contract of the PR that ships equal play (plan, V1). Four players A, B, C, D in seat order,
`equalPlay = on`, `afterPass = reenter`, no other variant. "Holds" lists only the cards that matter.

1. **Singles chain closed in turn.** A leads 9♠; B plays 9♥ (equal: C is bound); C plays 9♦ (D is bound); D
   holds 9♣ and plays it: the four 9s are down, the trick closes (reason `QUAD`) and D leads the next trick.
   Had D held no 9, D could only pass; D could not play a 10.
2. **Pair chain.** A leads 4♠ 4♥; B plays 4♦ 4♣ (an equal pair): the four 4s are down, the trick closes at
   once and B leads. C and D never act in that trick.
3. **Interruption by B, who already played in the chain.** A leads 9♠; B plays 9♥ (C is bound); C plays 9♦
   (D is bound). Now B holds 9♣, the last 9: before D acts, B plays 9♣ out of turn; the trick closes and B
   leads. D, bound and holding no 9, could only have passed, which would not have closed the window
   (example 5); only a **play** on the trick closes it.
4. **Interruption by C, who passed.** A leads 9♠; B plays 9♥ (C is bound); C, holding 9♦ 9♣, passes (C may
   keep them: completion is optional). D passes too. While the 9s are still the top and before A's pass
   closes the trick, C plays 9♦ 9♣ out of turn (two cards on single 9s: a completion ignores the count); the
   trick closes and C leads.
5. **D passes, then B completes.** A leads 9♠; B plays 9♥; C plays 9♦; D, bound, passes. B holds 9♣ and plays
   it out of turn after D's pass: accepted, since a pass does not close the window. The trick closes and B
   leads.
6. **Chain broken by a pass, then a play** (working assumption of O1). A leads 9♠; B plays 9♥ (C is bound);
   C passes, which ends the binding; D plays a 10 (legal again: D beats the 9s normally). The window for the
   9s is now closed: the holder of 9♦ 9♣ can no longer complete. Under the other answer to O1 (binding held
   to the trick end), D could only play a 9 or pass, and the window would stay open.

Each example is one parameterised test row: the starting hands, the command sequence (seat, cards or pass,
in or out of turn), and the expected trick state after each command (top, binding, window open, trick
closed and its reason, next leader).

### 3.2 Card exchange (rounds 2 and later) [S, O]

French classic, the default (`exchange = two`) [S, fr.wikipedia, regles-du-jeu.net, 10h26.com], confirmed by
the owner:

- the Scum is forced to give their **2 best** cards to the President, who gives back **2 cards of their
  choice** (free choice);
- the Vice-Scum gives their **best** card to the Vice-President, who gives back **1 card of their choice**;
- Neutrals neither give nor receive.

With 3 players only the President / Scum pair exchanges. "Best" follows the ranking in force at the deal (a
revolution never carries over the deal unless `revolution = persistent`, see 3.3) [D].

Flow [D]: the server moves the low ranks' best cards at once when the round is dealt (they have no choice);
then each high rank picks the cards they give back, all pairs at the same time, in any order. Play starts
when every pending return is done. A high rank may give back a card they have just received [S, nothing
forbids it]. Each return is visible only to its two players; the others see the counts.

### 3.3 Variant catalogue

One row per variant. **Status**: *Offered* = a host option of this design (PR in the plan). The variants not
offered, and those dropped for now by the owner, are in 3.4 and 3.5. **Combined** = how often it is played
together with others in the sources. Defaults are those of the "Classic" preset (section 4.2).

| # | Variant | Names | Rule | Combined | Interactions / conflicts | Default | Status | Certainty |
|---|---------|-------|------|----------|--------------------------|---------|--------|-----------|
| V1 | Re-entry after passing | "passer plusieurs fois son tour"; Australian "no playing after passing" is the opposite | `reenter`: a player who passed may play again when the turn comes back in the same trick. `out`: a pass is final until the trick ends | always one of the two | With `out`, the trick ends as soon as nobody but the last player is still in it. A pass forced by the equal-play binding is an ordinary pass | `reenter` | Offered (`afterPass`), P1 | [O] decided 2026-10-15 (sources split: pagat and regles-du-jeu.net allow re-entry, regles.com memo says a passer "generally does not come back") |
| V2 | Equal play binds the next player ("X or nothing"), the four closes the trick, completion out of turn | "X ou rien", "carte égale ou rien", "égaler", "carré qui coupe" | A play of the same count and the **same** rank as the top is legal; the player after it must play that rank and count, or pass, and the binding repeats; the four cards of a rank played in succession close the trick, the completing player leads; the holder of the missing cards may complete out of turn (3.1.1) | very common in French play | End of the binding open (O1). A cut (V4) cannot answer the binding. Two players left: the binding falls back on the first player, no special case. A run chain (V12) never completes a four | `on` | Offered (`equalPlay = on|off`), V1 | [O] the owner's rules (decided 2026-10-15); fr.wikipedia describes "or nothing" as a sub-variant of "passage de tour"; the name "ou rien" and the completion out of turn are not found in any written source; the four completed by equal plays is Danish Røvhul (pagat) |
| V4 | The 2 cuts | "le 2 coupe", "2 utilisé comme atout", "single two wins", "bomb" | `single`: a single top card (2) may be played on any play (any count) and ends the trick at once; the player leads. `ladder`: k twos beat a set of k+1 cards of a lower rank (a 2 beats a pair, two 2s a triple, three 2s a quad), and end the trick | common | Under a revolution the cutting card is the top rank in force (the 3) [O]. A cut cannot answer the equal-play binding [O]; a chain of 2s can still be completed (it is a completion, not a cut). Leading with a 2 is allowed (it simply wins the trick at once) [U: en.wikipedia mentions groups that forbid leading a 2] | off | Offered (`twoCuts = off|single|ladder`), V2 | [S] single: fr.wikipedia, pagat, 10h26, officialgamerules; ladder: fr.wikipedia example "two 2s beat a triple of jacks", pagat Sofia/Toronto |
| V5 | A quad played at once clears the trick | "carré qui coupe", "bombe", Danish Røvhul "four of a rank clear the pile" | A single play of four cards of one rank ends the trick; that player leads. The four completed in succession is not this option: it is part of `equalPlay = on` (V2) | regional | With revolution (V8) both apply: order reversed, then a fresh lead. Whether the owner's rules also close the trick on a quad played at once is open (O5) | off | Offered (`quadClears`), V2 | [S] pagat (Røvhul), gathertogethergames |
| V7 | Forbidden finish | "interdiction de terminer par un 2", "on ne finit pas sur un 2", "cannot go out with the highest card" | A player whose last play contains a top-rank card (2; the 3 under a revolution) goes out but is placed **last**; among several such players the later one is lower [S, pagat Røvhul] | very common in French play | Under a revolution the forbidden card is the top rank in force (3) [O; S fr.wikipedia, pagat Daifugō]. A completion of 2s that empties the hand is a forbidden finish too. Alternative "the play is refused" not offered [U] | `lastPlace` | Offered (`forbiddenFinish = lastPlace|off`), V2 | [O] always on in the owner's rules, kept as a cheap host option; [S] regles-du-jeu.net, fr.wikipedia, 10h26, pagat Daifugō |
| V8 | Revolution | "révolution", "contre-révolution", "four of a kind inverts ranks", "kakumei" | A quad (four of a rank in one play) reverses the ranking (3 high, 2 low); a later quad reverses it back (counter-revolution). Duration: `round` (until the end of the round) [S, fr.wikipedia, pagat Daifugō], `persistent` (carries over to later rounds until a new quad) [S, fr.wikipedia variant] | common | Effect is **immediate**: the next player must beat the quad in the reversed order, i.e. with a lower quad [O; S, fr.wikipedia "must follow the newly established order"; pagat Daifugō says "from the next clearing"]. The cutting card and the forbidden finish follow the top rank in force (3). A four completed in succession is no revolution. Exchange "best cards" follow the order in force at the deal | off | Offered (`revolution = off|round|persistent`), V3 | [O] decided 2026-10-15 as specified |
| V10 | Aces high, 2 low | "Aces high", West Coast | Ranking A K Q ... 3 2 | rare | First lead `lowestClub` becomes the 2 of clubs; `kingOfHearts` and `queenOfHearts` are unaffected | `twoHigh` | Offered (`ranking = twoHigh|aceHigh`), V3 | [S] pagat, en.wikipedia |
| V11 | Compulsory beating | "obligation de jouer", "compulsory beating" | A player who has a legal play must play; pass only when none | rare | Counts cuts and equal plays as legal plays when those variants are on; under the equal-play binding only a matching play counts; a completion out of turn is never compulsory [D] | off | Offered (`mustPlay`), V2 | [S] pagat. "Obligation de jouer le plus petit" (must play the lowest legal play) has **no** written source [U]: not offered |
| V12 | Runs | "suites", "sequences", "kaidan" | A run is 3 or more consecutive ranks, all of one suit (`sameSuit`, Daifugō). A run is beaten only by a run of the same length with a higher top card. 2 may end a run (K-A-2), no wrap (A-2-3 is not a run) | Daifugō | Equal play (V2) on a run: same length and same top, and the binding asks for such a run; no four is completed by runs. Revolution reverses run order | off | Offered (`runs = off|sameSuit`), V4 | [S] pagat Daifugō |
| V13 | Suit lock | "shibari", "following suit", "même couleur" | When two consecutive plays in a trick have the same suits, every later play of the trick must use those suits | Daifugō | With runs `sameSuit`, a run locks when beaten by a run of the same suit. A completion out of turn ignores the lock (it holds the only missing cards) [D] | off | Offered (`suitLock`), V4 | [S] pagat Daifugō; the partial lock "kata-shibari" is not offered |
| V14 | Exchange amount | "deux cartes / une carte", "Butthead version", Hűbéres 3/2/1 | `none`; `one` (top pair exchanges one card); `two` (default, 3.2: 2 cards for the President pair, 1 for the Vice pair); `scaled` (with p title pairs, the i-th pair from the top exchanges p - i + 1 cards: 6 players 3/2/1) | always | `scaled` with 3 players is `one` | `two` | Offered (`exchange`), P2 | [O] default confirmed; [S] pagat, fr.wikipedia |
| V15 | What the high rank gives back | "le Président donne ses plus faibles", "returns any unwanted card" | `free`: cards of their choice [S, O]; `lowest`: automatically their lowest cards, even out of a pair [S, fr.wikipedia variant] | always one | `lowest` makes the exchange instant (no exchange phase wait) | `free` | Offered (`giveBack`), P2 | [O] default confirmed |
| V16 | First lead of round 1 | "Roi de cœur", "3 de trèfle", "Dame de cœur", "joueur après le donneur" | `kingOfHearts` (the owner's rule); `lowestClub` (holder of the lowest club: 3♣, or 2♣ with aces high); `queenOfHearts` (fr.wikipedia variant); `random` (a seat drawn at random, replacing "after the dealer"). The card only designates the leader, who opens with any legal play [O] | always one | - | `kingOfHearts` | Offered (`firstLead`), P1 (`kingOfHearts`: P3) | [O] king of hearts; [S] 3♣: pagat, en.wikipedia; queen of hearts: fr.wikipedia |
| V17 | First lead of rounds 2+ | "le Trou du cul commence", "the President leads" | `scum` or `president` | always one | - | `scum` | Offered (`nextLead`), P2 | [S] both, sources split |
| V18 | Points and match length | "points", "partie en N donnes" | Scoring by place: `rank` (place p of n scores n - p, fr.wikipedia), `titles` (President 2, Vice 1, others 0, pagat), `zeroSum` (+k for the k-th title pair from the top, -k from the bottom: 5 players 2 1 0 -1 -2, pagat). Length: 3, 5 or 10 rounds, first to 11 or 21 points, or `endless` (no end: rounds chain, the scoreboard keeps accumulating, players leave whenever they like) | always | `zeroSum` with a points target may never end: the match then ends after 10 rounds anyway [D]. `endless` reaches `FINISHED` only when a single player is left (section 8) | `rank`, `rounds-5` (to confirm against `endless`, section 13 O2) | Offered (`scoring`, `length`), P2 (`endless`: P3) | [O] points by place, endless added; [S] pagat, fr.wikipedia, regles-du-jeu.net (4/8/12 deals) |

Row numbers are kept from the first version of this spec: V3 ("or nothing") is merged into V2, and V6, V9,
V19, V20, V21 moved to 3.4 or 3.5. Row ids name catalogue rows; the PR in the Status column (V1 to V4) is a
plan PR, re-sliced on 2026-10-15.

### 3.4 Variants not offered

| Variant | Source | Why not |
|---------|--------|---------|
| Jokers (former V9: `high`, 2 jokers above the 2, a single joker beats any play and ends the trick [regles-du-jeu.net Québec]; `wild`, a joker joins a set and takes its rank [pagat, Daifugō]; jokers on top under a revolution) | pagat, en.wikipedia, regles-du-jeu.net | Owner, 2026-10-15: the game is played with the 52-card deck only, never with jokers |
| Two players | - | Owner, 2026-10-15: 3 to 8 players only; with two there are no title pairs to speak of |
| Out-of-turn plays other than the completion of the four (a 2 out of turn; completing a quad that is not a chain at the top of the trick) | en.wikipedia, gathertogethergames | Turns the game into a race between players. The completion of the four (3.1.1) is the only out-of-turn play kept: a single seat can ever make it |
| Single-lap tricks (each player gets one chance) | pagat | Close to `afterPass = out`; one more rule for little gain |
| Larger sets beat smaller ones (by rank or regardless of rank) | pagat | Rare; conflicts with the same-count rule every other variant assumes |
| Skip as many players as cards played | pagat | Rare, and the owner's equal play skips nobody |
| Colour reversing, suit ranking, following suit for singles | pagat | Rare; suit lock (V13) covers the suit family |
| Transparent threes, black threes or jack of diamonds high, 3♠ beats a joker | pagat | Rare, single sources |
| 10 discards, 7 passes, 11 back, 5 skip | pagat Daifugō, Japanese apps | Japanese local rules, no French or English President source |
| 32-card deck (7 high) for 3-4 players | fr.wikipedia | Rare; a `deck` option can come later without model change |
| Extra cards set aside at the deal | fr.wikipedia | The default deal gives every card [S, pagat] |
| Collective best cards (a pair of kings over an ace and a king), negotiated exchange, merchants, auction | fr.wikipedia, pagat | Needs negotiation or interpretation; no server rule |
| Hand of Collusion, Klootzakken, drinking and seating penalties | pagat, fr.wikipedia | Different games or social rules |
| "Must play the lowest legal play" | none found | No written source [U] |
| Revolution triggered by a 5-card run | pagat Daifugō | Only with runs, rare; can be added to V12 later |

### 3.5 Out of scope for now

Dropped by the owner on 2026-10-15 (reason for each: **owner: not now**). The research is kept so that any of
them can come back as an option later without new research; none has a key, a PR or a test.

| Former row | Variant | Rule (as researched) | Certainty (as researched) |
|------------|---------|----------------------|---------------------------|
| V2 (first version) | Equal play **skips** the next player ("égaler et faire sauter", "ta gueule", "passage de tour", "Chump") | A play of the same count and rank as the top is legal; the next player in turn is skipped (counted as a pass); with two players left, the skip gives the turn back to the player, who wins the trick and leads [S, pagat] | [S] regles-du-jeu.net, fr.wikipedia, pagat, 10h26. Not the owner's equal play (3.1.1), which binds the next player instead |
| V6 | Eight stops ("8 coupe", "8 giri", "eight cut") | Any play containing an 8 ends the trick; the player leads; the forbidden finish (V7) then also covers 8s [S, pagat Daifugō] | [U] well sourced for Daifugō (pagat), no written French source |
| V8 value `trick` | Revolution until the end of the trick | The reversal ends with the trick | [U] fr.wikipedia mentions it |
| V12 value `anySuit` | Runs with free suits (Zheng Shangyou family) | A run of 3+ consecutive ranks of any suits | [U] for President |
| V19 | Two decks ("plusieurs jeux", "double deck"), 9 or 10 players | 104 cards for many players; identical cards are interchangeable (hands as multisets); sets up to 8 cards; a quad is "four or more"; the first lead goes to the first holder of the card after the dealer; `maxPlayers` 8 → 10 | [S] that it exists (en.wikipedia "9+ players", pagat); no written rule for its details [U]. Also excluded by the owner's 3-to-8-player rule |
| V20 | Putsch ("le putsch") | Before the exchange, the Scum may declare a putsch: no exchange this round. If the Scum finishes first, they are President with one bonus point; otherwise the round stops as soon as someone else goes out first, every player keeps their previous title, the putschist loses one point, gives their 4 best cards to the President next round and cannot putsch then | [U] single source (fr.wikipedia, citation needed) |
| V21 | The President falls ("miyako-ochi", "falling from the capital") | If the reigning President does not finish first, they are placed last at once (out of the round) | [S] pagat Daifugō; not French [U] |

## 4. Options (host, game-level)

### 4.1 Option keys

All options are `OptionType.CHOICE` (the only built type) and go through the existing lobby mechanism:
`GameFactory.optionDescriptors()` declares them, the host edits them before Start, `SessionService.updateOptions`
merges a change map and calls `PresidentFactory.validate`, which parses them strictly into a
`PresidentOptions` record (pattern of `BullshitOptions.parseStrict`). Label keys are
`options.president.<key>`. Every combination is legal: a combination where one rule makes another moot is
documented, never refused, so the host can set options in any order [D, O].

| Key | Values (first = default) | PR |
|-----|--------------------------|----|
| `afterPass` | `reenter`, `out` | P1 / P3 |
| `firstLead` | `kingOfHearts`, `lowestClub`, `queenOfHearts`, `random` | P1 / P3 (`kingOfHearts` added in P3) |
| `exchange` | `two`, `one`, `scaled`, `none` | P2 / P3 |
| `giveBack` | `free`, `lowest` | P2 / P3 |
| `nextLead` | `scum`, `president` | P2 / P3 |
| `scoring` | `rank`, `titles`, `zeroSum` | P2 / P3 |
| `length` | `rounds-5`, `rounds-3`, `rounds-10`, `points-11`, `points-21`, `endless` | P2 / P3 (`endless` added in P3) |
| `equalPlay` | `on`, `off` (on: binding, four closes the trick, completion out of turn) | V1 |
| `twoCuts` | `off`, `single`, `ladder` | V2 |
| `quadClears` | `off`, `on` (a quad played at once only) | V2 |
| `forbiddenFinish` | `lastPlace`, `off` | V2 |
| `mustPlay` | `off`, `on` | V2 |
| `revolution` | `off`, `round`, `persistent` | V3 |
| `ranking` | `twoHigh`, `aceHigh` | V3 |
| `runs` | `off`, `sameSuit` | V4 |
| `suitLock` | `off`, `on` | V4 |

Sixteen keys. Until a variant's PR ships, its key does not exist (the server refuses it as unknown), and the
domain behaves as the "off" value. Defaults are the Classic preset, so a room created without touching
anything plays the owner's rules.

### 4.2 Presets (frontend convenience) [D]

A preset is a named bundle of values sent as **one** change map (`updateOptions` already merges several keys
in one call); the server knows no preset. The lobby shows which preset matches the current values, or
"Custom".

| Preset | Values (only those that differ from the defaults) |
|--------|---------------------------------------------------|
| Classic (the owner's rules) | defaults: re-entry, equal play binds the next player (the four closes the trick, completion out of turn), forbidden finish on a 2, exchange 2/1 with a free return, king of hearts leads round 1, Scum leads rounds 2+, rank points, 5 rounds |
| Pagat basic | `firstLead=lowestClub`, `equalPlay=off`, `forbiddenFinish=off`, `exchange=one`, `nextLead=president`, `scoring=titles`, `length=points-11` |
| Strict | `afterPass=out`, `equalPlay=off`, `forbiddenFinish=off` |
| Party | `twoCuts=single`, `revolution=round` |
| Daifugō-like | `equalPlay=off`, `revolution=round`, `runs=sameSuit`, `suitLock=on` (without the jokers, eight stops and fall of the real Daifugō, out of scope) |

A preset that names a key not yet shipped is hidden until it ships.

### 4.3 Seat options

Only for bots (B2): `level` = `easy|medium|hard`, default `medium`, label `options.president.level`, the
Kobo pattern.

## 5. Players, deck, deal

- `minPlayers() = 3`, `maxPlayers() = 8` [O]. No 2-player mode and no 9-or-10-player mode (3.4, 3.5).
- Deck: `CardsService.createDeck(DeckType.FRENCH, ...)`, one 52-card deck, never `FRENCH_WITH_JOKERS` [O].
  Every card is unique; hands stay plain `List<Card>` [D].
- **Determinism seam** (Kobo pattern, `add-a-game.md` §5): a `DeckSource` port in `president/domain`,
  `RandomDeckSource` by default, `ScriptedDeckSource` and `PresidentTestDealController`
  (`PUT`/`DELETE /api/test/president/{gameId}/deal`) under the Spring `test` profile only. The P1 port takes a
  `DeckSpec(decks, jokers)`; with one deck and no jokers it carries nothing, so P3 removes it (or reduces it
  to the standard deck) [D].

## 6. Domain model (`president/domain`)

One aggregate root, `President implements Game`, Kobo style: every mutating method is `synchronized`,
validates everything first, then mutates, bumps `version()` and returns a small outcome; a refused command
never mutates. The aggregate owns the whole match (rounds, titles, points), because the exchange, the
end-of-match condition, forfeit and rematch span rounds. Package-private constructor with explicit hands for
tests (`PresidentBuilder`).

Sub-packages (each with the exceptions it protects):

| Package | Content |
|---------|---------|
| `rules` | `PresidentOptions` (typed options, `from` lenient / `parseStrict` strict, `toGameOptions`), one enum per option (`AfterPass`, `EqualPlay`, `TwoCuts`, `ForbiddenFinish`, `Revolution`, `Ranking`, `Runs`, `ExchangeAmount`, `GiveBack`, `FirstLead`, `NextLead`, `Scoring`, `MatchLength`), `PresidentException` |
| `card` | `RankOrder` (strength of a rank under `ranking` and the revolution state), `HandSort` |
| `play` | `Play` (value object: cards, kind `SET` or `RUN`, effective rank or top, count, suits), `PlayParser` (cards → `Play` or `IllegalPlayException`), `BeatRule` (does a play beat the top, is it equal, is it a cut), `LegalPlays` (every legal play of a hand against the trick state, binding included: used by the DTO hints, `mustPlay` and the bots), `PlayEffects` (clears the trick, reverses, binds the next player), `Completion` (the missing cards of the chain's rank and who holds them; pure) |
| `trick` | `Trick` (leader, plays, last player, seats that passed since the last play, seats out of the trick under `afterPass = out`, constraint `FREE`, `BEAT`, `MATCH_OR_PASS` with its rank and count, the chain: rank and cards of that rank played in succession at the top, locked suits) |
| `round` | `Round` (hands, finishing order, forbidden finishers, phase, current seat, revolution state, dealer), `Phase` |
| `exchange` | `Title`, `Titles.forPlaces(n)`, `ExchangePlan` (pairs and amounts from `exchange` and `n`), `PendingReturn` |
| `scoring` | `MatchScoring` (pure: points of a round from the finishing order), `RoundResult`, `Standings` |
| `event` | sealed `PresidentEvent`, `PresidentResult` (winners, reason `POINTS` or `FORFEIT`) |

### 6.1 Phases

| Phase | Meaning | Leaves with |
|-------|---------|-------------|
| `EXCHANGE` | round ≥ 2 with an exchange; low ranks' best cards already moved; high ranks choose what to give back | the last `giveBack` (instant when `giveBack = lowest`) |
| `PLAYING` | tricks | the last but one player goes out |
| `ROUND_OVER` | round scored and shown; barrier | `ready` from every remaining player, or `FINISHED` |
| `FINISHED` | the match length is reached (never under `endless`), or one player is left | Play again (outside the aggregate) |

### 6.2 Commands and queries

Commands: `play(seat, List<Card>)`, `pass(seat)`, `giveBack(seat, List<Card>)`, `ready(seat)`,
`forfeit(PlayerId)` (the `Game` contract). `play` is accepted from a seat that is **not** the current one
only when it is a completion (3.1.1): the cards are exactly all the missing cards of the chain's rank and
the window is open. Any other out-of-turn `play` is refused with `NotPlayersTurnException`.

Queries: `phase()`, `round()`, `version()`, `currentSeat()`, `hand(seat)`, `handSizes()`, `trick()` (read
model), `revolution()`, `finishingOrder()`, `titles()` (titles in force this round, from the previous one),
`exchange()` (pairs, amounts, pending returns, and per seat what they gave and received), `standings()`,
`lastRoundResult()`, `legalPlays(seat)`, `completion(seat)` (the cards that complete the four when that seat
holds them and the window is open, in or out of turn; empty otherwise), `availableActions(seat)` (`PLAY`,
`PASS`, `COMPLETE`, `GIVE_BACK`, `READY`; `COMPLETE` may be the only action of a seat whose turn it is not),
`options()`, `result()`.

Rules enforced in the domain only: turn ownership, phase, card ownership, combination validity, beating,
equal play and its binding, the four completed in succession and its completion out of turn (window included), cuts and clears, revolution, forbidden finish, compulsory play, the trick closing
on the first finisher, the exchange (who, how many, best cards), first leads, titles, scoring, end of match.

### 6.3 Trick algorithm [D]

- Eligible seats: seats with cards, not forfeited and, under `afterPass = out`, not out of this trick.
- `play`: if the seat is not the current one, the play must be a completion (below). Otherwise the cards
  must be in the hand; `PlayParser` builds the `Play`; a completion is accepted whatever the constraint;
  else `BeatRule` checks it against the constraint (`FREE` when leading; `BEAT` same count and higher rank,
  an equal play under `equalPlay = on`, or a cut; `MATCH_OR_PASS` same rank and count only, no cut). Then, in
  this order: remove the cards; record the play and update the chain (same rank as the top: extend it; else
  start a new one); apply the revolution (quad in one play); detect a going-out (place, forbidden finish);
  if the player took 1st place, the trick ends at once (reason `FIRST_OUT`) and the next eligible seat after
  them leads [O]; else if the chain now holds the four cards of its rank, the trick ends (reason `QUAD`) and
  the player leads (or the next eligible seat when they just went out) [O]; else apply the clear effects
  (cut, quad at once): likewise; otherwise set the constraint of the next seat (`MATCH_OR_PASS` after an
  equal play, `BEAT` otherwise) and move to the next eligible seat. The "responded since the last play" set
  is cleared on every play.
- **Completion** (in or out of turn) [O]: legal when `equalPlay = on`, the trick is not over, its chain
  holds at least two plays (working assumption of O4) and the cards played are exactly all the missing
  cards of the chain's rank, held by that seat. The seat need not be eligible (a passer, even under
  `afterPass = out`, may complete). After the play the turn order is not resumed: the trick closes as above
  and the completing player leads.
- **Ordering against the next play**: the aggregate is `synchronized`, so the completion and the current
  player's command are processed one after the other, in arrival order. Only a **play** closes the window
  (it changes the top, so the chain is no longer the top); a pass leaves it open. The guard is therefore the
  completion condition itself, re-checked when the command is processed, not the version: passes bump
  `version()` too, and a completion built on a state that has since seen only passes must be accepted. A
  completion processed after a play is refused (`CompletionClosedException`, answered to the sender alone).
  The current player has nothing to lose: they never hold the missing cards, else they would complete in
  turn.
- `pass`: allowed for the current seat unless the trick is `FREE` (a leader must lead) or `mustPlay` and a
  legal play exists (a completion is never compulsory). The seat joins "responded" (and "out of the trick"
  under `afterPass = out`); under `MATCH_OR_PASS` the binding ends after this pass (working assumption of
  O1), and the next seat must beat the top normally. The chain and its completion window are unchanged.
- Trick end: when every eligible seat except the last player is in "responded". The winner is the last
  player if they still have cards, else the next eligible seat after them. Event `TrickWon(reason)`, reasons
  `ALL_PASSED`, `FIRST_OUT`, `CUT`, `QUAD` (the four completed in succession, in or out of turn, or a quad at
  once under `quadClears`). A trick that closes on `ALL_PASSED` also closes the completion window.
- Round end: one seat with cards left. That seat takes the last free place, above the forbidden finishers,
  who fill the bottom in reverse order of going out [S, pagat Røvhul].

### 6.4 Events (domain outcomes)

`RoundDealt(round, dealer, handSizes)`, `BestCardsTaken(from, to, count)` (cards only for the pair),
`CardsGivenBack(from, to, count)`, `ExchangeDone`, `Played(seat, cards, kind, outOfTurn)`, `Passed(seat)`,
`MatchRequired(seat, rank, count)` (the equal-play binding), `RevolutionToggled(active)`,
`TrickWon(seat, reason)`, `PlayerOut(seat, place, forbidden)`, `RoundScored(RoundResult)`, `Ready(seat)`,
`MatchFinished(PresidentResult)`, `PlayerForfeited(seat)`.

### 6.5 Exceptions

All extend a checked `PresidentException`: `FinishedGameException`, `WrongPhaseException`,
`NotPlayersTurnException`, `CardsNotInHandException`, `IllegalPlayException` (not one rank, not a run),
`PlayDoesNotBeatException` (including a higher play under the binding), `MustLeadException`,
`MustPlayException`, `CompletionClosedException` (a completion out of turn that arrives after a play, or with
the wrong cards), `WrongGiveBackException` (count, or not your turn to return), `AlreadyReadyException`.

## 7. Presentation

### 7.1 WebSocket (`/app/president/...`, token-authenticated through `SeatCommands`)

| Destination | Payload | Effect |
|-------------|---------|--------|
| `/president/start` | `GameActionPayload` | host starts (game-type check first: "This room is not a President game") |
| `/president/play` | `PresidentCardsPayload(gameId, token, cards: CardDto[])` | play, in turn or as a completion out of turn (same destination; the domain tells them apart) |
| `/president/pass` | `GameActionPayload` | pass |
| `/president/giveBack` | `PresidentCardsPayload` | exchange return |
| `/president/ready` | `GameActionPayload` | round barrier |
| `/presence`, `/forfeit` | existing | unchanged |

`PresidentEventType`: `START`, `ROUND_START`, `EXCHANGE`, `PLAY`, `COMPLETION` (a play out of turn), `PASS`,
`MATCH_REQUIRED`, `TRICK_WON`,
`REVOLUTION`, `PLAYER_OUT`, `ROUND_END`, `READY`, `GAME_OVER`. One `SuccessResponse` per human seat through a
`SeatStateBroadcaster<President, PresidentDto>`; refusals answered to the sender alone with
`ErrorResponse(type, message, state)`.

### 7.2 REST (`/api/president`) over `SeatGameRest`

`GET /game/{id}?token=`, `POST /game/{id}/join`, `POST /game/{id}/play-again`.

### 7.3 Per-seat DTO (`PresidentDto.forViewer(game, viewer, names, bots)`, the only projection)

Public: `version`, `round`, `rounds` (length, none under `endless`), `phase`, `currentSeat`, `revolution`,
`options` (the values in force, for the rules panel and table hints), per seat `{seat, name, bot?, handSize,
title, place, out, forbiddenFinish, passed, points, ready}`, the trick `{leader, plays: [{seat, cards}], top,
constraint, requiredRank, requiredCount, chain: {rank, cards}, lockedSuits, lastPlayer}`, exchange counts per pair, `lastRound`
(finishing order, titles, points), `standings`, `outcome`.

Private (viewer only): `hand` (sorted by strength in force), `received` / `given` (the exchange cards of the
viewer's pair), `toGiveBack` (count, while pending), `legalPlays` (patterns `{kind, rank or top, count,
suits?}` for the viewer's turn, from the domain `LegalPlays`, so the client never computes a rule; the client
only matches a selection to a pattern), `completion` (the exact cards that complete the four, from the domain
`completion(seat)`, present only in the holder's DTO while the window is open, whether or not it is their
turn: it appears once the chain holds two plays, survives every pass, and disappears on the next play or
when the trick closes), `availableActions` (with `COMPLETE` while `completion` is present).

Since only the holder's DTO carries `completion`, the others never learn who holds the missing cards.

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
- **Forfeit** (timer or `/forfeit`) [O, confirmed]: the player leaves the match. During `EXCHANGE` a pending
  return is first completed with their lowest cards; then their hand is removed from play. If they were the
  current player the turn moves on; if their play is the trick top, the trick goes on and its winner is
  computed as for a player who went out. They take no place in the round; titles are computed among the
  others. Their points are dropped from the standings (Kobo). Down to two players the match goes on; one
  player left: `FINISHED`, reason `FORFEIT` [D].
- **Endless match**: leaving is a forfeit; the standings stay visible throughout, and the match ends only
  when a single player is left (reason `FORFEIT`, that player wins) [D].
- **Rematch**: as Kobo and Bullshit: `FINISHED`, then `POST /api/president/game/{id}/play-again`; a new match
  starts at round 1 with no titles; options and bots are kept by the session core.

## 9. Frontend (summary; screens decided through the canvas, section 11)

Files per `game-conventions.md` §4: `model/president/` (`PresidentState.ts`, `selection.ts` matching a
selection to a `legalPlays` pattern, `titles.ts`), `application/PresidentSession.ts` (`PRESIDENT_SESSION_CONFIG`,
commands `play`, `pass`, `giveBack`, `ready`), `state/President.store.ts`,
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
  `Play(cards)`, `Complete(cards)`, `Pass`, `GiveBack(cards)`, `Ready`), `HeuristicPresidentBot`,
  `PresidentBotLevel` / `PresidentBotTuning`.
- **Candidates** always come from the domain's `LegalPlays`, so a new variant can never make a bot play an
  illegal card; the heuristic only ranks them. Under the equal-play binding the candidates are the matching
  sets and pass only. The completion comes from the domain's `completion(seat)` too.
- **Heuristic** (all levels, tuned constants):
  - lead the lowest set that does not break a bigger set; keep control cards (top ranks, cutting 2s) for the
    end;
  - follow with the lowest beating play that does not break a set; pass rather than break a pair unless the
    hand is short; play an equal when the binding lands on a dangerous next player (fewest cards) or one
    likely short of that rank (card counting); under a binding, match only when it does not break a set or
    the hand is short;
  - completion: take it (it closes the trick and gives the lead), in or out of turn; `hard` may keep the
    cards when they are its last ones and would make a forbidden finish (2s), or when holding them back
    protects a better end game (completion is optional [O]); `easy` takes it only half of the time;
  - end-game planner (`hard`): count the "tricks to go out" of the hand and make sure the last play is not a
    forbidden finish (keep a low card for last); going out first also closes the trick, so the planner
    values it as a free lead for the next seat;
  - revolution: play a quad when the hand's average strength is low (it helps), keep it otherwise;
  - exchange: give back the lowest singletons, never break a pair;
  - card counting (`medium`: ranks of the top cards; `hard`: every card) to know when a play is a sure
    winner.
- **Levels**: `easy` = a random legal play 30% of the time and a random pass when legal 20%; `medium` =
  heuristic with partial counting; `hard` = full counting and the planner.
- **Coordinator** (`presentation/bot/PresidentBotCoordinator`, Morris pattern: a `GameStateListener` and
  `GameEvictionListener`): after each broadcast, one task per bot seat that must act (its turn, its return,
  the barrier, or a completion it holds while it is not its turn), after the `ThinkingDelay` (`president.bots.delay-min-ms` / `delay-max-ms`, 700 to 1300 ms, 0
  under `test`), keyed on `version()` and dropped when stale; a failing strategy falls back to pass, else the
  lowest legal play, else the lowest cards to give back, else ready. A completion task that the domain
  refuses as too late (a play came first) is dropped silently. There is no race between players (one holder
  at a time), so no reaction delay: a completion uses the thinking delay like any other decision, and since
  passes keep the window open, only a human's quick play can beat it.

## 11. UI decisions to take (Claude Design canvas, owner's choice before the frontend spec)

Per the global rule, each is settled on a canvas with 2-3 options on real President data (a 6-player table
at round 3 with titles, a revolution active, an exchange pending). This spec does not choose.

1. **Table layout for 3 to 8 players.** (A) Ring of opponents around the central trick, my hand fanned at
   the bottom (Kobo/Bullshit ring); (B) a compact list of opponent rows (name, title badge, card count,
   passed / out) above the trick, scaling to 8 on a phone; (C) an arc of opponents across the top only, the
   trick in the middle, my hand at the bottom.
2. **Selecting and playing cards.** (A) Tap cards to select, Play enabled only when the selection matches a
   `legalPlays` pattern; (B) the hand grouped in rank stacks, tap a stack then a count chip (1..n);
   (C) a bar of suggested plays ("Pair of 9s", "Single 2: cut") above the hand, plus free selection. In every
   option, a visible "Complete the four" button appears for the holder while `completion` is present, even
   outside their turn, and plays exactly those cards.
3. **Lobby options (sixteen keys).** (A) Preset cards (Classic, Pagat, Strict, Party, Daifugō) with a
   "Customise" disclosure grouping every option; (B) all options in four accordions (Trick, Special cards,
   Exchange, Match) with the generic radios; (C) preset first, then a list of rule chips, each opening a
   small sheet to change it.
4. **Exchange step.** (A) Inline at the table: a banner, my received cards highlighted in my hand, select k
   cards and Give; (B) a modal sheet with "You received" and "Choose k cards to give back", with a
   "Suggest" button; (C) a trade view showing both partners of each pair (counts only for others).
5. **Round end and standings.** (A) An overlay sheet with the finishing order, title changes (arrows), round
   and total points, ready checks; (B) a persistent side scoreboard plus a short toast at round end (fits an
   `endless` match); (C) a full-screen podium (President to Scum) then the next-round button.
6. **Trick and rule state on the table.** (A) Only the top play, with a small stack and a log line;
   (B) the whole trick fanned with the seat name on each play; (C) top play plus status chips (Revolution,
   "9 or nothing", Suits locked, the chain count "9s: 3 of 4") next to the pile.

## 12. Sources

- The owner's house rules, decided on 2026-10-15 (section 13): king of hearts lead, equal play binding the
  next player, the four closing the trick and its completion out of turn, a fresh trick after the first player goes out, no jokers, 3 to 8 players.
- fr.wikipedia, "Trou du cul (jeu)": https://fr.wikipedia.org/wiki/Trou_du_cul_(jeu) (ranking, titles,
  exchange 2/1 and its variants, revolution and its duration, putsch, passage de tour, 2 as trump,
  forbidden finish on a 2, rank points, queen of hearts lead; carries citation-needed banners).
- en.wikipedia, "President (card game)": https://en.wikipedia.org/wiki/President_(card_game) (roles, deal,
  exchange, re-entry varies, bombs, no lead with a 2 in some groups, 3♣ start, 9+ players with two decks).
- pagat.com, "President": https://www.pagat.com/climbing/president.html (basic game with re-entry, all
  playing-procedure, card, suit and scoring variations, exchange versions, Røvhul, 3♣ start).
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
le plus petit", the king of hearts lead. They are the owner's rules [O], marked [U], or not offered.

## 13. Decisions to confirm

### 13.1 Decided by the owner on 2026-10-15

1. **Q1 — Identity and titles.** Decided 2026-10-15: id `president`, English UI titles President /
   Vice-President / Neutral / Vice-Scum / Scum; the French names (including "Trou du cul") only in the rules
   text.
2. **Q2 — Players and deck.** Decided 2026-10-15: 3 to 8 players only, one 52-card deck, no jokers; no
   2-player mode, no two-deck 9-or-10-player mode (3.4, 3.5).
3. **Q3 — Classic defaults.** Decided 2026-10-15: re-entry after a pass (`afterPass = reenter`); equal play
   on, binding the next player to match or pass (3.1.1); forbidden finish on a 2 always on (`lastPlace`, kept
   as a host option); exchange 2/1, the Scum and Vice-Scum forced to give their best cards, the high ranks
   giving back any cards they choose; the Scum leads rounds 2+; the king of hearts holder leads round 1;
   points by place (`rank`). Everything else off. The default match length is the open point O2.
4. **Q4 — Exchange flow.** Decided 2026-10-15: the server takes the low ranks' best cards automatically at the
   deal; the high ranks choose their returns freely, all pairs at once, with no timeout (the disconnect
   forfeit covers an absent player); a "Suggest" helper in the UI, not a server rule.
5. **Q5 — Delivery order.** Base game playable end to end first (P1-P3, F1-F4), then bots (B1-B3) on the base
   rules with candidates from the domain's `LegalPlays`, then the variant groups V1-V4 (each full stack: rule,
   option, texts, table indicator, bot tweak), then the e2e matrix. Re-sliced on 2026-10-15: V1 is the
   owner's equal play (binding, four, completion out of turn), V2 the other trick variants, V3 revolution and
   aces high, V4 runs and suit lock; the former V4 (two decks, putsch, fall) is dropped (3.5).
6. **First lead.** Decided 2026-10-15: `firstLead = kingOfHearts` by default (`lowestClub`, `queenOfHearts`,
   `random` kept as options); the holder is **not** obliged to open with the king of hearts: any legal
   opening play [O]. P1 already does not require the card.
7. **Trick after a player goes out.** Decided 2026-10-15 (option C of the question asked): when the first
   player to go out finishes, the trick closes at once and the next seat with cards leads a fresh trick; when
   a later player goes out, the trick continues and the others may still beat that last play. P1 (merged,
   #219) implements the "continue" behaviour for every finisher, so P2 adds "the first finisher closes the
   trick" (section 6.3, reason `FIRST_OUT`) with a parameterised test.
8. **Revolution.** Decided 2026-10-15, as specified: immediate (the next player must answer in the reversed
   order), lasts to the end of the round (`round`), `persistent` as the other value; the cutting card and the
   forbidden finish follow the top rank in force (3 under a revolution). No jokers.
9. **Forbidden finishers** go out and fill the bottom places, the later one lower.
10. **Barrier.** Decided 2026-10-15: a `ready` barrier between rounds, no timeout, as in Kobo.
11. **Forfeit.** Decided 2026-10-15: the match continues with two players; one left ends it with reason
    `FORFEIT`; points of a forfeiter are dropped.
12. **Combinations.** Decided 2026-10-15: every option combination is accepted; moot combinations are
    documented, not refused.
13. **`afterPass = out`.** Decided 2026-10-15: the reading "the trick ends when every eligible player except
    the last one has passed" is correct.
14. **Equal play.** Decided 2026-10-15: `equalPlay = on` by default with the owner's meaning (3.1.1): an
    equal play binds the **next** player to match or pass, never higher; the binding **repeats** after
    every matching play; nobody is skipped. This one rule replaces the former V2 (skip) and V3 ("or nothing")
    rows. Worked examples: 3.1.2.
15. **The four closes the trick.** Decided 2026-10-15: the four cards of a rank played in succession in the
    trick (singles, or a pair then a pair) close it at once; the completing player leads (or, if they just
    went out, the next seat with cards). It is part of `equalPlay = on`; `quadClears` keeps only the quad
    played at once (V5).
16. **Completion out of turn.** Decided 2026-10-15: the holder of all the missing cards of the chain's rank
    may play them at once, out of turn, including a seat that already played in the chain or passed; it
    closes the trick and they lead. The window stays open through any number of passes and closes with the
    next **play** on the trick (or the trick's end). Completing is optional, with no penalty. It is the only
    out-of-turn play of the game.
17. **Dropped for now.** Decided 2026-10-15 (owner: not now): the skip form of equal play, `eightClears`,
    `revolution = trick`, `runs = anySuit`, two decks, putsch, president fall (3.5). Jokers are excluded for
    good (3.4).

### 13.2 Still open

- **O1 — When the equal-play binding ends.** The binding repeats (decided). Scenario: A plays a 9, B plays a
  9, so C must play a 9 or pass; C passes. Is D (a) free again to beat the 9s with a higher single, the
  first pass having ended the binding, or (b) still bound to play a 9 or pass, the binding lasting until the
  trick closes? Working assumption until answered [U]: (a). Example 6 of 3.1.2 shows both outcomes.
- **O2 — Default match length.** Options: fixed `rounds-5` (current default) or `endless`. Recommendation:
  keep `rounds-5` as the default and offer `endless` as an option. A fixed length gives every match a winner
  by points, the end screen and Play again, which all other games share; an endless match only ends when the
  players leave (forfeits), so the last player standing "wins" by forfeit rather than by points. If the
  owner's table usually plays without an end, `endless` as the default only changes the value order in
  `length`.
- **O3 — Forbidden finisher going out first.** A player whose first-out play is a forbidden finish does not
  take 1st place (they are placed last); this spec lets the trick continue in that case, as for a later
  finisher [D]. Alternative: any first player out closes the trick, whatever their place.
- **O4 — When the completion window opens.** Scenario: A leads a single 9 and C holds the three other 9s.
  May C complete at once (three 9s out of turn on a single lead), or only once the chain holds an equal play
  (A 9, then B 9)? Working assumption [U]: only once the chain holds two plays, as in all the owner's
  examples ("A, B and C each played a 9").
- **O5 — A quad played at once.** Scenario: A leads four 7s together. Does that close the trick in the
  owner's rules (as the four completed in succession does), or must the others try to beat it with a higher
  quad? Today it is the `quadClears` option, off by default; the answer only changes that default.
