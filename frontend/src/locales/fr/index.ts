import type { Messages } from '../Messages';
import * as shell from './shell';
import * as cards from './cards';
import * as kobo from './kobo';
import * as boards from './boards';
import * as privacy from './privacy';
import { ninetyNineOptions, ninetyNineUi } from './ninetynine';

// The French bundle, composed from one file per translation chunk. Loaded on demand (`import('./fr')`).
export const messagesFr: Messages = {
  ...shell,
  ...cards,
  ...kobo,
  ...boards,
  ...privacy,
  ninetyNineUi,
  // 99's option texts join the shell's options (the area has no file of its own for them).
  options: { ...shell.options, ninetyNine: ninetyNineOptions },
};
