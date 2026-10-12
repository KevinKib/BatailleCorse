/** One seat of a game as the server lists it: the name typed by its player, a bot flag and the bot's options. */
export default interface SessionSeat {
  id: number;
  name: string | null;
  joined: boolean;
  bot?: boolean;
  /** Options of a computer seat (Bataille Corse: its tier), empty for a human. */
  seatOptions?: Record<string, string>;
}
