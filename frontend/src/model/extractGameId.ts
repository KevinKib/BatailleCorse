/**
 * Accepts either a bare game id or a pasted invite link
 * (`https://host/games/bullshit/join/<id>`) and returns the id.
 */
export function extractGameId(input: string): string {
  const trimmed = input.trim();
  if (!trimmed) return '';
  const segments = trimmed.replace(/[?#].*$/, '').split('/').filter(Boolean);
  return segments[segments.length - 1] ?? '';
}
