/** The Private game switch as the lobby hands it to a game's own options panel (the `privacy` slot prop). */
export interface LobbyPrivacy {
  /** True when the lobby is hidden from the public list. */
  value: boolean;
  /** Only the host changes it. */
  editable: boolean;
  set: (value: boolean) => void;
}
