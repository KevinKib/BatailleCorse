/**
 * Reads a persisted seat-to-token map. A missing, unparsable or non-object entry
 * counts as "no stored token"; a corrupted entry is removed.
 */
export function readStoredTokens(key: string): Record<string, string> | null {
  const stored = localStorage.getItem(key);
  if (!stored) return null;
  try {
    const parsed: unknown = JSON.parse(stored);
    if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, string>;
    }
  } catch {
    // fall through: corrupted entry
  }
  localStorage.removeItem(key);
  return null;
}
