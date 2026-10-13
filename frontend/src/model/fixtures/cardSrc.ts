import { mount } from '@vue/test-utils';
import PlayingCard from '../../components/PlayingCard.vue';

/** The image source `PlayingCard` gives a card (images are inlined, so a test compares sources, not names). */
export function cardSrc(rank: string, suit: string): string {
  return mount(PlayingCard, { props: { rank, suit } }).get('img').attributes('src')!;
}

/** The source of a card back. */
export function backSrc(): string {
  return mount(PlayingCard, { props: { hidden: true, rank: 'ace', suit: 'spade' } }).get('img').attributes('src')!;
}
