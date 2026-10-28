import type { Messages } from '../Messages';
import * as shell from './shell';
import * as cards from './cards';
import * as kobo from './kobo';
import * as boards from './boards';
import * as privacy from './privacy';
import { ninetyNine, ninetyNineOptions, ninetyNineUi } from './ninetynine';
import { draughts, draughtsOptions, draughtsUi } from './draughts';
import { blackjack, blackjackOptions, blackjackUi } from './blackjack';

// The French bundle, composed from one file per translation chunk. Loaded on demand (`import('./fr')`).
export const messagesFr: Messages = {
  ...shell,
  ...cards,
  ...kobo,
  ...boards,
  ...privacy,
  ninetyNine,
  ninetyNineUi,
  draughts,
  draughtsUi,
  blackjack,
  blackjackUi,
  // 99's, draughts' and Blackjack's option texts join the shell's options (these areas have no file of their own for them).
  options: {
    ...shell.options,
    ninetyNine: ninetyNineOptions,
    draughts: draughtsOptions,
    blackjack: blackjackOptions,
  },
};
