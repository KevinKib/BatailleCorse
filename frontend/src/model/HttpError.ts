/** A refused HTTP call: the status tells a missing game (404) from a full or started one (409). */
export class HttpError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
    this.name = 'HttpError';
  }
}
