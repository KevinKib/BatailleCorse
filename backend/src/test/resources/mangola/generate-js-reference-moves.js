// Generates js-reference-moves.txt from the owner's reference implementation (KevinKib/Ngola, script/).
// This file is a documented generator, not a test: nothing in the build runs it.
//
//   git clone https://github.com/KevinKib/Ngola.git /somewhere/Ngola      (commit recorded in the fixture header)
//   node generate-js-reference-moves.js /somewhere/Ngola/script fixture > js-reference-moves.txt
//   node generate-js-reference-moves.js /somewhere/Ngola/script big     > /scratch/js-reference-big.txt
//
// Node 18+; no dependency of its own. script/ngola.js requires './writer' (which needs the npm package
// console-read-write): provide a stub writer.js ({ log(){}, logOnLine(){} }) next to a copy of the three
// files if you do not want to install it. The driver sets pit counts and the player to move directly on
// the game object, plays one pit with Ngola.play, and prints position before, pit, position after,
// winner. Every random choice comes from the seeded generator below, so the output is deterministic.
//
// Line format (one move per line, pit counts as "pit:seeds" for the non-empty pits, rows 1 to 4):
//   <mover A|B> <pit> | <board before> | <board after> | <winner - | A | B>
//   <mover A|B> <pit> | <board before> | ENDLESS            (the JS hit MAX_REPEATS: game ends, no winner)
// A is the JS player 1 (rows 1 and 2), B is player 2 (rows 3 and 4).
'use strict';
const path = require('path');
const dir = path.resolve(process.argv[2]);
const mode = process.argv[3] || 'fixture';
const { Ngola } = require(path.join(dir, 'ngola.js'));

function rng(seed) { // mulberry32
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const random = rng(15102026);
const NAMES = [];
for (let row = 1; row <= 4; row++) for (const col of 'abcdefgh') NAMES.push(col + row);
const SIDE = { A: [1, 2], B: [3, 4] };

function describe(counts) {
  return NAMES.filter((n) => counts[n] > 0).map((n) => n + ':' + counts[n]).join(' ');
}
function snapshot(game) {
  const counts = {};
  for (const n of NAMES) counts[n] = game.plateau.getCase(n).nbBilles;
  return counts;
}
function load(counts, mover) {
  const game = new Ngola();
  for (const n of NAMES) game.plateau.getCase(n).nbBilles = counts[n] || 0;
  game.__joueurCourant = mover === 'A' ? 1 : 2;
  return game;
}
function layout(side, kind) {
  const pits = NAMES.filter((n) => SIDE[side].includes(Number(n[1])));
  const counts = {};
  pits.forEach((n) => (counts[n] = 0));
  if (kind === 'two') pits.forEach((n) => (counts[n] = 2));
  else if (kind === 'classic') {
    const letters = 'abcdefgh';
    pits.forEach((n) => (counts[n] = (letters.indexOf(n[0]) + Number(n[1])) % 2 === 0 ? 4 : 0));
  } else for (let i = 0; i < 32; i++) counts[pits[Math.floor(random() * 16)]]++; // uniform over the 16 pits
  return counts;
}
function sparse(side) {
  const pits = NAMES.filter((n) => SIDE[side].includes(Number(n[1])));
  const counts = {};
  pits.forEach((n) => (counts[n] = 0));
  const seeds = 6 + Math.floor(random() * 14);
  for (let i = 0; i < seeds; i++) counts[pits[Math.floor(random() * 16)]]++;
  return counts;
}
const stats = { finite: 0, endless: 0 };
function sow(counts, mover, pit, out, keep) {
  const game = load(counts, mover);
  const before = describe(snapshot(game));
  game.play(pit);
  if (game.__boucleInfinie) {
    stats.endless++;
    if (keep !== 'finite') out.push(`${mover} ${pit} | ${before} | ENDLESS`);
    return null;
  }
  stats.finite++;
  const after = snapshot(game);
  const winner = game.__vainqueur === 1 ? 'A' : game.__vainqueur === 2 ? 'B' : '-';
  if (keep !== 'endless') out.push(`${mover} ${pit} | ${before} | ${describe(after)} | ${winner}`);
  return { after, winner };
}
function allMoves(counts, mover, out, keep) {
  for (const pit of load(counts, mover).listeLegalMoves) sow(counts, mover, pit, out, keep);
}
function full(kind) {
  return { ...layout('A', kind), ...layout('B', kind) };
}
function play(counts, mover, plies, out, keepAll) {
  for (let ply = 0; ply < plies; ply++) {
    const game = load(counts, mover);
    if (!game.enJeu) return;
    const moves = game.listeLegalMoves;
    const pit = moves[Math.floor(random() * moves.length)];
    const result = sow(counts, mover, pit, out, keepAll ? 'all' : 'all');
    if (!result || result.winner !== '-') return;
    counts = result.after;
    mover = mover === 'A' ? 'B' : 'A';
  }
}

const out = [];
const big = mode === 'big';
for (const kind of ['classic', 'two']) for (const mover of ['A', 'B']) allMoves(full(kind), mover, out, 'all');
const positions = big ? 3000 : 8;
for (let i = 0; i < positions; i++) {
  const counts = full('random');
  for (const mover of ['A', 'B']) allMoves(counts, mover, out, 'all');
}
const games = big ? 1500 : 6;
const plies = big ? 400 : 14;
for (let g = 0; g < games; g++) play(full(['classic', 'two', 'random'][g % 3]), g % 2 ? 'B' : 'A', plies, out);
// Sparse one-sided boards, where endless sowings appear: finite moves of a few boards, endless ones of all.
const sparseCount = 20000;
let finiteKept = 0;
let endlessKept = 0;
for (let i = 0; i < sparseCount; i++) {
  const mover = i % 2 ? 'B' : 'A';
  const counts = sparse(mover);
  if (!big && finiteKept >= 25 && endlessKept >= 10) break;
  const lines = [];
  allMoves(counts, mover, lines, 'all');
  for (const line of lines) {
    const isEndless = line.endsWith('ENDLESS');
    if (big || (isEndless && endlessKept < 10) || (!isEndless && finiteKept < 25)) {
      out.push(line);
      if (isEndless) endlessKept++;
      else finiteKept++;
    }
  }
}
console.log(`# ${out.length} moves; JS stats during generation: ${stats.finite} finite, ${stats.endless} endless`);
for (const line of out) console.log(line);
