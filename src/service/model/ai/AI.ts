import { useBatailleCorseStore } from "../../../state/BatailleCorse.store";



export default class AI {

  private playerIndex: number;
  private reactionTime: number;
  private batailleCorse: any;

  private timeoutId: number | undefined;

  public constructor(playerIndex: number, reactionTime: number) {
    this.playerIndex = playerIndex;
    this.reactionTime = reactionTime;

    this.batailleCorse = useBatailleCorseStore();
  }

  public canSlap() {
    const pile = this.batailleCorse.state?.pile;

    if (pile == undefined || pile.cards == undefined && pile.cards.length == 0) {
      return false;
    }

    if (pile.cards.length >= 1 && pile.cards.at(0).rank == "10") {
      return true;
    }

    if (pile.cards.length >= 2 &&pile.cards.at(0).rank == pile.cards.at(1).rank) {
      return true;
    }

    if (pile.cards.length >= 3 &&pile.cards.at(0).rank == pile.cards.at(2).rank) {
      return true;
    }

    if (pile.cards.length >= 2) {
      const pileZeroRank = Number(pile.cards.at(0).rank);
      const pileOneRank = Number(pile.cards.at(1).rank);

      if (!isNaN(pileZeroRank) && !isNaN(pileOneRank) && pileZeroRank + pileOneRank == 10) {
        return true;
      }
    }

    return false;
  }

  public canSend() : boolean {
    return this.batailleCorse.state?.players.at(this.playerIndex).availableActions.includes("SEND");
  }

  private getRandomInt(max: number) {
    return Math.floor(Math.random() * max);
  }

  public get reaction() {
    const variation = this.getRandomInt(200) - 100;
    // const variation = 0;

    return this.reactionTime + variation;
  }

  public play() {

    if (this.timeoutId != undefined) {
      clearTimeout(this.timeoutId);
    }

    this.timeoutId = setTimeout(() => {
      if (this.canSlap()) {
        console.log("AI slaps");

        this.batailleCorse.slap(this.playerIndex);
      }
      else if (this.canSend()) {
        console.log("AI sends");
        this.batailleCorse.send(this.playerIndex);
      }
    }, this.reactionTime);
  }
    
}