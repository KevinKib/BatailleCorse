import { useI18n } from './useI18n';
import { HttpError } from '../model/HttpError';

/**
 * The line shown when a join is refused, by the join form and by the rows of the public list.
 * 404: the lobby was deleted (everybody left); 409: no free seat left, or the game has started.
 */
export function joinErrorMessage(e: unknown): string {
  const ui = useI18n().join;
  if (!(e instanceof HttpError)) return ui.failed;
  if (e.status === 404) return ui.notFound;
  if (e.status === 409 && e.reason === 'full') return ui.full;
  if (e.status === 409 && e.reason === 'started') return ui.started;
  return ui.failed;
}
