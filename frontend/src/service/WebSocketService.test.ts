import { beforeEach, describe, expect, it, vi } from 'vitest';

// A fake STOMP client: the test decides when the connection comes up or goes down.
const fake = vi.hoisted(() => ({
  subscribed: [] as string[],
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
    subscribe(destination: string) { fake.subscribed.push(destination); return { unsubscribe() {} }; }
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


describe('WebSocketService seat subscription', () => {
  beforeEach(() => { service.init(); fake.subscribed.length = 0; });

  it('tells the owner of the subscription after every connection, the first included, once subscribed', () => {
    const onConnected = vi.fn(() => { expect(fake.subscribed).toContain('/topic/game/g1/seat/tok'); });
    service.subscribeToSeat('g1', 0, 'tok', () => {}, onConnected);
    expect(onConnected).not.toHaveBeenCalled();

    connect();
    expect(onConnected).toHaveBeenCalledTimes(1);

    fake.instance!.connected = false;
    connect();
    expect(onConnected).toHaveBeenCalledTimes(2);
  });

  it('stops telling an owner that unsubscribed', () => {
    const onConnected = vi.fn();
    service.subscribeToSeat('g1', 0, 'tok', () => {}, onConnected);
    service.unsubscribeFromSeat();
    connect();
    expect(onConnected).not.toHaveBeenCalled();
  });
});

describe('WebSocketService public lobbies subscription', () => {
  beforeEach(() => { service.init(); fake.subscribed.length = 0; });

  it('subscribes to the topic of the game type after every connection and tells its owner', () => {
    const onConnected = vi.fn(() => { expect(fake.subscribed).toContain('/topic/lobbies/kobo'); });
    const leave = service.subscribeToPublicLobbies('kobo', () => {}, onConnected);
    expect(fake.subscribed).toEqual([]);

    connect();
    expect(onConnected).toHaveBeenCalledTimes(1);

    connect();
    expect(onConnected).toHaveBeenCalledTimes(2);
    leave();
  });

  it('subscribes at once when the connection is already live', () => {
    connect();
    const onConnected = vi.fn();
    const leave = service.subscribeToPublicLobbies('kobo', () => {}, onConnected);
    expect(fake.subscribed).toEqual(['/topic/lobbies/kobo']);
    expect(onConnected).toHaveBeenCalledTimes(1);
    leave();
  });

  it('stops resubscribing once the owner leaves', () => {
    const onConnected = vi.fn();
    const leave = service.subscribeToPublicLobbies('kobo', () => {}, onConnected);
    leave();
    connect();
    expect(fake.subscribed).toEqual([]);
    expect(onConnected).not.toHaveBeenCalled();
  });
});
