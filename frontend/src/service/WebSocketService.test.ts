import { beforeEach, describe, expect, it, vi } from 'vitest';

// A fake STOMP client: the test decides when the connection comes up or goes down.
const fake = vi.hoisted(() => ({
  instance: null as null | {
    connected: boolean;
    published: Array<{ destination: string; body?: string }>;
    options: { onConnect: (frame: unknown) => void };
  },
}));

vi.mock('@stomp/stompjs', () => ({
  Client: class {
    connected = false;
    published: Array<{ destination: string; body?: string }> = [];
    constructor(public options: { onConnect: (frame: unknown) => void }) {
      fake.instance = this;
    }
    activate() {}
    subscribe() { return { unsubscribe() {} }; }
    publish(message: { destination: string; body?: string }) {
      if (!this.connected) throw new Error('There is no underlying STOMP connection');
      this.published.push(message);
    }
  },
}));
vi.mock('sockjs-client/dist/sockjs', () => ({ default: class {} }));
vi.mock('../state/BatailleCorse.store', () => ({ useBatailleCorseStore: () => ({ onResponse() {} }) }));

import service from './WebSocketService';

function connect() {
  fake.instance!.connected = true;
  fake.instance!.options.onConnect({});
}

describe('WebSocketService.publish', () => {
  beforeEach(() => service.init());

  it('holds a command sent before the first connection and sends it once connected', () => {
    expect(() => service.publish('/app/lobby/leave', '{}')).not.toThrow();
    expect(fake.instance!.published).toEqual([]);

    connect();

    expect(fake.instance!.published).toEqual([{ destination: '/app/lobby/leave', body: '{}' }]);
  });

  it('still refuses a command while the socket is down after having been up', () => {
    connect();
    fake.instance!.connected = false;

    expect(() => service.publish('/app/lobby/leave', '{}')).toThrow();
  });
});
