/** Why the server refused a join with a 409: the lobby is full, or its game has already started. */
export type JoinRefusalReason = 'full' | 'started';

/** A refused HTTP call: the status tells a missing game (404) from a full or started one (409). */
export class HttpError extends Error {
  constructor(message: string, readonly status: number, readonly reason?: JoinRefusalReason) {
    super(message);
    this.name = 'HttpError';
  }

  /** The error of a non-2xx response; a join refusal carries its reason in the `X-Join-Refusal` header. */
  static fromResponse(res: Pick<Response, 'status' | 'headers'>, action: string): HttpError {
    const header = res.headers?.get('X-Join-Refusal');
    const reason = header === 'full' || header === 'started' ? header : undefined;
    return new HttpError(`${action}: ${res.status}`, res.status, reason);
  }
}
