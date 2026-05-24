import EventData from "./EventData";
import GameId from "../GameId";

export default interface CreateEventData extends EventData {
  game: GameId;
  tokens: Record<number, string>;
}