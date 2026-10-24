import { describe, expect, it } from 'vitest';
import config from '../vite.config.mjs';

// The e2e stack serves the production build with `vite preview` (frontend Dockerfile, target e2e).
// It must answer on the same container port as the dev server and reach the backend through the
// same proxy, or Cypress would hit a page with no API nor STOMP socket.
describe('vite preview (e2e stack)', () => {
  it('listens on all interfaces on the dev server port', () => {
    expect(config.preview?.host).toBe(true);
    expect(config.preview?.port).toBe(config.server?.port);
    expect(config.preview?.strictPort).toBe(true);
  });

  it('proxies /api and the /connect WebSocket to the backend like the dev server', () => {
    expect(config.preview?.proxy).toEqual(config.server?.proxy);
    expect(config.preview?.proxy).toMatchObject({
      '/connect': { target: 'http://backend:8080', ws: true },
      '/api': { target: 'http://backend:8080' },
    });
  });
});
