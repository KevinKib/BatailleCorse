import BatailleCorse from "./BatailleCorse";

export default interface Response {
  success: boolean,
  eventType: "CREATE" | "SEND " | "SLAP" | "GRAB",
  message: string,
  state: BatailleCorse,
}