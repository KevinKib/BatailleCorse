import SockJS from 'sockjs-client/dist/sockjs';
import { Client } from '@stomp/stompjs';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';

class WebSocketService {

  private readonly enableLogs = false;
  private readonly connectUrl = '/connect';
  // private readonly local_url = 'http://127.0.0.1:8080/connect';
  // private readonly windows_url = 'http://172.31.112.1:8080/connect';

  private client!: Client;

  public init() {
    const batailleCorse = useBatailleCorseStore();

    this.log("Creating SockJS...");
    const factory = () => {
      this.log("Creating SockJS connection to "+this.connectUrl);
      return new SockJS(this.connectUrl);
    };

    const stompClient = new Client({
      webSocketFactory: factory,
      reconnectDelay: 3000,
      debug: (str) => this.log("[STOMP DEBUG]", str),
      onConnect: (frame) => {
        this.log('[STOMP] Connected:', frame);

        stompClient.subscribe('/topic/game', message => {
          const response = JSON.parse(message.body);
          batailleCorse.onResponse(response);
        });

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

  public publish(destination: string, body?: any) {
    this.client.publish({destination, body});
  }

  private log(message?: any, ...optionalParams: any[]) {
    if (this.enableLogs) {
      console.log(message, ...optionalParams);
    }
  }

  private error(message?: any, ...optionalParams: any[]) {
    if (this.enableLogs) {
      console.error(message, ...optionalParams);
    }
  }

}

const service = new WebSocketService();

export default service;