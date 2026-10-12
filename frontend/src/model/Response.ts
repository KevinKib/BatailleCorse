import BatailleCorse from "./BatailleCorse";
import EventData from "./event/EventData";

/** A message of the Bataille Corse game topic: a game state, or a refused action (`success: false`). */
export default interface Response {
  success: boolean,
  eventType: "START" | "SEND" | "SLAP" | "GRAB" | "JOIN" | "OPPONENT_DISCONNECTED" | "OPPONENT_RECONNECTED" | "FORFEIT",
  eventData: EventData,
  message: string,
  state: BatailleCorse,
}
