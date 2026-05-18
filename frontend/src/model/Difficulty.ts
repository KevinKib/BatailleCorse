export interface Difficulty {
  name: string;
  color: string;
  reactionTime: number;
}

export const DIFFICULTY: Difficulty[] = [
  { name: 'Training',   color: '#6b7280', reactionTime: 2100 },
  { name: 'Bronze',     color: '#cd7f32', reactionTime: 1800 },
  { name: 'Silver',     color: '#a8a9ad', reactionTime: 1500 },
  { name: 'Gold',       color: '#ffd700', reactionTime: 1200 },
  { name: 'Platinum',   color: '#00b4d8', reactionTime:  900 },
  { name: 'Diamond',    color: '#91d7f5', reactionTime:  700 },
  { name: 'Champion',   color: '#a855f7', reactionTime:  600 },
  { name: 'Challenger', color: '#f97316', reactionTime:  500 },
  { name: 'Legend',     color: '#ef4444', reactionTime:  400 },
];

export const MIN_DIFFICULTY = 0;
export const MAX_DIFFICULTY = DIFFICULTY.length - 1;
