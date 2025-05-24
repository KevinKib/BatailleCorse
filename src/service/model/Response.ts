import BatailleCorse from "./BatailleCorse";

export default interface Response {
  success: boolean,
  eventType: string,
  message: string,
  state: BatailleCorse,
}