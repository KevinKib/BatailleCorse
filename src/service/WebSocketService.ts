import SockJS from 'sockjs-client/dist/sockjs';
import { Client } from '@stomp/stompjs';

class WebSocketService {

  public init() {
    console.log("Creating SockJS...");
    const factory = () => {
      console.log("Creating SockJS connection to http://127.0.0.1:8080/connect");
      return new SockJS('http://127.0.0.1:8080/connect');
      // return new SockJS('http://172.31.112.1:8080/connect');
    };

    const stompClient = new Client({
      brokerURL: undefined,
      webSocketFactory: factory,
      debug: (str) => console.log("[STOMP DEBUG]", str),
      onConnect: (frame) => {
        console.log('[STOMP] Connected:', frame);
      },
      onStompError: (frame) => {
        console.error('[STOMP] Error:', frame.headers['message']);
        console.error('[STOMP] Details:', frame.body);
      },
    });

    console.log("Activating STOMP client...");
    stompClient.activate();
  }

}

const service = new WebSocketService();

export default service;