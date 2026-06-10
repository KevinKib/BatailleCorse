import BatailleCorse from "./BatailleCorse";
import EventData from "./event/EventData";

export default interface Response {
  success: boolean,
  eventType: "CREATE" | "SEND" | "SLAP" | "GRAB" | "JOIN" | "OPPONENT_DISCONNECTED" | "OPPONENT_RECONNECTED" | "FORFEIT",
  eventData: EventData,
  message: string,
  state: BatailleCorse,
}