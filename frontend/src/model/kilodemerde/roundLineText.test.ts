import { describe, it, expect } from 'vitest';
import { roundLineText } from './roundLineText';
import { kiloCard } from './fixtures';
import { messagesEn } from '../../locales/en';
import { messagesFr } from '../../locales/fr';
import type { RoundLine } from './roundSummary';
import { faceLetters } from './kilo';

const FR_LETTERS = faceLetters(messagesFr.kiloDeMerdeUi.ranks);

// A line about my own seat is conjugated for "you" ("Vous prenez"), never "Vous prend" from the third-person template.
const ME = 0;
const nameOf = (seat: number) => seat === ME ? 'Vous' : `Bob`;
const line = (code: RoundLine['code'], seat: number, card = kiloCard('KING', 'SPADE'), kg = 13): RoundLine =>
  ({ code, seat, card, kg });

describe('roundLineText', () => {
  it('givenFrenchAndANamedSeat_thenTheThirdPersonIsUsed', () => {
    const outcome = messagesFr.kiloDeMerdeUi.outcome;
    expect(roundLineText(line('LAST_DRAWS', 2), nameOf, outcome, ME, FR_LETTERS)).toBe('Trop tard ! Bob prend R♠ (13 kg)');
    expect(roundLineText(line('FALSE_SLAP', 2, kiloCard('9', 'CLUB'), 9), nameOf, outcome, ME, FR_LETTERS))
      .toBe('Fausse claque : Bob prend 9♣ (9 kg)');
    expect(roundLineText(line('FLUSH', 2, kiloCard('7', 'HEART'), 0), nameOf, outcome, ME, FR_LETTERS))
      .toBe('Chasse d’eau ! Bob perd tous ses kilos');
  });

  it('givenFrenchAndMySeat_thenTheLineSpeaksToMe', () => {
    const outcome = messagesFr.kiloDeMerdeUi.outcome;
    expect(roundLineText(line('LAST_DRAWS', ME), nameOf, outcome, ME, FR_LETTERS)).toBe('Trop tard ! Vous prenez R♠ (13 kg)');
    expect(roundLineText(line('FALSE_SLAP', ME, kiloCard('9', 'CLUB'), 9), nameOf, outcome, ME, FR_LETTERS))
      .toBe('Fausse claque : vous prenez 9♣ (9 kg)');
    expect(roundLineText(line('FLUSH', ME, kiloCard('7', 'HEART'), 0), nameOf, outcome, ME, FR_LETTERS))
      .toBe('Chasse d’eau ! Vous perdez tous vos kilos');
  });

  it('givenEnglishAndMySeat_thenTheLineSpeaksToMe', () => {
    const outcome = messagesEn.kiloDeMerdeUi.outcome;
    expect(roundLineText(line('LAST_DRAWS', ME), nameOf, outcome, ME)).toBe('You were last and take K♠: 13 kg');
    expect(roundLineText(line('FALSE_SLAP', ME, kiloCard('9', 'CLUB'), 9), nameOf, outcome, ME))
      .toBe('False slap: you take 9♣: 9 kg');
    expect(roundLineText(line('FLUSH', ME, kiloCard('7', 'HEART'), 0), nameOf, outcome, ME))
      .toBe('Flush! You drop all your kilos');
  });

  it('givenEnglishAndANamedSeat_thenTheThirdPersonIsUsed', () => {
    const outcome = messagesEn.kiloDeMerdeUi.outcome;
    expect(roundLineText(line('LAST_DRAWS', 2), nameOf, outcome, ME)).toBe('Bob was last and takes K♠: 13 kg');
    expect(roundLineText(line('FLUSH', 2, kiloCard('7', 'HEART'), 0), nameOf, outcome, ME))
      .toBe('Flush! Bob drops all their kilos');
  });

  it('givenNoSeatOfMine_thenEveryLineIsInTheThirdPerson', () => {
    expect(roundLineText(line('LAST_DRAWS', 0), () => 'Bob', messagesEn.kiloDeMerdeUi.outcome, null))
      .toBe('Bob was last and takes K♠: 13 kg');
  });
});
