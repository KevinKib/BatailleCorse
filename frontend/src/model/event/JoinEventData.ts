import PlayerId from "../PlayerId";
import EventData from "./EventData";
import type SessionSeat from "../SessionSeat";

export default interface JoinEventData extends EventData {
  player: PlayerId,
  players: SessionSeat[],
}
