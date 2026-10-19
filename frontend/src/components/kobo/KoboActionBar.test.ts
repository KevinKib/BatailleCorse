import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
import KoboActionBar from './KoboActionBar.vue';
import { buildKoboState } from '../../model/fixtures/kobo';
import type { KoboAction, KoboPhase } from '../../model/kobo/KoboState';
import { messagesEn } from '../../locales/en';

const ui = messagesEn.koboUi;

function mountBar(phase: KoboPhase, actions: KoboAction[], props: Record<string, unknown> = {}) {
  return mount(KoboActionBar, {
    global: { plugins: [PrimeVue] },
    props: {
      game: buildKoboState({ phase, availableActions: actions }),
      prompt: null, phaseText: 'Phase line', yourTurn: false,
      pending: { kind: 'none' }, busy: false, notice: null, canHide: false,
      ...props,
    },
  });
}
const button = (wrapper: ReturnType<typeof mountBar>, id: string) => wrapper.find(`[data-test="action-${id}"]`);

describe('KoboActionBar', () => {
  it.each<[KoboPhase, KoboAction[], string, string]>([
    ['MEMORISING', ['READY'], 'ready', ui.actions.ready],
    ['DRAW', ['DRAW'], 'draw', ui.actions.draw],
    ['DECISION', ['SWAP_DRAWN', 'DISCARD_DRAWN'], 'discard', ui.actions.discard],
    ['POWER', ['PEEK', 'SKIP_POWER'], 'skip', ui.actions.skip],
    ['TURN_END', ['END_TURN', 'ANNOUNCE_KOBO'], 'endTurn', ui.actions.endTurn],
    ['TURN_END', ['END_TURN', 'ANNOUNCE_KOBO'], 'announce', ui.actions.announce],
  ])('given%sWith%j_thenShowsTheButton%s', (phase, actions, id, label) => {
    const wrapper = mountBar(phase, actions);
    expect(button(wrapper, id).text()).toBe(label);
  });

  it('givenNothingListed_thenNoButton', () => {
    const wrapper = mountBar('DRAW', []);
    expect(wrapper.findAll('button')).toHaveLength(0);
  });

  // An empty button column still costs its flex gap, which pushes the title off the centre of the bar.
  it('givenNoButton_thenNoButtonColumnTakesRoomBesideTheTitle', () => {
    const wrapper = mountBar('DRAW', []);
    expect(wrapper.find('.actions-buttons').exists()).toBe(false);
  });

  it('givenAButton_thenTheButtonColumnIsThere', () => {
    const wrapper = mountBar('DRAW', ['DRAW']);
    expect(wrapper.find('.actions-buttons').exists()).toBe(true);
  });

  // Where the buttons go around the text is in the CSS (one on the right, two one per side, three under it); the
  // Cypress spec kobo-turn-label-centered measures that the text stays on the axis of the cards in every case.
  it.each<[string, KoboPhase, KoboAction[], Record<string, unknown>, string]>([
    ['OneButton', 'DRAW', ['DRAW'], {}, 'actions-buttons--n1'],
    ['TwoButtons', 'TURN_END', ['END_TURN', 'ANNOUNCE_KOBO'], {}, 'actions-buttons--n2'],
    ['AButtonAndCancel', 'DECISION', ['DISCARD_DRAWN'], { pending: { kind: 'match-armed' } }, 'actions-buttons--n2'],
    ['ThreeButtons', 'TURN_END', ['END_TURN', 'ANNOUNCE_KOBO'], { canHide: true }, 'actions-buttons--n3'],
  ])('given%s_thenTheirPlaceAroundTheTextIs%s', (_name, phase, actions, props, expected) => {
    expect(mountBar(phase, actions, props).find('.actions-buttons').classes()).toContain(expected);
  });

  it('givenAButtonIsPressed_thenTheBarEmitsItsEvent', async () => {
    const wrapper = mountBar('TURN_END', ['END_TURN', 'ANNOUNCE_KOBO']);
    await button(wrapper, 'endTurn').trigger('click');
    await button(wrapper, 'announce').trigger('click');
    expect(wrapper.emitted('end-turn')).toHaveLength(1);
    expect(wrapper.emitted('announce')).toHaveLength(1);
  });

  it.each([
    ['ready', 'MEMORISING', ['READY']],
    ['draw', 'DRAW', ['DRAW']],
    ['discard', 'DECISION', ['DISCARD_DRAWN']],
    ['skip', 'POWER', ['SKIP_POWER']],
  ] as const)('givenPressing%s_thenEmitsIt', async (id, phase, actions) => {
    const wrapper = mountBar(phase, [...actions]);
    await button(wrapper, id).trigger('click');
    expect(wrapper.emitted(id)).toHaveLength(1);
  });

  it('givenACommandInFlight_thenEveryButtonIsDisabled', () => {
    const wrapper = mountBar('TURN_END', ['END_TURN', 'ANNOUNCE_KOBO'], { busy: true });
    expect(button(wrapper, 'endTurn').attributes('disabled')).toBeDefined();
    expect(button(wrapper, 'announce').attributes('disabled')).toBeDefined();
  });

  it('givenNoPrompt_thenTheLineIsThePhaseText', () => {
    const wrapper = mountBar('DRAW', ['DRAW']);
    expect(wrapper.get('[data-test="phase"]').text()).toBe('Phase line');
    expect(wrapper.find('[data-test="prompt"]').exists()).toBe(false);
  });

  it('givenAPrompt_thenTheLineIsThePromptInsteadOfThePhaseText', () => {
    const wrapper = mountBar('POWER', ['PEEK', 'SKIP_POWER'], { prompt: 'peekOwn' });
    expect(wrapper.get('[data-test="prompt"]').text()).toBe(ui.prompt.peekOwn);
    expect(wrapper.find('[data-test="phase"]').exists()).toBe(false);
  });

  it('givenAHalfMadeSelection_thenCancelIsOfferedAndEmitted', async () => {
    const wrapper = mountBar('POWER', ['BLIND_SWAP', 'SKIP_POWER'], { pending: { kind: 'blind-own', ownSlot: 1 } });
    await wrapper.get('[data-test="action-cancel"]').trigger('click');
    expect(wrapper.emitted('cancel')).toHaveLength(1);
    expect(mountBar('POWER', ['BLIND_SWAP']).find('[data-test="action-cancel"]').exists()).toBe(false);
  });

  it('givenAPeekToHide_thenHideIsOfferedAndEmitted', async () => {
    const wrapper = mountBar('TURN_END', ['END_TURN'], { canHide: true });
    expect(wrapper.get('[data-test="action-hide"]').text()).toBe(ui.actions.hide);
    await wrapper.get('[data-test="action-hide"]').trigger('click');
    expect(wrapper.emitted('hide')).toHaveLength(1);
    expect(mountBar('TURN_END', ['END_TURN']).find('[data-test="action-hide"]').exists()).toBe(false);
  });

  it.each(['matchTooLate', 'moveImpossible', 'connection'] as const)('givenTheNotice%s_thenItIsShownAsAnAlert', (key) => {
    const wrapper = mountBar('DRAW', ['DRAW'], { notice: key });
    const notice = wrapper.get('[data-test="notice"]');
    expect(notice.text()).toBe(ui.notice[key]);
    expect(notice.attributes('role')).toBe('alert');
  });

  it('givenNoNotice_thenNoAlertIsInThePage', () => {
    expect(mountBar('DRAW', ['DRAW']).find('[data-test="notice"]').exists()).toBe(false);
  });

  describe('matching discard', () => {
    it('givenMatchListed_thenMatchIsAToggleButtonEmittingItsEvent', async () => {
      const wrapper = mountBar('DRAW', ['DRAW', 'MATCH_DISCARD'], { matchButton: true });
      const match = button(wrapper, 'match');
      expect(match.text()).toBe(ui.actions.match);
      expect(match.attributes('aria-pressed')).toBe('false');
      await match.trigger('click');
      expect(wrapper.emitted('match')).toHaveLength(1);
    });

    it.each([
      [{ kind: 'match-armed' }],
      [{ kind: 'match-give', target: { seat: 1, slot: 0 }, expectedTopRank: '7', expectedRevision: 0 }],
    ])('givenTheModeIsArmed%j_thenMatchIsPressedAndCancelIsOffered', (pending) => {
      const wrapper = mountBar('DRAW', ['DRAW', 'MATCH_DISCARD'], { pending, matchButton: true });
      expect(button(wrapper, 'match').attributes('aria-pressed')).toBe('true');
      expect(wrapper.find('[data-test="action-cancel"]').exists()).toBe(true);
    });

    it('givenAnotherSelection_thenMatchIsNotPressed', () => {
      const wrapper = mountBar('POWER', ['BLIND_SWAP', 'MATCH_DISCARD'], { pending: { kind: 'blind-own', ownSlot: 1 }, matchButton: true });
      expect(button(wrapper, 'match').attributes('aria-pressed')).toBe('false');
    });

    it('givenACommandInFlight_thenMatchStaysEnabled_itIsARace', () => {
      const wrapper = mountBar('TURN_END', ['END_TURN', 'MATCH_DISCARD'], { busy: true, matchButton: true });
      expect(button(wrapper, 'endTurn').attributes('disabled')).toBeDefined();
      expect(button(wrapper, 'match').attributes('disabled')).toBeUndefined();
    });

    // Option B: a tap on a card already means Match when nothing else wants it, so the button is not drawn then.
    it('givenATapAlreadyMeansMatch_thenNoMatchButtonEvenThoughTheServerListsIt', () => {
      const wrapper = mountBar('DRAW', ['DRAW', 'MATCH_DISCARD']);
      expect(button(wrapper, 'match').exists()).toBe(false);
      expect(button(wrapper, 'draw').exists()).toBe(true);
      expect(mountBar('DRAW', ['DRAW', 'MATCH_DISCARD'], { matchButton: false }).find('[data-test="action-match"]').exists()).toBe(false);
    });

    it('givenTheButtonIsOnlyThere_thenItStillRespectsTheListing', () => {
      expect(mountBar('DRAW', ['DRAW'], { matchButton: true }).find('[data-test="action-match"]').exists()).toBe(false);
    });
  });

  it('givenALogSlot_thenItIsRenderedAboveTheButtons', () => {
    const wrapper = mount(KoboActionBar, {
      global: { plugins: [PrimeVue] },
      slots: { log: '<p data-test="slotted-log">Bob matched</p>' },
      props: {
        game: buildKoboState({ phase: 'DRAW', availableActions: ['DRAW'] }),
        prompt: null, phaseText: 'Phase line', yourTurn: false, pending: { kind: 'none' }, busy: false,
        notice: null, canHide: false,
      },
    });
    expect(wrapper.get('[data-test="slotted-log"]').text()).toBe('Bob matched');
  });

  it('keepsItsZoneAndTheTurnCueAlwaysInThePage', () => {
    const wrapper = mountBar('DRAW', []);
    expect(wrapper.attributes('data-test')).toBe('kobo-actions');
    expect(wrapper.find('[data-test="turn-indicator"]').exists()).toBe(true);
    expect(mountBar('DRAW', ['DRAW'], { yourTurn: true }).find('[data-test="turn-hint"]').exists()).toBe(true);
  });
});
