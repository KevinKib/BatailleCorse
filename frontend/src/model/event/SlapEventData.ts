import PlayerId from "../PlayerId";
import EventData from "./EventData";

export default interface SlapEventData extends EventData {
  isSuccessful: boolean;
  player: PlayerId;
}
