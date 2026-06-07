import { describe, it, expect } from 'vitest';
import { useDisclosure } from './useDisclosure';

describe('useDisclosure', () => {
  it('givenNoArg_thenStartsClosed', () => {
    expect(useDisclosure().isOpen.value).toBe(false);
  });

  it('givenClosed_whenOpen_thenOpen', () => {
    const { isOpen, open } = useDisclosure();
    open();
    expect(isOpen.value).toBe(true);
  });

  it('givenOpen_whenClose_thenClosed', () => {
    const { isOpen, close } = useDisclosure(true);
    close();
    expect(isOpen.value).toBe(false);
  });

  it('givenClosed_whenToggle_thenOpen', () => {
    const { isOpen, toggle } = useDisclosure();
    toggle();
    expect(isOpen.value).toBe(true);
  });

  it('givenOpen_whenToggle_thenClosed', () => {
    const { isOpen, toggle } = useDisclosure(true);
    toggle();
    expect(isOpen.value).toBe(false);
  });
});
