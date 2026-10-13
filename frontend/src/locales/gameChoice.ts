import type { GameKey } from '../model/games';
import type { GameChoiceMessages, Messages } from './Messages';

/**
 * The picker texts of a game. A registered game always has them (checked by the locale tests); a missing
 * entry is a wiring mistake and fails loudly instead of showing a blank card.
 */
export function gameChoice(messages: Messages, key: GameKey): GameChoiceMessages {
  const choice = messages.gamePicker.games[key];
  if (!choice) throw new Error(`No picker texts for the game "${key}"`);
  return choice;
}
