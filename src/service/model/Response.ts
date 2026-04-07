import BatailleCorse from "./BatailleCorse";
import EventData from "./event/EventData";

export default interface Response {
  success: boolean,
  eventType: "CREATE" | "SEND" | "SLAP" | "GRAB",
  eventData: EventData,
  message: string,
  state: BatailleCorse,
}