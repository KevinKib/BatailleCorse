import PlayerId from "../PlayerId";
import EventData from "./EventData";

export default interface GrabEventData extends EventData {
  player: PlayerId,
}