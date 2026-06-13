import { describe, it, expect } from 'vitest';
import BatailleCorse from './BatailleCorse';
import Pile from './Pile';
import Player from './Player';
import { buildGame, buildPlayer } from './fixtures';

describe('BatailleCorse.fromJSON', () => {
  it('constructs Pile and Player instances with their behaviour methods', () => {
    const data = {
      currentPlayer: { id: '0', nbCards: 26, availableActions: ['SEND'] },
      pile: {
        cards: [],
        grabbable: true,
        nbCardsSinceLastHonourCard: 0,
        playerThatAddedLastHonourCard: { id: '0' },
      },
      players: [
        { id: '0', nbCards: 26, availableActions: ['SEND'] },
        { id: '1', nbCards: 26, availableActions: [] },
      ],
      winner: null,
    };

    const game = BatailleCorse.fromJSON(data);

    expect(game).toBeInstanceOf(BatailleCorse);
    expect(game.pile).toBeInstanceOf(Pile);
    expect(game.players[0]).toBeInstanceOf(Player);
    expect(game.players[1]).toBeInstanceOf(Player);
    expect(game.pile.getAutoGrabPlayer()).toBe(0);
    expect(game.players[0].hasAvailableAction('SEND')).toBe(true);
    expect(game.players[1].hasAvailableAction('SEND')).toBe(false);
    expect(game.winner).toBeNull();
  });
});

describe('BatailleCorse end-of-game queries', () => {
  it('givenNoWinner_thenIsOverIsFalse', () => {
    const game = buildGame({ winner: null });
    expect(game.isOver()).toBe(false);
  });

  it('givenWinner_thenIsOverIsTrue', () => {
    const game = buildGame({ winner: { id: '0' } });
    expect(game.isOver()).toBe(true);
  });

  it('givenWinner_thenIsWinnerMatchesOnlyTheWinningId', () => {
    const game = buildGame({ winner: { id: '0' } });
    expect(game.isWinner('0')).toBe(true);
    expect(game.isWinner('1')).toBe(false);
  });

  it('givenNoWinner_thenIsWinnerIsFalseForEveryId', () => {
    const game = buildGame({ winner: null });
    expect(game.isWinner('0')).toBe(false);
    expect(game.isWinner('1')).toBe(false);
  });

  it('givenNoWinner_thenIsWinnerIsFalseForUndefinedId', () => {
    const game = buildGame({ winner: null });
    expect(game.isWinner(undefined)).toBe(false);
  });

  it('givenWinner_thenIsWinnerAtResolvesTheSeatId', () => {
    const game = buildGame({
      players: [buildPlayer({ id: 'a' }), buildPlayer({ id: 'b' })],
      winner: { id: 'b' },
    });
    expect(game.isWinnerAt(0)).toBe(false);
    expect(game.isWinnerAt(1)).toBe(true);
  });

  it('givenNoWinner_thenIsWinnerAtIsFalse', () => {
    const game = buildGame({ winner: null });
    expect(game.isWinnerAt(0)).toBe(false);
    expect(game.isWinnerAt(1)).toBe(false);
  });
});

describe('BatailleCorse turn queries', () => {
  it('givenPlayerHasSendAvailable_thenCanSendIsTrue', () => {
    const game = buildGame({
      players: [
        buildPlayer({ id: 'a', availableActions: ['SEND'] }),
        buildPlayer({ id: 'b', availableActions: ['SLAP'] }),
      ],
    });
    expect(game.canSend(0)).toBe(true);
  });

  it('givenPlayerLacksSendAvailable_thenCanSendIsFalse', () => {
    const game = buildGame({
      players: [
        buildPlayer({ id: 'a', availableActions: ['SEND'] }),
        buildPlayer({ id: 'b', availableActions: ['SLAP'] }),
      ],
    });
    expect(game.canSend(1)).toBe(false);
  });

  it('givenNoPlayerHasSend_thenCanSendIsFalseForEverySeat', () => {
    // e.g. the pile is full/grabbable or the game is finished: the server offers
    // SEND to no one.
    const game = buildGame({
      players: [
        buildPlayer({ id: 'a', availableActions: ['GRAB'] }),
        buildPlayer({ id: 'b', availableActions: ['SLAP'] }),
      ],
    });
    expect(game.canSend(0)).toBe(false);
    expect(game.canSend(1)).toBe(false);
  });

  it('givenIndexOutOfRange_thenCanSendIsFalse', () => {
    const game = buildGame();
    expect(game.canSend(5)).toBe(false);
  });
});

describe('BatailleCorse.canPlayerAct', () => {
  it('givenPlayerHasActionAvailable_thenTrue', () => {
    const game = buildGame({
      players: [
        buildPlayer({ id: 'a', availableActions: ['SEND'] }),
        buildPlayer({ id: 'b', availableActions: ['SLAP'] }),
      ],
    });
    expect(game.canPlayerAct(0, 'send')).toBe(true);
    expect(game.canPlayerAct(1, 'slap')).toBe(true);
  });

  it('givenPlayerLacksAction_thenFalse', () => {
    const game = buildGame({
      players: [
        buildPlayer({ id: 'a', availableActions: ['SEND'] }),
        buildPlayer({ id: 'b', availableActions: ['SLAP'] }),
      ],
    });
    expect(game.canPlayerAct(0, 'slap')).toBe(false);
    expect(game.canPlayerAct(1, 'send')).toBe(false);
  });

  it('givenIndexOutOfRange_thenFalse', () => {
    const game = buildGame();
    expect(game.canPlayerAct(5, 'send')).toBe(false);
  });
});

describe('BatailleCorse.opponentForfeitReason', () => {
  it('returns the other seat forfeit reason for the given player index', () => {
    const game = buildGame({
      players: [
        buildPlayer({ id: '0', forfeitReason: null }),
        buildPlayer({ id: '1', forfeitReason: 'DISCONNECTED' }),
      ],
    });
    // seat 0 is the winner; its opponent (seat 1) disconnected
    expect(game.opponentForfeitReason(0)).toBe('DISCONNECTED');
  });

  it('returns null when the opponent did not forfeit', () => {
    const game = buildGame({
      players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1' })],
    });
    expect(game.opponentForfeitReason(0)).toBeNull();
  });

  it('parses forfeitReason from JSON onto players', () => {
    const game = BatailleCorse.fromJSON({
      currentPlayer: { id: '0', nbCards: 26, availableActions: [] },
      pile: {
        cards: [], grabbable: false, nbCardsSinceLastHonourCard: 0,
        playerThatAddedLastHonourCard: { id: '0' },
      },
      players: [
        { id: '0', nbCards: 26, availableActions: [] },
        { id: '1', nbCards: 0, availableActions: [], forfeitReason: 'RESIGNED' },
      ],
      winner: { id: '0' },
    });
    expect(game.players[1].forfeitReason).toBe('RESIGNED');
  });
});
