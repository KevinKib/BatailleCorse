import { useHotkeys } from './useHotkeys';

export const DISCARD_KEY = 'd';
export const CALL_KEY = 'b';
// Digits pick the hand cards in display order: 1 to 9, then 0 for the tenth.
export const CARD_KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];

// Keyboard shortcuts of the Bullshit table, on top of the shared useHotkeys. Each one does what
// its button does and nothing when the button is disabled (checked at the time of the key press).
export function useBullshitHotkeys(actions: {
  canDiscard: () => boolean;
  canCall: () => boolean;
  handSize: () => number;
  discard: () => void;
  call: () => void;
  toggleCard: (displayIndex: number) => void;
}) {
  useHotkeys(
    () => { if (actions.canDiscard()) actions.discard(); },
    () => { if (actions.canCall()) actions.call(); },
    () => [DISCARD_KEY],
    () => [CALL_KEY],
    (key) => {
      const index = CARD_KEYS.indexOf(key);
      if (index >= 0 && index < actions.handSize()) actions.toggleCard(index);
    },
  );
}
