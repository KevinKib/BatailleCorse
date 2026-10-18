import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentHandFan from './PresidentHandFan.vue';
import { FAN_MAX_BACKS } from '../../model/president/handFan';

const fan = (count: number, narrow = false) => mount(PresidentHandFan, { props: { count, narrow } });

describe('PresidentHandFan', () => {
  it('givenCards_thenOneBackPerCardAndTheNumberWrittenUnderThem', () => {
    const w = fan(5);
    expect(w.findAll('[data-test="fan-back"]')).toHaveLength(5);
    expect(w.get('[data-test="seat-cards"]').text()).toBe('5 cards');
  });

  it('givenAFullHand_thenTheBacksAreCappedButTheNumberIsExact', () => {
    const w = fan(18, true);
    expect(w.findAll('[data-test="fan-back"]')).toHaveLength(FAN_MAX_BACKS.narrow);
    expect(w.get('[data-test="seat-cards"]').text()).toBe('18 cards');
  });

  it('givenAnEmptyHand_thenNoFanButTheTextStays', () => {
    const w = fan(0);
    expect(w.findAll('[data-test="fan-back"]')).toHaveLength(0);
    expect(w.get('[data-test="seat-cards"]').text()).toBe('0 cards');
  });

  it('isReadAsANumberOfCards_thePictureHidden', () => {
    const w = fan(1);
    expect(w.get('[data-test="seat-cards"]').text()).toBe('1 card');
    expect(w.get('.fan-picture').attributes('aria-hidden')).toBe('true');
  });
});
