import PlayerId from "../PlayerId";
import EventData from "./EventData";

export default interface JoinEventData extends EventData {
  player: PlayerId,
}
