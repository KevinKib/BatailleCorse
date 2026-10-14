
import { describe, it, expect } from 'vitest';
import { cardSrc, backSrc } from '../../model/fixtures/cardSrc';
import { mount } from '@vue/test-utils';
import KoboPiles from './KoboPiles.vue';
import { buildKoboCard, buildKoboState } from '../../model/fixtures/kobo';
import type { KoboState } from '../../model/kobo/KoboState';

function mountPiles(overrides: Partial<KoboState> = {}, mySeat = 0, currentName = 'Bob', extra: Record<string, unknown> = {}) {
  return mount(KoboPiles, { props: { game: buildKoboState(overrides), mySeat, currentName, ...extra } });
}

describe('KoboPiles', () => {
  it('showsTheDrawPileCountAndTheDiscardTopFaceUpWithItsCount', () => {
    const wrapper = mountPiles({ drawPileSize: 31, discardSize: 3, discardTop: buildKoboCard('KING', 'SPADE') });
    expect(wrapper.get('[data-test="draw-count"]').text()).toBe('31');
    expect(wrapper.get('[data-test="draw-pile"]').attributes('aria-label')).toBe('Draw pile, 31 cards left');
    expect(wrapper.get('[data-test="discard-count"]').text()).toBe('3');
    expect(wrapper.get('[data-test="discard-pile"] img').attributes('src')).toBe(cardSrc('KING', 'SPADE'));
    expect(wrapper.get('[data-test="discard-pile"]').attributes('aria-label'))
      .toBe('Discard pile, top card king of spade, 3 in the pile');
  });

  it('givenAnEmptyDiscard_thenAPlaceholderIsShown', () => {
    const wrapper = mountPiles({ discardTop: null, discardSize: 0 });
    expect(wrapper.find('[data-test="discard-pile"] img').exists()).toBe(false);
    expect(wrapper.get('[data-test="discard-pile"]').attributes('aria-label')).toBe('Discard pile, empty');
  });

  it('givenMyDrawnCard_thenItIsFaceUpInTheDrawnZone', () => {
    const wrapper = mountPiles({ hasDrawn: true, currentSeat: 0, drawnCard: buildKoboCard('QUEEN', 'HEART') });
    expect(wrapper.get('[data-test="drawn-zone"] img').attributes('src')).toBe(cardSrc('QUEEN', 'HEART'));
    expect(wrapper.get('[data-test="drawn-zone"]').attributes('aria-label')).toBe('Your drawn card: queen of heart');
  });

  it('givenAnotherPlayerDrew_thenTheDrawnZoneShowsABack', () => {
    const wrapper = mountPiles({ hasDrawn: true, currentSeat: 1, drawnCard: null });
    expect(wrapper.get('[data-test="drawn-zone"] img').attributes('src')).toBe(backSrc());
    expect(wrapper.get('[data-test="drawn-zone"]').attributes('aria-label')).toBe('Bob holds a drawn card');
  });

  it('givenNoDrawnCard_thenTheZoneStaysAsAnEmptyPlaceholder', () => {
    const wrapper = mountPiles({ hasDrawn: false });
    expect(wrapper.find('[data-test="drawn-zone"] img').exists()).toBe(false);
    expect(wrapper.get('[data-test="drawn-zone"]').attributes('aria-label')).toBe('No drawn card');
  });

  describe('click targets', () => {
    it('givenNothingToDoOnThePiles_thenNoneIsAButton', () => {
      const wrapper = mountPiles();
      expect(wrapper.findAll('button')).toHaveLength(0);
      expect(wrapper.get('[data-test="draw-pile"]').attributes('role')).toBe('img');
    });

    it('givenICanDraw_thenTheDrawPileIsANamedButtonThatEmitsDraw', async () => {
      const wrapper = mountPiles({ drawPileSize: 31 }, 0, 'Bob', { canDraw: true });
      const pile = wrapper.get('button[data-test="draw-pile"]');
      expect(pile.attributes('aria-label')).toBe('Draw a card, 31 cards left');
      expect(wrapper.find('button[data-test="discard-pile"]').exists()).toBe(false);
      expect(wrapper.get('[data-test="draw-count"]').text()).toBe('31');
      await pile.trigger('click');
      expect(wrapper.emitted('draw')).toHaveLength(1);
    });

    it('givenIHoldADrawnCard_thenTheDiscardPileIsANamedButtonThatEmitsDiscard', async () => {
      const wrapper = mountPiles({ discardTop: buildKoboCard('KING', 'SPADE'), discardSize: 3 }, 0, 'Bob', { canDiscard: true });
      const pile = wrapper.get('button[data-test="discard-pile"]');
      expect(pile.attributes('aria-label')).toBe('Discard the drawn card. Pile, top card king of spade, 3 in the pile');
      expect(wrapper.get('[data-test="discard-pile"] img').attributes('src')).toBe(cardSrc('KING', 'SPADE'));
      expect(wrapper.get('[data-test="discard-count"]').text()).toBe('3');
      await pile.trigger('click');
      expect(wrapper.emitted('discard')).toHaveLength(1);
    });

    it('givenTheScreenIsBusy_thenTheActionablePileIsDisabled', () => {
      const wrapper = mountPiles({}, 0, 'Bob', { canDraw: true, busy: true });
      expect(wrapper.get('button[data-test="draw-pile"]').attributes('disabled')).toBeDefined();
    });
  });
});
