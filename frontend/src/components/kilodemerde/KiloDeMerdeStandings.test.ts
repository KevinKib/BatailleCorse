import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import KiloDeMerdeStandings from './KiloDeMerdeStandings.vue';
import { kiloDeMerdeFinishedState, kiloDeMerdeStepState } from '../../model/kilodemerde/fixtures';

const nameOf = (seat: number) => `P${seat}`;

describe('KiloDeMerdeStandings', () => {
  it('givenTheFinalPlaces_thenSharedPlacesAreKeptAndMyRowIsNamedYou', () => {
    const wrapper = mount(KiloDeMerdeStandings, {
      props: { players: kiloDeMerdeFinishedState.players, places: kiloDeMerdeFinishedState.result!.standings, nameOf, mySeat: 0 },
    });
    expect(wrapper.findAll('[data-test="final-rank"]').map(r => r.text())).toEqual(['1', '1', '3', '4']);
    expect(wrapper.findAll('[data-test="final-name"]').map(r => r.text())).toEqual(['You', 'P2', 'P1', 'P3']);
    expect(wrapper.findAll('[data-test="final-total"]').map(r => r.text())).toEqual(['0', '0', '24', '113']);
  });

  it('givenAGameInProgress_thenSeatsAreRankedByKilosWithTiesShared', () => {
    const wrapper = mount(KiloDeMerdeStandings, {
      props: { players: kiloDeMerdeStepState.players, places: null, nameOf, mySeat: 0 },
    });
    expect(wrapper.findAll('[data-test="final-rank"]').map(r => r.text())).toEqual(['1', '1', '3']);
    expect(wrapper.findAll('[data-test="final-total"]').map(r => r.text())).toEqual(['0', '0', '13']);
  });
});
