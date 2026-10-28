import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useDraughtsStore } from './Draughts.store';
import {
  draughtsState, draughtsOpeningState, draughtsCaptureState, draughtsAmbiguousState, draughtsFinishedState,
} from '../model/draughts/fixtures';
import type { LobbyView } from '../model/lobby/LobbyView';
import webSocketService from '../service/WebSocketService';

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: null, joined: false }],
    hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 2, canStart: false,
    ...overrides,
  };
}

describe('Draughts store', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  function seated(seat = 0) {
    const store = useDraughtsStore();
    store.applyEvent({ type: 'seat-change', seat });
    return store;
  }

  function playing(seat = 0, state = draughtsOpeningState) {
    const store = seated(seat);
    store.restore('g1', seat, `tok-${seat}`);
    store.applyEvent({ type: 'state-update', state });
    return store;
  }

  const spyPublish = () => vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});

  it('is connecting until a view arrives, then lobby, playing and finished', () => {
    const store = seated();
    expect(store.phase).toBe('connecting');
    store.applyEvent({ type: 'state-update', state: lobbyView() });
    expect(store.phase).toBe('lobby');
    expect(store.lobby).not.toBeNull();
    expect(store.game).toBeNull();
    store.applyEvent({ type: 'state-update', state: draughtsOpeningState });
    expect(store.phase).toBe('playing');
    expect(store.game).not.toBeNull();
    store.applyEvent({ type: 'state-update', state: draughtsFinishedState });
    expect(store.phase).toBe('finished');
  });

  it('isMyTurn follows the current seat, and myColour and orientation follow my seat', () => {
    const store = playing(0);
    expect(store.isMyTurn).toBe(true);
    expect(store.myColour).toBe('WHITE');
    expect(store.orientation).toBe('WHITE');
    store.applyEvent({ type: 'seat-change', seat: 1 });
    expect(store.isMyTurn).toBe(false);
    expect(store.myColour).toBe('BLACK');
    expect(store.orientation).toBe('BLACK');
  });

  it('reads the colour of each seat from the state (the host may play Black)', () => {
    const store = playing(0, draughtsState({
      whiteSeat: 1,
      currentSeat: 1,
      players: [
        { seat: 0, name: 'Alice', colour: 'BLACK', current: false, men: 20, kings: 0, captured: 0 },
        { seat: 1, name: 'Bob', colour: 'WHITE', current: true, men: 20, kings: 0, captured: 0 },
      ],
    }));
    expect(store.colourOf(0)).toBe('BLACK');
    expect(store.colourOf(1)).toBe('WHITE');
    expect(store.myColour).toBe('BLACK');
    expect(store.orientation).toBe('BLACK');
  });

  it('knows who won, and a draw', () => {
    const store = playing(0, draughtsFinishedState);
    expect(store.isOver).toBe(true);
    expect(store.iWon).toBe(true);
    expect(store.isDraw).toBe(false);
    store.applyEvent({
      type: 'state-update',
      state: draughtsState({ phase: 'FINISHED', outcome: { status: 'FINISHED', winner: null, reason: 'DRAW_AGREED' } }),
    });
    expect(store.isDraw).toBe(true);
    expect(store.iWon).toBe(false);
  });

  it('remembers the level of a computer seat', () => {
    const store = seated();
    store.applyEvent({
      type: 'state-update',
      state: lobbyView({ players: [
        { seat: 0, name: 'Alice', joined: true },
        { seat: 1, name: null, joined: true, bot: true, level: 'greedy' },
      ] }),
    });
    expect(store.botLevels).toEqual({ 1: 'greedy' });
  });

  describe('selection', () => {
    it('offers the pieces that can move, and the destinations of the selected one', () => {
      const store = playing();
      expect(store.selectableSquares).toEqual([31, 32, 33, 34, 35]);
      expect(store.targetSquares).toEqual([]);
      store.squareClicked(31);
      expect(store.selected).toBe(31);
      expect(store.targetSquares).toEqual([26, 27]);
    });

    it('offers nothing when it is not my turn', () => {
      const store = playing(1);
      expect(store.selectableSquares).toEqual([]);
      store.squareClicked(1);
      expect(store.selected).toBeNull();
    });

    it('switches the selection, and clears it on a tap on the selected piece or Escape', () => {
      const store = playing();
      store.squareClicked(31);
      store.squareClicked(32);
      expect(store.selected).toBe(32);
      store.squareClicked(32);
      expect(store.selected).toBeNull();
      store.squareClicked(32);
      store.clearSelection();
      expect(store.selected).toBeNull();
    });

    it('drops the selection when a new state arrives', () => {
      const store = playing();
      store.squareClicked(31);
      store.applyEvent({ type: 'state-update', state: draughtsState({ version: 1 }) });
      expect(store.selected).toBeNull();
    });

    it('with a compulsory capture, only capturing pieces can be selected, and the hint carries the majority', () => {
      const store = playing(0, draughtsCaptureState);
      expect(store.selectableSquares).toEqual([33, 34]);
      store.squareClicked(47);
      expect(store.selected).toBeNull();
      expect(store.captureHint).toBe(1);
    });

    it('has no capture hint when no capture is compulsory, or when it is not my turn', () => {
      expect(playing().captureHint).toBeNull();
      expect(playing(1, draughtsCaptureState).captureHint).toBeNull();
    });
  });

  describe('playing', () => {
    it('a tap on a destination publishes the move with its captured set and clears the selection', () => {
      const publish = spyPublish();
      const store = playing(0, draughtsCaptureState);
      store.squareClicked(33);
      expect(publish).not.toHaveBeenCalled();
      store.squareClicked(22);
      expect(publish).toHaveBeenCalledWith('/app/draughts/move',
        JSON.stringify({ gameId: 'g1', token: 'tok-0', from: 33, to: 22, captures: [28] }));
      expect(store.selected).toBeNull();
    });

    it('an ambiguous destination shows the candidates and sends nothing', () => {
      const publish = spyPublish();
      const store = playing(0, draughtsAmbiguousState);
      store.squareClicked(46);
      store.squareClicked(19);
      expect(publish).not.toHaveBeenCalled();
      expect(store.choice).toHaveLength(2);
      expect(store.choiceSquares).toEqual(new Map([[37, 0], [28, 0], [32, 1], [23, 1]]));
    });

    it('a tap on a piece that belongs to one candidate sends that move', () => {
      const publish = spyPublish();
      const store = playing(0, draughtsAmbiguousState);
      store.squareClicked(46);
      store.squareClicked(19);
      store.squareClicked(23);
      expect(publish).toHaveBeenCalledWith('/app/draughts/move',
        JSON.stringify({ gameId: 'g1', token: 'tok-0', from: 46, to: 19, captures: [32, 23] }));
      expect(store.choice).toBeNull();
      expect(store.selected).toBeNull();
    });

    it('the chooser can pick a candidate by index (a list of "takes ...")', () => {
      const publish = spyPublish();
      const store = playing(0, draughtsAmbiguousState);
      store.squareClicked(46);
      store.squareClicked(19);
      store.chooseCandidate(0);
      expect(publish).toHaveBeenCalledWith('/app/draughts/move',
        JSON.stringify({ gameId: 'g1', token: 'tok-0', from: 46, to: 19, captures: [37, 28] }));
      expect(store.choice).toBeNull();
    });

    it('a tap elsewhere closes the chooser and Escape too', () => {
      const store = playing(0, draughtsAmbiguousState);
      store.squareClicked(46);
      store.squareClicked(19);
      store.squareClicked(5);
      expect(store.choice).toBeNull();
      store.squareClicked(19);
      store.clearSelection();
      expect(store.choice).toBeNull();
      expect(store.selected).toBeNull();
    });

    it('keeps the selection when the socket is down, so that the player can retry', () => {
      vi.spyOn(webSocketService, 'publish').mockImplementation(() => { throw new Error('down'); });
      const store = playing();
      store.squareClicked(31);
      expect(() => store.squareClicked(26)).not.toThrow();
      expect(store.selected).toBe(31);
    });
  });

  describe('draw', () => {
    const offerable = draughtsState({ availableActions: ['MOVE', 'OFFER_DRAW'] });
    const answerable = draughtsState({
      availableActions: ['ACCEPT_DRAW', 'DECLINE_DRAW'],
      drawOffer: { by: 1 },
    });

    it('reads what I may do from availableActions', () => {
      const store = playing(0, offerable);
      expect(store.canOfferDraw).toBe(true);
      expect(store.canAnswerDraw).toBe(false);
      store.applyEvent({ type: 'state-update', state: answerable });
      expect(store.canOfferDraw).toBe(false);
      expect(store.canAnswerDraw).toBe(true);
    });

    it('tells an incoming offer from my own pending offer', () => {
      const store = playing(0, answerable);
      expect(store.drawOfferedBy).toBe(1);
      expect(store.incomingDrawOffer).toBe(true);
      expect(store.myDrawOfferPending).toBe(false);
      store.applyEvent({ type: 'state-update', state: draughtsState({ drawOffer: { by: 0 }, availableActions: [] }) });
      expect(store.incomingDrawOffer).toBe(false);
      expect(store.myDrawOfferPending).toBe(true);
    });

    it('reports a position seen twice, and nothing once the game is over', () => {
      const store = playing(0, draughtsState({ repetitions: 2 }));
      expect(store.repeatedTwice).toBe(true);
      store.applyEvent({ type: 'state-update', state: draughtsState({ repetitions: 1 }) });
      expect(store.repeatedTwice).toBe(false);
    });

    it('publishes the three draw commands', () => {
      const publish = spyPublish();
      const store = playing(0, offerable);
      store.offerDraw();
      store.acceptDraw();
      store.declineDraw();
      expect(publish.mock.calls.map(c => c[0])).toEqual([
        '/app/draughts/offerDraw', '/app/draughts/acceptDraw', '/app/draughts/declineDraw',
      ]);
    });

    it('drops a draw command the socket cannot carry', () => {
      vi.spyOn(webSocketService, 'publish').mockImplementation(() => { throw new Error('down'); });
      const store = playing(0, offerable);
      expect(() => store.offerDraw()).not.toThrow();
    });

    it('notes an offer or a refusal until the next move', () => {
      const store = playing();
      store.applyEvent({ type: 'event', eventType: 'DRAW_OFFERED', message: '', eventData: { seat: 1 } });
      expect(store.drawNotice).toEqual({ kind: 'offered', seat: 1 });
      store.applyEvent({ type: 'event', eventType: 'DRAW_DECLINED', message: '', eventData: { seat: 0 } });
      expect(store.drawNotice).toEqual({ kind: 'declined', seat: 0 });
      store.applyEvent({
        type: 'event', eventType: 'MOVE', message: '',
        eventData: { seat: 1, from: 19, to: 23, path: [23], captures: [], promotes: false },
      });
      expect(store.drawNotice).toBeNull();
    });
  });

  describe('last move', () => {
    const moveData = { seat: 1, from: 19, to: 30, path: [24, 30], captures: [24], promotes: false };

    it('keeps the data of a MOVE event, and of a GAME_OVER caused by a move', () => {
      const store = playing();
      store.applyEvent({ type: 'event', eventType: 'MOVE', message: '', eventData: moveData });
      expect(store.lastMove).toEqual(moveData);
      store.applyEvent({
        type: 'event', eventType: 'GAME_OVER', message: '', eventData: { ...moveData, from: 20 },
      });
      expect(store.lastMove?.from).toBe(20);
    });

    it('ignores a GAME_OVER that did not end on a move', () => {
      const store = playing();
      store.applyEvent({ type: 'event', eventType: 'MOVE', message: '', eventData: moveData });
      store.applyEvent({ type: 'event', eventType: 'GAME_OVER', message: '', eventData: { winner: 0 } });
      expect(store.lastMove).toEqual(moveData);
    });

    it('is the opponent move only while it is not mine', () => {
      const store = playing(0);
      store.applyEvent({ type: 'event', eventType: 'MOVE', message: '', eventData: moveData });
      expect(store.opponentLastMove).toEqual(moveData);
      store.applyEvent({ type: 'event', eventType: 'MOVE', message: '', eventData: { ...moveData, seat: 0 } });
      expect(store.opponentLastMove).toBeNull();
    });

    it('is forgotten when the room goes back to the lobby', () => {
      const store = playing();
      store.applyEvent({ type: 'event', eventType: 'MOVE', message: '', eventData: moveData });
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.lastMove).toBeNull();
    });
  });

  it('a FORFEIT event keeps its reason, apart from the last move', () => {
    const store = playing();
    store.applyEvent({
      type: 'event', eventType: 'MOVE', message: '',
      eventData: { seat: 0, from: 32, to: 28, path: [28], captures: [], promotes: false },
    });
    store.applyEvent({ type: 'event', eventType: 'FORFEIT', message: '', eventData: { loserSeat: 1, reason: 'RESIGNED' } });
    expect(store.lastForfeit).toEqual({ seat: 1, reason: 'RESIGNED' });
    expect(store.lastMove?.from).toBe(32);
  });

  it('a lobby error from a refused command shows in the lobby, not at the table', () => {
    const store = seated(0);
    store.applyEvent({ type: 'state-update', state: lobbyView() });
    store.applyEvent({ type: 'error', eventType: 'START', message: 'Not enough players' });
    expect(store.lobbyError).toBe('Not enough players');
    store.applyEvent({ type: 'state-update', state: draughtsOpeningState });
    store.applyEvent({ type: 'error', eventType: 'MOVE', message: 'draughts.errors.illegalMove' });
    expect(store.lobbyError).toBeNull();
  });

  it('removed by the host clears the table', () => {
    const store = playing();
    store.squareClicked(31);
    store.applyEvent({ type: 'removed' });
    expect(store.state).toBeNull();
    expect(store.selected).toBeNull();
    expect(store.gameId).toBeNull();
  });

  it('setOption sends the lobby option to the server, nothing is changed locally', () => {
    const store = seated(0);
    store.restore('g1', 0, 'tok-0');
    store.applyEvent({ type: 'state-update', state: lobbyView({ options: { drawOffers: 'on' } }) });
    const publish = spyPublish();
    store.setOption('drawOffers', 'off');
    expect(publish).toHaveBeenCalledWith('/app/lobby/setOptions',
      JSON.stringify({ gameId: 'g1', token: 'tok-0', values: { drawOffers: 'off' } }));
    expect(store.lobby!.options).toEqual({ drawOffers: 'on' });
  });

  it('forfeit sends the shared forfeit command', () => {
    const publish = spyPublish();
    const store = playing();
    store.forfeit();
    expect(publish).toHaveBeenCalledWith('/app/forfeit', JSON.stringify({ gameId: 'g1', token: 'tok-0' }));
  });
});
