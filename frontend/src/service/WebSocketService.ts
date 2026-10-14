import SockJS from 'sockjs-client/dist/sockjs';
import { Client } from '@stomp/stompjs';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';
import { setConnectionOnline } from '../composables/useConnectionStatus';

class WebSocketService {

  private readonly enableLogs = false;
  private readonly connectUrl = '/connect';

  private client!: Client;
  private currentGameId: string | null = null;
  private currentGameSubscription: { unsubscribe: () => void } | null = null;
  private currentPresence: string | null = null;
  private seatSubscription: { gameId: string; seat: number; token: string; onMessage: (r: any) => void; onConnected?: () => void } | null = null;
  private currentSeatSub: { unsubscribe: () => void } | null = null;
  // Commands sent before the first connection came up. A lobby is shown from REST data, so a player can act
  // while SockJS is still connecting: those commands wait for the connection instead of failing.
  private everConnected = false;
  private pending: Array<{ destination: string; body?: any }> = [];

  public init() {
    this.everConnected = false;
    this.pending = [];
    this.log("Creating SockJS...");
    const factory = () => {
      this.log("Creating SockJS connection to " + this.connectUrl);
      return new SockJS(this.connectUrl);
    };

    const stompClient = new Client({
      webSocketFactory: factory,
      reconnectDelay: 3000,
      debug: (str) => this.log("[STOMP DEBUG]", str),
      onConnect: (frame) => {
        this.log('[STOMP] Connected:', frame);
        setConnectionOnline(true);
        this.everConnected = true;

        // Re-subscribe to per-game channel after reconnect.
        if (this.currentGameId) {
          this.doSubscribeToGame(this.currentGameId);
        }

        if (this.seatSubscription) {
          this.doSubscribeToSeat();
        }

        // Re-assert presence after every (re)connect so the server can re-bind
        // this session to its seat and cancel any pending disconnect timer.
        if (this.currentPresence) {
          this.publish('/app/presence', this.currentPresence);
        }

        const waiting = this.pending;
        this.pending = [];
        waiting.forEach(({ destination, body }) => this.publish(destination, body));

        // Whatever was broadcast before this subscription existed (the first connection, or an offline spell)
        // is lost: let the owner of the seat reload its state now that the channel is live.
        this.seatSubscription?.onConnected?.();
      },
      onWebSocketClose: () => {
        // Fires on every close, including a failed reconnect attempt: we are offline until onConnect.
        setConnectionOnline(false);
      },
      onDisconnect: () => {
        this.log('[STOMP] Disconnected — will reconnect in 3s');
      },
      onStompError: (frame) => {
        console.error('[STOMP] Error:', frame.headers['message']);
        console.error('[STOMP] Details:', frame.body);
      },
    });

    this.log("Activating STOMP client...");
    stompClient.activate();

    this.client = stompClient;
  }

  /**
   * `onConnected` is called after every STOMP connection once the seat channel is subscribed, the first one
   * included (not when subscribing on an already live connection).
   */
  public subscribeToSeat(
    gameId: string, seat: number, token: string, onMessage: (response: any) => void, onConnected?: () => void,
  ) {
    this.seatSubscription = { gameId, seat, token, onMessage, onConnected };
    if (this.client?.connected) {
      this.doSubscribeToSeat();
    }
  }

  public unsubscribeFromSeat() {
    this.currentSeatSub?.unsubscribe();
    this.currentSeatSub = null;
    this.seatSubscription = null;
  }

  public subscribeToGame(gameId: string) {
    this.currentGameId = gameId;
    if (this.client?.connected) {
      this.doSubscribeToGame(gameId);
    }
  }

  public unsubscribeFromGame() {
    this.currentGameSubscription?.unsubscribe();
    this.currentGameSubscription = null;
    this.currentGameId = null;
    this.currentPresence = null;
  }

  public setPresence(body: string) {
    this.currentPresence = body;
    if (this.client?.connected) {
      this.publish('/app/presence', body);
    }
  }

  public clearPresence() {
    this.currentPresence = null;
  }

  public publish(destination: string, body?: any) {
    if (!this.everConnected && !this.client.connected) {
      this.pending.push({ destination, body });
      return;
    }
    this.client.publish({ destination, body });
  }

  private doSubscribeToSeat() {
    if (!this.seatSubscription) return;
    this.currentSeatSub?.unsubscribe();
    // Per-seat channels are addressed by token, not seat index: indices are recycled when a room
    // reopens for a rematch (same gameId), so an index-keyed topic would leak the new occupant's
    // messages to the previous occupant's still-live subscription. The token is unique per claim.
    const { gameId, token, onMessage } = this.seatSubscription;
    this.currentSeatSub = this.client.subscribe(
      `/topic/game/${gameId}/seat/${token}`,
      message => onMessage(JSON.parse(message.body)),
      { token },
    );
  }

  private doSubscribeToGame(gameId: string) {
    this.currentGameSubscription?.unsubscribe();
    this.currentGameSubscription = this.client.subscribe(`/topic/game/${gameId}`, message => {
      const response = JSON.parse(message.body);
      useBatailleCorseStore().onResponse(response);
    });
  }

  private log(message?: any, ...optionalParams: any[]) {
    if (this.enableLogs) {
      console.log(message, ...optionalParams);
    }
  }

}

const service = new WebSocketService();

export default service;
