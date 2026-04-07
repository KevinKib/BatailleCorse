import PlayerId from "../PlayerId";
import EventData from "./EventData";

export default interface SendEventData extends EventData {
  player: PlayerId,
}