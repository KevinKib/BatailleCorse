import { describe, it, expect } from 'vitest';
import { HttpError } from './HttpError';

describe('HttpError.fromResponse', () => {
  const res = (status: number, refusal?: string) =>
    ({ status, headers: new Headers(refusal ? { 'X-Join-Refusal': refusal } : {}) }) as unknown as Response;

  it('carries the status and the refusal reason the server gave', () => {
    const error = HttpError.fromResponse(res(409, 'full'), 'Join failed');
    expect(error.status).toBe(409);
    expect(error.reason).toBe('full');
    expect(error.message).toBe('Join failed: 409');
  });

  it('knows "started" too, and ignores an unknown reason', () => {
    expect(HttpError.fromResponse(res(409, 'started'), 'Join failed').reason).toBe('started');
    expect(HttpError.fromResponse(res(409, 'whatever'), 'Join failed').reason).toBeUndefined();
    expect(HttpError.fromResponse(res(404), 'Join failed').reason).toBeUndefined();
  });
});
